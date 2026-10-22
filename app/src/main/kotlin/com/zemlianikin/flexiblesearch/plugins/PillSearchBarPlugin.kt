package com.zemlianikin.flexiblesearch.plugins

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import com.zemlianikin.flexiblesearch.core.Plugin
import com.zemlianikin.flexiblesearch.core.PluginScope
import com.zemlianikin.flexiblesearch.core.SearchBar

/**
 * Строка-«таблетка»: пример плагина, который меняет только стиль
 * компонента.
 */
class PillSearchBarPlugin : Plugin {
    override val id = "pill-search-bar"

    override fun install(scope: PluginScope) {
        scope.style(SearchBar) {
            it.copy(shape = CircleShape, height = 56.dp)
        }
    }
}
