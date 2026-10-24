package com.zemlianikin.flexiblesearch.core.widgets

import android.util.Log
import com.zemlianikin.flexiblesearch.core.PluginScope
import com.zemlianikin.flexiblesearch.core.buttons.ActionIcon
import com.zemlianikin.flexiblesearch.core.results.ResultGroup
import com.zemlianikin.flexiblesearch.core.results.ResultItem
import com.zemlianikin.flexiblesearch.core.results.ResultSection
import com.zemlianikin.flexiblesearch.core.results.ResultsBuilder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Виджет плагина на пустой строке. Каждый `emit` [provide] — полный текущий
 * список, пустой скрывает виджет.
 */
class RegisteredWidget(
    val pluginId: String,
    val title: String?,
    val icon: ActionIcon?,
    val provide: () -> Flow<List<ResultItem>>,
)

class WidgetState(
    /** Видимые виджеты — группы для [ResultSection]. */
    val groups: List<ResultGroup>,
    /** Кто-то из виджетов ещё не ответил в этом сборе. */
    val loading: Boolean,
)

/**
 * Собирает виджеты параллельно на [dispatcher]. Порядок — порядок
 * регистрации, как у групп результатов. Лимита времени нет: таймер или
 * музыка шлют обновления, пока виджеты собирают. Упавший виджет скрывается.
 */
class WidgetEngine(
    /**
     * Только растёт: виджет, добавленный после install, сразу начинает
     * собираться.
     */
    private val widgets: Flow<List<RegisteredWidget>>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    /**
     * Ответы прошлого сбора по номеру виджета: окно открыли снова — виджет
     * показывает прежнее, пока не ответил заново, а не пропадает.
     */
    @Volatile private var last = emptyList<List<ResultItem>?>()

    /** Сбор виджетов; каждый сбор — заново, с прошлыми ответами. */
    fun state(): Flow<WidgetState> = channelFlow {
        val mutex = Mutex()
        var started = emptyList<RegisteredWidget>()
        val lists = mutableListOf<List<ResultItem>?>()
        val answered = mutableListOf<Boolean>()

        suspend fun publish() {
            val groups = started.mapIndexedNotNull { index, widget ->
                val items = lists[index]
                if (items.isNullOrEmpty()) return@mapIndexedNotNull null
                ResultGroup(
                    key = "$index/${widget.pluginId}",
                    pluginId = widget.pluginId,
                    title = widget.title,
                    icon = widget.icon,
                    items = items.distinctBy { it.id },
                    fullList = null,
                    complete = false,
                )
            }
            last = lists.toList()
            send(WidgetState(groups, loading = !answered.all { it }))
        }

        widgets.collect { all ->
            val first = mutex.withLock {
                val first = started.size
                if (all.size > first) {
                    started = all
                    repeat(all.size - first) {
                        lists += last.getOrNull(lists.size)
                        answered += false
                    }
                    if (first == 0 && lists.any { it != null }) publish()
                }
                // Виджетов нет — сразу пусто и готово, а не тишина.
                if (all.isEmpty() && started.isEmpty()) publish()
                first
            }
            for (index in first until all.size) {
                val widget = all[index]
                launch(dispatcher) {
                    try {
                        widget.provide().collect { list ->
                            mutex.withLock {
                                lists[index] = list
                                answered[index] = true
                                publish()
                            }
                        }
                    } catch (e: Throwable) {
                        // Отменили нас — дальше; чужая отмена и Error —
                        // падение виджета.
                        ensureActive()
                        Log.e(TAG, "${widget.pluginId} failed", e)
                        mutex.withLock {
                            lists[index] = null
                            answered[index] = true
                            publish()
                        }
                    }
                }
            }
        }
    }
        .conflate()

    private companion object {
        const val TAG = "WidgetEngine"
    }
}

/** Виджеты плагина на пустой строке, `PluginScope.widgets`. */
class PluginWidgets internal constructor(private val scope: PluginScope) {
    /**
     * Виджет на пустой строке без префикса, один ответ при открытии окна:
     * ```
     * add { card("+12°", "Cloudy", id = "now") { ... } }
     * ```
     *
     * Как группа результатов с заголовком [title], но видны все строки.
     * Пустой список — виджета нет.
     */
    fun add(
        title: String? = null,
        icon: ActionIcon? = null,
        provide: suspend ResultsBuilder.() -> Unit,
    ) =
        addFlow(title, icon) {
            flow {
                emit(ResultsBuilder().apply { provide() }.items.toList())
            }
        }

    /**
     * Живой виджет: каждый `emit` — полный список, пустой прячет виджет.
     * Поток собирается, пока окно поиска на экране, и запускается заново
     * при каждом открытии — это и есть обновление. Кэш, если нужен, держит
     * сам плагин.
     */
    fun addFlow(
        title: String? = null,
        icon: ActionIcon? = null,
        provide: () -> Flow<List<ResultItem>>,
    ) {
        val widget = RegisteredWidget(scope.pluginId, title, icon, provide)
        scope.register { scope.host.widgetList.update { it + widget } }
    }
}
