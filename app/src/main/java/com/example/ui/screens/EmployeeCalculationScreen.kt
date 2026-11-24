package com.example.ui.screens

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.AdjustmentColumn
import com.example.domain.AdjustmentOperation
import com.example.domain.ComputedCalculationSummary
import com.example.domain.ComputedRowResult
import com.example.domain.FormulaConfig
import com.example.domain.PayrollCalculator
import com.example.domain.RoundingConfig
import com.example.domain.RoundingModeType
import com.example.ui.ActiveCalculationDraft
import com.example.ui.AutoSaveStatus
import com.example.ui.components.AdjustmentColumnDialog
import com.example.ui.components.FormulaControlBar
import com.example.ui.components.FormulaEditorSheet
import com.example.ui.components.RoundingRuleSelectorCard
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.NumericDisplayStyle
import com.example.ui.theme.NumericFormulaStyle
import com.example.ui.theme.NumericHeadlineStyle
import com.example.ui.theme.NumericSmallStyle
import com.example.ui.theme.PtDeductionAccent
import com.example.ui.theme.PtDeductionBorder
import com.example.ui.theme.PtDeductionContainer

/**
 * Per-Employee Calculation Screen opened directly when tapping an employee name in Daftar Karyawan:
 * - Edits auto-save immediately to local Room database so Tab Total & Riwayat update live.
 * - Keeps a "Simpan Perubahan" button as a safe fallback alongside "Kembali ke Daftar".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeCalculationScreen(
    memberId: String,
    draft: ActiveCalculationDraft,
    computed: ComputedCalculationSummary,
    globalFormula: FormulaConfig,
    roundingConfig: RoundingConfig,
    autoSaveStatus: AutoSaveStatus,
    onBackToMembers: () -> Unit,
    onFlushOnLeave: () -> Unit,
    onSaveFallback: () -> Unit,
    onSwitchMember: (String) -> Unit,
    onAddRow: () -> Unit,
    onUpdateRow: (rowId: String, n1: String?, n2: String?) -> Unit,
    onRemoveRow: (rowId: String) -> Unit,
    onToggleFormula: () -> Unit,
    onApplyFormulaPerList: (FormulaConfig) -> Unit,
    onApplyFormulaGlobal: (FormulaConfig) -> Unit,
    onUseGlobalFormula: () -> Unit,
    onUpdateRounding: (RoundingConfig) -> Unit,
    onAddAdjustmentColumn: (String, AdjustmentOperation) -> Unit,
    onUpdateAdjustmentColumn: (String, String, AdjustmentOperation) -> Unit,
    onDeleteAdjustmentColumn: (String) -> Unit,
    onUpdateAdjustmentValue: (columnId: String, value: String) -> Unit,
    onOpenScanForMember: () -> Unit,
    modifier: Modifier = Modifier
) {
    DisposableEffect(Unit) {
        onDispose {
            onFlushOnLeave()
        }
    }

    val memberIndex = computed.members.indexOfFirst { it.member.id == memberId }
    val memberResult = computed.members.getOrNull(memberIndex)

    if (memberResult == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Data karyawan tidak ditemukan", style = MaterialTheme.typography.titleMedium)
                Button(onClick = onBackToMembers) { Text("Kembali ke Daftar Karyawan") }
            }
        }
        return
    }

    var showFormulaSheet by remember { mutableStateOf(false) }
    var showAddColumnDialog by remember { mutableStateOf(false) }
    var editingColumn by remember { mutableStateOf<AdjustmentColumn?>(null) }

    val prevMember = computed.members.getOrNull(memberIndex - 1)
    val nextMember = computed.members.getOrNull(memberIndex + 1)
    val effectiveFormula = computed.effectiveFormula

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = memberResult.member.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Karyawan ${memberIndex + 1} dari ${computed.members.size} • Jumlah × Harga ${
                            if (effectiveFormula.enabled) effectiveFormula.shortLabel() else "(Rumus Mati)"
                        }",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = onBackToMembers,
                    modifier = Modifier.testTag("emp_calc_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back)
                    )
                }
            },
            actions = {
                if (prevMember != null) {
                    IconButton(
                        onClick = { onSwitchMember(prevMember.member.id) },
                        modifier = Modifier.testTag("emp_calc_prev_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Karyawan sebelumnya"
                        )
                    }
                }
                if (nextMember != null) {
                    IconButton(
                        onClick = { onSwitchMember(nextMember.member.id) },
                        modifier = Modifier.testTag("emp_calc_next_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Karyawan berikutnya"
                        )
                    }
                }
                IconButton(
                    onClick = onOpenScanForMember,
                    modifier = Modifier.testTag("emp_calc_scan_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DocumentScanner,
                        contentDescription = "Scan Foto OCR",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Formula Control Bar (÷ 12 toggle + Ubah Rumus: ÷, ×, +, −)
                item {
                    FormulaControlBar(
                        effectiveFormula = effectiveFormula,
                        usesGlobalFormula = draft.usesGlobalFormula,
                        globalFormula = globalFormula,
                        onToggleFormula = onToggleFormula,
                        onOpenFormulaEditor = { showFormulaSheet = true },
                        onUseGlobalFormula = onUseGlobalFormula
                    )
                }

                // 2. Calculation Rows Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Perhitungan (${memberResult.member.name})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${memberResult.computedRows.size} baris",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // 3. Jumlah × Harga [op operand] = N Rows (v1 style with v2 configurable formula)
                itemsIndexed(
                    items = memberResult.computedRows,
                    key = { _, item -> item.row.id }
                ) { idx, rowResult ->
                    EmployeeFormulaRowCard(
                        index = idx,
                        result = rowResult,
                        formula = effectiveFormula,
                        roundingConfig = roundingConfig,
                        canDelete = memberResult.computedRows.size > 1,
                        onJumlahChange = { onUpdateRow(rowResult.row.id, it, null) },
                        onHargaChange = { onUpdateRow(rowResult.row.id, null, it) },
                        onToggleFormula = onToggleFormula,
                        onRemove = { onRemoveRow(rowResult.row.id) }
                    )
                }

                // 4. "+ Tambah Kolom Perhitungan" button directly below N1 × N2 rows
                item {
                    OutlinedButton(
                        onClick = onAddRow,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("emp_calc_add_row_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Tambah Kolom Perhitungan", style = MaterialTheme.typography.labelLarge)
                    }
                }

                // 5. Potongan & Penambahan Section directly below (v1 style + unlimited dynamic columns)
                item {
                    val hasPtCol = computed.columns.any { it.name.equals("Potongan PT", ignoreCase = true) }
                    val hasBonusCol = computed.columns.any { it.name.equals("Bonus", ignoreCase = true) }
                    val hasKasbonCol = computed.columns.any { it.name.equals("Kasbon", ignoreCase = true) }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(
                            1.dp,
                            if (computed.columns.isNotEmpty()) PtDeductionBorder else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Potongan & Penambahan (Opsional)",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = PtDeductionAccent,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Dihitung terpisah setelah hasil Jumlah × Harga. Sel kosong dilewati; ketik 0 jika ingin menampilkan 0.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Quick 1-tap preset chips for common deductions/additions
                            if (!hasPtCol || !hasBonusCol || !hasKasbonCol) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (!hasPtCol) {
                                        AssistChip(
                                            onClick = { onAddAdjustmentColumn("Potongan PT", AdjustmentOperation.SUBTRACT) },
                                            label = { Text("+ Potongan PT (−)") },
                                            modifier = Modifier.testTag("quick_add_pt_col")
                                        )
                                    }
                                    if (!hasBonusCol) {
                                        AssistChip(
                                            onClick = { onAddAdjustmentColumn("Bonus", AdjustmentOperation.ADD) },
                                            label = { Text("+ Bonus (+)") },
                                            modifier = Modifier.testTag("quick_add_bonus_col")
                                        )
                                    }
                                    if (!hasKasbonCol) {
                                        AssistChip(
                                            onClick = { onAddAdjustmentColumn("Kasbon", AdjustmentOperation.SUBTRACT) },
                                            label = { Text("+ Kasbon (−)") },
                                            modifier = Modifier.testTag("quick_add_kasbon_col")
                                        )
                                    }
                                }
                            }

                            // Dynamic Adjustment Input Fields for this employee
                            if (computed.columns.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    computed.columns.forEach { col ->
                                        val isDeduction = col.operation == AdjustmentOperation.SUBTRACT
                                        val rawValue = memberResult.member.adjustmentValues[col.id].orEmpty()

                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isDeduction) PtDeductionContainer.copy(alpha = 0.65f)
                                            else EmeraldContainer.copy(alpha = 0.65f),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isDeduction) PtDeductionBorder else EmeraldVerified.copy(alpha = 0.25f)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedTextField(
                                                    value = rawValue,
                                                    onValueChange = { onUpdateAdjustmentValue(col.id, it) },
                                                    label = { Text(col.headerLabel()) },
                                                    prefix = {
                                                        Text(
                                                            text = "${col.operation.symbol} Rp ",
                                                            color = if (isDeduction) PtDeductionAccent else EmeraldVerified,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    },
                                                    singleLine = true,
                                                    textStyle = NumericFormulaStyle,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .testTag("emp_adj_input_${col.id}")
                                                )
                                                IconButton(
                                                    onClick = { editingColumn = col },
                                                    modifier = Modifier.testTag("emp_adj_edit_col_${col.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Edit,
                                                        contentDescription = "Ubah kolom ${col.name}",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Button to add more custom Potongan / Penambahan columns
                            OutlinedButton(
                                onClick = { showAddColumnDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("emp_calc_add_adjustment_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tambah Kolom Potongan / Penambahan")
                            }
                        }
                    }
                }

                // 6. Opsi Pembulatan Khusus ke Bawah (seperti v1)
                item {
                    RoundingRuleSelectorCard(
                        currentConfig = roundingConfig,
                        onSelectConfig = onUpdateRounding,
                        modifier = Modifier.testTag("emp_calc_rounding_selector")
                    )
                }

                // 7. Live Summary Card for this Employee (pure sum of each column's rounded results — never re-rounded)
                item {
                    val filledAdjustments = computed.columns.mapNotNull { col ->
                        val amount = memberResult.adjustmentAmounts[col.id]
                        if (amount != null) col to amount else null
                    }
                    val validRows = memberResult.computedRows.filter { it.isValid }
                    val sumBreakdownText = remember(validRows) {
                        when {
                            validRows.size in 2..4 -> validRows.joinToString(" + ") {
                                PayrollCalculator.formatNumber(it.afterFormula, 4)
                            }
                            validRows.size > 4 -> {
                                val firstThree = validRows.take(3).joinToString(" + ") {
                                    PayrollCalculator.formatNumber(it.afterFormula, 4)
                                }
                                "$firstThree + … (${validRows.size} kolom)"
                            }
                            else -> null
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Always show exact raw sum before per-column rounding
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Hasil Penjumlahan Asli (Sebelum Pembulatan)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Rp ${PayrollCalculator.formatRawNumber(memberResult.subtotalRaw)}",
                                        style = NumericFormulaStyle.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.testTag("emp_calc_subtotal_raw_text")
                                    )
                                }
                            }

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
                                        text = if (filledAdjustments.isNotEmpty()) {
                                            "TOTAL PENJUMLAHAN (Sebelum Penyesuaian)"
                                        } else {
                                            "TOTAL PENJUMLAHAN"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = sumBreakdownText ?: "Jumlah langsung hasil tiap kolom (tanpa pembulatan ulang)",
                                        style = if (sumBreakdownText != null) NumericSmallStyle else MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = PayrollCalculator.formatRupiah(memberResult.subtotalAfterFormula),
                                    style = if (filledAdjustments.isNotEmpty()) NumericHeadlineStyle else NumericDisplayStyle,
                                    color = if (filledAdjustments.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag("emp_calc_subtotal_text")
                                )
                            }

                            if (filledAdjustments.isNotEmpty()) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                filledAdjustments.forEach { (col, amount) ->
                                    val isSub = col.operation == AdjustmentOperation.SUBTRACT
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = col.headerLabel(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isSub) PtDeductionAccent else EmeraldVerified
                                        )
                                        Text(
                                            text = "${col.operation.symbol} ${PayrollCalculator.formatRupiah(amount)}",
                                            style = NumericFormulaStyle.copy(fontWeight = FontWeight.Bold),
                                            color = if (isSub) PtDeductionAccent else EmeraldVerified
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = EmeraldContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "TOTAL AKHIR",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = EmeraldVerified,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = PayrollCalculator.formatRupiah(memberResult.finalTotal),
                                            style = NumericDisplayStyle,
                                            color = EmeraldVerified,
                                            modifier = Modifier.testTag("emp_calc_final_total_text")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sticky Footer: Live Auto-Save Status + Kembali ke Daftar + Safe Fallback "Simpan Perubahan"
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (autoSaveStatus != AutoSaveStatus.IDLE) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.testTag("emp_calc_autosave_status")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = if (autoSaveStatus == AutoSaveStatus.SAVED) EmeraldVerified
                                else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (autoSaveStatus == AutoSaveStatus.SAVING) {
                                    "Menyimpan perubahan secara otomatis…"
                                } else {
                                    "Hasil otomatis tersimpan ke Daftar & Tab Total"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (autoSaveStatus == AutoSaveStatus.SAVED) EmeraldVerified
                                else MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                onFlushOnLeave()
                                onBackToMembers()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("emp_calc_done_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Daftar Karyawan", style = MaterialTheme.typography.labelLarge)
                        }

                        Button(
                            onClick = onSaveFallback,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("emp_calc_save_fallback_button")
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Simpan Perubahan", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }

    if (showFormulaSheet) {
        FormulaEditorSheet(
            currentFormula = effectiveFormula,
            usesGlobalFormula = draft.usesGlobalFormula,
            globalFormula = globalFormula,
            showPerListOption = true,
            onDismiss = { showFormulaSheet = false },
            onApplyPerList = onApplyFormulaPerList,
            onApplyGlobal = onApplyFormulaGlobal,
            onUseGlobalFormula = onUseGlobalFormula
        )
    }

    if (showAddColumnDialog) {
        AdjustmentColumnDialog(
            onDismiss = { showAddColumnDialog = false },
            onConfirm = { name, op ->
                onAddAdjustmentColumn(name, op)
                showAddColumnDialog = false
            }
        )
    }

    editingColumn?.let { col ->
        AdjustmentColumnDialog(
            initialColumn = col,
            onDismiss = { editingColumn = null },
            onConfirm = { name, op ->
                onUpdateAdjustmentColumn(col.id, name, op)
                editingColumn = null
            },
            onDelete = {
                onDeleteAdjustmentColumn(col.id)
                editingColumn = null
            }
        )
    }
}

@Composable
private fun EmployeeFormulaRowCard(
    index: Int,
    result: ComputedRowResult,
    formula: FormulaConfig,
    roundingConfig: RoundingConfig,
    canDelete: Boolean,
    onJumlahChange: (String) -> Unit,
    onHargaChange: (String) -> Unit,
    onToggleFormula: () -> Unit,
    onRemove: () -> Unit
) {
    val disabledGrey = Color(0xFF94A3B8)
    val isActive = formula.enabled
    val isRoundingActive = roundingConfig.mode != RoundingModeType.NONE

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                Text(
                    text = "Kolom Hitungan #${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                if (canDelete) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("emp_calc_remove_row_$index")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Hapus baris ${index + 1}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = result.row.n1Text,
                    onValueChange = onJumlahChange,
                    label = { Text("Jumlah") },
                    singleLine = true,
                    textStyle = NumericFormulaStyle,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("emp_calc_n1_$index")
                )

                Text(
                    text = "×",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = result.row.n2Text,
                    onValueChange = onHargaChange,
                    label = { Text("Harga") },
                    singleLine = true,
                    textStyle = NumericFormulaStyle,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("emp_calc_n2_$index")
                )

                // Interactive trailing formula pill (e.g. ÷ 12 — turns grey when toggled OFF!)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isActive) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        1.dp,
                        if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else disabledGrey
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onToggleFormula)
                        .testTag("emp_calc_row_formula_pill_$index")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formula.operation.symbol,
                            style = NumericFormulaStyle.copy(fontWeight = FontWeight.Bold),
                            color = if (isActive) MaterialTheme.colorScheme.primary else disabledGrey
                        )
                        Text(
                            text = formula.formattedOperand(),
                            style = NumericFormulaStyle.copy(fontWeight = FontWeight.Bold),
                            color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else disabledGrey
                        )
                    }
                }
            }

            if (result.isValid) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Jumlah × Harga",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = PayrollCalculator.formatRawNumber(result.product),
                                style = NumericSmallStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Hasil Asli (Sebelum Pembulatan)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = PayrollCalculator.formatRawNumber(result.afterFormulaRaw),
                                style = NumericSmallStyle.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = when {
                                    isRoundingActive -> "Hasil Pembulatan (${roundingConfig.shortBadge()})"
                                    isActive -> "Hasil (${formula.shortLabel()})"
                                    else -> "Hasil (Rumus Nonaktif)"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "= ${PayrollCalculator.formatRupiah(result.afterFormula)}",
                                style = NumericFormulaStyle.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("emp_calc_row_result_$index")
                            )
                        }
                    }
                }
            }
        }
    }
}
