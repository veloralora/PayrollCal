package com.example.domain

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.util.Calendar
import java.util.UUID

@Serializable
enum class FormulaOperation(val symbol: String, val labelId: String) {
    DIVIDE("÷", "Bagi"),
    MULTIPLY("×", "Kali"),
    ADD("+", "Tambah"),
    SUBTRACT("−", "Kurang")
}

@Serializable
data class FormulaConfig(
    val enabled: Boolean = true,
    val operation: FormulaOperation = FormulaOperation.DIVIDE,
    val operand: String = "12"
) {
    fun operandBigDecimal(): BigDecimal? = PayrollCalculator.parseNumber(operand)

    fun isValid(): Boolean {
        val parsed = operandBigDecimal() ?: return false
        if (enabled && operation == FormulaOperation.DIVIDE) {
            return parsed > BigDecimal.ZERO
        }
        return true
    }

    fun formattedOperand(): String {
        val parsed = operandBigDecimal()
        return if (parsed != null) PayrollCalculator.formatNumber(parsed, 4) else operand
    }

    fun shortLabel(): String = "${operation.symbol} ${formattedOperand()}"
}

@Serializable
enum class AdjustmentOperation(val symbol: String, val labelId: String) {
    ADD("+", "Penambahan"),
    SUBTRACT("−", "Potongan")
}

@Serializable
data class AdjustmentColumn(
    val id: String,
    val name: String,
    val operation: AdjustmentOperation
) {
    fun headerLabel(): String = "$name (${operation.symbol})"
}

/**
 * Single calculation row for an employee: N1 × N2 [op operand] = N.
 * Default trailing operation is ÷ 12 (configurable to ÷, ×, +, −, or toggled OFF).
 */
@Serializable
data class CalculationRowItem(
    val id: String = UUID.randomUUID().toString(),
    val n1Text: String = "",
    val n2Text: String = ""
)

@Serializable
data class CalculationMemberEntry(
    val id: String,
    val name: String,
    val isCustomOneOff: Boolean = false,
    val rows: List<CalculationRowItem> = listOf(CalculationRowItem()),
    val adjustmentValues: Map<String, String> = emptyMap()
)

data class ComputedRowResult(
    val row: CalculationRowItem,
    val isValid: Boolean,
    val n1: BigDecimal,
    val n2: BigDecimal,
    val product: BigDecimal,
    val afterFormulaRaw: BigDecimal,
    val afterFormula: BigDecimal,
    val formulaApplied: FormulaConfig
) {
    val isRoundedDifferent: Boolean
        get() = afterFormulaRaw.compareTo(afterFormula) != 0
}

data class ComputedMemberResult(
    val member: CalculationMemberEntry,
    val computedRows: List<ComputedRowResult>,
    val validRowCount: Int,
    val subtotalRaw: BigDecimal,
    val subtotalAfterFormula: BigDecimal,
    val adjustmentAmounts: Map<String, BigDecimal?>,
    val netAdjustment: BigDecimal,
    val finalTotal: BigDecimal
)

data class ComputedCalculationSummary(
    val calculationId: Long,
    val scheduledDateTimeMillis: Long,
    val usesGlobalFormula: Boolean,
    val effectiveFormula: FormulaConfig,
    val columns: List<AdjustmentColumn>,
    val members: List<ComputedMemberResult>,
    val totalRawBeforeRounding: BigDecimal = BigDecimal.ZERO,
    val totalBeforeAdjustments: BigDecimal,
    val totalAdjustments: BigDecimal,
    val grandTotal: BigDecimal
)

object IndonesianDateFormatter {
    private val DAYS = arrayOf(
        "Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu"
    )
    val MONTHS = arrayOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember"
    )
    val MONTHS_SHORT = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "Mei", "Jun",
        "Jul", "Agu", "Sep", "Okt", "Nov", "Des"
    )

    fun formatFullDate(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val dayName = DAYS[cal.get(Calendar.DAY_OF_WEEK) - 1]
        val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
        val monthName = MONTHS[cal.get(Calendar.MONTH)]
        val year = cal.get(Calendar.YEAR)
        return "$dayName, $dayOfMonth $monthName $year"
    }

    fun formatTime(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        return "%02d:%02d".format(hour, minute)
    }

    fun formatDateTime(millis: Long): String = "${formatFullDate(millis)} • ${formatTime(millis)}"

    fun buildMillis(year: Int, monthZeroBased: Int, dayOfMonth: Int, hourOfDay: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroBased.coerceIn(0, 11))
            val maxDay = getActualMaximum(Calendar.DAY_OF_MONTH)
            set(Calendar.DAY_OF_MONTH, dayOfMonth.coerceIn(1, maxDay))
            set(Calendar.HOUR_OF_DAY, hourOfDay.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}

/**
 * Represents a calendar month period (1st day 00:00 through last day of the month)
 * and encapsulates the 12-month rolling retention window.
 */
data class PayrollMonth(
    val year: Int,
    val monthZeroBased: Int
) : Comparable<PayrollMonth> {
    val monthIndex: Int get() = year * 12 + monthZeroBased

    override fun compareTo(other: PayrollMonth): Int = monthIndex.compareTo(other.monthIndex)

    fun startOfMonthMillis(): Long =
        IndonesianDateFormatter.buildMillis(year, monthZeroBased, 1, 0, 0)

    fun endOfMonthDay(): Int {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroBased.coerceIn(0, 11))
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    fun endOfMonthExclusiveMillis(): Long = plusMonths(1).startOfMonthMillis()

    fun plusMonths(delta: Int): PayrollMonth {
        val total = monthIndex + delta
        val newYear = Math.floorDiv(total, 12)
        val newMonth = Math.floorMod(total, 12)
        return PayrollMonth(newYear, newMonth)
    }

    fun labelFull(): String =
        "${IndonesianDateFormatter.MONTHS[monthZeroBased.coerceIn(0, 11)]} $year"

    fun labelShort(): String =
        "${IndonesianDateFormatter.MONTHS_SHORT[monthZeroBased.coerceIn(0, 11)]} $year"

    fun periodRangeLabel(): String {
        val shortMonth = IndonesianDateFormatter.MONTHS_SHORT[monthZeroBased.coerceIn(0, 11)]
        return "1 $shortMonth $year – ${endOfMonthDay()} $shortMonth $year"
    }

    fun contains(timestampMillis: Long): Boolean {
        val start = startOfMonthMillis()
        val endExclusive = endOfMonthExclusiveMillis()
        return timestampMillis in start until endExclusive
    }

    companion object {
        const val MAX_RETENTION_MONTHS = 12

        fun now(): PayrollMonth = fromMillis(System.currentTimeMillis())

        fun fromMillis(millis: Long): PayrollMonth {
            val cal = Calendar.getInstance().apply { timeInMillis = millis }
            return PayrollMonth(
                year = cal.get(Calendar.YEAR),
                monthZeroBased = cal.get(Calendar.MONTH)
            )
        }

        fun anchorMonth(
            referenceMonth: PayrollMonth,
            storedTimestamps: List<Long>
        ): PayrollMonth {
            val maxStored = storedTimestamps.maxOfOrNull { fromMillis(it) }
            return if (maxStored != null && maxStored > referenceMonth) maxStored else referenceMonth
        }

        /**
         * Returns the inclusive 12-month window ending at [anchor], ordered newest-first.
         */
        fun lastTwelveMonthsWindow(anchor: PayrollMonth = now()): List<PayrollMonth> =
            (0 until MAX_RETENTION_MONTHS).map { offset -> anchor.plusMonths(-offset) }

        /**
         * Start-of-month timestamp for the oldest month still allowed in the 12-month rolling window.
         * Any record strictly older than this timestamp is outside the 12-month cap and must be pruned.
         */
        fun retentionCutoffMillis(
            referenceMonth: PayrollMonth,
            storedTimestamps: List<Long>
        ): Long {
            val anchor = anchorMonth(referenceMonth, storedTimestamps)
            val oldestKept = anchor.plusMonths(-(MAX_RETENTION_MONTHS - 1))
            return oldestKept.startOfMonthMillis()
        }
    }
}
