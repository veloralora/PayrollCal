package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.AdjustmentColumn
import com.example.domain.AdjustmentOperation
import com.example.domain.FormulaConfig
import com.example.domain.FormulaOperation
import com.example.domain.PayrollCalculator
import com.example.domain.RoundingConfig
import com.example.domain.RoundingModeType
import com.example.ui.theme.NumericFormulaStyle
import com.example.ui.theme.NumericSmallStyle
import java.math.BigDecimal

/**
 * Formula Control Bar displayed on every calculation list (Section 3):
 * - Sakelar operasi button (e.g. "÷ 12"): when clicked to disable, the number ("12") turns grey
 *   and the operation is bypassed. Clicking again re-enables with the last setting.
 * - "Ubah Rumus" button opening the full FormulaEditorSheet with Per Daftar, Global, and
 *   "Gunakan Rumus Global" scope options.
 */
@Composable
fun FormulaControlBar(
    effectiveFormula: FormulaConfig,
    usesGlobalFormula: Boolean,
    globalFormula: FormulaConfig,
    onToggleFormula: () -> Unit,
    onOpenFormulaEditor: () -> Unit,
    onUseGlobalFormula: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = effectiveFormula.enabled
    val disabledGrey = Color(0xFF94A3B8)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant
        )
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Functions,
                        contentDescription = null,
                        tint = if (isActive) MaterialTheme.colorScheme.primary else disabledGrey,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "Pengaturan Rumus",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (usesGlobalFormula) {
                                "Cakupan: Global (${globalFormula.shortLabel()})"
                            } else {
                                "Cakupan: Khusus daftar ini"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (usesGlobalFormula) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Sakelar tombol operasi (e.g. ÷ 12)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isActive) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        1.dp,
                        if (isActive) MaterialTheme.colorScheme.primary else disabledGrey
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onToggleFormula)
                        .testTag("formula_toggle_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = effectiveFormula.operation.symbol,
                            style = NumericFormulaStyle.copy(fontWeight = FontWeight.Bold),
                            color = if (isActive) MaterialTheme.colorScheme.primary else disabledGrey
                        )
                        Text(
                            text = effectiveFormula.formattedOperand(),
                            style = NumericFormulaStyle.copy(fontWeight = FontWeight.Bold),
                            color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else disabledGrey,
                            modifier = Modifier.testTag("formula_operand_text")
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isActive) MaterialTheme.colorScheme.primary else disabledGrey
                        ) {
                            Text(
                                text = if (isActive) "AKTIF" else "MATI",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onOpenFormulaEditor,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("open_formula_editor_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ubah Rumus")
                }

                if (!usesGlobalFormula) {
                    TextButton(
                        onClick = onUseGlobalFormula,
                        modifier = Modifier.testTag("use_global_formula_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Restore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Gunakan Rumus Global")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormulaEditorSheet(
    currentFormula: FormulaConfig,
    usesGlobalFormula: Boolean,
    globalFormula: FormulaConfig,
    showPerListOption: Boolean = true,
    onDismiss: () -> Unit,
    onApplyPerList: (FormulaConfig) -> Unit,
    onApplyGlobal: (FormulaConfig) -> Unit,
    onUseGlobalFormula: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var enabled by remember(currentFormula) { mutableStateOf(currentFormula.enabled) }
    var operation by remember(currentFormula) { mutableStateOf(currentFormula.operation) }
    var operandText by remember(currentFormula) { mutableStateOf(currentFormula.operand) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun validateAndBuild(): FormulaConfig? {
        val cleaned = operandText.trim()
        val parsed = PayrollCalculator.parseNumber(cleaned)
        if (parsed == null) {
            errorMessage = "Masukkan angka nilai rumus yang valid"
            return null
        }
        if (enabled && operation == FormulaOperation.DIVIDE && parsed <= BigDecimal.ZERO) {
            errorMessage = "Jika operasi bagi (÷) aktif, nilai pembagi wajib lebih besar dari 0"
            return null
        }
        errorMessage = null
        return FormulaConfig(
            enabled = enabled,
            operation = operation,
            operand = PayrollCalculator.toPlainExactString(parsed)
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Ubah Rumus Perhitungan",
                style = MaterialTheme.typography.headlineSmall
            )

            // Active status toggle
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Status Rumus",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = if (enabled) "Rumus aktif dan diterapkan pada nilai dasar"
                            else "Rumus nonaktif — nilai dasar tidak diubah",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            errorMessage = null
                        },
                        modifier = Modifier.testTag("formula_editor_enabled_switch")
                    )
                }
            }

            // Operation Picker (÷, ×, +, −)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Pilih Operasi",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FormulaOperation.entries.forEach { op ->
                        val selected = operation == op
                        FilterChip(
                            selected = selected,
                            onClick = {
                                operation = op
                                errorMessage = null
                            },
                            label = {
                                Text(
                                    text = "${op.symbol} ${op.labelId}",
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("formula_op_${op.name.lowercase()}")
                        )
                    }
                }
            }

            // Operand Value Field (0 allowed for ×, +, −; >0 required for ÷ when active)
            OutlinedTextField(
                value = operandText,
                onValueChange = {
                    operandText = it
                    errorMessage = null
                },
                label = { Text("Nilai") },
                placeholder = { Text("Contoh: 12, 10, 50000") },
                prefix = {
                    Text(
                        text = "${operation.symbol} ",
                        style = NumericFormulaStyle.copy(fontWeight = FontWeight.Bold),
                        color = if (enabled) MaterialTheme.colorScheme.primary else Color(0xFF94A3B8)
                    )
                },
                singleLine = true,
                textStyle = NumericFormulaStyle,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = errorMessage != null,
                supportingText = {
                    if (errorMessage != null) {
                        Text(errorMessage!!)
                    } else {
                        Text(
                            if (operation == FormulaOperation.DIVIDE) {
                                "Untuk operasi bagi (÷) aktif, nilai wajib lebih besar dari 0."
                            } else {
                                "Angka 0 tetap dihitung sebagai nilai yang diisi."
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("formula_operand_input")
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Scope Actions: Per Daftar, Global, Gunakan Rumus Global
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Cakupan Perubahan",
                    style = MaterialTheme.typography.labelLarge
                )

                if (showPerListOption) {
                    Button(
                        onClick = {
                            val built = validateAndBuild() ?: return@Button
                            onApplyPerList(built)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("formula_apply_per_list_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Terapkan Per Daftar (Hanya Daftar Ini)")
                    }
                }

                if (showPerListOption) {
                    OutlinedButton(
                        onClick = {
                            val built = validateAndBuild() ?: return@OutlinedButton
                            onApplyGlobal(built)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("formula_apply_global_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Terapkan Global (Semua Pengikut Global)")
                    }
                } else {
                    Button(
                        onClick = {
                            val built = validateAndBuild() ?: return@Button
                            onApplyGlobal(built)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("formula_apply_global_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Simpan Rumus Global")
                    }
                }

                if (showPerListOption && !usesGlobalFormula) {
                    TextButton(
                        onClick = {
                            onUseGlobalFormula()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("formula_sheet_use_global_button")
                    ) {
                        Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Gunakan Rumus Global (${globalFormula.shortLabel()})")
                    }
                }
            }
        }
    }
}

@Composable
fun AdjustmentColumnDialog(
    initialColumn: AdjustmentColumn? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, AdjustmentOperation) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember(initialColumn) { mutableStateOf(initialColumn?.name.orEmpty()) }
    var operation by remember(initialColumn) {
        mutableStateOf(initialColumn?.operation ?: AdjustmentOperation.ADD)
    }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialColumn == null) "Tambah Kolom Penyesuaian" else "Edit Kolom Penyesuaian")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Kolom ini hanya berlaku untuk karyawan ini pada perhitungan tanggal ini dan tidak memengaruhi karyawan atau tanggal lain.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = { Text("Nama Kolom") },
                    placeholder = { Text("Contoh: Bonus, Kasbon, Potongan PT") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("adjustment_col_name_input")
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Operasi", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AdjustmentOperation.entries.forEach { op ->
                            val selected = operation == op
                            FilterChip(
                                selected = selected,
                                onClick = { operation = op },
                                label = { Text("${op.symbol} ${op.labelId}") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("adjustment_op_${op.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) {
                        error = "Nama kolom tidak boleh kosong"
                    } else {
                        onConfirm(trimmed, operation)
                    }
                },
                modifier = Modifier.testTag("adjustment_col_confirm_button")
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (initialColumn != null && onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.action_delete))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )
}

@Composable
fun RoundingRuleSelectorCard(
    currentConfig: RoundingConfig,
    onSelectConfig: (RoundingConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCustomDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Aturan Pembulatan ke Bawah",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Aktif: ${currentConfig.labelIndonesian()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            if (currentConfig.isDefaultFloor100()) {
                Text(
                    text = "Rumus Default: FLOOR(N ÷ 100) × 100 (akhiran pas 50 seperti 70.550 tetap) • Hasil asli tetap ditampilkan",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Kolom Asli
                FilterChip(
                    selected = currentConfig.mode == RoundingModeType.NONE,
                    onClick = { onSelectConfig(RoundingConfig.ASLI) },
                    label = { Text("Asli") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("rounding_chip_asli")
                )

                // 2. Kolom Default (FLOOR(N / 100) × 100) tepat di sebelah kolom Asli
                FilterChip(
                    selected = currentConfig.isDefaultFloor100(),
                    onClick = { onSelectConfig(RoundingConfig.DEFAULT) },
                    label = { Text("Default (↓ 100)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("rounding_chip_default")
                )

                // 3. Preset kelipatan lainnya (tanpa duplikasi 100)
                PayrollCalculator.PRESET_ROUNDING_STEPS
                    .filterNot { it == "100" }
                    .forEach { step ->
                        val isSelected = currentConfig.mode == RoundingModeType.FLOOR_TO_STEP &&
                            currentConfig.step == step
                        val formattedStep = PayrollCalculator.formatNumber(BigDecimal(step), 0)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                onSelectConfig(RoundingConfig(mode = RoundingModeType.FLOOR_TO_STEP, step = step))
                            },
                            label = { Text("↓ $formattedStep") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }

                // 4. Kolom Kustomisasi (tetap dipertahankan)
                val isCustomActive = currentConfig.mode == RoundingModeType.FLOOR_TO_STEP &&
                    currentConfig.step !in PayrollCalculator.PRESET_ROUNDING_STEPS
                FilterChip(
                    selected = isCustomActive,
                    onClick = { showCustomDialog = true },
                    label = {
                        Text(if (isCustomActive) "Kustom (↓ ${currentConfig.step})" else "Kustom")
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("rounding_chip_custom")
                )
            }
        }
    }

    if (showCustomDialog) {
        var stepText by remember { mutableStateOf(currentConfig.step) }
        var errorText by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text("Kelipatan Pembulatan ke Bawah") },
            text = {
                OutlinedTextField(
                    value = stepText,
                    onValueChange = {
                        stepText = it
                        errorText = null
                    },
                    label = { Text("Kelipatan (Step)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = errorText != null,
                    supportingText = errorText?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val parsed = PayrollCalculator.parseNumber(stepText)
                    if (parsed == null || parsed <= BigDecimal.ZERO) {
                        errorText = "Masukkan angka lebih besar dari 0"
                    } else {
                        onSelectConfig(
                            RoundingConfig(
                                mode = RoundingModeType.FLOOR_TO_STEP,
                                step = PayrollCalculator.toPlainExactString(parsed)
                            )
                        )
                        showCustomDialog = false
                    }
                }) {
                    Text("Terapkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    confirmLabel: String = stringResource(R.string.action_delete),
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.testTag("confirm_delete_button")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_delete_button")
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBackupSheet(
    globalFormula: FormulaConfig,
    roundingConfig: RoundingConfig,
    onDismiss: () -> Unit,
    onOpenGlobalFormulaEditor: () -> Unit,
    onUpdateRounding: (RoundingConfig) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall
            )

            // Global Formula Quick Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Rumus Global Bawaan", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "${globalFormula.shortLabel()} (${if (globalFormula.enabled) "Aktif" else "Nonaktif"})",
                            style = NumericSmallStyle.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenGlobalFormulaEditor()
                        },
                        modifier = Modifier.testTag("settings_edit_global_formula_button")
                    ) {
                        Text("Ubah")
                    }
                }
            }

            RoundingRuleSelectorCard(
                currentConfig = roundingConfig,
                onSelectConfig = onUpdateRounding
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_backup_section),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.settings_backup_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onExportBackup,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_backup_button")
                        ) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Backup")
                        }
                        OutlinedButton(
                            onClick = onImportBackup,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("import_backup_button")
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restore")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.settings_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
