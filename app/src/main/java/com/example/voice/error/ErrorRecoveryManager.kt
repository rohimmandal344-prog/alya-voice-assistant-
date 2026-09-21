package com.example.voice.error

import android.content.Context
import android.util.Log
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.nio.channels.ClosedChannelException
import java.util.Locale

/**
 * ErrorRecoveryManager (Alya v3.3.0)
 *
 * Maps low-level system exceptions, socket timeout events, hardware initialization failures,
 * and permission denials directly to warm, empathetic, and user-friendly spoken feedback
 * across multiple supported locales (English, Hindi, Bengali, Japanese).
 */
object ErrorRecoveryManager {
    private const val TAG = "ErrorRecoveryManager"

    /**
     * Translates any thrown system Throwable into a natural, conversational spoken message
     * tailored to the active system locale.
     */
    fun getSpokenMessageForException(throwable: Throwable, context: Context): String {
        val locale = Locale.getDefault().language.lowercase()
        Log.d(TAG, "Mapping exception: ${throwable.javaClass.name} | Locale: $locale")

        return when (throwable) {
            is SocketTimeoutException, is ConnectException, is ClosedChannelException -> {
                when (locale) {
                    "hi" -> "Alya: Mujhe lagta hai network connection thoda dheema hai. Main background mein ise theek karne ki koshish kar rahi hoon, aap thodi der mein firse boliye."
                    "bn" -> "Alya: Amar mone hochhe network connection ektu durbol. Ami background-e eti thik korar cheshta korchi, apni ektu por abor bolun."
                    "ja" -> "Alya: ネット接続が少し不安定なようです。バックグラウンドで再接続中ですので、少し待ってからもう一度お話しください。"
                    else -> "Alya: It looks like our network connection is a bit slow right now. Don't worry, I am working on restoring it in the background. Please try speaking again in a moment."
                }
            }
            is SecurityException -> {
                when (locale) {
                    "hi" -> "Alya: Mujhe aapse baat karne ke liye microphone ki permission chahiye hogi. Kripya settings mein jaakar ise allow kar dijiye."
                    "bn" -> "Alya: Amar apnar sathe kotha bolar jonyo microphone-er permission proyojon. Dayakore settings-e giye eti allow korun."
                    "ja" -> "Alya: お話しするにはマイクの使用許可が必要です。設定画面からマイクの権限をオンにしてください。"
                    else -> "Alya: I would love to talk to you, but I don't have permission to use your microphone. Could you please grant microphone access in settings?"
                }
            }
            is IllegalStateException -> {
                // Typically associated with AudioRecord initialization failure or resource contention
                when (locale) {
                    "hi" -> "Alya: Microphone shuru karne mein rukawat aa rahi hai. Lagta hai koi aur app iska istemaal kar raha hai."
                    "bn" -> "Alya: Microphone chalu korte somosya hochhe. Mone hochhe anyo kono app eti bebohar korche."
                    "ja" -> "Alya: マイクを開始できませんでした。他のアプリがマイクを使用中である可能性があります。"
                    else -> "Alya: I'm having trouble starting the microphone. It seems another application is currently using it or the system is busy."
                }
            }
            is IOException -> {
                when (locale) {
                    "hi" -> "Alya: Ek chota sa technical issue aaya hai, lekin main active hoon aur aapki madad ke liye taiyaar hoon!"
                    "bn" -> "Alya: Ektu technical somosya hoyeche, kintu ami active achhi ebong apnake sahajyo korar jonyo prostut!"
                    "ja" -> "Alya: 一時的な通信エラーが発生しました。私は準備できていますので、もう一度お試しください。"
                    else -> "Alya: We encountered a brief connection glitch, but I'm fully recovered and ready to help. What can I do for you?"
                }
            }
            else -> {
                when (locale) {
                    "hi" -> "Alya: Kuch technical dikkat aayi hai, chaliye ek baar firse koshish karte hain."
                    "bn" -> "Alya: Kichhu technical somosya hoyeche, cholun arekbar cheshta kori."
                    "ja" -> "Alya: エラーが発生しました。もう一度やり直してみましょう。"
                    else -> "Alya: Something unexpected happened behind the scenes. Let's try that one more time."
                }
            }
        }
    }

    /**
     * Translates a registered VoiceError from the core system registry into an empathetic,
     * human-like spoken explanation.
     */
    fun getSpokenMessageForVoiceError(error: VoiceError): String {
        val locale = Locale.getDefault().language.lowercase()
        Log.d(TAG, "Mapping VoiceError: ${error.type.name} | Locale: $locale")

        return when (error.type) {
            VoiceErrorType.SECURITY_PERMISSION_DENIED -> {
                when (locale) {
                    "hi" -> "Alya: Mujhe aapse baat karne ke liye microphone ki permission chahiye hogi. Kripya settings mein jaakar ise allow kar dijiye."
                    "bn" -> "Alya: Amar apnar sathe kotha bolar jonyo microphone-er permission proyojon. Dayakore settings-e giye eti allow korun."
                    "ja" -> "Alya: お話しするにはマイクの使用許可が必要です。設定画面からマイクの権限をオンにしてください。"
                    else -> "Alya: Microphone permission is currently denied. Please enable microphone permissions in your device settings."
                }
            }
            VoiceErrorType.AUDIO_RECORD_INIT_FAILED -> {
                when (locale) {
                    "hi" -> "Alya: Microphone chalu nahi ho pa raha hai. Kripya check karein ki koi aur app to mic use nahi kar raha."
                    "bn" -> "Alya: Microphone chalu kora jachhe na. Dayakore check korun anyo kono app mic bebohar korche kina."
                    "ja" -> "Alya: マイクが初期化できませんでした。他の音声アプリが起動していないか確認してください。"
                    else -> "Alya: I'm having trouble starting the microphone. Please make sure no other app is using the mic right now."
                }
            }
            VoiceErrorType.RECOGNIZER_BUSY -> {
                when (locale) {
                    "hi" -> "Alya: Mera voice system thoda busy hai. Kripya ek pal rukiye aur fir boliye."
                    "bn" -> "Alya: Amar voice system ektu busy achhe. Dayakore ektu opekkha kore bolun."
                    "ja" -> "Alya: 音声エンジンが少し混み合っています。少し待ってからお話しください。"
                    else -> "Alya: My speech recognition service is a bit busy. Let's wait a moment and try again."
                }
            }
            VoiceErrorType.RECOGNIZER_NETWORK_ERROR -> {
                when (locale) {
                    "hi" -> "Alya: Network connection toot gaya hai. Main offline mode mein aapki offline commands ki madad kar sakti hoon."
                    "bn" -> "Alya: Network connection chhinno hoyeche. Ami offline mode-e apnar sahajyo korte prostut achhi."
                    "ja" -> "Alya: ネットワークが切れました。オフライン対応機能で引き続きお手伝いいたします。"
                    else -> "Alya: We are currently offline. Voice processing is paused, but I can still assist you with offline system commands."
                }
            }
            VoiceErrorType.RECOGNIZER_TIMEOUT -> {
                when (locale) {
                    "hi" -> "Alya: Lagta hai aapne koshish rok di. Jab bhi aap taiyaar ho, mic button dabakar boliye."
                    "bn" -> "Alya: Mone hochhe apni thome gyechen. Apni prostut holei mic button-ti tipe bolun."
                    "ja" -> "Alya: 入力待ち時間が超過しました。準備ができたらマイクボタンを押してください。"
                    else -> "Alya: I didn't catch that. Tap the microphone icon whenever you are ready to speak."
                }
            }
            VoiceErrorType.RECOGNIZER_NO_MATCH -> {
                when (locale) {
                    "hi" -> "Alya: Main aapki baat samajh nahi payi. Kripya thoda saaf aur dheere se boliye."
                    "bn" -> "Alya: Ami apnar kotha thik bhabe bujhte parini. Dayakore arektu sposto bhabe bolun."
                    "ja" -> "Alya: 音声をうまく聞き取れませんでした。もう少しはっきりとお話しいただけますか？"
                    else -> "Alya: I couldn't quite catch that. Could you please speak a bit more clearly or slowly?"
                }
            }
            else -> {
                when (locale) {
                    "hi" -> "Alya: Mujhe ek choti dikkat aayi hai, chaliye ek baar firse koshish karte hain."
                    "bn" -> "Alya: Ektu technical somosya hoyeche, cholun arekbar cheshta kori."
                    "ja" -> "Alya: エラーが発生しました。もう一度試してみましょう。"
                    else -> "Alya: I ran into a minor voice service error. Let's try that again."
                }
            }
        }
    }
}
