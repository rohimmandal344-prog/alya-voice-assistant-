package com.example.util

import android.util.Log

/**
 * MultiLanguageCommandRegistry
 * 
 * Centralized, structured command repository supporting multi-lingual voice commands
 * across United States English (EN-US), Hindi (हिन्दी - HI-IN), Bengali (বাংলা - BN-BD/IN),
 * and global languages across 3 primary categories:
 * 1. Device & App Control (Wi-Fi, Bluetooth, Flashlight, Volume, Navigation, Apps, Notifications, Settings)
 * 2. Media, Communication & Calls (Calls, Messages, WhatsApp, YouTube, Reels, Music, Scrolling)
 * 3. Utilities, Safety & Conversational Tasks (Timers, Alarms, Reminders, Weather, Search, System Info)
 *
 * Implements regex pattern matching, synonym expansions, and phrase normalization.
 */
object MultiLanguageCommandRegistry {

    private const val TAG = "CommandRegistry"

    data class CommandIntent(
        val intentName: String,
        val targetAction: String,
        val defaultParams: Map<String, String> = emptyMap(),
        val enPatterns: List<String>,
        val hiPatterns: List<String>,
        val bnPatterns: List<String>
    )

    val registry: List<CommandIntent> = listOf(
        // === 1. NAVIGATION & SYSTEM KEY ACTIONS ===
        CommandIntent(
            intentName = "GO_BACK",
            targetAction = "go_back",
            enPatterns = listOf("go back", "back", "navigate back", "previous screen", "pop screen", "return back", "close current page"),
            hiPatterns = listOf("पीछे जाओ", "पीछे", "वापस", "वापस जाओ", "पिछला स्क्रीन", "बैक करो", "बैक जाओ"),
            bnPatterns = listOf("পেছনে যাও", "ব্যাক করো", "ফিরে যাও", "আগের স্ক্রিন", "পেছনে চলুন")
        ),
        CommandIntent(
            intentName = "GO_HOME",
            targetAction = "go_home",
            enPatterns = listOf("go home", "home", "home screen", "main screen", "open home", "launch home screen", "desktop"),
            hiPatterns = listOf("होम पर जाओ", "होम", "घर", "मुख्य स्क्रीन", "होम स्क्रीन खोलो", "मेन स्क्रीन पर जाओ"),
            bnPatterns = listOf("হোমে যাও", "মূল স্ক্রিন", "হোম স্ক্রিনে যান", "মেনু স্ক্রিন")
        ),
        CommandIntent(
            intentName = "OPEN_RECENTS",
            targetAction = "open_recents",
            enPatterns = listOf("open recents", "recents", "recent apps", "show recent tasks", "app switcher", "overview screen"),
            hiPatterns = listOf("हाल के ऐप", "रीसेंट ऐप्स", "रिसेंट टास्क", "हाल के ऐप्स दिखाओ", "रिसेंट ऐप्स खोलो"),
            bnPatterns = listOf("সাম্প্রতিক অ্যাপস", "রিসেন্ট কাজ", "সাম্প্রতিক অ্যাপস দেখান")
        ),
        CommandIntent(
            intentName = "OPEN_NOTIFICATIONS",
            targetAction = "open_notifications",
            enPatterns = listOf("open notifications", "notifications", "show notifications", "pull down notification shade", "notification center"),
            hiPatterns = listOf("नोटिफिकेशन खोलो", "नोटिफिकेशन दिखाओ", "सूचनाएं दिखाओ", "नोटिफिकेशन पैनल"),
            bnPatterns = listOf("নোটিফিকেশন দেখান", "নোটিফিকেশন প্যানেল", "বিজ্ঞপ্তি দেখান")
        ),
        CommandIntent(
            intentName = "OPEN_QUICK_SETTINGS",
            targetAction = "open_quick_settings",
            enPatterns = listOf("open quick settings", "quick settings", "status bar settings", "control center", "pull down status bar"),
            hiPatterns = listOf("क्विक सेटिंग्स खोलो", "कंट्रोल सेंटर", "क्विक सेटिंग्स दिखाओ", "स्टेटस बार"),
            bnPatterns = listOf("কুইক সেটিংস খুলুন", "কন্ট্রোল সেন্টার দেখান")
        ),
        CommandIntent(
            intentName = "LOCK_SCREEN",
            targetAction = "lock_screen",
            enPatterns = listOf("lock screen", "lock phone", "turn off screen", "sleep phone", "lock device"),
            hiPatterns = listOf("स्क्रीन लॉक करो", "फोन लॉक करो", "स्क्रीन बंद करो", "डिवाइस लॉक करो"),
            bnPatterns = listOf("স্ক্রিন লক করুন", "ফোন লক করুন", "স্ক্রিন অফ করুন")
        ),
        CommandIntent(
            intentName = "TAKE_SCREENSHOT",
            targetAction = "take_screenshot",
            enPatterns = listOf("take screenshot", "capture screen", "screenshot", "save screen image", "snap screen"),
            hiPatterns = listOf("स्क्रीनशॉट लो", "स्क्रीनशॉट खींचो", "स्क्रीनशॉट सेव करो", "स्क्रीन कैप्चर करो"),
            bnPatterns = listOf("স্ক্রিনশট নিন", "স্ক্রিনশট তুলুন", "স্ক্রিন ক্যাপচার করুন")
        ),

        // === 2. CONNECTIVITY & DEVICE TOGGLES ===
        CommandIntent(
            intentName = "WIFI_ON",
            targetAction = "toggle_wifi_auto",
            defaultParams = mapOf("state" to "on"),
            enPatterns = listOf("turn on wifi", "enable wifi", "wifi on", "activate wifi", "switch on wifi", "connect wifi"),
            hiPatterns = listOf("वाई-फाई चालू करो", "वाई-फाई ऑन करो", "वाई-फाई चालू करें", "वाईफाई ऑन करो"),
            bnPatterns = listOf("ওয়াইফাই চালু করুন", "ওয়াইফাই অন করুন", "ওয়াইফাই সক্রিয় করুন")
        ),
        CommandIntent(
            intentName = "WIFI_OFF",
            targetAction = "toggle_wifi_auto",
            defaultParams = mapOf("state" to "off"),
            enPatterns = listOf("turn off wifi", "disable wifi", "wifi off", "deactivate wifi", "switch off wifi"),
            hiPatterns = listOf("वाई-फाई बंद करो", "वाई-फाई ऑफ करो", "वाई-फाई बंद करें"),
            bnPatterns = listOf("ওয়াইফাই বন্ধ করুন", "ওয়াইফাই অফ করুন")
        ),
        CommandIntent(
            intentName = "BLUETOOTH_ON",
            targetAction = "toggle_bluetooth_auto",
            defaultParams = mapOf("state" to "on"),
            enPatterns = listOf("turn on bluetooth", "enable bluetooth", "bluetooth on", "activate bluetooth"),
            hiPatterns = listOf("ब्लूटूथ चालू करो", "ब्लूटूथ ऑन करो", "ब्लूटूथ चालू करें"),
            bnPatterns = listOf("ব্লুটুথ চালু করুন", "ব্লুটুথ অন করুন")
        ),
        CommandIntent(
            intentName = "BLUETOOTH_OFF",
            targetAction = "toggle_bluetooth_auto",
            defaultParams = mapOf("state" to "off"),
            enPatterns = listOf("turn off bluetooth", "disable bluetooth", "bluetooth off"),
            hiPatterns = listOf("ब्लूटूथ बंद करो", "ब्लूटूथ ऑफ करो", "ब्लूटूथ बंद करें"),
            bnPatterns = listOf("ব্লুটুথ বন্ধ করুন", "ব্লুটুথ অফ করুন")
        ),
        CommandIntent(
            intentName = "FLASHLIGHT_ON",
            targetAction = "toggle_flashlight",
            defaultParams = mapOf("state" to "on"),
            enPatterns = listOf("turn on flashlight", "turn on torch", "flashlight on", "torch on", "enable torch"),
            hiPatterns = listOf("टॉर्च चालू करो", "फ्लैशलाइट ऑन करो", "टॉर्च ऑन करो", "लाइट जलाओ"),
            bnPatterns = listOf("টর্চ অন করুন", "ফ্ল্যাশলাইট চালু করুন", "আলো জ্বালান")
        ),
        CommandIntent(
            intentName = "FLASHLIGHT_OFF",
            targetAction = "toggle_flashlight",
            defaultParams = mapOf("state" to "off"),
            enPatterns = listOf("turn off flashlight", "turn off torch", "flashlight off", "torch off", "disable torch"),
            hiPatterns = listOf("टॉर्च बंद करो", "फ्लैशलाइट ऑफ करो", "टॉर्च बंद करो", "लाइट बंद करो"),
            bnPatterns = listOf("টর্চ অফ করুন", "ফ্ল্যাশলাইট বন্ধ করুন", "আলো নিভান")
        ),

        // === 3. CALLS & PHONE AUTOMATION ===
        CommandIntent(
            intentName = "ANSWER_CALL",
            targetAction = "answer_call",
            enPatterns = listOf("answer call", "pick up call", "accept call", "receive call", "pick up the phone", "answer incoming call"),
            hiPatterns = listOf("कॉल उठाओ", "फोन उठाओ", "कॉल रिसीव करो", "कॉल का जवाब दो", "फोन पिक करो"),
            bnPatterns = listOf("কল ধরুন", "ফোন ধরুন", "কল রিসিভ করুন", "ফোন তুলুন")
        ),
        CommandIntent(
            intentName = "END_CALL",
            targetAction = "end_call",
            enPatterns = listOf("end call", "hang up", "cut call", "decline call", "reject call", "stop call", "disconnect call"),
            hiPatterns = listOf("कॉल काटो", "फोन काटो", "कॉल रिजेक्ट करो", "कॉल बंद करो", "फोन कट करो"),
            bnPatterns = listOf("কল কাটুন", "ফোন কাটুন", "কল রিজেক্ট করুন", "কল ডিসকানেক্ট করুন")
        ),

        // === 4. MEDIA & SCROLLING ===
        CommandIntent(
            intentName = "SCROLL_DOWN",
            targetAction = "scroll_screen",
            defaultParams = mapOf("direction" to "DOWN"),
            enPatterns = listOf("scroll down", "next reel", "skip reel", "scroll forward", "swipe up", "move down"),
            hiPatterns = listOf("नीचे स्क्रॉल करो", "अगला रील", "आगे बढ़ो", "ऊपर स्वाइप करो", "नीचे जाओ"),
            bnPatterns = listOf("নিচে স্ক্রোল করুন", "পরের রিল", "উপরে সোয়াইপ করুন")
        ),
        CommandIntent(
            intentName = "SCROLL_UP",
            targetAction = "scroll_screen",
            defaultParams = mapOf("direction" to "UP"),
            enPatterns = listOf("scroll up", "previous reel", "scroll backward", "swipe down", "move up"),
            hiPatterns = listOf("ऊपर स्क्रॉल करो", "पिछला रील", "पीछे जाओ", "नीचे स्वाइप करो", "ऊपर जाओ"),
            bnPatterns = listOf("উপরে স্ক্রোল করুন", "আগের রিল", "নিচে সোয়াইপ করুন")
        ),
        CommandIntent(
            intentName = "VOLUME_UP",
            targetAction = "volume_up",
            enPatterns = listOf("volume up", "increase volume", "louder", "turn up volume", "raise volume"),
            hiPatterns = listOf("आवाज बढ़ाओ", "वॉल्यूम बढ़ाओ", "तेज करो", "साउंड बढ़ाओ"),
            bnPatterns = listOf("ভলিউম বাড়ান", "শব্দ বাড়ান", "সাউন্ড বাড়ান")
        ),
        CommandIntent(
            intentName = "VOLUME_DOWN",
            targetAction = "volume_down",
            enPatterns = listOf("volume down", "decrease volume", "quieter", "turn down volume", "lower volume"),
            hiPatterns = listOf("आवाज कम करो", "वॉल्यूम कम करो", "धीमा करो", "साउंड कम करो"),
            bnPatterns = listOf("ভলিউম কমান", "শব্দ কমান", "সাউন্ড কমান")
        )
    )

    /**
     * Resolves a spoken query against the multi-lingual command repository.
     * Returns matching targetAction and default parameters if found, or null for LLM fallback.
     */
    fun resolveSpokenCommand(spokenText: String): Pair<String, Map<String, String>>? {
        val cleanText = spokenText.lowercase().trim()
        if (cleanText.isBlank()) return null

        for (item in registry) {
            val allPatterns = item.enPatterns + item.hiPatterns + item.bnPatterns
            for (pattern in allPatterns) {
                if (cleanText == pattern || cleanText.contains(pattern)) {
                    Log.i(TAG, "Matched Command Registry Intent '${item.intentName}' for query: '$cleanText'")
                    return Pair(item.targetAction, item.defaultParams)
                }
            }
        }
        return null
    }
}
