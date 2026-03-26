package com.royal.insightlens.engine

import com.google.mlkit.vision.text.Text
import kotlin.math.abs

/**
 * BookObjectDetector — Detects if OCR result contains a book object
 *
 * Book detection heuristics:
 * - Rectangular region with book-like aspect ratio
 * - Multiple text blocks at different vertical positions (spine/cover text)
 * - Text blocks arranged in book layout pattern
 * - Significant horizontal/vertical spacing between elements
 * 
 * Prevents detection of:
 * - Single lines of text (posters, signs)
 * - Scattered random text
 * - Very small text (barcodes, fine print)
 */
object BookObjectDetector {

    // ─── Configuration ────────────────────────────────────────────────────────

    private const val MIN_BLOCKS_FOR_DETECTION = 2  // At least 2 text blocks
    private const val MIN_VERTICAL_SPREAD_RATIO = 0.15f  // Blocks spread across 15%+ of height
    private const val BOOK_ASPECT_RATIO_MIN = 0.5f  // Width:Height ratio (e.g., 0.5 = portrait)
    private const val BOOK_ASPECT_RATIO_MAX = 2.0f  // Width:Height ratio (e.g., 2.0 = wide)
    
    // Minimum distance between text blocks to count as separate book elements
    private const val MIN_BLOCK_SEPARATION_PX = 10

    data class BookDetectionResult(
        val isBook: Boolean,
        val confidence: Float,
        val blocksCount: Int,
        val verticalSpread: Float,
        val detectionDetails: String
    )

    /**
     * Main detection entry point.
     * Analyzes OCR result to determine if text comes from a book object.
     */
    fun detectBook(
        ocrResult: Text,
        imageWidth: Int,
        imageHeight: Int
    ): BookDetectionResult {
        val blocks = ocrResult.textBlocks
        
        // Check 1: Minimum number of text blocks
        if (blocks.size < MIN_BLOCKS_FOR_DETECTION) {
            return BookDetectionResult(
                isBook = false,
                confidence = 0f,
                blocksCount = blocks.size,
                verticalSpread = 0f,
                detectionDetails = "Insufficient text blocks (${blocks.size} of $MIN_BLOCKS_FOR_DETECTION)"
            )
        }

        // Check 2: Calculate bounding box dimensions of all text
        val overallBounds = calculateOverallBounds(blocks)
        if (overallBounds == null) {
            return BookDetectionResult(
                isBook = false,
                confidence = 0f,
                blocksCount = blocks.size,
                verticalSpread = 0f,
                detectionDetails = "Could not calculate bounds"
            )
        }

        // Check 3: Verify book-like aspect ratio
        val aspectRatio = calculateAspectRatio(overallBounds, imageWidth, imageHeight)
        if (!isValidBookAspectRatio(aspectRatio)) {
            return BookDetectionResult(
                isBook = false,
                confidence = 0f,
                blocksCount = blocks.size,
                verticalSpread = aspectRatio,
                detectionDetails = "Invalid aspect ratio: $aspectRatio (expected $BOOK_ASPECT_RATIO_MIN-$BOOK_ASPECT_RATIO_MAX)"
            )
        }

        // Check 4: Calculate vertical spread (text distributed across height)
        val verticalSpread = calculateVerticalSpread(blocks, overallBounds)
        if (verticalSpread < MIN_VERTICAL_SPREAD_RATIO) {
            return BookDetectionResult(
                isBook = false,
                confidence = 0.3f,
                blocksCount = blocks.size,
                verticalSpread = verticalSpread,
                detectionDetails = "Low vertical spread: $verticalSpread (min: $MIN_VERTICAL_SPREAD_RATIO)"
            )
        }

        // Check 5: Verify blocks are spatially separated (not just one large block)
        val spatialScore = calculateSpatialSeparation(blocks)
        if (spatialScore < 0.5f) {
            return BookDetectionResult(
                isBook = false,
                confidence = spatialScore * 0.5f,
                blocksCount = blocks.size,
                verticalSpread = verticalSpread,
                detectionDetails = "Poor spatial separation: $spatialScore"
            )
        }

        // All checks passed — compute overall confidence
        val confidence = computeBookConfidence(
            blocksCount = blocks.size,
            verticalSpread = verticalSpread,
            aspectRatio = aspectRatio,
            spatialScore = spatialScore
        )

        return BookDetectionResult(
            isBook = confidence >= 0.6f,  // 60% threshold for book detection
            confidence = confidence,
            blocksCount = blocks.size,
            verticalSpread = verticalSpread,
            detectionDetails = "Book detected with confidence: $confidence"
        )
    }

    /**
     * Calculate the overall bounding box containing all text blocks.
     */
    private fun calculateOverallBounds(blocks: List<Text.TextBlock>): BoundingBox? {
        var minLeft = Int.MAX_VALUE
        var maxRight = Int.MIN_VALUE
        var minTop = Int.MAX_VALUE
        var maxBottom = Int.MIN_VALUE

        for (block in blocks) {
            val box = block.boundingBox ?: continue
            minLeft = minOf(minLeft, box.left)
            maxRight = maxOf(maxRight, box.right)
            minTop = minOf(minTop, box.top)
            maxBottom = maxOf(maxBottom, box.bottom)
        }

        if (minLeft == Int.MAX_VALUE || maxRight == Int.MIN_VALUE ||
            minTop == Int.MAX_VALUE || maxBottom == Int.MIN_VALUE) {
            return null
        }

        return BoundingBox(
            left = minLeft,
            top = minTop,
            right = maxRight,
            bottom = maxBottom
        )
    }

    /**
     * Calculate aspect ratio of the text region.
     * Book covers/spines have specific aspect ratios (portrait: ~0.6, landscape: ~1.6)
     */
    private fun calculateAspectRatio(bounds: BoundingBox, @Suppress("UNUSED_PARAMETER") imageWidth: Int, @Suppress("UNUSED_PARAMETER") imageHeight: Int): Float {
        val regionWidth = bounds.right - bounds.left
        val regionHeight = bounds.bottom - bounds.top

        if (regionHeight == 0) return 0f
        return regionWidth.toFloat() / regionHeight.toFloat()
    }

    /**
     * Check if aspect ratio is consistent with book dimensions.
     */
    private fun isValidBookAspectRatio(ratio: Float): Boolean {
        return ratio in BOOK_ASPECT_RATIO_MIN..BOOK_ASPECT_RATIO_MAX
    }

    /**
     * Calculate how much of the vertical space the text occupies.
     * Books typically have text spread across multiple vertical positions.
     */
    private fun calculateVerticalSpread(blocks: List<Text.TextBlock>, bounds: BoundingBox): Float {
        if (blocks.size < 2) return 0f

        val totalHeight = bounds.bottom - bounds.top
        if (totalHeight == 0) return 0f

        // Find top and bottom blocks
        var topmost = Int.MAX_VALUE
        var bottommost = Int.MIN_VALUE

        for (block in blocks) {
            val box = block.boundingBox ?: continue
            topmost = minOf(topmost, box.top)
            bottommost = maxOf(bottommost, box.bottom)
        }

        if (topmost == Int.MAX_VALUE || bottommost == Int.MIN_VALUE) return 0f

        val spread = (bottommost - topmost).toFloat() / totalHeight
        return spread
    }

    /**
     * Measure spatial separation between blocks.
     * Tightly grouped blocks (all in same area) = lower score.
     * Well-distributed blocks = higher score.
     */
    private fun calculateSpatialSeparation(blocks: List<Text.TextBlock>): Float {
        if (blocks.size < 2) return 0f

        var totalDistance = 0f
        var comparisons = 0

        for (i in blocks.indices) {
            for (j in i + 1 until blocks.size) {
                val box1 = blocks[i].boundingBox ?: continue
                val box2 = blocks[j].boundingBox ?: continue

                val verticalDistance = abs(getCenterY(box1) - getCenterY(box2))
                val horizontalDistance = abs(getCenterX(box1) - getCenterX(box2))
                val distance = kotlin.math.sqrt(
                    (verticalDistance.toFloat() * verticalDistance) +
                    (horizontalDistance.toFloat() * horizontalDistance)
                )

                if (distance >= MIN_BLOCK_SEPARATION_PX) {
                    totalDistance += distance
                    comparisons++
                }
            }
        }

        if (comparisons == 0) return 0f
        
        // Normalize to 0-1 range (deeper analysis based on image size)
        val avgDistance = totalDistance / comparisons
        return (avgDistance / 500f).coerceIn(0f, 1f)  // 500px as reference
    }

    /**
     * Compute overall book detection confidence.
     */
    private fun computeBookConfidence(
        blocksCount: Int,
        verticalSpread: Float,
        aspectRatio: Float,
        spatialScore: Float
    ): Float {
        var confidence = 0f

        // Factor 1: Block count (more blocks = more reliable)
        confidence += minOf(blocksCount.toFloat() / 5f, 0.3f)

        // Factor 2: Vertical spread (distributed text = book-like)
        confidence += verticalSpread * 0.3f

        // Factor 3: Aspect ratio (closer to 1:1 or portrait = book-like)
        val aspectDelta = abs(aspectRatio - 0.7f)  // Ideal for book covers
        confidence += (1f - (aspectDelta / 1.5f)) * 0.2f

        // Factor 4: Spatial separation
        confidence += spatialScore * 0.2f

        return confidence.coerceIn(0f, 1f)
    }

    // Helper class for bounds
    private data class BoundingBox(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )

    // Helper: Get center X coordinate from bounding box
    private fun getCenterX(rect: android.graphics.Rect): Int {
        return (rect.left + rect.right) / 2
    }

    // Helper: Get center Y coordinate from bounding box
    private fun getCenterY(rect: android.graphics.Rect): Int {
        return (rect.top + rect.bottom) / 2
    }
}
