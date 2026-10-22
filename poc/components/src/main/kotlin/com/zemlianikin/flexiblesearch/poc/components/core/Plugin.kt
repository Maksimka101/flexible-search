package com.zemlianikin.flexiblesearch.poc.components.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * В PoC плагины встроенные; позже те же объекты будут приходить из APK
 * через DexClassLoader.
 */
interface Plugin {
    /** Уникальный id, будущий пакет плагина. */
    val id: String

    fun install(scope: PluginScope)
}

/** Всё, что плагин может сделать при установке. */
class PluginScope
internal constructor(
    private val pluginId: String,
    /** Контекст приложения: живёт дольше окна поиска. */
    val context: Context,
    private val registry: ComponentRegistry,
    private val providers: MutableList<RegisteredProvider>,
    private val triggers: MutableList<Trigger>,
    private val quickActions: MutableList<QuickAction>,
    private val widgets: MutableList<RegisteredWidget>,
    private val services: HostServices,
) {
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
        get() = services.http

    /**
     * Общий kotlinx.serialization [Json], неизвестные поля пропускает:
     * `json.decodeFromString<Forecast>(body)`.
     */
    val json: Json
        get() = services.json

    /**
     * Сколько провайдер может отвечать на запрос, потом — отмена. Одно
     * значение на всех: поменял один плагин — поменялось для всех.
     */
    var providerTimeLimitMs: Long
        get() = services.providerTimeLimitMs
        set(value) {
            services.providerTimeLimitMs = value
        }

    /**
     * Поменять вид: `style(SearchBar) { it.copy(shape = CircleShape) }`.
     */
    fun <P, S> style(
        component: Component<P, S>,
        transform: StyleTransform<S>,
    ) = registry.addStyle(component, transform)

    /**
     * Обернуть, дополнить или заменить компонент:
     * ```
     * override(SearchBar) { props, style, original -> ... }
     * ```
     */
    fun <P, S> override(
        component: Component<P, S>,
        override: ComponentOverride<P, S>,
    ) = registry.addOverride(component, override)

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
            .also { triggers += it }

    /**
     * Показывать результаты без Compose, один ответ на запрос:
     * ```
     * results(title = "Files") { query -> item("report.pdf") { ... } }
     * ```
     *
     * Без [trigger] — на любую непустую строку без префикса, с [onEmpty] —
     * и на пустую (недавние, предложенные); с ним — только на `префикс
     * остаток`, остаток может быть пустым. Результаты провайдера — одна
     * группа с заголовком [title], до 3 строк; [fullList] — триггер, где
     * видна вся группа.
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
     *         emit(buildResults { item(cache.find(query)) { ... } })
     *         emit(buildResults { item(network.find(query)) { ... } })
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
        providers +=
            RegisteredProvider(
                pluginId,
                trigger,
                title,
                fullList,
                provide,
                icon,
                onEmpty,
            )
    }

    /** Кнопка в ряду под результатами, видна всегда. */
    fun quickAction(
        label: String,
        icon: ActionIcon,
        id: String = label,
        isSelected: (SearchQuery) -> Boolean = { false },
        action: Action,
    ) {
        quickActions +=
            QuickAction("$pluginId/$id", label, icon, isSelected, action)
    }

    /**
     * Виджет на пустой строке без префикса, один ответ при открытии окна:
     * ```
     * widget { card("+12°", "Cloudy") { ... } }
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
        widgets += RegisteredWidget(pluginId, title, icon, provide)
    }
}

/**
 * Устанавливает плагины по порядку и хранит то, что они зарегистрировали.
 */
class PluginHost(context: Context, plugins: List<Plugin>) {
    val components = ComponentRegistry()
    val engine: SearchEngine
    val triggers: TriggerParser
    val quickActions: List<QuickAction>
    val widgets: WidgetEngine

    init {
        val providers = mutableListOf<RegisteredProvider>()
        val triggers = mutableListOf<Trigger>()
        val quickActions = mutableListOf<QuickAction>()
        val widgets = mutableListOf<RegisteredWidget>()
        val services = HostServices(context.applicationContext)
        for (plugin in plugins) {
            plugin.install(
                PluginScope(
                    plugin.id,
                    context.applicationContext,
                    components,
                    providers,
                    triggers,
                    quickActions,
                    widgets,
                    services,
                )
            )
        }
        engine = SearchEngine(providers) { services.providerTimeLimitMs }
        this.triggers = TriggerParser(triggers)
        this.quickActions = quickActions
        this.widgets = WidgetEngine(widgets)
    }
}
