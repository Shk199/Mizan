package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.SavedVerse
import com.example.engine.ArudiEngine
import com.example.engine.GrammarAnalysisResult
import com.example.engine.MeterDetector
import com.example.engine.MeterMatchResult
import com.example.engine.PoemQafiyaComparison
import com.example.engine.QafiyaAnalyzer
import com.example.engine.QafiyaResult
import com.example.engine.ShatrAnalysis
import com.example.engine.InputValidator
import com.example.engine.ValidationResult
import kotlinx.coroutines.CancellationException
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

data class FullAnalysisData(
    val inputRaw: String,
    val vocalizedSadr: String,
    val vocalizedAjuz: String,
    val meterResult: MeterMatchResult,
    val sadrAnalysis: ShatrAnalysis,
    val ajuzAnalysis: ShatrAnalysis?,
    val grammarData: GrammarAnalysisResult?,
    val qafiyaResult: QafiyaResult? = null,
    val poemComparison: PoemQafiyaComparison? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class AnalyzerViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val savedVerseDao = db.savedVerseDao()

    val savedVerses = savedVerseDao.getAllVerses()

    val sampleVerse = "عَلى قَدْرِ أَهْلِ العَزْمِ تَأْتِي العَزَائِمُ ... وَتَأْتِي عَلَى قَدْرِ الكِرَامِ المَكَارِمُ"

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _optMeter = MutableStateFlow(true)
    val optMeter: StateFlow<Boolean> = _optMeter.asStateFlow()

    private val _optDiacritics = MutableStateFlow(true)
    val optDiacritics: StateFlow<Boolean> = _optDiacritics.asStateFlow()

    private val _optGrammar = MutableStateFlow(true)
    val optGrammar: StateFlow<Boolean> = _optGrammar.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisData = MutableStateFlow<FullAnalysisData?>(null)
    val analysisData: StateFlow<FullAnalysisData?> = _analysisData.asStateFlow()

    private val _symbolsMode = MutableStateFlow("// o") // "// o" or "1 0"
    val symbolsMode: StateFlow<String> = _symbolsMode.asStateFlow()

    private val _isEditingTashkeel = MutableStateFlow(false)
    val isEditingTashkeel: StateFlow<Boolean> = _isEditingTashkeel.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    private val _inputError = MutableStateFlow<String?>(null)
    val inputError: StateFlow<String?> = _inputError.asStateFlow()

    init {
        MeterDetector.initialize(application)
    }

    fun validateInput(text: String): ValidationResult {
        return InputValidator.validateInput(text)
    }

    fun setInputText(text: String) {
        _inputText.value = text
        if (_inputError.value != null) {
            _inputError.value = null
        }
    }

    fun setOptMeter(value: Boolean) {
        _optMeter.value = value
    }

    fun setOptDiacritics(value: Boolean) {
        _optDiacritics.value = value
    }

    fun setOptGrammar(value: Boolean) {
        _optGrammar.value = value
    }

    fun insertSample() {
        _inputText.value = sampleVerse
        _inputError.value = null
        showToast("تم إدراج بيت المتنبي الشهير")
    }

    fun clearInput() {
        _inputText.value = ""
        _analysisData.value = null
        _isEditingTashkeel.value = false
        _inputError.value = null
        showToast("تم مسح الحقل والنتائج")
    }

    fun toggleSymbolsMode() {
        _symbolsMode.value = if (_symbolsMode.value == "// o") "1 0" else "// o"
    }

    fun setEditingTashkeel(editing: Boolean) {
        _isEditingTashkeel.value = editing
    }


    private fun showToast(msg: String) {
        viewModelScope.launch {
            _toastEvent.emit(msg)
        }
    }

    private fun splitVerse(raw: String): Pair<String, String> {
        val text = raw.trim()
        val separators = listOf("...", "…", "/", "–", "-")
        for (sep in separators) {
            if (text.contains(sep)) {
                val parts = text.split(sep, limit = 2)
                return Pair(parts[0].trim(), parts.getOrElse(1) { "" }.trim())
            }
        }
        if (text.contains("\n")) {
            val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            return Pair(lines.getOrElse(0) { "" }, lines.getOrElse(1) { "" })
        }
        val words = text.split(Regex("\\s+"))
        return if (words.size >= 6) {
            val mid = words.size / 2
            Pair(words.take(mid).joinToString(" "), words.drop(mid).joinToString(" "))
        } else {
            Pair(text, "")
        }
    }

    private fun splitMultipleVerses(raw: String): List<Pair<String, String>> {
        val text = raw.trim()
        val separators = listOf("...", "…", "/", "–", "-")
        val blankBlocks = text.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
        if (blankBlocks.size >= 2) {
            return blankBlocks.map { splitVerse(it) }
        }
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size >= 2 && (lines.any { l -> separators.any { sep -> l.contains(sep) } } || lines.size >= 3)) {
            return lines.map { splitVerse(it) }
        }
        return listOf(splitVerse(text))
    }

    fun selectManualRoi(charIndex: Int) {
        val current = _analysisData.value ?: return
        val updatedQafiya = QafiyaAnalyzer.analyzeFromShatrAnalysis(
            sadrAnalysis = current.sadrAnalysis,
            ajuzAnalysis = current.ajuzAnalysis,
            overrideRoiCharIndex = charIndex
        )
        _analysisData.value = current.copy(qafiyaResult = updatedQafiya)
    }

    fun runAnalysis(overrideText: String? = null) {
        val rawInput = overrideText ?: _inputText.value
        val validation = validateInput(rawInput)

        if (validation is ValidationResult.Error) {
            _analysisData.value = null
            _isAnalyzing.value = false
            _isEditingTashkeel.value = false
            _inputError.value = validation.message
            if (validation.hadLinks) {
                showToast("أزلت الروابط من النص.")
            }
            showToast(validation.message)
            return
        }

        val valid = validation as ValidationResult.Valid
        _inputError.value = null
        if (valid.hadLinks) {
            showToast("أزلت الروابط من النص.")
        }
        val targetText = valid.cleanedText

        viewModelScope.launch {
            _isAnalyzing.value = true
            _analysisData.value = null
            _isEditingTashkeel.value = false

            try {
                val parsedVerses = splitMultipleVerses(targetText)
                val (vocalizedSadr, vocalizedAjuz) = parsedVerses.first()

                // المحرك العروضي الحتمي
                val sadrAnalysis = ArudiEngine.toArudi(vocalizedSadr, false)
                val ajuzAnalysis = if (vocalizedAjuz.isNotEmpty()) ArudiEngine.toArudi(vocalizedAjuz, true) else null
                val meterResult = MeterDetector.detectMeter(sadrAnalysis, ajuzAnalysis)

                // تحليل القافية محلياً اعتماداً على مقاطع العروض
                val qafiyaResult = QafiyaAnalyzer.analyzeFromShatrAnalysis(sadrAnalysis, ajuzAnalysis)
                val poemComparison = if (parsedVerses.size > 1) {
                    val verseAnalyses = parsedVerses.mapIndexed { idx, (s, a) ->
                        if (idx == 0) {
                            sadrAnalysis to ajuzAnalysis
                        } else {
                            val sAn = ArudiEngine.toArudi(s, false)
                            val aAn = if (a.isNotEmpty()) ArudiEngine.toArudi(a, true) else null
                            sAn to aAn
                        }
                    }
                    QafiyaAnalyzer.compareVerses(verseAnalyses)
                } else {
                    null
                }

                val resultData = FullAnalysisData(
                    inputRaw = targetText,
                    vocalizedSadr = vocalizedSadr,
                    vocalizedAjuz = vocalizedAjuz,
                    meterResult = meterResult,
                    sadrAnalysis = sadrAnalysis,
                    ajuzAnalysis = ajuzAnalysis,
                    grammarData = null,
                    qafiyaResult = qafiyaResult,
                    poemComparison = poemComparison
                )
                _analysisData.value = resultData

                if (meterResult.isSound) {
                    showToast("اكتمل ميزان البيت بنجاح وسلامة عروضية ✓")
                } else {
                    showToast("الوزن به انكسار عروضي، تم استخراج أقرب البحور")
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                _analysisData.value = null
                showToast("حدث خطأ أثناء التحليل العروضي")
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun saveCurrentVerse() {
        val current = _analysisData.value ?: return
        viewModelScope.launch {
            val verseText = if (current.vocalizedAjuz.isNotEmpty()) {
                "${current.vocalizedSadr} ... ${current.vocalizedAjuz}"
            } else {
                current.vocalizedSadr
            }
            val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val dateStr = formatter.format(Date(current.timestamp))

            savedVerseDao.insertVerse(
                SavedVerse(
                    verse = verseText,
                    meterName = current.meterResult.fullMeterTitle,
                    date = dateStr,
                    timestamp = current.timestamp
                )
            )
            showToast("تم حفظ البيت في المحفوظات بنجاح ✓")
        }
    }

    fun deleteSavedVerse(id: Long) {
        viewModelScope.launch {
            savedVerseDao.deleteVerse(id)
            showToast("تم حذف البيت من المحفوظات")
        }
    }

    fun clearAllSaved() {
        viewModelScope.launch {
            savedVerseDao.clearAll()
            showToast("تم إفراغ قائمة المحفوظات بالكامل")
        }
    }
}
