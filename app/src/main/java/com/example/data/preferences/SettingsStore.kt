package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.FormulaConfig
import com.example.domain.FormulaOperation
import com.example.domain.RoundingConfig
import com.example.domain.RoundingModeType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "hitung_gajian_settings"
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val ROUNDING_MODE = stringPreferencesKey("rounding_mode")
        val ROUNDING_STEP = stringPreferencesKey("rounding_step")
        val GLOBAL_FORMULA_ENABLED = booleanPreferencesKey("global_formula_enabled")
        val GLOBAL_FORMULA_OP = stringPreferencesKey("global_formula_op")
        val GLOBAL_FORMULA_OPERAND = stringPreferencesKey("global_formula_operand")
    }

    val roundingConfigFlow: Flow<RoundingConfig> = context.settingsDataStore.data.map { prefs ->
        val modeName = prefs[Keys.ROUNDING_MODE] ?: RoundingModeType.FLOOR_TO_STEP.name
        val mode = runCatching { RoundingModeType.valueOf(modeName) }
            .getOrDefault(RoundingModeType.FLOOR_TO_STEP)
        val step = prefs[Keys.ROUNDING_STEP] ?: "100"
        RoundingConfig(mode = mode, step = step)
    }

    val globalFormulaFlow: Flow<FormulaConfig> = context.settingsDataStore.data.map { prefs ->
        val enabled = prefs[Keys.GLOBAL_FORMULA_ENABLED] ?: true
        val opName = prefs[Keys.GLOBAL_FORMULA_OP] ?: FormulaOperation.DIVIDE.name
        val op = runCatching { FormulaOperation.valueOf(opName) }
            .getOrDefault(FormulaOperation.DIVIDE)
        val operand = prefs[Keys.GLOBAL_FORMULA_OPERAND] ?: "12"
        FormulaConfig(enabled = enabled, operation = op, operand = operand)
    }

    suspend fun updateRoundingConfig(config: RoundingConfig) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.ROUNDING_MODE] = config.mode.name
            prefs[Keys.ROUNDING_STEP] = config.step
        }
    }

    suspend fun updateGlobalFormula(formula: FormulaConfig) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.GLOBAL_FORMULA_ENABLED] = formula.enabled
            prefs[Keys.GLOBAL_FORMULA_OP] = formula.operation.name
            prefs[Keys.GLOBAL_FORMULA_OPERAND] = formula.operand
        }
    }
}
