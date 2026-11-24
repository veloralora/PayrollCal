package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.backup.PayrollJsonCodec
import com.example.data.local.MasterEmployeeEntity
import com.example.data.ocr.MlKitOcrEngine
import com.example.data.repository.PayrollRepository
import com.example.domain.AdjustmentColumn
import com.example.domain.AdjustmentOperation
import com.example.domain.CalculationMemberEntry
import com.example.domain.CalculationRowItem
import com.example.domain.ComputedCalculationSummary
import com.example.domain.FormulaConfig
import com.example.domain.FormulaOperation
import com.example.domain.IndonesianDateFormatter
import com.example.domain.PayrollCalculator
import com.example.domain.PayrollEngine
import com.example.domain.PayrollMonth
import com.example.domain.RoundingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.util.Calendar
import java.util.UUID

enum class AutoSaveStatus {
    IDLE,
    SAVING,
    SAVED
}

data class DateTimeSetupState(
    val year: Int,
    val monthZeroBased: Int,
    val dayOfMonth: Int,
    val hourOfDay: Int,
    val minute: Int
) {
    fun toMillis(): Long = IndonesianDateFormatter.buildMillis(
        year = year,
        monthZeroBased = monthZeroBased,
        dayOfMonth = dayOfMonth,
        hourOfDay = hourOfDay,
        minute = minute
    )

    companion object {
        fun now(): DateTimeSetupState {
            val cal = Calendar.getInstance()
            return DateTimeSetupState(
                year = cal.get(Calendar.YEAR),
                monthZeroBased = cal.get(Calendar.MONTH),
                dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
                hourOfDay = cal.get(Calendar.HOUR_OF_DAY),
                minute = cal.get(Calendar.MINUTE)
            )
        }

        fun fromMillis(millis: Long): DateTimeSetupState {
            val cal = Calendar.getInstance().apply { timeInMillis = millis }
            return DateTimeSetupState(
                year = cal.get(Calendar.YEAR),
                monthZeroBased = cal.get(Calendar.MONTH),
                dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
                hourOfDay = cal.get(Calendar.HOUR_OF_DAY),
                minute = cal.get(Calendar.MINUTE)
            )
        }
    }
}

data class ActiveCalculationDraft(
    val isInitialized: Boolean = false,
    val calculationId: Long = 0L,
    val scheduledDateTimeMillis: Long = System.currentTimeMillis(),
    val usesGlobalFormula: Boolean = true,
    val customFormula: FormulaConfig = FormulaConfig(),
    val columns: List<AdjustmentColumn> = emptyList(),
    val members: List<CalculationMemberEntry> = emptyList()
) {
    val isEditingExisting: Boolean get() = calculationId > 0L
}

data class ScanRowCandidate(
    val localId: Long,
    val n1Text: String = "",
    val n2Text: String = "",
    val sourceHint: String = ""
)

data class ScanScreenState(
    val selectedImageUri: Uri? = null,
    val isScanning: Boolean = false,
    val hasCompletedScan: Boolean = false,
    val rawOcrText: String = "",
    val detectedName: String = "",
    val targetMemberId: String? = null,
    val rows: List<ScanRowCandidate> = emptyList(),
    val errorMessage: String? = null
)

class PayrollViewModel(
    private val repository: PayrollRepository,
    private val ocrEngine: MlKitOcrEngine
) : ViewModel() {

    private var nextScanRowId = 1L
    private val saveMutex = Mutex()
    private var autoSaveJob: Job? = null

    val masterEmployees: StateFlow<List<MasterEmployeeEntity>> = repository.masterEmployees
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val globalFormula: StateFlow<FormulaConfig> = repository.globalFormula
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FormulaConfig())

    val roundingConfig: StateFlow<RoundingConfig> = repository.roundingConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RoundingConfig())

    val savedCalculations: StateFlow<List<ComputedCalculationSummary>> = repository.savedCalculations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalSavedPayroll: StateFlow<BigDecimal> = repository.totalSavedPayroll
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BigDecimal.ZERO)

    // ---- Monthly Scope (1-Month Period & Max 12-Month Window) ----

    private val _selectedTotalMonth = MutableStateFlow(PayrollMonth.now())
    val selectedTotalMonth: StateFlow<PayrollMonth> = _selectedTotalMonth.asStateFlow()

    val availableTotalMonths: StateFlow<List<PayrollMonth>> = savedCalculations
        .map { calcs ->
            val anchor = PayrollMonth.anchorMonth(
                referenceMonth = PayrollMonth.now(),
                storedTimestamps = calcs.map { it.scheduledDateTimeMillis }
            )
            PayrollMonth.lastTwelveMonthsWindow(anchor)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            PayrollMonth.lastTwelveMonthsWindow(PayrollMonth.now())
        )

    val monthCalculationCounts: StateFlow<Map<PayrollMonth, Int>> = savedCalculations
        .map { calcs ->
            calcs.groupingBy { PayrollMonth.fromMillis(it.scheduledDateTimeMillis) }.eachCount()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val monthlySavedCalculations: StateFlow<List<ComputedCalculationSummary>> = combine(
        savedCalculations,
        _selectedTotalMonth
    ) { calcs, month ->
        PayrollEngine.filterCalculationsByMonth(calcs, month)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthlyTotalPayroll: StateFlow<BigDecimal> = monthlySavedCalculations
        .map { PayrollEngine.sumSavedCalculations(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BigDecimal.ZERO)

    val currentMonthCalculations: StateFlow<List<ComputedCalculationSummary>> = savedCalculations
        .map { calcs -> PayrollEngine.filterCalculationsByMonth(calcs, PayrollMonth.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentMonthTotalPayroll: StateFlow<BigDecimal> = currentMonthCalculations
        .map { PayrollEngine.sumSavedCalculations(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BigDecimal.ZERO)

    private val _autoSaveStatus = MutableStateFlow(AutoSaveStatus.IDLE)
    val autoSaveStatus: StateFlow<AutoSaveStatus> = _autoSaveStatus.asStateFlow()

    init {
        viewModelScope.launch {
            repository.enforceTwelveMonthRetention()
        }
    }

    fun selectTotalMonth(month: PayrollMonth) {
        _selectedTotalMonth.value = month
    }

    fun stepTotalMonth(delta: Int) {
        val months = availableTotalMonths.value
        if (months.isEmpty()) return
        val current = _selectedTotalMonth.value
        val candidate = current.plusMonths(delta)
        val oldest = months.last()
        val newest = months.first()
        if (candidate >= oldest && candidate <= newest) {
            _selectedTotalMonth.value = candidate
        }
    }

    private val _needsMasterEmployeeGuide = MutableStateFlow(false)
    val needsMasterEmployeeGuide: StateFlow<Boolean> = _needsMasterEmployeeGuide.asStateFlow()

    private val _dateTimeSetup = MutableStateFlow(DateTimeSetupState.now())
    val dateTimeSetup: StateFlow<DateTimeSetupState> = _dateTimeSetup.asStateFlow()

    private val _activeDraft = MutableStateFlow(ActiveCalculationDraft())
    val activeDraft: StateFlow<ActiveCalculationDraft> = _activeDraft.asStateFlow()

    val computedActiveDraft: StateFlow<ComputedCalculationSummary> = combine(
        _activeDraft,
        globalFormula,
        roundingConfig
    ) { draft, global, rounding ->
        PayrollEngine.computeCalculation(
            calculationId = draft.calculationId,
            scheduledDateTimeMillis = draft.scheduledDateTimeMillis,
            usesGlobalFormula = draft.usesGlobalFormula,
            customFormula = draft.customFormula,
            globalFormula = global,
            columns = draft.columns,
            members = draft.members,
            roundingConfig = rounding
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        PayrollEngine.computeCalculation(
            calculationId = 0L,
            scheduledDateTimeMillis = System.currentTimeMillis(),
            usesGlobalFormula = true,
            customFormula = FormulaConfig(),
            globalFormula = FormulaConfig(),
            columns = emptyList(),
            members = emptyList()
        )
    )

    private val _scanState = MutableStateFlow(ScanScreenState())
    val scanState: StateFlow<ScanScreenState> = _scanState.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    private fun emitMessage(msg: String) {
        _snackbarEvents.tryEmit(msg)
    }

    // ---- Master Data Karyawan ----

    fun addMasterEmployee(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            emitMessage("Masukkan nama karyawan terlebih dahulu")
            return
        }
        viewModelScope.launch {
            repository.addMasterEmployee(trimmed)
            emitMessage("Nama \"$trimmed\" ditambahkan ke Data Karyawan")
        }
    }

    fun renameMasterEmployee(id: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.renameMasterEmployee(id, trimmed)
            emitMessage("Nama karyawan diperbarui menjadi \"$trimmed\"")
        }
    }

    fun deleteMasterEmployee(employee: MasterEmployeeEntity) {
        viewModelScope.launch {
            repository.deleteMasterEmployee(employee.id)
            emitMessage("\"${employee.name}\" dihapus dari Data Karyawan")
        }
    }

    fun clearMasterEmployeeGuide() {
        _needsMasterEmployeeGuide.value = false
    }

    // ---- Start New Calculation Flow ----

    fun beginCreateCalculation(
        onEmptyMasterRedirect: () -> Unit,
        onProceedToDateTime: () -> Unit
    ) {
        viewModelScope.launch {
            val masters = repository.getAllMasterEmployeesOnce()
            if (masters.isEmpty()) {
                _needsMasterEmployeeGuide.value = true
                emitMessage("Isi minimal satu nama di Data Karyawan sebelum membuat perhitungan")
                onEmptyMasterRedirect()
            } else {
                _needsMasterEmployeeGuide.value = false
                _dateTimeSetup.value = DateTimeSetupState.now()
                onProceedToDateTime()
            }
        }
    }

    fun updateDateTimeSetup(
        year: Int? = null,
        monthZeroBased: Int? = null,
        dayOfMonth: Int? = null,
        hourOfDay: Int? = null,
        minute: Int? = null
    ) {
        _dateTimeSetup.update { cur ->
            val newYear = (year ?: cur.year).coerceIn(2000, 2100)
            val newMonth = (monthZeroBased ?: cur.monthZeroBased).coerceIn(0, 11)
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, newYear)
                set(Calendar.MONTH, newMonth)
                set(Calendar.DAY_OF_MONTH, 1)
            }
            val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            cur.copy(
                year = newYear,
                monthZeroBased = newMonth,
                dayOfMonth = (dayOfMonth ?: cur.dayOfMonth).coerceIn(1, maxDay),
                hourOfDay = (hourOfDay ?: cur.hourOfDay).coerceIn(0, 23),
                minute = (minute ?: cur.minute).coerceIn(0, 59)
            )
        }
    }

    fun setDateTimeFromMillis(millis: Long) {
        _dateTimeSetup.value = DateTimeSetupState.fromMillis(millis)
    }

    fun confirmDateTimeAndLoadMembers(onReady: () -> Unit) {
        viewModelScope.launch {
            val masters = repository.getAllMasterEmployeesOnce()
            if (masters.isEmpty()) {
                _needsMasterEmployeeGuide.value = true
                emitMessage("Data Karyawan masih kosong. Tambahkan minimal satu nama.")
                return@launch
            }
            val selectedMillis = _dateTimeSetup.value.toMillis()
            val members = masters.map { master ->
                CalculationMemberEntry(
                    id = UUID.randomUUID().toString(),
                    name = master.name,
                    isCustomOneOff = false,
                    rows = listOf(CalculationRowItem())
                )
            }
            _activeDraft.value = ActiveCalculationDraft(
                isInitialized = true,
                calculationId = 0L,
                scheduledDateTimeMillis = selectedMillis,
                usesGlobalFormula = true,
                customFormula = globalFormula.value,
                columns = emptyList(),
                members = members
            )
            _autoSaveStatus.value = AutoSaveStatus.IDLE
            onReady()
        }
    }

    // ---- Open Saved Calculation from Riwayat / Total ----

    fun openSavedCalculation(calculationId: Long, onOpened: () -> Unit) {
        viewModelScope.launch {
            autoSaveJob?.cancel()
            val entity = repository.getCalculationEntity(calculationId) ?: return@launch
            val customFormula = FormulaConfig(
                enabled = entity.formulaEnabled,
                operation = runCatching { FormulaOperation.valueOf(entity.formulaOperation) }
                    .getOrDefault(FormulaOperation.DIVIDE),
                operand = entity.formulaOperand
            )
            val decodedMembers = PayrollJsonCodec.decodeMembers(entity.membersJson).map { m ->
                if (m.rows.isEmpty()) m.copy(rows = listOf(CalculationRowItem())) else m
            }
            _activeDraft.value = ActiveCalculationDraft(
                isInitialized = true,
                calculationId = entity.id,
                scheduledDateTimeMillis = entity.scheduledDateTimeMillis,
                usesGlobalFormula = entity.usesGlobalFormula,
                customFormula = customFormula,
                columns = PayrollJsonCodec.decodeColumns(entity.columnsJson),
                members = decodedMembers
            )
            _autoSaveStatus.value = AutoSaveStatus.SAVED
            onOpened()
        }
    }

    // ---- Active Calculation Members ----

    fun addOneOffMemberToDraft(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        _activeDraft.update { draft ->
            draft.copy(
                members = draft.members + CalculationMemberEntry(
                    id = UUID.randomUUID().toString(),
                    name = trimmed,
                    isCustomOneOff = true,
                    rows = listOf(CalculationRowItem())
                )
            )
        }
        scheduleAutoSave()
        emitMessage("Nama khusus \"$trimmed\" ditambahkan ke perhitungan ini")
    }

    fun renameDraftMember(memberId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        _activeDraft.update { draft ->
            draft.copy(
                members = draft.members.map { m ->
                    if (m.id == memberId) m.copy(name = trimmed) else m
                }
            )
        }
        scheduleAutoSave()
    }

    fun removeDraftMember(memberId: String) {
        _activeDraft.update { draft ->
            draft.copy(members = draft.members.filterNot { it.id == memberId })
        }
        scheduleAutoSave()
    }

    // ---- Per-Employee Jumlah x Harga Calculation Rows ----

    fun addRowToMember(memberId: String) {
        _activeDraft.update { draft ->
            draft.copy(
                members = draft.members.map { m ->
                    if (m.id == memberId) {
                        m.copy(rows = m.rows + CalculationRowItem())
                    } else m
                }
            )
        }
        scheduleAutoSave()
    }

    fun updateMemberRow(memberId: String, rowId: String, n1: String? = null, n2: String? = null) {
        _activeDraft.update { draft ->
            draft.copy(
                members = draft.members.map { m ->
                    if (m.id == memberId) {
                        m.copy(
                            rows = m.rows.map { r ->
                                if (r.id == rowId) {
                                    r.copy(
                                        n1Text = n1 ?: r.n1Text,
                                        n2Text = n2 ?: r.n2Text
                                    )
                                } else r
                            }
                        )
                    } else m
                }
            )
        }
        scheduleAutoSave()
    }

    fun removeRowFromMember(memberId: String, rowId: String) {
        _activeDraft.update { draft ->
            draft.copy(
                members = draft.members.map { m ->
                    if (m.id == memberId) {
                        val remaining = m.rows.filterNot { it.id == rowId }
                        m.copy(rows = remaining.ifEmpty { listOf(CalculationRowItem()) })
                    } else m
                }
            )
        }
        scheduleAutoSave()
    }

    // ---- Formula Control (Toggle, Per Daftar, Global, Gunakan Rumus Global) ----

    fun toggleActiveDraftFormula() {
        val currentGlobal = globalFormula.value
        _activeDraft.update { draft ->
            val currentEffective = if (draft.usesGlobalFormula) currentGlobal else draft.customFormula
            val toggled = currentEffective.copy(enabled = !currentEffective.enabled)
            draft.copy(
                usesGlobalFormula = false,
                customFormula = toggled
            )
        }
        scheduleAutoSave()
    }

    fun applyFormulaPerList(config: FormulaConfig) {
        if (!config.isValid()) {
            emitMessage("Nilai pembagi wajib lebih besar dari nol saat operasi bagi aktif")
            return
        }
        _activeDraft.update { draft ->
            draft.copy(
                usesGlobalFormula = false,
                customFormula = config
            )
        }
        scheduleAutoSave()
        emitMessage("Rumus khusus diterapkan untuk daftar ini")
    }

    fun applyFormulaGlobal(config: FormulaConfig, attachActiveDraftToGlobal: Boolean = true) {
        if (!config.isValid()) {
            emitMessage("Nilai pembagi wajib lebih besar dari nol saat operasi bagi aktif")
            return
        }
        viewModelScope.launch {
            repository.updateGlobalFormula(config)
            if (attachActiveDraftToGlobal && _activeDraft.value.isInitialized) {
                _activeDraft.update { it.copy(usesGlobalFormula = true, customFormula = config) }
                scheduleAutoSave()
            }
            emitMessage("Rumus Global diperbarui (${config.shortLabel()})")
        }
    }

    fun revertDraftToGlobalFormula() {
        val global = globalFormula.value
        _activeDraft.update { draft ->
            draft.copy(usesGlobalFormula = true, customFormula = global)
        }
        scheduleAutoSave()
        emitMessage("Daftar kembali menggunakan Rumus Global (${global.shortLabel()})")
    }

    // ---- Dynamic Adjustment Columns (Potongan & Penambahan) ----

    fun addAdjustmentColumn(name: String, operation: AdjustmentOperation) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val newCol = AdjustmentColumn(
            id = "col_${UUID.randomUUID().toString().take(8)}",
            name = trimmed,
            operation = operation
        )
        _activeDraft.update { draft ->
            draft.copy(columns = draft.columns + newCol)
        }
        scheduleAutoSave()
        emitMessage("Kolom \"${newCol.headerLabel()}\" ditambahkan")
    }

    fun updateAdjustmentColumn(columnId: String, name: String, operation: AdjustmentOperation) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        _activeDraft.update { draft ->
            draft.copy(
                columns = draft.columns.map { col ->
                    if (col.id == columnId) col.copy(name = trimmed, operation = operation) else col
                }
            )
        }
        scheduleAutoSave()
    }

    fun deleteAdjustmentColumn(columnId: String) {
        _activeDraft.update { draft ->
            draft.copy(
                columns = draft.columns.filterNot { it.id == columnId },
                members = draft.members.map { m ->
                    m.copy(adjustmentValues = m.adjustmentValues - columnId)
                }
            )
        }
        scheduleAutoSave()
        emitMessage("Kolom penyesuaian dihapus")
    }

    fun updateMemberAdjustmentValue(memberId: String, columnId: String, newValue: String) {
        _activeDraft.update { draft ->
            draft.copy(
                members = draft.members.map { m ->
                    if (m.id == memberId) {
                        val updatedMap = if (newValue.isEmpty()) {
                            m.adjustmentValues - columnId
                        } else {
                            m.adjustmentValues + (columnId to newValue)
                        }
                        m.copy(adjustmentValues = updatedMap)
                    } else m
                }
            )
        }
        scheduleAutoSave()
    }

    // ---- Live Auto-Save + Safe Fallback Save ----

    private fun scheduleAutoSave(debounceMs: Long = 220L) {
        val snapshot = _activeDraft.value
        if (!snapshot.isInitialized || snapshot.members.isEmpty()) return
        autoSaveJob?.cancel()
        _autoSaveStatus.value = AutoSaveStatus.SAVING
        autoSaveJob = viewModelScope.launch {
            if (debounceMs > 0L) {
                delay(debounceMs)
            }
            persistActiveDraftInternal()
        }
    }

    /**
     * Immediately persists any pending edits when navigating away or tapping fallback Save.
     */
    fun flushAutoSaveNow(showConfirmationToast: Boolean = false) {
        val snapshot = _activeDraft.value
        if (!snapshot.isInitialized || snapshot.members.isEmpty()) return
        autoSaveJob?.cancel()
        viewModelScope.launch {
            _autoSaveStatus.value = AutoSaveStatus.SAVING
            val saved = persistActiveDraftInternal()
            if (saved && showConfirmationToast) {
                emitMessage("Perubahan berhasil disimpan")
            }
        }
    }

    private suspend fun persistActiveDraftInternal(): Boolean {
        return saveMutex.withLock {
            val draft = _activeDraft.value
            if (!draft.isInitialized || draft.members.isEmpty()) {
                _autoSaveStatus.value = AutoSaveStatus.IDLE
                return@withLock false
            }
            val effective = if (draft.usesGlobalFormula) globalFormula.value else draft.customFormula
            if (!effective.isValid()) {
                _autoSaveStatus.value = AutoSaveStatus.IDLE
                return@withLock false
            }

            val savedId = repository.saveCalculation(
                calculationId = draft.calculationId,
                scheduledDateTimeMillis = draft.scheduledDateTimeMillis,
                usesGlobalFormula = draft.usesGlobalFormula,
                customFormula = draft.customFormula,
                columns = draft.columns,
                members = draft.members
            )
            if (draft.calculationId == 0L && savedId > 0L) {
                _activeDraft.update { current ->
                    if (current.isInitialized && current.calculationId == 0L) {
                        current.copy(calculationId = savedId)
                    } else current
                }
            }
            _autoSaveStatus.value = AutoSaveStatus.SAVED
            true
        }
    }

    // ---- Explicit Save Total / Simpan Perubahan (Safe Fallback) ----

    fun saveActiveCalculation(onSaved: () -> Unit) {
        val draft = _activeDraft.value
        if (!draft.isInitialized || draft.members.isEmpty()) {
            emitMessage("Daftar karyawan belum boleh kosong")
            return
        }
        val effective = if (draft.usesGlobalFormula) globalFormula.value else draft.customFormula
        if (!effective.isValid()) {
            emitMessage("Periksa rumus: nilai pembagi harus lebih besar dari 0")
            return
        }

        autoSaveJob?.cancel()
        viewModelScope.launch {
            val wasEditing = draft.isEditingExisting
            val scheduledMillis = draft.scheduledDateTimeMillis
            val ok = persistActiveDraftInternal()
            if (ok) {
                _selectedTotalMonth.value = PayrollMonth.fromMillis(scheduledMillis)
                _activeDraft.value = ActiveCalculationDraft()
                _autoSaveStatus.value = AutoSaveStatus.IDLE
                val label = IndonesianDateFormatter.formatFullDate(scheduledMillis)
                emitMessage(
                    if (wasEditing) "Perhitungan ($label) berhasil diperbarui"
                    else "Perhitungan ($label) disimpan ke Riwayat & Total"
                )
                onSaved()
            }
        }
    }

    fun deleteSavedCalculation(calculation: ComputedCalculationSummary) {
        viewModelScope.launch {
            if (_activeDraft.value.calculationId == calculation.calculationId) {
                autoSaveJob?.cancel()
                _activeDraft.value = ActiveCalculationDraft()
                _autoSaveStatus.value = AutoSaveStatus.IDLE
            }
            repository.deleteCalculation(calculation.calculationId)
            val label = IndonesianDateFormatter.formatFullDate(calculation.scheduledDateTimeMillis)
            emitMessage("Riwayat $label dihapus")
        }
    }

    fun updateRoundingConfig(config: RoundingConfig) {
        viewModelScope.launch {
            repository.updateRoundingConfig(config)
            emitMessage("Aturan pembulatan: ${config.labelIndonesian()}")
        }
    }

    // ---- Offline ML Kit OCR Scan Foto ----

    fun preselectScanMember(memberId: String?) {
        _scanState.update { it.copy(targetMemberId = memberId) }
    }

    fun processScannedImage(uri: Uri) {
        _scanState.update {
            it.copy(
                selectedImageUri = uri,
                isScanning = true,
                hasCompletedScan = false,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            val result = ocrEngine.scanImageUri(uri)
            result.onSuccess { parsed ->
                val rows = parsed.detectedRows.map { r ->
                    ScanRowCandidate(
                        localId = nextScanRowId++,
                        n1Text = r.n1Text,
                        n2Text = r.n2Text,
                        sourceHint = r.sourceLine
                    )
                }
                _scanState.update {
                    it.copy(
                        isScanning = false,
                        hasCompletedScan = true,
                        rawOcrText = parsed.rawText,
                        detectedName = parsed.detectedName ?: it.detectedName,
                        rows = rows,
                        errorMessage = null
                    )
                }
            }.onFailure { err ->
                _scanState.update {
                    it.copy(
                        isScanning = false,
                        hasCompletedScan = true,
                        errorMessage = "Gagal membaca teks foto: ${err.localizedMessage ?: "Error"}"
                    )
                }
            }
        }
    }

    fun updateScanTargetMember(memberId: String?) {
        _scanState.update { it.copy(targetMemberId = memberId, errorMessage = null) }
    }

    fun updateScanDetectedName(name: String) {
        _scanState.update { it.copy(detectedName = name, targetMemberId = null, errorMessage = null) }
    }

    fun addScanRow() {
        _scanState.update {
            it.copy(rows = it.rows + ScanRowCandidate(localId = nextScanRowId++))
        }
    }

    fun updateScanRow(localId: Long, n1: String? = null, n2: String? = null) {
        _scanState.update { state ->
            state.copy(
                rows = state.rows.map { r ->
                    if (r.localId == localId) r.copy(n1Text = n1 ?: r.n1Text, n2Text = n2 ?: r.n2Text)
                    else r
                }
            )
        }
    }

    fun swapScanRow(localId: Long) {
        _scanState.update { state ->
            state.copy(
                rows = state.rows.map { r ->
                    if (r.localId == localId) r.copy(n1Text = r.n2Text, n2Text = r.n1Text) else r
                }
            )
        }
    }

    fun removeScanRow(localId: Long) {
        _scanState.update { state ->
            state.copy(rows = state.rows.filterNot { it.localId == localId })
        }
    }

    fun clearScanState() {
        _scanState.value = ScanScreenState()
    }

    /**
     * Appends confirmed OCR (N1, N2) pairs directly into the target employee's calculation rows.
     * Returns the target memberId so the UI navigates straight to that employee's screen.
     */
    fun confirmScanIntoActiveCalculation(onApplied: (memberId: String) -> Unit) {
        val state = _scanState.value
        val validRows = mutableListOf<CalculationRowItem>()

        for ((index, row) in state.rows.withIndex()) {
            if (row.n1Text.isBlank() && row.n2Text.isBlank()) continue
            val n1 = PayrollCalculator.parseNumber(row.n1Text)
            val n2 = PayrollCalculator.parseNumber(row.n2Text)
            if (n1 == null || n2 == null) {
                _scanState.update {
                    it.copy(errorMessage = "Baris scan #${index + 1} harus memiliki angka Jumlah dan Harga yang valid.")
                }
                return
            }
            validRows.add(
                CalculationRowItem(
                    n1Text = PayrollCalculator.toPlainExactString(n1),
                    n2Text = PayrollCalculator.toPlainExactString(n2)
                )
            )
        }

        if (validRows.isEmpty()) {
            _scanState.update { it.copy(errorMessage = "Isi minimal satu pasangan Jumlah dan Harga hasil scan.") }
            return
        }

        val draft = _activeDraft.value
        val resultMemberId: String

        if (!draft.isInitialized) {
            val newId = UUID.randomUUID().toString()
            val personName = state.detectedName.trim().ifEmpty { "Karyawan Scan" }
            _activeDraft.value = ActiveCalculationDraft(
                isInitialized = true,
                calculationId = 0L,
                scheduledDateTimeMillis = System.currentTimeMillis(),
                usesGlobalFormula = true,
                customFormula = globalFormula.value,
                columns = emptyList(),
                members = listOf(
                    CalculationMemberEntry(
                        id = newId,
                        name = personName,
                        isCustomOneOff = true,
                        rows = validRows
                    )
                )
            )
            resultMemberId = newId
            emitMessage("${validRows.size} baris hasil scan diterapkan untuk $personName")
        } else {
            val targetId = state.targetMemberId
            if (targetId != null && draft.members.any { it.id == targetId }) {
                _activeDraft.update { d ->
                    d.copy(
                        members = d.members.map { m ->
                            if (m.id == targetId) {
                                val nonEmptyExisting = m.rows.filterNot {
                                    it.n1Text.isBlank() && it.n2Text.isBlank()
                                }
                                m.copy(rows = nonEmptyExisting + validRows)
                            } else m
                        }
                    )
                }
                resultMemberId = targetId
                val targetName = draft.members.first { it.id == targetId }.name
                emitMessage("${validRows.size} baris hasil scan ditambahkan ke $targetName")
            } else {
                val newId = UUID.randomUUID().toString()
                val personName = state.detectedName.trim().ifEmpty { "Karyawan Scan" }
                _activeDraft.update { d ->
                    d.copy(
                        members = d.members + CalculationMemberEntry(
                            id = newId,
                            name = personName,
                            isCustomOneOff = true,
                            rows = validRows
                        )
                    )
                }
                resultMemberId = newId
                emitMessage("${validRows.size} baris hasil scan ditambahkan untuk $personName")
            }
        }

        clearScanState()
        scheduleAutoSave(debounceMs = 0L)
        onApplied(resultMemberId)
    }

    // ---- Backup & Restore ----

    fun exportBackupToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val json = repository.exportFullBackupJson()
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                        stream.flush()
                    } ?: error("Tidak dapat membuka file tujuan.")
                }
            }
            result.onSuccess {
                emitMessage("Backup lokal berhasil disimpan")
            }.onFailure { err ->
                emitMessage("Gagal backup: ${err.localizedMessage ?: "Error"}")
            }
        }
    }

    fun restoreBackupFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val readResult = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    } ?: error("Tidak dapat membaca file backup.")
                }
            }
            readResult.onSuccess { rawJson ->
                val restoreResult = repository.restoreFromBackupJson(rawJson)
                restoreResult.onSuccess { (masterCount, calcCount) ->
                    _activeDraft.value = ActiveCalculationDraft()
                    emitMessage("Restore berhasil: $masterCount nama master & $calcCount riwayat dipulihkan")
                }.onFailure { err ->
                    emitMessage("File backup tidak valid: ${err.localizedMessage ?: "Format salah"}")
                }
            }.onFailure { err ->
                emitMessage("Gagal membaca file: ${err.localizedMessage ?: "Error"}")
            }
        }
    }

    companion object {
        fun provideFactory(
            repository: PayrollRepository,
            ocrEngine: MlKitOcrEngine
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PayrollViewModel(repository, ocrEngine) as T
            }
        }
    }
}
