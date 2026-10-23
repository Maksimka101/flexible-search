package com.zemlianikin.flexiblesearch.plugins.converter.calc.engine

import com.zemlianikin.flexiblesearch.plugins.converter.calc.CalcError
import com.zemlianikin.flexiblesearch.plugins.converter.calc.Calculation
import com.zemlianikin.flexiblesearch.plugins.converter.calc.Span
import com.zemlianikin.flexiblesearch.plugins.converter.core.CurrencyCode
import com.zemlianikin.flexiblesearch.plugins.converter.core.Decimal
import com.zemlianikin.flexiblesearch.plugins.converter.core.Num
import kotlin.test.Test
import kotlin.test.assertEquals

class RecursiveDescentParserTest {
    private val parser = RecursiveDescentParser()

    /**
     * Токены через пробел, как после лексера и балансировщика; [Src.tight]
     * — вплотную к предыдущему.
     */
    private class Src {
        private var at = 0
        val tokens = mutableListOf<Token>()

        private fun add(width: Int, make: (Span) -> Token) {
            tokens += make(Span(at, at + width))
            at += width + if (width > 0) 1 else 0
        }

        fun tight() {
            at--
        }

        fun n(value: Int) = n(value.toString())

        fun n(text: String) =
            add(text.length) { Token.Number(Num(Decimal(text)), it) }

        fun scale(factor: Int) =
            add(1) { Token.Scale(Num(Decimal.of(factor.toLong())), it) }

        fun cur(code: String, text: String = code) =
            add(text.length) {
                Token.Currency(CurrencyCode(code.uppercase()), it)
            }

        fun plus() = add(1) { Token.Operator(Operation.Plus, it) }

        fun minus() = add(1) { Token.Operator(Operation.Minus, it) }

        fun times() = add(1) { Token.Operator(Operation.Multiply, it) }

        fun div() = add(1) { Token.Operator(Operation.Divide, it) }

        fun pct() = add(1) { Token.Percent(it) }

        fun of() = add(2) { Token.Of(it) }

        fun to() = add(2) { Token.To(it) }

        fun lp() =
            add(1) {
                Token.Paren(open = true, synthetic = false, span = it)
            }

        fun rp() =
            add(1) {
                Token.Paren(open = false, synthetic = false, span = it)
            }

        fun slp() =
            add(0) { Token.Paren(open = true, synthetic = true, span = it) }

        fun srp() =
            add(0) {
                Token.Paren(open = false, synthetic = true, span = it)
            }
    }

    private fun tokens(build: Src.() -> Unit) = Src().apply(build).tokens

    /**
     * Дерево в виде S-выражения без spans: сравнивать структуру проще, чем
     * собирать узлы.
     */
    private fun shape(node: Node): String =
        when (node) {
            is Node.Number ->
                node.value.value.stripTrailingZeros().toPlainString()
            is Node.Currency -> node.code.code.lowercase()
            is Node.WithCurrency ->
                "(with ${shape(node.inner)} ${node.code.code.lowercase()})"
            is Node.Percent -> "(% ${shape(node.inner)})"
            is Node.Negate -> "(neg ${shape(node.inner)})"
            is Node.Binary ->
                "(${symbol(node.operation)} ${shape(node.left)} ${shape(node.right)})"
            is Node.PercentOf ->
                "(of ${shape(node.percent)} ${shape(node.base)})"
            is Node.Convert ->
                "(to ${shape(node.inner)} ${node.target.code.lowercase()})"
        }

    private fun symbol(op: Operation) =
        when (op) {
            Operation.Plus -> "+"
            Operation.Minus -> "-"
            Operation.Multiply -> "*"
            Operation.Divide -> "/"
        }

    private fun check(expected: String, build: Src.() -> Unit) =
        assertEquals(expected, shape(parser.parse(tokens(build))))

    private fun stop(build: Src.() -> Unit): Calculation {
        try {
            parser.parse(tokens(build))
        } catch (e: Stop) {
            return e.outcome
        }
        throw AssertionError("Stop не брошен")
    }

    private fun failed(error: CalcError, span: Span) =
        Calculation.Failed(error, span)

    @Test
    fun `priority of multiplication`() =
        check("(+ 2 (* 3 4))") {
            n(2)
            plus()
            n(3)
            times()
            n(4)
        }

    @Test
    fun `left associativity`() =
        check("(- (- 10 3) 2)") {
            n(10)
            minus()
            n(3)
            minus()
            n(2)
        }

    @Test
    fun `group overrides priority and span covers brackets`() {
        val src = tokens {
            lp()
            n(2)
            plus()
            n(3)
            rp()
            times()
            n(4)
        }
        val node = parser.parse(src)
        assertEquals("(* (+ 2 3) 4)", shape(node))
        assertEquals(Span(0, src.last().span.end), node.span)
    }

    @Test
    fun `unary minus folds into literal`() =
        check("(+ -5 2)") {
            minus()
            n(5)
            plus()
            n(2)
        }

    @Test
    fun `unary minus before group and percent stays Negate`() {
        check("(neg (+ 2 3))") {
            minus()
            lp()
            n(2)
            plus()
            n(3)
            rp()
        }
        check("(neg (% 10))") {
            minus()
            n(10)
            pct()
        }
    }

    @Test
    fun `binary minus after currency is subtraction`() =
        check("(- usd 5)") {
            cur("usd")
            minus()
            n(5)
        }

    @Test
    fun `currency after number`() =
        check("(with 10 usd)") {
            n(10)
            cur("usd")
        }

    @Test
    fun `currency before number`() {
        check("(with 10 usd)") {
            cur("usd", "$")
            tight()
            n(10)
        }
        check("(with 10 usd)") {
            cur("usd")
            n(10)
        }
    }

    @Test
    fun `sign glued to currency folds into number`() {
        check("(with -10 usd)") {
            cur("usd", "$")
            tight()
            minus()
            tight()
            n(10)
        }
        check("(with -10 usd)") {
            minus()
            n(10)
            cur("usd")
        }
    }

    @Test fun `bare currency`() = check("eur") { cur("eur") }

    @Test
    fun `scale multiplies number and extends span`() {
        val node =
            parser.parse(
                tokens {
                    n("1.5")
                    scale(1000)
                }
            )
        assertEquals(
            0,
            Decimal("1500").compareTo((node as Node.Number).value.value),
        )
        assertEquals(Span(0, 5), node.span)
    }

    @Test
    fun `percent adjusts the sum`() =
        check("(- 100 (% 10))") {
            n(100)
            minus()
            n(10)
            pct()
        }

    @Test
    fun `percent of`() =
        check("(of (% 10) (with 50 usd))") {
            n(10)
            pct()
            of()
            n(50)
            cur("usd")
        }

    @Test
    fun `convert`() =
        check("(to (with 10 usd) eur)") {
            n(10)
            cur("usd")
            to()
            cur("eur")
        }

    @Test
    fun `convert at the end of a group`() =
        check("(+ (to (with 10 usd) eur) (with 5 usd))") {
            lp()
            n(10)
            cur("usd")
            to()
            cur("eur")
            rp()
            plus()
            n(5)
            cur("usd")
        }

    @Test
    fun `convert in the middle continues as an operand`() {
        check("(* (to (with 10 usd) eur) 10)") {
            n(10)
            cur("usd")
            to()
            cur("eur")
            times()
            n(10)
        }
        check("(+ (* (to (with 10 usd) eur) 2) (with 1 eur))") {
            n(10)
            cur("usd")
            to()
            cur("eur")
            times()
            n(2)
            plus()
            n(1)
            cur("eur")
        }
        check("(to (- (with 10 usd) (% 10)) eur)") {
            n(10)
            cur("usd")
            minus()
            n(10)
            pct()
            to()
            cur("eur")
        }
    }

    @Test
    fun `implicit multiplication`() {
        check("(* 2 (+ 3 4))") {
            n(2)
            lp()
            n(3)
            plus()
            n(4)
            rp()
        }
        check("(* (/ 6 2) (+ 1 2))") {
            n(6)
            div()
            n(2)
            lp()
            n(1)
            plus()
            n(2)
            rp()
        }
        check("(* 2 3)") {
            lp()
            n(2)
            rp()
            n(3)
        }
        check("(- 2 3)") {
            lp()
            n(2)
            rp()
            minus()
            n(3)
        }
    }

    @Test
    fun `currency after group is a suffix`() =
        check("(with (+ 10 5) usd)") {
            lp()
            n(10)
            plus()
            n(5)
            rp()
            cur("usd")
        }

    @Test
    fun `synthetic brackets parse like explicit ones`() {
        check("(* (- 10 2) 3)") {
            slp()
            n(10)
            minus()
            n(2)
            rp()
            times()
            n(3)
        }
        check("(+ 2 3)") {
            lp()
            n(2)
            plus()
            n(3)
            srp()
        }
    }

    @Test
    fun `two operands in a row`() {
        assertEquals(
            failed(CalcError.MissingOperator, Span(2, 3)),
            stop {
                n(5)
                n(5)
            },
        )
        assertEquals(
            failed(CalcError.MissingOperator, Span(7, 8)),
            stop {
                n(10)
                cur("usd")
                n(5)
            },
        )
    }

    @Test
    fun `two currencies at one number`() =
        assertEquals(
            failed(CalcError.TwoCurrencies, Span(4, 7)),
            stop {
                cur("usd", "$")
                tight()
                n(10)
                cur("eur")
            },
        )

    @Test
    fun `unexpected tokens`() {
        assertEquals(
            failed(CalcError.UnexpectedToken, Span(5, 6)),
            stop {
                n(10)
                plus()
                times()
                n(5)
            },
        )
        assertEquals(
            failed(CalcError.UnexpectedToken, Span(5, 6)),
            stop {
                n(10)
                plus()
                rp()
            },
        )
        assertEquals(
            failed(CalcError.UnexpectedToken, Span(0, 1)),
            stop { scale(1000) },
        )
        assertEquals(
            failed(CalcError.UnexpectedToken, Span(2, 4)),
            stop {
                n(5)
                of()
                n(1)
            },
        )
        assertEquals(
            failed(CalcError.UnexpectedToken, Span(6, 7)),
            stop {
                n(10)
                to()
                n(5)
            },
        )
    }

    @Test
    fun `input that can be continued is incomplete`() {
        assertEquals(Calculation.Incomplete, stop {})
        assertEquals(
            Calculation.Incomplete,
            stop {
                n(5)
                plus()
            },
        )
        assertEquals(
            Calculation.Incomplete,
            stop {
                n(5)
                plus()
                lp()
                srp()
            },
        )
        assertEquals(
            Calculation.Incomplete,
            stop {
                n(5)
                cur("usd")
                times()
            },
        )
        assertEquals(
            Calculation.Incomplete,
            stop {
                n(10)
                cur("usd")
                to()
            },
        )
        assertEquals(
            Calculation.Incomplete,
            stop {
                n(10)
                pct()
                of()
            },
        )
    }
}
