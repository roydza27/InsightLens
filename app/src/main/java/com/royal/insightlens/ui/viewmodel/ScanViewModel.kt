package com.royal.insightlens.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.text.Text
import com.royal.insightlens.data.repository.BookRepository
import com.royal.insightlens.scan.ScanController
import com.royal.insightlens.scan.ScanEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ScanViewModel — SRS §3.2
 *
 * Responsibilities:
 * - Receive OCR results from ScanFragment
 * - Coordinate ScanController (state machine)
 * - Manage single active coroutine Job (cancel previous before new)
 * - Trigger repository cache-first fetch
 * - Emit deterministic ScanUiState to UI
 *
 * Survives config changes (device rotation) — SRS §5
 */
class ScanViewModel(
    private val repository: BookRepository
) : ViewModel() {

    companion object {
        private const val TAG = "ScanViewModel"
    }

    // ─── UI State ─────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    // ─── Single Active Job ────────────────────────────────────────────────

    // SRS §3.2: Only one active scan Job — previous cancelled before new scan
    private var activeJob: Job? = null

    // ─── ScanController ───────────────────────────────────────────────────

    val scanController = ScanController(
        scope       = viewModelScope,
        cooldownMs  = ScanController.DEFAULT_COOLDOWN_MS,
        onResult    = ::handleScanEvent
    )

    // ─── OCR Frame Entry Point ────────────────────────────────────────────

    /**
     * Called by ScanFragment's ImageAnalyzer for every camera frame.
     * ScanController decides whether to process or drop the frame.
     */
    fun onOcrResult(
        ocrResult: Text,
        imageWidth: Int,
        imageHeight: Int
    ) {
        scanController.onFrame(
            ocrResult   = ocrResult,
            imageWidth  = imageWidth,
            imageHeight = imageHeight
        )
    }

    // ─── ScanEvent Handler ────────────────────────────────────────────────

    /**
     * Receives events from ScanController and drives UI state + repository.
     */
    private fun handleScanEvent(event: ScanEvent) {
        Log.d(TAG, "ScanEvent: $event")

        when (event) {

            is ScanEvent.ScanStarted -> {
                _uiState.value = ScanUiState.Scanning(progress = 0.1f)
            }

            is ScanEvent.TitleExtracted -> {
                // Cancel any previous in-flight repository call
                activeJob?.cancel()

                activeJob = viewModelScope.launch {
                    _uiState.value = ScanUiState.Scanning(progress = 0.5f)

                    val result = repository.fetchBook(
                        normalizedTitle = event.normalizedTitle,
                        rawTitle        = event.rawTitle,
                        confidenceScore = event.confidenceScore
                    )

                    result.fold(
                        onSuccess = { volumeId ->
                            _uiState.value = ScanUiState.Scanning(progress = 0.9f)

                            // Fetch the saved entity from DB (SSOT)
                            val book = repository.observeBook(volumeId)
                            book.collect { entity ->
                                if (entity != null) {
                                    _uiState.value = ScanUiState.Success(entity)
                                    return@collect
                                }
                            }
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Repository fetch failed: ${error.message}")
                            _uiState.value = ScanUiState.Error(
                                error.message ?: "Failed to fetch book details"
                            )
                        }
                    )
                }
            }

            is ScanEvent.ExtractionFailed -> {
                // OCR ran but confidence too low — go back to idle quietly
                // Don't show error dialog for this — just stay scanning
                _uiState.value = ScanUiState.NoMatch
            }

            is ScanEvent.Error -> {
                _uiState.value = ScanUiState.Error(event.message)
            }

            is ScanEvent.CooldownStarted -> {
                _uiState.value = ScanUiState.Cooldown(event.durationMs)
            }

            is ScanEvent.CooldownEnded -> {
                _uiState.value = ScanUiState.Idle
            }

            is ScanEvent.Reset -> {
                activeJob?.cancel()
                _uiState.value = ScanUiState.Idle
            }
        }
    }

    // ─── User Actions ─────────────────────────────────────────────────────

    /**
     * Called when user taps "Retry" in scan failed dialog.
     */
    fun onRetry() {
        activeJob?.cancel()
        scanController.reset()
    }

    /**
     * Called when user taps "Dismiss" in scan failed dialog.
     */
    fun onDismiss() {
        activeJob?.cancel()
        scanController.reset()
        _uiState.value = ScanUiState.Idle
    }

    /**
     * Called when user manually taps capture button.
     * Skips cooldown so they can scan immediately.
     */
    fun onManualCapture() {
        scanController.skipCooldown()
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        activeJob?.cancel()
        scanController.release()
        Log.d(TAG, "ViewModel cleared")
    }

    // ─── Factory ──────────────────────────────────────────────────────────

    class Factory(private val repository: BookRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ScanViewModel(repository) as T
        }
    }
}