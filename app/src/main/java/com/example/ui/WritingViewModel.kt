package com.example.ui

import android.app.Application
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.SavedVerse
import com.example.data.WritingDraft
import com.example.data.WritingDraftStore
import com.example.data.WritingVerseDraft
import com.example.engine.ArudiEngine
import com.example.engine.InputValidator
import com.example.engine.MeterCorrector
import com.example.engine.MeterDefinition
import com.example.engine.MeterDetector
import com.example.engine.MeterForm
import com.example.engine.QafiyaAnalyzer
import com.example.engine.QafiyaResult
import com.example.engine.ShatrAnalysis
import com.example.engine.WritingShatrValidation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ShatrWeightStatus {
    EMPTY,
    INVALID_INPUT,
    NEEDS_DIACRITICS,
    INCOMPLETE,
    SOUND,
    BROKEN
}

data class WritingFootUi(
    val name: String,
    val pattern: String,
    val isBroken: Boolean
)

data class ShatrEvaluationUi(
    val status: ShatrWeightStatus = ShatrWeightStatus.EMPTY,
    val message: String? = null,
    val feet: List<WritingFootUi> = emptyList(),
    val brokenFootNumbers: List<Int> = emptyList(),
    val brokenWords: List<String> = emptyList(),
    val brokenCharRanges: List<IntRange> = emptyList(),
    val currentBinary: String = "",
    val expectedBinary: String = "",
    val rawCleanedText: String = "",
    val analysis: ShatrAnalysis? = null,
    val matchingPairIndices: Set<Int> = emptySet()
)

data class VerseEvaluationUi(
    val sadr: ShatrEvaluationUi = ShatrEvaluationUi(),
    val ajuz: ShatrEvaluationUi = ShatrEvaluationUi(),
    val weightOk: Boolean? = null,
    val qafiyaOk: Boolean? = null,
    val qafiyaIsHardError: Boolean = false,
    val qafiyaResult: QafiyaResult? = null,
    val qafiyaMessages: List<String> = emptyList()
)

data class WritingVerseItem(
    val id: Long,
    val sadr: TextFieldValue = TextFieldValue(""),
    val ajuz: TextFieldValue = TextFieldValue(""),
    val eval: VerseEvaluationUi = VerseEvaluationUi()
)

data class ActiveFieldTarget(
    val verseId: Long,
    val isAjuz: Boolean
)

data class WritingUiState(
    val meters: List<MeterDefinition> = emptyList(),
    val selectedMeterId: String = "tawil",
    val selectedFormType: String = "تام",
    val noFixedQafiya: Boolean = true,
    val roiInput: String = "",
    val roiError: String? = null,
    val majraOption: String = "ضمة",
    val tasriFirstVerse: Boolean = false,
    val isSettingsExpanded: Boolean = true,
    val verses: List<WritingVerseItem> = listOf(WritingVerseItem(id = 1L)),
    val activeField: ActiveFieldTarget? = ActiveFieldTarget(1L, false),
    val draftLoaded: Boolean = false
) {
    val selectedMeter: MeterDefinition?
        get() = meters.find { it.id == selectedMeterId } ?: meters.firstOrNull()

    val selectedForm: MeterForm?
        get() = selectedMeter?.forms?.find { it.type == selectedFormType }
            ?: selectedMeter?.forms?.firstOrNull()

    val soundVerseCount: Int
        get() = verses.count { v ->
            v.eval.weightOk == true && (v.eval.qafiyaOk != false)
        }
}

class WritingViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val savedVerseDao = db.savedVerseDao()

    private val _uiState = MutableStateFlow(WritingUiState())
    val uiState: StateFlow<WritingUiState> = _uiState.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    private var nextVerseId = 2L
    private var debounceJob: Job? = null
    private var saveJob: Job? = null

    init {
        MeterDetector.initialize(application)
        val meters = MeterDetector.METERS
        val defaultMeter = meters.firstOrNull()
        val defaultForm = defaultMeter?.forms?.firstOrNull()
        _uiState.value = _uiState.value.copy(
            meters = meters,
            selectedMeterId = defaultMeter?.id ?: "tawil",
            selectedFormType = defaultForm?.type ?: "تام"
        )
        loadSavedDraft()
    }

    private fun loadSavedDraft() {
        viewModelScope.launch {
            val draft = WritingDraftStore.loadDraft(getApplication())
            if (draft != null) {
                val meters = _uiState.value.meters
                val meter = meters.find { it.id == draft.meterId } ?: meters.firstOrNull()
                val form = meter?.forms?.find { it.type == draft.formType } ?: meter?.forms?.firstOrNull()
                val loadedVerses = draft.verses.mapIndexed { idx, vd ->
                    WritingVerseItem(
                        id = (idx + 1).toLong(),
                        sadr = TextFieldValue(vd.sadr, TextRange(vd.sadr.length)),
                        ajuz = TextFieldValue(vd.ajuz, TextRange(vd.ajuz.length))
                    )
                }.ifEmpty { listOf(WritingVerseItem(id = 1L)) }
                nextVerseId = (loadedVerses.maxOfOrNull { it.id } ?: 1L) + 1L
                _uiState.value = _uiState.value.copy(
                    selectedMeterId = meter?.id ?: draft.meterId,
                    selectedFormType = form?.type ?: draft.formType,
                    noFixedQafiya = draft.noFixedQafiya,
                    roiInput = draft.roiChar,
                    roiError = validateRoiInput(draft.roiChar, draft.noFixedQafiya),
                    majraOption = draft.majraOption,
                    tasriFirstVerse = draft.tasriFirstVerse,
                    isSettingsExpanded = draft.isSettingsExpanded,
                    verses = loadedVerses,
                    activeField = ActiveFieldTarget(loadedVerses.first().id, false),
                    draftLoaded = true
                )
                evaluateAllNow()
            } else {
                _uiState.value = _uiState.value.copy(draftLoaded = true)
            }
        }
    }

    fun selectMeter(meterId: String) {
        val meter = _uiState.value.meters.find { it.id == meterId } ?: return
        val preferredForm = meter.forms.find { it.type == _uiState.value.selectedFormType }
            ?: meter.forms.find { it.type == "تام" }
            ?: meter.forms.firstOrNull()
        _uiState.value = _uiState.value.copy(
            selectedMeterId = meter.id,
            selectedFormType = preferredForm?.type ?: "تام"
        )
        evaluateAllNow()
        persistDraft()
    }

    fun selectFormType(formType: String) {
        val meter = _uiState.value.selectedMeter ?: return
        if (meter.forms.none { it.type == formType }) return
        _uiState.value = _uiState.value.copy(selectedFormType = formType)
        evaluateAllNow()
        persistDraft()
    }

    fun setNoFixedQafiya(noFixed: Boolean) {
        _uiState.value = _uiState.value.copy(
            noFixedQafiya = noFixed,
            roiError = validateRoiInput(_uiState.value.roiInput, noFixed)
        )
        evaluateAllNow()
        persistDraft()
    }

    fun updateRoiInput(raw: String) {
        val cleaned = raw.filter { it in '\u0621'..'\u064A' }.take(1)
        val err = if (raw.isNotEmpty() && cleaned.isEmpty()) {
            "أدخل حرفًا عربيًا واحدًا"
        } else {
            validateRoiInput(cleaned, _uiState.value.noFixedQafiya)
        }
        _uiState.value = _uiState.value.copy(
            roiInput = if (cleaned.isNotEmpty()) cleaned else raw.take(1),
            roiError = err
        )
        evaluateAllNow()
        persistDraft()
    }

    private fun validateRoiInput(roi: String, noFixed: Boolean): String? {
        if (noFixed || roi.isEmpty()) return null
        return if (roi.length == 1 && roi[0] in '\u0621'..'\u064A') {
            null
        } else {
            "أدخل حرفًا عربيًا واحدًا للروي"
        }
    }

    fun selectMajraOption(majra: String) {
        _uiState.value = _uiState.value.copy(majraOption = majra)
        evaluateAllNow()
        persistDraft()
    }

    fun setTasriFirstVerse(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(tasriFirstVerse = enabled)
        evaluateAllNow()
        persistDraft()
    }

    fun setSettingsExpanded(expanded: Boolean) {
        _uiState.value = _uiState.value.copy(isSettingsExpanded = expanded)
        persistDraft()
    }

    fun startWriting() {
        _uiState.value = _uiState.value.copy(isSettingsExpanded = false)
        evaluateAllNow()
        persistDraft()
    }

    fun setActiveField(verseId: Long, isAjuz: Boolean) {
        _uiState.value = _uiState.value.copy(activeField = ActiveFieldTarget(verseId, isAjuz))
    }

    fun updateSadr(verseId: Long, newValue: TextFieldValue) {
        val updated = _uiState.value.verses.map { item ->
            if (item.id == verseId) item.copy(sadr = newValue) else item
        }
        _uiState.value = _uiState.value.copy(
            verses = updated,
            activeField = ActiveFieldTarget(verseId, false)
        )
        scheduleDebouncedEvaluation()
        persistDraft()
    }

    fun updateAjuz(verseId: Long, newValue: TextFieldValue) {
        val updated = _uiState.value.verses.map { item ->
            if (item.id == verseId) item.copy(ajuz = newValue) else item
        }
        _uiState.value = _uiState.value.copy(
            verses = updated,
            activeField = ActiveFieldTarget(verseId, true)
        )
        scheduleDebouncedEvaluation()
        persistDraft()
    }

    fun insertHaraka(mark: String) {
        val state = _uiState.value
        val target = state.activeField ?: state.verses.firstOrNull()?.let { ActiveFieldTarget(it.id, false) } ?: return
        val verse = state.verses.find { it.id == target.verseId } ?: return
        val currentField = if (target.isAjuz) verse.ajuz else verse.sadr

        val start = minOf(currentField.selection.start, currentField.selection.end).coerceIn(0, currentField.text.length)
        val end = maxOf(currentField.selection.start, currentField.selection.end).coerceIn(0, currentField.text.length)

        val newText = buildString {
            append(currentField.text.substring(0, start))
            append(mark)
            append(currentField.text.substring(end))
        }
        val newCursor = start + mark.length
        val updatedValue = TextFieldValue(
            text = newText,
            selection = TextRange(newCursor)
        )

        if (target.isAjuz) {
            updateAjuz(verse.id, updatedValue)
        } else {
            updateSadr(verse.id, updatedValue)
        }
    }

    fun addVerse() {
        val newId = nextVerseId++
        val newVerse = WritingVerseItem(id = newId)
        _uiState.value = _uiState.value.copy(
            verses = _uiState.value.verses + newVerse,
            activeField = ActiveFieldTarget(newId, false)
        )
        persistDraft()
    }

    fun deleteVerse(verseId: Long) {
        val current = _uiState.value.verses
        val remaining = if (current.size <= 1) {
            listOf(WritingVerseItem(id = current.first().id))
        } else {
            current.filterNot { it.id == verseId }
        }
        val active = _uiState.value.activeField?.takeIf { target ->
            remaining.any { it.id == target.verseId }
        } ?: ActiveFieldTarget(remaining.first().id, false)

        _uiState.value = _uiState.value.copy(
            verses = remaining,
            activeField = active
        )
        evaluateAllNow()
        persistDraft()
    }

    fun resetNewDraft() {
        val freshId = nextVerseId++
        val defaultMeter = _uiState.value.meters.firstOrNull()
        val defaultForm = defaultMeter?.forms?.firstOrNull()
        _uiState.value = _uiState.value.copy(
            selectedMeterId = defaultMeter?.id ?: "tawil",
            selectedFormType = defaultForm?.type ?: "تام",
            noFixedQafiya = true,
            roiInput = "",
            roiError = null,
            majraOption = "ضمة",
            tasriFirstVerse = false,
            isSettingsExpanded = true,
            verses = listOf(WritingVerseItem(id = freshId)),
            activeField = ActiveFieldTarget(freshId, false)
        )
        viewModelScope.launch {
            WritingDraftStore.clearDraft(getApplication())
            showToast("تم بدء مسودة جديدة")
        }
    }

    fun formatPoemForCopy(): String {
        val state = _uiState.value
        val meterName = state.selectedMeter?.name ?: ""
        val formType = state.selectedForm?.type ?: ""
        val qafiyaSummary = if (state.noFixedQafiya || state.roiInput.isBlank()) {
            "بدون قافية محددة"
        } else {
            "روي ${state.roiInput} (${state.majraOption})"
        }
        val lines = state.verses.mapNotNull { item ->
            val s = item.sadr.text.trim()
            val a = item.ajuz.text.trim()
            when {
                s.isNotEmpty() && a.isNotEmpty() -> "$s ... $a"
                s.isNotEmpty() -> s
                a.isNotEmpty() -> a
                else -> null
            }
        }
        val body = lines.joinToString("\n")
        return if (body.isNotEmpty()) {
            "$body\nالبحر: $meterName ($formType)، القافية: $qafiyaSummary"
        } else {
            "البحر: $meterName ($formType)، القافية: $qafiyaSummary"
        }
    }

    fun savePoem() {
        val state = _uiState.value
        val lines = state.verses.mapNotNull { item ->
            val s = item.sadr.text.trim()
            val a = item.ajuz.text.trim()
            when {
                s.isNotEmpty() && a.isNotEmpty() -> "$s ... $a"
                s.isNotEmpty() -> s
                a.isNotEmpty() -> a
                else -> null
            }
        }
        if (lines.isEmpty()) {
            showToast("اكتب بيتًا واحدًا على الأقل قبل الحفظ")
            return
        }
        val meterName = state.selectedMeter?.name ?: "غير محدد"
        val formType = state.selectedForm?.type ?: "تام"
        val now = System.currentTimeMillis()
        val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        viewModelScope.launch {
            savedVerseDao.insertVerse(
                SavedVerse(
                    verse = lines.joinToString("\n"),
                    meterName = "بَحْرُ $meterName ($formType)",
                    date = formatter.format(Date(now)),
                    timestamp = now
                )
            )
            showToast("تم حفظ القصيدة في المحفوظات ✓")
        }
    }

    private fun scheduleDebouncedEvaluation() {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(300L)
            evaluateAllNow()
        }
    }

    fun evaluateAllNow() {
        val state = _uiState.value
        val form = state.selectedForm ?: return

        // 1) تقييم الوزن لكل شطر على حدة، ثم فحص توافق زوج (العروض، الضرب)
        val weightEvaluated = state.verses.map { verse ->
            val sadrUi = evaluateSingleShatr(verse.sadr.text, form, isAjuz = false)
            var ajuzUi = evaluateSingleShatr(verse.ajuz.text, form, isAjuz = true)

            if (sadrUi.status == ShatrWeightStatus.SOUND && ajuzUi.status == ShatrWeightStatus.SOUND) {
                val commonPairs = sadrUi.matchingPairIndices.intersect(ajuzUi.matchingPairIndices)
                if (commonPairs.isEmpty() && ajuzUi.analysis != null) {
                    val correction = MeterCorrector.findClosestPatternForForm(
                        shatrAnalysis = ajuzUi.analysis,
                        rawShatrText = verse.ajuz.text,
                        form = form,
                        isAjuz = true
                    )
                    ajuzUi = ajuzUi.copy(
                        status = ShatrWeightStatus.BROKEN,
                        message = "العروض والضرب لا يكوّنان زوجًا مسموحًا في هذا البحر",
                        feet = correction.feet.map { WritingFootUi(it.foot.name, it.foot.pattern, it.isBroken) },
                        brokenFootNumbers = correction.brokenFootNumbers,
                        brokenWords = correction.brokenWords,
                        brokenCharRanges = correction.brokenCharRangesInText,
                        currentBinary = ajuzUi.analysis.binaryPattern,
                        expectedBinary = correction.expectedBinary
                    )
                }
            }

            val weightOk = when {
                sadrUi.status == ShatrWeightStatus.BROKEN || ajuzUi.status == ShatrWeightStatus.BROKEN -> false
                sadrUi.status == ShatrWeightStatus.INVALID_INPUT || ajuzUi.status == ShatrWeightStatus.INVALID_INPUT -> false
                sadrUi.status == ShatrWeightStatus.SOUND && ajuzUi.status == ShatrWeightStatus.SOUND -> true
                else -> null
            }

            verse.copy(
                eval = VerseEvaluationUi(
                    sadr = sadrUi,
                    ajuz = ajuzUi,
                    weightOk = weightOk
                )
            )
        }

        // 2) تقييم القافية لكل بيت مكتمل العجز + المقارنة بين الأبيات (إقواء / إصراف / إيطاء / تصريع)
        val baselineQafiya = if (state.noFixedQafiya) {
            weightEvaluated.firstOrNull {
                it.eval.ajuz.status in setOf(ShatrWeightStatus.SOUND, ShatrWeightStatus.BROKEN) &&
                    it.eval.ajuz.analysis != null
            }?.let { QafiyaAnalyzer.analyzeFromShatrAnalysis(it.eval.sadr.analysis, it.eval.ajuz.analysis) }
        } else {
            null
        }

        val completedQafiyas = mutableMapOf<Long, QafiyaResult>()
        weightEvaluated.forEach { verse ->
            val ajuzStatus = verse.eval.ajuz.status
            if (ajuzStatus in setOf(ShatrWeightStatus.SOUND, ShatrWeightStatus.BROKEN) && verse.eval.ajuz.analysis != null) {
                completedQafiyas[verse.id] = QafiyaAnalyzer.analyzeFromShatrAnalysis(
                    verse.eval.sadr.analysis,
                    verse.eval.ajuz.analysis
                )
            }
        }

        val finalVerses = weightEvaluated.mapIndexed { index, verse ->
            val qRes = completedQafiyas[verse.id]
            val qMsgs = mutableListOf<String>()
            var hasHardError = false
            var hasWarning = false

            if (qRes != null) {
                val fixedRoi = if (!state.noFixedQafiya && state.roiError == null && state.roiInput.isNotEmpty()) {
                    state.roiInput[0]
                } else {
                    baselineQafiya?.roi
                }
                val expectedMajraOption = if (!state.noFixedQafiya && state.roiError == null && state.roiInput.isNotEmpty()) {
                    state.majraOption
                } else if (baselineQafiya != null) {
                    if (!baselineQafiya.isMutlaqa) "مقيدة" else baselineQafiya.majra
                } else {
                    null
                }

                if (fixedRoi != null && qRes.roi != fixedRoi) {
                    hasHardError = true
                    qMsgs.add("الروي ‹$fixedRoi› وليس ‹${qRes.roi}›")
                }

                if (expectedMajraOption != null) {
                    if (expectedMajraOption == "مقيدة" && qRes.isMutlaqa) {
                        hasWarning = true
                        qMsgs.add("محتمل: قافية مطلقة والمطلوب مقيدة")
                    } else if (expectedMajraOption != "مقيدة" && !qRes.isMutlaqa) {
                        hasWarning = true
                        qMsgs.add("محتمل: قافية مقيدة والمطلوب مطلقة ($expectedMajraOption)")
                    } else if (expectedMajraOption != "مقيدة" && qRes.majra != null && qRes.majra != expectedMajraOption) {
                        hasWarning = true
                        val isIqwa = (expectedMajraOption in setOf("ضمة", "كسرة")) && (qRes.majra in setOf("ضمة", "كسرة"))
                        if (isIqwa) {
                            qMsgs.add("محتمل: إقواء (اختلاف المجرى بين $expectedMajraOption و${qRes.majra})")
                        } else {
                            qMsgs.add("محتمل: إصراف (اختلاف المجرى بين $expectedMajraOption و${qRes.majra})")
                        }
                    }
                }

                // فحص الإيطاء المحتمل في نطاق 7 أبيات
                val wordNorm = qRes.lastWordNormalized
                if (wordNorm.isNotEmpty()) {
                    val lower = maxOf(0, index - 6)
                    val repeatedEarlier = (lower until index).any { prevIdx ->
                        val prevId = weightEvaluated[prevIdx].id
                        completedQafiyas[prevId]?.lastWordNormalized == wordNorm
                    }
                    if (repeatedEarlier) {
                        hasWarning = true
                        qMsgs.add("محتمل: إيطاء لتكرار كلمة القافية ضمن 7 أبيات")
                    }
                }
            }

            // التصريع في البيت الأول إن كان مفعّلًا واكتمل الصدر
            if (index == 0 && state.tasriFirstVerse) {
                val sadrSt = verse.eval.sadr
                if (sadrSt.status in setOf(ShatrWeightStatus.SOUND, ShatrWeightStatus.BROKEN) && sadrSt.analysis != null) {
                    val sadrQ = QafiyaAnalyzer.analyzeFromShatrAnalysis(sadrSt.analysis, null)
                    val targetRoi = if (!state.noFixedQafiya && state.roiInput.isNotEmpty()) {
                        state.roiInput[0]
                    } else {
                        qRes?.roi
                    }
                    val targetMajra = if (!state.noFixedQafiya && state.roiInput.isNotEmpty()) {
                        if (state.majraOption == "مقيدة") null else state.majraOption
                    } else {
                        qRes?.majra
                    }
                    val targetMutlaqa = if (!state.noFixedQafiya && state.roiInput.isNotEmpty()) {
                        state.majraOption != "مقيدة"
                    } else {
                        qRes?.isMutlaqa
                    }

                    if (targetRoi != null &&
                        (sadrQ.roi != targetRoi ||
                            (targetMajra != null && sadrQ.majra != targetMajra) ||
                            (targetMutlaqa != null && sadrQ.isMutlaqa != targetMutlaqa))
                    ) {
                        hasHardError = true
                        qMsgs.add("البيت الأول غير مصرّع (قافية الصدر لا تطابق القافية)")
                    }
                }
            }

            val qafiyaOk = when {
                hasHardError || hasWarning -> false
                qRes != null -> true
                else -> null
            }

            verse.copy(
                eval = verse.eval.copy(
                    qafiyaOk = qafiyaOk,
                    qafiyaIsHardError = hasHardError,
                    qafiyaResult = qRes,
                    qafiyaMessages = qMsgs
                )
            )
        }

        _uiState.value = _uiState.value.copy(verses = finalVerses)
    }

    private fun evaluateSingleShatr(
        rawText: String,
        form: MeterForm,
        isAjuz: Boolean
    ): ShatrEvaluationUi {
        return when (val v = InputValidator.validateShatrForWriting(rawText)) {
            is WritingShatrValidation.Empty -> ShatrEvaluationUi(status = ShatrWeightStatus.EMPTY)
            is WritingShatrValidation.Error -> ShatrEvaluationUi(
                status = ShatrWeightStatus.INVALID_INPUT,
                message = v.message
            )
            is WritingShatrValidation.NeedsDiacritics -> ShatrEvaluationUi(
                status = ShatrWeightStatus.NEEDS_DIACRITICS,
                message = "شكّل الشطر لتظهر النتيجة"
            )
            is WritingShatrValidation.Valid -> {
                val analysis = ArudiEngine.toArudi(v.cleanedText, isLastShatr = isAjuz)
                val matchRes = MeterDetector.matchShatrForForm(analysis, form, isAjuz)
                if (matchRes.isSound) {
                    ShatrEvaluationUi(
                        status = ShatrWeightStatus.SOUND,
                        message = "سليم",
                        feet = matchRes.matchedFeet.map { WritingFootUi(it.name, it.pattern, isBroken = false) },
                        currentBinary = analysis.binaryPattern,
                        rawCleanedText = v.cleanedText,
                        analysis = analysis,
                        matchingPairIndices = matchRes.matchingPairIndices
                    )
                } else if (analysis.binaryPattern.length < matchRes.minAllowedLength) {
                    ShatrEvaluationUi(
                        status = ShatrWeightStatus.INCOMPLETE,
                        message = "لم يكتمل",
                        currentBinary = analysis.binaryPattern,
                        rawCleanedText = v.cleanedText,
                        analysis = analysis
                    )
                } else {
                    val correction = MeterCorrector.findClosestPatternForForm(
                        shatrAnalysis = analysis,
                        rawShatrText = rawText,
                        form = form,
                        isAjuz = isAjuz
                    )
                    ShatrEvaluationUi(
                        status = ShatrWeightStatus.BROKEN,
                        message = "مكسور",
                        feet = correction.feet.map { WritingFootUi(it.foot.name, it.foot.pattern, it.isBroken) },
                        brokenFootNumbers = correction.brokenFootNumbers,
                        brokenWords = correction.brokenWords,
                        brokenCharRanges = correction.brokenCharRangesInText,
                        currentBinary = correction.currentBinary,
                        expectedBinary = correction.expectedBinary,
                        rawCleanedText = v.cleanedText,
                        analysis = analysis
                    )
                }
            }
        }
    }

    private fun persistDraft() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            val s = _uiState.value
            val draft = WritingDraft(
                meterId = s.selectedMeterId,
                formType = s.selectedFormType,
                noFixedQafiya = s.noFixedQafiya,
                roiChar = s.roiInput,
                majraOption = s.majraOption,
                tasriFirstVerse = s.tasriFirstVerse,
                isSettingsExpanded = s.isSettingsExpanded,
                verses = s.verses.map { WritingVerseDraft(it.sadr.text, it.ajuz.text) }
            )
            WritingDraftStore.saveDraft(getApplication(), draft)
        }
    }

    fun showToast(msg: String) {
        viewModelScope.launch {
            _toastEvent.emit(msg)
        }
    }
}
