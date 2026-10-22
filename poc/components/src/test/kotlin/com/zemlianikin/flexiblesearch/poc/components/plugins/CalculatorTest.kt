package com.zemlianikin.flexiblesearch.poc.components.plugins

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalculatorTest {
    private fun calc(input: String) =
        Calculator.evaluate(input)?.let(Calculator::format)

    @Test
    fun arithmetic() {
        assertEquals("6", calc("2+2*2"))
        assertEquals("9", calc("(1 + 2) * 3"))
        assertEquals("2.5", calc("10/4"))
        assertEquals("1024", calc("2^10"))
        assertEquals("-4", calc("-2^2"))
        assertEquals("512", calc("2^3^2"))
        assertEquals("3", calc("1,5×2"))
        assertEquals("0.3", calc("0.1+0.2"))
        assertEquals("0.333333333333", calc("1/3"))
        assertEquals("0.15", calc("15%"))
        assertEquals("3", calc("5--2-4"))
    }

    @Test
    fun unclosedParenthesisAtEnd() {
        assertEquals("5", calc("(2+3"))
        assertEquals("10", calc("2*(2+3"))
    }

    @Test
    fun notAnExpression() {
        assertNull(calc(""))
        assertNull(calc("42"))
        assertNull(calc("-5"))
        assertNull(calc("(5)"))
        assertNull(calc("telegram"))
        assertNull(calc("2+"))
        assertNull(calc("1/0"))
        assertNull(calc("(2+3))"))
    }

    @Test
    fun calculatorModeAcceptsPlainNumbers() {
        fun inMode(input: String) =
            Calculator.evaluate(input, requireOperation = false)
                ?.let(Calculator::format)
        assertEquals("42", inMode("42"))
        assertEquals("-5", inMode("−5"))
        assertEquals("1", inMode("3−2"))
        assertNull(inMode(""))
        assertNull(inMode("2+"))
    }

    @Test
    fun largeAndSmallNumbers() {
        assertEquals("1E+20", calc("10^20"))
        assertEquals("0.001", calc("1/1000"))
    }
}
