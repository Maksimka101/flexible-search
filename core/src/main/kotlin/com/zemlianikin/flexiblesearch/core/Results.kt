package com.zemlianikin.flexiblesearch.core

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Окно поиска: его даёт Activity. */
interface SearchWindow {
    val context: Context

    /** Закрыть окно поиска, например после запуска приложения. */
    fun close()
}

/**
 * То, что доступно действию результата или кнопки при нажатии; экраны
 * плагинов — [open].
 */
interface ActionScope : SearchWindow, Navigator {
    val query: SearchQuery

    /**
     * Префикс в начале строки сразу становится чипом: `setQuery("calc ")`.
     */
    fun setQuery(input: String)

    /** Включить триггер без префикса: `setQuery(SearchQuery("", calc))`. */
    fun setQuery(query: SearchQuery)
}

typealias Action = ActionScope.() -> Unit

/**
 * Звено Enter: сделать своё или передать дальше через `next`, см.
 * [PluginScope.onSubmit].
 */
typealias SubmitHandler =
    ActionScope.(results: SearchState, next: () -> Unit) -> Unit

/** [ActionScope] окна для своей отрисовки ([ResultItem.Custom]). */
val LocalActionScope =
    staticCompositionLocalOf<ActionScope> {
        error("ActionScope вне окна поиска")
    }

/** Превью грузится лениво, когда строка видна, и кэшируется в core. */
typealias ThumbnailLoader = suspend () -> ImageBitmap?

/**
 * Результат без Compose: рисуется компонентами core ([ResultCard],
 * [AppRow], [ResultRow], [SuggestionRow]). Исключение — [Custom].
 */
sealed interface ResultItem {
    /** Уникален в пределах плагина. */
    val id: String
    val action: Action

    /** Крупный ответ: калькулятор, конвертер, погода. */
    class Card(
        override val id: String,
        val title: String,
        val subtitle: String?,
        override val action: Action,
    ) : ResultItem

    /** Плитка в ряду. */
    class App(
        override val id: String,
        val label: String,
        /** Как [Row.thumbnail]: грузится, когда плитка видна. */
        val icon: ThumbnailLoader?,
        override val action: Action,
    ) : ResultItem

    /** Строка: превью или иконка, заголовок, подпись. Файлы, контакты. */
    class Row(
        override val id: String,
        val title: String,
        /** Позиции символов [title] для подсветки, из [NameMatch]. */
        val titleHighlights: List<Int>,
        val subtitle: String?,
        val icon: ActionIcon?,
        val thumbnail: ThumbnailLoader?,
        override val action: Action,
    ) : ResultItem

    /** Текстовая подсказка; `↖` подставляет её в строку. */
    class Suggestion(
        override val id: String,
        val text: String,
        val highlights: List<Int>,
        override val action: Action,
    ) : ResultItem

    /**
     * Своя отрисовка на Compose, когда шаблонов мало: живые кнопки, график.
     * Нажатия — внутри [content], поэтому Enter на нём ничего не делает.
     */
    class Custom(
        override val id: String,
        val content: @Composable ActionScope.() -> Unit,
    ) : ResultItem {
        override val action: Action = {}
    }
}

/**
 * `id` обязателен: результаты с одинаковым id в группе склеиваются, по нему
 * же кэшируется превью. Название не годится — `report.pdf` бывает в разных
 * папках.
 */
class ResultsBuilder {
    internal val items = mutableListOf<ResultItem>()

    fun card(
        title: String,
        subtitle: String? = null,
        id: String,
        action: Action,
    ) {
        items += ResultItem.Card(id, title, subtitle, action)
    }

    fun app(
        label: String,
        icon: ThumbnailLoader?,
        id: String,
        action: Action,
    ) {
        items += ResultItem.App(id, label, icon, action)
    }

    fun item(
        title: String,
        subtitle: String? = null,
        titleHighlights: List<Int> = emptyList(),
        icon: ActionIcon? = null,
        thumbnail: ThumbnailLoader? = null,
        id: String,
        action: Action,
    ) {
        items +=
            ResultItem.Row(
                id,
                title,
                titleHighlights,
                subtitle,
                icon,
                thumbnail,
                action,
            )
    }

    fun suggestion(
        text: String,
        highlights: List<Int> = emptyList(),
        id: String,
        action: Action,
    ) {
        items += ResultItem.Suggestion(id, text, highlights, action)
    }

    fun custom(id: String, content: @Composable ActionScope.() -> Unit) {
        items += ResultItem.Custom(id, content)
    }
}

/** Список результатов для `emit` в провайдере через Flow. */
fun buildResults(build: ResultsBuilder.() -> Unit): List<ResultItem> =
    ResultsBuilder().apply(build).items.toList()

/**
 * Провайдер результатов. Каждый `emit` — полный текущий список провайдера,
 * а не добавка: кэш, потом сеть — два `emit`.
 */
typealias ResultProvider = (query: String) -> Flow<List<ResultItem>>

/** Провайдер с одним ответом; сахар для `PluginScope.results { }`. */
internal fun singleResult(
    provide: suspend ResultsBuilder.(query: String) -> Unit
): ResultProvider = { query ->
    flow { emit(ResultsBuilder().apply { provide(query) }.items.toList()) }
}

/**
 * Провайдер плагина. С [trigger] — только для запросов с его префиксом. Его
 * результаты — одна группа с заголовком [title]; [fullList] — триггер, где
 * видна вся группа (заголовок «все ›»).
 */
class RegisteredProvider(
    val pluginId: String,
    val trigger: Trigger?,
    val title: String?,
    val fullList: Trigger?,
    val provide: ResultProvider,
    /** Иконка в заголовке группы. */
    val icon: ActionIcon? = null,
    /** Без [trigger]: отвечать и на пустую строку. */
    val onEmpty: Boolean = false,
)

/** Результаты одного провайдера. */
class ResultGroup(
    /** Стабильный ключ группы для списка. */
    val key: String,
    val pluginId: String,
    val title: String?,
    val icon: ActionIcon?,
    val items: List<ResultItem>,
    val fullList: Trigger?,
    /** Запрос с префиксом этой группы: показывать её целиком. */
    val complete: Boolean,
    /** Провайдер ещё отвечает: поток не завершился. */
    val loading: Boolean = false,
)

/**
 * Плитки ([ResultItem.App]) всех групп — в один ряд, в группе первой
 * плитки, в порядке групп. Места в ряду у тех, кто раньше зарегистрирован.
 * Группа только из плиток после этого исчезает.
 */
fun List<ResultGroup>.withSharedAppRow(): List<ResultGroup> {
    val apps = flatMap { group ->
        group.items.filterIsInstance<ResultItem.App>()
    }
    val owner = firstOrNull { group ->
        group.items.any { it is ResultItem.App }
    }
    return mapNotNull { group ->
        val items =
            group.items.filter { it !is ResultItem.App } +
                if (group == owner) apps else emptyList()
        if (items.isEmpty()) return@mapNotNull null
        ResultGroup(
            group.key,
            group.pluginId,
            group.title,
            group.icon,
            items,
            group.fullList,
            group.complete,
            group.loading,
        )
    }
}

class SearchState(
    val groups: List<ResultGroup>,
    /** Кто-то из провайдеров ещё не ответил ни разу. */
    val loading: Boolean,
) {
    /** Все результаты подряд; цель Enter — первый. */
    val items: List<ResultItem> = groups.flatMap { it.items }

    companion object {
        val Empty = SearchState(emptyList(), loading = false)
    }
}

/**
 * Enter по цепочке [handlers]: последний — снаружи, внутри всех — первый
 * результат.
 */
internal fun submitChain(
    handlers: List<SubmitHandler>,
    scope: ActionScope,
    results: SearchState,
) {
    val first: () -> Unit = {
        results.items.firstOrNull()?.action?.invoke(scope)
    }
    handlers.fold(first) { next, handler ->
        { scope.handler(results, next) }
    }()
}

/**
 * Собирает провайдеры параллельно на [dispatcher], чтобы медленный плагин
 * не держал главный поток. Группы идут в порядке регистрации провайдеров,
 * независимо от того, кто ответил раньше: раскладка не меняется от запроса
 * к запросу. Провайдер, превысивший [providerTimeLimitMs] или упавший,
 * останавливается, его последний список остаётся. Новый провайдер из
 * [providers] сразу получает текущий запрос.
 *
 * Загрузка (`loading`) — только до первого ответа каждого провайдера.
 * Дальнейшие `emit` приходят без неё: пока поток провайдера не завершился,
 * загрузку показывает его группа ([ResultGroup.loading]), а не весь поиск.
 */
class SearchEngine(
    /** Растёт, если плагин регистрирует провайдер после install. */
    private val providers: Flow<List<RegisteredProvider>>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** Читается на каждый запрос: плагин может поменять его на ходу. */
    private val providerTimeLimitMs: () -> Long = {
        DEFAULT_PROVIDER_TIME_LIMIT_MS
    },
) {
    /**
     * Результаты для потока запросов. Группа прежнего запроса остаётся на
     * месте, пока её провайдер не ответил на новый: группы не мигают.
     */
    fun results(queries: Flow<SearchQuery>): Flow<SearchState> =
        channelFlow {
            var last = SearchState.Empty
            combine(queries, providers, ::Pair).collectLatest {
                (query, providers) ->
                search(query, providers, last.groups).collect {
                    last = it
                    send(it)
                }
            }
        }

    /**
     * С триггером — только его провайдеры, даже при пустом тексте. Без
     * триггера — глобальные, на пустой текст — только с `onEmpty`.
     */
    private fun search(
        query: SearchQuery,
        providers: List<RegisteredProvider>,
        previous: List<ResultGroup>,
    ): Flow<SearchState> {
        val selected =
            providers.withIndex().filter { (_, provider) ->
                when {
                    query.trigger != null ->
                        provider.trigger == query.trigger
                    provider.trigger != null -> false
                    query.text.isBlank() -> provider.onEmpty
                    else -> true
                }
            }
        return channelFlow {
            val mutex = Mutex()
            val stale = previous.associate { it.key to it.items }
            val lists =
                Array(selected.size) { index ->
                    val (number, provider) = selected[index]
                    stale[groupKey(number, provider)]
                }
            val answered = BooleanArray(selected.size)
            val finished = BooleanArray(selected.size)

            suspend fun publish() {
                val groups =
                    selected.mapIndexedNotNull { index, (number, provider)
                        ->
                        val items = lists[index]
                        if (items.isNullOrEmpty())
                            return@mapIndexedNotNull null
                        ResultGroup(
                            key = groupKey(number, provider),
                            pluginId = provider.pluginId,
                            title = provider.title,
                            icon = provider.icon,
                            items = items.distinctBy { it.id },
                            fullList = provider.fullList,
                            complete = provider.trigger != null,
                            loading = !finished[index],
                        )
                    }
                send(SearchState(groups, loading = !answered.all { it }))
            }

            publish()
            val timeLimitMs = providerTimeLimitMs()
            selected.forEachIndexed { index, (_, provider) ->
                launch(dispatcher) {
                    val pluginId = provider.pluginId
                    try {
                        withTimeoutOrNull(timeLimitMs) {
                            provider.provide(query.text).collect { list ->
                                mutex.withLock {
                                    lists[index] = list
                                    answered[index] = true
                                    publish()
                                }
                            }
                        } ?: Log.w(TAG, "$pluginId: time limit for $query")
                    } catch (e: Throwable) {
                        // Отменили нас — дальше. Чужая отмена, например
                        // `withTimeout` внутри плагина, и Error — падение
                        // провайдера, а не всего поиска.
                        ensureActive()
                        Log.e(TAG, "$pluginId failed for $query", e)
                    }
                    // Закончил, даже не ответив: ждать больше нечего.
                    mutex.withLock {
                        if (!answered[index]) lists[index] = null
                        answered[index] = true
                        finished[index] = true
                        publish()
                    }
                }
            }
        }
            .conflate()
    }

    private fun groupKey(number: Int, provider: RegisteredProvider) =
        "$number/${provider.pluginId}"

    companion object {
        const val DEFAULT_PROVIDER_TIME_LIMIT_MS = 30_000L
        private const val TAG = "SearchEngine"
    }
}
