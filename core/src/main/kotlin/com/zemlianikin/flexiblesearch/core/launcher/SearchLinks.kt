package com.zemlianikin.flexiblesearch.core.launcher

import android.net.Uri
import com.zemlianikin.flexiblesearch.core.screens.Screen
import com.zemlianikin.flexiblesearch.core.search.SearchScreen

/**
 * Ссылки в поиск для виджетов, ярлыков и других приложений:
 * ```
 * flexiblesearch://search               — открыть, запрос как был
 * flexiblesearch://search?q=files%20pdf  — с запросом, префикс — чипом
 * flexiblesearch://screen/<id экрана>    — экран плагина поверх поиска
 * flexiblesearch://action/<id кнопки>    — действие кнопки на главном
 * flexiblesearch://shortcut/<id ярлыка>  — действие ярлыка
 * ```
 *
 * Хост принимает их в своей Activity и передаёт в [SearchScreen].
 */
object SearchLinks {
    const val SCHEME = "flexiblesearch"

    /** Поиск с [query]; без него — с тем запросом, что был. */
    fun search(query: String? = null): Uri =
        Uri.Builder()
            .scheme(SCHEME)
            .authority(SEARCH)
            .apply { if (query != null) appendQueryParameter(QUERY, query) }
            .build()

    /** Экран по [Screen.id], `плагин/экран`. */
    fun screen(id: String): Uri =
        Uri.Builder().scheme(SCHEME).authority(SCREEN).path(id).build()

    /** Действие [HomeScreenButton] по id, `плагин/кнопка`. */
    fun action(id: String): Uri =
        Uri.Builder().scheme(SCHEME).authority(ACTION).path(id).build()

    /** Действие [Shortcut] по id, `плагин/ярлык`. */
    fun shortcut(id: String): Uri =
        Uri.Builder().scheme(SCHEME).authority(SHORTCUT).path(id).build()

    internal fun parse(uri: Uri): SearchLink? =
        when {
            uri.scheme != SCHEME -> null
            uri.authority == SEARCH ->
                SearchLink.Search(uri.getQueryParameter(QUERY))
            uri.authority == SCREEN ->
                uri.path
                    ?.removePrefix("/")
                    ?.takeIf { it.isNotEmpty() }
                    ?.let(SearchLink::OpenScreen)
            uri.authority == ACTION ->
                uri.path
                    ?.removePrefix("/")
                    ?.takeIf { it.isNotEmpty() }
                    ?.let(SearchLink::RunAction)
            uri.authority == SHORTCUT ->
                uri.path
                    ?.removePrefix("/")
                    ?.takeIf { it.isNotEmpty() }
                    ?.let(SearchLink::RunShortcut)
            else -> null
        }

    private const val SEARCH = "search"
    private const val SCREEN = "screen"
    private const val ACTION = "action"
    private const val SHORTCUT = "shortcut"
    private const val QUERY = "q"
}

internal sealed interface SearchLink {
    data class Search(val query: String?) : SearchLink

    data class OpenScreen(val id: String) : SearchLink

    data class RunAction(val id: String) : SearchLink

    data class RunShortcut(val id: String) : SearchLink
}
