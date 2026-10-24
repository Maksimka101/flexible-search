package com.zemlianikin.flexiblesearch.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Все плагины приложения, и выключенные тоже. Какие выключены, хранит core:
 * выключенный плагин не устанавливается и сам ничего сделать не может.
 * Выключаются на экране настроек ([SearchScreen]).
 */
class PluginManager(context: Context, val plugins: List<Plugin>) {
    private val context = context.applicationContext
    private val prefs =
        context.getSharedPreferences("plugins", Context.MODE_PRIVATE)
    private val state =
        MutableStateFlow(
            prefs.getStringSet(DISABLED, null).orEmpty().toSet()
        )

    val disabled: StateFlow<Set<String>> = state

    fun setEnabled(id: String, enabled: Boolean) {
        val disabled = if (enabled) state.value - id else state.value + id
        prefs.edit().putStringSet(DISABLED, disabled).apply()
        state.value = disabled
    }

    private var built: Pair<Set<String>, PluginHost>? = null

    init {
        SearchWidget.watch(context, this)
    }

    /**
     * Плагины живут дольше окна: список приложений и кэш иконок не грузятся
     * заново при каждом открытии. Хост собирается заново, только когда
     * сменились выключенные.
     */
    fun host(disabled: Set<String> = state.value): PluginHost {
        built
            ?.takeIf { it.first == disabled }
            ?.let {
                return it.second
            }
        // Выгрузки пока нет: подписки старых плагинов остаются до конца
        // процесса.
        val enabled = plugins.filter { it.id !in disabled }
        return PerfTrace.section("plugin host") {
                PluginHost(context, enabled)
            }
            .also { built = disabled to it }
    }

    private companion object {
        const val DISABLED = "disabled"
    }
}
