package com.example.domain

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

@Serializable
enum class RoundingModeType {
    NONE,
    FLOOR_TO_STEP
}

@Serializable
data class RoundingConfig(
    val mode: RoundingModeType = RoundingModeType.FLOOR_TO_STEP,
    val step: String = "100"
) {
    fun stepBigDecimal(): BigDecimal {
        val parsed = PayrollCalculator.parseNumber(step)
        return if (parsed != null && parsed > BigDecimal.ZERO) parsed else BigDecimal("100")
    }

    fun isDefaultFloor100(): Boolean =
        mode == RoundingModeType.FLOOR_TO_STEP &&
            stepBigDecimal().compareTo(BigDecimal("100")) == 0

    fun labelIndonesian(): String = when {
        mode == RoundingModeType.NONE -> "Asli (Tanpa Pembulatan)"
        isDefaultFloor100() -> "Default (Bulat ke Bawah 100)"
        else -> {
            val s = PayrollCalculator.formatNumber(stepBigDecimal())
            "Bulat ke Bawah ($s)"
        }
    }

    fun shortBadge(): String = when {
        mode == RoundingModeType.NONE -> "Asli"
        isDefaultFloor100() -> "Default ↓ 100"
        else -> "↓ ${PayrollCalculator.formatNumber(stepBigDecimal())}"
    }

    companion object {
        val DEFAULT = RoundingConfig(mode = RoundingModeType.FLOOR_TO_STEP, step = "100")
        val ASLI = RoundingConfig(mode = RoundingModeType.NONE, step = "1")
    }
}

data class RowCalculationResult(
    val n1: BigDecimal,
    val n2: BigDecimal,
    val rawResult: BigDecimal,
    val roundedResult: BigDecimal,
    val roundingConfig: RoundingConfig
) {
    val isRoundedDifferent: Boolean
        get() = rawResult.compareTo(roundedResult) != 0
}

data class EmployeeCalculationSummary(
    val employeeId: Long,
    val name: String,
    val rows: List<RowCalculationResult>,
    val subtotalRaw: BigDecimal,
    val subtotalRounded: BigDecimal,
    val hasPt: Boolean,
    val ptAmount: BigDecimal,
    val ptNote: String,
    val finalTotal: BigDecimal
)

data class GrandPayrollSummary(
    val employees: List<EmployeeCalculationSummary>,
    val totalRowCount: Int,
    val totalBeforePtRaw: BigDecimal,
    val totalBeforePtRounded: BigDecimal,
    val totalPtDeductions: BigDecimal,
    val grandFinalTotal: BigDecimal
) {
    val hasAnyPt: Boolean
        get() = totalPtDeductions.compareTo(BigDecimal.ZERO) > 0
}

object PayrollCalculator {
    private val DIVISOR = BigDecimal("12")
    private val INDONESIAN_LOCALE = Locale("id", "ID")
    private val SYMBOLS_ID = DecimalFormatSymbols(INDONESIAN_LOCALE).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }

    /**
     * Parses user-typed numeric text safely into BigDecimal.
     * Supports plain numbers ("100"), decimals with dot ("54.5") or comma ("54,5"),
     * and Indonesian thousands format ("1.000.000" or "1.250,50").
     */
    fun parseNumber(input: String): BigDecimal? {
        val cleaned = input
            .replace("Rp", "", ignoreCase = true)
            .replace(" ", "")
            .trim()
        if (cleaned.isEmpty()) return null

        val normalized = when {
            cleaned.contains(',') && cleaned.contains('.') -> {
                // Indonesian format e.g. 1.250,5 -> 1250.5
                cleaned.replace(".", "").replace(",", ".")
            }
            cleaned.contains(',') -> {
                val commaCount = cleaned.count { it == ',' }
                if (commaCount == 1) {
                    cleaned.replace(",", ".")
                } else {
                    cleaned.replace(",", "")
                }
            }
            cleaned.contains('.') -> {
                val dotCount = cleaned.count { it == '.' }
                if (dotCount > 1) {
                    // Multiple dots are Indonesian thousands separators e.g. 1.000.000
                    cleaned.replace(".", "")
                } else {
                    cleaned
                }
            }
            else -> cleaned
        }

        return normalized.toBigDecimalOrNull()
    }

    /**
     * Core mandatory formula: N1 × N2 ÷ 12 = N
     * Never alters the formula. Preserves high-precision raw value alongside custom floor value.
     */
    fun calculateRow(
        n1: BigDecimal,
        n2: BigDecimal,
        roundingConfig: RoundingConfig
    ): RowCalculationResult {
        val product = n1.multiply(n2)
        // Use exact division if terminating, otherwise 8 decimal places with HALF_UP for clean display/storage
        val raw = try {
            product.divide(DIVISOR)
        } catch (_: ArithmeticException) {
            product.divide(DIVISOR, 8, RoundingMode.HALF_UP)
        }.stripTrailingZerosNormalized()

        val rounded = applyCustomRounding(raw, roundingConfig)
        return RowCalculationResult(
            n1 = n1.stripTrailingZerosNormalized(),
            n2 = n2.stripTrailingZerosNormalized(),
            rawResult = raw,
            roundedResult = rounded,
            roundingConfig = roundingConfig
        )
    }

    /**
     * Applies user-configured custom floor rounding downward.
     * When mode == NONE, returns exact raw value without any rounding.
     * When mode == FLOOR_TO_STEP, floors downward to the nearest multiple of `step`:
     *   floor(raw / step) * step
     */
    fun applyCustomRounding(raw: BigDecimal, config: RoundingConfig): BigDecimal {
        if (config.mode == RoundingModeType.NONE) {
            return raw.stripTrailingZerosNormalized()
        }
        val step = config.stepBigDecimal()
        if (step.compareTo(BigDecimal.ZERO) <= 0) {
            return raw.stripTrailingZerosNormalized()
        }
        val units = raw.divide(step, 0, RoundingMode.FLOOR)
        return units.multiply(step).stripTrailingZerosNormalized()
    }

    /**
     * Computes a single employee's payroll summary.
     * PT deduction is strictly optional and kept completely separate from calculation rows.
     */
    fun calculateEmployeeSummary(
        employeeId: Long,
        name: String,
        rows: List<RowCalculationResult>,
        hasPt: Boolean,
        ptAmount: BigDecimal,
        ptNote: String = ""
    ): EmployeeCalculationSummary {
        var subtotalRaw = BigDecimal.ZERO
        var subtotalRounded = BigDecimal.ZERO
        for (row in rows) {
            subtotalRaw = subtotalRaw.add(row.rawResult)
            subtotalRounded = subtotalRounded.add(row.roundedResult)
        }
        subtotalRaw = subtotalRaw.stripTrailingZerosNormalized()
        subtotalRounded = subtotalRounded.stripTrailingZerosNormalized()

        val effectivePt = if (hasPt && ptAmount > BigDecimal.ZERO) {
            ptAmount.stripTrailingZerosNormalized()
        } else {
            BigDecimal.ZERO
        }
        val appliedPtFlag = hasPt && effectivePt > BigDecimal.ZERO
        val finalTotal = if (appliedPtFlag) {
            subtotalRounded.subtract(effectivePt).stripTrailingZerosNormalized()
        } else {
            subtotalRounded
        }

        return EmployeeCalculationSummary(
            employeeId = employeeId,
            name = name,
            rows = rows,
            subtotalRaw = subtotalRaw,
            subtotalRounded = subtotalRounded,
            hasPt = appliedPtFlag,
            ptAmount = effectivePt,
            ptNote = ptNote,
            finalTotal = finalTotal
        )
    }

    /**
     * Computes grand total across all employees.
     * Deduplicates strictly by unique employeeId so no employee is ever counted twice.
     */
    fun calculateGrandSummary(summaries: List<EmployeeCalculationSummary>): GrandPayrollSummary {
        val uniqueEmployees = summaries.distinctBy { it.employeeId }
        var totalRows = 0
        var totalRaw = BigDecimal.ZERO
        var totalRounded = BigDecimal.ZERO
        var totalPt = BigDecimal.ZERO
        var grandFinal = BigDecimal.ZERO

        for (emp in uniqueEmployees) {
            totalRows += emp.rows.size
            totalRaw = totalRaw.add(emp.subtotalRaw)
            totalRounded = totalRounded.add(emp.subtotalRounded)
            if (emp.hasPt) {
                totalPt = totalPt.add(emp.ptAmount)
            }
            grandFinal = grandFinal.add(emp.finalTotal)
        }

        return GrandPayrollSummary(
            employees = uniqueEmployees,
            totalRowCount = totalRows,
            totalBeforePtRaw = totalRaw.stripTrailingZerosNormalized(),
            totalBeforePtRounded = totalRounded.stripTrailingZerosNormalized(),
            totalPtDeductions = totalPt.stripTrailingZerosNormalized(),
            grandFinalTotal = grandFinal.stripTrailingZerosNormalized()
        )
    }

    /**
     * Formats number in clean Indonesian locale (e.g. 1.050 or 458,33).
     */
    fun formatNumber(value: BigDecimal, maxDecimals: Int = 2): String {
        val normalized = value.stripTrailingZerosNormalized()
        val pattern = if (maxDecimals <= 0) {
            "#,##0"
        } else {
            "#,##0." + "#".repeat(maxDecimals)
        }
        val df = DecimalFormat(pattern, SYMBOLS_ID).apply {
            roundingMode = RoundingMode.HALF_UP
        }
        return df.format(normalized)
    }

    /**
     * Formats raw value before rounding with up to 4 decimals so fractional precision is transparent.
     */
    fun formatRawNumber(value: BigDecimal): String = formatNumber(value, maxDecimals = 4)

    fun formatRupiah(value: BigDecimal, maxDecimals: Int = 4): String {
        val isNegative = value < BigDecimal.ZERO
        val absValue = value.abs()
        val formatted = formatNumber(absValue, maxDecimals)
        return if (isNegative) "-Rp $formatted" else "Rp $formatted"
    }

    fun formatRowEquation(row: RowCalculationResult): String {
        val n1Str = formatNumber(row.n1, maxDecimals = 4)
        val n2Str = formatNumber(row.n2, maxDecimals = 4)
        val rawStr = formatRawNumber(row.rawResult)
        val roundedStr = formatNumber(row.roundedResult, maxDecimals = 2)
        return if (row.isRoundedDifferent) {
            "$n1Str × $n2Str / 12 = $rawStr → $roundedStr"
        } else {
            "$n1Str × $n2Str / 12 = $rawStr"
        }
    }

    fun formatEmployeeLedgerText(emp: EmployeeCalculationSummary): String = buildString {
        appendLine("NAMA: ${emp.name.uppercase(INDONESIAN_LOCALE)}")
        emp.rows.forEach { row ->
            appendLine(formatRowEquation(row))
        }
        appendLine("TOTAL = ${formatRupiah(emp.subtotalRounded)}")
        if (emp.hasPt) {
            appendLine("PT:")
            val noteSuffix = if (emp.ptNote.isNotBlank()) " (${emp.ptNote})" else ""
            appendLine("Potongan$noteSuffix = ${formatRupiah(emp.ptAmount)}")
            append("TOTAL AKHIR = ${formatRupiah(emp.finalTotal)}")
        }
    }.trimEnd()

    fun formatGrandLedgerText(grand: GrandPayrollSummary, title: String? = null): String = buildString {
        appendLine("=== HITUNG GAJIAN PRO ===")
        if (!title.isNullOrBlank()) {
            appendLine(title)
        }
        appendLine()
        grand.employees.forEachIndexed { idx, emp ->
            appendLine(formatEmployeeLedgerText(emp))
            if (idx < grand.employees.lastIndex) {
                appendLine("----------------------------")
            }
        }
        appendLine("============================")
        appendLine("TOTAL SEBELUM PT = ${formatRupiah(grand.totalBeforePtRounded)}")
        if (grand.hasAnyPt) {
            appendLine("TOTAL POTONGAN PT = ${formatRupiah(grand.totalPtDeductions)}")
        }
        append("TOTAL AKHIR KESELURUHAN = ${formatRupiah(grand.grandFinalTotal)}")
    }

    fun BigDecimal.stripTrailingZerosNormalized(): BigDecimal {
        if (this.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO
        val stripped = this.stripTrailingZeros()
        return if (stripped.scale() < 0) stripped.setScale(0) else stripped
    }

    fun toPlainExactString(value: BigDecimal): String =
        value.stripTrailingZerosNormalized().toPlainString()

    val PRESET_ROUNDING_STEPS = listOf("1", "50", "100", "500", "1000", "5000")
}
