package com.dacn1.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.dacn1.app.ui.EkycNavHost
import com.dacn1.core.designsystem.EkycTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EkycTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    EkycNavHost()
                }
            }
        }
    }
}
