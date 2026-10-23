package com.zemlianikin.flexiblesearch.plugins.converter.calc.engine

// Серый ящик: токены и дерево. Снаружи calc не используются.

import com.zemlianikin.flexiblesearch.plugins.converter.calc.Span
import com.zemlianikin.flexiblesearch.plugins.converter.core.CurrencyCode
import com.zemlianikin.flexiblesearch.plugins.converter.core.Num

enum class Operation {
    Plus,
    Minus,
    Multiply,
    Divide,
}

/** Слово ввода: что написано. Плоский список, выход лексера. */
sealed interface Token {
    val span: Span

    /** 10, 1 000, 1,5 */
    data class Number(val value: Num, override val span: Span) : Token

    /** + - − * × x / ÷ */
    data class Operator(val operation: Operation, override val span: Span) :
        Token

    /** % */
    data class Percent(override val span: Span) : Token

    /** Множитель после числа: k, тыс, млн. */
    data class Scale(val factor: Num, override val span: Span) : Token

    /** usd, $, рублей */
    data class Currency(val code: CurrencyCode, override val span: Span) :
        Token

    /** synthetic — вставлена балансировщиком, span нулевой ширины. */
    data class Paren(
        val open: Boolean,
        val synthetic: Boolean,
        override val span: Span,
    ) : Token

    /** to, in, в, на, → */
    data class To(override val span: Span) : Token

    /** of, от */
    data class Of(override val span: Span) : Token
}

/**
 * Дерево выражения: что и в каком порядке считать. span покрывает детей.
 */
sealed interface Node {
    val span: Span

    /** 10; минус перед литералом парсер сворачивает сам: -5. */
    data class Number(val value: Num, override val span: Span) : Node

    /** 10% */
    data class Percent(val inner: Node, override val span: Span) : Node

    /** Валюта без числа = 1 единица: eur. */
    data class Currency(val code: CurrencyCode, override val span: Span) :
        Node

    /** 10 usd, usd 10, (2 + 3) usd */
    data class WithCurrency(
        val inner: Node,
        val code: CurrencyCode,
        override val span: Span,
    ) : Node

    /**
     * Минус не перед литералом: -(2 + 3), -10% (процент остаётся
     * процентом).
     */
    data class Negate(val inner: Node, override val span: Span) : Node

    /** a + b, a * b, неявное 2(3 + 4). */
    data class Binary(
        val left: Node,
        val operation: Operation,
        val right: Node,
        override val span: Span,
    ) : Node

    /** 10% of 50 */
    data class PercentOf(
        val percent: Node,
        val base: Node,
        override val span: Span,
    ) : Node

    /** x to eur */
    data class Convert(
        val inner: Node,
        val target: CurrencyCode,
        override val span: Span,
    ) : Node
}
