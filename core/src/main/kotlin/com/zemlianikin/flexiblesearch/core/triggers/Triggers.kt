package com.zemlianikin.flexiblesearch.core.triggers

import com.zemlianikin.flexiblesearch.core.PluginScope
import com.zemlianikin.flexiblesearch.core.buttons.ActionIcon
import kotlinx.coroutines.flow.StateFlow

/**
 * Префикс-команда: `calc 2+2`, `=2+2`. Пока триггер включён, запрос
 * получают только провайдеры этого триггера, и даже с пустым остатком. В
 * строке он показывается чипом с [label]. Включается префиксом или
 * `setQuery(SearchQuery(text, trigger))`, поэтому префиксов может не быть.
 */
class Trigger
internal constructor(
    val id: String,
    val label: String,
    prefixes: List<String>,
    /** Подсказка в поле, пока после префикса ничего не набрано. */
    val placeholder: String?,
    /** Показывается в чипе перед [label]. */
    val icon: ActionIcon? = null,
) {
    /** Меняется через [PluginTriggers.setPrefixes], парсер видит сразу. */
    @Volatile
    var prefixes: List<String> = prefixes
        internal set
}

/**
 * После словесного префикса (`calc`) нужен пробел, иначе `calculator`
 * включал бы калькулятор; символьный (`=`) срабатывает сразу.
 */
internal fun separatorAfter(prefix: String) =
    if (prefix.last().isLetterOrDigit()) " " else ""

/**
 * Строка поиска: активный триггер (чип) и текст после него. Без триггера
 * [text] — вся строка.
 */
data class SearchQuery(val text: String = "", val trigger: Trigger? = null)

/**
 * Находит префикс в начале строки, см. [separatorAfter]. Регистр не важен.
 * При одинаковых префиксах побеждает первый зарегистрированный.
 */
class TriggerParser(private val triggers: StateFlow<List<Trigger>>) {
    fun parse(input: String): SearchQuery {
        for (trigger in triggers.value) for (prefix in trigger.prefixes) {
            val start = prefix + separatorAfter(prefix)
            if (input.startsWith(start, ignoreCase = true)) {
                return SearchQuery(input.substring(start.length), trigger)
            }
        }
        return SearchQuery(input)
    }

    fun byId(id: String?): Trigger? = triggers.value.find { it.id == id }
}

/** Префиксы-команды плагина, `PluginScope.triggers`. */
class PluginTriggers internal constructor(private val scope: PluginScope) {
    /**
     * Префикс-команда, например `add("Calculator", "calc", "=")`.
     * Результаты для неё — `results.add(trigger) { ... }`.
     */
    fun add(
        label: String,
        vararg prefixes: String,
        placeholder: String? = null,
        icon: ActionIcon? = null,
    ): Trigger =
        Trigger(
                scope.ownId(label),
                label,
                prefixes.toList(),
                placeholder,
                icon,
            )
            .also { scope.register { scope.host.addTrigger(it) } }

    /**
     * Заменить префиксы своего триггера, например из настроек плагина.
     * Пустой список — триггер включается только через
     * `setQuery(SearchQuery(text, trigger))`.
     */
    fun setPrefixes(trigger: Trigger, prefixes: List<String>) {
        scope.requireOwn(trigger.id)
        require(prefixes.none { it.isEmpty() }) { "Empty prefix" }
        trigger.prefixes = prefixes.toList()
        scope.host.warnTaken(trigger)
    }
}
