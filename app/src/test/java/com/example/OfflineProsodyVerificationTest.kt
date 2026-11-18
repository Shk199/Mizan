package com.example

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.ui.AnalyzerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OfflineProsodyVerificationTest {

    private lateinit var app: Application
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        app = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `case_a_vocalized_mutanabbi_detects_tawil_with_feet`() {
        val vm = AnalyzerViewModel(app)
        val vocalizedVerse = "عَلى قَدْرِ أَهْلِ العَزْمِ تَأْتِي العَزَائِمُ ... وَتَأْتِي عَلَى قَدْرِ الكِرَامِ المَكَارِمُ"

        vm.runAnalysis(vocalizedVerse)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertNull(vm.inputError.value)
        val data = vm.analysisData.value
        assertNotNull(data)
        assertEquals("الطويل", data!!.meterResult.meterName)
        assertTrue(data.meterResult.isSound)
        assertEquals(4, data.meterResult.sadrMatch.feet.size)
        assertEquals(4, data.meterResult.ajuzMatch?.feet?.size)
        assertTrue(data.meterResult.darbDescription.isNotEmpty())
        assertNull(data.grammarData)
    }

    @Test
    fun `case_b_unvocalized_mutanabbi_shows_insufficient_diacritics_error_and_no_analysis`() {
        val vm = AnalyzerViewModel(app)
        val unvocalizedVerse = "على قدر أهل العزم تأتي العزائم ... وتأتي على قدر الكرام المكارم"

        vm.runAnalysis(unvocalizedVerse)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertNull(vm.analysisData.value)
        assertEquals(
            "البيت غير مشكول بما يكفي. التقطيع العروضي يعتمد على الحركات، فشكّل البيت.",
            vm.inputError.value
        )
    }

    @Test
    fun `case_c_english_text_is_rejected`() {
        val vm = AnalyzerViewModel(app)

        vm.runAnalysis("To be or not to be that is the question")
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertNull(vm.analysisData.value)
        assertEquals(
            "التطبيق يحلل الشعر العربي فقط. أدخل بيتًا بالعربية.",
            vm.inputError.value
        )
    }

    @Test
    fun `case_d_offline_airplane_mode_vocalized_analysis_works_locally`() {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val shadowCm = Shadows.shadowOf(cm)
        @Suppress("DEPRECATION")
        shadowCm.setDefaultNetworkActive(false)
        shadowCm.setActiveNetworkInfo(null)

        val vm = AnalyzerViewModel(app)
        val vocalizedVerse = "عَلى قَدْرِ أَهْلِ العَزْمِ تَأْتِي العَزَائِمُ ... وَتَأْتِي عَلَى قَدْرِ الكِرَامِ المَكَارِمُ"

        vm.runAnalysis(vocalizedVerse)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertNull(vm.inputError.value)
        val data = vm.analysisData.value
        assertNotNull(data)
        assertEquals("الطويل", data!!.meterResult.meterName)
        assertTrue(data.meterResult.isSound)
    }
}
