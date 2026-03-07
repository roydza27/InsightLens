package com.royal.insightlens.engine

import com.google.mlkit.vision.text.Text

/**
 * CandidateBuilder — SRS §3.3
 *
 * Responsibilities:
 * - Merge vertically aligned OCR text blocks
 * - Remove stop words (Edition, Volume, ISBN, etc.)
 * - Filter tiny bounding boxes (noise)
 * - Construct phrase candidates for scoring
 */
object CandidateBuilder {

    // Words commonly found on book covers that are NOT the title
    private val STOP_WORDS = setOf(
        "edition", "volume", "vol", "isbn", "revised", "updated",
        "illustrated", "paperback", "hardcover", "reprint",
        "bestseller", "bestselling", "international", "national",
        "award", "winner", "prize", "foreword", "introduction",
        "preface", "appendix", "index", "copyright", "rights",
        "reserved", "printed", "published", "publishing", "publishers",
        "press", "books", "inc", "ltd", "llc", "co", "barcode"
    )

    // Minimum bounding box height in pixels to be considered a title candidate
    private const val MIN_BOX_HEIGHT_PX = 20

    // Maximum number of lines to merge vertically into one candidate
    private const val MAX_MERGE_LINES = 4

    // Maximum vertical gap (px) between lines to be considered the same block
    private const val MAX_VERTICAL_GAP_PX = 60

    /**
     * Builds a list of [TitleCandidate] from raw ML Kit OCR result.
     *
     * @param ocrResult The Text object from ML Kit TextRecognizer
     * @param imageWidth  Width of the analyzed image frame
     * @param imageHeight Height of the analyzed image frame
     */
    fun build(
        ocrResult: Text,
        imageWidth: Int,
        imageHeight: Int
    ): List<TitleCandidate> {

        val candidates = mutableListOf<TitleCandidate>()

        for (block in ocrResult.textBlocks) {

            // Filter out tiny blocks (noise, price labels, etc.)
            val blockHeight = block.boundingBox?.height() ?: 0
            if (blockHeight < MIN_BOX_HEIGHT_PX) continue

            // Build candidates from individual lines within block
            val lines = block.lines
            if (lines.isEmpty()) continue

            // Single-line candidate from each meaningful line
            for (line in lines) {
                val lineHeight = line.boundingBox?.height() ?: 0
                if (lineHeight < MIN_BOX_HEIGHT_PX) continue

                val cleaned = cleanText(line.text)
                if (cleaned.isBlank() || cleaned.length < 2) continue

                val box = line.boundingBox ?: continue
                val confidence = line.elements
                    .mapNotNull { it.confidence }
                    .average()
                    .toFloat()
                    .takeIf { !it.isNaN() } ?: 0.5f

                candidates.add(
                    TitleCandidate(
                        text        = cleaned,
                        rawText     = line.text,
                        boundingBox = BoundingRect(
                            left   = box.left,
                            top    = box.top,
                            right  = box.right,
                            bottom = box.bottom
                        ),
                        imageWidth    = imageWidth,
                        imageHeight   = imageHeight,
                        ocrConfidence = confidence,
                        lineCount     = 1
                    )
                )
            }

            // Multi-line merged candidate from the entire block
            if (lines.size in 2..MAX_MERGE_LINES) {
                val mergedText = tryMergeLines(lines)
                if (mergedText != null) {
                    val blockBox = block.boundingBox ?: continue
                    val avgConfidence = lines
                        .flatMap { it.elements }
                        .mapNotNull { it.confidence }
                        .average()
                        .toFloat()
                        .takeIf { !it.isNaN() } ?: 0.5f

                    candidates.add(
                        TitleCandidate(
                            text        = mergedText,
                            rawText     = block.text,
                            boundingBox = BoundingRect(
                                left   = blockBox.left,
                                top    = blockBox.top,
                                right  = blockBox.right,
                                bottom = blockBox.bottom
                            ),
                            imageWidth    = imageWidth,
                            imageHeight   = imageHeight,
                            ocrConfidence = avgConfidence,
                            lineCount     = lines.size
                        )
                    )
                }
            }
        }

        return candidates
    }

    /**
     * Tries to merge multiple OCR lines into one title phrase.
     * Checks that lines are vertically close enough to be the same title.
     */
    private fun tryMergeLines(lines: List<Text.Line>): String? {
        val cleaned = mutableListOf<String>()

        for (i in lines.indices) {
            val line = lines[i]
            val text = cleanText(line.text)
            if (text.isBlank()) continue

            // Check vertical gap with previous line
            if (i > 0) {
                val prevBottom = lines[i - 1].boundingBox?.bottom ?: 0
                val currTop    = line.boundingBox?.top ?: 0
                val gap = currTop - prevBottom
                if (gap > MAX_VERTICAL_GAP_PX) return null // Too far apart
            }

            cleaned.add(text)
        }

        if (cleaned.size < 2) return null
        return cleaned.joinToString(" ")
    }

    /**
     * Removes stop words and normalizes spacing from raw OCR text.
     */
    private fun cleanText(raw: String): String {
        val words = raw.trim().split(Regex("\\s+"))
        val filtered = words.filter { word ->
            word.lowercase() !in STOP_WORDS && word.length > 1
        }
        return filtered.joinToString(" ")
    }
}

// ─── Data Models ───────────────────────────────────────────────────────────

data class TitleCandidate(
    val text: String,
    val rawText: String,
    val boundingBox: BoundingRect,
    val imageWidth: Int,
    val imageHeight: Int,
    val ocrConfidence: Float,
    val lineCount: Int,
    // Score assigned by CandidateScorer
    var score: Float = 0f
)

data class BoundingRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width:  Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}