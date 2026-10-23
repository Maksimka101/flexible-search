package com.zemlianikin.flexiblesearch.plugins.converter.calc

import com.zemlianikin.flexiblesearch.plugins.converter.core.CurrencyCode
import com.zemlianikin.flexiblesearch.plugins.converter.core.Decimal
import com.zemlianikin.flexiblesearch.plugins.converter.core.Num
import com.zemlianikin.flexiblesearch.plugins.converter.core.RateTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

/** Сквозные тесты: строка → результат, со всеми настоящими этапами. */
class PipelineCalculatorTest {
    private val calc = defaultCalculator()

    // 1 usd = 0.9 eur = 90 rub = 36 thb (цифры условные)
    private val rates =
        RateTable(
            LocalDate(2026, 9, 25),
            mapOf(
                    "USD" to "1",
                    "EUR" to "0.9",
                    "RUB" to "90",
                    "THB" to "36",
                )
                .map { (code, rate) ->
                    CurrencyCode(code) to Num(Decimal(rate))
                }
                .toMap(),
        )

    private fun ok(text: String): Calculation.Ok {
        val result = calc.calculate(text, rates)
        assertTrue(result is Calculation.Ok, "$text → $result")
        return result as Calculation.Ok
    }

    private fun assertNear(expected: String, actual: Num, text: String) {
        val diff = (Decimal(expected) - actual.value).abs()
        assertTrue(
            diff < Decimal("0.000001"),
            "$text: ожидалось $expected, получено ${actual.value}",
        )
    }

    private fun number(text: String, expected: String) {
        val value = ok(text).value
        assertTrue(value is Value.Number, "$text → $value")
        assertNear(expected, (value as Value.Number).value, text)
    }

    private fun money(text: String, expected: String, currency: String) {
        val value = ok(text).value
        assertTrue(value is Value.Money, "$text → $value")
        value as Value.Money
        assertEquals(CurrencyCode(currency), value.currency, text)
        assertNear(expected, value.amount, text)
    }

    private fun failed(text: String): Calculation.Failed {
        val result = calc.calculate(text, rates)
        assertTrue(result is Calculation.Failed, "$text → $result")
        return result as Calculation.Failed
    }

    @Test
    fun арифметика() {
        number("2 + 3 * 4", "14")
        number("(2 + 3) * 4", "20")
        number("-5 + 2", "-3")
        number("7 / 2", "3.5")
        number("1 000 + 1,5", "1001.5")
    }

    @Test
    fun множители() {
        number("10k", "10000")
        number("2 млн", "2000000")
        money("1.5к руб", "1500", "RUB")
    }

    @Test
    fun валюты() {
        money("10 usd", "10", "USD")
        money("\$10", "10", "USD")
        money("100 рублей", "100", "RUB")
        money("eur", "1", "EUR")
        money("10 usd * 3", "30", "USD")
        money("30 usd / 3", "10", "USD")
    }

    @Test
    fun конвертация() {
        money("10 usd to eur", "9", "EUR")
        money("100 usd в рублях", "9000", "RUB")
        money("1200 thb to rub", "3000", "RUB")
        money("100 to eur", "100", "EUR")
    }

    @Test
    fun конвертацияПосредиВыражения() {
        money("10 usd to eur * 10", "90", "EUR")
        money("10 usd to eur * 2 + 1 eur", "19", "EUR")
        money("(10 usd to eur) * 10", "90", "EUR")
    }

    @Test
    fun смешанныеВалюты() {
        money("120 eur - 100 usd", "30", "EUR")
        money("1000 rub + 10 usd + 10 eur", "2900", "RUB")
    }

    @Test
    fun проценты() {
        number("10%", "0.1")
        money("100 usd - 10%", "90", "USD")
        money("10% of 50 usd", "5", "USD")
        money("50 eur + 15%", "57.5", "EUR")
        number("100 + 10% + 10%", "121")
        money("10 usd - 10% to eur", "8.1", "EUR")
    }

    @Test
    fun отношение() {
        val value = ok("4500 rub / 45 usd").value
        assertTrue(value is Value.Ratio)
        assertNear("1.111111", (value as Value.Ratio).value, "ratio")
    }

    @Test
    fun виртуальныеСкобки() {
        number("10 + 20) * 2", "60")
        number("10 - 2) * 3 + 1)", "32")
        number("2 * (3 + 4) + 1) * 5", "75")
        assertEquals(listOf(0), ok("10 + 20)").virtualParens)
        assertEquals(emptyList<Int>(), ok("(10 + 2").virtualParens)
    }

    @Test
    fun автозакрытиеИНеявноеУмножение() {
        number("(10 + 2", "12")
        number("2(3 + 4)", "14")
        number("6/2(1+2)", "9")
        money("(10 + 5) usd", "15", "USD")
    }

    @Test
    fun незаконченныйВвод() {
        assertEquals(Calculation.Incomplete, calc.calculate("5 +", rates))
        assertEquals(
            Calculation.Incomplete,
            calc.calculate("10 usd to", rates),
        )
        assertEquals(Calculation.Incomplete, calc.calculate("10 us", rates))
        // Голое число после суммы в конце ввода ещё можно дописать: `8
        // eur`, `8%`.
        assertEquals(
            Calculation.Incomplete,
            calc.calculate("13 usd + 8", rates),
        )
        assertEquals(
            Calculation.Incomplete,
            calc.calculate("13 usd - 8 ", rates),
        )
        assertEquals(
            Calculation.Incomplete,
            calc.calculate("13 usd + 8 * 2", rates),
        )
        assertEquals(
            Calculation.Incomplete,
            calc.calculate("2 * (13 usd + 8", rates),
        )
    }

    @Test
    fun ошибки() {
        assertEquals(
            CalcError.MixedNumberMoney,
            failed("10 + 100 usd").error,
        )
        assertEquals(
            CalcError.MixedNumberMoney,
            failed("(100 usd + 10) * 2").error,
        )
        assertEquals(
            CalcError.MixedNumberMoney,
            failed("100 usd + 10 to eur").error,
        )
        assertEquals(CalcError.MoneyTimesMoney, failed("usd * eur").error)
        assertEquals(CalcError.DivideByZero, failed("1 / 0").error)
        assertEquals(CalcError.MissingOperator, failed("10 usd 5").error)
        assertEquals(CalcError.TwoCurrencies, failed("\$10 eur").error)
        assertEquals(CalcError.UnknownWord, failed("10 foo bar").error)
        assertEquals(CalcError.UnexpectedToken, failed("10 + )").error)
    }
}
