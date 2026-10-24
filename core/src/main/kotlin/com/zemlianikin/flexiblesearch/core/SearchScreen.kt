package com.zemlianikin.flexiblesearch.core

import android.net.Uri
import android.util.Log
import android.view.ViewTreeObserver
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Корень навигации; id экранов плагинов — `плагин/экран`, без пересечений.
 */
private const val SEARCH_ROUTE = "search"

private const val TAG = "SearchScreen"

/**
 * Поиск и экраны плагинов поверх него. Сменился [host] (включили или
 * выключили плагин) — поиск начинается заново, открытые экраны остаются,
 * если их плагин на месте. [links] — [SearchLinks], каждая применяется один
 * раз.
 */
@Composable
fun SearchScreen(
    host: PluginHost,
    window: SearchWindow,
    links: Flow<Uri> = emptyFlow(),
) {
    CompositionLocalProvider(LocalComponents provides host.components) {
        SearchTheme(
            SearchThemeProps {
                // Id, а не сами экраны: переживают смену хоста и
                // пересоздание
                // Activity при складывании.
                val backStack =
                    rememberSaveable(
                        saver =
                            listSaver(
                                { it.toList() },
                                { it.toMutableStateList() },
                            )
                    ) {
                        mutableStateListOf(SEARCH_ROUTE)
                    }
                val screens by host.screens.collectAsState()
                LaunchedEffect(screens) {
                    backStack.retainAll {
                        it == SEARCH_ROUTE || it in screens
                    }
                }
                // Запрос здесь, а не в Search: ссылка меняет его сразу,
                // даже пока окно в фоне и кадров нет, — к открытию
                // результаты уже готовы. Новый host — поиск заново.
                val queryState =
                    rememberSaveable(host, saver = QueryState.Saver) {
                        QueryState()
                    }
                val currentQueryState by rememberUpdatedState(queryState)
                val currentHost by rememberUpdatedState(host)
                val currentScreens by rememberUpdatedState(screens)
                LaunchedEffect(links) {
                    links.collect { uri ->
                        when (val link = SearchLinks.parse(uri)) {
                            is SearchLink.Search -> {
                                backStack.retainAll { it == SEARCH_ROUTE }
                                if (link.query != null)
                                    currentQueryState.set(
                                        currentHost.triggers.parse(
                                            link.query
                                        )
                                    )
                            }
                            is SearchLink.OpenScreen ->
                                when {
                                    link.id !in currentScreens ->
                                        Log.w(TAG, "No screen ${link.id}")
                                    backStack.last() != link.id ->
                                        backStack += link.id
                                }
                            null -> Log.w(TAG, "Unknown link $uri")
                        }
                    }
                }
                val navigator =
                    remember(backStack) {
                        object : Navigator {
                            override fun open(screen: Screen) {
                                backStack += screen.id
                            }

                            override fun back() {
                                if (backStack.size > 1) {
                                    backStack.removeAt(backStack.lastIndex)
                                }
                            }
                        }
                    }
                NavDisplay(
                    // Экраны выключенного плагина пропадают сразу, ещё до
                    // LaunchedEffect.
                    backStack =
                        backStack.filter {
                            it == SEARCH_ROUTE || it in screens
                        },
                    onBack = navigator::back,
                    entryProvider = { route ->
                        val screen = screens[route]
                        NavEntry(route) {
                            if (screen != null) {
                                ScreenEntry(screen, window, navigator)
                            } else {
                                // Состояние поиска ссылается на плагины
                                // хоста.
                                key(host) {
                                    Search(
                                        host,
                                        window,
                                        navigator,
                                        queryState,
                                    )
                                }
                            }
                        }
                    },
                )
            }
        )
    }
}

@Composable
private fun ScreenEntry(
    screen: Screen,
    window: SearchWindow,
    navigator: Navigator,
) {
    val scope =
        remember(window, navigator) {
            object :
                ScreenScope,
                SearchWindow by window,
                Navigator by navigator {}
        }
    ScreenLayout(
        ScreenLayoutProps(screen.title, navigator::back) {
            screen.content(scope)
        }
    )
}

@Composable
private fun Search(
    host: PluginHost,
    window: SearchWindow,
    navigator: Navigator,
    queryState: QueryState,
) {
    var text by queryState::text
    var triggerId by queryState::triggerId
    val query = SearchQuery(text, host.triggers.byId(triggerId))
    val actions =
        remember(window, queryState) {
            object :
                ActionScope,
                SearchWindow by window,
                Navigator by navigator {
                override val query
                    get() =
                        SearchQuery(
                            text,
                            host.triggers.byId(triggerId),
                        )

                override fun setQuery(input: String) {
                    queryState.set(host.triggers.parse(input))
                }
            }
        }
    // Каждое открытие окна: плагины обновляют данные, а запрос
    // пересчитывается — пока окно скрыто, результаты устаревают.
    var opens by remember { mutableIntStateOf(0) }
    LifecycleStartEffect(host) {
        host.windowOpened()
        opens++
        onStopOrDispose {}
    }
    val searchState by
        remember(host) {
                host.engine.results(
                    snapshotFlow { actions.query to opens }.map { it.first }
                )
            }
            .collectAsState(null)
    val state = searchState ?: SearchState.Empty
    // Пока окно на экране, даже если строка не пустая: стёртый
    // запрос сразу показывает виджеты, без загрузки.
    val widgetState by
        remember(host) { host.widgets.state() }
            .collectAsStateWithLifecycle(null)
    val widgets = widgetState?.groups.orEmpty()
    val thumbnailLoads = remember { ThumbnailLoads() }
    val revealed =
        // Виджеты первый кадр не ждёт: появляются, когда готовы.
        rememberFirstFrame(thumbnailLoads) { searchState?.loading == false }
    val quickActions by host.quickActions.collectAsState()
    val searchBarActions by host.searchBarActions.collectAsState()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    // «Назад» сначала убирает чип, потом закрывает поиск.
    BackHandler(enabled = query.trigger != null) {
        triggerId = null
    }

    CompositionLocalProvider(
        LocalActionScope provides actions,
        LocalItemAnimation provides revealed,
        LocalThumbnailLoads provides thumbnailLoads.takeUnless { revealed },
    ) {
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
                            placeholder = query.trigger?.placeholder,
                            actions = searchBarActions,
                            onAction = { it.action(actions) },
                        )
                    )
                },
                loading = {
                    LoadingIndicator(LoadingIndicatorProps(state.loading))
                },
                results = {
                    Results(
                        ResultsProps(
                            state.groups,
                            widgets =
                                if (
                                    query.trigger == null &&
                                        query.text.isBlank()
                                )
                                    widgets
                                else emptyList(),
                            onClick = { it.action(actions) },
                            // Подсказка заменяет текст, чип
                            // остаётся.
                            onFill = {
                                actions.setQuery(
                                    query.trigger?.command(it) ?: it
                                )
                            },
                            onShowAll = {
                                actions.setQuery(it.command(query.text))
                            },
                        )
                    )
                },
                quickActions = {
                    QuickActions(
                        QuickActionsProps(
                            quickActions,
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

/**
 * Запрос в строке. Хранит id триггера, а не сам триггер: чип переживает
 * складывание экрана.
 */
private class QueryState(text: String = "", triggerId: String? = null) {
    var text by mutableStateOf(text)
    var triggerId by mutableStateOf(triggerId)

    fun set(query: SearchQuery) {
        triggerId = query.trigger?.id
        text = query.text
    }

    companion object {
        val Saver =
            listSaver<QueryState, String?>(
                { listOf(it.text, it.triggerId) },
                { QueryState(it[0].orEmpty(), it[1]) },
            )
    }
}

/** Дольше первый кадр не ждёт: дальше — что успело. */
private const val FIRST_FRAME_TIMEOUT_MS = 500L

/**
 * Окно появляется целиком: первый кадр не рисуется, пока [ready] не станет
 * true и не догрузятся видимые превью ([loads]), но не дольше
 * [FIRST_FRAME_TIMEOUT_MS]. Так держат и splash screen. До первого кадра —
 * true нет, и результаты появляются без анимации.
 */
@Composable
private fun rememberFirstFrame(
    loads: ThumbnailLoads,
    ready: () -> Boolean,
): Boolean {
    var revealed by remember { mutableStateOf(false) }
    val view = LocalView.current
    DisposableEffect(view) {
        val listener =
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (!revealed) return false
                    PerfTrace.mark("first frame")
                    view.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
            }
        view.viewTreeObserver.addOnPreDrawListener(listener)
        onDispose {
            view.viewTreeObserver.removeOnPreDrawListener(listener)
        }
    }
    LaunchedEffect(Unit) {
        val loaded =
            withTimeoutOrNull(FIRST_FRAME_TIMEOUT_MS) {
                snapshotFlow(ready).first { it }
                PerfTrace.mark("results ready")
                // Кадр, чтобы строки с превью успели начать загрузку.
                withFrameNanos {}
                snapshotFlow { loads.pending }.first { it == 0 }
                PerfTrace.mark("thumbnails loaded")
            }
        if (loaded == null) PerfTrace.mark("first frame timeout")
        revealed = true
    }
    return revealed
}
