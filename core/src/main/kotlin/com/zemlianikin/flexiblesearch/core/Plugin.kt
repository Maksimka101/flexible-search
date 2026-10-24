package com.zemlianikin.flexiblesearch.core

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

/**
 * Всё, что плагин может сделать при установке. Сервисы и вид — прямо здесь,
 * остальное по фичам: [triggers], [results], [widgets], [buttons],
 * [launcher], [screens].
 */
class PluginScope
internal constructor(
    internal val pluginId: String,
    /** Контекст приложения: живёт дольше окна поиска. */
    val context: Context,
    internal val host: PluginHost,
) {
    // До конца install регистрации копятся: упавший плагин не оставляет
    // половины. После — применяются сразу: плагин может добавить виджет
    // или кнопку когда угодно и из любого потока.
    private var pending: MutableList<() -> Unit>? = mutableListOf()
    private var discarded = false

    internal fun register(add: () -> Unit): Unit =
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

    /** Полный id того, что регистрирует плагин: `плагин/id`. */
    internal fun ownId(id: String) = "$pluginId/$id"

    /** Менять можно только своё: триггеры, ярлыки. */
    internal fun requireOwn(id: String) =
        require(id.startsWith("$pluginId/")) {
            "$id does not belong to $pluginId"
        }

    // --- Сервисы ---

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

    // --- Вид ---

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

    // --- Фичи ---

    /** Префиксы-команды с чипом в строке: `calc 2+2`, `=2+2`. */
    val triggers = PluginTriggers(this)

    /** Результаты на запрос и Enter в строке поиска. */
    val results = PluginResults(this)

    /** Виджеты на пустой строке: погода, переключатели. */
    val widgets = PluginWidgets(this)

    /** Кнопки в окне поиска: ряд под результатами и строка поиска. */
    val buttons = PluginButtons(this)

    /** Вне окна: кнопки виджета на главном экране и ярлыки. */
    val launcher = PluginLauncher(this)

    /** Свои экраны поверх поиска и настройки плагина. */
    val screens = PluginScreens(this)

    // --- Жизненный цикл ---

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
    internal val submitHandlerList =
        MutableStateFlow(emptyList<SubmitHandler>())
    internal val homeScreenButtonList =
        MutableStateFlow(emptyList<HomeScreenButton>())
    internal val shortcutList = MutableStateFlow(emptyList<Shortcut>())
    internal val widgetList =
        MutableStateFlow(emptyList<RegisteredWidget>())
    internal val screenMap = MutableStateFlow(emptyMap<String, Screen>())
    internal val openList =
        MutableStateFlow(emptyList<Pair<String, () -> Unit>>())
    internal val settingsMap =
        MutableStateFlow(emptyMap<String, ScreenScope.() -> Unit>())

    val engine = SearchEngine(providerList) { services.providerTimeLimitMs }
    val triggers = TriggerParser(triggerList)
    val quickActions: StateFlow<List<QuickAction>> = quickActionList
    val searchBarActions: StateFlow<List<QuickAction>> = searchBarActionList
    val widgets = WidgetEngine(widgetList)
    val homeScreenButtons: StateFlow<List<HomeScreenButton>> =
        homeScreenButtonList
    val shortcuts: StateFlow<List<Shortcut>> = shortcutList

    /** Экраны плагинов по [Screen.id]. */
    val screens: StateFlow<Map<String, Screen>> = screenMap

    /** Открыть настройки плагина по его id: [PluginScreens.settings]. */
    val settings: StateFlow<Map<String, ScreenScope.() -> Unit>> =
        settingsMap

    init {
        for (plugin in plugins) {
            val scope =
                PluginScope(plugin.id, context.applicationContext, this)
            try {
                PerfTrace.section("install ${plugin.id}") {
                    plugin.install(scope)
                }
                scope.commit()
            } catch (e: Throwable) {
                scope.discard()
                Log.e(TAG, "${plugin.id} failed to install", e)
            }
        }
    }

    /** Окно поиска открылось: см. [PluginScope.onOpen]. */
    fun windowOpened() {
        PerfTrace.mark("window opened")
        for ((pluginId, action) in openList.value) {
            try {
                action()
            } catch (e: Exception) {
                Log.e(TAG, "$pluginId failed on open", e)
            }
        }
    }

    /** Enter в строке поиска: см. [PluginResults.onSubmit]. */
    fun submit(scope: ActionScope, results: SearchState) =
        submitChain(submitHandlerList.value, scope, results)

    /** При одинаковых префиксах побеждает первый зарегистрированный. */
    internal fun addTrigger(trigger: Trigger) {
        warnTaken(trigger)
        triggerList.update { it + trigger }
    }

    internal fun warnTaken(trigger: Trigger) {
        for (prefix in trigger.prefixes) {
            val owner =
                triggerList.value.find { other ->
                    other !== trigger &&
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
    }

    private companion object {
        const val TAG = "PluginHost"
    }
}
