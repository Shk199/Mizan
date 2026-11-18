package com.example.engine

data class QafiyaCandidate(
    val char: Char,
    val label: String,
    val charIndex: Int
)

data class QafiyaResult(
    val roi: Char,
    val majra: String?,
    val wasl: String?,
    val khuruj: String?,
    val radf: String?,
    val taasis: String?,
    val dakheel: Char?,
    val isMutlaqa: Boolean,
    val kind: String,
    val qafiyaText: String,
    val roiRangeInText: IntRange?,
    val confidence: String,
    val note: String? = null,
    val roiCandidates: List<QafiyaCandidate> = emptyList(),
    val lastWordNormalized: String = ""
)

data class PoemQafiyaComparison(
    val verseCount: Int,
    val isTasri: Boolean,
    val notes: List<String>
)

object QafiyaAnalyzer {

    private data class ParsedChar(
        val char: Char,
        val marks: String
    )

    private val HARAKAT_SET = setOf(
        '\u064B', '\u064C', '\u064D', '\u064E', '\u064F', '\u0650', '\u0651', '\u0652', '\u0670'
    )
    private val TANWEEN_SET = setOf('\u064B', '\u064C', '\u064D')

    fun analyzeSingle(
        shatrRaw: String,
        overrideRoiCharIndex: Int? = null
    ): QafiyaResult {
        val analysis = ArudiEngine.toArudi(shatrRaw, isLastShatr = true)
        return analyzeFromShatrAnalysis(
            sadrAnalysis = analysis,
            ajuzAnalysis = null,
            overrideRoiCharIndex = overrideRoiCharIndex
        )
    }

    fun analyzeFromShatrAnalysis(
        sadrAnalysis: ShatrAnalysis?,
        ajuzAnalysis: ShatrAnalysis?,
        overrideRoiCharIndex: Int? = null
    ): QafiyaResult {
        val targetShatr = if (ajuzAnalysis != null && ajuzAnalysis.words.isNotEmpty()) {
            ajuzAnalysis
        } else {
            sadrAnalysis
        }

        if (targetShatr == null || targetShatr.words.isEmpty()) {
            return emptyResult()
        }

        val lastWord = targetShatr.words.last()
        val parsedChars = parseBaseChars(lastWord.originalText)
        if (parsedChars.isEmpty()) {
            return emptyResult()
        }

        // المرشحون الثلاثة الأخيرة للروي (تُعرض للمستخدم عند لمس شارة الروي)
        val candidateStart = maxOf(0, parsedChars.size - 3)
        val candidates = (candidateStart until parsedChars.size).reversed().map { idx ->
            val pc = parsedChars[idx]
            val shortMark = pc.marks.firstOrNull { it in listOf('\u064E', '\u064F', '\u0650', '\u0652', '\u064B', '\u064C', '\u064D') }
            val label = if (shortMark != null) "${pc.char}$shortMark" else pc.char.toString()
            QafiyaCandidate(char = pc.char, label = label, charIndex = idx)
        }

        // استخراج القافية بناءً على مقاطع العروض (من آخر ساكن إلى الساكن الذي قبله مع المتحرك قبله)
        val flatSyls = targetShatr.words.flatMap { it.syllables }
        val (qafiyaStartSylIdx, lastSakinSylIdx) = findQafiyaSpan(flatSyls)
        val qafiyaSyls = if (lastSakinSylIdx >= 0 && qafiyaStartSylIdx <= lastSakinSylIdx) {
            flatSyls.subList(qafiyaStartSylIdx, lastSakinSylIdx + 1)
        } else {
            flatSyls
        }

        // هل أحدث المحرك إشباعاً في آخر الكلمة؟
        val lastWordSyls = lastWord.syllables
        val hasIshba = detectTrailingIshba(lastWordSyls, parsedChars)
        val ishbaLetter = if (hasIshba) lastWordSyls.lastOrNull()?.letter?.firstOrNull() else null

        // تحديد موضع الروي وفق القواعد أو وفق الاختيار اليدوي
        var roiCharIdx: Int
        var wasl: String? = null
        var khuruj: String? = null
        var confidence = "عالية"
        var note: String? = null
        var hasTanween = false

        val lastIdx = parsedChars.lastIndex
        val lastPc = parsedChars[lastIdx]

        if (overrideRoiCharIndex != null && overrideRoiCharIndex in parsedChars.indices) {
            roiCharIdx = overrideRoiCharIndex
            if (roiCharIdx < lastIdx) {
                val nextPc = parsedChars[roiCharIdx + 1]
                if (nextPc.char == 'ه') {
                    wasl = "هاء"
                    khuruj = resolveHaKhuruj(parsedChars, roiCharIdx + 1, ishbaLetter)
                } else {
                    wasl = letterToMaddName(nextPc.char)
                }
            } else {
                wasl = ishbaLetter?.let { letterToMaddName(it) }
            }
        } else {
            // القاعدة 2(ج): التنوين (بما فيه تنوين الفتح المتبوع بألف غير منطوقة مثل "شعرًا" / "هدًى")
            val tanweenIdx = findTanweenIndex(parsedChars)
            if (tanweenIdx != null) {
                roiCharIdx = tanweenIdx
                hasTanween = true
                wasl = null
                note = "فيه تنوين"
                confidence = "متوسطة"
            } else if (isHaDamirCase(parsedChars)) {
                // القاعدة 2(ب): هاء الضمير الزائدة (ـهُ، ـهِ، ـها، ـهم)
                val haIdx = findHaDamirIndex(parsedChars)
                roiCharIdx = haIdx - 1
                wasl = "هاء"
                khuruj = resolveHaKhuruj(parsedChars, haIdx, ishbaLetter)
                confidence = "متوسطة"
            } else {
                // القاعدة 2 و 3: الحرف الأخير الأصلي (أو حرف المد الأصلي ا/ى/ي الساكنة)
                // نتجاهل ألف واو الجماعة الصامتة في آخر الكلمة ("قالوا" -> الروي أو الوصل على حسب الأصل)
                val effectiveLastIdx = if (
                    lastPc.char == 'ا' &&
                    lastIdx >= 1 &&
                    parsedChars[lastIdx - 1].char == 'و' &&
                    parsedChars[lastIdx - 1].marks.none { it in listOf('\u064E', '\u064F', '\u0650', '\u0651') }
                ) {
                    lastIdx - 1
                } else {
                    lastIdx
                }
                roiCharIdx = effectiveLastIdx
                wasl = if (effectiveLastIdx == lastIdx) ishbaLetter?.let { letterToMaddName(it) } else null
            }
        }

        val roiPc = parsedChars[roiCharIdx]
        val roiChar = roiPc.char

        // القاعدة 3 و 6: المجرى والمطلقة/المقيدة
        val isOriginalTrailingMadd = (roiCharIdx == lastIdx) && !hasIshba && !hasTanween &&
            (roiChar in setOf('ا', 'ى', 'ي', 'و')) &&
            roiPc.marks.none { it in listOf('\u064E', '\u064F', '\u0650') }

        val roiHasSukun = roiPc.marks.contains('\u0652') || isOriginalTrailingMadd

        val isMutlaqa: Boolean
        val majra: String?

        if (hasTanween) {
            isMutlaqa = false
            majra = harakahNameFromMarks(roiPc.marks)
        } else if (roiHasSukun) {
            isMutlaqa = false
            majra = null
        } else {
            // إذا اختار المستخدم الروي أو كان بعده هاء ضمير أو إشباع
            val extractedMajra = harakahNameFromMarks(roiPc.marks)
            if (wasl != null || hasIshba) {
                isMutlaqa = true
                majra = extractedMajra ?: "فتحة"
            } else {
                // حرف أخير بدون سكون ولا إشباع (مثل حالة اختيار حرف في آخر الكلمة بدون حركة ظاهرة)
                isMutlaqa = extractedMajra != null
                majra = extractedMajra
            }
        }

        // القاعدة 5: الردف، التأسيس، الدخيل، نوع القافية
        var radf: String? = null
        var taasis: String? = null
        var dakheel: Char? = null

        if (roiCharIdx >= 1) {
            val prevPc = parsedChars[roiCharIdx - 1]
            val prevIsSakinOrBare = prevPc.marks.isEmpty() || prevPc.marks == "\u0652"

            if (prevIsSakinOrBare && (prevPc.char == 'ا' || prevPc.char == 'آ')) {
                val beforePrevHasSukun = roiCharIdx >= 2 && parsedChars[roiCharIdx - 2].marks.contains('\u0652')
                if (!beforePrevHasSukun && (roiCharIdx - 1 > 0 || prevPc.char == 'آ')) {
                    radf = "ألف"
                }
            } else if (prevIsSakinOrBare && prevPc.char == 'و' && roiCharIdx >= 2) {
                val beforePrevMarks = parsedChars[roiCharIdx - 2].marks
                if (beforePrevMarks.contains('\u064F')) {
                    radf = "واو"
                }
                // الواو الساكنة بعد فتحة حرف لين وليست ردفًا
            } else if (prevIsSakinOrBare && prevPc.char == 'ي' && roiCharIdx >= 2) {
                val beforePrevMarks = parsedChars[roiCharIdx - 2].marks
                if (beforePrevMarks.contains('\u0650')) {
                    radf = "ياء"
                }
                // الياء الساكنة بعد فتحة حرف لين وليست ردفًا
            }
        }

        if (radf == null && roiCharIdx >= 2) {
            val dakheelPc = parsedChars[roiCharIdx - 1]
            val taasisPc = parsedChars[roiCharIdx - 2]
            val dakheelIsVocalized = !dakheelPc.marks.contains('\u0652') &&
                !(dakheelPc.char in setOf('ا', 'ى') && dakheelPc.marks.isEmpty())
            val taasisIsAlif = (taasisPc.char == 'ا' && roiCharIdx - 2 >= 1 && (taasisPc.marks.isEmpty() || taasisPc.marks == "\u0652")) ||
                (taasisPc.char == 'آ')

            if (dakheelIsVocalized && taasisIsAlif) {
                taasis = "ألف"
                dakheel = dakheelPc.char
            }
        }

        val kind = when {
            radf != null -> "مردوفة"
            taasis != null -> "مؤسسة"
            else -> "مجردة"
        }

        val (qafiyaText, roiRange) = formatQafiyaText(
            qafiyaSyls = qafiyaSyls,
            roiChar = roiChar,
            roiPc = roiPc,
            parsedChars = parsedChars,
            roiCharIdx = roiCharIdx,
            hasIshba = hasIshba,
            hasTanween = hasTanween
        )

        val normalizedLastWord = normalizeWordForItaa(lastWord.originalText)

        return QafiyaResult(
            roi = roiChar,
            majra = majra,
            wasl = wasl,
            khuruj = khuruj,
            radf = radf,
            taasis = taasis,
            dakheel = dakheel,
            isMutlaqa = isMutlaqa,
            kind = kind,
            qafiyaText = qafiyaText,
            roiRangeInText = roiRange,
            confidence = confidence,
            note = note,
            roiCandidates = candidates,
            lastWordNormalized = normalizedLastWord
        )
    }

    fun compareVerses(verses: List<Pair<ShatrAnalysis, ShatrAnalysis?>>): PoemQafiyaComparison {
        if (verses.size <= 1) {
            return PoemQafiyaComparison(verseCount = verses.size, isTasri = false, notes = emptyList())
        }

        val analyses = verses.map { (sadr, ajuz) ->
            analyzeFromShatrAnalysis(sadr, ajuz)
        }
        val notes = mutableListOf<String>()

        // 1) التصريع في البيت الأول: اتفاق قافية الصدر مع قافية العجز في البيت الأول
        val firstVerse = verses.first()
        var isTasri = false
        if (firstVerse.second != null && firstVerse.second!!.words.isNotEmpty()) {
            val firstSadrText = firstVerse.first.originalText
            val firstSadrQafiya = analyzeSingle(firstSadrText)
            val firstAjuzQafiya = analyses.first()
            if (firstSadrQafiya.roi == firstAjuzQafiya.roi &&
                firstSadrQafiya.majra == firstAjuzQafiya.majra &&
                firstSadrQafiya.isMutlaqa == firstAjuzQafiya.isMutlaqa &&
                firstSadrQafiya.radf == firstAjuzQafiya.radf
            ) {
                isTasri = true
                notes.add("محتمل: البيت الأول مصرّع لاتفاق قافية الصدر والعجز")
            }
        }

        // 2) اختلاف الروي بين الأبيات
        val distinctRois = analyses.map { it.roi }.distinct()
        if (distinctRois.size > 1) {
            notes.add("محتمل: اختلاف الروي بين الأبيات (${distinctRois.joinToString("، ")})")
        }

        // 3) الإقواء والإصراف (اختلاف حركة الروي)
        val majras = analyses.mapNotNull { it.majra }.distinct()
        val hasDamma = majras.contains("ضمة")
        val hasKasra = majras.contains("كسرة")
        val hasFatha = majras.contains("فتحة")
        if (hasDamma && hasKasra) {
            notes.add("محتمل: إقواء بسبب اختلاف حركة الروي بين الضم والكسر")
        }
        if (hasFatha && (hasDamma || hasKasra)) {
            notes.add("محتمل: إصراف بسبب اختلاف حركة الفتح مع الضم أو الكسر")
        }

        // 4) سناد الردف
        val hasRadfCount = analyses.count { it.radf != null }
        if (hasRadfCount in 1 until analyses.size) {
            notes.add("محتمل: سناد الردف لورود الردف في بعض الأبيات دون بعض")
        }

        // 5) سناد التأسيس
        val hasTaasisCount = analyses.count { it.taasis != null }
        if (hasTaasisCount in 1 until analyses.size) {
            notes.add("محتمل: سناد التأسيس لورود التأسيس في بعض الأبيات دون بعض")
        }

        // 6) الإيطاء المحتمل: تكرار كلمة القافية نفسها في بيتين ضمن 7 أبيات
        var hasItaa = false
        for (i in analyses.indices) {
            val w1 = analyses[i].lastWordNormalized
            if (w1.isEmpty()) continue
            val upper = minOf(analyses.lastIndex, i + 6)
            for (j in (i + 1)..upper) {
                if (analyses[j].lastWordNormalized == w1) {
                    hasItaa = true
                    break
                }
            }
            if (hasItaa) break
        }
        if (hasItaa) {
            notes.add("محتمل: إيطاء لتكرار كلمة القافية نفسها ضمن نطاق 7 أبيات")
        }

        return PoemQafiyaComparison(
            verseCount = verses.size,
            isTasri = isTasri,
            notes = notes
        )
    }

    private fun parseBaseChars(word: String): List<ParsedChar> {
        val cleaned = word
            .replace(Regex("[ـ\\u0640]"), "")
            .replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
            .trim()
        val result = mutableListOf<ParsedChar>()
        var i = 0
        while (i < cleaned.length) {
            val ch = cleaned[i]
            if (ch in '\u0621'..'\u064A') {
                val marks = StringBuilder()
                var j = i + 1
                while (j < cleaned.length && cleaned[j] in HARAKAT_SET) {
                    marks.append(cleaned[j])
                    j++
                }
                result.add(ParsedChar(ch, marks.toString()))
                i = j
            } else {
                i++
            }
        }
        return result
    }

    private fun findQafiyaSpan(flatSyls: List<ArudiSyllable>): Pair<Int, Int> {
        if (flatSyls.isEmpty()) return 0 to -1
        val lastSakinIdx = flatSyls.indexOfLast { it.bit == '0' }
        if (lastSakinIdx < 0) return 0 to flatSyls.lastIndex

        var prevSakinIdx = -1
        for (i in (lastSakinIdx - 1) downTo 0) {
            if (flatSyls[i].bit == '0') {
                prevSakinIdx = i
                break
            }
        }
        if (prevSakinIdx < 0) {
            return 0 to lastSakinIdx
        }

        var startIdx = prevSakinIdx - 1
        while (startIdx > 0 && flatSyls[startIdx].bit != '1') {
            startIdx--
        }
        if (startIdx < 0) startIdx = 0
        return startIdx to lastSakinIdx
    }

    private fun detectTrailingIshba(
        lastWordSyls: List<ArudiSyllable>,
        parsedChars: List<ParsedChar>
    ): Boolean {
        if (lastWordSyls.size < 2 || parsedChars.isEmpty()) return false
        val lastSyl = lastWordSyls.last()
        if (lastSyl.bit != '0') return false
        val lastBase = parsedChars.last()
        val hasShortEndingVowel = lastBase.marks.any { it in listOf('\u064E', '\u064F', '\u0650') } &&
            lastBase.marks.none { it in TANWEEN_SET } &&
            !lastBase.marks.contains('\u0652')
        val isRealMadd = (lastBase.char in setOf('ا', 'ى', 'ي', 'و')) &&
            lastBase.marks.none { it in listOf('\u064E', '\u064F', '\u0650') }
        return hasShortEndingVowel && !isRealMadd && lastSyl.letter in setOf("ا", "و", "ي")
    }

    private fun findTanweenIndex(parsedChars: List<ParsedChar>): Int? {
        if (parsedChars.isEmpty()) return null
        val lastIdx = parsedChars.lastIndex
        if (parsedChars[lastIdx].marks.any { it in TANWEEN_SET }) {
            return lastIdx
        }
        // تنوين الفتح المتبوع بألف التنوين الصامتة أو ألف مقصورة
        if (lastIdx >= 1 &&
            parsedChars[lastIdx].char in setOf('ا', 'ى') &&
            parsedChars[lastIdx].marks.isEmpty() &&
            parsedChars[lastIdx - 1].marks.contains('\u064B')
        ) {
            return lastIdx - 1
        }
        return null
    }

    private fun findHaDamirIndex(parsedChars: List<ParsedChar>): Int {
        val lastIdx = parsedChars.lastIndex
        val lastPc = parsedChars[lastIdx]
        if (lastPc.char == 'ه') return lastIdx
        if (lastIdx >= 1 && parsedChars[lastIdx - 1].char == 'ه') return lastIdx - 1
        return lastIdx
    }

    private fun isHaDamirCase(parsedChars: List<ParsedChar>): Boolean {
        if (parsedChars.size < 3) return false
        val lastIdx = parsedChars.lastIndex
        val lastPc = parsedChars[lastIdx]

        // استبعاد لفظ الجلالة ونحوه
        val bare = parsedChars.map { it.char }.joinToString("")
        if (bare.endsWith("الله") || bare.endsWith("لله") || bare.endsWith("إله")) {
            return false
        }

        // ـهُ / ـهِ
        if (lastPc.char == 'ه' && (lastPc.marks.contains('\u064F') || lastPc.marks.contains('\u0650'))) {
            return true
        }
        // ـها
        if (lastIdx >= 2 && lastPc.char == 'ا' && lastPc.marks.isEmpty() && parsedChars[lastIdx - 1].char == 'ه') {
            return true
        }
        // ـهم (ـهُمْ / ـهِمْ)
        if (lastIdx >= 2 && lastPc.char == 'م' && (lastPc.marks.isEmpty() || lastPc.marks == "\u0652") &&
            parsedChars[lastIdx - 1].char == 'ه' &&
            (parsedChars[lastIdx - 1].marks.contains('\u064F') || parsedChars[lastIdx - 1].marks.contains('\u0650'))
        ) {
            return true
        }
        return false
    }

    private fun resolveHaKhuruj(
        parsedChars: List<ParsedChar>,
        haIdx: Int,
        ishbaLetter: Char?
    ): String? {
        if (haIdx + 1 <= parsedChars.lastIndex) {
            val afterHa = parsedChars[haIdx + 1]
            if (afterHa.char == 'ا') return "ألف"
            return null
        }
        return ishbaLetter?.let { letterToMaddName(it) }
    }

    private fun harakahNameFromMarks(marks: String): String? {
        return when {
            marks.contains('\u064F') || marks.contains('\u064C') -> "ضمة"
            marks.contains('\u0650') || marks.contains('\u064D') -> "كسرة"
            marks.contains('\u064E') || marks.contains('\u064B') -> "فتحة"
            else -> null
        }
    }

    private fun letterToMaddName(ch: Char): String? {
        return when (ch) {
            'ا', 'ى' -> "ألف"
            'و' -> "واو"
            'ي' -> "ياء"
            else -> null
        }
    }

    private fun formatQafiyaText(
        qafiyaSyls: List<ArudiSyllable>,
        roiChar: Char,
        roiPc: ParsedChar,
        parsedChars: List<ParsedChar>,
        roiCharIdx: Int,
        hasIshba: Boolean,
        hasTanween: Boolean
    ): Pair<String, IntRange?> {
        if (qafiyaSyls.isEmpty()) return "" to null

        // عند وجود تنوين في آخر الكلمة، نُبقي حركة التنوين الأصلية بدل نون العروض الساكنة في العرض
        val effectiveSyls = if (hasTanween && qafiyaSyls.size >= 2 && qafiyaSyls.last().letter == "ن") {
            qafiyaSyls.dropLast(1)
        } else {
            qafiyaSyls
        }

        val sb = StringBuilder()
        val charRanges = mutableListOf<IntRange>()

        for (i in effectiveSyls.indices) {
            val syl = effectiveSyls[i]
            val prevSyl = if (i > 0) effectiveSyls[i - 1] else null

            val startPos = sb.length
            sb.append(syl.letter)

            if (hasTanween && i == effectiveSyls.lastIndex) {
                val origTanween = roiPc.marks.firstOrNull { it in TANWEEN_SET }
                if (origTanween != null) {
                    sb.append(origTanween)
                } else if (syl.harakah.isNotEmpty()) {
                    sb.append(syl.harakah)
                }
            } else if (syl.bit == '1') {
                if (syl.harakah.isNotEmpty()) {
                    sb.append(syl.harakah)
                }
            } else {
                // إخفاء السكون فوق حروف المد الصريحة بعد الحركة المجانسة (مثل كَا، مُو، لِي) وإبقاؤه في اللين والسواكن الصحيحة
                val isSilentMadd = when (syl.letter) {
                    "ا", "ى" -> prevSyl != null && prevSyl.bit == '1' && prevSyl.harakah == ArudiEngine.FATHA
                    "و" -> prevSyl != null && prevSyl.bit == '1' && prevSyl.harakah == ArudiEngine.DAMMA
                    "ي" -> prevSyl != null && prevSyl.bit == '1' && prevSyl.harakah == ArudiEngine.KASRA
                    else -> false
                }
                if (!isSilentMadd && syl.harakah.isNotEmpty()) {
                    sb.append(syl.harakah)
                }
            }
            val endPos = sb.length
            charRanges.add(startPos until endPos)
        }

        val text = sb.toString()

        // نحسب موقع الروي بدقة من نهاية الكلمة (مع مراعاة وجود إشباع أو ألف تنوين أو ألف واو جماعة في الأصل)
        var nonIshbaEndIdx = charRanges.lastIndex
        if (hasIshba && nonIshbaEndIdx > 0) {
            nonIshbaEndIdx--
        }

        var charsAfterRoiInOriginal = parsedChars.lastIndex - roiCharIdx
        if (hasTanween && parsedChars.last().char in setOf('ا', 'ى') && parsedChars.last().marks.isEmpty()) {
            charsAfterRoiInOriginal = maxOf(0, charsAfterRoiInOriginal - 1)
        }
        val targetSylIdx = nonIshbaEndIdx - charsAfterRoiInOriginal

        val exactRange = if (targetSylIdx in 0..nonIshbaEndIdx &&
            effectiveSyls[targetSylIdx].letter.firstOrNull() == roiChar
        ) {
            charRanges[targetSylIdx]
        } else {
            // في حال فك الشدة أو غيرها نبحث من النهاية وحتى nonIshbaEndIdx
            var fallback: IntRange? = null
            for (idx in nonIshbaEndIdx downTo 0) {
                if (effectiveSyls[idx].letter.firstOrNull() == roiChar) {
                    fallback = charRanges[idx]
                    break
                }
            }
            fallback
        }

        return text to exactRange
    }

    private fun normalizeWordForItaa(word: String): String {
        return word
            .replace(Regex("[\\u064B-\\u0652\\u0670\\u0640]"), "")
            .trim()
    }

    private fun emptyResult(): QafiyaResult {
        return QafiyaResult(
            roi = ' ',
            majra = null,
            wasl = null,
            khuruj = null,
            radf = null,
            taasis = null,
            dakheel = null,
            isMutlaqa = false,
            kind = "مجردة",
            qafiyaText = "",
            roiRangeInText = null,
            confidence = "عالية"
        )
    }
}
