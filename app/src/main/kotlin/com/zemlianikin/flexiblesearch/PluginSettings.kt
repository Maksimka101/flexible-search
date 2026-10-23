package com.zemlianikin.flexiblesearch

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Какие плагины выключены. Хранит хост: выключенный плагин не
 * устанавливается и сам ничего сделать не может.
 */
class PluginSettings(private val prefs: SharedPreferences) {
    private val state =
        MutableStateFlow(
            prefs.getStringSet(DISABLED, null).orEmpty().toSet()
        )

    val disabled: StateFlow<Set<String>> = state

    fun setEnabled(id: String, enabled: Boolean) {
        val disabled = if (enabled) state.value - id else state.value + id
        prefs.edit { putStringSet(DISABLED, disabled) }
        state.value = disabled
    }

    private companion object {
        const val DISABLED = "disabled"
    }
}
