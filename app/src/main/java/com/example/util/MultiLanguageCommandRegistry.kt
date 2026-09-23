package com.example.util

import android.util.Log

/**
 * MultiLanguageCommandRegistry
 * 
 * Centralized, structured command repository supporting multi-lingual voice commands
 * across United States English (EN-US), Hindi (हिन्दी - HI-IN), Hinglish, Bengali (বাংলা - BN-BD/IN),
 * Marathi (मराठी - MR-IN), Telugu, Japanese (日本語 - JA-JP), and global languages across 5 primary categories:
 * 1. Device & Connectivity Controls (Wi-Fi, Bluetooth, Flashlight, Volume, Brightness, Screen Lock, Screenshot)
 * 2. Media, Entertainment & Navigation (Music, Video, YouTube, Shorts, Reels, Scrolling, Next/Prev)
 * 3. Natural Wake-Up vs System Alarm & Timers (Semantic Separation of Voice Wake-Up vs Clock Alarm)
 * 4. Communication & Social (Phone Calls, Answering/Ending, WhatsApp, Contacts, Messages)
 * 5. App Launching, System Utilities & Offline Essentials (Calculator, Camera, Gallery, Storage, Battery, Weather)
 *
 * Implements token normalization, regex matching, and semantic intent mapping.
 */
object MultiLanguageCommandRegistry {

    private const val TAG = "CommandRegistry"

    data class CommandIntent(
        val intentName: String,
        val targetAction: String,
        val defaultParams: Map<String, String> = emptyMap(),
        val enPatterns: List<String>,
        val hiPatterns: List<String>,
        val bnPatterns: List<String>,
        val otherPatterns: List<String> = emptyList()
    )

    val registry: List<CommandIntent> = listOf(
        // === 1. NAVIGATION & SYSTEM KEY ACTIONS ===
        CommandIntent(
            intentName = "GO_BACK",
            targetAction = "go_back",
            enPatterns = listOf("go back", "back", "navigate back", "previous screen", "pop screen", "return back", "close current page", "step back"),
            hiPatterns = listOf("पीछे जाओ", "पीछे", "वापस", "वापस जाओ", "पिछला स्क्रीन", "बैक करो", "बैक जाओ", "peechhe jao", "wapas jao", "back jao"),
            bnPatterns = listOf("পেছনে যাও", "ব্যাক করো", "ফিরে যাও", "আগের স্ক্রিন", "পেছনে চলুন"),
            otherPatterns = listOf("modoru", "usiro", "wapas", "back")
        ),
        CommandIntent(
            intentName = "GO_HOME",
            targetAction = "go_home",
            enPatterns = listOf("go home", "home", "home screen", "main screen", "open home", "launch home screen", "desktop", "exit to home"),
            hiPatterns = listOf("होम पर जाओ", "होम", "घर", "मुख्य स्क्रीन", "होम स्क्रीन खोलो", "मेन स्क्रीन पर जाओ", "home screen jao", "home par jao"),
            bnPatterns = listOf("হোমে যাও", "মূল স্ক্রিন", "হোম স্ক্রিনে যান", "মেনু স্ক্রিন"),
            otherPatterns = listOf("hoomu", "home")
        ),
        CommandIntent(
            intentName = "OPEN_RECENTS",
            targetAction = "open_recents",
            enPatterns = listOf("open recents", "recents", "recent apps", "show recent tasks", "app switcher", "overview screen", "multitask"),
            hiPatterns = listOf("हाल के ऐप", "रीसेंट ऐप्स", "रिसेंट टास्क", "हाल के ऐप्स दिखाओ", "रिसेंट ऐप्स खोलो", "recent apps dikhao", "recent task"),
            bnPatterns = listOf("সাম্প্রতিক অ্যাপস", "রিসেন্ট কাজ", "সাম্প্রতিক অ্যাপস দেখান"),
            otherPatterns = listOf("recent")
        ),
        CommandIntent(
            intentName = "OPEN_NOTIFICATIONS",
            targetAction = "open_notifications",
            enPatterns = listOf("open notifications", "notifications", "show notifications", "pull down notification shade", "notification center", "notification panel"),
            hiPatterns = listOf("नोटिफिकेशन खोलो", "नोटिफिकेशन दिखाओ", "सूचनाएं दिखाओ", "नोटिफिकेशन पैनल", "notification kholo", "suchnaye dikhao"),
            bnPatterns = listOf("নোটিফিকেশন দেখান", "নোটিফিকেশন প্যানেল", "বিজ্ঞপ্তি দেখান"),
            otherPatterns = listOf("tsuuchi", "notification")
        ),
        CommandIntent(
            intentName = "OPEN_QUICK_SETTINGS",
            targetAction = "open_quick_settings",
            enPatterns = listOf("open quick settings", "quick settings", "status bar settings", "control center", "pull down status bar"),
            hiPatterns = listOf("क्विक सेटिंग्स खोलो", "कंट्रोल सेंटर", "क्विक सेटिंग्स दिखाओ", "स्टेटस बार", "control center kholo"),
            bnPatterns = listOf("কুইক সেটিংস খুলুন", "কন্ট্রোল সেন্টার দেখান"),
            otherPatterns = listOf("settei", "quick settings")
        ),
        CommandIntent(
            intentName = "LOCK_SCREEN",
            targetAction = "lock_screen",
            enPatterns = listOf("lock screen", "lock phone", "turn off screen", "sleep phone", "lock device", "screen lock"),
            hiPatterns = listOf("स्क्रीन लॉक करो", "फोन लॉक करो", "स्क्रीन बंद करो", "डिवाइस लॉक करो", "screen lock karo", "phone lock karo"),
            bnPatterns = listOf("স্ক্রিন লক করুন", "ফোন লক করুন", "স্ক্রিন অফ করুন"),
            otherPatterns = listOf("rokku", "gamen rokku")
        ),
        CommandIntent(
            intentName = "TAKE_SCREENSHOT",
            targetAction = "take_screenshot",
            enPatterns = listOf("take screenshot", "capture screen", "screenshot", "save screen image", "snap screen", "screen grab"),
            hiPatterns = listOf("स्क्रीनशॉट लो", "स्क्रीनशॉट खींचो", "स्क्रीनशॉट सेव करो", "स्क्रीन कैप्चर करो", "screenshot lo", "screenshot kheecho"),
            bnPatterns = listOf("স্ক্রিনশট নিন", "স্ক্রিনশট তুলুন", "স্ক্রিন ক্যাপচার করুন"),
            otherPatterns = listOf("sukusho", "screenshot")
        ),

        // === 2. CONNECTIVITY & DEVICE TOGGLES ===
        CommandIntent(
            intentName = "WIFI_ON",
            targetAction = "toggle_wifi_auto",
            defaultParams = mapOf("state" to "on"),
            enPatterns = listOf("turn on wifi", "enable wifi", "wifi on", "activate wifi", "switch on wifi", "connect wifi"),
            hiPatterns = listOf("वाई-फाई चालू करो", "वाई-फाई ऑन करो", "वाई-फाई चालू करें", "वाईफाई ऑन करो", "wifi chalu karo", "wifi on karo"),
            bnPatterns = listOf("ওয়াইফাই চালু করুন", "ওয়াইফাই অন করুন", "ওয়াইফাই সক্রিয় করুন"),
            otherPatterns = listOf("waifai on", "wifi activate")
        ),
        CommandIntent(
            intentName = "WIFI_OFF",
            targetAction = "toggle_wifi_auto",
            defaultParams = mapOf("state" to "off"),
            enPatterns = listOf("turn off wifi", "disable wifi", "wifi off", "deactivate wifi", "switch off wifi"),
            hiPatterns = listOf("वाई-फाई बंद करो", "वाई-फाई ऑफ करो", "वाई-फाई बंद करें", "wifi band karo", "wifi off karo"),
            bnPatterns = listOf("ওয়াইফাই বন্ধ করুন", "ওয়াইফাই অফ করুন"),
            otherPatterns = listOf("waifai off", "wifi deactivate")
        ),
        CommandIntent(
            intentName = "BLUETOOTH_ON",
            targetAction = "toggle_bluetooth_auto",
            defaultParams = mapOf("state" to "on"),
            enPatterns = listOf("turn on bluetooth", "enable bluetooth", "bluetooth on", "activate bluetooth", "switch on bluetooth"),
            hiPatterns = listOf("ब्लूटूथ चालू करो", "ब्लूटूथ ऑन करो", "ब्लूटूथ चालू करें", "bluetooth chalu karo", "bluetooth on karo"),
            bnPatterns = listOf("ব্লুটুথ চালু করুন", "ব্লুটুথ অন করুন"),
            otherPatterns = listOf("buruutousu on", "bluetooth enable")
        ),
        CommandIntent(
            intentName = "BLUETOOTH_OFF",
            targetAction = "toggle_bluetooth_auto",
            defaultParams = mapOf("state" to "off"),
            enPatterns = listOf("turn off bluetooth", "disable bluetooth", "bluetooth off", "switch off bluetooth"),
            hiPatterns = listOf("ब्लूटूथ बंद करो", "ब्लूटूथ ऑफ करो", "ब्लूटूथ बंद करें", "bluetooth band karo", "bluetooth off karo"),
            bnPatterns = listOf("ব্লুটুথ বন্ধ করুন", "ব্লুটুথ অফ করুন"),
            otherPatterns = listOf("buruutousu off", "bluetooth disable")
        ),
        CommandIntent(
            intentName = "FLASHLIGHT_ON",
            targetAction = "toggle_flashlight",
            defaultParams = mapOf("state" to "on"),
            enPatterns = listOf("turn on flashlight", "turn on torch", "flashlight on", "torch on", "enable torch", "light up", "turn on light"),
            hiPatterns = listOf("टॉर्च चालू करो", "फ्लैशलाइट ऑन करो", "टॉर्च ऑन करो", "लाइट जलाओ", "torch chalu karo", "flashlight on karo", "roshni karo"),
            bnPatterns = listOf("টর্চ অন করুন", "ফ্ল্যাশলাইট চালু করুন", "আলো জ্বালান"),
            otherPatterns = listOf("raito on", "torch on")
        ),
        CommandIntent(
            intentName = "FLASHLIGHT_OFF",
            targetAction = "toggle_flashlight",
            defaultParams = mapOf("state" to "off"),
            enPatterns = listOf("turn off flashlight", "turn off torch", "flashlight off", "torch off", "disable torch", "turn off light"),
            hiPatterns = listOf("टॉर्च बंद करो", "फ्लैशलाइट ऑफ करो", "टॉर्च बंद करो", "लाइट बंद करो", "torch band karo", "light band karo"),
            bnPatterns = listOf("টর্চ অফ করুন", "ফ্ল্যাশলাইট বন্ধ করুন", "আলো নিভান"),
            otherPatterns = listOf("raito off", "torch off")
        ),

        // === 3. CALLS & PHONE AUTOMATION ===
        CommandIntent(
            intentName = "ANSWER_CALL",
            targetAction = "answer_call",
            enPatterns = listOf("answer call", "pick up call", "accept call", "receive call", "pick up the phone", "answer incoming call"),
            hiPatterns = listOf("कॉल उठाओ", "फोन उठाओ", "कॉल रिसीव करो", "कॉल का जवाब दो", "फोन पिक करो", "call uthao", "phone uthao", "call receive karo"),
            bnPatterns = listOf("কল ধরুন", "ফোন ধরুন", "কল রিসিভ করুন", "ফোন তুলুন"),
            otherPatterns = listOf("denwa deru", "answer")
        ),
        CommandIntent(
            intentName = "END_CALL",
            targetAction = "end_call",
            enPatterns = listOf("end call", "hang up", "cut call", "decline call", "reject call", "stop call", "disconnect call"),
            hiPatterns = listOf("कॉल काटो", "फोन काटो", "कॉल रिजेक्ट करो", "कॉल बंद करो", "फोन कट करो", "call kato", "phone kato", "call disconnect karo"),
            bnPatterns = listOf("কল কাটুন", "ফোন কাটুন", "কল রিজেক্ট করুন", "কল ডিসকানেক্ট করুন"),
            otherPatterns = listOf("denwa kiru", "hang up")
        ),

        // === 4. MEDIA & SCROLLING ===
        CommandIntent(
            intentName = "SCROLL_DOWN",
            targetAction = "scroll_screen",
            defaultParams = mapOf("direction" to "DOWN"),
            enPatterns = listOf("scroll down", "next reel", "skip reel", "scroll forward", "swipe up", "move down", "next video"),
            hiPatterns = listOf("नीचे स्क्रॉल करो", "अगला रील", "आगे बढ़ो", "ऊपर स्वाइप करो", "नीचे जाओ", "neeche scroll karo", "agla reel"),
            bnPatterns = listOf("নিচে স্ক্রোল করুন", "পরের রিল", "উপরে সোয়াইপ করুন"),
            otherPatterns = listOf("shita sukurooru", "tsugi")
        ),
        CommandIntent(
            intentName = "SCROLL_UP",
            targetAction = "scroll_screen",
            defaultParams = mapOf("direction" to "UP"),
            enPatterns = listOf("scroll up", "previous reel", "scroll backward", "swipe down", "move up", "back reel"),
            hiPatterns = listOf("ऊपर स्क्रॉल करो", "पिछला रील", "पीछे जाओ", "नीचे स्वाइप करो", "ऊपर जाओ", "upar scroll karo", "pichhla reel"),
            bnPatterns = listOf("উপরে স্ক্রোল করুন", "আগের রিল", "নিচে সোয়াইপ করুন"),
            otherPatterns = listOf("ue sukurooru", "mae")
        ),
        CommandIntent(
            intentName = "VOLUME_UP",
            targetAction = "volume_up",
            enPatterns = listOf("volume up", "increase volume", "louder", "turn up volume", "raise volume", "more sound"),
            hiPatterns = listOf("आवाज बढ़ाओ", "वॉल्यूम बढ़ाओ", "तेज करो", "साउंड बढ़ाओ", "awaaz badhao", "volume badhao", "tez karo"),
            bnPatterns = listOf("ভলিউম বাড়ান", "শব্দ বাড়ান", "সাউন্ড বাড়ান"),
            otherPatterns = listOf("oto ookiku", "volume up")
        ),
        CommandIntent(
            intentName = "VOLUME_DOWN",
            targetAction = "volume_down",
            enPatterns = listOf("volume down", "decrease volume", "quieter", "turn down volume", "lower volume", "less sound"),
            hiPatterns = listOf("आवाज कम करो", "वॉल्यूम कम करो", "धीमा करो", "साउंड कम करो", "awaaz kam karo", "volume kam karo", "dheere karo"),
            bnPatterns = listOf("ভলিউম কমান", "শব্দ কমান", "সাউন্ড কমান"),
            otherPatterns = listOf("oto chiisaku", "volume down")
        ),

        // === 5. MEDIA CONTROLS & APPS ===
        CommandIntent(
            intentName = "MEDIA_PLAY_PAUSE",
            targetAction = "media_play_pause",
            enPatterns = listOf("play music", "pause music", "stop music", "resume music", "play video", "pause video", "toggle play"),
            hiPatterns = listOf("गाना चलाओ", "गाना रोको", "म्यूजिक चलाओ", "म्यूजिक बंद करो", "वीडियो रोको", "प्ले करो", "पॉज करो", "gana chalao", "gana roko"),
            bnPatterns = listOf("গান চালান", "গান থামান", "প্লে করুন", "পজ করুন"),
            otherPatterns = listOf("saisei", "teishi", "play", "pause")
        ),
        CommandIntent(
            intentName = "MEDIA_NEXT",
            targetAction = "media_next",
            enPatterns = listOf("next song", "next track", "skip song", "next video"),
            hiPatterns = listOf("अगला गाना", "अगला ट्रैक", "नेक्स्ट सॉन्ग", "अगला वीडियो", "agla gana", "next gana"),
            bnPatterns = listOf("পরের গান", "পরের ট্র্যাক", "নেক্সট গান"),
            otherPatterns = listOf("tsugi no kyoku", "next")
        ),
        CommandIntent(
            intentName = "MEDIA_PREVIOUS",
            targetAction = "media_previous",
            enPatterns = listOf("previous song", "previous track", "last song", "go back song"),
            hiPatterns = listOf("पिछला गाना", "पिछला ट्रैक", "लास्ट सॉन्ग", "pichhla gana"),
            bnPatterns = listOf("আগের গান", "আগের ট্র্যাক"),
            otherPatterns = listOf("mae no kyoku", "previous")
        ),
        CommandIntent(
            intentName = "OPEN_YOUTUBE",
            targetAction = "open_app",
            defaultParams = mapOf("appName" to "YouTube", "packageName" to "com.google.android.youtube"),
            enPatterns = listOf("open youtube", "launch youtube", "start youtube", "play youtube"),
            hiPatterns = listOf("यूट्यूब खोलो", "यूट्यूब चलाओ", "यूट्यूब स्टार्ट करो", "youtube kholo", "youtube chalao"),
            bnPatterns = listOf("ইউটিউব খুলুন", "ইউটিউব চালান"),
            otherPatterns = listOf("yuuchuubu", "youtube")
        ),
        CommandIntent(
            intentName = "OPEN_WHATSAPP",
            targetAction = "open_app",
            defaultParams = mapOf("appName" to "WhatsApp", "packageName" to "com.whatsapp"),
            enPatterns = listOf("open whatsapp", "launch whatsapp", "start whatsapp", "check whatsapp"),
            hiPatterns = listOf("व्हाट्सएप खोलो", "व्हाट्सएप चालू करो", "व्हाट्सएप देखो", "whatsapp kholo", "whatsapp chalao"),
            bnPatterns = listOf("হোয়াটসঅ্যাপ খুলুন", "হোয়াটসঅ্যাপ চালু করুন"),
            otherPatterns = listOf("whatsapp")
        ),
        CommandIntent(
            intentName = "OPEN_INSTAGRAM",
            targetAction = "open_app",
            defaultParams = mapOf("appName" to "Instagram", "packageName" to "com.instagram.android"),
            enPatterns = listOf("open instagram", "launch instagram", "start instagram", "open insta"),
            hiPatterns = listOf("इंस्टाग्राम खोलो", "इंस्टा खोलो", "इंस्टाग्राम स्टार्ट करो", "instagram kholo", "insta kholo"),
            bnPatterns = listOf("ইনস্টাগ্রাম খুলুন", "ইনস্টা খুলুন"),
            otherPatterns = listOf("insuta", "instagram")
        ),
        CommandIntent(
            intentName = "OPEN_CAMERA",
            targetAction = "open_app",
            defaultParams = mapOf("appName" to "Camera", "packageName" to "com.android.camera"),
            enPatterns = listOf("open camera", "launch camera", "take photo", "start camera"),
            hiPatterns = listOf("कैमरा खोलो", "कैमरा स्टार्ट करो", "फोटो खींचो", "camera kholo", "photo kheecho"),
            bnPatterns = listOf("ক্যামেরা খুলুন", "ক্যামেরা চালু করুন", "ছবি তুলুন"),
            otherPatterns = listOf("kamera", "camera")
        ),

        // === 6. UTILITIES, TIME & WAKE-UP (SEMANTIC SEPARATION) ===
        CommandIntent(
            intentName = "CHECK_BATTERY",
            targetAction = "check_battery",
            enPatterns = listOf("battery status", "check battery", "how much battery", "battery percentage", "battery level"),
            hiPatterns = listOf("बैटरी कितनी है", "बैटरी स्टेटस", "बैटरी चेक करो", "कितनी चार्जिंग है", "battery kitni hai", "charging check karo"),
            bnPatterns = listOf("ব্যাটারি কত আছে", "ব্যাটারি স্ট্যাটাস", "চার্জ কত"),
            otherPatterns = listOf("batterii", "battery")
        ),
        CommandIntent(
            intentName = "GET_WEATHER",
            targetAction = "get_weather",
            enPatterns = listOf("what is the weather", "current weather", "is it raining", "temperature today", "weather update"),
            hiPatterns = listOf("मौसम कैसा है", "आज का मौसम", "क्या बारिश होगी", "तापमान कितना है", "mausam kaisa hai", "aaj ka mausam"),
            bnPatterns = listOf("আবহাওয়া কেমন", "আজকের আবহাওয়া", "বৃষ্টি কি হবে"),
            otherPatterns = listOf("tenki", "weather")
        ),
        // === 6A. NATURAL WAKE-UP (Alya Voice Calling User by Name) ===
        CommandIntent(
            intentName = "NATURAL_WAKE_UP",
            targetAction = "create_wakeup_alarm",
            defaultParams = mapOf("type" to "natural_wake_up", "title" to "Good Morning! Time to Wake Up"),
            enPatterns = listOf(
                "wake me up", "wake me at", "wake me up tomorrow", "wake me in the morning",
                "call me to wake up", "wake me up at 7", "wake me up at 6", "wake me at 7 am",
                "wake me at 6:30", "wake me up when it's time", "i'm going to sleep wake me up"
            ),
            hiPatterns = listOf(
                "मुझे जगा देना", "सुबह जगा देना", "मुझे सुबह उठा देना", "जगा देना", "सुबह 7 बजे उठाना",
                "mujhe jaga dena", "subah utha dena", "subah 7 baje utha dena", "mujhe utha dena",
                "jaga dena subah", "kal subah jaga dena", "main sone ja raha hoon mujhe utha dena"
            ),
            bnPatterns = listOf(
                "আমাকে জাগিয়ে তুলুন", "সকালে ডেকে দিও", "আমাকে কাল সকালে ডেকে দিন",
                "আমাকে ঘুম থেকে তুলুন", "সকাল ৭ টায় ডেকে দিন"
            ),
            otherPatterns = listOf("okoshite", "asa okoshite", "wake me up")
        ),
        // === 6B. SYSTEM CLOCK ALARM ===
        CommandIntent(
            intentName = "SET_ALARM",
            targetAction = "set_alarm",
            defaultParams = mapOf("type" to "system_alarm", "title" to "Alarm"),
            enPatterns = listOf("set alarm", "set an alarm", "create alarm", "alarm for 7 am", "set alarm for 6"),
            hiPatterns = listOf("अलार्म लगाओ", "अलार्म सेट करो", "घड़ी का अलार्म लगाओ", "alarm lagao", "alarm set karo"),
            bnPatterns = listOf("অ্যালার্ম দিন", "অ্যালার্ম সেট করুন"),
            otherPatterns = listOf("araamu", "alarm")
        ),
        // === 6C. TIMERS ===
        CommandIntent(
            intentName = "SET_TIMER",
            targetAction = "create_timer",
            defaultParams = mapOf("seconds" to "300", "message" to "Alya Timer"),
            enPatterns = listOf("set timer", "start timer", "timer for 5 minutes", "countdown timer", "set a timer"),
            hiPatterns = listOf("टाइमर लगाओ", "टाइमर शुरू करो", "5 मिनट का टाइमर लगाओ", "timer lagao", "timer shuru karo"),
            bnPatterns = listOf("টাইমার দিন", "টাইমার সেট করুন", "কাউন্টডাউন শুরু করুন"),
            otherPatterns = listOf("taimaa", "timer")
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
            val allPatterns = item.enPatterns + item.hiPatterns + item.bnPatterns + item.otherPatterns
            for (pattern in allPatterns) {
                val pClean = pattern.lowercase().trim()
                if (cleanText == pClean || cleanText.contains(pClean)) {
                    Log.i(TAG, "Matched Command Registry Intent '${item.intentName}' for query: '$cleanText'")
                    return Pair(item.targetAction, item.defaultParams)
                }
            }
        }
        return null
    }
}
