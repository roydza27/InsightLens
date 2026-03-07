package com.royal.insightlens.engine

/**
 * Normalizer — SRS §3.3
 *
 * Prevents duplicate DB entries like:
 *   "Atomic Habits"
 *   "Atomic Habit"
 *   "Atomic Habits – James Clear"
 *
 * All producing the same normalizedTitle = "atomic habits"
 */
object Normalizer {

    // Common author name connectors that appear on book covers
    private val AUTHOR_CONNECTORS = listOf(
        " by ", " - ", " – ", " — ", " | "
    )

    // Trailing noise words that sometimes appear on covers
    private val TRAILING_NOISE = listOf(
        "a novel", "a memoir", "an autobiography",
        "the novel", "the memoir", "revised edition",
        "new edition", "special edition", "illustrated edition",
        "expanded edition", "updated edition"
    )

    /**
     * Normalizes a raw OCR title string into a stable key for DB dedup.
     *
     * Steps:
     * 1. Remove trailing author name (everything after connector)
     * 2. Lowercase
     * 3. Remove punctuation (keep alphanumeric + spaces)
     * 4. Remove trailing noise phrases
     * 5. Collapse multiple spaces
     * 6. Trim
     */
    fun normalize(raw: String): String {
        var result = raw

        // Step 1: Strip author name if connector found
        for (connector in AUTHOR_CONNECTORS) {
            val idx = result.indexOf(connector, ignoreCase = true)
            if (idx > 0) {
                result = result.substring(0, idx)
                break
            }
        }

        // Step 2: Lowercase
        result = result.lowercase()

        // Step 3: Remove punctuation, keep alphanumeric and spaces
        result = result.replace(Regex("[^a-z0-9 ]"), " ")

        // Step 4: Remove trailing noise phrases
        for (noise in TRAILING_NOISE) {
            result = result.removeSuffix(noise).trimEnd()
        }

        // Step 5: Collapse multiple spaces
        result = result.replace(Regex("\\s+"), " ")

        // Step 6: Trim
        return result.trim()
    }

    /**
     * Quick check — are two titles the same after normalization?
     */
    fun areSame(a: String, b: String): Boolean =
        normalize(a) == normalize(b)
}