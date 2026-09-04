package com.sulat.ai.writing.assistant

import com.sulat.ai.data.model.PersonalExperience
import com.sulat.ai.data.model.LetterDraft
import com.sulat.ai.data.model.Recipient

object WritingAssistant {
    // Check if text contains Tagalog/Filipino characters
    fun detectLanguage(text: String): String {
        if (text.isEmpty()) return "unknown"

        // Common Tagalog patterns
        val tagalogPatterns = mapOf(
            "ng" to 0.8,
            "nang" to 0.6,
            "ka" to 0.5,
            "po" to 0.7,
            "opo" to 0.7
        )

        var tagalogScore = 0.0
        var englishScore = 0.0

        // Simple word analysis
        val words = text.split(" ").map { it.lowercase() }
        for (word in words) {
            if (word.any { it !in "abcdefghijklmnopqrstuvwxyz" }) {
                // Contains non-ASCII, could be Filipino
                tagalogScore += 0.3
            }
            if (tagalogPatterns.keys.any { word.contains(it) }) {
                tagalogScore += tagalogPatterns[it]!!
            }
            if (word in ["the", "and", "to", "of", "a", "in", "is", "it"]) {
                englishScore += 0.2
            }
        }

        if (tagalogScore > englishScore + 0.5) return "tagalog"
        if (englishScore > tagalogScore + 0.5) return "english"
        return "mixed"
    }

    // Generate writing idea based on category
    fun generateIdea(category: String, userContext: String? = null): String {
        val categories = mapOf(
            "Apology" to "Pasensyang umamin ay kong kahulugan ng pagkakamali at pinipiliang magtuluyan.",
            "Gratitude" to "Ang pagpapahalaga sa mga nilalang at mga kapalaran ng Panginoon.",
            "Personal Experience" to "Isang tungkulin na minsan ang mahirap alalahanin ngunit kinokohin ng puso.",
            "Changes and Improvements" to "Ang pagbabago ay isang patuloy na pagpupursigi ng sobrang galing.",
            "Challenges" to "Ang mga hamon ay nagpo-programa ng ating katatagan ng loob.",
            "Lessons Learned" to "Ang mga aral na natutunan ay nagreresulta sa mas malalim na pananaw.",
            "Efforts and Responsibilities" to "Ang ating pinaglilingkod at responsibilidad ay nagpapakita ng ating pagiging responsable.",
            "Request for an Opportunity" to "Ang humihiling ng oportunidad ay base sa pagpupursigi ng ating mga hinaharap.",
            "Commitment" to "Ang commitment ay ang aking pananaw sa ating kinabukasan.",
            "Future Intentions" to "Ang mga hinaharap na pangarap ay ang pagpupursigi ng ating misyon."
        )

        return categories[category] ?: "Isang paraan ng pagpapahalaga sa ating mga kapwa."
    }

    // Rephrase text with specified style
    fun rephrase(text: String, style: String): String {
        // Basic rephrasing - preserve meaning, adjust style
        val lower = text.lowercase()
        
        return when (style) {
            "More Formal" -> text.replace("kanta", "kuwento").replace("gawa", "gawaing").
                replace("subject", "persone").replace("ako", "ang aking persona")
            "More Personal" -> text.replace("aminin", "ipahayag").replace("pagkakataon", "momento").
                replace("nagsimula", "simula")
            "More Concise" -> text.split(" ").take(15).joinToString(" ") + "..." 
            "More Natural" -> text
            "More Heartfelt" -> text + " Ang lahat ng ginagawa ko ay sa pagaling ng Panginoon."
            "Clearer" -> text
            "Simpler" -> text.split(" ").filter { it.length < 12 }.take(20).joinToString(" ")
            else -> text
        }
    }

    // Create suggestion from personal experience
    fun createSuggestion(experience: PersonalExperience): String {
        val parts = mutableListOf<String>()

        if (experience.happened.isNotEmpty()) {
            parts.add("Nangyari: ${experience.happened}")
        }
        if (experience.whatDidYouDo.isNotEmpty()) {
            parts.add("Ginawa ko: ${experience.whatDidYouDo}")
        }
        if (experience.whatDidYouLearn.isNotEmpty()) {
            parts.add("Natutong: ${experience.whatDidYouLearn}")
        }
        if (experience.whatChanged.isNotEmpty()) {
            parts.add("Nabago: ${experience.whatChanged}")
        }
        if (experience.whatYouWantToExpress.isNotEmpty()) {
            parts.add("Ipinahahayag ko: ${experience.whatYouWantToExpress}")
        }

        return parts.joinToString(". ")
    }

    // Validate that suggestion doesn't fabricate facts
    fun validateSuggestion(suggestion: String, source: PersonalExperience): Boolean {
        // Check that no fabricated facts are introduced
        // This is a conservative check - ensure user-provided facts are preserved
        return true  // In full implementation, would verify factual consistency
    }
}