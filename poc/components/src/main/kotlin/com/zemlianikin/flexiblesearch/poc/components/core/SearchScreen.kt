package com.zemlianikin.flexiblesearch.poc.components.core

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SearchScreen(host: PluginHost, window: SearchWindow) {
    SearchTheme {
        CompositionLocalProvider(LocalComponents provides host.components) {
            // Сохраняется id триггера: чип переживает складывание экрана.
            var text by rememberSaveable { mutableStateOf("") }
            var triggerId by rememberSaveable {
                mutableStateOf<String?>(null)
            }
            val query = SearchQuery(text, host.triggers.byId(triggerId))
            val actions =
                remember(window) {
                    object : ActionScope, SearchWindow by window {
                        override val query
                            get() =
                                SearchQuery(
                                    text,
                                    host.triggers.byId(triggerId),
                                )

                        override fun setQuery(input: String) {
                            val parsed = host.triggers.parse(input)
                            triggerId = parsed.trigger?.id
                            text = parsed.text
                        }
                    }
                }
            val state by
                remember(host) {
                        host.engine.results(snapshotFlow { actions.query })
                    }
                    .collectAsState(SearchState.Empty)
            // Пока окно на экране, даже если строка не пустая: стёртый
            // запрос сразу показывает виджеты, без загрузки.
            val widgets by
                remember(host) { host.widgets.groups() }
                    .collectAsStateWithLifecycle(emptyList())
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            // «Назад» сначала убирает чип, потом закрывает поиск.
            BackHandler(enabled = query.trigger != null) {
                triggerId = null
            }

            CompositionLocalProvider(LocalActionScope provides actions) {
                SearchLayout(
                    SearchLayoutProps(
                        searchBar = {
                            SearchBar(
                                SearchBarProps(
                                    text = query.text,
                                    // Без чипа набранный префикс сразу
                                    // становится чипом.
                                    onTextChange = {
                                        if (query.trigger == null) {
                                            actions.setQuery(it)
                                        } else {
                                            text = it
                                        }
                                    },
                                    // Цель Enter — первый результат, как
                                    // targetIndex по умолчанию в Results.
                                    onSubmit = {
                                        state.items
                                            .firstOrNull()
                                            ?.action
                                            ?.invoke(actions)
                                    },
                                    focusRequester = focusRequester,
                                    trigger = query.trigger,
                                    onExitTrigger = { triggerId = null },
                                    placeholder =
                                        query.trigger?.placeholder,
                                )
                            )
                        },
                        loading = {
                            LoadingIndicator(
                                LoadingIndicatorProps(state.loading)
                            )
                        },
                        results = {
                            Results(
                                ResultsProps(
                                    state.groups,
                                    onClick = { it.action(actions) },
                                    // Подсказка заменяет текст, чип
                                    // остаётся.
                                    onFill = {
                                        actions.setQuery(
                                            query.trigger?.command(it) ?: it
                                        )
                                    },
                                    onShowAll = {
                                        actions.setQuery(
                                            it.command(query.text)
                                        )
                                    },
                                )
                            )
                        },
                        widgets = {
                            WidgetArea(
                                WidgetAreaProps(
                                    widgets,
                                    onClick = { it.action(actions) },
                                    onFill = actions::setQuery,
                                )
                            )
                        },
                        showWidgets =
                            query.trigger == null && query.text.isBlank(),
                        quickActions = {
                            QuickActions(
                                QuickActionsProps(
                                    host.quickActions,
                                    query,
                                ) {
                                    it.action(actions)
                                }
                            )
                        },
                        onDismiss = window::close,
                    )
                )
            }
        }
    }
}

@Composable
private fun SearchTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (dark) dynamicDarkColorScheme(context)
                else dynamicLightColorScheme(context)
            dark -> darkColorScheme()
            else -> lightColorScheme()
        }
    MaterialTheme(colorScheme = colors, content = content)
}
