package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.IndonesianDateFormatter
import com.example.ui.DateTimeSetupState
import com.example.ui.theme.NumericHeadlineStyle

/**
 * Step 2 of Alur Perhitungan Gaji:
 * Displays editable Date (Tanggal), Month (Bulan), Year (Tahun), and Time (Jam & Menit),
 * pre-populated from the device's local date and time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeSetupScreen(
    state: DateTimeSetupState,
    masterEmployeeCount: Int,
    onBack: () -> Unit,
    onUpdateDateTime: (year: Int?, monthZeroBased: Int?, dayOfMonth: Int?, hourOfDay: Int?, minute: Int?) -> Unit,
    onResetToNow: () -> Unit,
    onConfirmAndContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    var monthDropdownExpanded by remember { mutableStateOf(false) }

    var dayText by remember(state.dayOfMonth) { mutableStateOf(state.dayOfMonth.toString()) }
    var yearText by remember(state.year) { mutableStateOf(state.year.toString()) }
    var hourText by remember(state.hourOfDay) { mutableStateOf("%02d".format(state.hourOfDay)) }
    var minuteText by remember(state.minute) { mutableStateOf("%02d".format(state.minute)) }

    val previewMillis = state.toMillis()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "Tanggal & Jam Perhitungan",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Langkah 1 dari 3",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("datetime_back_button")
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
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Live Indonesian Date/Time Preview
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Label Riwayat yang Akan Disimpan",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = IndonesianDateFormatter.formatFullDate(previewMillis),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("datetime_preview_date")
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AccessTime,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Jam ${IndonesianDateFormatter.formatTime(previewMillis)}",
                                    style = NumericHeadlineStyle,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag("datetime_preview_time")
                                )
                            }
                        }
                    }
                }

                // Editable Tanggal, Bulan, Tahun Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Tanggal, Bulan, dan Tahun",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = dayText,
                                    onValueChange = { raw ->
                                        val digits = raw.filter { it.isDigit() }.take(2)
                                        dayText = digits
                                        digits.toIntOrNull()?.let { d ->
                                            if (d in 1..31) onUpdateDateTime(null, null, d, null, null)
                                        }
                                    },
                                    label = { Text("Tanggal") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .testTag("datetime_day_input")
                                )

                                Box(modifier = Modifier.weight(1.5f)) {
                                    OutlinedTextField(
                                        value = IndonesianDateFormatter.MONTHS[state.monthZeroBased],
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Bulan") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("datetime_month_input")
                                    )
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .clickable { monthDropdownExpanded = true }
                                    )
                                    DropdownMenu(
                                        expanded = monthDropdownExpanded,
                                        onDismissRequest = { monthDropdownExpanded = false }
                                    ) {
                                        IndonesianDateFormatter.MONTHS.forEachIndexed { idx, monthName ->
                                            DropdownMenuItem(
                                                text = { Text(monthName) },
                                                onClick = {
                                                    onUpdateDateTime(null, idx, null, null, null)
                                                    monthDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = yearText,
                                    onValueChange = { raw ->
                                        val digits = raw.filter { it.isDigit() }.take(4)
                                        yearText = digits
                                        digits.toIntOrNull()?.let { y ->
                                            if (y in 2000..2100) onUpdateDateTime(y, null, null, null, null)
                                        }
                                    },
                                    label = { Text("Tahun") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .testTag("datetime_year_input")
                                )
                            }
                        }
                    }
                }

                // Editable Jam & Menit Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Jam Perhitungan",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                OutlinedButton(
                                    onClick = onResetToNow,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("datetime_reset_now_button")
                                ) {
                                    Text("Waktu Sekarang")
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = hourText,
                                    onValueChange = { raw ->
                                        val digits = raw.filter { it.isDigit() }.take(2)
                                        hourText = digits
                                        digits.toIntOrNull()?.let { h ->
                                            if (h in 0..23) onUpdateDateTime(null, null, null, h, null)
                                        }
                                    },
                                    label = { Text("Jam (00–23)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("datetime_hour_input")
                                )

                                Text(
                                    text = ":",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                OutlinedTextField(
                                    value = minuteText,
                                    onValueChange = { raw ->
                                        val digits = raw.filter { it.isDigit() }.take(2)
                                        minuteText = digits
                                        digits.toIntOrNull()?.let { m ->
                                            if (m in 0..59) onUpdateDateTime(null, null, null, null, m)
                                        }
                                    },
                                    label = { Text("Menit (00–59)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("datetime_minute_input")
                                )
                            }
                        }
                    }
                }

                // Continue CTA
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "$masterEmployeeCount nama dari Data Karyawan akan langsung dimuat pada langkah berikutnya.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onConfirmAndContinue,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("datetime_confirm_continue_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Simpan & Buka Daftar Karyawan", style = MaterialTheme.typography.labelLarge)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
