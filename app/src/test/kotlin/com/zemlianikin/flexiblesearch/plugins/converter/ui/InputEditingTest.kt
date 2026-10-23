package com.zemlianikin.flexiblesearch.plugins.converter.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.zemlianikin.flexiblesearch.plugins.converter.calc.defaultCalculator
import com.zemlianikin.flexiblesearch.plugins.converter.rates.mockRates
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Поле ввода целиком: набираем символы по одному, как с клавиатуры, и
 * смотрим, что оказалось в поле. В [typing] `|` — курсор (в начале и в
 * результате), `⌫` — backspace.
 */
class InputEditingTest {

    private val calculator = defaultCalculator()
    private val rates = mockRates()

    private fun typing(script: String, from: String = "|"): String {
        var field =
            TextFieldValue(
                from.replace("|", ""),
                TextRange(from.indexOf('|').coerceAtLeast(0)),
            )
        for (key in script) {
            val at = field.selection.start
            val next =
                if (key == '⌫') {
                    if (at == 0) field
                    else
                        TextFieldValue(
                            field.text.removeRange(at - 1, at),
                            TextRange(at - 1),
                        )
                } else {
                    TextFieldValue(
                        field.text.substring(0, at) +
                            key +
                            field.text.substring(at),
                        TextRange(at + 1),
                    )
                }
            field = editInput(field, next, calculator, rates)
        }
        return field.text.substring(0, field.selection.start) +
            "|" +
            field.text.substring(field.selection.start)
    }

    private fun assertTyping(
        script: String,
        expected: String,
        from: String = "|",
    ) = assertEquals(expected, typing(script, from), "набрано: $script")

    @Test
    fun `operators get spaces on both sides`() {
        assertTyping("10+5", "10 + 5|")
        assertTyping("10-5", "10 - 5|")
        assertTyping("10*5", "10 * 5|")
        assertTyping("10/5", "10 / 5|")
        assertTyping("10 + 5", "10 + 5|")
        assertTyping("(1+2)*3", "(1 + 2) * 3|")
        assertTyping("1,5+2.5", "1,5 + 2.5|")
        assertTyping("10%+5", "10% + 5|")
        assertTyping("1 000+1", "1 000 + 1|")
    }

    @Test
    fun `unary sign stays tight`() {
        assertTyping("-5", "-5|")
        assertTyping("(-5)", "(-5)|")
        assertTyping("2*-3", "2 * -3|")
        assertTyping("2+-3", "2 + -3|")
        assertTyping("+5", "+5|")
    }

    @Test
    fun `operator typed in the middle keeps the rest`() {
        assertTyping("+", "10 + |5", from = "10|5")
        assertTyping("+", "10 + |5", from = "10| 5")
        assertTyping("+", "(10 +|)", from = "(10|)")
    }

    @Test
    fun `whole words get a space after them`() {
        assertTyping("10 usd", "10 usd |")
        assertTyping("10 chf", "10 chf |")
        assertTyping("10 usd to", "10 usd to |")
        assertTyping("10 usd to eur", "10 usd to eur |")
        assertTyping("10 eur", "10 eur |")
        assertTyping("10 usd-5", "10 usd - 5|")
    }

    @Test
    fun `words that can still grow get no space`() {
        assertTyping("10 in", "10 in|")
        assertTyping("10 inr", "10 inr |")
        assertTyping("10 kr", "10 kr|")
    }

    @Test
    fun `space between a number and a word or currency sign`() {
        assertTyping("10usd", "10 usd |")
        assertTyping("10USD", "10 USD |")
        assertTyping("10k", "10 k|")
        assertTyping("10\$", "10 \$|")
        assertTyping("10€", "10 €|")
        assertTyping("(1+2)usd", "(1 + 2) usd |")
        assertTyping("10%of", "10% of |")
    }

    @Test
    fun `erased space after a word comes back on the next letter or digit`() {
        assertTyping("10 usd to⌫eur", "10 usd to eur |")
        assertTyping("10 usd⌫to", "10 usd to |")
        assertTyping("10 usd⌫5", "10 usd 5|")
        assertTyping("usd⌫5", "usd 5|")
        // слово ещё растёт или правка посреди слова: ничего не вставляем
        assertTyping("10 inr", "10 inr |")
        assertTyping("5", "us5|d", from = "us|d")
        assertTyping("5", "\$5|", from = "\$|")
    }

    @Test
    fun `currency sign before a number stays tight`() {
        assertTyping("\$10", "\$10|")
        assertTyping("\$10+\$5", "\$10 + \$5|")
    }

    @Test
    fun `typed space after a space is ignored`() {
        assertTyping("10 usd  to  eur", "10 usd to eur |")
        assertTyping("10 + 5", "10 + 5|")
        assertTyping("10+ 5", "10 + 5|")
        assertTyping("10 usd -> eur", "10 usd -> eur |")
    }

    @Test
    fun `arrow is typed as an arrow`() {
        assertTyping("10 usd->", "10 usd -> |")
        assertTyping("10 usd->eur", "10 usd -> eur |")
        assertTyping("10 usd - >", "10 usd -> |")
    }

    @Test
    fun `backspace after a spaced operator erases it whole`() {
        assertTyping("10+⌫", "10|")
        assertTyping("10+⌫-", "10 - |")
        assertTyping("10+5⌫⌫", "10|")
        assertTyping("10+5⌫", "10 + |")
        assertTyping("⌫", "10| 5", from = "10 + |5")
        assertTyping("⌫", "(1|)", from = "(1 + |)")
    }

    @Test
    fun `backspace elsewhere is plain`() {
        assertTyping("⌫", "10 us|", from = "10 usd|")
        assertTyping("⌫", "10 |", from = "10 u|")
    }

    @Test
    fun `unpaired closing paren gets a real opening one`() {
        assertTyping("10+5)", "(10 + 5)|")
        assertTyping("(10+5)", "(10 + 5)|")
        assertTyping("10+5)*2", "(10 + 5) * 2|")
        assertTyping(")", ")|")
        assertTyping("10+)", "10 + )|")
    }

    @Test
    fun `inserted opening paren can be erased`() {
        assertTyping("10+5)⌫", "(10 + 5|")
        assertTyping("⌫", "|10 + 5)", from = "(|10 + 5)")
    }

    private fun pressing(from: String, vararg keys: String): String {
        var field =
            TextFieldValue(
                from.replace("|", ""),
                TextRange(from.indexOf('|').coerceAtLeast(0)),
            )
        for (key in keys) field = typeInput(field, key, calculator, rates)
        return field.text.substring(0, field.selection.start) +
            "|" +
            field.text.substring(field.selection.start)
    }

    @Test
    fun `panel keys go through the typing rules`() {
        assertEquals("10 + |", pressing("10|", "+"))
        assertEquals("10 * -|", pressing("10|", "*", "-"))
        assertEquals("10 usd to |", pressing("10 usd |", "to"))
        assertEquals("10 to |", pressing("10|", "to"))
        assertEquals("(1 + 2)|", pressing("|", "(", "1", "+", "2", ")"))
    }

    @Test
    fun `digits are grouped by three`() {
        assertTyping("100", "100|")
        assertTyping("1000", "1 000|")
        assertTyping("10000", "10 000|")
        assertTyping("1000000", "1 000 000|")
        assertTyping("1 000", "1 000|")
        assertTyping("1000+2000", "1 000 + 2 000|")
        assertTyping("1000usd", "1 000 usd |")
        assertTyping("1000.5", "1 000.5|")
        assertTyping("1000,5000", "1 000,5000|")
        assertTyping("0.12345", "0.12345|")
    }

    @Test
    fun `grouping in the middle keeps the cursor`() {
        assertTyping("5", "10 5|00", from = "1 0|00")
        assertTyping("1", "1| 234", from = "|234")
        assertTyping("9", "9|1 000", from = "|1 000")
    }

    @Test
    fun `backspace regroups the number`() {
        assertTyping("⌫", "100|", from = "1 000|")
        assertTyping("⌫", "1 000|", from = "10 000|")
        assertTyping("⌫⌫", "10|", from = "1 000|")
        assertTyping("⌫", "|000", from = "1 |000")
        assertTyping("⌫", "123|", from = "1 234|")
        assertTyping("⌫", "usd |000", from = "usd 1| 000")
    }
}
