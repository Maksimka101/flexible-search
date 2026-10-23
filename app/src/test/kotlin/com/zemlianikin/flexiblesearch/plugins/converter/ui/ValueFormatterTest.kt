package com.zemlianikin.flexiblesearch.plugins.converter.ui

import com.zemlianikin.flexiblesearch.plugins.converter.calc.Value
import com.zemlianikin.flexiblesearch.plugins.converter.core.CurrencyCode
import com.zemlianikin.flexiblesearch.plugins.converter.core.Decimal
import com.zemlianikin.flexiblesearch.plugins.converter.core.Num
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class ValueFormatterTest {
    private val en = ValueFormatter(Locale.US)

    private fun num(s: String) = Num(Decimal(s))

    private fun money(s: String, code: String) =
        Value.Money(num(s), CurrencyCode(code))

    @Test
    fun `money has two fraction digits and a non-breaking group space`() {
        assertEquals("1 234.50 USD", en.value(money("1234.5", "USD")))
        assertEquals("0.13", en.amount(num("0.125001")))
        assertEquals("90.00 EUR", en.value(money("90", "EUR")))
    }

    @Test
    fun `number drops trailing zeros and keeps up to eight digits`() {
        assertEquals("4", en.value(Value.Number(num("4.000"))))
        assertEquals(
            "0.12345679",
            en.value(Value.Number(num("0.123456789"))),
        )
        assertEquals(
            "1 000 000.5",
            en.value(Value.Number(num("1000000.5"))),
        )
    }

    @Test
    fun `ratio shows the multiplier and the signed percent`() {
        assertEquals("×1.11 (+11%)", en.value(Value.Ratio(num("1.11"))))
        assertEquals("×0.9 (-10%)", en.value(Value.Ratio(num("0.9"))))
        assertEquals("×1 (+0%)", en.value(Value.Ratio(num("1"))))
    }

    @Test
    fun `decimal separator follows the locale`() {
        val ru = ValueFormatter(Locale.forLanguageTag("ru"))
        assertEquals(",", ru.decimalSeparator)
        assertEquals("1 234,50 RUB", ru.value(money("1234.5", "RUB")))
        assertEquals(".", en.decimalSeparator)
    }
}
