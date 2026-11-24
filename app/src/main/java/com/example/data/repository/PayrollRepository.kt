package com.example.data.repository

import com.example.data.backup.FullBackupPayload
import com.example.data.backup.MasterEmployeeBackupDto
import com.example.data.backup.PayrollJsonCodec
import com.example.data.backup.SavedCalculationBackupDto
import com.example.data.local.MasterEmployeeEntity
import com.example.data.local.PayrollCalculationEntity
import com.example.data.local.PayrollDao
import com.example.data.preferences.SettingsStore
import com.example.domain.AdjustmentColumn
import com.example.domain.CalculationMemberEntry
import com.example.domain.ComputedCalculationSummary
import com.example.domain.FormulaConfig
import com.example.domain.FormulaOperation
import com.example.domain.PayrollCalculator
import com.example.domain.PayrollEngine
import com.example.domain.PayrollMonth
import com.example.domain.RoundingConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.math.BigDecimal

class PayrollRepository(
    private val dao: PayrollDao,
    private val settingsStore: SettingsStore
) {
    val masterEmployees: Flow<List<MasterEmployeeEntity>> = dao.observeMasterEmployees()

    val globalFormula: Flow<FormulaConfig> = settingsStore.globalFormulaFlow

    val roundingConfig: Flow<RoundingConfig> = settingsStore.roundingConfigFlow

    /**
     * Live computed list of all saved payroll calculations (for Riwayat and Tab Total).
     * Automatically reflects changes to saved calculations, global formula, or rounding.
     */
    val savedCalculations: Flow<List<ComputedCalculationSummary>> = combine(
        dao.observeCalculations(),
        settingsStore.globalFormulaFlow,
        settingsStore.roundingConfigFlow
    ) { entities, globalFormula, rounding ->
        entities.map { entity ->
            entity.toComputedSummary(globalFormula, rounding)
        }
    }

    val totalSavedPayroll: Flow<BigDecimal> = combine(
        savedCalculations
    ) { (list) ->
        PayrollEngine.sumSavedCalculations(list)
    }

    // ---- Master Data Karyawan ----

    suspend fun getAllMasterEmployeesOnce(): List<MasterEmployeeEntity> =
        dao.getAllMasterEmployeesOnce()

    suspend fun addMasterEmployee(name: String): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Nama karyawan tidak boleh kosong." }
        val now = System.currentTimeMillis()
        return dao.insertMasterEmployee(
            MasterEmployeeEntity(name = trimmed, createdAt = now, updatedAt = now)
        )
    }

    suspend fun renameMasterEmployee(id: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        val existing = dao.getAllMasterEmployeesOnce().find { it.id == id } ?: return
        dao.updateMasterEmployee(
            existing.copy(name = trimmed, updatedAt = System.currentTimeMillis())
        )
    }

    suspend fun deleteMasterEmployee(id: Long) {
        dao.deleteMasterEmployeeById(id)
    }

    // ---- Saved Calculations (Create / Update / Load / Delete) ----

    suspend fun getCalculationEntity(id: Long): PayrollCalculationEntity? =
        dao.getCalculationById(id)

    suspend fun saveCalculation(
        calculationId: Long,
        scheduledDateTimeMillis: Long,
        usesGlobalFormula: Boolean,
        customFormula: FormulaConfig,
        columns: List<AdjustmentColumn>,
        members: List<CalculationMemberEntry>
    ): Long {
        val global = settingsStore.globalFormulaFlow.first()
        val rounding = settingsStore.roundingConfigFlow.first()
        val computed = PayrollEngine.computeCalculation(
            calculationId = calculationId,
            scheduledDateTimeMillis = scheduledDateTimeMillis,
            usesGlobalFormula = usesGlobalFormula,
            customFormula = customFormula,
            globalFormula = global,
            columns = columns,
            members = members,
            roundingConfig = rounding
        )
        val existing = if (calculationId > 0L) dao.getCalculationById(calculationId) else null
        val now = System.currentTimeMillis()
        val entity = PayrollCalculationEntity(
            id = calculationId,
            scheduledDateTimeMillis = scheduledDateTimeMillis,
            usesGlobalFormula = usesGlobalFormula,
            formulaEnabled = customFormula.enabled,
            formulaOperation = customFormula.operation.name,
            formulaOperand = customFormula.operand,
            membersJson = PayrollJsonCodec.encodeMembers(members),
            columnsJson = PayrollJsonCodec.encodeColumns(columns),
            memberCount = members.size,
            grandTotal = PayrollCalculator.toPlainExactString(computed.grandTotal),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        val savedId = dao.upsertCalculation(entity)
        enforceTwelveMonthRetention()
        return savedId
    }

    /**
     * Enforces maximum 12-month rolling storage:
     * When advancing to a new month beyond 12 stored months, calculations from the oldest
     * expired month(s) are automatically deleted and replaced by the new month's window.
     */
    suspend fun enforceTwelveMonthRetention(referenceMonth: PayrollMonth = PayrollMonth.now()): Int {
        val all = dao.getAllCalculationsOnce()
        if (all.isEmpty()) return 0
        val cutoffMillis = PayrollMonth.retentionCutoffMillis(
            referenceMonth = referenceMonth,
            storedTimestamps = all.map { it.scheduledDateTimeMillis }
        )
        return dao.deleteCalculationsBefore(cutoffMillis)
    }

    suspend fun deleteCalculation(id: Long) {
        dao.deleteCalculationById(id)
    }

    // ---- Global Formula & Rounding Updates ----

    /**
     * Updates Global Formula in DataStore AND recalculates & persists totals for all
     * saved calculations that still follow the Global Formula (usesGlobalFormula == true).
     * Saved calculations with their own per-list formula remain untouched!
     */
    suspend fun updateGlobalFormula(newGlobalFormula: FormulaConfig) {
        settingsStore.updateGlobalFormula(newGlobalFormula)
        val rounding = settingsStore.roundingConfigFlow.first()
        val globalFollowers = dao.getCalculationsFollowingGlobalFormula()
        if (globalFollowers.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val updated = globalFollowers.map { entity ->
                val computed = entity.toComputedSummary(newGlobalFormula, rounding)
                entity.copy(
                    grandTotal = PayrollCalculator.toPlainExactString(computed.grandTotal),
                    updatedAt = now
                )
            }
            dao.updateCalculationsBatch(updated)
        }
    }

    suspend fun updateRoundingConfig(newRounding: RoundingConfig) {
        settingsStore.updateRoundingConfig(newRounding)
        val globalFormula = settingsStore.globalFormulaFlow.first()
        val allSaved = dao.getAllCalculationsOnce()
        if (allSaved.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val updated = allSaved.map { entity ->
                val computed = entity.toComputedSummary(globalFormula, newRounding)
                entity.copy(
                    grandTotal = PayrollCalculator.toPlainExactString(computed.grandTotal),
                    updatedAt = now
                )
            }
            dao.updateCalculationsBatch(updated)
        }
    }

    // ---- Backup & Restore ----

    suspend fun exportFullBackupJson(): String {
        val masters = dao.getAllMasterEmployeesOnce().map {
            MasterEmployeeBackupDto(name = it.name, createdAt = it.createdAt)
        }
        val calcs = dao.getAllCalculationsOnce().map { c ->
            SavedCalculationBackupDto(
                scheduledDateTimeMillis = c.scheduledDateTimeMillis,
                usesGlobalFormula = c.usesGlobalFormula,
                formulaEnabled = c.formulaEnabled,
                formulaOperation = c.formulaOperation,
                formulaOperand = c.formulaOperand,
                members = PayrollJsonCodec.decodeMembers(c.membersJson),
                columns = PayrollJsonCodec.decodeColumns(c.columnsJson),
                createdAt = c.createdAt
            )
        }
        val payload = FullBackupPayload(
            globalFormula = settingsStore.globalFormulaFlow.first(),
            roundingConfig = settingsStore.roundingConfigFlow.first(),
            masterEmployees = masters,
            calculations = calcs
        )
        return PayrollJsonCodec.encodeFullBackup(payload)
    }

    suspend fun restoreFromBackupJson(rawJson: String): Result<Pair<Int, Int>> {
        val decoded = PayrollJsonCodec.decodeFullBackup(rawJson)
        return decoded.map { payload ->
            val now = System.currentTimeMillis()
            val masterEntities = payload.masterEmployees.map { m ->
                MasterEmployeeEntity(
                    name = m.name,
                    createdAt = m.createdAt,
                    updatedAt = now
                )
            }
            val calcEntities = payload.calculations.map { c ->
                val customFormula = FormulaConfig(
                    enabled = c.formulaEnabled,
                    operation = runCatching { FormulaOperation.valueOf(c.formulaOperation) }
                        .getOrDefault(FormulaOperation.DIVIDE),
                    operand = c.formulaOperand
                )
                val computed = PayrollEngine.computeCalculation(
                    calculationId = 0L,
                    scheduledDateTimeMillis = c.scheduledDateTimeMillis,
                    usesGlobalFormula = c.usesGlobalFormula,
                    customFormula = customFormula,
                    globalFormula = payload.globalFormula,
                    columns = c.columns,
                    members = c.members,
                    roundingConfig = payload.roundingConfig
                )
                PayrollCalculationEntity(
                    scheduledDateTimeMillis = c.scheduledDateTimeMillis,
                    usesGlobalFormula = c.usesGlobalFormula,
                    formulaEnabled = c.formulaEnabled,
                    formulaOperation = c.formulaOperation,
                    formulaOperand = c.formulaOperand,
                    membersJson = PayrollJsonCodec.encodeMembers(c.members),
                    columnsJson = PayrollJsonCodec.encodeColumns(c.columns),
                    memberCount = c.members.size,
                    grandTotal = PayrollCalculator.toPlainExactString(computed.grandTotal),
                    createdAt = c.createdAt,
                    updatedAt = now
                )
            }
            dao.restoreFullBackup(masterEntities, calcEntities)
            settingsStore.updateGlobalFormula(payload.globalFormula)
            settingsStore.updateRoundingConfig(payload.roundingConfig)
            enforceTwelveMonthRetention()
            val keptCalcs = dao.getAllCalculationsOnce().size
            masterEntities.size to keptCalcs
        }
    }

    fun PayrollCalculationEntity.toComputedSummary(
        globalFormula: FormulaConfig,
        roundingConfig: RoundingConfig
    ): ComputedCalculationSummary {
        val customFormula = FormulaConfig(
            enabled = formulaEnabled,
            operation = runCatching { FormulaOperation.valueOf(formulaOperation) }
                .getOrDefault(FormulaOperation.DIVIDE),
            operand = formulaOperand
        )
        val members = PayrollJsonCodec.decodeMembers(membersJson)
        val columns = PayrollJsonCodec.decodeColumns(columnsJson)
        return PayrollEngine.computeCalculation(
            calculationId = id,
            scheduledDateTimeMillis = scheduledDateTimeMillis,
            usesGlobalFormula = usesGlobalFormula,
            customFormula = customFormula,
            globalFormula = globalFormula,
            columns = columns,
            members = members,
            roundingConfig = roundingConfig
        )
    }
}
