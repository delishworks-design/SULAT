package com.sulat.ai.writing.assistant

import com.sulat.ai.data.model.PersonalExperience

object SpellChecker {
    // Offline spelling check supporting Filipino/Tagalog and English
    fun checkSpelling(text: String, customDictionary: Set<String> = emptySet()): SpellCheckResult {
        val words = text.split(" ").map { it.lowercase().replace("?", "").replace(".", "").replace(",", "") }
        val misspelled = mutableListOf<String>()
        val suggestions = mutableMapOf<String, List<String>>()

        for (word in words) {
            if (word.isEmpty()) continue
            if (!isValidWord(word, customDictionary)) {
                misspelled.add(word)
                suggestions[word] = generateSuggestions(word)
            }
        }

        return SpellCheckResult(misspelled, suggestions)
    }

    private fun isValidWord(word: String, customDictionary: Set<String>): Boolean {
        // Common English words
        val commonEnglish = setOf(
            "the", "be", "to", "of", "and", "a", "in", "that", "have", "i",
            "it", "for", "not", "on", "with", "he", "as", "you", "do", "at"
        )

        // Common Tagalog words
        val commonTagalog = setOf(
            "ng", "nang", "ka", "ko", "mo", "ni", "da", "to", "ko", "po", "opo"
        )

        // Check custom dictionary first
        if (customDictionary.contains(word)) return true

        // Check common words
        if (commonEnglish.contains(word) || commonTagalog.contains(word)) return true

        // Single characters or very short words are OK
        if (word.length <= 2) return true

        // Check if alphabetic (allow Tagalog/English mixed)
        return word.all { it.isLetter() || it == ' ' || it == '-' }
    }

    private fun generateSuggestions(word: String): List<String> {
        // Simple vowel substitution for Tagalog/English
        val suggestions = mutableListOf<String>()
        val vowels = "aeiou"

        if (word.length < 3) return suggestions

        // Generate simple substitutions
        for (i in word.indices) {
            val chars = word.toCharArray()
            for (v in vowels) {
                chars[i] = v
                suggestions.add(chars.joinToString(""))
            }
        }

        return suggestions.distinct().take(3)
    }

    data class SpellCheckResult(
        val misspelled: List<String>,
        val suggestions: Map<String, List<String>>
    )
}