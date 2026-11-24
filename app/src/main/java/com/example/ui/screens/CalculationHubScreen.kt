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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.ComputedCalculationSummary
import com.example.domain.FormulaConfig
import com.example.domain.IndonesianDateFormatter
import com.example.domain.PayrollCalculator
import com.example.ui.ActiveCalculationDraft
import com.example.ui.theme.NumericFormulaStyle
import com.example.ui.theme.NumericHeadlineStyle

/**
 * Perhitungan Gaji Hub (Section 2 Step 1):
 * Entry point for "Buat Perhitungan", resuming an open draft, or directing to Data Karyawan if empty.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculationHubScreen(
    masterEmployeeCount: Int,
    globalFormula: FormulaConfig,
    activeDraft: ActiveCalculationDraft,
    computedDraft: ComputedCalculationSummary,
    recentCalculations: List<ComputedCalculationSummary>,
    onCreateCalculation: () -> Unit,
    onResumeActiveDraft: () -> Unit,
    onOpenSavedCalculation: (Long) -> Unit,
    onNavigateToMasterEmployees: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "Perhitungan Gaji",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Rumus Global: ${globalFormula.shortLabel()} (${if (globalFormula.enabled) "Aktif" else "Mati"})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Primary "Buat Perhitungan" Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Mulai Perhitungan Gaji Baru",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (masterEmployeeCount == 0) {
                                    "Data Karyawan masih kosong. Tambahkan minimal satu nama karyawan terlebih dahulu sebelum membuat perhitungan."
                                } else {
                                    "Pilih tanggal & jam perhitungan, periksa daftar karyawan ($masterEmployeeCount nama dari Data Karyawan), atur rumus & kolom penyesuaian, lalu simpan total."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Button(
                                onClick = onCreateCalculation,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("hub_create_calculation_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Filled.AddCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Buat Perhitungan", style = MaterialTheme.typography.labelLarge)
                            }

                            if (masterEmployeeCount == 0) {
                                OutlinedButton(
                                    onClick = onNavigateToMasterEmployees,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("hub_open_master_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Filled.Groups, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Isi Data Karyawan Sekarang")
                                }
                            }
                        }
                    }
                }

                // 2. Resume Open Draft if any
                if (activeDraft.isInitialized) {
                    item {
                        Card(
                            onClick = onResumeActiveDraft,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.EditNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(30.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (activeDraft.isEditingExisting) "Lanjutkan Edit Riwayat"
                                        else "Lanjutkan Perhitungan Berjalan",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = IndonesianDateFormatter.formatDateTime(activeDraft.scheduledDateTimeMillis),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "${activeDraft.members.size} karyawan • ${PayrollCalculator.formatRupiah(computedDraft.grandTotal)}",
                                        style = NumericFormulaStyle.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Lanjutkan",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // 3. Recent Saved Calculations
                if (recentCalculations.isNotEmpty()) {
                    item {
                        Text(
                            text = "Perhitungan Tersimpan Terbaru",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    items(
                        items = recentCalculations.take(5),
                        key = { it.calculationId }
                    ) { item ->
                        Card(
                            onClick = { onOpenSavedCalculation(item.calculationId) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = IndonesianDateFormatter.formatFullDate(item.scheduledDateTimeMillis),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "${IndonesianDateFormatter.formatTime(item.scheduledDateTimeMillis)} • ${item.members.size} karyawan",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = PayrollCalculator.formatRupiah(item.grandTotal),
                                        style = NumericHeadlineStyle,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
