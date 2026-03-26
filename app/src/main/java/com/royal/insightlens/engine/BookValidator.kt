package com.royal.insightlens.engine

/**
 * BookValidator — Validates if extracted text is a valid book title
 * 
 * Prevents random text, spam, and non-book-related strings from being searched.
 * Applies heuristics to filter:
 * - Too short/long text
 * - Number-only strings
 * - Single words
 * - Common spam patterns
 * - Repetitive characters
 */
object BookValidator {

    // ─── Configuration ────────────────────────────────────────────────────────

    private const val MIN_TITLE_LENGTH = 3        // At least 3 characters
    private const val MAX_TITLE_LENGTH = 150      // Book titles rarely exceed 150 chars
    private const val MIN_MEANINGFUL_WORDS = 1    // At least 1 meaningful word
    private const val MAX_NUMBERS_RATIO = 0.4f    // Max 40% of characters can be digits

    // Common English stop words (articles, prepositions, etc.)
    private val STOP_WORDS = setOf(
        "a", "an", "the", "to", "of", "in", "on", "at", "by", "for",
        "from", "with", "and", "or", "but", "is", "am", "are", "be",
        "been", "being", "have", "has", "do", "does", "did", "will",
        "would", "could", "should", "may", "might", "must", "can",
        "as", "if", "because", "that", "this", "it", "which", "who",
        "what", "where", "when", "why", "how", "all", "each", "every"
    )

    // Common spam/noise patterns
    private val SPAM_KEYWORDS = setOf(
        "price", "cost", "barcode", "isbn", "skip", "next", "back",
        "menu", "settings", "login", "password", "email", "phone",
        "address", "zip", "code", "error", "warning", "alert",
        "copyright", "reserved", "rights", "published", "edition",
        "volume", "issue", "number", "version", "serial"
    )

    /**
     * Main validation entry point.
     * Returns true if the text is likely a valid book title.
     */
    fun isValidBookTitle(normalizedText: String): Boolean {
        if (normalizedText.isBlank()) return false

        // Check 1: Length constraints
        if (normalizedText.length < MIN_TITLE_LENGTH) return false
        if (normalizedText.length > MAX_TITLE_LENGTH) return false

        // Check 2: Contains no meaningful content (only numbers/symbols)
        if (!containsMeaningfulContent(normalizedText)) return false

        // Check 3: Too many digits (likely not a book title)
        if (countDigitRatio(normalizedText) > MAX_NUMBERS_RATIO) return false

        // Check 4: Repetitive characters (spam pattern: "aaaa", "!!!!", etc.)
        if (hasRepetitiveCharacters(normalizedText)) return false

        // Check 5: Contains spam keywords
        if (containsSpamKeywords(normalizedText)) return false

        // Check 6: At least one meaningful word (not all stop words)
        if (!hasMeaningfulWords(normalizedText)) return false

        // Check 7: Not just a single character repeated or symbols
        if (isSingleCharacterPattern(normalizedText)) return false

        return true
    }

    /**
     * Check if text contains at least some letters/meaningful characters.
     */
    private fun containsMeaningfulContent(text: String): Boolean {
        return text.any { it.isLetter() }
    }

    /**
     * Calculate percentage of digits in text.
     */
    private fun countDigitRatio(text: String): Float {
        if (text.isEmpty()) return 0f
        val digitCount = text.count { it.isDigit() }
        return digitCount.toFloat() / text.length
    }

    /**
     * Detect patterns like "AAAA", "!!!!", "1111", etc.
     */
    private fun hasRepetitiveCharacters(text: String): Boolean {
        if (text.length < 4) return false

        // Check for 3+ consecutive identical characters
        for (i in 0 until text.length - 2) {
            if (text[i] == text[i + 1] && text[i] == text[i + 2]) {
                // Allow if it's a hyphen or dash (e.g., "---" in "The---Book")
                if (text[i] !in setOf('-', '–', '—', ' ')) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Check if text contains known spam keywords.
     */
    private fun containsSpamKeywords(text: String): Boolean {
        val lowerText = text.lowercase()
        return SPAM_KEYWORDS.any { keyword ->
            lowerText.contains(keyword)
        }
    }

    /**
     * Ensure at least one meaningful (non-stop) word exists.
     */
    private fun hasMeaningfulWords(text: String): Boolean {
        val words = text.split(Regex("[\\s\\-_.,;:!?()\\[\\]{}]+"))
            .filter { it.isNotBlank() }
            .map { it.lowercase() }

        if (words.isEmpty()) return false

        // Check if there's at least one non-stop word
        val meaningfulWords = words.filterNot { it in STOP_WORDS }
        return meaningfulWords.isNotEmpty()
    }

    /**
     * Detect single-character repeated pattern or pure symbols.
     * Examples: "AAAAAAA", ".......", "!!!!!!!"
     */
    private fun isSingleCharacterPattern(text: String): Boolean {
        if (text.length < 3) return false

        val uniqueChars = text.filter { !it.isWhitespace() }.toList().distinct()
        
        // If only 1 unique non-whitespace character, it's likely spam
        if (uniqueChars.size == 1) {
            val char = uniqueChars.first()
            // Exception: allow letters if they form a word
            return !char.isLetter()
        }

        return false
    }

    /**
     * Additional check: validate detected title against expected book patterns.
     * Tests for characteristics typical of real book titles.
     */
    fun scoreBookLikelihood(text: String): Float {
        var score = 0.8f // Start with baseline confidence

        // Bonus: Title has multiple words (typical for books)
        val wordCount = text.split(Regex("[\\s\\-_.,;:!?()\\[\\]{}]+"))
            .filter { it.isNotBlank() }
            .size
        if (wordCount >= 2) score += 0.1f
        if (wordCount >= 4) score += 0.05f

        // Bonus: Contains capital letters (title case)
        if (text.any { it.isUpperCase() }) score += 0.05f

        // Penalty: Too many symbols
        val symbolRatio = text.count { !it.isLetterOrDigit() && !it.isWhitespace() }
            .toFloat() / text.length
        if (symbolRatio > 0.3f) score -= 0.2f

        return score.coerceIn(0f, 1f)
    }
}
