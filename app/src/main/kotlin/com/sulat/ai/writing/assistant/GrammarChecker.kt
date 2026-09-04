package com.sulat.ai.writing.assistant

import com.sulat.ai.data.model.PersonalExperience

object GrammarChecker {
    // Offline grammar check supporting Filipino/Tagalog and English
    fun checkGrammar(text: String): GrammarCheckResult {
        val issues = mutableListOf<GrammarIssue>()
        val lines = text.lines()

        for (i in lines.indices) {
            val line = lines[i]
            val sentences = line.split(".").map { it.trim() }.filter { it.isNotEmpty() }

            for (sentence in sentences) {
                checkSentence(sentence, i + 1, issues)
            }
        }

        return GrammarCheckResult(issues)
    }

    private fun checkSentence(sentence: String, lineNumber: Int, issues: MutableList<GrammarIssue>) {
        // Check for repeated words
        val words = sentence.split(" ").map { it.lowercase() }.filter { it.isNotEmpty() }
        var prevWord = ""
        var repeatCount = 0

        for (word in words) {
            if (word == prevWord) {
                repeatCount++
                if (repeatCount >= 2) {
                    issues.add(GrammarIssue(
                        lineNumber,
                        "Repeated word: '$word'",
                        "Replace or rephrase",
                        "low"
                    ))
                }
            } else {
                prevWord = word
                repeatCount = 0
            }
        }

        // Check for capitalization at start of sentence
        if (sentence.isNotEmpty()) {
            val firstChar = sentence.first()
            if (firstChar.isLowerCase() && sentence.length > 1) {
                issues.add(GrammarIssue(
                    lineNumber,
                    "Sentence should start with capital letter",
                    "Capitalize first letter",
                    "low"
                ))
            }
        }

        // Check for missing punctuation
        if (sentence.isNotEmpty() && !sentence.endsWith(".") && !sentence.endsWith("!") && !sentence.endsWith("?")) {
            issues.add(GrammarIssue(
                lineNumber,
                "Missing end punctuation",
                "Add period or appropriate punctuation",
                "low"
            ))
        }

        // Basic agreement check - very simplified
        // Check for "I are" or "she am" patterns (too complex for offline, just flag obvious issues)
        if (sentence.contains(" i are ") || sentence.contains(" she am ") || sentence.contains(" he are ")) {
            issues.add(GrammarIssue(
                lineNumber,
                "Subject-verb agreement issue",
                "Review verb form",
                "medium"
            ))
        }
    }

    data class GrammarIssue(
        val line: Int,
        val description: String,
        val suggestion: String,
        val severity: String
    )

    data class GrammarCheckResult(
        val issues: List<GrammarIssue>
    )
}