package com.example.domain

data class OcrDetectedRow(
    val id: Long,
    val n1Text: String,
    val n2Text: String,
    val sourceLine: String
)

data class OcrParseResult(
    val rawText: String,
    val detectedName: String?,
    val detectedRows: List<OcrDetectedRow>,
    val detectedPtAmount: String?
)

object OcrPayrollParser {
    // Matches "100 x 54", "100 × 54", "100 * 54", optionally followed by "/ 12 = 450"
    private val MULTIPLY_PAIR_REGEX = Regex(
        """(\d[\d.,]*)\s*[xX×*]\s*(\d[\d.,]*)"""
    )

    // Matches standalone numbers (integers, decimals, thousands)
    private val NUMBER_TOKEN_REGEX = Regex("""\d+(?:[.,]\d+)*""")

    // Matches "NAMA: BPK" or "Nama : Budi"
    private val NAME_PREFIX_REGEX = Regex("""^(?:nama|karyawan|pegawai)\s*[:=\-]\s*(.+)$""", RegexOption.IGNORE_CASE)

    // Matches "PT: 100" or "Potongan 100.000"
    private val PT_LINE_REGEX = Regex("""(?:^|\b)(?:pt|potongan)\b.*?(\d+(?:[.,]\d+)*)""", RegexOption.IGNORE_CASE)

    // Lines that represent computed summary lines rather than input rows
    private val SUMMARY_LINE_REGEX = Regex("""^(?:total|jumlah|subtotal|grand\s*total|akhir)""", RegexOption.IGNORE_CASE)

    fun parseRecognizedText(rawText: String): OcrParseResult {
        val lines = rawText
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        var detectedName: String? = null
        var detectedPt: String? = null
        val detectedRows = mutableListOf<OcrDetectedRow>()
        val unpairedSingleNumbers = mutableListOf<Pair<String, String>>()
        var nextId = 1L

        for (line in lines) {
            // 1. Check explicit NAMA: prefix
            val nameMatch = NAME_PREFIX_REGEX.find(line)
            if (nameMatch != null && detectedName == null) {
                val candidate = nameMatch.groupValues[1].trim()
                if (candidate.isNotEmpty()) {
                    detectedName = candidate
                }
                continue
            }

            // 2. Check PT / Potongan line (keep strictly separate from main calculation rows)
            val ptMatch = PT_LINE_REGEX.find(line)
            if (ptMatch != null) {
                if (detectedPt == null) {
                    detectedPt = normalizeOcrNumberToken(ptMatch.groupValues[1])
                }
                continue
            }

            // 3. Skip summary lines like "TOTAL = 1.050"
            if (SUMMARY_LINE_REGEX.containsMatchIn(line)) {
                continue
            }

            // 4. Priority match: explicit multiplication expression (N1 x N2 ...)
            val multMatch = MULTIPLY_PAIR_REGEX.find(line)
            if (multMatch != null) {
                val n1 = normalizeOcrNumberToken(multMatch.groupValues[1])
                val n2 = normalizeOcrNumberToken(multMatch.groupValues[2])
                detectedRows.add(
                    OcrDetectedRow(
                        id = nextId++,
                        n1Text = n1,
                        n2Text = n2,
                        sourceLine = line
                    )
                )
                continue
            }

            // 5. Fallback: line with 2+ numeric tokens (e.g. table columns "100  54")
            val numbers = NUMBER_TOKEN_REGEX.findAll(line)
                .map { normalizeOcrNumberToken(it.value) }
                .filter { it.isNotEmpty() }
                .toList()

            when {
                numbers.size >= 2 -> {
                    // Ignore leading serial number like "1. 100 54" if line starts with "1." or "1)"
                    val adjusted = if (
                        numbers.size >= 3 &&
                        Regex("""^\d+[.)]\s+""").containsMatchIn(line)
                    ) {
                        numbers.drop(1)
                    } else {
                        numbers
                    }
                    if (adjusted.size >= 2) {
                        detectedRows.add(
                            OcrDetectedRow(
                                id = nextId++,
                                n1Text = adjusted[0],
                                n2Text = adjusted[1],
                                sourceLine = line
                            )
                        )
                    }
                }
                numbers.size == 1 -> {
                    unpairedSingleNumbers.add(numbers[0] to line)
                }
                else -> {
                    // Pure alphabetic line near the top might be an employee name
                    if (detectedName == null && line.length in 2..36 && line.any { it.isLetter() }) {
                        val cleaned = line.replace(Regex("""[^a-zA-Z\s.'-]"""), "").trim()
                        if (cleaned.length >= 2) {
                            detectedName = cleaned
                        }
                    }
                }
            }
        }

        // If no explicit pairs were found, pair up sequential standalone numbers (e.g. vertical column scan)
        if (detectedRows.isEmpty() && unpairedSingleNumbers.size >= 2) {
            var i = 0
            while (i + 1 < unpairedSingleNumbers.size) {
                val first = unpairedSingleNumbers[i]
                val second = unpairedSingleNumbers[i + 1]
                detectedRows.add(
                    OcrDetectedRow(
                        id = nextId++,
                        n1Text = first.first,
                        n2Text = second.first,
                        sourceLine = "${first.second} / ${second.second}"
                    )
                )
                i += 2
            }
        }

        return OcrParseResult(
            rawText = rawText,
            detectedName = detectedName,
            detectedRows = detectedRows,
            detectedPtAmount = detectedPt
        )
    }

    private fun normalizeOcrNumberToken(token: String): String {
        val parsed = PayrollCalculator.parseNumber(token) ?: return token.trim()
        return PayrollCalculator.toPlainExactString(parsed)
    }
}
