package com.zemlianikin.flexiblesearch.core

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Экран плагина поверх поиска: настройки, подробности. Регистрируется через
 * `PluginScope.screen`, открывается [Navigator.open]; «назад» возвращает на
 * предыдущий.
 */
class Screen
internal constructor(
    val id: String,
    val title: String,
    internal val content: @Composable ScreenScope.() -> Unit,
)

interface Navigator {
    /** Открыть поверх текущего. */
    fun open(screen: Screen)

    /** Вернуться на предыдущий экран. */
    fun back()
}

/** То, что доступно содержимому [Screen]. */
interface ScreenScope : SearchWindow, Navigator

// --- Обёртка экрана: заголовок и «назад» ---

class ScreenLayoutProps(
    val title: String,
    val onBack: () -> Unit,
    val content: @Composable () -> Unit,
)

data class ScreenLayoutStyle(
    val background: Color,
    val titleColor: Color,
)

@OptIn(ExperimentalMaterial3Api::class)
val ScreenLayout =
    Component<ScreenLayoutProps, ScreenLayoutStyle>(
        name = "ScreenLayout",
        defaultStyle = {
            val colors = MaterialTheme.colorScheme
            ScreenLayoutStyle(colors.surface, colors.onSurface)
        },
    ) { props, style ->
        Scaffold(
            containerColor = style.background,
            topBar = {
                TopAppBar(
                    title = { Text(props.title) },
                    navigationIcon = {
                        IconButton(onClick = props.onBack) {
                            Icon(BackIcon, contentDescription = "Back")
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = style.background,
                            titleContentColor = style.titleColor,
                            navigationIconContentColor = style.titleColor,
                        ),
                )
            },
        ) { padding ->
            Box(
                Modifier.fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
            ) {
                props.content()
            }
        }
    }

// Material Icons «arrow_back».
private val BackIcon: ImageVector =
    materialIcon(
        "Back",
        "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z",
    )
