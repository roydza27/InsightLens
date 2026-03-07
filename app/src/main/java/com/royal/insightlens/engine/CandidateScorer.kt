package com.royal.insightlens.engine

/**
 * CandidateScorer — SRS §3.3
 *
 * Applies weighted scoring formula:
 *
 *   Score =
 *     (SizeWeight     × 0.4) +
 *     (PositionWeight × 0.2) +
 *     (CapWeight      × 0.2) +
 *     (OCRConfidence  × 0.2)
 *
 * Returns the highest-scoring candidate.
 */
object CandidateScorer {

    // Weights from SRS
    private const val WEIGHT_SIZE        = 0.4f
    private const val WEIGHT_POSITION    = 0.2f
    private const val WEIGHT_CAPITALIZATION = 0.2f
    private const val WEIGHT_CONFIDENCE  = 0.2f

    // Minimum total score to be considered a valid title (0.0 - 1.0)
    const val MINIMUM_SCORE_THRESHOLD = 0.35f

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

        return (sizeScore     * WEIGHT_SIZE) +
                (positionScore * WEIGHT_POSITION) +
                (capScore      * WEIGHT_CAPITALIZATION) +
                (confScore     * WEIGHT_CONFIDENCE)
    }

    /**
     * SizeWeight — larger text = more likely to be the title.
     *
     * Normalizes bounding box height relative to image height.
     * Title text is typically 5-20% of image height.
     */
    private fun computeSizeScore(candidate: TitleCandidate): Float {
        if (candidate.imageHeight <= 0) return 0f

        val relativeHeight = candidate.boundingBox.height.toFloat() / candidate.imageHeight

        return when {
            relativeHeight >= 0.15f -> 1.0f   // Very large — strong title
            relativeHeight >= 0.08f -> 0.85f  // Large
            relativeHeight >= 0.04f -> 0.65f  // Medium — possible title
            relativeHeight >= 0.02f -> 0.40f  // Small
            else                    -> 0.10f  // Tiny — likely noise
        }
    }

    /**
     * PositionWeight — text in the upper-center is most likely the title.
     *
     * Book titles typically appear in the top 60% of the cover,
     * horizontally centered.
     */
    private fun computePositionScore(candidate: TitleCandidate): Float {
        if (candidate.imageWidth <= 0 || candidate.imageHeight <= 0) return 0f

        val relativeY = candidate.boundingBox.centerY / candidate.imageHeight
        val relativeX = candidate.boundingBox.centerX / candidate.imageWidth

        // Vertical score: top-center is best (bias toward upper region)
        val verticalScore = when {
            relativeY <= 0.25f -> 1.0f   // Top quarter — very likely title
            relativeY <= 0.45f -> 0.85f  // Upper half
            relativeY <= 0.60f -> 0.65f  // Middle
            relativeY <= 0.75f -> 0.40f  // Lower middle
            else               -> 0.15f  // Bottom — likely author/publisher
        }

        // Horizontal score: center-aligned titles score higher
        val distFromCenter = Math.abs(relativeX - 0.5f) * 2f // 0 = center, 1 = edge
        val horizontalScore = 1f - (distFromCenter * 0.5f)    // max penalty = 0.5

        return (verticalScore * 0.7f) + (horizontalScore * 0.3f)
    }

    /**
     * CapitalizationWeight — title case or ALL CAPS is common for book titles.
     *
     * Checks ratio of words that are properly capitalized.
     */
    private fun computeCapitalizationScore(candidate: TitleCandidate): Float {
        val words = candidate.text.trim().split(Regex("\\s+"))
        if (words.isEmpty()) return 0f

        val allCaps       = candidate.text == candidate.text.uppercase()
        val capitalizedCount = words.count { word ->
            word.isNotEmpty() && word[0].isUpperCase()
        }
        val capitalizedRatio = capitalizedCount.toFloat() / words.size

        return when {
            allCaps              -> 0.95f  // ALL CAPS — very common for titles
            capitalizedRatio >= 0.75f -> 0.90f  // Most words capitalized
            capitalizedRatio >= 0.50f -> 0.70f  // Half capitalized
            capitalizedRatio >= 0.25f -> 0.45f  // Some capitalized
            else                      -> 0.20f  // Mostly lowercase
        }
    }
}