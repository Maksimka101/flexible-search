package com.zemlianikin.flexiblesearch.plugins.converter.calc.engine

import com.zemlianikin.flexiblesearch.plugins.converter.calc.Calculation
import com.zemlianikin.flexiblesearch.plugins.converter.calc.Value
import com.zemlianikin.flexiblesearch.plugins.converter.core.RateTable

// Конвейер: text → Lexer → Balancer → Parser → Evaluator → Value.
// Этап, который не может продолжить, бросает Stop; Calculator превращает
// его в результат.

/**
 * Досрочный результат конвейера: Incomplete или Failed. Без стектрейса —
 * это не баг.
 */
class Stop(val outcome: Calculation) : Exception(null, null, false, false)

/**
 * Текст → плоский список слов. Ловит неизвестные, неоднозначные слова и
 * плохие числа.
 */
interface Lexer {
    /** @throws Stop */
    fun lex(text: String): List<Token>
}

/** Токены со скобками парами + где рисовать виртуальные '('. */
data class Balanced(val tokens: List<Token>, val virtualParens: List<Int>)

/**
 * Дополняет скобки до парных по правилам R1b и R2. Ошибок не бросает:
 * лишнюю ')' ловит парсер.
 */
interface Balancer {
    fun balance(tokens: List<Token>): Balanced
}

/**
 * Токены с парными скобками → дерево. Упал на конце ввода — Incomplete,
 * раньше — Failed.
 */
interface Parser {
    /** @throws Stop */
    fun parse(tokens: List<Token>): Node
}

/**
 * Дерево + курсы → значение по правилам типов. Ошибки — Failed; Incomplete
 * — только когда к сумме прибавляют голое число в конце ввода (`13 usd +
 * 8`): его ещё можно дописать до суммы или процента.
 */
interface Evaluator {
    /** @throws Stop */
    fun eval(node: Node, rates: RateTable): Value
}
