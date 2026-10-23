package com.zemlianikin.flexiblesearch.core

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * В PoC плагины встроенные; позже те же объекты будут приходить из APK
 * через DexClassLoader.
 */
interface Plugin {
    /** Уникальный id, будущий пакет плагина. */
    val id: String

    /** Название в списке плагинов. */
    val name: String

    /** Что делает, одной строкой. */
    val description: String

    fun install(scope: PluginScope)
}

/** Всё, что плагин может сделать при установке. */
class PluginScope
internal constructor(
    private val pluginId: String,
    /** Контекст приложения: живёт дольше окна поиска. */
    val context: Context,
    private val host: PluginHost,
) {
    // До конца install регистрации копятся: упавший плагин не оставляет
    // половины. После — применяются сразу: плагин может добавить виджет
    // или кнопку когда угодно и из любого потока.
    private var pending: MutableList<() -> Unit>? = mutableListOf()
    private var discarded = false

    private fun register(add: () -> Unit): Unit =
        synchronized(this) {
            val queue = pending
            if (queue != null) queue += add else if (!discarded) add()
        }

    internal fun commit() =
        synchronized(this) {
            pending?.forEach { it() }
            pending = null
        }

    /** install упал: забыть и его регистрации, и все будущие. */
    internal fun discard() =
        synchronized(this) {
            pending = null
            discarded = true
        }

    /**
     * Ключ-значение плагина: свой файл, не пересекается с другими
     * плагинами, хосту видно, чьи это данные.
     */
    val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(
            "plugin_$pluginId",
            Context.MODE_PRIVATE,
        )
    }

    /**
     * Общий HTTP-клиент с кэшем. В корутинах — `executeAsync()` из
     * okhttp-coroutines:
     * ```
     * http.newCall(Request(url)).executeAsync().use { it.body.string() }
     * ```
     */
    val http: OkHttpClient
        get() = host.services.http

    /**
     * Общий kotlinx.serialization [Json], неизвестные поля пропускает:
     * `json.decodeFromString<Forecast>(body)`.
     */
    val json: Json
        get() = host.services.json

    /**
     * Сколько провайдер может отвечать на запрос, потом — отмена. Одно
     * значение на всех: поменял один плагин — поменялось для всех.
     */
    var providerTimeLimitMs: Long
        get() = host.services.providerTimeLimitMs
        set(value) {
            host.services.providerTimeLimitMs = value
        }

    /** Поменять вид: `style(SearchBar) { it.copy(height = 64.dp) }`. */
    fun <P, S> style(
        component: Component<P, S>,
        transform: StyleTransform<S>,
    ) = register { host.components.addStyle(component, transform) }

    /**
     * Обернуть, дополнить или заменить компонент:
     * ```
     * override(SearchBar) { props, style, original -> ... }
     * ```
     */
    fun <P, S> override(
        component: Component<P, S>,
        override: ComponentOverride<P, S>,
    ) = register { host.components.addOverride(component, override) }

    /**
     * Префикс-команда, например `trigger("Calculator", "calc", "=")`.
     * Результаты для неё — `results(trigger) { ... }`.
     */
    fun trigger(
        label: String,
        vararg prefixes: String,
        placeholder: String? = null,
        icon: ActionIcon? = null,
    ): Trigger =
        Trigger(
                "$pluginId/$label",
                label,
                prefixes.toList(),
                placeholder,
                icon,
            )
            .also { register { host.addTrigger(it) } }

    /**
     * Показывать результаты без Compose, один ответ на запрос:
     * ```
     * results(title = "Files") { query ->
     *     item(file.name, id = file.path) { ... }
     * }
     * ```
     *
     * Без [trigger] — на любую непустую строку без префикса, с [onEmpty] —
     * и на пустую (недавние, предложенные); с ним — только на `префикс
     * остаток`, остаток может быть пустым. Результаты провайдера — одна
     * группа с заголовком [title], до 3 строк; [fullList] — триггер, где
     * видна вся группа. При каждом открытии окна провайдер получает текущий
     * запрос заново.
     */
    fun results(
        trigger: Trigger? = null,
        title: String? = null,
        icon: ActionIcon? = null,
        fullList: Trigger? = null,
        onEmpty: Boolean = false,
        provide: suspend ResultsBuilder.(query: String) -> Unit,
    ) {
        resultsFlow(
            trigger,
            title,
            icon,
            fullList,
            onEmpty,
            singleResult(provide),
        )
    }

    /**
     * Несколько ответов на запрос, каждый — полный список:
     * ```
     * resultsFlow { query ->
     *     flow {
     *         emit(buildResults { item(cached, id = ...) { ... } })
     *         emit(buildResults { item(fresh, id = ...) { ... } })
     *     }
     * }
     * ```
     */
    fun resultsFlow(
        trigger: Trigger? = null,
        title: String? = null,
        icon: ActionIcon? = null,
        fullList: Trigger? = null,
        onEmpty: Boolean = false,
        provide: ResultProvider,
    ) {
        val provider =
            RegisteredProvider(
                pluginId,
                trigger,
                title,
                fullList,
                provide,
                icon,
                onEmpty,
            )
        register { host.providerList.update { it + provider } }
    }

    /** Кнопка в ряду под результатами, видна всегда. */
    fun quickAction(
        label: String,
        icon: ActionIcon,
        id: String = label,
        isSelected: (SearchQuery) -> Boolean = { false },
        action: Action,
    ) {
        val quickAction =
            QuickAction("$pluginId/$id", label, icon, isSelected, action)
        register { host.quickActionList.update { it + quickAction } }
    }

    /** Кнопка-иконка справа в строке поиска; [label] — для TalkBack. */
    fun searchBarAction(
        label: String,
        icon: ActionIcon,
        id: String = label,
        action: Action,
    ) {
        val quickAction =
            QuickAction("$pluginId/$id", label, icon, { false }, action)
        register { host.searchBarActionList.update { it + quickAction } }
    }

    /**
     * Свой экран поверх поиска, с заголовком [title] и «назад»:
     * ```
     * val settings = screen("Settings") { Text("...") }
     * quickAction("Settings", icon) { open(settings) }
     * ```
     */
    fun screen(
        title: String,
        id: String = title,
        content: @Composable ScreenScope.() -> Unit,
    ): Screen =
        Screen("$pluginId/$id", title, content).also { screen ->
            register {
                host.screenMap.update { it + (screen.id to screen) }
            }
        }

    /**
     * Виджет на пустой строке без префикса, один ответ при открытии окна:
     * ```
     * widget { card("+12°", "Cloudy", id = "now") { ... } }
     * ```
     *
     * Как группа результатов с заголовком [title], но видны все строки.
     * Пустой список — виджета нет.
     */
    fun widget(
        title: String? = null,
        icon: ActionIcon? = null,
        provide: suspend ResultsBuilder.() -> Unit,
    ) {
        widgetFlow(title, icon) {
            flow {
                emit(ResultsBuilder().apply { provide() }.items.toList())
            }
        }
    }

    /**
     * Живой виджет: каждый `emit` — полный список, пустой прячет виджет.
     * Поток собирается, пока окно поиска на экране, и запускается заново
     * при каждом открытии — это и есть обновление. Кэш, если нужен, держит
     * сам плагин.
     */
    fun widgetFlow(
        title: String? = null,
        icon: ActionIcon? = null,
        provide: () -> Flow<List<ResultItem>>,
    ) {
        val widget = RegisteredWidget(pluginId, title, icon, provide)
        register { host.widgetList.update { it + widget } }
    }

    /**
     * Окно поиска открылось, и в первый раз тоже. Сразу после этого
     * провайдеры получают текущий запрос заново: здесь — запустить в фоне
     * обновление того, что могло устареть, пока окно было скрыто.
     * Вызывается в главном потоке, поэтому быстрое.
     */
    fun onOpen(action: () -> Unit) {
        register { host.openList.update { it + (pluginId to action) } }
    }
}

/**
 * Устанавливает плагины по порядку и хранит то, что они зарегистрировали.
 * Плагин, упавший в install, пропускается целиком. Регистрации после
 * install дописываются в конец списков: их видно сразу.
 */
class PluginHost(context: Context, plugins: List<Plugin>) {
    val components = ComponentRegistry()
    internal val services = HostServices(context.applicationContext)
    internal val providerList =
        MutableStateFlow(emptyList<RegisteredProvider>())
    internal val triggerList = MutableStateFlow(emptyList<Trigger>())
    internal val quickActionList =
        MutableStateFlow(emptyList<QuickAction>())
    internal val searchBarActionList =
        MutableStateFlow(emptyList<QuickAction>())
    internal val widgetList =
        MutableStateFlow(emptyList<RegisteredWidget>())
    internal val screenMap = MutableStateFlow(emptyMap<String, Screen>())
    internal val openList =
        MutableStateFlow(emptyList<Pair<String, () -> Unit>>())

    val engine = SearchEngine(providerList) { services.providerTimeLimitMs }
    val triggers = TriggerParser(triggerList)
    val quickActions: StateFlow<List<QuickAction>> = quickActionList
    val searchBarActions: StateFlow<List<QuickAction>> = searchBarActionList
    val widgets = WidgetEngine(widgetList)

    /** Экраны плагинов по [Screen.id]. */
    val screens: StateFlow<Map<String, Screen>> = screenMap

    init {
        for (plugin in plugins) {
            val scope =
                PluginScope(plugin.id, context.applicationContext, this)
            try {
                plugin.install(scope)
                scope.commit()
            } catch (e: Throwable) {
                scope.discard()
                Log.e(TAG, "${plugin.id} failed to install", e)
            }
        }
    }

    /** Окно поиска открылось: см. [PluginScope.onOpen]. */
    fun windowOpened() {
        for ((pluginId, action) in openList.value) {
            try {
                action()
            } catch (e: Exception) {
                Log.e(TAG, "$pluginId failed on open", e)
            }
        }
    }

    /** При одинаковых префиксах побеждает первый зарегистрированный. */
    internal fun addTrigger(trigger: Trigger) {
        for (prefix in trigger.prefixes) {
            val owner =
                triggerList.value.find { other ->
                    other.prefixes.any {
                        it.equals(prefix, ignoreCase = true)
                    }
                }
            if (owner != null) {
                Log.w(
                    TAG,
                    "\"$prefix\" of ${trigger.id} taken by ${owner.id}",
                )
            }
        }
        triggerList.update { it + trigger }
    }

    private companion object {
        const val TAG = "PluginHost"
    }
}
