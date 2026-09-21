package com.example.data.ai

enum class EmotionCue(val label: String, val toneAdvice: String) {
    HAPPY("Happy", "Respond with upbeat warmth and shared enthusiasm."),
    SAD("Sad", "Respond with gentle kindness, patience, and empathetic understanding."),
    CONFUSED("Confused", "Explain simply, step-by-step, avoiding jargon."),
    FRUSTRATED("Frustrated", "Stay calm, direct, and focus immediately on solving the problem."),
    EXCITED("Excited", "Match their positive energy and dynamic pace."),
    CALM("Calm", "Maintain a balanced, attentive, and smooth conversation."),
    CURIOUS("Curious", "Provide engaging, insightful, and intriguing details."),
    HESITANT("Hesitant", "Offer reassuring clarity and gentle encouragement.")
}

object EmotionDetector {

    fun detectEmotion(text: String): EmotionCue {
        val lower = text.lowercase().trim()

        return when {
            lower.contains("frustrated") || lower.contains("angry") || lower.contains("hate this") ||
                    lower.contains("annoying") || lower.contains("doesn't work") || lower.contains("ugh") ||
                    lower.contains("stop doing that") || lower.contains("stupid") -> EmotionCue.FRUSTRATED

            lower.contains("confused") || lower.contains("don't understand") || lower.contains("what do you mean") ||
                    lower.contains("huh") || lower.contains("wait what") || lower.contains("how does") -> EmotionCue.CONFUSED

            lower.contains("sad") || lower.contains("depressed") || lower.contains("unhappy") ||
                    lower.contains("bad day") || lower.contains("crying") || lower.contains("heartbroken") -> EmotionCue.SAD

            lower.contains("awesome") || lower.contains("yay") || lower.contains("can't wait") ||
                    lower.contains("so excited") || lower.contains("hurray") || lower.contains("incredible") -> EmotionCue.EXCITED

            lower.contains("happy") || lower.contains("great") || lower.contains("love") ||
                    lower.contains("thank you") || lower.contains("glad") || lower.contains("wonderful") -> EmotionCue.HAPPY

            lower.contains("why") || lower.contains("tell me more") || lower.contains("curious") ||
                    lower.contains("wondering") || lower.contains("how come") || lower.contains("explain") -> EmotionCue.CURIOUS

            lower.contains("um") || lower.contains("maybe") || lower.contains("not sure") ||
                    lower.contains("i think so") || lower.contains("i guess") -> EmotionCue.HESITANT

            else -> EmotionCue.CALM
        }
    }
}
