package com.example.voice.wakeword

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PreferencesManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SpeakerVerificationManagerTest {

    private lateinit var context: Context
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var verificationManager: SpeakerVerificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        preferencesManager = PreferencesManager(context)
        verificationManager = SpeakerVerificationManager(context, preferencesManager)
    }

    @Test
    fun testCosineSimilarity_identicalVectors_returnsOne() {
        val v1 = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)
        val v2 = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)
        val similarity = verificationManager.calculateCosineSimilarity(v1, v2)
        assertEquals(1.0f, similarity, 0.001f)
    }

    @Test
    fun testCosineSimilarity_orthogonalVectors_returnsZero() {
        val v1 = floatArrayOf(1.0f, 0.0f, 0.0f, 0.0f)
        val v2 = floatArrayOf(0.0f, 1.0f, 0.0f, 0.0f)
        val similarity = verificationManager.calculateCosineSimilarity(v1, v2)
        assertEquals(0.0f, similarity, 0.001f)
    }

    @Test
    fun testExtractEmbedding_nonEmptyPcm_returnsNormalizedVector() {
        val pcm = ShortArray(1024) { (Math.sin(it * 0.1) * 10000).toInt().toShort() }
        val embedding = verificationManager.extractEmbedding(pcm)
        assertEquals(SpeakerVerificationManager.EMBEDDING_DIM, embedding.size)
        
        var sumSq = 0.0f
        for (v in embedding) sumSq += v * v
        assertTrue("Vector should be normalized", Math.abs(1.0f - Math.sqrt(sumSq.toDouble()).toFloat()) < 0.05f)
    }

    @Test
    fun testSnrCalculation_cleanSignal_returnsHighDb() {
        val pcm = ShortArray(1600) { (Math.sin(it * 0.2) * 8000).toInt().toShort() }
        val snrDb = verificationManager.calculateSnrDb(pcm)
        assertTrue("SNR should be > 10dB for synthetic speech signal", snrDb >= 10.0f)
    }
}
