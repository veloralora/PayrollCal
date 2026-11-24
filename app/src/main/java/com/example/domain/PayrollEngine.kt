package com.example.domain

import com.example.domain.PayrollCalculator.stripTrailingZerosNormalized
import java.math.BigDecimal
import java.math.RoundingMode

object PayrollEngine {

    /**
     * Applies FormulaConfig to a row's base product (N1 × N2).
     * - Default is ÷ 12 (active), matching N1 × N2 ÷ 12 = N.
     * - Can be changed to ÷, ×, +, or − with any user-chosen operand.
     * - If formula is disabled, bypasses the trailing operation and returns baseAmount unchanged.
     * - Zero operand for MULTIPLY/ADD/SUBTRACT is valid and applied.
     * - For DIVIDE, operand must be > 0 (returns baseAmount defensively if invalid).
     */
    fun applyFormula(baseAmount: BigDecimal, formula: FormulaConfig): BigDecimal {
        if (!formula.enabled) {
            return baseAmount.stripTrailingZerosNormalized()
        }
        val operand = formula.operandBigDecimal() ?: return baseAmount.stripTrailingZerosNormalized()
        val result = when (formula.operation) {
            FormulaOperation.DIVIDE -> {
                if (operand.compareTo(BigDecimal.ZERO) <= 0) {
                    baseAmount
                } else {
                    try {
                        baseAmount.divide(operand)
                    } catch (_: ArithmeticException) {
                        baseAmount.divide(operand, 8, RoundingMode.HALF_UP)
                    }
                }
            }
            FormulaOperation.MULTIPLY -> baseAmount.multiply(operand)
            FormulaOperation.ADD -> baseAmount.add(operand)
            FormulaOperation.SUBTRACT -> baseAmount.subtract(operand)
        }
        return result.stripTrailingZerosNormalized()
    }

    /**
     * Computes a single N1 × N2 [op operand] = N row.
     */
    fun computeRow(
        row: CalculationRowItem,
        formula: FormulaConfig,
        roundingConfig: RoundingConfig = RoundingConfig()
    ): ComputedRowResult {
        val n1 = PayrollCalculator.parseNumber(row.n1Text)
        val n2 = PayrollCalculator.parseNumber(row.n2Text)
        if (n1 == null || n2 == null) {
            return ComputedRowResult(
                row = row,
                isValid = false,
                n1 = n1 ?: BigDecimal.ZERO,
                n2 = n2 ?: BigDecimal.ZERO,
                product = BigDecimal.ZERO,
                afterFormulaRaw = BigDecimal.ZERO,
                afterFormula = BigDecimal.ZERO,
                formulaApplied = formula
            )
        }

        val cleanN1 = n1.stripTrailingZerosNormalized()
        val cleanN2 = n2.stripTrailingZerosNormalized()
        val product = cleanN1.multiply(cleanN2).stripTrailingZerosNormalized()
        val afterFormulaRaw = applyFormula(product, formula)
        val afterFormula = PayrollCalculator.applyCustomRounding(afterFormulaRaw, roundingConfig)

        return ComputedRowResult(
            row = row,
            isValid = true,
            n1 = cleanN1,
            n2 = cleanN2,
            product = product,
            afterFormulaRaw = afterFormulaRaw,
            afterFormula = afterFormula,
            formulaApplied = formula
        )
    }

    /**
     * Computes an employee's full calculation:
     * 1. Computes each N1 × N2 row and applies active FormulaConfig (or skips if disabled).
     * 2. Sums all valid rows into subtotalAfterFormula.
     * 3. Applies filled additions (+) and deductions (−). Empty cells stay null and skipped;
     *    explicit "0" is preserved as BigDecimal.ZERO.
     */
    fun computeMember(
        member: CalculationMemberEntry,
        formula: FormulaConfig,
        columns: List<AdjustmentColumn>,
        roundingConfig: RoundingConfig = RoundingConfig()
    ): ComputedMemberResult {
        val computedRows = member.rows.map { computeRow(it, formula, roundingConfig) }
        var subtotalRaw = BigDecimal.ZERO
        var subtotalRounded = BigDecimal.ZERO
        var validCount = 0

        for (r in computedRows) {
            if (r.isValid) {
                validCount++
                subtotalRaw = subtotalRaw.add(r.afterFormulaRaw)
                subtotalRounded = subtotalRounded.add(r.afterFormula)
            }
        }
        subtotalRaw = subtotalRaw.stripTrailingZerosNormalized()
        subtotalRounded = subtotalRounded.stripTrailingZerosNormalized()

        val adjAmounts = LinkedHashMap<String, BigDecimal?>(columns.size)
        var netAdj = BigDecimal.ZERO

        for (col in columns) {
            val rawCell = member.adjustmentValues[col.id]?.trim().orEmpty()
            if (rawCell.isEmpty()) {
                adjAmounts[col.id] = null
                continue
            }
            val parsedCell = PayrollCalculator.parseNumber(rawCell)
            if (parsedCell == null) {
                adjAmounts[col.id] = null
                continue
            }
            val cleanCell = parsedCell.stripTrailingZerosNormalized()
            adjAmounts[col.id] = cleanCell
            netAdj = when (col.operation) {
                AdjustmentOperation.ADD -> netAdj.add(cleanCell)
                AdjustmentOperation.SUBTRACT -> netAdj.subtract(cleanCell)
            }
        }

        val finalTotal = subtotalRounded.add(netAdj).stripTrailingZerosNormalized()

        return ComputedMemberResult(
            member = member,
            computedRows = computedRows,
            validRowCount = validCount,
            subtotalRaw = subtotalRaw,
            subtotalAfterFormula = subtotalRounded,
            adjustmentAmounts = adjAmounts,
            netAdjustment = netAdj.stripTrailingZerosNormalized(),
            finalTotal = finalTotal
        )
    }

    fun computeCalculation(
        calculationId: Long,
        scheduledDateTimeMillis: Long,
        usesGlobalFormula: Boolean,
        customFormula: FormulaConfig,
        globalFormula: FormulaConfig,
        columns: List<AdjustmentColumn>,
        members: List<CalculationMemberEntry>,
        roundingConfig: RoundingConfig = RoundingConfig()
    ): ComputedCalculationSummary {
        val effectiveFormula = if (usesGlobalFormula) globalFormula else customFormula
        val computedMembers = members.map { member ->
            computeMember(member, effectiveFormula, columns, roundingConfig)
        }

        var totalRawBeforeRounding = BigDecimal.ZERO
        var totalBeforeAdj = BigDecimal.ZERO
        var totalAdj = BigDecimal.ZERO
        var grandTotal = BigDecimal.ZERO

        for (item in computedMembers) {
            totalRawBeforeRounding = totalRawBeforeRounding.add(item.subtotalRaw)
            totalBeforeAdj = totalBeforeAdj.add(item.subtotalAfterFormula)
            totalAdj = totalAdj.add(item.netAdjustment)
            grandTotal = grandTotal.add(item.finalTotal)
        }

        return ComputedCalculationSummary(
            calculationId = calculationId,
            scheduledDateTimeMillis = scheduledDateTimeMillis,
            usesGlobalFormula = usesGlobalFormula,
            effectiveFormula = effectiveFormula,
            columns = columns,
            members = computedMembers,
            totalRawBeforeRounding = totalRawBeforeRounding.stripTrailingZerosNormalized(),
            totalBeforeAdjustments = totalBeforeAdj.stripTrailingZerosNormalized(),
            totalAdjustments = totalAdj.stripTrailingZerosNormalized(),
            grandTotal = grandTotal.stripTrailingZerosNormalized()
        )
    }

    fun sumSavedCalculations(calculations: List<ComputedCalculationSummary>): BigDecimal {
        val unique = calculations.distinctBy { it.calculationId }
        var total = BigDecimal.ZERO
        for (calc in unique) {
            total = total.add(calc.grandTotal)
        }
        return total.stripTrailingZerosNormalized()
    }

    fun filterCalculationsByMonth(
        calculations: List<ComputedCalculationSummary>,
        month: PayrollMonth
    ): List<ComputedCalculationSummary> =
        calculations.filter { month.contains(it.scheduledDateTimeMillis) }

    fun sumSavedCalculationsForMonth(
        calculations: List<ComputedCalculationSummary>,
        month: PayrollMonth
    ): BigDecimal = sumSavedCalculations(filterCalculationsByMonth(calculations, month))

    fun formatRowEquation(row: ComputedRowResult): String {
        val n1Str = PayrollCalculator.formatNumber(row.n1, 4)
        val n2Str = PayrollCalculator.formatNumber(row.n2, 4)
        val formulaPart = if (row.formulaApplied.enabled) " ${row.formulaApplied.shortLabel()}" else ""
        val rawStr = PayrollCalculator.formatRawNumber(row.afterFormulaRaw)
        val finalStr = PayrollCalculator.formatNumber(row.afterFormula, 2)
        return if (row.isRoundedDifferent) {
            "$n1Str × $n2Str$formulaPart = $rawStr → $finalStr"
        } else {
            "$n1Str × $n2Str$formulaPart = $rawStr"
        }
    }

    fun formatCalculationLedgerText(summary: ComputedCalculationSummary): String = buildString {
        appendLine("=== HITUNG GAJIAN PRO ===")
        appendLine("Tanggal: ${IndonesianDateFormatter.formatDateTime(summary.scheduledDateTimeMillis)}")
        val formulaText = if (summary.effectiveFormula.enabled) {
            "Jumlah × Harga ${summary.effectiveFormula.shortLabel()}"
        } else {
            "Jumlah × Harga (${summary.effectiveFormula.shortLabel()} Nonaktif)"
        }
        val scopeText = if (summary.usesGlobalFormula) "Global" else "Per Daftar"
        appendLine("Rumus: $formulaText [$scopeText]")
        appendLine("----------------------------")
        summary.members.forEach { m ->
            appendLine("NAMA: ${m.member.name.uppercase()}")
            m.computedRows.filter { it.isValid }.forEach { r ->
                appendLine("  ${formatRowEquation(r)}")
            }
            appendLine("  TOTAL ASLI (SEBELUM PEMBULATAN) = ${PayrollCalculator.formatRawNumber(m.subtotalRaw)}")
            appendLine("  TOTAL PENJUMLAHAN = ${PayrollCalculator.formatRupiah(m.subtotalAfterFormula)}")
            var hasAnyAdj = false
            summary.columns.forEach { col ->
                val v = m.adjustmentAmounts[col.id]
                if (v != null) {
                    hasAnyAdj = true
                    appendLine("  ${col.name} (${col.operation.symbol}) = ${PayrollCalculator.formatRupiah(v)}")
                }
            }
            if (hasAnyAdj) {
                appendLine("  TOTAL AKHIR = ${PayrollCalculator.formatRupiah(m.finalTotal)}")
            }
        }
        appendLine("============================")
        append("TOTAL PERHITUNGAN = ${PayrollCalculator.formatRupiah(summary.grandTotal)}")
    }

    fun formatAllSavedSummaryText(calculations: List<ComputedCalculationSummary>): String = buildString {
        appendLine("=== TOTAL GAJIAN TERSIMPAN ===")
        calculations.forEachIndexed { idx, calc ->
            appendLine("${idx + 1}. ${IndonesianDateFormatter.formatFullDate(calc.scheduledDateTimeMillis)} (${IndonesianDateFormatter.formatTime(calc.scheduledDateTimeMillis)})")
            appendLine("   ${calc.members.size} karyawan • Total = ${PayrollCalculator.formatRupiah(calc.grandTotal)}")
        }
        appendLine("==============================")
        append("TOTAL KESELURUHAN = ${PayrollCalculator.formatRupiah(sumSavedCalculations(calculations))}")
    }

    fun formatMonthlySummaryText(
        month: PayrollMonth,
        monthlyCalculations: List<ComputedCalculationSummary>
    ): String = buildString {
        appendLine("=== TOTAL GAJI ${month.labelFull().uppercase()} ===")
        appendLine("Periode: ${month.periodRangeLabel()}")
        appendLine("------------------------------")
        monthlyCalculations.forEachIndexed { idx, calc ->
            appendLine("${idx + 1}. ${IndonesianDateFormatter.formatFullDate(calc.scheduledDateTimeMillis)} (${IndonesianDateFormatter.formatTime(calc.scheduledDateTimeMillis)})")
            appendLine("   ${calc.members.size} karyawan • Total = ${PayrollCalculator.formatRupiah(calc.grandTotal)}")
        }
        appendLine("==============================")
        append("TOTAL BULAN ${month.labelFull().uppercase()} = ${PayrollCalculator.formatRupiah(sumSavedCalculations(monthlyCalculations))}")
    }
}
