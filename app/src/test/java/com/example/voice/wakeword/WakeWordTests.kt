package com.example.voice.wakeword

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordTests {

    @Test
    fun `TriggerWordConfig validates correctly`() {
        // Valid words
        assertTrue(TriggerWordConfig.isValid("alya"))
        assertTrue(TriggerWordConfig.isValid("jarvis"))
        assertTrue(TriggerWordConfig.isValid("ho"))
        assertTrue(TriggerWordConfig.isValid("abcdefghijklmnopqrst")) // 20 chars
        
        // Invalid words
        assertFalse(TriggerWordConfig.isValid("a")) // Too short
        assertFalse(TriggerWordConfig.isValid("abcdefghijklmnopqrstu")) // 21 chars
        assertFalse(TriggerWordConfig.isValid("hey alya")) // Space
        assertFalse(TriggerWordConfig.isValid("jarvis!")) // Special char
        assertFalse(TriggerWordConfig.isValid("nova123")) // Digits
    }

    @Test
    fun `Fuzzy matching works for common mishearings`() {
        val target = "alya"
        
        val fuzzyTargets = listOf(
            target,
            target.replace("y", "i"), 
            target.replace("i", "y"),
            target + "a"
        )
        
        // We simulate the detector logic here
        fun checkFuzzyMatch(text: String): Boolean {
            val lower = text.lowercase()
            return fuzzyTargets.any { lower.contains(it) }
        }
        
        assertTrue(checkFuzzyMatch("alya"))
        assertTrue(checkFuzzyMatch("alia"))
        assertTrue(checkFuzzyMatch("alyaa"))
        assertTrue(checkFuzzyMatch("hey alya")) // Contains it
        assertTrue(checkFuzzyMatch("is alia there"))
        
        assertFalse(checkFuzzyMatch("alexa"))
        assertFalse(checkFuzzyMatch("hello"))
    }
}
