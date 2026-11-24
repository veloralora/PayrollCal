package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.FormulaOperation

/**
 * Reusable master employee names (Menu Data Karyawan).
 * Modifying or deleting master names never changes already-saved calculations.
 */
@Entity(tableName = "master_employees")
data class MasterEmployeeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Saved payroll calculation session (shown in Riwayat and summed in Tab Total).
 * Stores its own snapshot of member names (master + one-off '+' names), base amounts,
 * dynamic adjustment columns, date/time, and formula settings/scope.
 */
@Entity(tableName = "payroll_calculations")
data class PayrollCalculationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduledDateTimeMillis: Long,
    val usesGlobalFormula: Boolean = true,
    val formulaEnabled: Boolean = true,
    val formulaOperation: String = FormulaOperation.DIVIDE.name,
    val formulaOperand: String = "12",
    val membersJson: String,
    val columnsJson: String,
    val memberCount: Int,
    val grandTotal: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
