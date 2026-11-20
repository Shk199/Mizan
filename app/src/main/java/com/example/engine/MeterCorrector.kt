package com.example.engine

import kotlin.math.min

object MeterCorrector {

    private data class LevResult(
        val distance: Int,
        val operations: List<LevOp>
    )

    private data class LevOp(
        val op: String, // "replace", "insert", "delete"
        val indexA: Int,
        val indexB: Int
    )

    private fun levenshteinDistance(a: String, b: String): LevResult {
        val m = a.length
        val n = b.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                if (a[i - 1] == b[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1]
                } else {
                    dp[i][j] = 1 + min(
                        dp[i - 1][j], // delete
                        min(
                            dp[i][j - 1], // insert
                            dp[i - 1][j - 1] // replace
                        )
                    )
                }
            }
        }

        var i = m
        var j = n
        val ops = mutableListOf<LevOp>()

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && a[i - 1] == b[j - 1]) {
                i--
                j--
            } else if (i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] + 1) {
                ops.add(0, LevOp("replace", i - 1, j - 1))
                i--
                j--
            } else if (i > 0 && dp[i][j] == dp[i - 1][j] + 1) {
                ops.add(0, LevOp("delete", i - 1, j))
                i--
            } else if (j > 0 && dp[i][j] == dp[i][j - 1] + 1) {
                ops.add(0, LevOp("insert", i, j - 1))
                j--
            } else {
                break
            }
        }

        return LevResult(dp[m][n], ops)
    }

    private fun generateRepresentativePattern(form: MeterForm): String {
        val hashwBits = form.hashw.map { opts -> opts.firstOrNull()?.split(":")?.get(0) ?: "" }
        val arudBit = form.pairs.firstOrNull()?.firstOrNull()?.split(":")?.get(0) ?: ""
        return hashwBits.joinToString("") + arudBit
    }

    fun findClosestMeters(
        sadrAnalysis: ShatrAnalysis,
        ajuzAnalysis: ShatrAnalysis?,
        allMeters: List<MeterDefinition>
    ): BrokenMeterCorrection {
        val combinedCurrent = sadrAnalysis.binaryPattern + (ajuzAnalysis?.binaryPattern ?: "")
        val candidates = mutableListOf<CandidateResult>()

        for (meter in allMeters) {
            for (form in meter.forms) {
                val repShatr = generateRepresentativePattern(form)
                val repFull = if (ajuzAnalysis != null) repShatr + repShatr else repShatr

                val lev = levenshteinDistance(combinedCurrent, repFull)
                candidates.add(
                    CandidateResult(
                        meter = meter,
                        form = form,
                        distance = lev.distance,
                        expectedPattern = repFull,
                        operations = lev.operations
                    )
                )
            }
        }

        candidates.sortBy { it.distance }

        val top3 = candidates.take(3).map { c ->
            ClosestMeterInfo(
                id = c.meter.id,
                name = c.meter.name,
                type = c.form.type,
                distance = c.distance,
                expectedPattern = c.expectedPattern
            )
        }

        val best = candidates.firstOrNull() ?: CandidateResult(
            meter = MeterDefinition("unknown", "غير محدد", "", "", "", "", emptyList()),
            form = MeterForm("تام", 4, emptyList(), emptyList(), emptyList()),
            distance = 0,
            expectedPattern = "",
            operations = emptyList()
        )

        val brokenPositions = mutableListOf<BrokenPosition>()
        val sadrLen = sadrAnalysis.binaryPattern.length

        for (op in best.operations) {
            val isSadr = op.indexA < sadrLen
            val targetAnalysis = if (isSadr) sadrAnalysis else ajuzAnalysis ?: continue
            val localBitIndex = if (isSadr) op.indexA else (op.indexA - sadrLen)

            var runningBits = 0
            var foundWordIndex = 0
            var foundChar = "موضع صوتي"
            var wordText = ""

            for (w in targetAnalysis.words.indices) {
                val wordObj = targetAnalysis.words[w]
                val wordBits = wordObj.binaryPattern.length
                if (runningBits + wordBits > localBitIndex) {
                    foundWordIndex = w
                    wordText = wordObj.originalText
                    val sylIndex = localBitIndex - runningBits
                    if (sylIndex in wordObj.syllables.indices) {
                        foundChar = wordObj.syllables[sylIndex].letter
                    }
                    break
                }
                runningBits += wordBits
            }

            val detail = when (op.op) {
                "replace" -> "يلزم تحويل هذا الحرف من " + if (op.indexA < combinedCurrent.length && combinedCurrent[op.indexA] == '1') "متحرك إلى ساكن" else "ساكن إلى متحرك"
                "delete" -> "هذا المقطع زائد عروضياً ويلزم حذفه أو تخفيفه"
                else -> "ينقص هذا الموضع حركة أو حرف مد لتحقيق استقامة الوزن"
            }

            brokenPositions.add(
                BrokenPosition(
                    shatr = if (isSadr) "sadr" else "ajuz",
                    wordIndex = foundWordIndex,
                    word = wordText,
                    charIndex = localBitIndex,
                    letter = foundChar,
                    operation = op.op,
                    detail = detail
                )
            )
        }

        val currentSymbols = ArudiEngine.binaryToTraditionalSymbols(combinedCurrent)
        val expectedSymbols = ArudiEngine.binaryToTraditionalSymbols(best.expectedPattern)

        val keyWords = brokenPositions.map { it.word.ifEmpty { it.letter } }.filter { it.isNotEmpty() }.take(3).joinToString(" و ")
        val explanation = "الوزن به انكسار عروضي لا يطابق أي بحر تماماً. أقرب وزن هو بحر ${best.meter.name} (${best.form.type}) بفارق ${best.distance} تعديلات صوتية، وتتركز أغلب مواضع الكسر في تفعيلات ${if (keyWords.isNotEmpty()) "($keyWords)" else "المقاطع الأخيرة"}."

        return BrokenMeterCorrection(
            closestMeters = top3,
            brokenPositions = brokenPositions,
            currentBinary = combinedCurrent,
            expectedBinary = best.expectedPattern,
            currentSymbols = currentSymbols,
            expectedSymbols = expectedSymbols,
            explanation = explanation
        )
    }

    data class FormCorrectionFoot(
        val foot: FootMatch,
        val isBroken: Boolean
    )

    data class FormPatternCorrection(
        val distance: Int,
        val expectedBinary: String,
        val currentBinary: String,
        val feet: List<FormCorrectionFoot>,
        val brokenFootNumbers: List<Int>,
        val brokenWords: List<String>,
        val brokenCharRangesInText: List<IntRange>
    )

    fun findClosestPatternForForm(
        shatrAnalysis: ShatrAnalysis,
        rawShatrText: String,
        form: MeterForm,
        isAjuz: Boolean,
        allowedPairIndices: Set<Int>? = null
    ): FormPatternCorrection {
        data class PatternCand(
            val fullPattern: String,
            val feet: List<FootMatch>,
            val footBitRanges: List<IntRange>
        )

        val hashwParsed = form.hashw.map { opts ->
            opts.map { entry ->
                val parts = entry.split(":")
                parts.getOrElse(0) { "" } to parts.getOrElse(1) { "" }
            }
        }
        val filteredPairs = if (allowedPairIndices != null && allowedPairIndices.isNotEmpty()) {
            form.pairs.filterIndexed { idx, _ -> idx in allowedPairIndices }
        } else {
            form.pairs
        }
        val endItems = filteredPairs.map { pair ->
            val raw = if (isAjuz) pair.getOrElse(1) { "" } else pair.getOrElse(0) { "" }
            val parts = raw.split(":")
            parts.getOrElse(0) { "" } to parts.getOrElse(1) { "" }
        }.distinct()

        // نبني كل الصيغ المسموحة لهذا البحر والنوع (hashw ثم العروض/الضرب)
        val candidates = mutableListOf<PatternCand>()
        fun buildCombinations(pos: Int, acc: List<Pair<String, String>>) {
            if (pos == hashwParsed.size) {
                for (endPair in endItems) {
                    val allPairs = acc + endPair
                    val sb = StringBuilder()
                    val feetList = mutableListOf<FootMatch>()
                    val ranges = mutableListOf<IntRange>()
                    for (idx in allPairs.indices) {
                        val (pat, name) = allPairs[idx]
                        val start = sb.length
                        sb.append(pat)
                        val end = sb.length
                        ranges.add(start until end)
                        feetList.add(
                            FootMatch(
                                name = name,
                                pattern = pat,
                                description = if (idx == allPairs.lastIndex) (if (isAjuz) "ضرب" else "عروض") else "حشو",
                                isEndFoot = idx == allPairs.lastIndex
                            )
                        )
                    }
                    candidates.add(PatternCand(sb.toString(), feetList, ranges))
                }
                return
            }
            for (opt in hashwParsed[pos]) {
                buildCombinations(pos + 1, acc + opt)
            }
        }
        buildCombinations(0, emptyList())

        val currentBinary = shatrAnalysis.binaryPattern
        var bestCand: PatternCand? = null
        var bestLev: LevResult? = null

        for (cand in candidates) {
            val lev = levenshteinDistance(currentBinary, cand.fullPattern)
            if (bestLev == null || lev.distance < bestLev.distance ||
                (lev.distance == bestLev.distance && kotlin.math.abs(cand.fullPattern.length - currentBinary.length) <
                    kotlin.math.abs((bestCand?.fullPattern?.length ?: 0) - currentBinary.length))
            ) {
                bestLev = lev
                bestCand = cand
            }
        }

        if (bestCand == null || bestLev == null) {
            return FormPatternCorrection(
                distance = 0,
                expectedBinary = "",
                currentBinary = currentBinary,
                feet = emptyList(),
                brokenFootNumbers = emptyList(),
                brokenWords = emptyList(),
                brokenCharRangesInText = emptyList()
            )
        }

        // تحديد التفعيلات المتأثرة ومواقع الكلمات والحروف عبر srcIndex
        val brokenFootIndices = mutableSetOf<Int>()
        val brokenWordIndices = linkedSetOf<Int>()
        val brokenCharRanges = mutableListOf<IntRange>()

        // حساب مواضع الكلمات في النص الأصلي للشطر
        val wordSpansInRaw = computeWordSpansInRaw(rawShatrText, shatrAnalysis.words.map { it.originalText })

        for (op in bestLev.operations) {
            val bIdx = op.indexB.coerceIn(0, maxOf(0, bestCand.fullPattern.length - 1))
            val footIdx = bestCand.footBitRanges.indexOfFirst { bIdx in it }
                .takeIf { it >= 0 } ?: bestCand.feet.lastIndex
            if (footIdx >= 0) {
                brokenFootIndices.add(footIdx + 1)
            }

            if (shatrAnalysis.words.isNotEmpty()) {
                val aIdx = op.indexA.coerceIn(0, maxOf(0, currentBinary.length - 1))
                var runningBits = 0
                for (wIdx in shatrAnalysis.words.indices) {
                    val wordObj = shatrAnalysis.words[wIdx]
                    val wordBits = wordObj.binaryPattern.length
                    if (runningBits + wordBits > aIdx || wIdx == shatrAnalysis.words.lastIndex) {
                        brokenWordIndices.add(wIdx)
                        val sylIdx = (aIdx - runningBits).coerceIn(0, maxOf(0, wordObj.syllables.lastIndex))
                        if (sylIdx in wordObj.syllables.indices && wIdx in wordSpansInRaw.indices) {
                            val syl = wordObj.syllables[sylIdx]
                            val span = wordSpansInRaw[wIdx]
                            val rangeInRaw = mapSrcIndexToRawRange(rawShatrText, span, wordObj.originalText, syl.srcIndex)
                            if (rangeInRaw != null) {
                                brokenCharRanges.add(rangeInRaw)
                            }
                        }
                        break
                    }
                    runningBits += wordBits
                }
            }
        }

        val feetStatus = bestCand.feet.mapIndexed { idx, foot ->
            FormCorrectionFoot(foot = foot, isBroken = (idx + 1) in brokenFootIndices)
        }
        val brokenWords = brokenWordIndices.mapNotNull { wIdx ->
            shatrAnalysis.words.getOrNull(wIdx)?.originalText
        }

        return FormPatternCorrection(
            distance = bestLev.distance,
            expectedBinary = bestCand.fullPattern,
            currentBinary = currentBinary,
            feet = feetStatus,
            brokenFootNumbers = brokenFootIndices.sorted(),
            brokenWords = brokenWords,
            brokenCharRangesInText = mergeOverlappingRanges(brokenCharRanges)
        )
    }

    private fun computeWordSpansInRaw(rawText: String, words: List<String>): List<IntRange> {
        val spans = mutableListOf<IntRange>()
        var cursor = 0
        for (w in words) {
            val found = rawText.indexOf(w, startIndex = cursor)
            if (found >= 0) {
                spans.add(found until (found + w.length))
                cursor = found + w.length
            } else {
                spans.add(0 until 0)
            }
        }
        return spans
    }

    private fun mapSrcIndexToRawRange(
        rawText: String,
        wordSpan: IntRange,
        originalWord: String,
        srcIndex: Int
    ): IntRange? {
        if (wordSpan.isEmpty() || originalWord.isEmpty()) return null
        val noDiacritics = originalWord.replace(Regex("[\\u064B-\\u0652\\u0670]"), "")
        val relativePronouns = setOf("التي", "الذي", "الذين", "اللاتي", "اللائي", "اللواتي", "اللذان", "اللتان")
        val prefixOffset = if (noDiacritics.startsWith("ال") && noDiacritics !in relativePronouns) {
            val m = Regex("^ال[\\u064B-\\u0652\\u0670]?").find(originalWord)
            m?.value?.length ?: 0
        } else {
            0
        }
        val localIdx = (srcIndex + prefixOffset).coerceIn(0, originalWord.lastIndex)
        var start = wordSpan.first + localIdx
        if (start !in rawText.indices) return null
        // إذا وقع على حركة نتراجع خطوة للحرف الأساسي
        while (start > wordSpan.first && rawText[start] in '\u064B'..'\u0652' || (start > wordSpan.first && rawText[start] == '\u0670')) {
            start--
        }
        var end = start + 1
        while (end < wordSpan.last + 1 && end < rawText.length &&
            (rawText[end] in '\u064B'..'\u0652' || rawText[end] == '\u0670')
        ) {
            end++
        }
        return start until end
    }

    private fun mergeOverlappingRanges(ranges: List<IntRange>): List<IntRange> {
        if (ranges.isEmpty()) return emptyList()
        val sorted = ranges.sortedBy { it.first }
        val merged = mutableListOf<IntRange>()
        var current = sorted.first()
        for (r in sorted.drop(1)) {
            if (r.first <= current.last + 1) {
                current = current.first..maxOf(current.last, r.last)
            } else {
                merged.add(current)
                current = r
            }
        }
        merged.add(current)
        return merged
    }

    private data class CandidateResult(
        val meter: MeterDefinition,
        val form: MeterForm,
        val distance: Int,
        val expectedPattern: String,
        val operations: List<LevOp>
    )
}

