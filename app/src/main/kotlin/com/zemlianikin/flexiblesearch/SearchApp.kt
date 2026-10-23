package com.zemlianikin.flexiblesearch

import android.app.Application
import com.zemlianikin.flexiblesearch.core.PluginHost
import com.zemlianikin.flexiblesearch.plugins.AppsPlugin
import com.zemlianikin.flexiblesearch.plugins.SearchInAppsPlugin
import com.zemlianikin.flexiblesearch.plugins.TogglesPlugin
import com.zemlianikin.flexiblesearch.plugins.converter.ConverterPlugin
import com.zemlianikin.flexiblesearch.plugins.mocks.MocksPlugin

class SearchApp : Application() {
    /** Все встроенные, и выключенные тоже. */
    val plugins =
        listOf(
            // Порядок плагинов — это порядок кнопок в ряду быстрых
            // действий, порядок групп и виджетов снизу вверх: первая —
            // у клавиатуры.
            SearchInAppsPlugin(),
            AppsPlugin(),
            ConverterPlugin(),
            TogglesPlugin(),
            // Веб и файлы после калькулятора, погода после кнопок.
            MocksPlugin(),
        )

    val pluginSettings by lazy {
        PluginSettings(getSharedPreferences("plugins", MODE_PRIVATE))
    }

    private val settingsPlugin by lazy {
        SettingsPlugin(plugins, pluginSettings)
    }

    private var built: Pair<Set<String>, PluginHost>? = null

    /**
     * Плагины живут дольше окна: список приложений и кэш иконок не грузятся
     * заново при каждом открытии. Хост собирается заново, только когда
     * сменились выключенные.
     */
    fun host(disabled: Set<String>): PluginHost {
        built
            ?.takeIf { it.first == disabled }
            ?.let {
                return it.second
            }
        // Выгрузки пока нет: подписки старых плагинов остаются до конца
        // процесса.
        val enabled = plugins.filter { it.id !in disabled }
        return PluginHost(this, listOf(settingsPlugin) + enabled).also {
            built = disabled to it
        }
    }
}
