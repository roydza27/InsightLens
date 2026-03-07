package com.royal.insightlens.scan

import android.util.Log
import com.google.mlkit.vision.text.Text
import com.royal.insightlens.engine.ExtractionResult
import com.royal.insightlens.engine.TitleExtractionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * ScanController — SRS §3.4
 *
 * Deterministic state machine:
 *   IDLE → PROCESSING → COOLDOWN → IDLE
 *
 * Prevents:
 * - API spamming
 * - UI flicker
 * - Duplicate DB inserts
 * - Frame flooding
 *
 * Thread-safe via AtomicReference for state.
 */
class ScanController(
    private val scope: CoroutineScope,
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
    private val onResult: (ScanEvent) -> Unit
) {

    companion object {
        private const val TAG = "ScanController"
        const val DEFAULT_COOLDOWN_MS = 2000L
    }

    // ─── State Machine ────────────────────────────────────────────────────

    enum class State { IDLE, PROCESSING, COOLDOWN }

    private val _state = AtomicReference(State.IDLE)

    val currentState: State get() = _state.get()

    val isIdle: Boolean get() = _state.get() == State.IDLE

    // ─── Cooldown Job ─────────────────────────────────────────────────────

    private var cooldownJob: Job? = null

    // ─── Frame Processing ─────────────────────────────────────────────────

    /**
     * Called for every camera frame by the ImageAnalyzer.
     *
     * SRS behavior:
     * - Frames ignored unless state = IDLE
     * - PROCESSING locks pipeline
     * - COOLDOWN prevents duplicate scans
     */
    fun onFrame(
        ocrResult: Text,
        imageWidth: Int,
        imageHeight: Int
    ) {
        // Only process if IDLE — all other states are ignored
        if (!_state.compareAndSet(State.IDLE, State.PROCESSING)) {
            Log.v(TAG, "Frame dropped — state: ${_state.get()}")
            return
        }

        Log.d(TAG, "Frame accepted — starting extraction")
        onResult(ScanEvent.ScanStarted)

        scope.launch {
            try {
                val result = TitleExtractionEngine.extract(
                    ocrResult   = ocrResult,
                    imageWidth  = imageWidth,
                    imageHeight = imageHeight
                )

                when (result) {
                    is ExtractionResult.Success -> {
                        Log.d(TAG, "Extraction success: '${result.rawTitle}' (score=${result.confidenceScore})")
                        onResult(
                            ScanEvent.TitleExtracted(
                                rawTitle        = result.rawTitle,
                                normalizedTitle = result.normalizedTitle,
                                confidenceScore = result.confidenceScore
                            )
                        )
                        enterCooldown()
                    }

                    is ExtractionResult.Failure -> {
                        Log.d(TAG, "Extraction failed: ${result.reason}")
                        onResult(ScanEvent.ExtractionFailed(result.reason.name))
                        // Return to IDLE immediately on failure — allow next frame
                        _state.set(State.IDLE)
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during extraction", e)
                onResult(ScanEvent.Error(e.message ?: "Unknown error"))
                _state.set(State.IDLE)
            }
        }
    }

    // ─── Cooldown ─────────────────────────────────────────────────────────

    /**
     * Enters COOLDOWN state for [cooldownMs] milliseconds,
     * then automatically returns to IDLE.
     *
     * This prevents duplicate scans of the same object.
     */
    private fun enterCooldown() {
        _state.set(State.COOLDOWN)
        onResult(ScanEvent.CooldownStarted(cooldownMs))
        Log.d(TAG, "Cooldown started (${cooldownMs}ms)")

        cooldownJob?.cancel()
        cooldownJob = scope.launch {
            delay(cooldownMs)
            _state.set(State.IDLE)
            onResult(ScanEvent.CooldownEnded)
            Log.d(TAG, "Cooldown ended — back to IDLE")
        }
    }

    // ─── Manual Controls ──────────────────────────────────────────────────

    /**
     * Force-resets to IDLE state.
     * Used when user taps "Retry" after a failed scan dialog.
     */
    fun reset() {
        cooldownJob?.cancel()
        _state.set(State.IDLE)
        onResult(ScanEvent.Reset)
        Log.d(TAG, "Manual reset — back to IDLE")
    }

    /**
     * Skips remaining cooldown and immediately returns to IDLE.
     * Used when user manually taps the capture button.
     */
    fun skipCooldown() {
        if (_state.get() == State.COOLDOWN) {
            cooldownJob?.cancel()
            _state.set(State.IDLE)
            Log.d(TAG, "Cooldown skipped — back to IDLE")
        }
    }

    /**
     * Clean up — cancel any running jobs.
     * Call from ViewModel.onCleared() or Fragment.onDestroyView().
     */
    fun release() {
        cooldownJob?.cancel()
        _state.set(State.IDLE)
        Log.d(TAG, "ScanController released")
    }
}

// ─── Events emitted to ViewModel ──────────────────────────────────────────

sealed class ScanEvent {

    // Pipeline started processing a frame
    object ScanStarted : ScanEvent()

    // Title successfully extracted — pass to repository
    data class TitleExtracted(
        val rawTitle: String,
        val normalizedTitle: String,
        val confidenceScore: Float
    ) : ScanEvent()

    // OCR ran but couldn't build a confident title candidate
    data class ExtractionFailed(val reason: String) : ScanEvent()

    // Unexpected exception
    data class Error(val message: String) : ScanEvent()

    // Cooldown period began
    data class CooldownStarted(val durationMs: Long) : ScanEvent()

    // Cooldown ended, ready to scan again
    object CooldownEnded : ScanEvent()

    // Manual reset triggered
    object Reset : ScanEvent()
}