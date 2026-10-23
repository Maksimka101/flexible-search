package com.zemlianikin.flexiblesearch.plugins.converter.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.zemlianikin.flexiblesearch.plugins.converter.calc.defaultCalculator
import com.zemlianikin.flexiblesearch.plugins.converter.calc.engine.Lexicon
import com.zemlianikin.flexiblesearch.plugins.converter.core.CurrencyCode
import com.zemlianikin.flexiblesearch.plugins.converter.rates.mockRates
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Экранный numpad: backspace, подсказки валют и их подстановка. `|` —
 * курсор.
 */
class NumpadInputTest {

    private val calculator = defaultCalculator()
    private val rates = mockRates()
    private val ranking =
        listOf("EUR", "USD", "RUB", "GBP", "SEK", "NOK", "DKK")
            .map(::CurrencyCode)

    private fun field(s: String) =
        TextFieldValue(s.replace("|", ""), TextRange(s.indexOf('|')))

    private fun show(f: TextFieldValue) =
        f.text.substring(0, f.selection.start) +
            "|" +
            f.text.substring(f.selection.start)

    private fun suggest(s: String): Suggestions {
        val f = field(s)
        return suggestCurrencies(
            f,
            calculator.calculate(f.text, rates),
            ranking,
        )
    }

    private fun codes(s: String) = suggest(s).codes.map { it.code }

    @Test
    fun `backspace deletes one char or the selection`() {
        assertEquals(
            "1|",
            show(deleteInput(field("12|"), calculator, rates)),
        )
        assertEquals("|", show(deleteInput(field("|"), calculator, rates)))
        assertEquals(
            "|12",
            show(deleteInput(field("|12"), calculator, rates)),
        )
        val selected = TextFieldValue("10 usd", TextRange(1, 4))
        assertEquals("1|sd", show(deleteInput(selected, calculator, rates)))
    }

    @Test
    fun `panel key after a word without space gets the space`() {
        assertEquals(
            "10 usd to |",
            show(typeInput(field("10 usd|"), "to", calculator, rates)),
        )
    }

    @Test
    fun `every currency chip is followed by a space and the to key separates from any word`() {
        for (code in Lexicon.Default.currenciesWithPrefix("")) {
            val picked =
                applySuggestion(
                    field("10|"),
                    TextRange(2),
                    code,
                    calculator,
                    rates,
                )
            assertEquals(
                "10 ${code.code.lowercase()} |",
                show(picked),
                "${code.code}: chip",
            )
            assertEquals(
                "10 ${code.code.lowercase()} to |",
                show(typeInput(picked, "to", calculator, rates)),
                "${code.code}: to",
            )
            // без пробела (стёрт): кнопка to сама отделяется
            val glued = field("10 ${code.code.lowercase()}|")
            assertEquals(
                "10 ${code.code.lowercase()} to |",
                show(typeInput(glued, "to", calculator, rates)),
                "${code.code}: glued to",
            )
        }
    }

    @Test
    fun `chip pick keeps the text after the cursor`() {
        val f = field("10|)")
        assertEquals(
            "10 usd|)",
            show(
                applySuggestion(
                    f,
                    TextRange(2),
                    CurrencyCode("USD"),
                    calculator,
                    rates,
                )
            ),
        )
    }

    @Test
    fun `word under cursor suggests currencies by prefix in frecency order`() {
        assertEquals(listOf("USD"), codes("10 us|"))
        assertEquals(listOf("EUR"), codes("10 eu|"))
        assertEquals(listOf("SEK", "NOK", "DKK"), codes("100 kr|"))
    }

    @Test
    fun `nothing to suggest for empty input, non-currency words and an already typed code`() {
        assertEquals(emptyList<String>(), codes("|"))
        assertEquals(emptyList<String>(), codes("10 usd|"))
        assertEquals(emptyList<String>(), codes("10 usd +|"))
        assertEquals(emptyList<String>(), codes("10 usd + |"))
    }

    @Test
    fun `after a number or to the top of frecency is offered`() {
        assertEquals(ranking.take(8).map { it.code }, codes("10|"))
        assertEquals(ranking.take(8).map { it.code }, codes("(10 + 5)|"))
        assertEquals(ranking.take(8).map { it.code }, codes("10 usd to |"))
        assertEquals(ranking.take(8).map { it.code }, codes("10 usd to|"))
    }

    @Test
    fun `picked currency replaces the word and gets spaces`() {
        fun pick(s: String, code: String): String {
            val f = field(s)
            val replace = suggest(s).replace
            return show(
                applySuggestion(
                    f,
                    replace,
                    CurrencyCode(code),
                    calculator,
                    rates,
                )
            )
        }
        assertEquals("10 usd |", pick("10 us|", "USD"))
        assertEquals("10 eur |", pick("10|", "EUR"))
        assertEquals("10 usd to eur |", pick("10 usd to |", "EUR"))
        assertEquals("100 sek |", pick("100 kr|", "SEK"))
        assertEquals("10 usd to eur |", pick("10 usd to|", "EUR"))
    }
}
