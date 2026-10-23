package com.zemlianikin.flexiblesearch.plugins.converter.calc.engine

// Серый ящик: лексер по словарю.

import com.zemlianikin.flexiblesearch.plugins.converter.calc.CalcError
import com.zemlianikin.flexiblesearch.plugins.converter.calc.Calculation
import com.zemlianikin.flexiblesearch.plugins.converter.calc.Span
import com.zemlianikin.flexiblesearch.plugins.converter.core.Decimal
import com.zemlianikin.flexiblesearch.plugins.converter.core.Num

/**
 * Текст → токены со span. Бросает первую проблему слева: неизвестное или
 * неоднозначное слово, плохое число, либо Incomplete, если ввод у конца
 * можно дописать до валидного.
 */
class LexiconLexer(private val lexicon: Lexicon = Lexicon.Default) : Lexer {
    override fun lex(text: String): List<Token> = Run(text).lex()

    private inner class Run(private val text: String) {
        private val tokens = ArrayList<Token>()
        private val end = text.length

        fun lex(): List<Token> {
            var i = 0
            while (i < end) {
                val c = text[i]
                i =
                    when {
                        c.isWhitespace() -> i + 1
                        c in '0'..'9' -> number(i)
                        c == '-' && text.startsWith("->", i) ->
                            emit(2, i) { Token.To(it) }
                        c in OPERATORS ->
                            emit(1, i) {
                                Token.Operator(OPERATORS.getValue(c), it)
                            }
                        c == '%' -> emit(1, i) { Token.Percent(it) }
                        c == '(' || c == ')' ->
                            emit(1, i) {
                                Token.Paren(c == '(', synthetic = false, it)
                            }
                        c == '→' -> emit(1, i) { Token.To(it) }
                        c in lexicon.symbols || Lexicon.isWordChar(c) ->
                            word(i)
                        else -> {
                            // Знак вне BMP (эмодзи) занимает два Char —
                            // подсвечиваем целиком.
                            val pair =
                                c.isHighSurrogate() &&
                                    i + 1 < end &&
                                    text[i + 1].isLowSurrogate()
                            fail(
                                CalcError.UnknownWord,
                                i,
                                i + if (pair) 2 else 1,
                            )
                        }
                    }
            }
            return tokens
        }

        private fun emit(
            length: Int,
            start: Int,
            token: (Span) -> Token,
        ): Int {
            tokens += token(Span(start, start + length))
            return start + length
        }

        private fun fail(error: CalcError, start: Int, end: Int): Nothing =
            throw Stop(Calculation.Failed(error, Span(start, end)))

        private fun incomplete(): Nothing =
            throw Stop(Calculation.Incomplete)

        /**
         * Число: целая часть с группами разрядов, затем не больше одной
         * дробной части.
         */
        private fun number(start: Int): Int {
            val digits = StringBuilder()
            var p = digitRun(start, digits)
            // Группировка возможна, только если первая группа 1–3 цифры.
            val groupable = p - start <= 3
            while (p < end && text[p] in GROUP_SEPARATORS) {
                val tail = digitRun(p + 1, null) - (p + 1)
                if (groupable && tail == 3) {
                    p = digitRun(p + 1, digits)
                    continue
                }
                val atEnd = p + 1 + tail == end
                // `1 000` набирают как `1 0`, `1 00`; для пробела это не
                // отличить от `5 5`, поэтому
                // ждём только нули. `_` и `'` числа не разделяют — там ждём
                // любые цифры.
                if (text[p] !in "_'") {
                    if (
                        groupable &&
                            atEnd &&
                            tail in 1..2 &&
                            (1..tail).all { text[p + it] == '0' }
                    )
                        incomplete()
                    break
                }
                if (groupable && atEnd && tail < 3) incomplete()
                fail(CalcError.BadNumber, start, p + 1 + tail)
            }
            if (p < end && text[p] in DECIMAL_SEPARATORS) {
                if (!digitAt(p + 1)) {
                    if (p + 1 == end) incomplete()
                    fail(CalcError.BadNumber, start, p + 1)
                }
                digits.append('.')
                p = digitRun(p + 1, digits)
                // Второй разделитель (`1,000.50`, `1.000.000`): съедаем
                // весь хвост для подчёркивания.
                if (
                    p < end &&
                        text[p] in DECIMAL_SEPARATORS &&
                        digitAt(p + 1)
                ) {
                    while (
                        p < end &&
                            text[p] in DECIMAL_SEPARATORS &&
                            digitAt(p + 1)
                    ) p = digitRun(p + 1, null)
                    fail(CalcError.BadNumber, start, p)
                }
            }
            tokens +=
                Token.Number(
                    Num(Decimal(digits.toString())),
                    Span(start, p),
                )
            return p
        }

        private fun digitAt(p: Int): Boolean =
            p < end && text[p] in '0'..'9'

        /**
         * Конец серии цифр с позиции [from]; сами цифры дописывает в
         * [into].
         */
        private fun digitRun(from: Int, into: StringBuilder?): Int {
            var p = from
            while (digitAt(p)) {
                into?.append(text[p])
                p++
            }
            return p
        }

        /**
         * Слово: максимальная серия букв (и `$`) либо один знак-валюта.
         * Совпадение только целиком.
         */
        private fun word(start: Int): Int {
            var p = start + 1
            if (text[start] !in lexicon.symbols) {
                while (p < end && Lexicon.isWordChar(text[p])) p++
            }
            val span = Span(start, p)
            val word = text.substring(start, p)
            // Слово вплотную к концу ещё может дорасти: `us` → `usd`.
            val growing = p == end && lexicon.canExtend(word)
            return when (val meaning = lexicon.meaning(word)) {
                is Lexicon.Meaning.Currency ->
                    abbreviation(span) { Token.Currency(meaning.code, it) }
                is Lexicon.Meaning.Scale ->
                    abbreviation(span) { Token.Scale(meaning.factor, it) }
                is Lexicon.Meaning.Operator ->
                    emit(p - start, start) {
                        Token.Operator(meaning.operation, it)
                    }
                Lexicon.Meaning.To ->
                    emit(p - start, start) { Token.To(it) }
                Lexicon.Meaning.Of ->
                    emit(p - start, start) { Token.Of(it) }
                // Неоднозначное слово, которое нельзя дописать (`песо`,
                // `¥`), — ошибка сразу:
                // Incomplete обещал бы, что ввод можно починить справа.
                is Lexicon.Meaning.Ambiguous ->
                    if (growing) incomplete()
                    else
                        fail(
                            CalcError.AmbiguousCurrency(meaning.options),
                            start,
                            p,
                        )
                null ->
                    if (growing) incomplete()
                    else fail(CalcError.UnknownWord, start, p)
            }
        }

        /**
         * Сокращение съедает точку после себя: `тыс.`, `руб.`. Точка перед
         * цифрой — начало другого числа.
         */
        private fun abbreviation(span: Span, token: (Span) -> Token): Int {
            var p = span.end
            val dotted =
                text[p - 1].isLetter() &&
                    p < end &&
                    text[p] == '.' &&
                    !digitAt(p + 1)
            if (dotted) p++
            tokens += token(Span(span.start, p))
            return p
        }
    }

    private companion object {
        val OPERATORS =
            mapOf(
                '+' to Operation.Plus,
                '-' to Operation.Minus,
                '−' to Operation.Minus,
                '–' to Operation.Minus,
                '*' to Operation.Multiply,
                '×' to Operation.Multiply,
                '·' to Operation.Multiply,
                '/' to Operation.Divide,
                '÷' to Operation.Divide,
            )
        val GROUP_SEPARATORS = charArrayOf(' ', ' ', ' ', '_', '\'')
        val DECIMAL_SEPARATORS = charArrayOf('.', ',')
    }
}
