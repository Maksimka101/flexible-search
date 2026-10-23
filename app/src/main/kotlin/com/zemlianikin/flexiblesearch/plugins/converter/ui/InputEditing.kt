package com.zemlianikin.flexiblesearch.plugins.converter.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.zemlianikin.flexiblesearch.plugins.converter.calc.Calculation
import com.zemlianikin.flexiblesearch.plugins.converter.calc.Calculator
import com.zemlianikin.flexiblesearch.plugins.converter.calc.engine.Lexicon
import com.zemlianikin.flexiblesearch.plugins.converter.core.RateTable

/**
 * Правки текста за пользователя. Каждая реагирует только на один набранный
 * символ (или один backspace): вставка, выделение и правки посреди слова
 * текст не меняют. Не подошло — вернут `new`.
 */
fun editInput(
    old: TextFieldValue,
    new: TextFieldValue,
    calculator: Calculator,
    rates: RateTable,
): TextFieldValue {
    val opened = openParenOnClose(old, new, calculator, rates)
    if (opened !== new) return opened
    val spaced = autoSpace(old, new)
    if (spaced !== new) return spaced
    val erased = eraseSpacedOperator(old, new)
    if (erased !== new) return erased
    return groupDigits(old, new)
}

/**
 * Нажатие кнопки панели: [text] набирается в позицию курсора по одному
 * символу через [editInput], как с клавиатуры, поэтому пробелы и скобки
 * расставляются теми же правилами. Выделение заменяется. Кнопка-слово
 * (`to`) сама отделяется пробелом от слова слева: авто-пробел после слов
 * вроде `rub` (это ещё `ruble`) не ставится, а `rubto` — неизвестное слово.
 */
fun typeInput(
    old: TextFieldValue,
    text: String,
    calculator: Calculator,
    rates: RateTable,
): TextFieldValue {
    var field = old
    val wordBefore =
        old.text.getOrNull(old.selection.min - 1)?.let {
            Lexicon.isWordChar(it)
        } == true
    val typed =
        if (wordBefore && text.firstOrNull()?.isLetter() == true) " $text"
        else text
    for (c in typed) {
        val start = field.selection.min
        val base =
            field.copy(
                text = field.text.removeRange(start, field.selection.max),
                selection = TextRange(start),
                composition = null,
            )
        val typed =
            base.copy(
                text =
                    base.text.substring(0, start) +
                        c +
                        base.text.substring(start),
                selection = TextRange(start + 1),
            )
        field = editInput(base, typed, calculator, rates)
    }
    return field
}

/**
 * Набрана `)` без пары — дописывает в текст настоящую `(` там, где её
 * поставил бы балансировщик. Скобка обычная: её можно стереть, дальше это
 * уже явные скобки пользователя.
 */
fun openParenOnClose(
    old: TextFieldValue,
    new: TextFieldValue,
    calculator: Calculator,
    rates: RateTable,
): TextFieldValue {
    if (typedChar(old, new) != ')') return new
    val parens =
        (calculator.calculate(new.text, rates) as? Calculation.Ok)
            ?.virtualParens
            .orEmpty()
    if (parens.isEmpty()) return new
    val text = StringBuilder(new.text)
    for (p in parens.sortedDescending()) text.insert(p, '(')
    return new.copy(
        text = text.toString(),
        selection = TextRange(new.selection.start + parens.size),
        composition = null,
    )
}

/**
 * Пробелы при наборе:
 * - вокруг бинарного оператора: `10+5` → `10 + 5`. Унарный (`-5`, `(-5)`,
 *   `* -5`) остаётся вплотную. Оператор бинарный, если слева операнд:
 *   цифра, `)`, `%`, слово или знак валюты;
 * - между числом (`)`, `%`) и словом или знаком валюты: `10usd` → `10 usd`;
 * - после целого слова словаря (валюта, `to`, `of`, множитель), если оно
 *   ничем не дорастает: `usd` → `usd `, но не `in` (это ещё `inr`) и не
 *   `руб` (`рубль`);
 * - тот же пробел ставится перед буквой или цифрой, набранной вплотную к
 *   такому слову, если авто-пробел стёрли: `usd|` + `to` → `usd to`, иначе
 *   слова склеятся (`toeur` — неизвестное слово);
 * - пробел, набранный сразу после пробела (в том числе поставленного
 *   автоматически), игнорируется;
 * - `->` («to»): `-` с пробелами при вводе `>` становится `->`.
 */
fun autoSpace(
    old: TextFieldValue,
    new: TextFieldValue,
    lexicon: Lexicon = Lexicon.Default,
): TextFieldValue {
    val c = typedChar(old, new) ?: return new
    val cursor = new.selection.start
    val before = new.text.substring(0, cursor - 1)
    val after = new.text.substring(cursor)
    val prev = before.trimEnd().lastOrNull()
    return when {
        c == '>' && before.endsWith(" - ") ->
            new.withTail(before.dropLast(1) + ">", after)
        c in OPERATORS && prev != null && prev.endsOperand() ->
            new.withTail(
                before +
                    (if (before.last().isWhitespace()) "" else " ") +
                    c,
                after,
            )
        c == ' ' && before.endsWith(" ") ->
            new.copy(
                text = old.text,
                selection = old.selection,
                composition = null,
            )
        c.isLetter() || c.isCurrencySymbol() -> {
            val glued =
                before.lastOrNull()?.let {
                    it.isDigit() || it == ')' || it == '%'
                } == true ||
                    (c.isLetter() &&
                        before.endsWithFinishedWord(after, lexicon))
            (if (glued) new.spacedBefore(before, after) else new)
                .spaceAfterWord(lexicon)
        }
        c.isDigit() && before.endsWithFinishedWord(after, lexicon) ->
            new.spacedBefore(before, after)
        else -> new
    }
}

/**
 * Разряды при наборе: целая часть числа группируется по три пробелом,
 * `1000` → `1 000`, ещё `0` → `10 000`. Реагирует на набранную цифру и на
 * backspace: стёртая цифра перегруппировывает число, backspace по пробелу
 * между группами стирает цифру слева от него (иначе пробел тут же вернулся
 * бы). Дробную часть (после `.` и `,`) и числа с `_`/`'` не трогает. Пробел
 * между цифрами считается частью числа: `5 5` при наборе станет `55`.
 */
fun groupDigits(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
    if (!old.selection.collapsed || !new.selection.collapsed) return new
    val cursor = new.selection.start
    val typed = typedChar(old, new)
    val base: TextFieldValue
    val anchor: Int
    if (typed != null) {
        if (typed !in '0'..'9') return new
        base = new
        anchor = cursor - 1
    } else {
        // Один стёртый символ слева от курсора.
        if (
            old.text.length != new.text.length + 1 ||
                old.selection.start != cursor + 1 ||
                new.text != old.text.removeRange(cursor, cursor + 1)
        )
            return new
        val erased = old.text[cursor]
        val digitAfter =
            old.text.getOrNull(cursor + 1)?.isAsciiDigit() == true
        val digitBefore = cursor > 0 && old.text[cursor - 1].isAsciiDigit()
        base =
            when {
                // Пробел между группами: стираем и цифру перед ним.
                erased == ' ' && digitBefore && digitAfter ->
                    new.copy(
                        text = old.text.removeRange(cursor - 1, cursor + 1),
                        selection = TextRange(cursor - 1),
                        composition = null,
                    )
                // Стёрта единственная цифра первой группы: пробел за ней
                // уже не разделитель.
                erased.isAsciiDigit() &&
                    !digitBefore &&
                    old.text.getOrNull(cursor + 1) == ' ' &&
                    old.text.getOrNull(cursor + 2)?.isAsciiDigit() ==
                        true ->
                    new.copy(
                        text = old.text.removeRange(cursor, cursor + 2),
                        composition = null,
                    )
                erased.isAsciiDigit() -> new
                else -> return new
            }
        val at = base.selection.start
        anchor =
            when {
                at > 0 && base.text[at - 1].isAsciiDigit() -> at - 1
                base.text.getOrNull(at)?.isAsciiDigit() == true -> at
                else -> return base.takeIf { it.text != new.text } ?: new
            }
    }
    val text = base.text
    var start = anchor
    while (
        start > 0 &&
            (text[start - 1].isAsciiDigit() ||
                (text[start - 1] == ' ' &&
                    start >= 2 &&
                    text[start - 2].isAsciiDigit()))
    ) start--
    var end = anchor + 1
    while (
        end < text.length &&
            (text[end].isAsciiDigit() ||
                (text[end] == ' ' &&
                    end + 1 < text.length &&
                    text[end + 1].isAsciiDigit()))
    ) end++
    if (
        start > 0 && text[start - 1] in NUMBER_JOINERS ||
            end < text.length && text[end] in "_'"
    ) {
        return if (base.text == new.text) new else base
    }
    val span = text.substring(start, end)
    val digits = span.filter { it != ' ' }
    val grouped = digits.reversed().chunked(3).joinToString(" ").reversed()
    if (grouped == span) return if (base.text == new.text) new else base
    val left =
        text
            .substring(start, base.selection.start.coerceIn(start, end))
            .count { it != ' ' }
    var pos = 0
    var seen = 0
    while (seen < left) {
        if (grouped[pos] != ' ') seen++
        pos++
    }
    return base.copy(
        text = text.substring(0, start) + grouped + text.substring(end),
        selection = TextRange(start + pos),
        composition = null,
    )
}

private const val NUMBER_JOINERS = ".,_'"

private fun Char.isAsciiDigit() = this in '0'..'9'

/**
 * [this] с пробелом перед только что набранным символом: `before` +
 * пробел + символ + `after`.
 */
private fun TextFieldValue.spacedBefore(
    before: String,
    after: String,
): TextFieldValue =
    copy(
        text = before + " " + text[before.length] + after,
        selection = TextRange(selection.start + 1),
        composition =
            composition?.let {
                TextRange(
                    it.start + if (it.start >= before.length) 1 else 0,
                    it.end + 1,
                )
            },
    )

/**
 * Текст слева заканчивается целым словом словаря, которое не дорастает, а
 * справа слово не продолжается.
 */
private fun String.endsWithFinishedWord(
    after: String,
    lexicon: Lexicon,
): Boolean {
    if (after.firstOrNull()?.let { Lexicon.isWordChar(it) } == true)
        return false
    val word = takeLastWhile { Lexicon.isWordChar(it) }
    // Одиночный `$` — знак перед числом (`$10`), а не слово.
    if (word.none { it.isLetter() }) return false
    val meaning = lexicon.meaning(word)
    return meaning != null &&
        meaning !is Lexicon.Meaning.Ambiguous &&
        !lexicon.canExtend(word)
}

/**
 * Курсор стоит сразу за целым словом, которое дальше не растёт: ставим за
 * ним пробел.
 */
private fun TextFieldValue.spaceAfterWord(
    lexicon: Lexicon
): TextFieldValue {
    val cursor = selection.start
    val after = text.substring(cursor)
    if (
        after.firstOrNull()?.let {
            Lexicon.isWordChar(it) || it == ' ' || it == ')'
        } == true
    )
        return this
    var start = cursor
    while (start > 0 && Lexicon.isWordChar(text[start - 1])) start--
    val word = text.substring(start, cursor)
    // Одиночный `$` — знак перед числом (`$10`), а не слово.
    if (word.none { it.isLetter() }) return this
    val meaning = lexicon.meaning(word)
    if (
        meaning == null ||
            meaning is Lexicon.Meaning.Ambiguous ||
            lexicon.canExtend(word)
    )
        return this
    return copy(
        text = text.substring(0, cursor) + " " + after,
        selection = TextRange(cursor + 1),
        composition = null,
    )
}

/**
 * [head] + пробел после него (если дальше нет пробела или `)`) + [after];
 * курсор — за пробелом.
 */
private fun TextFieldValue.withTail(
    head: String,
    after: String,
): TextFieldValue {
    val space =
        if (after.startsWith(" ") || after.startsWith(")")) "" else " "
    val skip = if (after.startsWith(" ")) 1 else 0
    return copy(
        text = head + space + after,
        selection = TextRange(head.length + space.length + skip),
        composition = null,
    )
}

/**
 * Backspace по пробелу после ` + ` стирает оператор вместе с пробелами:
 * одно нажатие вместо трёх, а следующий оператор снова встанет с пробелами.
 */
fun eraseSpacedOperator(
    old: TextFieldValue,
    new: TextFieldValue,
): TextFieldValue {
    if (!old.selection.collapsed || !new.selection.collapsed) return new
    val at = new.selection.start
    val erased =
        old.text.length == new.text.length + 1 &&
            old.selection.start == at + 1 &&
            new.text == old.text.removeRange(at, at + 1)
    if (
        !erased ||
            at < 2 ||
            old.text[at] != ' ' ||
            old.text[at - 1] !in OPERATORS ||
            old.text[at - 2] != ' '
    )
        return new
    // Справа операнд: оставляем один пробел, чтобы `10 + 5` не склеилось в
    // `105`.
    val glued =
        old.text.getOrNull(at + 1)?.let {
            !it.isWhitespace() && it != ')'
        } == true
    val text =
        if (glued) old.text.removeRange(at - 1, at + 1)
        else old.text.removeRange(at - 2, at + 1)
    return new.copy(
        text = text,
        selection = TextRange(at - 2),
        composition = null,
    )
}

/**
 * Символ, который пользователь только что набрал в позиции курсора
 * (единственная правка текста).
 */
private fun typedChar(old: TextFieldValue, new: TextFieldValue): Char? {
    val cursor = new.selection.start
    if (!old.selection.collapsed || !new.selection.collapsed || cursor <= 0)
        return null
    if (new.text.length != old.text.length + 1) return null
    return new.text[cursor - 1].takeIf {
        new.text.removeRange(cursor - 1, cursor) == old.text
    }
}

private const val OPERATORS = "+-*/×÷−"

/**
 * Слово (валюта, `of`, `to`) считаем операндом: `of -5` получит лишние
 * пробелы, парсер их не замечает.
 */
private fun Char.endsOperand() =
    isLetterOrDigit() || this == ')' || this == '%' || isCurrencySymbol()

private fun Char.isCurrencySymbol() =
    category == CharCategory.CURRENCY_SYMBOL
