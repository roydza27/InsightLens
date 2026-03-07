package com.royal.insightlens.engine

import com.google.mlkit.vision.text.Text

/**
 * TitleExtractionEngine — SRS §3.3
 *
 * Orchestrates the full pipeline:
 *   CandidateBuilder → CandidateScorer → Normalizer
 *
 * Input:  Raw ML Kit OCR result (Text)
 * Output: ExtractionResult (success/failure)
 */
object TitleExtractionEngine {

    /**
     * Main entry point — processes an ML Kit OCR result
     * and extracts the most likely book title.
     *
     * @param ocrResult   ML Kit Text object from ImageAnalysis
     * @param imageWidth  Width of analyzed frame
     * @param imageHeight Height of analyzed frame
     * @return [ExtractionResult]
     */
    fun extract(
        ocrResult: Text,
        imageWidth: Int,
        imageHeight: Int
    ): ExtractionResult {

        // Guard: no text detected at all
        if (ocrResult.text.isBlank()) {
            return ExtractionResult.Failure(reason = FailureReason.NO_TEXT_DETECTED)
        }

        // Step 1: Build candidates from OCR blocks
        val candidates = CandidateBuilder.build(
            ocrResult   = ocrResult,
            imageWidth  = imageWidth,
            imageHeight = imageHeight
        )

        if (candidates.isEmpty()) {
            return ExtractionResult.Failure(reason = FailureReason.NO_CANDIDATES_BUILT)
        }

        // Step 2: Score all candidates, pick best
        val bestCandidate = CandidateScorer.pickBest(candidates)
            ?: return ExtractionResult.Failure(
                reason = FailureReason.SCORE_BELOW_THRESHOLD,
                allCandidates = candidates
            )

        // Step 3: Normalize for DB dedup
        val normalizedTitle = Normalizer.normalize(bestCandidate.text)

        if (normalizedTitle.isBlank() || normalizedTitle.length < 2) {
            return ExtractionResult.Failure(reason = FailureReason.NORMALIZATION_EMPTY)
        }

        return ExtractionResult.Success(
            rawTitle        = bestCandidate.text,
            normalizedTitle = normalizedTitle,
            confidenceScore = bestCandidate.score,
            candidate       = bestCandidate
        )
    }
}

// ─── Result Types ──────────────────────────────────────────────────────────

sealed class ExtractionResult {

    data class Success(
        val rawTitle: String,
        val normalizedTitle: String,
        val confidenceScore: Float,
        val candidate: TitleCandidate
    ) : ExtractionResult()

    data class Failure(
        val reason: FailureReason,
        val allCandidates: List<TitleCandidate> = emptyList()
    ) : ExtractionResult()
}

enum class FailureReason {
    NO_TEXT_DETECTED,       // OCR returned blank
    NO_CANDIDATES_BUILT,    // All blocks filtered out
    SCORE_BELOW_THRESHOLD,  // Best score < 0.35 (SRS §6.1: low confidence)
    NORMALIZATION_EMPTY     // Normalized result was empty string
}