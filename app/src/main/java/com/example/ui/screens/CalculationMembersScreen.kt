package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.CalculationMemberEntry
import com.example.domain.ComputedCalculationSummary
import com.example.domain.FormulaConfig
import com.example.domain.IndonesianDateFormatter
import com.example.domain.PayrollCalculator
import com.example.domain.PayrollEngine
import com.example.ui.ActiveCalculationDraft
import com.example.ui.AutoSaveStatus
import com.example.ui.components.FormulaControlBar
import com.example.ui.components.FormulaEditorSheet
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.NumericFormulaStyle
import com.example.ui.theme.NumericHeadlineStyle
import com.example.ui.theme.NumericSmallStyle

/**
 * Daftar Karyawan for the opened calculation:
 * - Tapping ANY employee card immediately opens THAT employee's calculation screen.
 * - Changes auto-save live, while Simpan Perubahan / Simpan Total remains as a safe fallback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculationMembersScreen(
    draft: ActiveCalculationDraft,
    computed: ComputedCalculationSummary,
    globalFormula: FormulaConfig,
    autoSaveStatus: AutoSaveStatus,
    onBack: () -> Unit,
    onSelectEmployee: (memberId: String) -> Unit,
    onAddOneOffMember: (String) -> Unit,
    onRenameMember: (String, String) -> Unit,
    onRemoveMember: (String) -> Unit,
    onToggleFormula: () -> Unit,
    onApplyFormulaPerList: (FormulaConfig) -> Unit,
    onApplyFormulaGlobal: (FormulaConfig) -> Unit,
    onUseGlobalFormula: () -> Unit,
    onOpenCalculationTable: () -> Unit,
    onSaveTotal: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddOneOffDialog by remember { mutableStateOf(false) }
    var editingMember by remember { mutableStateOf<CalculationMemberEntry?>(null) }
    var showFormulaSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "Daftar Karyawan",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = when (autoSaveStatus) {
                            AutoSaveStatus.SAVING -> "Menyimpan otomatis…"
                            AutoSaveStatus.SAVED -> "Tersimpan otomatis • Ketuk nama untuk edit"
                            AutoSaveStatus.IDLE -> if (draft.isEditingExisting) "Ketuk nama untuk edit perhitungan"
                            else "Ketuk nama karyawan untuk menghitung"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (autoSaveStatus == AutoSaveStatus.SAVED) EmeraldVerified
                        else MaterialTheme.colorScheme.primary
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("members_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back)
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Date & Time Header Card
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = IndonesianDateFormatter.formatFullDate(draft.scheduledDateTimeMillis),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Jam ${IndonesianDateFormatter.formatTime(draft.scheduledDateTimeMillis)} • ${draft.members.size} karyawan",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Formula Control Bar
                item {
                    FormulaControlBar(
                        effectiveFormula = computed.effectiveFormula,
                        usesGlobalFormula = draft.usesGlobalFormula,
                        globalFormula = globalFormula,
                        onToggleFormula = onToggleFormula,
                        onOpenFormulaEditor = { showFormulaSheet = true },
                        onUseGlobalFormula = onUseGlobalFormula
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Klik Nama untuk Buka Perhitungan",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${draft.members.size} orang",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Clickable Employee Cards
                itemsIndexed(
                    items = computed.members,
                    key = { _, item -> item.member.id }
                ) { index, computedMember ->
                    val member = computedMember.member
                    val validRows = computedMember.computedRows.filter { it.isValid }

                    Card(
                        onClick = { onSelectEmployee(member.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(
                            1.dp,
                            if (validRows.isNotEmpty()) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("calc_member_row_$index")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = member.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (member.isCustomOneOff) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer
                                            ) {
                                                Text(
                                                    text = "Khusus",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = if (validRows.isNotEmpty()) {
                                            "${validRows.size} baris hitungan • Ketuk untuk edit"
                                        } else {
                                            "Belum diisi • Ketuk untuk isi Jumlah × Harga"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (validRows.isNotEmpty()) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconButton(
                                    onClick = { editingMember = member },
                                    modifier = Modifier.testTag("calc_member_edit_$index")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = "Ubah nama ${member.name}",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onRemoveMember(member.id) },
                                    modifier = Modifier.testTag("calc_member_remove_$index")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Hapus ${member.name}",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Buka hitungan ${member.name}",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (validRows.isNotEmpty() || computedMember.netAdjustment.signum() != 0) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    validRows.take(3).forEach { r ->
                                        Text(
                                            text = PayrollEngine.formatRowEquation(r),
                                            style = NumericSmallStyle,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (validRows.size > 3) {
                                        Text(
                                            text = "+${validRows.size - 3} baris lainnya…",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (computedMember.netAdjustment.signum() != 0) {
                                                "Subtotal ${PayrollCalculator.formatRupiah(computedMember.subtotalAfterFormula)} • Penyesuaian ${PayrollCalculator.formatRupiah(computedMember.netAdjustment)}"
                                            } else {
                                                "Total Penjumlahan (${validRows.size} kolom)"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Asli (sebelum bulat): ${PayrollCalculator.formatRawNumber(computedMember.subtotalRaw)}",
                                            style = NumericSmallStyle,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = PayrollCalculator.formatRupiah(computedMember.finalTotal),
                                        style = NumericFormulaStyle.copy(fontWeight = FontWeight.Bold),
                                        color = EmeraldVerified
                                    )
                                }
                            }
                        }
                    }
                }

                // '+' Button directly below list for one-off calculation-only names
                item {
                    OutlinedButton(
                        onClick = { showAddOneOffDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("members_add_oneoff_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Tambah nama khusus", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Tambah Nama Khusus (+)", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // Bottom Sticky Summary & Save Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
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
                                text = "TOTAL PERHITUNGAN",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Sebelum dipotong & ditambah: ${PayrollCalculator.formatRupiah(computed.totalBeforeAdjustments)}",
                                style = NumericSmallStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (computed.totalDeductions.signum() > 0) {
                                Text(
                                    text = "Potongan (info, tdk masuk total): ${PayrollCalculator.formatRupiah(computed.totalDeductions)}",
                                    style = NumericSmallStyle,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text(
                                    text = "Sebelum pembulatan: Rp ${PayrollCalculator.formatRawNumber(computed.totalRawBeforeRounding)}",
                                    style = NumericSmallStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = PayrollCalculator.formatRupiah(computed.grandTotal),
                            style = NumericHeadlineStyle.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("members_grand_total_text")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenCalculationTable,
                            enabled = draft.members.isNotEmpty(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("members_open_table_button")
                        ) {
                            Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tabel")
                        }

                        Button(
                            onClick = onSaveTotal,
                            enabled = draft.members.isNotEmpty(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("members_save_total_button")
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

    if (showAddOneOffDialog) {
        var oneOffName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddOneOffDialog = false },
            title = { Text("Tambah Nama Khusus") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Nama ini hanya disimpan pada perhitungan yang sedang dibuka dan tidak akan masuk ke Data Karyawan master.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = oneOffName,
                        onValueChange = { oneOffName = it },
                        label = { Text("Nama Karyawan Tambahan") },
                        placeholder = { Text("Contoh: Pekerja Harian") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            if (oneOffName.isNotBlank()) {
                                onAddOneOffMember(oneOffName)
                                showAddOneOffDialog = false
                            }
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("oneoff_member_name_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddOneOffMember(oneOffName)
                        showAddOneOffDialog = false
                    },
                    enabled = oneOffName.isNotBlank(),
                    modifier = Modifier.testTag("oneoff_member_confirm_button")
                ) {
                    Text("Tambahkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddOneOffDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    editingMember?.let { member ->
        var editedName by remember(member.id) { mutableStateOf(member.name) }
        AlertDialog(
            onDismissRequest = { editingMember = null },
            title = { Text("Ubah Nama di Perhitungan Ini") },
            text = {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text("Nama") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameMember(member.id, editedName)
                        editingMember = null
                    },
                    enabled = editedName.isNotBlank()
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMember = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
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
