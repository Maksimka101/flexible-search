package com.zemlianikin.flexiblesearch

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import com.zemlianikin.flexiblesearch.core.ActionIcon
import com.zemlianikin.flexiblesearch.core.Plugin
import com.zemlianikin.flexiblesearch.core.PluginScope
import com.zemlianikin.flexiblesearch.core.materialIcon

/**
 * Настройки хоста тем же API, что у плагинов: кнопка в строке поиска и
 * экран со списком плагинов. Сам в списке не виден и не выключается — иначе
 * плагины не включить обратно.
 */
class SettingsPlugin(
    private val plugins: List<Plugin>,
    private val settings: PluginSettings,
) : Plugin {
    override val id = "settings"
    override val name = "Settings"
    override val description = "Search bar button and the plugin list"

    override fun install(scope: PluginScope) {
        val screen =
            scope.screen("Plugins") {
                val disabled by settings.disabled.collectAsState()
                LazyColumn {
                    items(plugins, key = { it.id }) { plugin ->
                        val enabled = plugin.id !in disabled
                        ListItem(
                            headlineContent = { Text(plugin.name) },
                            supportingContent = {
                                Text(plugin.description)
                            },
                            trailingContent = {
                                // Нажатие обрабатывает вся строка.
                                Switch(
                                    checked = enabled,
                                    onCheckedChange = null,
                                )
                            },
                            modifier =
                                Modifier.toggleable(
                                    value = enabled,
                                    role = Role.Switch,
                                ) {
                                    settings.setEnabled(plugin.id, it)
                                },
                        )
                    }
                }
            }
        scope.searchBarAction("Plugins", ActionIcon.Vector(SettingsIcon)) {
            open(screen)
        }
    }
}

// Material Icons «settings».
private val SettingsIcon: ImageVector =
    materialIcon(
        "Settings",
        "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94" +
            "c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58" +
            "c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32" +
            "c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96" +
            "c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81" +
            "c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84" +
            "c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35" +
            "C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33" +
            "c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87" +
            "C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58" +
            "C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58" +
            "c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32" +
            "c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96" +
            "c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54" +
            "c0.05,0.24 0.24,0.41 0.48,0.41h3.84" +
            "c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54" +
            "c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96" +
            "c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32" +
            "c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94z" +
            "M12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6" +
            " 3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z",
    )
