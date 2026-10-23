package com.zemlianikin.flexiblesearch.plugins.converter.calc.engine

import com.zemlianikin.flexiblesearch.plugins.converter.calc.CalcError
import com.zemlianikin.flexiblesearch.plugins.converter.calc.Calculation
import com.zemlianikin.flexiblesearch.plugins.converter.calc.Span
import com.zemlianikin.flexiblesearch.plugins.converter.core.CurrencyCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LexiconLexerTest {
    private val lexer = LexiconLexer()

    // Токен одной строкой. Число нормализуем: Decimal.equals учитывает
    // scale (4 ≠ 4.0).
    private fun show(token: Token): String =
        when (token) {
            is Token.Number ->
                "num ${token.value.value.stripTrailingZeros().toPlainString()}"
            is Token.Scale ->
                "scale ${token.factor.value.stripTrailingZeros().toPlainString()}"
            is Token.Operator -> "op ${token.operation}"
            is Token.Currency -> "cur ${token.code.code}"
            is Token.Paren -> if (token.open) "(" else ")"
            is Token.Percent -> "%"
            is Token.To -> "to"
            is Token.Of -> "of"
        }

    private fun lex(text: String): List<String> =
        lexer.lex(text).map(::show)

    private fun stop(text: String): Calculation {
        val tokens =
            try {
                lexer.lex(text)
            } catch (stop: Stop) {
                return stop.outcome
            }
        throw AssertionError(
            "ожидался Stop для '$text', получили ${tokens.map(::show)}"
        )
    }

    private fun assertFailed(text: String, error: CalcError, span: Span) =
        assertEquals(Calculation.Failed(error, span), stop(text))

    private fun check(vararg cases: Pair<String, List<String>>) {
        for ((text, expected) in cases) assertEquals(
            expected,
            lex(text),
            text,
        )
    }

    @Test
    fun emptyInputGivesNoTokens() {
        check("" to emptyList(), "   " to emptyList())
    }

    @Test
    fun numbers() =
        check(
            "10" to listOf("num 10"),
            "1.5" to listOf("num 1.5"),
            "1,5" to listOf("num 1.5"),
            "1,000" to listOf("num 1"),
            "1 000" to listOf("num 1000"),
            "12 345" to listOf("num 12345"),
            "1_000_000" to listOf("num 1000000"),
            "1'000.5" to listOf("num 1000.5"),
            "1 000,50" to listOf("num 1000.5"),
            "12 345 6" to listOf("num 12345", "num 6"),
            "5 5" to listOf("num 5", "num 5"),
            "1000 000" to listOf("num 1000", "num 0"),
        )

    @Test
    fun scales() =
        check(
            "10k" to listOf("num 10", "scale 1000"),
            "10к" to listOf("num 10", "scale 1000"),
            "1.5к" to listOf("num 1.5", "scale 1000"),
            "10 тыс." to listOf("num 10", "scale 1000"),
            "2 млн" to listOf("num 2", "scale 1000000"),
            "10 k" to listOf("num 10", "scale 1000"),
            "10 mxn" to listOf("num 10", "cur MXN"),
        )

    @Test
    fun operatorsAndBrackets() =
        check(
            "1+2" to listOf("num 1", "op Plus", "num 2"),
            "1 - 2" to listOf("num 1", "op Minus", "num 2"),
            "1 − 2 – 3" to
                listOf("num 1", "op Minus", "num 2", "op Minus", "num 3"),
            "2*3 × 4 · 5" to
                listOf(
                    "num 2",
                    "op Multiply",
                    "num 3",
                    "op Multiply",
                    "num 4",
                    "op Multiply",
                    "num 5",
                ),
            "2 x 3" to listOf("num 2", "op Multiply", "num 3"),
            "6/3 ÷ 2" to
                listOf("num 6", "op Divide", "num 3", "op Divide", "num 2"),
            "(1)" to listOf("(", "num 1", ")"),
            "10%" to listOf("num 10", "%"),
        )

    @Test
    fun currencies() =
        check(
            "usd" to listOf("cur USD"),
            "10 USD" to listOf("num 10", "cur USD"),
            "\$10" to listOf("cur USD", "num 10"),
            "10€" to listOf("num 10", "cur EUR"),
            "100 рублей" to listOf("num 100", "cur RUB"),
            "5 долларов" to listOf("num 5", "cur USD"),
            "руб. 5" to listOf("cur RUB", "num 5"),
            "C\$5 A\$5 US\$5" to
                listOf(
                    "cur CAD",
                    "num 5",
                    "cur AUD",
                    "num 5",
                    "cur USD",
                    "num 5",
                ),
            "10 ₽" to listOf("num 10", "cur RUB"),
            "10 thb" to listOf("num 10", "cur THB"),
        )

    @Test
    fun keywords() =
        check(
            "10 usd to eur" to listOf("num 10", "cur USD", "to", "cur EUR"),
            "10 usd в eur" to listOf("num 10", "cur USD", "to", "cur EUR"),
            "10 usd → eur" to listOf("num 10", "cur USD", "to", "cur EUR"),
            "10 usd->eur" to listOf("num 10", "cur USD", "to", "cur EUR"),
            "10 in eur" to listOf("num 10", "to", "cur EUR"),
            "10% of 50" to listOf("num 10", "%", "of", "num 50"),
            "10% от 50" to listOf("num 10", "%", "of", "num 50"),
        )

    @Test
    fun spans() {
        fun spans(text: String) = lexer.lex(text).map { it.span }
        assertEquals(
            listOf(
                Span(0, 2),
                Span(3, 4),
                Span(5, 6),
                Span(7, 10),
                Span(11, 13),
                Span(14, 17),
            ),
            spans("10 - 5 usd to eur"),
        )
        assertEquals(
            listOf(Span(0, 5), Span(5, 6), Span(7, 11)),
            spans("1 000+ тыс."),
        )
        assertEquals(
            listOf(Span(0, 2), Span(2, 3), Span(4, 5)),
            spans("10k €"),
        )
    }

    @Test
    fun unknownWordIsFailed() {
        assertFailed("10 foo", CalcError.UnknownWord, Span(3, 6))
        assertFailed("15круб", CalcError.UnknownWord, Span(2, 6))
        assertFailed("10 us ", CalcError.UnknownWord, Span(3, 5))
        assertFailed("1 @ 2", CalcError.UnknownWord, Span(2, 3))
    }

    @Test
    fun ambiguousWord() {
        val options = listOf("SEK", "NOK", "DKK").map(::CurrencyCode)
        assertFailed(
            "10 kr + 5",
            CalcError.AmbiguousCurrency(options),
            Span(3, 5),
        )
        assertFailed(
            "10 песо",
            CalcError.AmbiguousCurrency(
                listOf("MXN", "ARS", "CLP", "COP").map(::CurrencyCode)
            ),
            Span(3, 7),
        )
    }

    @Test
    fun endOfInputMayBeExtended() {
        assertEquals(Calculation.Incomplete, stop("100 kr"))
        assertEquals(Calculation.Incomplete, stop("10 us"))
        assertEquals(Calculation.Incomplete, stop("1 0"))
        assertEquals(Calculation.Incomplete, stop("1 00"))
        assertEquals(Calculation.Incomplete, stop("1."))
    }

    @Test
    fun badNumbers() {
        assertFailed("1,000.50", CalcError.BadNumber, Span(0, 8))
        assertFailed("1.000.000 + 1", CalcError.BadNumber, Span(0, 9))
        assertFailed("1_0 + 1", CalcError.BadNumber, Span(0, 3))
    }

    @Test
    fun firstProblemFromTheLeftWins() {
        assertFailed("1,000.50 foo", CalcError.BadNumber, Span(0, 8))
        assertFailsWith<Stop> { lexer.lex("foo 10 kr + 5") }
    }

    @Test
    fun customLexicon() {
        val lexicon = Lexicon.build { currency("BTC", "биткоин") }
        assertEquals(
            listOf("cur BTC"),
            LexiconLexer(lexicon).lex("БИТКОИН").map(::show),
        )
    }

    @Test
    fun conflictingWordsAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            Lexicon.build {
                currency("USD", "dollar")
                currency("CAD", "dollar")
            }
        }
        assertFailsWith<IllegalArgumentException> {
            Lexicon.build {
                to("в")
                of("в")
            }
        }
    }
}
