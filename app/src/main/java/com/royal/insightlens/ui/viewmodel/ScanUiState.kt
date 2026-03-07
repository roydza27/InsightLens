package com.royal.insightlens.ui.viewmodel

import com.royal.insightlens.data.local.entity.BookEntity

/**
 * ScanUiState — SRS §3.1
 *
 * Sealed class covering all possible UI states.
 * UI ONLY renders based on this — never touches API responses directly.
 */
sealed class ScanUiState {

    // Camera is active, waiting for a book to be pointed at
    object Idle : ScanUiState()

    // OCR frame accepted, extraction + API call in progress
    // progress: 0.0 - 1.0 for scan progress bar animation
    data class Scanning(val progress: Float = 0f) : ScanUiState()

    // Book found and saved to DB — show details or navigate
    data class Success(val book: BookEntity) : ScanUiState()

    // OCR ran but couldn't confidently identify a title
    object NoMatch : ScanUiState()

    // Network/API/DB error — show scan failed dialog
    data class Error(val message: String) : ScanUiState()

    // In cooldown — scanning locked temporarily
    data class Cooldown(val remainingMs: Long) : ScanUiState()
}