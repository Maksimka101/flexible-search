package com.zemlianikin.flexiblesearch.core.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zemlianikin.flexiblesearch.core.Plugin
import com.zemlianikin.flexiblesearch.core.PluginManager
import com.zemlianikin.flexiblesearch.core.buttons.ActionIcon
import com.zemlianikin.flexiblesearch.core.buttons.QuickAction
import com.zemlianikin.flexiblesearch.core.buttons.materialIcon
import com.zemlianikin.flexiblesearch.core.component.Component

/** Id экрана настроек: `SearchLinks.screen(SETTINGS_SCREEN)`. */
const val SETTINGS_SCREEN = "settings"

/**
 * Настройки: строки хоста ([header]), потом все плагины — выключить или
 * открыть их настройки ([PluginScreens.settings]). Своё у плагина только у
 * включённого: выключенный не установлен.
 */
internal fun settingsScreen(
    plugins: PluginManager,
    header: State<@Composable () -> Unit>,
): Screen =
    Screen(SETTINGS_SCREEN, "Settings") {
        val scope = this
        val disabled by plugins.disabled.collectAsState()
        val openSettings by plugins.host(disabled).settings.collectAsState()
        LazyColumn {
            item { header.value() }
            items(plugins.plugins, key = { it.id }) { plugin ->
                val enabled = plugin.id !in disabled
                PluginRow(
                    PluginRowProps(
                        plugin,
                        enabled,
                        onEnabledChange = {
                            plugins.setEnabled(plugin.id, it)
                        },
                        onOpenSettings =
                            openSettings[plugin.id]
                                ?.takeIf { enabled }
                                ?.let { { it(scope) } },
                    )
                )
            }
        }
    }

/** Шестерёнка в строке поиска, после кнопок плагинов. */
internal fun settingsAction(screen: Screen) =
    QuickAction(
        SETTINGS_SCREEN,
        "Settings",
        ActionIcon.Vector(SettingsIcon),
        isSelected = { false },
    ) {
        open(screen)
    }

// --- Строка плагина ---

class PluginRowProps(
    val plugin: Plugin,
    val enabled: Boolean,
    val onEnabledChange: (Boolean) -> Unit,
    /** null — у плагина нет настроек или он выключен. */
    val onOpenSettings: (() -> Unit)?,
)

data class PluginRowStyle(val colors: ListItemColors)

/**
 * Без настроек вся строка — переключатель. С ними нажатие открывает
 * настройки, а выключает только сам переключатель за чертой, как Wi-Fi в
 * настройках Android.
 */
val PluginRow =
    Component<PluginRowProps, PluginRowStyle>(
        name = "PluginRow",
        defaultStyle = { PluginRowStyle(ListItemDefaults.colors()) },
    ) { props, style ->
        val openSettings = props.onOpenSettings
        ListItem(
            headlineContent = { Text(props.plugin.name) },
            supportingContent = { Text(props.plugin.description) },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (openSettings != null) {
                        VerticalDivider(
                            Modifier.padding(end = 16.dp).height(32.dp)
                        )
                    }
                    // LazyColumn отдаёт ушедшую строку другому плагину,
                    // а Switch анимирует смену checked в своём узле —
                    // key создаёт узел заново.
                    key(props.plugin.id) {
                        Switch(
                            checked = props.enabled,
                            onCheckedChange =
                                props.onEnabledChange.takeIf {
                                    openSettings != null
                                },
                        )
                    }
                }
            },
            colors = style.colors,
            modifier =
                if (openSettings != null) {
                    Modifier.clickable(onClick = openSettings)
                } else {
                    Modifier.toggleable(
                        value = props.enabled,
                        role = Role.Switch,
                        onValueChange = props.onEnabledChange,
                    )
                },
        )
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
