package com.zemlianikin.flexiblesearch.plugins.converter.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.zemlianikin.flexiblesearch.plugins.converter.calc.CalcError
import com.zemlianikin.flexiblesearch.plugins.converter.calc.Calculation
import com.zemlianikin.flexiblesearch.plugins.converter.calc.Calculator
import com.zemlianikin.flexiblesearch.plugins.converter.calc.engine.Lexicon
import com.zemlianikin.flexiblesearch.plugins.converter.core.CurrencyCode
import com.zemlianikin.flexiblesearch.plugins.converter.core.RateTable

/**
 * Backspace экранного numpad: удаляет выделение или символ слева от курсора
 * и пропускает правку через [editInput], как настоящий backspace (стирание
 * ` + ` целиком и т.п.).
 */
fun deleteInput(
    old: TextFieldValue,
    calculator: Calculator,
    rates: RateTable,
): TextFieldValue {
    val sel = old.selection
    val from = if (sel.collapsed) sel.start - 1 else sel.min
    if (from < 0) return old
    val to = if (sel.collapsed) sel.start else sel.max
    val next =
        old.copy(
            text = old.text.removeRange(from, to),
            selection = TextRange(from),
            composition = null,
        )
    return editInput(old, next, calculator, rates)
}

/** Сколько чипов валют в ряду над numpad. */
const val CHIP_LIMIT = 8

/**
 * Чипы валют: [codes] в порядке показа и [replace] — что в тексте заменит
 * выбранная валюта.
 */
data class Suggestions(
    val codes: List<CurrencyCode>,
    val replace: TextRange,
) {
    companion object {
        val None = Suggestions(emptyList(), TextRange.Zero)
    }
}

/**
 * Подсказки валют для режима numpad, где букв нет. По приоритету:
 * 1. слово под курсором — валюты с таким началом (`us` → USD), по frecency;
 * 2. ошибка «уточните валюту» (`kr`) при курсоре вне слова — её варианты
 *    вместо слова с ошибкой;
 * 3. курсор после числа, `)`, `%` или `to` (с пробелом или вплотную) — топ
 *    [limit] по frecency. Пусто, если курсор выделяет диапазон, ввод пуст
 *    или подошло бы только слово, которое уже набрано.
 */
fun suggestCurrencies(
    input: TextFieldValue,
    result: Calculation,
    ranking: List<CurrencyCode>,
    lexicon: Lexicon = Lexicon.Default,
    limit: Int = CHIP_LIMIT,
): Suggestions {
    if (!input.selection.collapsed) return Suggestions.None
    val text = input.text
    val cursor = input.selection.start
    fun byRank(codes: List<CurrencyCode>) =
        codes
            .sortedBy {
                ranking.indexOf(it).let { i ->
                    if (i < 0) Int.MAX_VALUE else i
                }
            }
            .take(limit)

    var start = cursor
    while (start > 0 && Lexicon.isWordChar(text[start - 1])) start--
    var end = cursor
    while (end < text.length && Lexicon.isWordChar(text[end])) end++

    if (start < cursor) {
        val whole = lexicon.meaning(text.substring(start, end))
        // Целое неоднозначное слово (`kr`) — только его варианты, а не все
        // валюты на `kr…` (krone).
        if (whole is Lexicon.Meaning.Ambiguous)
            return Suggestions(byRank(whole.options), TextRange(start, end))
        val codes =
            lexicon.currenciesWithPrefix(text.substring(start, cursor))
        if (
            codes.size == 1 &&
                codes.single() == (whole as? Lexicon.Meaning.Currency)?.code
        )
            return Suggestions.None
        if (codes.isNotEmpty())
            return Suggestions(byRank(codes), TextRange(start, end))
        // Не валюта (`to` без пробела после стёртого авто-пробела): смотрим
        // ниже, что предложить после слова.
    }

    val failed = result as? Calculation.Failed
    val ambiguous = failed?.error as? CalcError.AmbiguousCurrency
    if (failed != null && ambiguous != null) {
        val span =
            TextRange(
                failed.span.start.coerceIn(0, text.length),
                failed.span.end.coerceIn(0, text.length),
            )
        return Suggestions(byRank(ambiguous.options), span)
    }

    val before = text.substring(0, cursor).trimEnd()
    val afterOperand =
        before.isNotEmpty() &&
            (before.last().isDigit() || before.last() in ")%")
    val lastWord = before.takeLastWhile(Lexicon::isWordChar)
    val afterTo =
        before.endsWith("->") ||
            (lastWord.isNotEmpty() &&
                lexicon.meaning(lastWord) == Lexicon.Meaning.To)
    if (afterOperand || afterTo)
        return Suggestions(ranking.take(limit), TextRange(cursor))
    return Suggestions.None
}

/**
 * Что заменит валюта, выбранная кнопкой «+», а не с чипа: подсказанное
 * слово, иначе выделение или позиция курсора.
 */
fun pickRange(input: TextFieldValue, suggestions: Suggestions): TextRange =
    if (suggestions.codes.isNotEmpty()) suggestions.replace
    else input.selection

/**
 * Подставляет [code] вместо [replace] и набирает его как с клавиатуры:
 * пробелы расставляются теми же правилами. Пробел после кода ставится
 * всегда, в том числе для кодов, которые дорастают до другого слова (`rub`
 * → `ruble`): выбор чипа — законченное слово, продолжать его не будут.
 */
fun applySuggestion(
    old: TextFieldValue,
    replace: TextRange,
    code: CurrencyCode,
    calculator: Calculator,
    rates: RateTable,
): TextFieldValue {
    val base =
        old.copy(
            text = old.text.removeRange(replace.min, replace.max),
            selection = TextRange(replace.min),
            composition = null,
        )
    val typed = typeInput(base, code.code.lowercase(), calculator, rates)
    val at = typed.selection.start
    val next = typed.text.getOrNull(at)
    return when {
        typed.text.getOrNull(at - 1) == ' ' || next == ')' -> typed
        next == ' ' -> typed.copy(selection = TextRange(at + 1))
        else ->
            typed.copy(
                text =
                    typed.text.substring(0, at) +
                        " " +
                        typed.text.substring(at),
                selection = TextRange(at + 1),
            )
    }
}
