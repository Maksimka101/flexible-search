package com.zemlianikin.flexiblesearch.core

import java.text.Normalizer

/** Очки совпадения и позиции совпавших символов в исходном тексте. */
class NameMatch(val score: Int, val positions: List<Int>)

class Ranked<T>(
    val value: T,
    val match: NameMatch,
    /** Совпавшее имя: позиции [match] — в нём. */
    val name: String,
)

/**
 * Поиск по названиям — приложений, файлов, контактов — так, как их
 * набирают:
 * - нечётко, в духе fzf: символы запроса идут в тексте по порядку, но не
 *   обязательно подряд (`tg` находит `Telegram`); очки выше за начало
 *   слова, подряд идущие символы и короткие разрывы;
 * - слова запроса через пробел — независимо, в любом порядке (`maps
 *   google`);
 * - без учёта регистра, диакритики и `ё`;
 * - в другой раскладке и транслитом ([QueryVariants.Default]): `ntktuhfv` и
 *   `телеграм` находят `Telegram`. Совпадение по варианту чуть слабее
 *   совпадения по набранному.
 *
 * Не для всего подряд: калькулятору и веб-поиску нужен ровно набранный
 * текст, они его и получают — варианты включает только тот, кто вызывает
 * [NameMatcher].
 *
 * Своя реализация: у готовых библиотек нет позиций для подсветки
 * (fuzzywuzzy — Левенштейн, commons-text `FuzzyScore`).
 */
object NameMatcher {
    fun match(
        query: String,
        text: String,
        variants: QueryVariants = QueryVariants.Default,
    ): NameMatch? = best(candidates(query, variants), text)

    /**
     * Совпавшие элементы, лучшие первыми; при равных очках короче — выше.
     */
    fun <T> rank(
        query: String,
        items: Iterable<T>,
        variants: QueryVariants = QueryVariants.Default,
        text: (T) -> String,
    ): List<Ranked<T>> =
        rankByNames(query, items, variants) { listOf(text(it)) }

    /**
     * Как [rank], но у элемента несколько имён, например название и его
     * переводы; засчитывается лучшее, при равенстве — раньше в списке.
     */
    fun <T> rankByNames(
        query: String,
        items: Iterable<T>,
        variants: QueryVariants = QueryVariants.Default,
        names: (T) -> List<String>,
    ): List<Ranked<T>> {
        val candidates = candidates(query, variants)
        return items
            .mapNotNull { item ->
                names(item)
                    .mapNotNull { name ->
                        best(candidates, name)?.let {
                            Ranked(item, it, name)
                        }
                    }
                    .maxByOrNull { it.match.score }
            }
            .sortedWith(
                compareByDescending<Ranked<T>> { it.match.score }
                    .thenBy { it.name.length }
            )
    }

    /** Набранный запрос и его варианты; варианты получают штраф. */
    private fun candidates(query: String, variants: QueryVariants) =
        listOf(query to false) + variants.of(query).map { it to true }

    // Вариант применяется к запросу, а не к тексту: позиции остаются
    // позициями в настоящем названии, подсветка работает как есть.
    private fun best(
        candidates: List<Pair<String, Boolean>>,
        text: String,
    ): NameMatch? =
        candidates
            .mapNotNull { (query, isVariant) ->
                val match = fuzzy(query, text) ?: return@mapNotNull null
                if (!isVariant) match
                else
                    NameMatch(
                        match.score - match.score / VARIANT_PENALTY,
                        match.positions,
                    )
            }
            .maxByOrNull { it.score }

    private fun fuzzy(query: String, text: String): NameMatch? {
        val tokens = query.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        val normalized = normalize(text)
        var score = 0
        val positions = sortedSetOf<Int>()
        for (token in tokens) {
            val match =
                matchToken(normalize(token), text, normalized)
                    ?: return null
            score += match.score
            positions += match.positions
        }
        return NameMatch(score, positions.toList())
    }

    /**
     * Посимвольно, длина не меняется: позиции подходят к исходному тексту.
     */
    fun normalize(text: String): String =
        buildString(text.length) {
            for (char in text) {
                val base =
                    Normalizer.normalize(
                            char.toString(),
                            Normalizer.Form.NFD,
                        )[0]
                append(
                    if (base == 'ё' || base == 'Ё') 'е'
                    else base.lowercaseChar()
                )
            }
        }

    /**
     * Как fzf v1: первое вхождение подпоследовательности вперёд, затем
     * назад от его конца — самое короткое окно, в нём считаются очки.
     */
    private fun matchToken(
        token: String,
        original: String,
        text: String,
    ): NameMatch? {
        var index = 0
        var end = -1
        for ((position, char) in text.withIndex()) {
            if (char == token[index] && ++index == token.length) {
                end = position
                break
            }
        }
        if (end < 0) return null
        index = token.lastIndex
        var start = end
        while (index >= 0) {
            if (text[start] == token[index]) index--
            if (index >= 0) start--
        }
        val positions = ArrayList<Int>(token.length)
        index = 0
        for (position in start..end) {
            if (index < token.length && text[position] == token[index]) {
                positions += position
                index++
            }
        }
        return NameMatch(score(original, positions), positions)
    }

    private fun score(text: String, positions: List<Int>): Int {
        var score = 0
        var previous = -1
        for ((number, position) in positions.withIndex()) {
            var bonus = 0
            val before = text.getOrNull(position - 1)
            if (before == null || !before.isLetterOrDigit()) {
                bonus = BOUNDARY_BONUS
            } else if (
                before.isLowerCase() && text[position].isUpperCase()
            ) {
                bonus = CAMEL_CASE_BONUS
            }
            if (number == 0) bonus *= FIRST_CHAR_MULTIPLIER
            if (previous >= 0) {
                val gap = position - previous - 1
                if (gap == 0) bonus += CONSECUTIVE_BONUS
                else
                    score -=
                        GAP_START_PENALTY +
                            (gap - 1) * GAP_EXTENSION_PENALTY
            }
            score += MATCH_SCORE + bonus
            previous = position
        }
        return score
    }

    /** Вариант запроса теряет десятую часть очков. */
    private const val VARIANT_PENALTY = 10
    private const val MATCH_SCORE = 16
    private const val BOUNDARY_BONUS = 8
    private const val CAMEL_CASE_BONUS = 7
    private const val CONSECUTIVE_BONUS = 4
    private const val FIRST_CHAR_MULTIPLIER = 2
    private const val GAP_START_PENALTY = 3
    private const val GAP_EXTENSION_PENALTY = 1
}
