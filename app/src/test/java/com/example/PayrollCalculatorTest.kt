package com.example

import com.example.domain.AdjustmentColumn
import com.example.domain.AdjustmentOperation
import com.example.domain.CalculationMemberEntry
import com.example.domain.CalculationRowItem
import com.example.domain.FormulaConfig
import com.example.domain.FormulaOperation
import com.example.domain.IndonesianDateFormatter
import com.example.domain.OcrPayrollParser
import com.example.domain.PayrollCalculator
import com.example.domain.PayrollEngine
import com.example.domain.PayrollMonth
import com.example.domain.RoundingConfig
import com.example.domain.RoundingModeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PayrollCalculatorTest {

    @Test
    fun defaultFloor100RoundingMatchesAllSpecExamples() {
        // Default RoundingConfig must be FLOOR_TO_STEP with step = 100
        val defaultConfig = RoundingConfig()
        assertEquals(RoundingModeType.FLOOR_TO_STEP, defaultConfig.mode)
        assertTrue(defaultConfig.isDefaultFloor100())

        // All 7 examples from the specification table:
        val cases = listOf(
            "114333" to "114300",
            "74666" to "74600",
            "166777" to "166700",
            "186666.66666667" to "186600",
            "14583.33333333" to "14500",
            "174166.66666667" to "174100",
            "250000" to "250000"
        )
        cases.forEach { (raw, expected) ->
            val actual = PayrollCalculator.applyCustomRounding(BigDecimal(raw), defaultConfig)
            assertEquals("Failed for raw=$raw", 0, BigDecimal(expected).compareTo(actual))
        }

        // Spec Section 4 example: 1.372.000 / 12 = 114.333,333... -> Hasil pembulatan = 114.300
        val member = CalculationMemberEntry(
            id = "m_spec",
            name = "Uji",
            rows = listOf(
                CalculationRowItem(id = "r1", n1Text = "1", n2Text = "1372000"),
                CalculationRowItem(id = "r2", n1Text = "1", n2Text = "3000000") // 3.000.000 / 12 = 250.000 exact
            )
        )
        val computed = PayrollEngine.computeMember(member, FormulaConfig(), emptyList(), defaultConfig)
        // Row 1 raw preserved & rounded to 114.300
        assertEquals("114.333,3333", PayrollCalculator.formatRawNumber(computed.computedRows[0].afterFormulaRaw))
        assertEquals(0, BigDecimal("114300").compareTo(computed.computedRows[0].afterFormula))
        // Row 2 exact 250.000 stays 250.000
        assertEquals(0, BigDecimal("250000").compareTo(computed.computedRows[1].afterFormula))
        // Total uses rounded values (114.300 + 250.000 = 364.300) while raw sum is preserved (364.333,3333)
        assertEquals(0, BigDecimal("364300").compareTo(computed.subtotalAfterFormula))
        assertEquals("364.333,3333", PayrollCalculator.formatRawNumber(computed.subtotalRaw))
    }

    @Test
    fun employeeFinalTotal_sumsRoundedColumnsWithoutRerounding() {
        // Row 1: 1.372.000 / 12 = 114.333,3333... -> rounded per column = 114.300
        // Row 2:   896.000 / 12 =  74.666,6667... -> rounded per column =  74.600
        // Raw sum = 189.000, but sum of rounded columns MUST be 114.300 + 74.600 = 188.900 (NOT 189.000!)
        // Plus deduction 150 -> finalTotal MUST be 188.750 (NOT re-rounded to 188.700!)
        val ptCol = AdjustmentColumn(id = "pt", name = "Potongan PT", operation = AdjustmentOperation.SUBTRACT)
        val member = CalculationMemberEntry(
            id = "m_sum",
            name = "Budi",
            rows = listOf(
                CalculationRowItem(id = "r1", n1Text = "1", n2Text = "1372000"),
                CalculationRowItem(id = "r2", n1Text = "1", n2Text = "896000")
            ),
            adjustmentValues = mapOf("pt" to "150")
        )
        val computed = PayrollEngine.computeMember(member, FormulaConfig(), listOf(ptCol), RoundingConfig.DEFAULT)
        assertEquals(0, BigDecimal("114300").compareTo(computed.computedRows[0].afterFormula))
        assertEquals(0, BigDecimal("74600").compareTo(computed.computedRows[1].afterFormula))
        assertEquals(0, BigDecimal("188900").compareTo(computed.subtotalAfterFormula))
        assertEquals(0, BigDecimal("188750").compareTo(computed.finalTotal))
        assertEquals("Rp 188.750", PayrollCalculator.formatRupiah(computed.finalTotal))
    }

    @Test
    fun v1FormulaWithConfigurableTrailingDivide12AndToggleOff() {
        // BPK: 100 x 54 / 12 = 450, 200 x 36 / 12 = 600
        val member = CalculationMemberEntry(
            id = "m1",
            name = "BPK",
            rows = listOf(
                CalculationRowItem(id = "r1", n1Text = "100", n2Text = "54"),
                CalculationRowItem(id = "r2", n1Text = "200", n2Text = "36")
            )
        )

        val defaultDivide12 = FormulaConfig(enabled = true, operation = FormulaOperation.DIVIDE, operand = "12")
        // With Default FLOOR(N/100)*100 (exact 50 preserved): 450 stays 450, 600 stays 600, Total = 1.050
        val computedDefaultRounding = PayrollEngine.computeMember(member, defaultDivide12, emptyList())
        assertEquals(0, BigDecimal("450").compareTo(computedDefaultRounding.computedRows[0].afterFormulaRaw))
        assertEquals(0, BigDecimal("450").compareTo(computedDefaultRounding.computedRows[0].afterFormula))
        assertEquals(0, BigDecimal("600").compareTo(computedDefaultRounding.computedRows[1].afterFormula))
        assertEquals(0, BigDecimal("1050").compareTo(computedDefaultRounding.subtotalRaw))
        assertEquals(0, BigDecimal("1050").compareTo(computedDefaultRounding.subtotalAfterFormula))

        // With Asli (NONE): 450 + 600 = 1.050
        val computedAsli = PayrollEngine.computeMember(member, defaultDivide12, emptyList(), RoundingConfig.ASLI)
        assertEquals(0, BigDecimal("450").compareTo(computedAsli.computedRows[0].afterFormula))
        assertEquals(0, BigDecimal("600").compareTo(computedAsli.computedRows[1].afterFormula))
        assertEquals(0, BigDecimal("1050").compareTo(computedAsli.subtotalAfterFormula))

        // Toggle formula OFF -> trailing ÷ 12 bypassed -> 5.400 + 7.200 = 12.600
        val disabledFormula = defaultDivide12.copy(enabled = false)
        val computedDisabled = PayrollEngine.computeMember(member, disabledFormula, emptyList(), RoundingConfig.ASLI)
        assertEquals(0, BigDecimal("5400").compareTo(computedDisabled.computedRows[0].afterFormula))
        assertEquals(0, BigDecimal("7200").compareTo(computedDisabled.computedRows[1].afterFormula))
        assertEquals(0, BigDecimal("12600").compareTo(computedDisabled.subtotalAfterFormula))

        // Change trailing ÷ 12 to + 50 -> (100x54)+50 = 5450, (200x36)+50 = 7250
        val plus50 = FormulaConfig(enabled = true, operation = FormulaOperation.ADD, operand = "50")
        val computedPlus = PayrollEngine.computeMember(member, plus50, emptyList(), RoundingConfig.ASLI)
        assertEquals(0, BigDecimal("5450").compareTo(computedPlus.computedRows[0].afterFormula))
        assertEquals(0, BigDecimal("12700").compareTo(computedPlus.subtotalAfterFormula))
    }

    @Test
    fun exactEnding50PreservedUnderDefaultRounding() {
        val defaultConfig = RoundingConfig.DEFAULT
        // Exact 50 ending stays untouched (user example: 70.550 -> 70.550)
        assertEquals(0, BigDecimal("70550").compareTo(PayrollCalculator.applyCustomRounding(BigDecimal("70550"), defaultConfig)))
        assertEquals(0, BigDecimal("70550").compareTo(PayrollCalculator.applyCustomRounding(BigDecimal("70550.00"), defaultConfig)))
        assertEquals(0, BigDecimal("450").compareTo(PayrollCalculator.applyCustomRounding(BigDecimal("450"), defaultConfig)))
        // Non-50 endings still follow default FLOOR(N / 100) * 100
        assertEquals(0, BigDecimal("70500").compareTo(PayrollCalculator.applyCustomRounding(BigDecimal("70549"), defaultConfig)))
        assertEquals(0, BigDecimal("70500").compareTo(PayrollCalculator.applyCustomRounding(BigDecimal("70551"), defaultConfig)))
        assertEquals(0, BigDecimal("70500").compareTo(PayrollCalculator.applyCustomRounding(BigDecimal("70550.3333"), defaultConfig)))
        assertEquals(0, BigDecimal("70500").compareTo(PayrollCalculator.applyCustomRounding(BigDecimal("70583.3333"), defaultConfig)))
    }

    @Test
    fun perRowFormulaDisableAffectsOnlyThatSingleRowAndNoteIsPreserved() {
        // Row 1 turns OFF ÷ 12 -> 100 * 54 = 5.400 (with note)
        // Row 2 keeps ÷ 12 ON -> 200 * 36 / 12 = 600
        val member = CalculationMemberEntry(
            id = "m_row_iso",
            name = "Siti",
            rows = listOf(
                CalculationRowItem(
                    id = "r1",
                    n1Text = "100",
                    n2Text = "54",
                    formulaEnabledOverride = false,
                    note = "Borongan khusus"
                ),
                CalculationRowItem(
                    id = "r2",
                    n1Text = "200",
                    n2Text = "36"
                )
            )
        )
        val computed = PayrollEngine.computeMember(member, FormulaConfig(), emptyList(), RoundingConfig.DEFAULT)
        assertFalse(computed.computedRows[0].formulaApplied.enabled)
        assertTrue(computed.computedRows[1].formulaApplied.enabled)
        assertEquals(0, BigDecimal("5400").compareTo(computed.computedRows[0].afterFormula))
        assertEquals(0, BigDecimal("600").compareTo(computed.computedRows[1].afterFormula))
        assertEquals(0, BigDecimal("6000").compareTo(computed.subtotalAfterFormula))
        assertTrue(PayrollEngine.formatRowEquation(computed.computedRows[0]).contains("[Borongan khusus]"))
    }

    @Test
    fun perEmployeeAdjustmentsAreIsolatedAndDeductionsExcludedFromGrandAndMonthlyTotal() {
        val ptColA = AdjustmentColumn(id = "a_pt", name = "Potongan PT", operation = AdjustmentOperation.SUBTRACT)
        val bonusColA = AdjustmentColumn(id = "a_bonus", name = "Bonus", operation = AdjustmentOperation.ADD)

        // Employee A: 1000 * 120 / 12 = 10.000; Bonus +2.000; Potongan PT -1.500 -> personal finalTotal = 10.500
        val empA = CalculationMemberEntry(
            id = "empA",
            name = "Ani",
            rows = listOf(CalculationRowItem(id = "r1", n1Text = "1000", n2Text = "120")),
            adjustmentColumns = listOf(ptColA, bonusColA),
            adjustmentValues = mapOf("a_pt" to "1500", "a_bonus" to "2000")
        )
        // Employee B: 2000 * 120 / 12 = 20.000; NO adjustment columns of their own!
        val empB = CalculationMemberEntry(
            id = "empB",
            name = "Budi",
            rows = listOf(CalculationRowItem(id = "r2", n1Text = "2000", n2Text = "120"))
        )

        val summary = PayrollEngine.computeCalculation(
            calculationId = 10L,
            scheduledDateTimeMillis = IndonesianDateFormatter.buildMillis(2026, 10, 10, 9, 0),
            usesGlobalFormula = true,
            customFormula = FormulaConfig(),
            globalFormula = FormulaConfig(),
            columns = emptyList(),
            members = listOf(empA, empB),
            roundingConfig = RoundingConfig.DEFAULT
        )

        // Isolation check: Employee A has 2 columns, Employee B has 0 columns
        assertEquals(2, summary.members[0].effectiveColumns.size)
        assertEquals(0, summary.members[1].effectiveColumns.size)
        // Employee A personal net = 10.000 + 2.000 - 1.500 = 10.500
        assertEquals(0, BigDecimal("10500").compareTo(summary.members[0].finalTotal))
        // Calculation totals:
        // Before adjustments (sebelum dipotong & ditambah) = 10.000 + 20.000 = 30.000
        assertEquals(0, BigDecimal("30000").compareTo(summary.totalBeforeAdjustments))
        // Additions = 2.000, Deductions (info only) = 1.500
        assertEquals(0, BigDecimal("2000").compareTo(summary.totalAdditions))
        assertEquals(0, BigDecimal("1500").compareTo(summary.totalDeductions))
        // Grand total EXCLUDES deductions: 30.000 + 2.000 = 32.000 (NOT 30.500!)
        assertEquals(0, BigDecimal("32000").compareTo(summary.grandTotal))

        // Monthly summary also preserves pre-adjustment total and excludes deductions
        val monthly = PayrollEngine.summarizeSavedCalculations(listOf(summary))
        assertEquals(0, BigDecimal("30000").compareTo(monthly.totalBeforeAdjustments))
        assertEquals(0, BigDecimal("2000").compareTo(monthly.totalAdditions))
        assertEquals(0, BigDecimal("1500").compareTo(monthly.totalDeductions))
        assertEquals(0, BigDecimal("32000").compareTo(monthly.grandTotal))
    }

    @Test
    fun formulaOperationsAndZeroValidation() {
        val invalidDiv = FormulaConfig(enabled = true, operation = FormulaOperation.DIVIDE, operand = "0")
        assertFalse(invalidDiv.isValid())

        val mulZero = FormulaConfig(enabled = true, operation = FormulaOperation.MULTIPLY, operand = "0")
        assertTrue(mulZero.isValid())
        assertEquals(0, BigDecimal.ZERO.compareTo(PayrollEngine.applyFormula(BigDecimal("120000"), mulZero)))

        val sub50k = FormulaConfig(enabled = true, operation = FormulaOperation.SUBTRACT, operand = "50000")
        assertEquals(0, BigDecimal("70000").compareTo(PayrollEngine.applyFormula(BigDecimal("120000"), sub50k)))

        val add25k = FormulaConfig(enabled = true, operation = FormulaOperation.ADD, operand = "25000")
        assertEquals(0, BigDecimal("145000").compareTo(PayrollEngine.applyFormula(BigDecimal("120000"), add25k)))
    }

    @Test
    fun perEmployeeAdjustments_emptySkippedAndExplicitZeroDisplayed() {
        val ptCol = AdjustmentColumn(id = "c1", name = "Potongan PT", operation = AdjustmentOperation.SUBTRACT)
        val bonusCol = AdjustmentColumn(id = "c2", name = "Bonus", operation = AdjustmentOperation.ADD)
        val kasbonCol = AdjustmentColumn(id = "c3", name = "Kasbon", operation = AdjustmentOperation.SUBTRACT)
        val tunjanganCol = AdjustmentColumn(id = "c4", name = "Tunjangan", operation = AdjustmentOperation.ADD)

        // 1000 * 120 / 12 = 10.000
        val member = CalculationMemberEntry(
            id = "m1",
            name = "Ani",
            rows = listOf(CalculationRowItem(id = "r1", n1Text = "1000", n2Text = "120")),
            adjustmentColumns = listOf(ptCol, bonusCol, kasbonCol, tunjanganCol),
            adjustmentValues = mapOf(
                "c1" to "500",    // Potongan PT -500
                "c2" to "2000",   // Bonus +2.000
                // c3 empty -> skipped
                "c4" to "0"       // Explicit 0 -> preserved as 0
            )
        )

        val formula = FormulaConfig(enabled = true, operation = FormulaOperation.DIVIDE, operand = "12")
        val result = PayrollEngine.computeMember(
            member = member,
            formula = formula,
            columns = emptyList()
        )

        // 10.000 - 500 + 2.000 + 0 = 11.500 (personal final), excluding deductions = 12.000
        assertEquals(0, BigDecimal("10000").compareTo(result.subtotalAfterFormula))
        assertEquals(0, BigDecimal("11500").compareTo(result.finalTotal))
        assertEquals(0, BigDecimal("12000").compareTo(result.totalExcludingDeductions))
        assertNull(result.adjustmentAmounts["c3"])
        assertEquals(0, BigDecimal.ZERO.compareTo(result.adjustmentAmounts["c4"]))
    }

    @Test
    fun formulaScopesGlobalVsPerListAndDeduplicatedTotal() {
        val members = listOf(
            CalculationMemberEntry(
                id = "m1",
                name = "BPK",
                rows = listOf(CalculationRowItem(id = "r1", n1Text = "1000", n2Text = "120"))
            )
        )
        val globalDiv10 = FormulaConfig(enabled = true, operation = FormulaOperation.DIVIDE, operand = "10")
        val customDiv12 = FormulaConfig(enabled = true, operation = FormulaOperation.DIVIDE, operand = "12")

        val globalFollower = PayrollEngine.computeCalculation(
            calculationId = 1L,
            scheduledDateTimeMillis = 0L,
            usesGlobalFormula = true,
            customFormula = customDiv12,
            globalFormula = globalDiv10,
            columns = emptyList(),
            members = members
        )
        assertEquals(0, BigDecimal("12000").compareTo(globalFollower.grandTotal))

        val customList = PayrollEngine.computeCalculation(
            calculationId = 2L,
            scheduledDateTimeMillis = 0L,
            usesGlobalFormula = false,
            customFormula = customDiv12,
            globalFormula = globalDiv10,
            columns = emptyList(),
            members = members
        )
        assertEquals(0, BigDecimal("10000").compareTo(customList.grandTotal))

        val total = PayrollEngine.sumSavedCalculations(listOf(globalFollower, customList, globalFollower))
        assertEquals(0, BigDecimal("22000").compareTo(total))
    }

    @Test
    fun indonesianDateLabelFormat() {
        val millis = IndonesianDateFormatter.buildMillis(2026, 10, 24, 5, 12)
        assertEquals("Selasa, 24 November 2026", IndonesianDateFormatter.formatFullDate(millis))
        assertEquals("05:12", IndonesianDateFormatter.formatTime(millis))
    }

    @Test
    fun ocrParser_extractsNamePairsAndPtCorrectly() {
        val sample = """
            NAMA: BPK
            100 x 54 / 12 = 450
            200 × 36
            TOTAL = 1.050
            Potongan = 100
        """.trimIndent()

        val parsed = OcrPayrollParser.parseRecognizedText(sample)
        assertEquals("BPK", parsed.detectedName)
        assertEquals(2, parsed.detectedRows.size)
        assertEquals("100", parsed.detectedRows[0].n1Text)
        assertEquals("54", parsed.detectedRows[0].n2Text)
        assertEquals("200", parsed.detectedRows[1].n1Text)
        assertEquals("36", parsed.detectedRows[1].n2Text)
        assertEquals("100", parsed.detectedPtAmount)
    }

    @Test
    fun monthlyPeriodFilteringAndTwelveMonthRollingRetention() {
        val nov2026 = PayrollMonth(2026, 10) // November 2026 (1 Nov - 30 Nov)
        assertEquals("November 2026", nov2026.labelFull())
        assertEquals("1 Nov 2026 – 30 Nov 2026", nov2026.periodRangeLabel())

        val oct2026 = PayrollMonth(2026, 9) // Oktober 2026 (1 Okt - 31 Okt)
        assertEquals("1 Okt 2026 – 31 Okt 2026", oct2026.periodRangeLabel())

        // Create 2 calculations in Nov 2026 (start & end of month) and 1 in Oct 2026
        val membersA = listOf(
            CalculationMemberEntry("m1", "A", rows = listOf(CalculationRowItem("r1", "1000", "120"))) // 10.000
        )
        val membersB = listOf(
            CalculationMemberEntry("m2", "B", rows = listOf(CalculationRowItem("r2", "2000", "120"))) // 20.000
        )
        val novStart = PayrollEngine.computeCalculation(
            calculationId = 1L,
            scheduledDateTimeMillis = IndonesianDateFormatter.buildMillis(2026, 10, 1, 0, 0),
            usesGlobalFormula = true,
            customFormula = FormulaConfig(),
            globalFormula = FormulaConfig(),
            columns = emptyList(),
            members = membersA
        )
        val novEnd = PayrollEngine.computeCalculation(
            calculationId = 2L,
            scheduledDateTimeMillis = IndonesianDateFormatter.buildMillis(2026, 10, 30, 23, 59),
            usesGlobalFormula = true,
            customFormula = FormulaConfig(),
            globalFormula = FormulaConfig(),
            columns = emptyList(),
            members = membersB
        )
        val octMid = PayrollEngine.computeCalculation(
            calculationId = 3L,
            scheduledDateTimeMillis = IndonesianDateFormatter.buildMillis(2026, 9, 15, 10, 0),
            usesGlobalFormula = true,
            customFormula = FormulaConfig(),
            globalFormula = FormulaConfig(),
            columns = emptyList(),
            members = membersA
        )

        val all = listOf(novStart, novEnd, octMid)
        // Nov total = 10.000 + 20.000 = 30.000 (Oct excluded!)
        assertEquals(2, PayrollEngine.filterCalculationsByMonth(all, nov2026).size)
        assertEquals(0, BigDecimal("30000").compareTo(PayrollEngine.sumSavedCalculationsForMonth(all, nov2026)))
        // Oct total = 10.000 (Nov excluded!)
        assertEquals(1, PayrollEngine.filterCalculationsByMonth(all, oct2026).size)
        assertEquals(0, BigDecimal("10000").compareTo(PayrollEngine.sumSavedCalculationsForMonth(all, oct2026)))

        // 12-month rolling retention check:
        // Window anchored at Nov 2026 keeps Des 2025..Nov 2026 (12 months).
        val dec2025Ts = IndonesianDateFormatter.buildMillis(2025, 11, 15, 9, 0)
        val jan2026Ts = IndonesianDateFormatter.buildMillis(2026, 0, 10, 9, 0)
        val dec2026Ts = IndonesianDateFormatter.buildMillis(2026, 11, 2, 9, 0)

        val cutoffAtNov2026 = PayrollMonth.retentionCutoffMillis(nov2026, listOf(dec2025Ts, jan2026Ts))
        assertTrue("Des 2025 must still be kept in Nov 2026 window", dec2025Ts >= cutoffAtNov2026)

        // Advancing to Des 2026 (month 13!) shifts cutoff to 1 Jan 2026 -> Des 2025 is pruned!
        val cutoffAtDec2026 = PayrollMonth.retentionCutoffMillis(
            referenceMonth = nov2026,
            storedTimestamps = listOf(dec2025Ts, jan2026Ts, dec2026Ts)
        )
        assertTrue("Des 2025 must be pruned when Des 2026 is added", dec2025Ts < cutoffAtDec2026)
        assertTrue("Jan 2026 must remain kept in Des 2026 window", jan2026Ts >= cutoffAtDec2026)
        assertEquals(12, PayrollMonth.lastTwelveMonthsWindow(PayrollMonth(2026, 11)).size)
    }
}
