package com.example

import com.example.engine.ArudiEngine
import com.example.engine.QafiyaAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QafiyaAnalyzerTest {

    @Test
    fun `case_1_hawmali_matches_expected`() {
        val result = QafiyaAnalyzer.analyzeSingle("بِسِقْطِ اللِّوَى بَيْنَ الدَّخُولِ فَحَوْمَلِ")
        assertEquals('ل', result.roi)
        assertEquals("كسرة", result.majra)
        assertEquals("ياء", result.wasl)
        assertNull(result.khuruj)
        assertNull(result.radf)
        assertNull(result.taasis)
        assertNull(result.dakheel)
        assertTrue(result.isMutlaqa)
        assertEquals("مجردة", result.kind)
        assertEquals("عالية", result.confidence)
        assertTrue(result.qafiyaText.isNotEmpty())
        assertTrue(result.roiRangeInText != null)
    }

    @Test
    fun `case_2_al_makarimu_matches_expected`() {
        val result = QafiyaAnalyzer.analyzeSingle("وَتَأْتِي عَلَى قَدْرِ الْكِرَامِ الْمَكَارِمُ")
        assertEquals('م', result.roi)
        assertEquals("ضمة", result.majra)
        assertEquals("واو", result.wasl)
        assertNull(result.radf)
        assertEquals("ألف", result.taasis)
        assertEquals('ر', result.dakheel)
        assertTrue(result.isMutlaqa)
        assertEquals("مؤسسة", result.kind)
        assertEquals("عالية", result.confidence)
    }

    @Test
    fun `case_3_al_ahyai_matches_expected`() {
        val result = QafiyaAnalyzer.analyzeSingle("إِنَّمَا الْمَيْتُ مَيِّتُ الْأَحْيَاءِ")
        assertEquals('ء', result.roi)
        assertEquals("كسرة", result.majra)
        assertEquals("ألف", result.radf)
        assertEquals("ياء", result.wasl)
        assertNull(result.taasis)
        assertTrue(result.isMutlaqa)
        assertEquals("مردوفة", result.kind)
        assertEquals("عالية", result.confidence)
    }

    @Test
    fun `case_4_al_qadar_matches_expected`() {
        val result = QafiyaAnalyzer.analyzeSingle("فَلَا بُدَّ أَنْ يَسْتَجِيبَ الْقَدَرْ")
        assertEquals('ر', result.roi)
        assertNull(result.majra)
        assertNull(result.wasl)
        assertFalse(result.isMutlaqa)
        assertEquals("مجردة", result.kind)
        assertEquals("عالية", result.confidence)
    }

    @Test
    fun `case_5_iqwa_between_hawmali_and_manzilu`() {
        val v1Sadr = ArudiEngine.toArudi("أَفَاطِمَ مَهْلًا بَعْضَ هَذَا التَّدَلُّلِ", false)
        val v1Ajuz = ArudiEngine.toArudi("بِسِقْطِ اللِّوَى بَيْنَ الدَّخُولِ فَحَوْمَلِ", true)
        val v2Sadr = ArudiEngine.toArudi("تَرَى بَعَرَ الآرَامِ فِي عَرَصَاتِهَا", false)
        val v2Ajuz = ArudiEngine.toArudi("قِفَا نَبْكِ مِنْ ذِكْرَى حَبِيبٍ وَمَنْزِلُ", true)

        val comparison = QafiyaAnalyzer.compareVerses(listOf(v1Sadr to v1Ajuz, v2Sadr to v2Ajuz))
        assertTrue(comparison.notes.any { it.startsWith("محتمل:") && it.contains("إقواء") })
    }

    @Test
    fun `case_6_itaa_same_qafiya_word_repeated`() {
        val v1Sadr = ArudiEngine.toArudi("أَفَاطِمَ مَهْلًا بَعْضَ هَذَا التَّدَلُّلِ", false)
        val v1Ajuz = ArudiEngine.toArudi("بِسِقْطِ اللِّوَى بَيْنَ الدَّخُولِ فَحَوْمَلِ", true)
        val v2Sadr = ArudiEngine.toArudi("فَإِنَّ صَبَاحَ الْيَوْمِ أَسْفَرَ مُقْبِلًا", false)
        val v2Ajuz = ArudiEngine.toArudi("وَيُبْصِرُ فِينَا النَّاسُ نَزْلَةَ حَوْمَلِ", true)

        // إذا كانت الكلمة نفسها ("فَحَوْمَلِ" و"فَحَوْمَلِ")
        val v2SameAjuz = ArudiEngine.toArudi("وَنَذْكُرُ أَيَّامَ اللِّقَاءِ فَحَوْمَلِ", true)
        val comparison = QafiyaAnalyzer.compareVerses(listOf(v1Sadr to v1Ajuz, v2Sadr to v2SameAjuz))
        assertTrue(comparison.notes.any { it.startsWith("محتمل:") && it.contains("إيطاء") })
    }

    @Test
    fun `additional_rules_al_fata_and_ha_damir_and_tanween_and_manual_override`() {
        val fata = QafiyaAnalyzer.analyzeSingle("وَمَنْ يَتَغَرَّبْ لَمْ يَجِدْ مِثْلَ الْفَتَى")
        assertEquals('ى', fata.roi)
        assertFalse(fata.isMutlaqa)

        val kitabuhu = QafiyaAnalyzer.analyzeSingle("وَيَبْقَى بَعْدَ حُلْوِ الْعُمْرِ كِتَابُهُ")
        assertEquals('ب', kitabuhu.roi)
        assertEquals("هاء", kitabuhu.wasl)
        assertEquals("واو", kitabuhu.khuruj)
        assertEquals("ضمة", kitabuhu.majra)
        assertEquals("ألف", kitabuhu.radf)
        assertEquals("مردوفة", kitabuhu.kind)
        assertEquals("متوسطة", kitabuhu.confidence)

        val tanween = QafiyaAnalyzer.analyzeSingle("وَيَرْوِي صِدْقَ مَا فِي الْقَلْبِ قَلَمٌ")
        assertEquals('م', tanween.roi)
        assertNull(tanween.wasl)
        assertEquals("فيه تنوين", tanween.note)
        assertEquals("متوسطة", tanween.confidence)

        // اختيار يدوي للروي من المرشحين الثلاثة
        assertEquals(3, kitabuhu.roiCandidates.size)
        val lastCand = kitabuhu.roiCandidates.first() // 'ه'
        val overridden = QafiyaAnalyzer.analyzeSingle("وَيَبْقَى بَعْدَ حُلْوِ الْعُمْرِ كِتَابُهُ", overrideRoiCharIndex = lastCand.charIndex)
        assertEquals('ه', overridden.roi)
    }
}
