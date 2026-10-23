package com.zemlianikin.flexiblesearch.plugins.mocks

import com.zemlianikin.flexiblesearch.core.Plugin
import com.zemlianikin.flexiblesearch.core.PluginScope

/**
 * Моки для UI, пока нет настоящих плагинов: веб-подсказки и файлы в
 * результатах, погода на пустой строке. Удалить целиком, когда появятся
 * настоящие.
 */
class MocksPlugin : Plugin {
    override val id = "mocks"
    override val name = "Mocks"
    override val description = "Fake web suggestions, files and weather"

    override fun install(scope: PluginScope) {
        WebSuggestionsMock.install(scope)
        FilesMock.install(scope)
        WeatherMock.install(scope)
    }
}
