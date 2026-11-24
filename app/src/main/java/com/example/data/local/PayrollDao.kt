package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PayrollDao {

    // ---- Master Data Karyawan ----

    @Query("SELECT * FROM master_employees ORDER BY name COLLATE NOCASE ASC, id ASC")
    fun observeMasterEmployees(): Flow<List<MasterEmployeeEntity>>

    @Query("SELECT * FROM master_employees ORDER BY name COLLATE NOCASE ASC, id ASC")
    suspend fun getAllMasterEmployeesOnce(): List<MasterEmployeeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMasterEmployee(employee: MasterEmployeeEntity): Long

    @Update
    suspend fun updateMasterEmployee(employee: MasterEmployeeEntity)

    @Query("DELETE FROM master_employees WHERE id = :id")
    suspend fun deleteMasterEmployeeById(id: Long)

    @Query("DELETE FROM master_employees")
    suspend fun deleteAllMasterEmployees()

    // ---- Saved Payroll Calculations (Riwayat & Total) ----

    @Query("SELECT * FROM payroll_calculations ORDER BY scheduledDateTimeMillis DESC, id DESC")
    fun observeCalculations(): Flow<List<PayrollCalculationEntity>>

    @Query("SELECT * FROM payroll_calculations ORDER BY scheduledDateTimeMillis DESC, id DESC")
    suspend fun getAllCalculationsOnce(): List<PayrollCalculationEntity>

    @Query("SELECT * FROM payroll_calculations WHERE id = :id")
    suspend fun getCalculationById(id: Long): PayrollCalculationEntity?

    @Query("SELECT * FROM payroll_calculations WHERE usesGlobalFormula = 1")
    suspend fun getCalculationsFollowingGlobalFormula(): List<PayrollCalculationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalculation(calculation: PayrollCalculationEntity): Long

    @Update
    suspend fun updateCalculation(calculation: PayrollCalculationEntity)

    @Query("DELETE FROM payroll_calculations WHERE id = :id")
    suspend fun deleteCalculationById(id: Long)

    @Query("DELETE FROM payroll_calculations WHERE scheduledDateTimeMillis < :minStartMillis")
    suspend fun deleteCalculationsBefore(minStartMillis: Long): Int

    @Query("DELETE FROM payroll_calculations")
    suspend fun deleteAllCalculations()

    @Transaction
    suspend fun upsertCalculation(entity: PayrollCalculationEntity): Long {
        return if (entity.id == 0L) {
            insertCalculation(entity)
        } else {
            updateCalculation(entity.copy(updatedAt = System.currentTimeMillis()))
            entity.id
        }
    }

    @Transaction
    suspend fun updateCalculationsBatch(entities: List<PayrollCalculationEntity>) {
        entities.forEach { updateCalculation(it) }
    }

    @Transaction
    suspend fun restoreFullBackup(
        masters: List<MasterEmployeeEntity>,
        calculations: List<PayrollCalculationEntity>
    ) {
        deleteAllMasterEmployees()
        masters.forEach { insertMasterEmployee(it.copy(id = 0)) }
        deleteAllCalculations()
        calculations.forEach { insertCalculation(it.copy(id = 0)) }
    }
}
