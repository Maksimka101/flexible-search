package com.zemlianikin.flexiblesearch.plugins.converter.calc.engine

import com.zemlianikin.flexiblesearch.plugins.converter.calc.CalcError
import com.zemlianikin.flexiblesearch.plugins.converter.calc.Calculation
import com.zemlianikin.flexiblesearch.plugins.converter.calc.Span
import com.zemlianikin.flexiblesearch.plugins.converter.core.Num

/**
 * Recursive descent без отката. Вход — токены с парными скобками, скобки в
 * дерево не попадают.
 *
 * ```
 * line    = expr { To Currency [ tail ] }
 * tail    = продолжение expr, где левый операнд — уже конвертированное
 * expr    = term { (+ | -) term }
 * term    = unary { (* | /) unary | unary }
 * unary   = { - | + } pct
 * pct     = primary [ % [ Of unary ] ]
 * primary = Number [Scale] [Currency]
 *         | Currency [ [-] Number [Scale] ]
 *         | group [Currency]
 * group   = ( line )
 * ```
 *
 * `to` конвертирует всё, что слева в этой группе; после него выражение
 * продолжается: `10 usd to eur * 10`. Второй вариант `term` — неявное
 * умножение вокруг скобок.
 *
 * Упал на конце ввода или на синтетической `)` в конце — Incomplete, на
 * любом другом токене — Failed.
 */
class RecursiveDescentParser : Parser {
    override fun parse(tokens: List<Token>): Node = Run(tokens).parseAll()

    private class Run(private val t: List<Token>) {
        private var pos = 0

        fun parseAll(): Node {
            val node = line()
            if (pos < t.size) fail(t[pos])
            return node
        }

        private fun peek(offset: Int = 0): Token? =
            t.getOrNull(pos + offset)

        /**
         * Span от токена [start] до последнего съеденного: покрывает и
         * скобки, которых нет в дереве.
         */
        private fun spanFrom(start: Int) =
            Span(t[start].span.start, t[pos - 1].span.end)

        private fun fail(token: Token?): Nothing {
            val incomplete =
                token == null ||
                    (token is Token.Paren && !token.open && token.synthetic)
            throw Stop(
                if (incomplete) Calculation.Incomplete
                else
                    Calculation.Failed(
                        CalcError.UnexpectedToken,
                        token.span,
                    )
            )
        }

        private fun line(): Node {
            val start = pos
            var node = expr(start)
            while (peek() is Token.To) {
                pos++
                val target = peek() as? Token.Currency ?: fail(peek())
                pos++
                node =
                    expr(
                        start,
                        Node.Convert(node, target.code, spanFrom(start)),
                    )
            }
            return node
        }

        /**
         * [first] — уже разобранный левый операнд (результат `to`): дальше
         * он ведёт себя как первичное выражение.
         */
        private fun expr(start: Int, first: Node? = null): Node {
            var left = term(start, first)
            while (true) {
                val op =
                    (peek() as? Token.Operator)?.operation?.takeIf {
                        it.additive
                    } ?: return left
                pos++
                left = Node.Binary(left, op, term(pos), spanFrom(start))
            }
        }

        private fun term(start: Int = pos, first: Node? = null): Node {
            var left = first ?: unary()
            while (true) {
                val next = peek()
                val op =
                    when {
                        next is Token.Operator ->
                            if (next.operation.additive) return left
                            else next.operation.also { pos++ }
                        next is Token.Paren && next.open ->
                            Operation.Multiply
                        next is Token.Number && endsWithClose() ->
                            Operation.Multiply
                        // Валюта после `)` — суффикс группы и сюда не
                        // доходит.
                        next is Token.Number || next is Token.Currency ->
                            missingOperator()
                        next is Token.Scale ||
                            next is Token.Percent ||
                            next is Token.Of -> fail(next)
                        else -> return left // To, `)`, конец ввода
                    }
                left = Node.Binary(left, op, unary(), spanFrom(start))
            }
        }

        private fun endsWithClose() =
            (t[pos - 1] as? Token.Paren)?.open == false

        /**
         * Span ошибки — весь второй операнд, поэтому его сначала разбираем.
         */
        private fun missingOperator(): Nothing =
            throw Stop(
                Calculation.Failed(CalcError.MissingOperator, unary().span)
            )

        private fun unary(): Node {
            val start = pos
            val op = (peek() as? Token.Operator)?.operation
            return when (op) {
                Operation.Minus -> {
                    pos++
                    negate(unary(), spanFrom(start))
                }
                Operation.Plus -> {
                    pos++
                    unary()
                }
                else -> pct()
            }
        }

        /** Минус перед литералом сворачивается в число, иначе Negate. */
        private fun negate(inner: Node, span: Span): Node =
            when {
                inner is Node.Number -> Node.Number(-inner.value, span)
                inner is Node.WithCurrency && inner.inner is Node.Number ->
                    inner.copy(
                        inner =
                            Node.Number(
                                -inner.inner.value,
                                Span(span.start, inner.inner.span.end),
                            ),
                        span = span,
                    )
                else -> Node.Negate(inner, span)
            }

        private operator fun Num.unaryMinus() = Num(-value)

        private fun pct(): Node {
            val start = pos
            val value = primary()
            if (peek() !is Token.Percent) return value
            pos++
            val percent = Node.Percent(value, spanFrom(start))
            if (peek() !is Token.Of) return percent
            pos++
            return Node.PercentOf(percent, unary(), spanFrom(start))
        }

        private fun primary(): Node {
            val start = pos
            val token = peek()
            return when {
                token is Token.Number -> withSuffix(number(), start)
                token is Token.Currency -> {
                    pos++
                    currencyFirst(token, start)
                }
                token is Token.Paren && token.open ->
                    withSuffix(group(), start)
                else -> fail(token)
            }
        }

        /** Число и множитель сразу после него: `1.5k`. */
        private fun number(): Node.Number {
            val digits = t[pos++] as Token.Number
            val scale = (peek() as? Token.Scale)?.also { pos++ }
            val value =
                if (scale == null) digits.value
                else Num(digits.value.value * scale.factor.value)
            return Node.Number(
                value,
                Span(digits.span.start, (scale ?: digits).span.end),
            )
        }

        /** `10 usd`, `(2 + 3) usd`: валюта справа. */
        private fun withSuffix(node: Node, start: Int): Node {
            val currency = peek() as? Token.Currency ?: return node
            pos++
            return Node.WithCurrency(node, currency.code, spanFrom(start))
                .also { rejectSecondCurrency() }
        }

        /**
         * `usd 10`, `$-10` (знак вплотную к обоим), голая `usd` = 1
         * единица. Валюта уже съедена.
         */
        private fun currencyFirst(
            currency: Token.Currency,
            start: Int,
        ): Node {
            val next = peek()
            val amount =
                when {
                    next is Token.Number -> number()
                    next is Token.Operator &&
                        next.operation == Operation.Minus &&
                        peek(1) is Token.Number &&
                        currency.span.end == next.span.start &&
                        next.span.end == peek(1)!!.span.start -> {
                        pos++
                        val number = number()
                        Node.Number(
                            -number.value,
                            Span(next.span.start, number.span.end),
                        )
                    }
                    else ->
                        return Node.Currency(currency.code, currency.span)
                }
            return Node.WithCurrency(amount, currency.code, spanFrom(start))
                .also { rejectSecondCurrency() }
        }

        private fun rejectSecondCurrency() {
            val extra = peek()
            if (extra is Token.Currency)
                throw Stop(
                    Calculation.Failed(CalcError.TwoCurrencies, extra.span)
                )
        }

        private fun group(): Node {
            pos++
            val inner = line()
            val close = peek()
            if (close is Token.Paren && !close.open) pos++ else fail(close)
            return inner
        }

        private val Operation.additive
            get() = this == Operation.Plus || this == Operation.Minus
    }
}
