package com.zemlianikin.flexiblesearch.core

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// --- Экран: только расставляет детей, как Scaffold. Строку вниз —
// переопределить его. ---

class SearchLayoutProps(
    val searchBar: @Composable () -> Unit,
    val loading: @Composable () -> Unit,
    val results: @Composable () -> Unit,
    /** Виджеты на пустой строке, над результатами. */
    val widgets: @Composable () -> Unit,
    /**
     * Строка пустая и без префикса: показать [widgets]. Результаты тоже
     * бывают — от провайдеров с `onEmpty`.
     */
    val showWidgets: Boolean,
    /** Ряд кнопок у клавиатуры, виден всегда. */
    val quickActions: @Composable () -> Unit,
    val onDismiss: () -> Unit,
)

data class SearchLayoutStyle(
    val background: Color,
    val padding: PaddingValues,
    val spacing: Dp,
)

val SearchLayout =
    Component<SearchLayoutProps, SearchLayoutStyle>(
        name = "SearchLayout",
        defaultStyle = {
            SearchLayoutStyle(
                MaterialTheme.colorScheme.surface,
                PaddingValues(12.dp),
                12.dp,
            )
        },
    ) { props, style ->
        Box(
            Modifier.fillMaxSize()
                .background(style.background)
                // Нажатие мимо результатов закрывает поиск, как в Pixel
                // Search.
                .clickable(
                    remember { MutableInteractionSource() },
                    indication = null,
                    onClick = props.onDismiss,
                )
                .safeDrawingPadding()
                .imePadding()
                .padding(style.padding)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(style.spacing)
            ) {
                props.searchBar()
                // Под строкой: появляясь, он сжимает область результатов
                // сверху, а прижатые к низу результаты не двигаются.
                props.loading()
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (props.showWidgets) {
                        Column(
                            verticalArrangement =
                                Arrangement.spacedBy(style.spacing)
                        ) {
                            // Результаты у клавиатуры меряются первыми,
                            // виджетам — что осталось.
                            Box(Modifier.weight(1f, fill = false)) {
                                props.widgets()
                            }
                            props.results()
                        }
                    } else {
                        props.results()
                    }
                }
                props.quickActions()
            }
        }
    }
