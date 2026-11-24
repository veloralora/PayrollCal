package com.example.data.backup

import com.example.domain.AdjustmentColumn
import com.example.domain.CalculationMemberEntry
import com.example.domain.FormulaConfig
import com.example.domain.RoundingConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class MasterEmployeeBackupDto(
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class SavedCalculationBackupDto(
    val scheduledDateTimeMillis: Long,
    val usesGlobalFormula: Boolean = true,
    val formulaEnabled: Boolean = true,
    val formulaOperation: String = "DIVIDE",
    val formulaOperand: String = "12",
    val members: List<CalculationMemberEntry> = emptyList(),
    val columns: List<AdjustmentColumn> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class FullBackupPayload(
    val schemaVersion: Int = 2,
    val appName: String = "Hitung Gajian Pro",
    val exportedAt: Long = System.currentTimeMillis(),
    val globalFormula: FormulaConfig = FormulaConfig(),
    val roundingConfig: RoundingConfig = RoundingConfig(),
    val masterEmployees: List<MasterEmployeeBackupDto> = emptyList(),
    val calculations: List<SavedCalculationBackupDto> = emptyList()
)

object PayrollJsonCodec {
    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val membersListSerializer = ListSerializer(CalculationMemberEntry.serializer())
    private val columnsListSerializer = ListSerializer(AdjustmentColumn.serializer())

    fun encodeMembers(members: List<CalculationMemberEntry>): String =
        json.encodeToString(membersListSerializer, members)

    fun decodeMembers(raw: String): List<CalculationMemberEntry> =
        runCatching { json.decodeFromString(membersListSerializer, raw) }.getOrDefault(emptyList())

    fun encodeColumns(columns: List<AdjustmentColumn>): String =
        json.encodeToString(columnsListSerializer, columns)

    fun decodeColumns(raw: String): List<AdjustmentColumn> =
        runCatching { json.decodeFromString(columnsListSerializer, raw) }.getOrDefault(emptyList())

    fun encodeFullBackup(payload: FullBackupPayload): String =
        json.encodeToString(FullBackupPayload.serializer(), payload)

    fun decodeFullBackup(raw: String): Result<FullBackupPayload> = runCatching {
        val parsed = json.decodeFromString(FullBackupPayload.serializer(), raw)
        require(parsed.schemaVersion >= 1) { "Format file backup tidak valid." }
        parsed
    }
}
