package com.zemlianikin.flexiblesearch.core

import android.net.Uri

/**
 * Ссылки в поиск для виджетов, ярлыков и других приложений:
 * ```
 * flexiblesearch://search               — открыть, запрос как был
 * flexiblesearch://search?q=files%20pdf  — с запросом, префикс — чипом
 * flexiblesearch://screen/<id экрана>    — экран плагина поверх поиска
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
            else -> null
        }

    private const val SEARCH = "search"
    private const val SCREEN = "screen"
    private const val QUERY = "q"
}

internal sealed interface SearchLink {
    data class Search(val query: String?) : SearchLink

    data class OpenScreen(val id: String) : SearchLink
}
