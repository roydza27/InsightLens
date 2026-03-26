package com.royal.insightlens.engine

/**
 * CandidateScorer — SRS §3.3 (Enhanced with stricter thresholds)
 *
 * Applies weighted scoring formula:
 *
 *   Score =
 *     (SizeWeight     × 0.4) +
 *     (PositionWeight × 0.2) +
 *     (CapWeight      × 0.2) +
 *     (OCRConfidence  × 0.2)
 *
 * Enhanced to prevent false positives from random text:
 * - Increased minimum threshold from 0.35 to 0.50
 * - Stricter size requirements for book titles
 * - Better position weighting for typical cover layouts
 * - Capitalization patterns more important for English books
 *
 * Returns the highest-scoring candidate.
 */
object CandidateScorer {

    // Weights from SRS (adjusted for better book detection)
    private const val WEIGHT_SIZE        = 0.25f  // Reduced (size can vary)
    private const val WEIGHT_POSITION    = 0.40f  // Increased significantly (position is most reliable)
    private const val WEIGHT_CAPITALIZATION = 0.20f  // Slightly reduced
    private const val WEIGHT_CONFIDENCE  = 0.15f  // Reduced (OCR can be unreliable)

    // Increased minimum threshold to 0.50 (was 0.35) to filter out weak candidates
    // This prevents random text from being processed
    const val MINIMUM_SCORE_THRESHOLD = 0.50f

    /**
     * Scores all candidates and returns them sorted by score descending.
     */
    fun score(candidates: List<TitleCandidate>): List<TitleCandidate> {
        if (candidates.isEmpty()) return emptyList()

        return candidates
            .map { candidate ->
                candidate.score = computeScore(candidate)
                candidate
            }
            .sortedByDescending { it.score }
    }

    /**
     * Returns the best candidate if its score exceeds the threshold.
     * Returns null if no candidate is confident enough.
     */
    fun pickBest(candidates: List<TitleCandidate>): TitleCandidate? {
        val scored = score(candidates)
        val best = scored.firstOrNull() ?: return null
        return if (best.score >= MINIMUM_SCORE_THRESHOLD) best else null
    }

    // ─── Score computation ────────────────────────────────────────────────

    private fun computeScore(candidate: TitleCandidate): Float {
        val sizeScore     = computeSizeScore(candidate)
        val positionScore = computePositionScore(candidate)
        val capScore      = computeCapitalizationScore(candidate)
        val confScore     = candidate.ocrConfidence.coerceIn(0f, 1f)

        val baseScore = (sizeScore     * WEIGHT_SIZE) +
                (positionScore * WEIGHT_POSITION) +
                (capScore      * WEIGHT_CAPITALIZATION) +
                (confScore     * WEIGHT_CONFIDENCE)

        // Apply subtitle penalty to deprioritize taglines and subtitles
        return SubtitleFilter.applySubtitlePenalty(baseScore, candidate)
    }

    /**
     * SizeWeight — larger text = more likely to be the title.
     *
     * REVISED: Main titles are typically among the LARGEST text on the cover
     * - Bylines and headers are often small
     * - Book title is usually 8-15% of image height
     * - Strongly penalize anything below 5% (likely byline/tagline)
     *
     * Normalizes bounding box height relative to image height.
     */
    private fun computeSizeScore(candidate: TitleCandidate): Float {
        if (candidate.imageHeight <= 0) return 0f

        val relativeHeight = candidate.boundingBox.height.toFloat() / candidate.imageHeight

        return when {
            relativeHeight >= 0.20f -> 0.75f  // Very large — likely cover art or big title
            relativeHeight >= 0.15f -> 1.0f   // Ideal title size
            relativeHeight >= 0.08f -> 0.98f  // Excellent title size
            relativeHeight >= 0.05f -> 0.70f  // Medium — possible, but small for main title
            relativeHeight >= 0.02f -> 0.30f  // Small — likely byline/tagline
            else                    -> 0.0f   // Tiny — definitely not title
        }
    }

    /**
     * PositionWeight — text in the CENTER of cover is most likely the title.
     *
     * Enhanced heuristics (REVISED):
     * - Book titles typically in CENTER (40-60% from top)
     * - Top region (0-30%) = author name, publisher, byline
     * - Bottom region (70%+) = author/publisher name
     * - Centered horizontally (not aligned to edges)
     */
    private fun computePositionScore(candidate: TitleCandidate): Float {
        if (candidate.imageWidth <= 0 || candidate.imageHeight <= 0) return 0f

        val relativeY = candidate.boundingBox.centerY / candidate.imageHeight
        val relativeX = candidate.boundingBox.centerX / candidate.imageWidth

        // Vertical score: prefer CENTER region (where main titles are)
        val verticalScore = when {
            relativeY <= 0.15f -> 0.40f  // Very top — likely byline/author
            relativeY <= 0.30f -> 0.65f  // Upper region — possible subtitle
            relativeY <= 0.45f -> 1.0f   // IDEAL — main title zone
            relativeY <= 0.60f -> 0.95f  // Center region — common title position
            relativeY <= 0.75f -> 0.60f  // Lower-middle — might be subtitle
            else               -> 0.15f  // Bottom — author/publisher name
        }

        // Horizontal score: center-aligned titles score higher
        val distFromCenter = Math.abs(relativeX - 0.5f) * 2f // 0 = center, 1 = edge
        val horizontalScore = 1f - (distFromCenter * 0.3f)    // max penalty = 0.3

        return (verticalScore * 0.75f) + (horizontalScore * 0.25f)
    }

    /**
     * CapitalizationWeight — title case or ALL CAPS is common for book titles.
     *
     * Enhanced: More weight on proper title case (common English pattern).
     *
     * Checks ratio of words that are properly capitalized.
     */
    private fun computeCapitalizationScore(candidate: TitleCandidate): Float {
        val words = candidate.text.trim().split(Regex("\\s+"))
        if (words.isEmpty()) return 0f

        val allCaps = candidate.text == candidate.text.uppercase() && candidate.text != candidate.text.lowercase()
        val capitalizedCount = words.count { word ->
            word.isNotEmpty() && word[0].isUpperCase()
        }
        val capitalizedRatio = capitalizedCount.toFloat() / words.size

        return when {
            allCaps              -> 0.92f  // ALL CAPS — very common for titles (but not all-lowercase)
            capitalizedRatio >= 0.80f -> 0.95f  // Title Case — very likely
            capitalizedRatio >= 0.60f -> 0.75f  // Most words capitalized
            capitalizedRatio >= 0.40f -> 0.50f  // Some capitalized
            capitalizedRatio >= 0.20f -> 0.30f  // Few capitalized
            else                      -> 0.10f  // All lowercase — unlikely for book title
        }
    }
}