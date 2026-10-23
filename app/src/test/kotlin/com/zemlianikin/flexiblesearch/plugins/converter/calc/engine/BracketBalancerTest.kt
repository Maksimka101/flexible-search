package com.zemlianikin.flexiblesearch.plugins.converter.calc.engine

import com.zemlianikin.flexiblesearch.plugins.converter.calc.Span
import com.zemlianikin.flexiblesearch.plugins.converter.core.CurrencyCode
import com.zemlianikin.flexiblesearch.plugins.converter.core.Decimal
import com.zemlianikin.flexiblesearch.plugins.converter.core.Num
import kotlin.test.Test
import kotlin.test.assertEquals

class BracketBalancerTest {

    private val balancer = BracketBalancer()

    /**
     * Токены из строки: числа, `+ - * /`, `%`, `( )`, слова `to`, `of`,
     * `usd`, `eur`. Span — символы строки.
     */
    private fun tokens(text: String): List<Token> {
        val result = ArrayList<Token>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            var end = i + 1
            when {
                c == ' ' -> {
                    i++
                    continue
                }
                c.isDigit() -> {
                    while (end < text.length && text[end].isDigit()) end++
                    result +=
                        Token.Number(
                            Num(Decimal(text.substring(i, end))),
                            Span(i, end),
                        )
                }
                c.isLetter() -> {
                    while (end < text.length && text[end].isLetter()) end++
                    val span = Span(i, end)
                    result +=
                        when (val word = text.substring(i, end)) {
                            "to" -> Token.To(span)
                            "of" -> Token.Of(span)
                            else ->
                                Token.Currency(
                                    CurrencyCode(word.uppercase()),
                                    span,
                                )
                        }
                }
                c == '+' ->
                    result += Token.Operator(Operation.Plus, Span(i, end))
                c == '-' ->
                    result += Token.Operator(Operation.Minus, Span(i, end))
                c == '*' ->
                    result +=
                        Token.Operator(Operation.Multiply, Span(i, end))
                c == '/' ->
                    result += Token.Operator(Operation.Divide, Span(i, end))
                c == '%' -> result += Token.Percent(Span(i, end))
                c == '(' || c == ')' ->
                    result +=
                        Token.Paren(
                            c == '(',
                            synthetic = false,
                            Span(i, end),
                        )
                else -> error("unknown char '$c'")
            }
            i = end
        }
        return result
    }

    /**
     * Запись токенов без пробелов; синтетические скобки — «‹» и «›», чтобы
     * отличать от явных.
     */
    private fun render(tokens: List<Token>) =
        tokens.joinToString("") {
            when (it) {
                is Token.Number -> it.value.value.toPlainString()
                is Token.Operator ->
                    when (it.operation) {
                        Operation.Plus -> "+"
                        Operation.Minus -> "-"
                        Operation.Multiply -> "*"
                        Operation.Divide -> "/"
                    }
                is Token.Percent -> "%"
                is Token.Scale -> "k"
                is Token.Currency -> it.code.code.lowercase()
                is Token.Paren ->
                    when {
                        it.open && it.synthetic -> "‹"
                        it.open -> "("
                        it.synthetic -> "›"
                        else -> ")"
                    }
                is Token.To -> "to"
                is Token.Of -> "of"
            }
        }

    /**
     * Проверяет вид результата и позиции виртуальных `(` в исходной строке.
     */
    private fun check(
        input: String,
        expected: String,
        virtualParens: List<Int> = emptyList(),
    ) {
        val balanced = balancer.balance(tokens(input))
        assertEquals(expected, render(balanced.tokens), input)
        assertEquals(virtualParens, balanced.virtualParens, input)
    }

    @Test
    fun `balanced input is unchanged`() = check("(10+2)*3", "(10+2)*3")

    @Test
    fun `empty input`() {
        val balanced = balancer.balance(emptyList())
        assertEquals(emptyList<Token>(), balanced.tokens)
        assertEquals(emptyList<Int>(), balanced.virtualParens)
    }

    @Test
    fun `close at start of line`() = check("10 + 20)", "‹10+20)", listOf(0))

    @Test
    fun `close then operator`() =
        check("10 + 20) * 2", "‹10+20)*2", listOf(0))

    @Test
    fun `next open goes after previous synthetic close`() =
        check("5*10+20)+10-3)", "‹5*10+20)+‹10-3)", listOf(0, 9))

    @Test
    fun `operator after close stays outside`() =
        check("10-2)*3+1)", "‹10-2)*‹3+1)", listOf(0, 6))

    @Test
    fun `implicit multiplication between synthetic groups`() =
        check("10-2) 3+1)", "‹10-2)‹3+1)", listOf(0, 6))

    @Test
    fun `explicit pair is an operand`() =
        check("2*(3+4)+1)*5", "‹2*(3+4)+1)*5", listOf(0))

    @Test
    fun `percent and of stay outside`() =
        check("10+5)% of 200)", "‹10+5)%of‹200)", listOf(0, 10))

    @Test
    fun `currency suffix stays outside`() =
        check("10+5) usd - 3)", "‹10+5)usd-‹3)", listOf(0, 12))

    @Test
    fun `unclosed open is closed at end`() = check("(10 + 2", "(10+2›")

    @Test fun `nested unclosed opens`() = check("2*(3+(4", "2*(3+(4››")

    @Test
    fun `close position is end of last token`() {
        val balanced = balancer.balance(tokens("(10 + 2"))
        assertEquals(Span(7, 7), balanced.tokens.last().span)
    }

    @Test
    fun `open span is zero width at operand start`() {
        val open =
            balancer.balance(tokens("  10 + 20)")).tokens.first()
                as Token.Paren
        assertEquals(Span(2, 2), open.span)
    }

    @Test
    fun `to before close stays inside the segment`() =
        check("10 usd to eur)", "‹10usdtoeur)", listOf(0))

    @Test
    fun `to after synthetic close stays outside`() =
        check("10 usd - 10%) to eur", "‹10usd-10%)toeur", listOf(0))

    @Test
    fun `close after operator stays unpaired`() = check("10 + )", "10+)")

    @Test fun `empty pair is left alone`() = check("()", "()")

    @Test fun `lone close stays unpaired`() = check(")", ")")

    @Test
    fun `second close directly after first stays unpaired`() =
        check("10))", "‹10))", listOf(0))

    @Test
    fun `unpaired close is a barrier for the next segment`() =
        check("10 + ) 5)", "10+)‹5)", listOf(7))

    @Test
    fun `explicit close after explicit open is untouched`() =
        check("(1+2)*3)", "‹(1+2)*3)", listOf(0))
}
