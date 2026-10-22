package com.zemlianikin.flexiblesearch.poc.components.plugins

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import com.zemlianikin.flexiblesearch.poc.components.core.Plugin
import com.zemlianikin.flexiblesearch.poc.components.core.PluginScope
import com.zemlianikin.flexiblesearch.poc.components.core.SearchBar

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
