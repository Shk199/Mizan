package com.example

import android.app.Application
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import com.example.data.WritingDraftStore
import com.example.engine.MeterDetector
import com.example.engine.MeterForm
import com.example.engine.ShatrAnalysis
import com.example.ui.ShatrWeightStatus
import com.example.ui.WritingViewModel
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WritingModePrecisionTest {

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

    private fun syntheticAnalysis(binary: String): ShatrAnalysis {
        return ShatrAnalysis(
            originalText = binary,
            words = emptyList(),
            phoneticLine = binary,
            binaryPattern = binary,
            hasOptionalIshba = false,
            alternativePatterns = listOf(binary)
        )
    }

    private fun buildHashwCombos(hashw: List<List<String>>): List<String> {
        var acc = listOf("")
        for (posOptions in hashw) {
            val next = mutableListOf<String>()
            for (prefix in acc) {
                for (opt in posOptions) {
                    val pat = opt.split(":").firstOrNull().orEmpty()
                    next.add(prefix + pat)
                }
            }
            acc = next
        }
        return acc
    }

    @Test
    fun `test_a_consistency_with_meters_json_accepts_valid_and_rejects_single_bit_flips`() {
        val vm = createViewModel()
        val meters = vm.uiState.value.meters
        assertTrue("Expected meters to be loaded", meters.isNotEmpty())

        var totalAcceptedChecked = 0
        var totalFlipRejected = 0
        var totalFlipValidByTable = 0

        for (meter in meters) {
            for (form in meter.forms) {
                val hashwCombos = buildHashwCombos(form.hashw)
                val validSadrByPair = form.pairs.indices.map { pIdx ->
                    val arudPat = form.pairs[pIdx].getOrElse(0) { "" }.split(":").firstOrNull().orEmpty()
                    hashwCombos.map { it + arudPat }.toSet()
                }
                val validAjuzByPair = form.pairs.indices.map { pIdx ->
                    val darbPat = form.pairs[pIdx].getOrElse(1) { "" }.split(":").firstOrNull().orEmpty()
                    hashwCombos.map { it + darbPat }.toSet()
                }

                // توليد عينة متوازنة من الشطور (حتى 200 شطر لكل بحر ونوع: أزواج صدر وعجز)
                val sampleVerses = mutableListOf<Triple<String, String, Int>>()
                outer@ for (pIdx in form.pairs.indices) {
                    val arudPat = form.pairs[pIdx].getOrElse(0) { "" }.split(":").firstOrNull().orEmpty()
                    val darbPat = form.pairs[pIdx].getOrElse(1) { "" }.split(":").firstOrNull().orEmpty()
                    for (hSadr in hashwCombos) {
                        for (hAjuz in hashwCombos) {
                            sampleVerses.add(Triple(hSadr + arudPat, hAjuz + darbPat, pIdx))
                            if (sampleVerses.size >= 100) break@outer
                        }
                    }
                }
                // تأكيد تغطية جميع أزواج pairs حتى إن كانت العينة محدودة
                for (pIdx in form.pairs.indices) {
                    if (sampleVerses.none { it.third == pIdx }) {
                        val arudPat = form.pairs[pIdx].getOrElse(0) { "" }.split(":").firstOrNull().orEmpty()
                        val darbPat = form.pairs[pIdx].getOrElse(1) { "" }.split(":").firstOrNull().orEmpty()
                        val hFirst = hashwCombos.firstOrNull().orEmpty()
                        sampleVerses.add(Triple(hFirst + arudPat, hFirst + darbPat, pIdx))
                    }
                }

                fun isValidPairInTable(sadrBin: String, ajuzBin: String): Boolean {
                    return form.pairs.indices.any { idx ->
                        sadrBin in validSadrByPair[idx] && ajuzBin in validAjuzByPair[idx]
                    }
                }

                for ((sadrBin, ajuzBin, _) in sampleVerses) {
                    // 1. يجب قبول الصيغ المسموحة في الجدول
                    val sadrRes = vm.evaluateShatrAnalysis(syntheticAnalysis(sadrBin), sadrBin, sadrBin, form, isAjuz = false)
                    val ajuzRes = vm.evaluateShatrAnalysis(syntheticAnalysis(ajuzBin), ajuzBin, ajuzBin, form, isAjuz = true)
                    val (pairedSadr, pairedAjuz) = vm.reconcileVersePair(sadrRes, ajuzRes, ajuzBin, form)

                    assertEquals("Expected valid sadr accepted for ${meter.name} (${form.type}): $sadrBin", ShatrWeightStatus.SOUND, pairedSadr.status)
                    assertEquals("Expected valid ajuz accepted for ${meter.name} (${form.type}): $ajuzBin", ShatrWeightStatus.SOUND, pairedAjuz.status)
                    totalAcceptedChecked += 2

                    // 2. قلب بت واحد في الصدر (0 <-> 1)
                    for (bitIdx in sadrBin.indices) {
                        val flippedChar = if (sadrBin[bitIdx] == '1') '0' else '1'
                        val flippedSadr = sadrBin.substring(0, bitIdx) + flippedChar + sadrBin.substring(bitIdx + 1)
                        val fSadrEval = vm.evaluateShatrAnalysis(syntheticAnalysis(flippedSadr), flippedSadr, flippedSadr, form, isAjuz = false)
                        val (fPairedSadr, fPairedAjuz) = vm.reconcileVersePair(fSadrEval, ajuzRes, ajuzBin, form)
                        val accepted = fPairedSadr.status == ShatrWeightStatus.SOUND && fPairedAjuz.status == ShatrWeightStatus.SOUND

                        if (isValidPairInTable(flippedSadr, ajuzBin)) {
                            assertTrue("Flipped sadr matches table so should be accepted (${meter.name}/${form.type}): $flippedSadr", accepted)
                            totalFlipValidByTable++
                        } else {
                            assertFalse("Flipped sadr not in table must be rejected (${meter.name}/${form.type}): $flippedSadr", accepted)
                            totalFlipRejected++
                        }
                    }

                    // 3. قلب بت واحد في العجز (0 <-> 1)
                    for (bitIdx in ajuzBin.indices) {
                        val flippedChar = if (ajuzBin[bitIdx] == '1') '0' else '1'
                        val flippedAjuz = ajuzBin.substring(0, bitIdx) + flippedChar + ajuzBin.substring(bitIdx + 1)
                        val fAjuzEval = vm.evaluateShatrAnalysis(syntheticAnalysis(flippedAjuz), flippedAjuz, flippedAjuz, form, isAjuz = true)
                        val (fPairedSadr, fPairedAjuz) = vm.reconcileVersePair(sadrRes, fAjuzEval, flippedAjuz, form)
                        val accepted = fPairedSadr.status == ShatrWeightStatus.SOUND && fPairedAjuz.status == ShatrWeightStatus.SOUND

                        if (isValidPairInTable(sadrBin, flippedAjuz)) {
                            assertTrue("Flipped ajuz matches table so should be accepted (${meter.name}/${form.type}): $flippedAjuz", accepted)
                            totalFlipValidByTable++
                        } else {
                            assertFalse("Flipped ajuz not in table must be rejected (${meter.name}/${form.type}): $flippedAjuz", accepted)
                            totalFlipRejected++
                        }
                    }
                }
            }
        }

        println("TEST_A_SUMMARY: accepted=$totalAcceptedChecked, flipRejected=$totalFlipRejected, flipMatchedAnotherValid=$totalFlipValidByTable")
        assertTrue(totalAcceptedChecked > 0)
        assertTrue(totalFlipRejected > 0)
    }

    @Test
    fun `test_b_real_verses_sound_on_their_meters_and_broken_on_all_others`() {
        data class RealVerseCase(
            val label: String,
            val expectedMeterId: String,
            val expectedFormType: String,
            val sadr: String,
            val ajuz: String
        )

        val cases = listOf(
            RealVerseCase(
                label = "المتنبي (الطويل)",
                expectedMeterId = "tawil",
                expectedFormType = "تام",
                sadr = "عَلَى قَدْرِ أَهْلِ الْعَزْمِ تَأْتِي الْعَزَائِمُ",
                ajuz = "وَتَأْتِي عَلَى قَدْرِ الْكِرَامِ الْمَكَارِمُ"
            ),
            RealVerseCase(
                label = "المعلقة (الطويل)",
                expectedMeterId = "tawil",
                expectedFormType = "تام",
                sadr = "قِفَا نَبْكِ مِنْ ذِكْرَى حَبِيبٍ وَمَنْزِلِ",
                ajuz = "بِسِقْطِ اللِّوَى بَيْنَ الدَّخُولِ فَحَوْمَلِ"
            ),
            RealVerseCase(
                label = "الوافر",
                expectedMeterId = "wafir",
                expectedFormType = "تام",
                sadr = "إِذَا غَامَرْتَ فِي شَرَفٍ مَرُومٍ",
                ajuz = "فَلَا تَقْنَعْ بِمَا دُونَ النُّجُومِ"
            ),
            RealVerseCase(
                label = "الخفيف",
                expectedMeterId = "khafif",
                expectedFormType = "تام",
                sadr = "لَيْسَ مَنْ مَاتَ فَاسْتَرَاحَ بِمَيْتٍ",
                ajuz = "إِنَّمَا الْمَيْتُ مَيِّتُ الْأَحْيَاءِ"
            )
        )

        val vm = createViewModel()
        val meters = vm.uiState.value.meters

        for (c in cases) {
            for (meter in meters) {
                for (form in meter.forms) {
                    vm.selectMeter(meter.id)
                    vm.selectFormType(form.type)
                    val vId = vm.uiState.value.verses.first().id
                    vm.updateSadr(vId, TextFieldValue(c.sadr))
                    vm.updateAjuz(vId, TextFieldValue(c.ajuz))
                    vm.evaluateAllNow()

                    val eval = vm.uiState.value.verses.first().eval
                    val isExpected = (meter.id == c.expectedMeterId && form.type == c.expectedFormType)
                    if (isExpected) {
                        assertEquals("${c.label} sadr should be SOUND", ShatrWeightStatus.SOUND, eval.sadr.status)
                        assertEquals("${c.label} ajuz should be SOUND", ShatrWeightStatus.SOUND, eval.ajuz.status)
                        assertEquals("${c.label} sadr message", "سليم", eval.sadr.message)
                        assertEquals("${c.label} ajuz message", "سليم", eval.ajuz.message)
                        assertEquals("${c.label} weightOk", true, eval.weightOk)
                    } else {
                        assertEquals(
                            "${c.label} should be broken on ${meter.name} (${form.type})",
                            false,
                            eval.weightOk
                        )
                        assertTrue(
                            "${c.label} should show مكسور on ${meter.name} (${form.type})",
                            eval.sadr.status == ShatrWeightStatus.BROKEN || eval.ajuz.status == ShatrWeightStatus.BROKEN
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `test_c_zihaf_names_and_roles_strictly_match_zihaf_dictionary_without_invention`() {
        val vm = createViewModel()
        val meters = vm.uiState.value.meters

        // 1. التحقق من جميع الأقدام في كل البحور والأنواع من الجدول مباشرة
        for (meter in meters) {
            for (form in meter.forms) {
                val hashwPat = form.hashw.map { opts -> opts.first().split(":")[0] }
                for (pair in form.pairs) {
                    val arudPat = pair[0].split(":")[0]
                    val darbPat = pair[1].split(":")[0]
                    val sadrBin = hashwPat.joinToString("") + arudPat
                    val ajuzBin = hashwPat.joinToString("") + darbPat

                    val sadrEval = vm.evaluateShatrAnalysis(syntheticAnalysis(sadrBin), sadrBin, sadrBin, form, isAjuz = false)
                    val ajuzEval = vm.evaluateShatrAnalysis(syntheticAnalysis(ajuzBin), ajuzBin, ajuzBin, form, isAjuz = true)
                    val (alignedSadr, alignedAjuz) = vm.reconcileVersePair(sadrEval, ajuzEval, ajuzBin, form)

                    assertEquals(form.feetPerShatr, alignedSadr.feet.size)
                    assertEquals(form.feetPerShatr, alignedAjuz.feet.size)

                    alignedSadr.feet.forEachIndexed { idx, foot ->
                        val expectedBase = form.bases[idx]
                        val expectedZihaf = MeterDetector.ZIHAF_DICT[expectedBase]?.get(foot.pattern) ?: "صيغة مسموحة"
                        assertEquals(expectedBase, foot.baseName)
                        assertEquals(expectedZihaf, foot.zihafName)
                        if (idx == alignedSadr.feet.lastIndex) {
                            assertEquals("عروض", foot.endFootRole)
                        } else {
                            assertNull(foot.endFootRole)
                        }
                    }

                    alignedAjuz.feet.forEachIndexed { idx, foot ->
                        val expectedBase = form.bases[idx]
                        val expectedZihaf = MeterDetector.ZIHAF_DICT[expectedBase]?.get(foot.pattern) ?: "صيغة مسموحة"
                        assertEquals(expectedBase, foot.baseName)
                        assertEquals(expectedZihaf, foot.zihafName)
                        if (idx == alignedAjuz.feet.lastIndex) {
                            assertEquals("ضرب", foot.endFootRole)
                        } else {
                            assertNull(foot.endFootRole)
                        }
                    }
                }
            }
        }

        // 2. التحقق الصريح من حالة غير موجودة في zihafDictionary (مستفعلان في مجزوء البسيط) -> "صيغة مسموحة"
        val basitMajzu = meters.first { it.id == "basit" }.forms.first { it.type == "مجزوء" }
        val basitSadr = "1010110" + "10110" + "1010110"
        val basitAjuz = "1010110" + "10110" + "1010100"
        val sEval = vm.evaluateShatrAnalysis(syntheticAnalysis(basitSadr), basitSadr, basitSadr, basitMajzu, isAjuz = false)
        val aEval = vm.evaluateShatrAnalysis(syntheticAnalysis(basitAjuz), basitAjuz, basitAjuz, basitMajzu, isAjuz = true)
        val (_, alignedAjuz) = vm.reconcileVersePair(sEval, aEval, basitAjuz, basitMajzu)
        val lastFoot = alignedAjuz.feet.last()
        assertEquals("مستفعلان", lastFoot.name)
        assertEquals("مستفعلن", lastFoot.baseName)
        assertEquals("صيغة مسموحة", lastFoot.zihafName)
        assertEquals("ضرب", lastFoot.endFootRole)

        // 3. التحقق من أسماء الزحافات في بيت الخفيف الحقيقي (مشعثة / مخبونة)
        vm.selectMeter("khafif")
        vm.selectFormType("تام")
        val vId = vm.uiState.value.verses.first().id
        vm.updateSadr(vId, TextFieldValue("لَيْسَ مَنْ مَاتَ فَاسْتَرَاحَ بِمَيْتٍ"))
        vm.updateAjuz(vId, TextFieldValue("إِنَّمَا الْمَيْتُ مَيِّتُ الْأَحْيَاءِ"))
        vm.evaluateAllNow()
        val khafifEval = vm.uiState.value.verses.first().eval
        assertEquals("مخبونة", khafifEval.sadr.feet.last().zihafName)
        assertEquals("عروض", khafifEval.sadr.feet.last().endFootRole)
        assertEquals("مشعثة", khafifEval.ajuz.feet.last().zihafName)
        assertEquals("ضرب", khafifEval.ajuz.feet.last().endFootRole)
        assertEquals("فاعلاتن", khafifEval.ajuz.feet.last().baseName)
    }
}
