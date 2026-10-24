package com.zemlianikin.flexiblesearch.core.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

typealias ComponentContent<P, S> = @Composable (props: P, style: S) -> Unit

/**
 * Обёртка над компонентом: вызвать [original], дополнить его или заменить
 * целиком (не вызывать).
 */
typealias ComponentOverride<P, S> =
    @Composable
    (props: P, style: S, original: ComponentContent<P, S>) -> Unit

typealias StyleTransform<S> = @Composable (S) -> S

/**
 * Заменяемая часть UI. Вызывается как обычный composable:
 * `SearchBar(SearchBarProps(...))`. Стиль = [defaultStyle], пропущенный
 * через стили плагинов; отрисовка = [content], обёрнутый переопределениями
 * плагинов (последний установленный — снаружи).
 */
class Component<P, S>(
    val name: String,
    private val defaultStyle: @Composable () -> S,
    private val content: ComponentContent<P, S>,
) {
    @Composable
    operator fun invoke(props: P) {
        val registry = LocalComponents.current
        var style = defaultStyle()
        for (transform in registry.stylesOf(this)) style = transform(style)
        val overrides = registry.overridesOf(this)
        val render =
            remember(overrides) {
                overrides.fold(content) { inner, override ->
                    val wrapped: ComponentContent<P, S> = { p, s ->
                        override(p, s, inner)
                    }
                    wrapped
                }
            }
        render(props, style)
    }

    override fun toString() = "Component($name)"
}

/**
 * Стили и переопределения компонентов. Плагин может добавить их и после
 * install, из любого потока: состояние Compose, компоненты перерисуются.
 */
class ComponentRegistry {
    // Типы P и S связаны с ключом-компонентом, поэтому значения хранятся
    // как Any и приводятся при чтении.
    private var styles by
        mutableStateOf(emptyMap<Component<*, *>, List<Any>>())
    private var overrides by
        mutableStateOf(emptyMap<Component<*, *>, List<Any>>())

    fun <P, S> addStyle(
        component: Component<P, S>,
        transform: StyleTransform<S>,
    ) =
        synchronized(this) {
            styles += component to styles[component].orEmpty() + transform
        }

    fun <P, S> addOverride(
        component: Component<P, S>,
        override: ComponentOverride<P, S>,
    ) =
        synchronized(this) {
            overrides +=
                component to overrides[component].orEmpty() + override
        }

    @Suppress("UNCHECKED_CAST")
    fun <P, S> stylesOf(
        component: Component<P, S>
    ): List<StyleTransform<S>> =
        styles[component].orEmpty() as List<StyleTransform<S>>

    @Suppress("UNCHECKED_CAST")
    fun <P, S> overridesOf(
        component: Component<P, S>
    ): List<ComponentOverride<P, S>> =
        overrides[component].orEmpty() as List<ComponentOverride<P, S>>
}

/**
 * Подмена в поддереве:
 * ```
 * CompositionLocalProvider(LocalComponents provides другойРеестр) { ... }
 * ```
 */
val LocalComponents = staticCompositionLocalOf { ComponentRegistry() }
