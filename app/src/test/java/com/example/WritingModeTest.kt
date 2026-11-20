package com.example

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import com.example.data.WritingDraftStore
import com.example.ui.ShatrWeightStatus
import com.example.ui.WritingViewModel
import com.example.ui.screens.WritingScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WritingModeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        app = ApplicationProvider.getApplicationContext()
        runBlocking {
            WritingDraftStore.clearDraft(app)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            WritingDraftStore.clearDraft(app)
        }
        Dispatchers.resetMain()
    }

    private fun createViewModel(): WritingViewModel {
        val vm = WritingViewModel(app)
        runBlocking {
            withTimeout(5000) {
                vm.uiState.first { it.draftLoaded }
            }
        }
        return vm
    }

    @Test
    fun `test_1_tawil_mutanabbi_sound_weight_and_qafiya`() {
        val vm = createViewModel()
        vm.selectMeter("tawil")
        vm.selectFormType("تام")
        vm.setNoFixedQafiya(false)
        vm.updateRoiInput("م")
        vm.selectMajraOption("ضمة")

        val verseId = vm.uiState.value.verses.first().id
        vm.updateSadr(verseId, TextFieldValue("عَلَى قَدْرِ أَهْلِ الْعَزْمِ تَأْتِي الْعَزَائِمُ"))
        vm.updateAjuz(verseId, TextFieldValue("وَتَأْتِي عَلَى قَدْرِ الْكِرَامِ الْمَكَارِمُ"))
        vm.evaluateAllNow()

        val eval = vm.uiState.value.verses.first().eval
        assertEquals(ShatrWeightStatus.SOUND, eval.sadr.status)
        assertEquals(ShatrWeightStatus.SOUND, eval.ajuz.status)
        assertEquals(true, eval.weightOk)
        assertEquals(true, eval.qafiyaOk)
        assertEquals(1, vm.uiState.value.soundVerseCount)
    }

    @Test
    fun `test_2_wrong_roi_lam_vs_mim_reports_error`() {
        val vm = createViewModel()
        vm.selectMeter("tawil")
        vm.selectFormType("تام")
        vm.setNoFixedQafiya(false)
        vm.updateRoiInput("ل")
        vm.selectMajraOption("ضمة")

        val verseId = vm.uiState.value.verses.first().id
        vm.updateSadr(verseId, TextFieldValue("عَلَى قَدْرِ أَهْلِ الْعَزْمِ تَأْتِي الْعَزَائِمُ"))
        vm.updateAjuz(verseId, TextFieldValue("وَتَأْتِي عَلَى قَدْرِ الْكِرَامِ الْمَكَارِمُ"))
        vm.evaluateAllNow()

        val eval = vm.uiState.value.verses.first().eval
        assertEquals(true, eval.weightOk)
        assertEquals(false, eval.qafiyaOk)
        assertTrue(eval.qafiyaIsHardError)
        assertTrue(eval.qafiyaMessages.any { it.contains("الروي ‹ل› وليس ‹م›") })
    }

    @Test
    fun `test_3_delete_one_word_marks_shatr_broken_with_location`() {
        val vm = createViewModel()
        vm.selectMeter("tawil")
        vm.selectFormType("تام")

        val verseId = vm.uiState.value.verses.first().id
        // حذف كلمة "أَهْلِ" من الشطر السليم
        vm.updateSadr(verseId, TextFieldValue("عَلَى قَدْرِ الْعَزْمِ تَأْتِي الْعَزَائِمُ"))
        vm.evaluateAllNow()

        val sadr = vm.uiState.value.verses.first().eval.sadr
        assertEquals(ShatrWeightStatus.BROKEN, sadr.status)
        assertTrue(sadr.brokenFootNumbers.isNotEmpty())
        assertTrue(sadr.brokenWords.isNotEmpty())
        assertTrue(sadr.brokenCharRanges.isNotEmpty())
        assertTrue(sadr.currentBinary.isNotEmpty())
        assertTrue(sadr.expectedBinary.isNotEmpty())
    }

    @Test
    fun `test_4_two_words_only_shows_incomplete`() {
        val vm = createViewModel()
        vm.selectMeter("tawil")
        vm.selectFormType("تام")

        val verseId = vm.uiState.value.verses.first().id
        vm.updateSadr(verseId, TextFieldValue("عَلَى قَدْرِ"))
        vm.evaluateAllNow()

        val sadr = vm.uiState.value.verses.first().eval.sadr
        assertEquals(ShatrWeightStatus.INCOMPLETE, sadr.status)
        assertEquals("لم يكتمل", sadr.message)
    }

    @Test
    fun `test_5_tawil_verse_on_kamil_meter_is_broken`() {
        val vm = createViewModel()
        vm.selectMeter("kamil")
        vm.selectFormType("تام")

        val verseId = vm.uiState.value.verses.first().id
        vm.updateSadr(verseId, TextFieldValue("عَلَى قَدْرِ أَهْلِ الْعَزْمِ تَأْتِي الْعَزَائِمُ"))
        vm.evaluateAllNow()

        val sadr = vm.uiState.value.verses.first().eval.sadr
        assertEquals(ShatrWeightStatus.BROKEN, sadr.status)
    }

    @Test
    fun `test_6_imru_alqais_tawil_lam_kasra_sound`() {
        val vm = createViewModel()
        vm.selectMeter("tawil")
        vm.selectFormType("تام")
        vm.setNoFixedQafiya(false)
        vm.updateRoiInput("ل")
        vm.selectMajraOption("كسرة")

        val verseId = vm.uiState.value.verses.first().id
        vm.updateSadr(verseId, TextFieldValue("قِفَا نَبْكِ مِنْ ذِكْرَى حَبِيبٍ وَمَنْزِلِ"))
        vm.updateAjuz(verseId, TextFieldValue("بِسِقْطِ اللِّوَى بَيْنَ الدَّخُولِ فَحَوْمَلِ"))
        vm.evaluateAllNow()

        val eval = vm.uiState.value.verses.first().eval
        assertEquals(ShatrWeightStatus.SOUND, eval.sadr.status)
        assertEquals(ShatrWeightStatus.SOUND, eval.ajuz.status)
        assertEquals(true, eval.weightOk)
        assertEquals(true, eval.qafiyaOk)
    }

    @Test
    fun `test_7_draft_persisted_and_restored_across_reopen`() {
        val vm1 = createViewModel()
        vm1.selectMeter("kamil")
        vm1.selectFormType("مجزوء")
        vm1.setNoFixedQafiya(false)
        vm1.updateRoiInput("ر")
        vm1.selectMajraOption("فتحة")
        vm1.setTasriFirstVerse(true)
        vm1.startWriting()

        val v1Id = vm1.uiState.value.verses.first().id
        vm1.updateSadr(v1Id, TextFieldValue("قِفَا نَبْكِ"))
        vm1.updateAjuz(v1Id, TextFieldValue("بِسِقْطِ اللِّوَى"))

        // انتظار كتابة DataStore في الخلفية
        runBlocking {
            withTimeout(5000) {
                while (true) {
                    val saved = WritingDraftStore.loadDraft(app)
                    if (saved != null && saved.meterId == "kamil" && saved.verses.firstOrNull()?.ajuz == "بِسِقْطِ اللِّوَى") {
                        break
                    }
                    kotlinx.coroutines.delay(50)
                }
            }
        }

        val vm2 = createViewModel()
        val state2 = vm2.uiState.value
        assertEquals("kamil", state2.selectedMeterId)
        assertEquals("مجزوء", state2.selectedFormType)
        assertFalse(state2.noFixedQafiya)
        assertEquals("ر", state2.roiInput)
        assertEquals("فتحة", state2.majraOption)
        assertTrue(state2.tasriFirstVerse)
        assertFalse(state2.isSettingsExpanded)
        assertEquals("قِفَا نَبْكِ", state2.verses.first().sadr.text)
        assertEquals("بِسِقْطِ اللِّوَى", state2.verses.first().ajuz.text)
    }

    @Test
    fun `unvocalized_shatr_prompts_diacritics`() {
        val vm = createViewModel()
        val verseId = vm.uiState.value.verses.first().id
        vm.updateSadr(verseId, TextFieldValue("على قدر أهل العزم"))
        vm.evaluateAllNow()

        val sadr = vm.uiState.value.verses.first().eval.sadr
        assertEquals(ShatrWeightStatus.NEEDS_DIACRITICS, sadr.status)
        assertEquals("شكّل الشطر لتظهر النتيجة", sadr.message)
        assertNotNull(vm.formatPoemForCopy())
    }

    @Test
    fun `writing_screen_hides_haraka_toolbar_and_evaluates_mutanabbi_from_keyboard_input`() {
        val vm = createViewModel()
        vm.selectMeter("tawil")
        vm.selectFormType("تام")
        vm.startWriting()

        composeRule.setContent {
            MyApplicationTheme {
                WritingScreen(
                    viewModel = vm,
                    onShowToast = {}
                )
            }
        }

        val removedTags = listOf(
            "writing_haraka_toolbar",
            "haraka_fatha",
            "haraka_damma",
            "haraka_kasra",
            "haraka_sukun",
            "haraka_shadda",
            "haraka_tanwin_fath",
            "haraka_tanwin_damm",
            "haraka_tanwin_kasr"
        )
        removedTags.forEach { tag ->
            assertEquals(0, composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size)
        }

        val verseId = vm.uiState.value.verses.first().id
        composeRule.onNodeWithTag("writing_sadr_input_$verseId")
            .performScrollTo()
            .performTextInput("عَلَى قَدْرِ أَهْلِ الْعَزْمِ تَأْتِي الْعَزَائِمُ")
        composeRule.onNodeWithTag("writing_ajuz_input_$verseId")
            .performScrollTo()
            .performTextInput("وَتَأْتِي عَلَى قَدْرِ الْكِرَامِ الْمَكَارِمُ")

        vm.evaluateAllNow()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("writing_qafiya_box_$verseId")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("الوزن ✓").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("القافية ✓").performScrollTo().assertIsDisplayed()
    }
}
