package com.sulat.ai.writing.check

import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.writing.assistant.GrammarChecker
import com.sulat.ai.writing.assistant.SpellChecker

object WritingCheck {
    // Perform comprehensive writing check
    fun checkLetter(draft: LetterDraft): CheckResult {
        // 1. Spelling check
        val spellResult = SpellChecker.checkSpelling(draft.body)

        // 2. Grammar check
        val grammarResult = GrammarChecker.checkGrammar(draft.body)

        // 3. Summarize issues
        val spellingIssues = spellResult.misspelled.joinToString(", ") : "none"
        val grammarSuggestions = grammarResult.issues.joinToString("\n") : "none"

        // 4. Calculate confidence
        val highConfidenceCorrections = spellResult.misspelled.size
        val uncertainCorrections = 0 // Would be calculated in full implementation

        return CheckResult(
            spellingIssues = spellingIssues,
            grammarSuggestions = grammarSuggestions,
            punctuationSuggestions = "Check punctuation consistency",
            capitalizationSuggestions = "Ensure proper sentence capitalization",
            highConfidenceCorrections = highConfidenceCorrections,
            uncertainCorrections = uncertainCorrections,
            applyAllSafe = applySafeCorrections(spellResult, grammarResult)
        )
    }

    // Apply safe corrections (high confidence only)
    private fun applySafeCorrections(
        spellResult: SpellChecker.SpellCheckResult,
        grammarResult: GrammarChecker.GrammarCheckResult
    ): Boolean {
        // Only apply corrections with high confidence
        // In full implementation, would have confidence scoring
        return spellResult.misspelled.isNotEmpty() && grammarResult.issues.isNotEmpty()
    }

    data class CheckResult(
        val spellingIssues: String,
        val grammarSuggestions: String,
        val punctuationSuggestions: String,
        val capitalizationSuggestions: String,
        val highConfidenceCorrections: Int,
        val uncertainCorrections: Int,
        val applyAllSafe: Boolean
    )
}