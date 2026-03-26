package com.royal.insightlens.engine

/**
 * SubtitleFilter — Detects and filters subtitles, taglines, and secondary text
 *
 * Book covers typically have:
 * - Main Title (largest, top-center)
 * - Subtitle/Tagline (smaller, below main title)
 * - Author name (often at bottom)
 * - Publisher/series info (small text)
 *
 * This filter helps identify when a candidate is likely a subtitle rather than
 * the main title, allowing the scorer to prioritize main titles.
 */
object SubtitleFilter {

    // Common subtitle/tagline keywords
    private val SUBTITLE_KEYWORDS = setOf(
        "a", "an", "the",  // Articles before subtitles
        "and", "or", "&",   // Connectives
        "from", "to", "in", "on", "at", "by"  // Prepositions
    )

    // Common tagline patterns
    private val TAGLINE_PATTERNS = setOf(
        "coming soon",
        "new edition",
        "limited edition",
        "special edition",
        "anniversary edition",
        "now a major motion picture",
        "soon to be a major motion picture",
        "based on a true story",
        "international bestseller",
        "number one bestseller",
        "book", "novel", "series"
    )

    // Common author byline prefixes
    private val AUTHOR_PREFIXES = setOf(
        "by", "author", "from", "written by"
    )

    /**
     * Determine if a candidate is likely a subtitle/tagline rather than main title.
     * Higher score = more likely to be subtitle (should be deprioritized)
     */
    fun scoreAsSubtitle(candidate: TitleCandidate): Float {
        val text = candidate.text.lowercase()
        var score = 0f

        // Check 1: Very short text (< 4 words) is often a tagline or author name
        val wordCount = text.split(Regex("[\\s\\-_]+")).filter { it.isNotBlank() }.size
        if (wordCount < 4) score += 0.2f
        if (wordCount < 2) score += 0.35f  // Single word — very likely author/tagline

        // Check 2: Starts with article or preposition (common in subtitles)
        val words = text.split(Regex("[\\s\\-_]+")).filter { it.isNotBlank() }
        val firstWord = words.firstOrNull()?.lowercase() ?: ""
        if (firstWord in SUBTITLE_KEYWORDS) score += 0.25f

        // Check 2b: Starts with author prefixes (likely author name)
        if (firstWord in AUTHOR_PREFIXES) score += 0.30f

        // Check 3: Contains tagline keywords
        for (keyword in TAGLINE_PATTERNS) {
            if (text.contains(keyword)) {
                score += 0.35f
                break
            }
        }

        // Check 4: Very small relative to image (likely byline, not main title)
        val relativeHeight = candidate.boundingBox.height.toFloat() / candidate.imageHeight
        if (relativeHeight < 0.025f) score += 0.25f
        if (relativeHeight < 0.015f) score += 0.25f  // Extra penalty for tiny text

        // Check 5: Text is mostly uppercase AND very short (common for emphasizing taglines)
        if (text == text.uppercase() && wordCount <= 3) score += 0.15f

        // Check 6: Likely author name pattern (two capital words, 1-2 words total)
        if (wordCount <= 2 && looksLikeAuthorName(candidate.text)) {
            score += 0.30f
        }

        return score.coerceIn(0f, 1f)
    }

    /**
     * Detect if text looks like an author name rather than a book title.
     */
    private fun looksLikeAuthorName(text: String): Boolean {
        val words = text.trim().split(Regex("[\\s\\-_]+")).filter { it.isNotBlank() }
        
        // Author names: 1-2 words, usually capitalized
        if (words.size > 2) return false
        
        // Check if words look like proper names (start with capital, rest lowercase)
        val looksLikeProperNames = words.all { word ->
            word.length >= 2 && word[0].isUpperCase()
        }
        
        return looksLikeProperNames
    }

    /**
     * Determine if a candidate is likely the main title.
     * Returns true if it has characteristics of a primary title.
     */
    fun isLikelyMainTitle(candidate: TitleCandidate): Boolean {
        val subtitleScore = scoreAsSubtitle(candidate)
        
        // If it scores high as a subtitle, it's not main title
        if (subtitleScore >= 0.5f) return false

        // Main titles usually have:
        val text = candidate.text
        val wordCount = text.split(Regex("[\\s\\-_]+")).filter { it.isNotBlank() }.size
        
        // At least 2 meaningful words minimum (better to have 3+)
        if (wordCount < 2) return false

        // Reasonable length (3-80 characters typical for book titles)
        if (text.length < 3 || text.length > 80) return false

        // Not all uppercase (unless it's a 2-3 word title like "THE HOBBIT")
        val allCaps = text == text.uppercase() && text != text.lowercase()
        if (allCaps && wordCount <= 2) return false
        
        // Not just a single proper name (author names look like this)
        if (looksLikeAuthorName(text)) return false

        return true
    }

    /**
     * Filter candidates to remove likely subtitles/taglines.
     * If all candidates would be filtered, returns the best remaining one.
     */
    fun filterSubtitles(candidates: List<TitleCandidate>): List<TitleCandidate> {
        if (candidates.isEmpty()) return candidates

        val validMain = candidates.filter { isLikelyMainTitle(it) }
        
        // If we found good candidates, return them
        if (validMain.isNotEmpty()) return validMain

        // Otherwise return all (better to search than reject everything)
        return candidates
    }

    /**
     * Apply subtitle penalty to candidate score.
     * Main scorer can use this to deprioritize likely subtitles.
     */
    fun applySubtitlePenalty(baseScore: Float, candidate: TitleCandidate): Float {
        val subtitleScore = scoreAsSubtitle(candidate)
        // Reduce score by up to 40% if it looks like a subtitle
        val penalty = subtitleScore * 0.4f
        return (baseScore - penalty).coerceIn(0f, 1f)
    }
}
