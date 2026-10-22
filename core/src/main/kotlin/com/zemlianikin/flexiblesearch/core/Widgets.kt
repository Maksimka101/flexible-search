package com.zemlianikin.flexiblesearch.core

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
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

/**
 * Собирает виджеты параллельно на [dispatcher]. Порядок — порядок
 * регистрации, как у групп результатов. Лимита времени нет: таймер или
 * музыка шлют обновления, пока виджеты собирают. Упавший виджет скрывается.
 */
class WidgetEngine(
    private val widgets: List<RegisteredWidget>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    /** Видимые виджеты — группы для [ResultSection]. */
    fun groups(): Flow<List<ResultGroup>> = channelFlow {
        val mutex = Mutex()
        val lists = arrayOfNulls<List<ResultItem>>(widgets.size)

        suspend fun publish() {
            val groups = widgets.mapIndexedNotNull { index, widget ->
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
            send(groups)
        }

        widgets.forEachIndexed { index, widget ->
            launch(dispatcher) {
                try {
                    widget.provide().collect { list ->
                        mutex.withLock {
                            lists[index] = list
                            publish()
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "${widget.pluginId} failed", e)
                    mutex.withLock {
                        lists[index] = null
                        publish()
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
