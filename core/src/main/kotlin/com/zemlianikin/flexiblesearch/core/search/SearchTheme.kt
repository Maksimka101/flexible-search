package com.zemlianikin.flexiblesearch.core.search

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.zemlianikin.flexiblesearch.core.component.Component

// --- Тема поиска и экранов плагинов. Цвета обоев —
// `style(SearchTheme) { it.copy(colorScheme = ...) }`. ---

class SearchThemeProps(val content: @Composable () -> Unit)

data class SearchThemeStyle(
    val colorScheme: ColorScheme,
    val typography: Typography,
    val shapes: Shapes,
)

val SearchTheme =
    Component<SearchThemeProps, SearchThemeStyle>(
        name = "SearchTheme",
        defaultStyle = {
            SearchThemeStyle(
                if (isSystemInDarkTheme()) darkColorScheme()
                else lightColorScheme(),
                Typography(),
                Shapes(),
            )
        },
    ) { props, style ->
        MaterialTheme(
            colorScheme = style.colorScheme,
            shapes = style.shapes,
            typography = style.typography,
            content = props.content,
        )
    }
