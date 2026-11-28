package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.ComputedCalculationSummary
import com.example.domain.IndonesianDateFormatter
import com.example.domain.MonthlyTotalSummary
import com.example.domain.PayrollCalculator
import com.example.domain.PayrollEngine
import com.example.domain.PayrollMonth
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.CobaltPrimaryDark
import com.example.ui.theme.NumericDisplayStyle
import com.example.ui.theme.NumericFormulaStyle
import com.example.ui.theme.NumericHeadlineStyle
import com.example.ui.theme.NumericSmallStyle
import com.example.ui.theme.PtDeductionAccent

/**
 * Tab Total Gaji (Monthly Scope — 1st to Last Day of Selected Month, Max 12 Months):
 * - Displays Total Sebelum Dipotong & Ditambah, Penambahan (+), Info Potongan (−), and Total Gaji
 *   (where deductions are NEVER subtracted from total calculation).
 * - Supports deleting individual calculation records directly from Tab Total (with confirmation).
 * - Month selector lets user switch across up to 12 stored months to inspect & edit past calculations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TotalScreen(
    selectedMonth: PayrollMonth,
    availableMonths: List<PayrollMonth>,
    monthCalculationCounts: Map<PayrollMonth, Int>,
    monthlyCalculations: List<ComputedCalculationSummary>,
    monthlyTotalSummary: MonthlyTotalSummary,
    onSelectMonth: (PayrollMonth) -> Unit,
    onStepMonth: (Int) -> Unit,
    onOpenSavedCalculation: (Long) -> Unit,
    onDeleteCalculation: (ComputedCalculationSummary) -> Unit,
    onCreateCalculation: () -> Unit,
    onCopiedFeedback: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var deletingCalc by remember { mutableStateOf<ComputedCalculationSummary?>(null) }

    val oldestAllowed = availableMonths.lastOrNull() ?: selectedMonth
    val newestAllowed = availableMonths.firstOrNull() ?: selectedMonth
    val canGoPrev = selectedMonth > oldestAllowed
    val canGoNext = selectedMonth < newestAllowed

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "Total Gaji",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "${selectedMonth.labelFull()} • ${monthlyCalculations.size} perhitungan",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            actions = {
                if (monthlyCalculations.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            val text = PayrollEngine.formatMonthlySummaryText(selectedMonth, monthlyCalculations)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Total Gajian ${selectedMonth.labelFull()}", text))
                            onCopiedFeedback("Ringkasan ${selectedMonth.labelFull()} disalin")
                        },
                        modifier = Modifier.testTag("total_copy_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Salin ringkasan bulanan",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Month Selector Card (12-Month Window)
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("total_month_selector_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { onStepMonth(-1) },
                                    enabled = canGoPrev,
                                    modifier = Modifier.testTag("total_prev_month_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = "Bulan sebelumnya"
                                    )
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CalendarMonth,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = selectedMonth.labelFull(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.testTag("total_selected_month_label")
                                        )
                                    }
                                    Text(
                                        text = selectedMonth.periodRangeLabel(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                IconButton(
                                    onClick = { onStepMonth(1) },
                                    enabled = canGoNext,
                                    modifier = Modifier.testTag("total_next_month_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Bulan berikutnya"
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                availableMonths.forEach { month ->
                                    val isSelected = month == selectedMonth
                                    val count = monthCalculationCounts[month] ?: 0
                                    val chipText = if (count > 0) "${month.labelShort()} ($count)" else month.labelShort()
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { onSelectMonth(month) },
                                        label = {
                                            Text(
                                                text = chipText,
                                                fontWeight = if (isSelected || count > 0) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        modifier = Modifier.testTag("total_month_chip_${month.year}_${month.monthZeroBased + 1}")
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Monthly Grand Total Card (shows Total Sebelum Dipotong & Ditambah + Total tanpa Potongan)
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("total_grand_card")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(CobaltPrimary, CobaltPrimaryDark)
                                    )
                                )
                                .padding(20.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "TOTAL GAJI • ${selectedMonth.labelFull().uppercase()}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White.copy(alpha = 0.88f)
                                    )
                                    Text(
                                        text = "Periode ${selectedMonth.periodRangeLabel()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.80f)
                                    )
                                }

                                Text(
                                    text = PayrollCalculator.formatRupiah(monthlyTotalSummary.grandTotal),
                                    style = NumericDisplayStyle,
                                    color = Color.White,
                                    modifier = Modifier.testTag("total_saved_grand_text")
                                )

                                // Clear breakdown: Sebelum Dipotong & Ditambah, Penambahan (+), Info Potongan (−)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White.copy(alpha = 0.14f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Sebelum Dipotong & Ditambah",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.90f),
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = PayrollCalculator.formatRupiah(monthlyTotalSummary.totalBeforeAdjustments),
                                                style = NumericFormulaStyle.copy(fontWeight = FontWeight.Bold),
                                                color = Color.White,
                                                modifier = Modifier.testTag("total_before_adjustments_text")
                                            )
                                        }

                                        if (monthlyTotalSummary.totalAdditions.signum() > 0) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Total Penambahan (+)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.White.copy(alpha = 0.88f),
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = "+ ${PayrollCalculator.formatRupiah(monthlyTotalSummary.totalAdditions)}",
                                                    style = NumericFormulaStyle.copy(fontWeight = FontWeight.SemiBold),
                                                    color = Color(0xFFA7F3D0),
                                                    modifier = Modifier.testTag("total_additions_text")
                                                )
                                            }
                                        }

                                        if (monthlyTotalSummary.totalDeductions.signum() > 0) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Info Potongan (−) • Tidak masuk total",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.White.copy(alpha = 0.82f),
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = PayrollCalculator.formatRupiah(monthlyTotalSummary.totalDeductions),
                                                    style = NumericFormulaStyle.copy(fontWeight = FontWeight.SemiBold),
                                                    color = Color(0xFFFDE68A),
                                                    modifier = Modifier.testTag("total_deductions_info_text")
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.VerifiedUser,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "${monthlyCalculations.size} perhitungan • Potongan tidak dimasukkan ke perhitungan total",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.88f)
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Monthly Calculation Breakdown List (with per-item Delete + Edit)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Rincian Bulan ${selectedMonth.labelFull()}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Ketuk ubah • Hapus per data",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (monthlyCalculations.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ReceiptLong,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "Belum Ada Perhitungan di ${selectedMonth.labelFull()}",
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Pilih bulan lain di atas untuk melihat data bulan sebelumnya, atau buat perhitungan baru.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = onCreateCalculation,
                                    modifier = Modifier.testTag("total_empty_create_button")
                                ) {
                                    Icon(Icons.Filled.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Buat Perhitungan")
                                }
                            }
                        }
                    }
                } else {
                    items(
                        items = monthlyCalculations,
                        key = { it.calculationId }
                    ) { calc ->
                        val dateLabel = IndonesianDateFormatter.formatFullDate(calc.scheduledDateTimeMillis)
                        Card(
                            onClick = { onOpenSavedCalculation(calc.calculationId) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("total_calc_row_${calc.calculationId}")
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = dateLabel,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Jam ${IndonesianDateFormatter.formatTime(calc.scheduledDateTimeMillis)} • ${calc.members.size} karyawan",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            val statusText = if (calc.effectiveFormula.enabled) {
                                                calc.effectiveFormula.shortLabel()
                                            } else {
                                                "${calc.effectiveFormula.shortLabel()} (Mati)"
                                            }
                                            val scope = if (calc.usesGlobalFormula) "Global" else "Per Daftar"
                                            Text(
                                                text = "$scope: $statusText",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { deletingCalc = calc },
                                        modifier = Modifier.testTag("total_delete_${calc.calculationId}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Hapus perhitungan $dateLabel",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "Sebelum penyesuaian: ${PayrollCalculator.formatRupiah(calc.totalBeforeAdjustments)}",
                                            style = NumericSmallStyle,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (calc.totalAdditions.signum() > 0) {
                                            Text(
                                                text = "Penambahan: +${PayrollCalculator.formatRupiah(calc.totalAdditions)}",
                                                style = NumericSmallStyle,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        if (calc.totalDeductions.signum() > 0) {
                                            Text(
                                                text = "Potongan (info): ${PayrollCalculator.formatRupiah(calc.totalDeductions)}",
                                                style = NumericSmallStyle,
                                                color = PtDeductionAccent
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = PayrollCalculator.formatRupiah(calc.grandTotal),
                                            style = NumericHeadlineStyle,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Buka & ubah perhitungan",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Rolling 12-Month Retention Policy Info Strip
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Data tersimpan otomatis maksimal 12 bulan terakhir. Saat masuk ke bulan baru berikutnya, data bulan ke-13 terlama otomatis dihapus dan diganti bulan baru.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    deletingCalc?.let { target ->
        val label = IndonesianDateFormatter.formatFullDate(target.scheduledDateTimeMillis)
        ConfirmDeleteDialog(
            title = "Hapus Data Perhitungan?",
            message = "Perhitungan \"$label\" akan dihapus permanen dari Tab Total dan Riwayat. Lanjutkan?",
            onDismiss = { deletingCalc = null },
            onConfirm = {
                onDeleteCalculation(target)
                deletingCalc = null
            }
        )
    }
}
