package com.zemlianikin.flexiblesearch

import android.app.Application
import com.zemlianikin.flexiblesearch.core.PerfTrace
import com.zemlianikin.flexiblesearch.core.PluginManager
import com.zemlianikin.flexiblesearch.plugins.AppsPlugin
import com.zemlianikin.flexiblesearch.plugins.MaterialYouPlugin
import com.zemlianikin.flexiblesearch.plugins.SearchInAppsPlugin
import com.zemlianikin.flexiblesearch.plugins.TogglesPlugin
import com.zemlianikin.flexiblesearch.plugins.converter.ConverterPlugin
import com.zemlianikin.flexiblesearch.plugins.mocks.MocksPlugin

class SearchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PerfTrace.mark("app created")
    }

    /** Все встроенные, и выключенные тоже. */
    val plugins by lazy {
        PluginManager(
            this,
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
                MaterialYouPlugin(),
            ),
        )
    }

    val wallpaper by lazy { BlurredWallpaper(this) }
}
