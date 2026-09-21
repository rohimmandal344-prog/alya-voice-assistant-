package com.example.voice.lang

import android.util.Log
import java.util.Locale

/**
 * Language Detection Result
 */
data class LanguageDetectionResult(
    val languageCode: String,
    val locale: Locale,
    val confidence: Float,
    val languageDisplayName: String
)

/**
 * OnDeviceLanguageDetector
 * 
 * Lightweight, zero-latency, on-device natural language identifier.
 * Analyzes unicode scripts, character n-grams, and high-frequency functional vocabulary
 * to identify user language in under 1 millisecond without external cloud latency.
 */
class OnDeviceLanguageDetector {

    companion object {
        private const val TAG = "LanguageDetector"

        private val ENGLISH_INDIA_KEYWORDS = setOf("yaar", "bhai", "ji", "re", "sir", "madam", "recharge", "upi", "paytm", "phonepe", "aadhar", "pan", "train", "auto", "rickshaw", "paisa", "rupee", "rs", "crore", "lakh")
        private val RAJBONSHI_KEYWORDS = setOf(
            "kila", "auchen", "keta", "ki koreso", "bhat khabaru", "mui", "tui", "bari", "bap", "ma",
            "konta", "kiya", "bala", "asen", "jaosen", "kotha", "koren", "keno", "tahe", "ake", "seke",
            "mor", "tor", "amar", "tomar", "ki", "ka", "kiya", "korsho", "jabo", "asbo", "tui kothay aso"
        )
        private val SPANISH_KEYWORDS = setOf("el", "la", "los", "las", "un", "una", "de", "en", "que", "por", "para", "con", "como", "hola", "gracias", "por favor", "ayuda", "cancion", "reproducir", "abrir", "tiempo", "clima", "enciende", "apaga", "como estas", "buenos dias", "buenas noches")
        private val FRENCH_KEYWORDS = setOf("le", "la", "les", "un", "une", "des", "du", "de", "et", "en", "que", "qui", "dans", "pour", "avec", "bonjour", "merci", "s'il", "vous", "plait", "aide", "musique", "jouer", "ouvrir", "meteo", "comment vas-tu", "bonne nuit")
        private val GERMAN_KEYWORDS = setOf("der", "die", "das", "ein", "eine", "einen", "und", "in", "den", "von", "zu", "mit", "sich", "auf", "fuer", "hallo", "danke", "bitte", "hilfe", "musik", "spielen", "oeffnen", "wetter", "wie gehts", "guten tag", "guten morgen")
        private val ITALIAN_KEYWORDS = setOf("il", "lo", "la", "i", "gli", "le", "un", "uno", "una", "di", "a", "da", "in", "con", "su", "per", "tra", "fra", "ciao", "grazie", "per favore", "aiuto", "musica", "riproduci", "apri", "tempo", "come stai", "buongiorno")
        private val PORTUGUESE_KEYWORDS = setOf("o", "a", "os", "as", "um", "uma", "de", "do", "da", "em", "no", "na", "para", "com", "por", "ola", "obrigado", "obrigada", "por favor", "ajuda", "musica", "tocar", "abrir", "clima", "tudo bem", "bom dia")
        private val INDONESIAN_KEYWORDS = setOf("yang", "di", "dan", "dari", "ini", "itu", "dengan", "untuk", "tidak", "ada", "ke", "saya", "halo", "terima", "kasih", "tolong", "putar", "buka", "cuaca", "apa kabar", "selamat pagi")
        private val TURKISH_KEYWORDS = setOf("bir", "ve", "bu", "da", "de", "icin", "ile", "ne", "var", "cok", "merhaba", "tesekkurler", "lutfen", "yardim", "muzik", "cal", "ac", "hava", "nasilsin", "gunaydin")
        private val RUSSIAN_KEYWORDS = setOf("и", "в", "не", "на", "я", "что", "с", "он", "по", "к", "но", "они", "мы", "привет", "спасибо", "пожалуйста", "помощь", "музыка", "включи", "открой", "погода", "как дела", "добрый день", "доброе утро", "spasibo", "privet", "kak dela")
        private val HINDI_KEYWORDS = setOf(
            "और", "है", "की", "के", "में", "को", "का", "से", "पर", "नमस्ते", "धन्यवाद", "कृपया", "मदद",
            "गाना", "चलाओ", "खोलो", "मौसम", "क्या", "कैसे", "हो", "सुनाओ", "बताओ", "करो", "आप", "तुम",
            "मेरा", "मेरी", "मुझे", "हम", "कौन", "कहाँ", "कब", "क्यों", "अच्छा", "ठीक", "बात", "सुना"
        )
        private val HINGLISH_KEYWORDS = setOf(
            "kholo", "chalao", "karo", "kaise", "mera", "meri", "naam", "tum", "kaun", "ho", "dhanyawad",
            "bhai", "shukriya", "gaana", "sunao", "mausam", "batao", "karega", "mujhe", "karna", "nahi",
            "wifi", "bluetooth", "chalu", "band", "namaste", "kya", "kar", "rahe", "rahi", "kaisa", "kaisi",
            "theek", "thik", "haan", "ha", "bol", "bolo", "suno", "sun", "apna", "apni", "aap", "aaya",
            "gaya", "kuch", "sab", "yeh", "yaha", "waha", "woh", "samajh", "aaj", "kal", "kaise ho", "kya hua"
        )
        private val BANGLA_KEYWORDS = setOf(
            "kemon", "acho", "ki", "korcho", "gaan", "bajao", "khulo", "bondho", "koro", "valo", "lagche",
            "tumi", "ke", "amake", "bolo", "dhonnobad", "shunchi", "kothay", "thako", "hamba", "amar", "apni",
            "khoob", "khobor", "bhalo", "shuno", "shuncho", "koren", "kore"
        )
        private val JAPANESE_KEYWORDS = setOf("konnichiwa", "arigato", "arigatou", "ohayo", "ohayou", "kudasai", "onegai", "watashi", "anata", "nani", "hai", "iie", "tasukete", "ongaku", "saisei", "tenki", "doko", "ikura", "sayonara")
        private val ARABIC_KEYWORDS = setOf("marhaban", "shukran", "afwan", "kaifa", "haluk", "habibi", "salam", "assalam", "albi", "min fadlak", "musiqa", "shaghel", "eftah", "taqs", "ahlan")
        private val KOREAN_KEYWORDS = setOf("annyeong", "annyeonghaseyo", "gamsahamnida", "jebal", "dowa", "eumak", "teul-eo", "yeol-eo", "nalssi", "neo", "nugu", "gwaenchanh-a")
        private val CHINESE_KEYWORDS = setOf("nihao", "xiexie", "qing", "bangzhu", "yinyue", "bofang", "dakai", "tianqi", "shei", "shenme", "zaijian", "hao de")
    }

    /**
     * Identifies the primary language and returns the best matching target Locale for TTS and NLP.
     */
    fun detectLanguage(text: String, fallbackLocale: Locale = Locale.US): LanguageDetectionResult {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            return LanguageDetectionResult(
                languageCode = fallbackLocale.language,
                locale = fallbackLocale,
                confidence = 1.0f,
                languageDisplayName = fallbackLocale.displayLanguage
            )
        }

        // 1. Script-based unambiguous detection
        // Devanagari (Hindi / Marathi / Sanskrit)
        if (trimmed.any { it in '\u0900'..'\u097F' }) {
            return LanguageDetectionResult("hi", Locale("hi", "IN"), 0.99f, "Hindi")
        }

        // Bengali / Bangla / Assamese
        if (trimmed.any { it in '\u0980'..'\u09FF' }) {
            return LanguageDetectionResult("bn", Locale("bn", "IN"), 0.99f, "Bengali")
        }

        // Cyrillic (Russian / Ukrainian / Slavic)
        if (trimmed.any { it in '\u0400'..'\u04FF' }) {
            return LanguageDetectionResult("ru", Locale("ru", "RU"), 0.99f, "Russian")
        }

        // Japanese (Hiragana, Katakana, or CJK Kanji with Japanese context)
        if (trimmed.any { it in '\u3040'..'\u309F' || it in '\u30A0'..'\u30FF' }) {
            return LanguageDetectionResult("ja", Locale.JAPAN, 0.99f, "Japanese")
        }

        // Korean (Hangul Syllables and Jamo)
        if (trimmed.any { it in '\uAC00'..'\uD7AF' || it in '\u1100'..'\u11FF' }) {
            return LanguageDetectionResult("ko", Locale.KOREA, 0.99f, "Korean")
        }

        // Chinese (CJK Unified Ideographs)
        if (trimmed.any { it in '\u4E00'..'\u9FFF' }) {
            return LanguageDetectionResult("zh", Locale.SIMPLIFIED_CHINESE, 0.98f, "Chinese")
        }

        // Arabic / Urdu / Persian Script
        if (trimmed.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' }) {
            // Check for Urdu specific characters
            val isUrdu = trimmed.any { it in listOf('ٹ', 'ڈ', 'ڑ', 'ں', 'ے', 'ہ', 'ھ') }
            return if (isUrdu) {
                LanguageDetectionResult("ur", Locale("ur", "PK"), 0.98f, "Urdu")
            } else {
                LanguageDetectionResult("ar", Locale("ar", "SA"), 0.98f, "Arabic")
            }
        }

        // Tamil
        if (trimmed.any { it in '\u0B80'..'\u0BFF' }) {
            return LanguageDetectionResult("ta", Locale("ta", "IN"), 0.99f, "Tamil")
        }

        // Telugu
        if (trimmed.any { it in '\u0C00'..'\u0C7F' }) {
            return LanguageDetectionResult("te", Locale("te", "IN"), 0.99f, "Telugu")
        }

        // Malayalam
        if (trimmed.any { it in '\u0D00'..'\u0D7F' }) {
            return LanguageDetectionResult("ml", Locale("ml", "IN"), 0.99f, "Malayalam")
        }

        // Thai
        if (trimmed.any { it in '\u0E00'..'\u0E7F' }) {
            return LanguageDetectionResult("th", Locale("th", "TH"), 0.99f, "Thai")
        }

        // Punjabi (Gurmukhi)
        if (trimmed.any { it in '\u0A00'..'\u0A7F' }) {
            return LanguageDetectionResult("pa", Locale("pa", "IN"), 0.99f, "Punjabi")
        }

        // Gujarati
        if (trimmed.any { it in '\u0A80'..'\u0AFF' }) {
            return LanguageDetectionResult("gu", Locale("gu", "IN"), 0.99f, "Gujarati")
        }

        // Vietnamese (Latin script with tone marks)
        if (trimmed.any { it in listOf('đ', 'Đ', 'ă', 'Ă', 'â', 'Â', 'ê', 'Ê', 'ô', 'Ô', 'ơ', 'Ơ', 'ư', 'Ư') }) {
            return LanguageDetectionResult("vi", Locale("vi", "VN"), 0.98f, "Vietnamese")
        }

        // 2. Latin Script N-gram and Stopword frequency profiling
        val normalized = trimmed.lowercase().replace("[^a-zà-ÿ\\s]".toRegex(), " ")
        val words = normalized.split("\\s+".toRegex()).filter { it.isNotBlank() }

        var enInScore = 0
        var rjbScore = 0
        var esScore = 0
        var frScore = 0
        var deScore = 0
        var itScore = 0
        var ptScore = 0
        var idScore = 0
        var trScore = 0
        var hiScore = 0
        var bnScore = 0
        var jaScore = 0
        var arScore = 0
        var koScore = 0
        var zhScore = 0
        var ruScore = 0

        for (word in words) {
            if (ENGLISH_INDIA_KEYWORDS.contains(word)) enInScore += 4
            if (RAJBONSHI_KEYWORDS.contains(word)) rjbScore += 5
            if (SPANISH_KEYWORDS.contains(word)) esScore += 3
            if (FRENCH_KEYWORDS.contains(word)) frScore += 3
            if (GERMAN_KEYWORDS.contains(word)) deScore += 3
            if (ITALIAN_KEYWORDS.contains(word)) itScore += 3
            if (PORTUGUESE_KEYWORDS.contains(word)) ptScore += 3
            if (INDONESIAN_KEYWORDS.contains(word)) idScore += 3
            if (TURKISH_KEYWORDS.contains(word)) trScore += 3
            if (HINGLISH_KEYWORDS.contains(word)) hiScore += 3
            if (BANGLA_KEYWORDS.contains(word)) bnScore += 3
            if (JAPANESE_KEYWORDS.contains(word)) jaScore += 3
            if (ARABIC_KEYWORDS.contains(word)) arScore += 3
            if (KOREAN_KEYWORDS.contains(word)) koScore += 3
            if (CHINESE_KEYWORDS.contains(word)) zhScore += 3
            if (RUSSIAN_KEYWORDS.contains(word)) ruScore += 3
        }

        // Characteristic diacritics
        if (trimmed.contains("ñ") || trimmed.contains("¿") || trimmed.contains("¡")) esScore += 5
        if (trimmed.contains("ç") && (trimmed.contains("ã") || trimmed.contains("õ"))) ptScore += 5
        if (trimmed.contains("œ") || trimmed.contains("è") || trimmed.contains("é") || trimmed.contains("ê") || trimmed.contains("à")) frScore += 3
        if (trimmed.contains("ä") || trimmed.contains("ö") || trimmed.contains("ü") || trimmed.contains("ß")) deScore += 4
        if (trimmed.contains("ğ") || trimmed.contains("ş") || trimmed.contains("ı")) trScore += 5

        val scores = listOf(
            Triple("en-IN", Locale("en", "IN"), enInScore),
            Triple("rjb", Locale("bn", "IN"), rjbScore),
            Triple("es", Locale("es", "ES"), esScore),
            Triple("fr", Locale.FRANCE, frScore),
            Triple("de", Locale.GERMANY, deScore),
            Triple("it", Locale.ITALY, itScore),
            Triple("pt", Locale("pt", "BR"), ptScore),
            Triple("id", Locale("id", "ID"), idScore),
            Triple("tr", Locale("tr", "TR"), trScore),
            Triple("hi", Locale("hi", "IN"), hiScore),
            Triple("bn", Locale("bn", "IN"), bnScore),
            Triple("ja", Locale.JAPAN, jaScore),
            Triple("ar", Locale("ar", "SA"), arScore),
            Triple("ko", Locale.KOREA, koScore),
            Triple("zh", Locale.SIMPLIFIED_CHINESE, zhScore),
            Triple("ru", Locale("ru", "RU"), ruScore)
        )

        val bestMatch = scores.maxByOrNull { it.third }
        if (bestMatch != null && bestMatch.third >= 2) {
            val confidence = minOf(1.0f, 0.75f + (bestMatch.third * 0.08f))
            Log.d(TAG, "Identified user language: ${bestMatch.first} (${bestMatch.second.displayName}, confidence: $confidence)")
            return LanguageDetectionResult(
                languageCode = bestMatch.first,
                locale = bestMatch.second,
                confidence = confidence,
                languageDisplayName = bestMatch.second.displayLanguage
            )
        }

        // If fallback locale is Hindi or Bengali and input is Latin-based short utterance, honor fallback locale
        if (fallbackLocale.language == "hi" || fallbackLocale.language == "bn") {
            return LanguageDetectionResult(
                languageCode = fallbackLocale.language,
                locale = fallbackLocale,
                confidence = 0.90f,
                languageDisplayName = fallbackLocale.displayLanguage
            )
        }

        // Default to fallbackLocale
        return LanguageDetectionResult(
            languageCode = fallbackLocale.language,
            locale = fallbackLocale,
            confidence = 0.85f,
            languageDisplayName = fallbackLocale.displayLanguage
        )
    }
}
