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
     * Computes a single Jumlah × Harga [op operand] = N row.
     * Respects per-row formulaEnabledOverride so disabling ÷ 12 on one row never affects any other row.
     */
    fun computeRow(
        row: CalculationRowItem,
        formula: FormulaConfig,
        roundingConfig: RoundingConfig = RoundingConfig()
    ): ComputedRowResult {
        val effectiveRowEnabled = row.formulaEnabledOverride ?: formula.enabled
        val rowFormula = if (effectiveRowEnabled == formula.enabled) {
            formula
        } else {
            formula.copy(enabled = effectiveRowEnabled)
        }

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
                formulaApplied = rowFormula
            )
        }

        val cleanN1 = n1.stripTrailingZerosNormalized()
        val cleanN2 = n2.stripTrailingZerosNormalized()
        val product = cleanN1.multiply(cleanN2).stripTrailingZerosNormalized()
        val afterFormulaRaw = applyFormula(product, rowFormula)
        val afterFormula = PayrollCalculator.applyCustomRounding(afterFormulaRaw, roundingConfig)

        return ComputedRowResult(
            row = row,
            isValid = true,
            n1 = cleanN1,
            n2 = cleanN2,
            product = product,
            afterFormulaRaw = afterFormulaRaw,
            afterFormula = afterFormula,
            formulaApplied = rowFormula
        )
    }

    /**
     * Resolves an employee's own isolated adjustment columns.
     * For backwards compatibility with older saved records that stored shared columns,
     * only legacy columns that actually have a value filled for THIS employee are included.
     */
    fun resolveMemberColumns(
        member: CalculationMemberEntry,
        legacyColumns: List<AdjustmentColumn>
    ): List<AdjustmentColumn> {
        if (legacyColumns.isEmpty()) return member.adjustmentColumns
        val existingIds = member.adjustmentColumns.mapTo(HashSet()) { it.id }
        val migratedLegacy = legacyColumns.filter { col ->
            col.id !in existingIds && !member.adjustmentValues[col.id].isNullOrBlank()
        }
        return if (migratedLegacy.isEmpty()) member.adjustmentColumns
        else member.adjustmentColumns + migratedLegacy
    }

    /**
     * Computes an employee's full calculation:
     * 1. Computes each Jumlah × Harga row using its own row-level formula toggle.
     * 2. Sums all valid rows into subtotalAfterFormula (no re-rounding of the sum).
     * 3. Applies ONLY this employee's isolated additions (+) and deductions (−).
     *    Empty cells stay null and skipped; explicit "0" is preserved as BigDecimal.ZERO.
     * 4. Tracks totalAdditions and totalDeductions separately so grand totals exclude deductions.
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

        val effectiveCols = resolveMemberColumns(member, columns)
        val adjAmounts = LinkedHashMap<String, BigDecimal?>(effectiveCols.size)
        var totalAdditions = BigDecimal.ZERO
        var totalDeductions = BigDecimal.ZERO

        for (col in effectiveCols) {
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
            when (col.operation) {
                AdjustmentOperation.ADD -> totalAdditions = totalAdditions.add(cleanCell)
                AdjustmentOperation.SUBTRACT -> totalDeductions = totalDeductions.add(cleanCell)
            }
        }

        totalAdditions = totalAdditions.stripTrailingZerosNormalized()
        totalDeductions = totalDeductions.stripTrailingZerosNormalized()
        val netAdj = totalAdditions.subtract(totalDeductions).stripTrailingZerosNormalized()
        val totalExcludingDeductions = subtotalRounded.add(totalAdditions).stripTrailingZerosNormalized()
        val finalTotal = subtotalRounded.add(netAdj).stripTrailingZerosNormalized()

        return ComputedMemberResult(
            member = member,
            effectiveColumns = effectiveCols,
            computedRows = computedRows,
            validRowCount = validCount,
            subtotalRaw = subtotalRaw,
            subtotalAfterFormula = subtotalRounded,
            adjustmentAmounts = adjAmounts,
            totalAdditions = totalAdditions,
            totalDeductions = totalDeductions,
            netAdjustment = netAdj,
            totalExcludingDeductions = totalExcludingDeductions,
            finalTotal = finalTotal
        )
    }

    /**
     * Computes calculation-level totals:
     * - totalBeforeAdjustments: pure sum before any additions or deductions.
     * - totalAdditions: sum of all per-employee additions (+).
     * - totalDeductions: sum of all per-employee deductions (−) for reference ONLY.
     * - grandTotal: totalBeforeAdjustments + totalAdditions (deductions are NEVER subtracted from total).
     */
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
        var totalAdditions = BigDecimal.ZERO
        var totalDeductions = BigDecimal.ZERO
        var totalNetAdj = BigDecimal.ZERO
        var grandTotal = BigDecimal.ZERO

        for (item in computedMembers) {
            totalRawBeforeRounding = totalRawBeforeRounding.add(item.subtotalRaw)
            totalBeforeAdj = totalBeforeAdj.add(item.subtotalAfterFormula)
            totalAdditions = totalAdditions.add(item.totalAdditions)
            totalDeductions = totalDeductions.add(item.totalDeductions)
            totalNetAdj = totalNetAdj.add(item.netAdjustment)
            grandTotal = grandTotal.add(item.totalExcludingDeductions)
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
            totalAdditions = totalAdditions.stripTrailingZerosNormalized(),
            totalDeductions = totalDeductions.stripTrailingZerosNormalized(),
            totalAdjustments = totalNetAdj.stripTrailingZerosNormalized(),
            grandTotal = grandTotal.stripTrailingZerosNormalized()
        )
    }

    fun summarizeSavedCalculations(calculations: List<ComputedCalculationSummary>): MonthlyTotalSummary {
        val unique = calculations.distinctBy { it.calculationId }
        var totalRaw = BigDecimal.ZERO
        var totalBeforeAdj = BigDecimal.ZERO
        var totalAdd = BigDecimal.ZERO
        var totalDed = BigDecimal.ZERO
        var grand = BigDecimal.ZERO
        for (calc in unique) {
            totalRaw = totalRaw.add(calc.totalRawBeforeRounding)
            totalBeforeAdj = totalBeforeAdj.add(calc.totalBeforeAdjustments)
            totalAdd = totalAdd.add(calc.totalAdditions)
            totalDed = totalDed.add(calc.totalDeductions)
            grand = grand.add(calc.grandTotal)
        }
        return MonthlyTotalSummary(
            totalRawBeforeRounding = totalRaw.stripTrailingZerosNormalized(),
            totalBeforeAdjustments = totalBeforeAdj.stripTrailingZerosNormalized(),
            totalAdditions = totalAdd.stripTrailingZerosNormalized(),
            totalDeductions = totalDed.stripTrailingZerosNormalized(),
            grandTotal = grand.stripTrailingZerosNormalized()
        )
    }

    fun sumSavedCalculations(calculations: List<ComputedCalculationSummary>): BigDecimal =
        summarizeSavedCalculations(calculations).grandTotal

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
        val base = if (row.isRoundedDifferent) {
            "$n1Str × $n2Str$formulaPart = $rawStr → $finalStr"
        } else {
            "$n1Str × $n2Str$formulaPart = $rawStr"
        }
        return if (row.row.note.isNotBlank()) "$base [${row.row.note.trim()}]" else base
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
            m.effectiveColumns.forEach { col ->
                val v = m.adjustmentAmounts[col.id]
                if (v != null) {
                    hasAnyAdj = true
                    appendLine("  ${col.name} (${col.operation.symbol}) = ${PayrollCalculator.formatRupiah(v)}")
                }
            }
            if (hasAnyAdj) {
                appendLine("  TOTAL AKHIR KARYAWAN = ${PayrollCalculator.formatRupiah(m.finalTotal)}")
            }
        }
        appendLine("============================")
        appendLine("TOTAL SEBELUM DIPOTONG & DITAMBAH = ${PayrollCalculator.formatRupiah(summary.totalBeforeAdjustments)}")
        if (summary.totalAdditions.signum() != 0) {
            appendLine("TOTAL PENAMBAHAN (+) = ${PayrollCalculator.formatRupiah(summary.totalAdditions)}")
        }
        if (summary.totalDeductions.signum() != 0) {
            appendLine("INFO POTONGAN (−) [TIDAK MASUK TOTAL] = ${PayrollCalculator.formatRupiah(summary.totalDeductions)}")
        }
        append("TOTAL PERHITUNGAN (TANPA POTONGAN) = ${PayrollCalculator.formatRupiah(summary.grandTotal)}")
    }

    fun formatAllSavedSummaryText(calculations: List<ComputedCalculationSummary>): String = buildString {
        val totals = summarizeSavedCalculations(calculations)
        appendLine("=== TOTAL GAJIAN TERSIMPAN ===")
        calculations.forEachIndexed { idx, calc ->
            appendLine("${idx + 1}. ${IndonesianDateFormatter.formatFullDate(calc.scheduledDateTimeMillis)} (${IndonesianDateFormatter.formatTime(calc.scheduledDateTimeMillis)})")
            appendLine("   ${calc.members.size} karyawan • Total = ${PayrollCalculator.formatRupiah(calc.grandTotal)}")
        }
        appendLine("==============================")
        appendLine("SEBELUM DIPOTONG & DITAMBAH = ${PayrollCalculator.formatRupiah(totals.totalBeforeAdjustments)}")
        if (totals.totalAdditions.signum() != 0) {
            appendLine("TOTAL PENAMBAHAN (+) = ${PayrollCalculator.formatRupiah(totals.totalAdditions)}")
        }
        if (totals.totalDeductions.signum() != 0) {
            appendLine("INFO POTONGAN (−) [TIDAK MASUK TOTAL] = ${PayrollCalculator.formatRupiah(totals.totalDeductions)}")
        }
        append("TOTAL KESELURUHAN = ${PayrollCalculator.formatRupiah(totals.grandTotal)}")
    }

    fun formatMonthlySummaryText(
        month: PayrollMonth,
        monthlyCalculations: List<ComputedCalculationSummary>
    ): String = buildString {
        val totals = summarizeSavedCalculations(monthlyCalculations)
        appendLine("=== TOTAL GAJI ${month.labelFull().uppercase()} ===")
        appendLine("Periode: ${month.periodRangeLabel()}")
        appendLine("------------------------------")
        monthlyCalculations.forEachIndexed { idx, calc ->
            appendLine("${idx + 1}. ${IndonesianDateFormatter.formatFullDate(calc.scheduledDateTimeMillis)} (${IndonesianDateFormatter.formatTime(calc.scheduledDateTimeMillis)})")
            appendLine("   ${calc.members.size} karyawan • Sebelum Penyesuaian = ${PayrollCalculator.formatRupiah(calc.totalBeforeAdjustments)} • Total = ${PayrollCalculator.formatRupiah(calc.grandTotal)}")
        }
        appendLine("==============================")
        appendLine("SEBELUM DIPOTONG & DITAMBAH = ${PayrollCalculator.formatRupiah(totals.totalBeforeAdjustments)}")
        if (totals.totalAdditions.signum() != 0) {
            appendLine("TOTAL PENAMBAHAN (+) = ${PayrollCalculator.formatRupiah(totals.totalAdditions)}")
        }
        if (totals.totalDeductions.signum() != 0) {
            appendLine("INFO POTONGAN (−) [TIDAK MASUK TOTAL] = ${PayrollCalculator.formatRupiah(totals.totalDeductions)}")
        }
        append("TOTAL BULAN ${month.labelFull().uppercase()} = ${PayrollCalculator.formatRupiah(totals.grandTotal)}")
    }
}
