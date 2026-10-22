package com.zemlianikin.flexiblesearch.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
        val render =
            remember(registry) {
                registry.overridesOf(this).fold(content) { inner, override
                    ->
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
 * Стили и переопределения компонентов. Заполняется при установке плагинов,
 * потом не меняется.
 */
class ComponentRegistry {
    // Типы P и S связаны с ключом-компонентом, поэтому значения хранятся
    // как Any и приводятся при чтении.
    private val styles = mutableMapOf<Component<*, *>, MutableList<Any>>()
    private val overrides =
        mutableMapOf<Component<*, *>, MutableList<Any>>()

    fun <P, S> addStyle(
        component: Component<P, S>,
        transform: StyleTransform<S>,
    ) {
        styles.getOrPut(component) { mutableListOf() } += transform
    }

    fun <P, S> addOverride(
        component: Component<P, S>,
        override: ComponentOverride<P, S>,
    ) {
        overrides.getOrPut(component) { mutableListOf() } += override
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
