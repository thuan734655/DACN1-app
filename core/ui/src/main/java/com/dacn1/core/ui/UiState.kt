package com.dacn1.core.ui

sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(
        val code: String? = null,
        val message: String,
        val retryable: Boolean = true
    ) : UiState<Nothing>
}
