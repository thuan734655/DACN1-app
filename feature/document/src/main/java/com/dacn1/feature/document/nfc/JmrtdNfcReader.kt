package com.dacn1.feature.document.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sf.scuba.smartcards.CardService
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.jmrtd.BACKey
import org.jmrtd.PACEKeySpec
import org.jmrtd.PassportService
import org.jmrtd.lds.CardAccessFile
import org.jmrtd.lds.PACEInfo
import org.jmrtd.lds.icao.DG1File
import org.jmrtd.lds.icao.DG2File
import java.io.DataInputStream
import java.security.Security
import com.dacn1.core.model.VerifyNfcRequest

private const val TAG = "JmrtdNfcReader"

/**
 * Đọc dữ liệu sinh trắc học từ chip NFC của thẻ CCCD Việt Nam bằng thư viện JMRTD.
 *
 * Quy trình chuẩn JMRTD:
 * 1. Tạo [BACKey] từ document number, ngày sinh, ngày hết hạn.
 * 2. Chuyển sang [PACEKeySpec] bằng [PACEKeySpec.createMRZKey] — API chuẩn của JMRTD.
 *    (createMRZKey nội bộ gọi PACEProtocol.computeKeySeedForPACE, khác với SHA-1 của BAC)
 * 3. Mở kết nối ISO/IEC 7816 qua IsoDep.
 * 4. Đọc EF.CardAccess để lấy thông số PACE.
 * 5. Gọi doPACE với PACEKeySpec và thông số từ chip.
 * 6. Đọc DG1, DG2 qua Secure Channel đã được thiết lập.
 */
object JmrtdNfcReader {

    init {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.addProvider(BouncyCastleProvider())
    }

    /**
     * Đọc dữ liệu từ chip NFC thẻ CCCD.
     *
     * @param tag   Tag NFC nhận được từ NfcAdapter.ReaderCallback
     * @param mrzKey Chuỗi 27 ký tự từ server:
     *              [CCCD 12 số][Check 1][DOB yyMMdd][Check 1][Expiry yyMMdd][Check 1]
     * @return [VerifyNfcRequest] chứa thông tin giải mã
     */
    suspend fun readTag(tag: Tag, mrzKey: String): VerifyNfcRequest = withContext(Dispatchers.IO) {
        require(mrzKey.length >= 27) { "Key MRZ không đủ 27 ký tự" }

        // Chuỗi mrzKey từ server có định dạng (27 ký tự):
        // [DocNum 9 số][Option 3 số][Check 1][DOB 6 số][Check 1][Expiry 6 số][Check 1]
        // Ví dụ: "205003458045005021913002197"
        val docNum9 = mrzKey.substring(0, 9)
        val dob     = mrzKey.substring(13, 19)
        val expiry  = mrzKey.substring(20, 26)

        val bacKey = BACKey(docNum9, dob, expiry)
        val paceKey = PACEKeySpec.createMRZKey(bacKey)

        // -----------------------------------------------------------------------
        // Bước C: Kết nối với chip
        // -----------------------------------------------------------------------
        val isoDep = IsoDep.get(tag)
            ?: throw IllegalStateException("Thẻ không hỗ trợ ISO-DEP")
        isoDep.timeout = 12_000

        var cardService: CardService? = null
        var passportService: PassportService? = null

        try {
            cardService = CardService.getInstance(isoDep)
            cardService.open()

            passportService = PassportService(
                cardService,
                PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
                PassportService.DEFAULT_MAX_BLOCKSIZE,
                false,
                true
            )
            passportService.open()

            // Bước C1: Đọc EF.CardAccess để lấy thông số PACE
            // Lưu ý: KHÔNG được gọi sendSelectApplet(false) ở đây vì EF.CardAccess
            // nằm ở ngoài/Master File. Chọn Applet sớm sẽ khiến việc đọc file này bị từ chối.
            val paceInfos = try {
                val stream = passportService.getInputStream(PassportService.EF_CARD_ACCESS)
                CardAccessFile(stream).securityInfos.filterIsInstance<PACEInfo>()
            } catch (_: Exception) {
                emptyList()
            }

            Log.w(TAG, "=== BẮT ĐẦU XÁC THỰC NFC ===")
            Log.w(TAG, "docNum9=$docNum9 | dob=$dob | expiry=$expiry")
            Log.w(TAG, "Chip hỗ trợ PACE: ${paceInfos.isNotEmpty()} (${paceInfos.size} PACEInfo)")

            if (paceInfos.isNotEmpty()) {
                val paceInfo = paceInfos.first()
                Log.w(TAG, "  → Gọi doPACE(oid=${paceInfo.objectIdentifier}, paramId=${paceInfo.parameterId})")
                passportService.doPACE(
                    paceKey,
                    paceInfo.objectIdentifier,
                    PACEInfo.toParameterSpec(paceInfo.parameterId),
                    paceInfo.parameterId
                )
                Log.w(TAG, "  ✓ PACE THÀNH CÔNG")
                
                // BẮT BUỘC: Select lại applet với cờ 'true' để JMRTD kích hoạt Secure Messaging
                passportService.sendSelectApplet(true)
                Log.w(TAG, "  ✓ Kích hoạt Secure Messaging (sendSelectApplet=true)")
            } else {
                Log.w(TAG, "  → Gọi doBAC (không có PACE)")
                passportService.doBAC(bacKey)
                Log.w(TAG, "  ✓ BAC THÀNH CÔNG")
            }

            // -----------------------------------------------------------------------
            // Bước D: Đọc Data Groups (Secure Messaging đã kích hoạt tự động)
            // -----------------------------------------------------------------------
            Log.w(TAG, "--- Đọc DG1 (thông tin MRZ) ---")
            val dg1 = try {
                DG1File(passportService.getInputStream(PassportService.EF_DG1))
                    .also { Log.w(TAG, "  ✓ DG1 OK (${it.encoded.size} bytes)") }
            } catch (e: Exception) {
                Log.e(TAG, "  ✗ DG1 lỗi: ${e.message}")
                throw e
            }

            Log.w(TAG, "--- Đọc DG2 (ảnh chân dung) ---")
            val dg2 = try {
                DG2File(passportService.getInputStream(PassportService.EF_DG2))
                    .also { Log.w(TAG, "  ✓ DG2 OK (${it.encoded.size} bytes)") }
            } catch (e: Exception) {
                Log.e(TAG, "  ✗ DG2 lỗi: ${e.message}")
                throw e
            }

            Log.w(TAG, "--- Phân tích dữ liệu DG1 (thông tin MRZ) ---")
            val mrzInfo = dg1.mrzInfo
            val documentNumber = mrzInfo.documentNumber ?: ""
            // Gộp Họ và Tên đệm + Tên
            val primaryId = mrzInfo.primaryIdentifier?.replace("<", " ")?.trim() ?: ""
            val secondaryId = mrzInfo.secondaryIdentifier?.replace("<", " ")?.trim() ?: ""
            val fullName = "$primaryId $secondaryId".trim()
            val dateOfBirthStr = mrzInfo.dateOfBirth ?: ""
            val dateOfExpiryStr = mrzInfo.dateOfExpiry ?: ""
            val nationality = mrzInfo.nationality ?: ""
            val gender = mrzInfo.gender?.toString() ?: ""

            Log.w(TAG, "--- Phân tích dữ liệu DG2 (ảnh chân dung) ---")
            var faceImageBase64 = ""
            val faceInfos = dg2.faceInfos
            if (!faceInfos.isNullOrEmpty()) {
                val imageInfos = faceInfos.first().faceImageInfos
                if (!imageInfos.isNullOrEmpty()) {
                    val imageInfo = imageInfos.first()
                    val imageLength = imageInfo.imageLength
                    
                    try {
                        imageInfo.imageInputStream.use { ism ->
                            val imageBytes = ByteArray(imageLength)
                            DataInputStream(ism).use { dis ->
                                dis.readFully(imageBytes)
                            }
                            faceImageBase64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                            Log.w(TAG, "  ✓ Trích xuất ảnh chân dung thành công (${imageBytes.size} bytes)")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "  ✗ Lỗi trích xuất ảnh DG2: ${e.message}")
                    }
                }
            }

            Log.w(TAG, "=== XÁC THỰC VÀ GIẢI MÃ NFC HOÀN TẤT ===")
            
            VerifyNfcRequest(
                idNumber = documentNumber,
                fullName = fullName,
                dateOfBirth = dateOfBirthStr,
                dateOfExpiry = dateOfExpiryStr,
                nationality = nationality,
                gender = gender,
                faceImageBase64 = faceImageBase64
            )

        } finally {
            runCatching { passportService?.close() }
            runCatching { cardService?.close() }
        }
    }
}
