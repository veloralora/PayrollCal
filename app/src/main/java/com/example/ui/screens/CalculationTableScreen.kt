package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.ComputedCalculationSummary
import com.example.domain.ComputedMemberResult
import com.example.domain.FormulaConfig
import com.example.domain.IndonesianDateFormatter
import com.example.domain.PayrollCalculator
import com.example.domain.PayrollEngine
import com.example.domain.RoundingConfig
import com.example.ui.ActiveCalculationDraft
import com.example.ui.components.FormulaControlBar
import com.example.ui.components.FormulaEditorSheet
import com.example.ui.components.RoundingRuleSelectorCard
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.NumericFormulaStyle
import com.example.ui.theme.NumericHeadlineStyle
import com.example.ui.theme.NumericSmallStyle
import com.example.ui.theme.PtDeductionAccent

/**
 * Ringkasan Tabel Perhitungan across all employees:
 * Shows each employee's Jumlah × Harga subtotal, isolated per-employee Penambahan (+) and
 * Potongan (−), and net employee total. Tapping any employee row opens their dedicated
 * EmployeeCalculationScreen where their private adjustments and rows are edited.
 * Bottom bar clearly shows Total Sebelum Dipotong & Ditambah and Total Perhitungan (potongan excluded).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculationTableScreen(
    draft: ActiveCalculationDraft,
    computed: ComputedCalculationSummary,
    globalFormula: FormulaConfig,
    roundingConfig: RoundingConfig,
    onBackToMembers: () -> Unit,
    onOpenEmployeeDetail: (memberId: String) -> Unit,
    onToggleFormula: () -> Unit,
    onApplyFormulaPerList: (FormulaConfig) -> Unit,
    onApplyFormulaGlobal: (FormulaConfig) -> Unit,
    onUseGlobalFormula: () -> Unit,
    onUpdateRounding: (RoundingConfig) -> Unit,
    onSaveTotal: () -> Unit,
    onOpenScan: () -> Unit,
    onCopiedFeedback: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showFormulaSheet by remember { mutableStateOf(false) }

    val tableHorizontalScroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "Tabel Perhitungan",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = IndonesianDateFormatter.formatFullDate(draft.scheduledDateTimeMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = onBackToMembers,
                    modifier = Modifier.testTag("table_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back)
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = onOpenScan,
                    modifier = Modifier.testTag("table_scan_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DocumentScanner,
                        contentDescription = "Scan Foto OCR",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = {
                        val text = PayrollEngine.formatCalculationLedgerText(computed)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Tabel Gajian", text))
                        onCopiedFeedback("Ringkasan tabel disalin ke papan klip")
                    },
                    modifier = Modifier.testTag("table_copy_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Salin ringkasan"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    FormulaControlBar(
                        effectiveFormula = computed.effectiveFormula,
                        usesGlobalFormula = draft.usesGlobalFormula,
                        globalFormula = globalFormula,
                        onToggleFormula = onToggleFormula,
                        onOpenFormulaEditor = { showFormulaSheet = true },
                        onUseGlobalFormula = onUseGlobalFormula,
                        modifier = Modifier.widthIn(max = 960.dp)
                    )
                }
            }

            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    RoundingRuleSelectorCard(
                        currentConfig = roundingConfig,
                        onSelectConfig = onUpdateRounding,
                        modifier = Modifier
                            .widthIn(max = 960.dp)
                            .testTag("table_rounding_selector")
                    )
                }
            }

            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 960.dp)
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
                                text = "Penambahan & Potongan diatur terpisah per karyawan. Ketuk nama karyawan di tabel untuk menambah/ubah. Potongan tidak dimasukkan ke Total Perhitungan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 960.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(tableHorizontalScroll)
                        ) {
                            Row(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f))
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                TableHeaderCell("Nama (Ketuk utk Edit)", width = 148.dp)
                                TableHeaderCell(
                                    text = if (computed.effectiveFormula.enabled) {
                                        "Hasil (${computed.effectiveFormula.shortLabel()})"
                                    } else {
                                        "Hasil (Jumlah × Harga)"
                                    },
                                    width = 140.dp
                                )
                                TableHeaderCell(
                                    text = "Penambahan (+)",
                                    width = 122.dp,
                                    accent = EmeraldVerified
                                )
                                TableHeaderCell(
                                    text = "Potongan (−) Info",
                                    width = 126.dp,
                                    accent = PtDeductionAccent
                                )
                                TableHeaderCell("Total Karyawan", width = 128.dp)
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            computed.members.forEachIndexed { idx, rowResult ->
                                SummaryTableRow(
                                    index = idx,
                                    result = rowResult,
                                    onOpenEmployee = { onOpenEmployeeDetail(rowResult.member.id) }
                                )
                                if (idx < computed.members.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Summary & Actions
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 960.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "TOTAL PERHITUNGAN (TANPA POTONGAN)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Sebelum dipotong & ditambah: ${PayrollCalculator.formatRupiah(computed.totalBeforeAdjustments)}",
                                style = NumericSmallStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (computed.totalAdditions.signum() > 0 || computed.totalDeductions.signum() > 0) {
                                val parts = buildList {
                                    if (computed.totalAdditions.signum() > 0) {
                                        add("Penambahan: +${PayrollCalculator.formatRupiah(computed.totalAdditions)}")
                                    }
                                    if (computed.totalDeductions.signum() > 0) {
                                        add("Potongan (info): ${PayrollCalculator.formatRupiah(computed.totalDeductions)}")
                                    }
                                }
                                Text(
                                    text = parts.joinToString(" • "),
                                    style = NumericSmallStyle,
                                    color = if (computed.totalDeductions.signum() > 0) PtDeductionAccent else EmeraldVerified
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = PayrollCalculator.formatRupiah(computed.grandTotal),
                            style = NumericHeadlineStyle.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("table_grand_total_text")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onBackToMembers,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("table_bottom_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Kembali")
                        }
                        Button(
                            onClick = onSaveTotal,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("table_save_total_button")
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (draft.isEditingExisting) "Simpan Perubahan" else "Simpan Total",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFormulaSheet) {
        FormulaEditorSheet(
            currentFormula = computed.effectiveFormula,
            usesGlobalFormula = draft.usesGlobalFormula,
            globalFormula = globalFormula,
            showPerListOption = true,
            onDismiss = { showFormulaSheet = false },
            onApplyPerList = onApplyFormulaPerList,
            onApplyGlobal = onApplyFormulaGlobal,
            onUseGlobalFormula = onUseGlobalFormula
        )
    }
}

@Composable
private fun TableHeaderCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    accent: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = accent,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width)
    )
}

@Composable
private fun SummaryTableRow(
    index: Int,
    result: ComputedMemberResult,
    onOpenEmployee: () -> Unit
) {
    Row(
        modifier = Modifier
            .background(
                if (index % 2 == 0) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
            )
            .clickable(onClick = onOpenEmployee)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.width(148.dp)) {
            Text(
                text = result.member.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${result.validRowCount} baris • Ketuk edit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(modifier = Modifier.width(140.dp)) {
            Text(
                text = PayrollCalculator.formatRupiah(result.subtotalAfterFormula),
                style = NumericFormulaStyle.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Asli: ${PayrollCalculator.formatRawNumber(result.subtotalRaw)}",
                style = NumericSmallStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = if (result.totalAdditions.signum() > 0) {
                "+ ${PayrollCalculator.formatRupiah(result.totalAdditions)}"
            } else "–",
            style = NumericFormulaStyle.copy(
                fontWeight = if (result.totalAdditions.signum() > 0) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (result.totalAdditions.signum() > 0) EmeraldVerified else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(122.dp)
        )

        Text(
            text = if (result.totalDeductions.signum() > 0) {
                "− ${PayrollCalculator.formatRupiah(result.totalDeductions)}"
            } else "–",
            style = NumericFormulaStyle.copy(
                fontWeight = if (result.totalDeductions.signum() > 0) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (result.totalDeductions.signum() > 0) PtDeductionAccent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(126.dp)
        )

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = EmeraldContainer,
            modifier = Modifier.width(128.dp)
        ) {
            Text(
                text = PayrollCalculator.formatRupiah(result.finalTotal),
                style = NumericFormulaStyle.copy(fontWeight = FontWeight.Bold),
                color = EmeraldVerified,
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 12.dp)
                    .testTag("table_member_total_$index")
            )
        }
    }
}
