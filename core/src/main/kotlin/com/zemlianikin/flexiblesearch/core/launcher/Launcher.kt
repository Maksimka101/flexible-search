package com.zemlianikin.flexiblesearch.core.launcher

import com.zemlianikin.flexiblesearch.core.PluginScope
import com.zemlianikin.flexiblesearch.core.buttons.ActionIcon
import com.zemlianikin.flexiblesearch.core.results.Action
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Всё плагина вне окна поиска, `PluginScope.launcher`: кнопки в виджете
 * поиска на главном экране и ярлыки. Тап открывает окно поиска и выполняет
 * `Action`.
 */
class PluginLauncher internal constructor(private val scope: PluginScope) {
    /**
     * Кнопка справа от строки в виджете поиска на главном экране, по
     * порядку плагинов. [visible] — например настройка плагина: виджет
     * перерисуется сам.
     *
     * ```
     * widgetButton("Calculator", icon) {
     *     setQuery(SearchQuery("", calc))
     * }
     * ```
     */
    fun widgetButton(
        label: String,
        icon: ActionIcon,
        id: String = label,
        visible: StateFlow<Boolean> = MutableStateFlow(true),
        action: Action,
    ) {
        val button =
            HomeScreenButton(scope.ownId(id), label, icon, visible, action)
        scope.register {
            scope.host.homeScreenButtonList.update { it + button }
        }
    }

    /**
     * Ярлык: в меню по долгому нажатию на иконку приложения, по порядку
     * плагинов, и на рабочем столе через [pin].
     *
     * ```
     * val calcShortcut = shortcut("Calculator", icon) {
     *     setQuery(SearchQuery("", calc))
     * }
     * ```
     */
    fun shortcut(
        label: String,
        icon: ActionIcon,
        id: String = label,
        action: Action,
    ): Shortcut =
        Shortcut(scope.ownId(id), label, icon, action).also { shortcut ->
            scope.register {
                scope.host.shortcutList.update { it + shortcut }
            }
        }

    /** Лаунчер умеет добавлять ярлыки на рабочий стол: [pin]. */
    val canPin: Boolean
        get() = Shortcuts.canPin(scope.context)

    /**
     * Предложить добавить свой [shortcut] на рабочий стол: лаунчер покажет
     * диалог. Только пока приложение на экране, например из настроек.
     */
    fun pin(shortcut: Shortcut) {
        scope.requireOwn(shortcut.id)
        Shortcuts.pin(scope.context, shortcut)
    }
}
