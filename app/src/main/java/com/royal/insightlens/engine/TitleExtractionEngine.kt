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
     * Enhanced pipeline:
     * 1. Detect if OCR region contains a book object
     * 2. Build candidates from OCR blocks
     * 3. Score and pick best candidate
     * 4. Validate extracted text is a book title
     * 5. Normalize for DB dedup
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

        // NEW Step 0: Detect if this is a book object (prevents random text)
        val bookDetection = BookObjectDetector.detectBook(
            ocrResult   = ocrResult,
            imageWidth  = imageWidth,
            imageHeight = imageHeight
        )
        
        if (!bookDetection.isBook) {
            return ExtractionResult.Failure(
                reason = FailureReason.NOT_A_BOOK_OBJECT,
                detectionDetails = bookDetection.detectionDetails
            )
        }

        // Step 1: Build candidates from OCR blocks
        var candidates = CandidateBuilder.build(
            ocrResult   = ocrResult,
            imageWidth  = imageWidth,
            imageHeight = imageHeight
        )

        if (candidates.isEmpty()) {
            return ExtractionResult.Failure(reason = FailureReason.NO_CANDIDATES_BUILT)
        }

        // NEW Step 1.5: Filter out likely subtitles/taglines to focus on main titles
        candidates = SubtitleFilter.filterSubtitles(candidates)

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

        // NEW Step 4: Validate that extracted text is a valid book title
        if (!BookValidator.isValidBookTitle(normalizedTitle)) {
            return ExtractionResult.Failure(
                reason = FailureReason.INVALID_BOOK_TITLE,
                invalidTitle = normalizedTitle
            )
        }

        // Step 5: Compute book-likelihood score to boost confidence
        val bookLikelihoodScore = BookValidator.scoreBookLikelihood(bestCandidate.text)
        val enhancedConfidenceScore = (bestCandidate.score * 0.7f) + (bookLikelihoodScore * 0.3f)

        return ExtractionResult.Success(
            rawTitle        = bestCandidate.text,
            normalizedTitle = normalizedTitle,
            confidenceScore = enhancedConfidenceScore,
            candidate       = bestCandidate,
            bookObjectConfidence = bookDetection.confidence
        )
    }
}

// ─── Result Types ──────────────────────────────────────────────────────────

sealed class ExtractionResult {

    data class Success(
        val rawTitle: String,
        val normalizedTitle: String,
        val confidenceScore: Float,
        val candidate: TitleCandidate,
        val bookObjectConfidence: Float = 0f
    ) : ExtractionResult()

    data class Failure(
        val reason: FailureReason,
        val allCandidates: List<TitleCandidate> = emptyList(),
        val detectionDetails: String = "",
        val invalidTitle: String = ""
    ) : ExtractionResult()
}

enum class FailureReason {
    NO_TEXT_DETECTED,           // OCR returned blank
    NO_CANDIDATES_BUILT,        // All blocks filtered out
    SCORE_BELOW_THRESHOLD,      // Best score < 0.35 (SRS §6.1: low confidence)
    NORMALIZATION_EMPTY,        // Normalized result was empty string
    NOT_A_BOOK_OBJECT,          // OCR region is not a book (wrong spatial layout)
    INVALID_BOOK_TITLE          // Text is not a valid book title (spam/random)
}