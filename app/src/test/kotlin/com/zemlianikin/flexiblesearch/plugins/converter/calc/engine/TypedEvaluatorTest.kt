package com.zemlianikin.flexiblesearch.plugins.converter.calc.engine

import com.zemlianikin.flexiblesearch.plugins.converter.calc.CalcError
import com.zemlianikin.flexiblesearch.plugins.converter.calc.Calculation
import com.zemlianikin.flexiblesearch.plugins.converter.calc.Span
import com.zemlianikin.flexiblesearch.plugins.converter.calc.Value
import com.zemlianikin.flexiblesearch.plugins.converter.core.CurrencyCode
import com.zemlianikin.flexiblesearch.plugins.converter.core.Decimal
import com.zemlianikin.flexiblesearch.plugins.converter.core.Num
import com.zemlianikin.flexiblesearch.plugins.converter.core.RateTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.datetime.LocalDate

class TypedEvaluatorTest {
    private val usd = CurrencyCode("USD")
    private val eur = CurrencyCode("EUR")
    private val rub = CurrencyCode("RUB")
    private val xyz = CurrencyCode("XYZ")
    private val rates =
        RateTable(
            LocalDate(2026, 9, 25),
            mapOf(
                usd to Num(Decimal("1")),
                eur to Num(Decimal("0.9")),
                rub to Num(Decimal("90")),
                CurrencyCode("THB") to Num(Decimal("36")),
            ),
        )
    private val evaluator = TypedEvaluator()

    // Узлы собираем руками, span у всех нулевой, кроме тех, где проверяем
    // ошибку.
    private val zero = Span(0, 0)

    private fun n(v: String, span: Span = zero) =
        Node.Number(Num(Decimal(v)), span)

    private fun money(v: String, code: CurrencyCode) =
        Node.WithCurrency(n(v), code, zero)

    private fun pct(v: String) = Node.Percent(n(v), zero)

    private fun bin(l: Node, op: Operation, r: Node, span: Span = zero) =
        Node.Binary(l, op, r, span)

    private fun eval(node: Node) = evaluator.eval(node, rates)

    /**
     * Сравнение до 10 знаков: кросс-курсы считаются с округлением, а
     * Decimal.equals учитывает scale.
     */
    private fun close(v: String) = Decimal(v).rounded(10)

    private fun Num.round() = value.rounded(10)

    private fun assertNumber(expected: String, actual: Value) {
        assertTrue(
            actual is Value.Number,
            "ожидалось Number, вышло $actual",
        )
        assertEquals(
            0,
            close(expected)
                .compareTo((actual as Value.Number).value.round()),
        )
    }

    private fun assertMoney(
        expected: String,
        code: CurrencyCode,
        actual: Value,
    ) {
        assertTrue(actual is Value.Money, "ожидалось Money, вышло $actual")
        actual as Value.Money
        assertEquals(code, actual.currency)
        assertEquals(0, close(expected).compareTo(actual.amount.round()))
    }

    private fun assertFails(error: CalcError, span: Span, node: Node) {
        try {
            eval(node)
            fail("ожидалась ошибка $error")
        } catch (e: Stop) {
            assertEquals(Calculation.Failed(error, span), e.outcome)
        }
    }

    @Test
    fun `2 + 3 умн 4`() =
        assertNumber(
            "14",
            eval(
                bin(
                    n("2"),
                    Operation.Plus,
                    bin(n("3"), Operation.Multiply, n("4")),
                )
            ),
        )

    @Test
    fun `10 usd + 9 eur - в валюте левой части`() =
        assertMoney(
            "20",
            usd,
            eval(bin(money("10", usd), Operation.Plus, money("9", eur))),
        )

    @Test
    fun `120 eur - 100 usd`() =
        assertMoney(
            "30",
            eur,
            eval(
                bin(money("120", eur), Operation.Minus, money("100", usd))
            ),
        )

    @Test
    fun `100 usd + 10 usd без обращения к курсам`() {
        val empty = RateTable(LocalDate(2026, 9, 25), emptyMap())
        val v =
            evaluator.eval(
                bin(money("100", usd), Operation.Plus, money("10", usd)),
                empty,
            )
        assertMoney("110", usd, v)
    }

    @Test
    fun `100 usd - 10 проц`() =
        assertMoney(
            "90",
            usd,
            eval(bin(money("100", usd), Operation.Minus, pct("10"))),
        )

    @Test
    fun `10 + 5 - 10 проц`() {
        val sum = bin(n("10"), Operation.Plus, n("5"))
        assertNumber("13.5", eval(bin(sum, Operation.Minus, pct("10"))))
    }

    @Test
    fun `10 проц of 50 usd`() =
        assertMoney(
            "5",
            usd,
            eval(Node.PercentOf(pct("10"), money("50", usd), zero)),
        )

    @Test
    fun `100 + 10 проц + 10 проц`() {
        val first = bin(n("100"), Operation.Plus, pct("10"))
        assertNumber("121", eval(bin(first, Operation.Plus, pct("10"))))
    }

    @Test
    fun `100 + 10 проц умн 2`() =
        assertNumber(
            "120",
            eval(
                bin(
                    n("100"),
                    Operation.Plus,
                    bin(pct("10"), Operation.Multiply, n("2")),
                )
            ),
        )

    @Test
    fun `100 usd умн 10 проц`() =
        assertMoney(
            "10",
            usd,
            eval(bin(money("100", usd), Operation.Multiply, pct("10"))),
        )

    @Test
    fun `10 usd to eur`() =
        assertMoney(
            "9",
            eur,
            eval(Node.Convert(money("10", usd), eur, zero)),
        )

    @Test
    fun `100 to eur`() =
        assertMoney("100", eur, eval(Node.Convert(n("100"), eur, zero)))

    @Test
    fun `10 проц to eur`() =
        assertMoney("0.1", eur, eval(Node.Convert(pct("10"), eur, zero)))

    @Test
    fun `10 процентов наружу числом`() =
        assertNumber("0.1", eval(pct("10")))

    @Test
    fun `4500 rub div 45 usd - отношение`() =
        assertTrue(
            eval(
                    bin(
                        money("4500", rub),
                        Operation.Divide,
                        money("45", usd),
                    )
                )
                .let {
                    it is Value.Ratio &&
                        it.value.round().compareTo(close("1.1111111111")) ==
                            0
                }
        )

    @Test
    fun `отношение в арифметике становится числом`() {
        val ratio =
            bin(money("90", eur), Operation.Divide, money("100", usd))
        assertNumber("10", eval(bin(ratio, Operation.Multiply, n("10"))))
    }

    @Test
    fun `eur - единица валюты, 30 usd div 3`() {
        assertMoney("1", eur, eval(Node.Currency(eur, zero)))
        assertMoney(
            "10",
            usd,
            eval(bin(money("30", usd), Operation.Divide, n("3"))),
        )
    }

    @Test
    fun `-10 usd`() =
        assertMoney("-10", usd, eval(Node.Negate(money("10", usd), zero)))

    @Test
    fun `10 + 100 usd - ошибка`() {
        val span = Span(0, 12)
        assertFails(
            CalcError.MixedNumberMoney,
            span,
            bin(n("10"), Operation.Plus, money("100", usd), span),
        )
    }

    @Test
    fun `100 usd + 10 в конце ввода - не закончено`() {
        val node =
            bin(
                money("100", usd),
                Operation.Plus,
                n("10", Span(10, 12)),
                Span(0, 12),
            )
        try {
            eval(node)
            fail("ожидался Incomplete")
        } catch (e: Stop) {
            assertEquals(Calculation.Incomplete, e.outcome)
        }
    }

    @Test
    fun `100 usd + 10 не в конце ввода - ошибка`() {
        val inner =
            bin(
                money("100", usd),
                Operation.Plus,
                n("10", Span(10, 12)),
                Span(1, 12),
            )
        assertFails(
            CalcError.MixedNumberMoney,
            Span(1, 12),
            bin(inner, Operation.Multiply, n("2"), Span(0, 17)),
        )
    }

    @Test
    fun `usd умн eur - ошибка`() {
        val span = Span(0, 9)
        assertFails(
            CalcError.MoneyTimesMoney,
            span,
            bin(
                Node.Currency(usd, zero),
                Operation.Multiply,
                Node.Currency(eur, zero),
                span,
            ),
        )
    }

    @Test
    fun `100 div 10 usd - ошибка`() =
        assertFails(
            CalcError.DivideByMoney,
            Span(0, 12),
            bin(n("100"), Operation.Divide, money("10", usd), Span(0, 12)),
        )

    @Test
    fun `1 div 0 - ошибка`() =
        assertFails(
            CalcError.DivideByZero,
            Span(0, 5),
            bin(n("1"), Operation.Divide, n("0"), Span(0, 5)),
        )

    @Test
    fun `10 usd проц - ошибка`() =
        assertFails(
            CalcError.MoneyPercent,
            Span(0, 7),
            Node.Percent(money("10", usd), Span(0, 7)),
        )

    @Test
    fun `нет курса`() {
        val span = Span(0, 10)
        assertFails(
            CalcError.NoRate,
            span,
            Node.Convert(money("10", usd), xyz, span),
        )
        assertFails(
            CalcError.NoRate,
            span,
            bin(money("10", usd), Operation.Plus, money("5", xyz), span),
        )
    }

    @Test
    fun `конвертировать отношение нельзя`() {
        val ratio =
            bin(money("90", eur), Operation.Divide, money("100", usd))
        assertFails(
            CalcError.ConvertRatio,
            Span(0, 20),
            Node.Convert(ratio, eur, Span(0, 20)),
        )
    }
}
