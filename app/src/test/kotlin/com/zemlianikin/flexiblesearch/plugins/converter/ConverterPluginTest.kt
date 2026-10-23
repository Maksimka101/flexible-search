package com.zemlianikin.flexiblesearch.plugins.converter

import com.zemlianikin.flexiblesearch.plugins.converter.calc.Calculation
import com.zemlianikin.flexiblesearch.plugins.converter.calc.defaultCalculator
import com.zemlianikin.flexiblesearch.plugins.converter.rates.mockRates
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConverterPluginTest {
    private val calculator = defaultCalculator()
    private val rates = mockRates()

    private fun shown(query: String): Boolean {
        val ok =
            calculator.calculate(query, rates) as? Calculation.Ok
                ?: return false
        return worthShowing(query, ok)
    }

    @Test
    fun examplesAreShown() {
        assertTrue(shown("2+2*2"))
        assertTrue(shown("(1 + 2) * 3"))
        assertTrue(shown("15%"))
        assertTrue(shown("3 x 4"))
        assertTrue(shown("10% of 50"))
        assertTrue(shown("10 usd"))
        assertTrue(shown("10 usd - 10% to eur"))
        assertTrue(shown("eur to usd"))
        assertTrue(shown("4500 rub / 45 usd"))
    }

    @Test
    fun searchesAreNot() {
        assertFalse(shown("42"))
        assertFalse(shown("-5"))
        assertFalse(shown("(5)"))
        assertFalse(shown("5k"))
        assertFalse(shown("usd"))
        assertFalse(shown("telegram"))
        assertFalse(shown("2+"))
        assertFalse(shown("1/0"))
    }
}
