package com.zemlianikin.flexiblesearch.plugins.converter.rates

import com.zemlianikin.flexiblesearch.plugins.converter.core.CurrencyCode
import com.zemlianikin.flexiblesearch.plugins.converter.core.Decimal
import com.zemlianikin.flexiblesearch.plugins.converter.core.Num
import kotlinx.datetime.LocalDate

/** Базовая валюта источника: в JSON курсы лежат в объекте `usd`. */
private val BASE = CurrencyCode("USD")

/**
 * Глубже мы не идём при пропуске неизвестных полей: защита от переполнения
 * стека на мусоре.
 */
private const val MAX_DEPTH = 64

/**
 * Разбирает ответ fawazahmed0/exchange-api (`v1/currencies/usd.json`):
 * `{"date":"2026-09-25","usd":{"eur":0.86,"rub":82.5,"btc":8.7e-06,...}}`.
 *
 * Значения читаются в [Decimal] прямо из текста числа, без промежуточного
 * Double. Коды приводятся к верхнему регистру; код не из `[A-Za-z0-9]`,
 * значение не число или `<= 0` — запись пропускается. Базы (USD) в списке
 * может не быть — тогда она добавляется с курсом 1. Неизвестные поля любой
 * вложенности пропускаются.
 *
 * @throws IllegalArgumentException битый JSON, нет `date` или `usd`,
 *   неверная дата.
 */
fun parseRates(json: String): RatesSnapshot {
    val parser = JsonReader(json)
    var date: LocalDate? = null
    var rates: Map<CurrencyCode, Num>? = null

    parser.skipWs()
    parser.expect('{')
    parser.skipWs()
    if (!parser.consumeIf('}')) {
        while (true) {
            parser.skipWs()
            when (parser.readString()) {
                "date" -> {
                    parser.skipWs()
                    parser.expect(':')
                    parser.skipWs()
                    date = parseDate(parser.readString())
                }
                "usd" -> {
                    parser.skipWs()
                    parser.expect(':')
                    parser.skipWs()
                    rates = readRates(parser)
                }
                else -> {
                    parser.skipWs()
                    parser.expect(':')
                    parser.skipWs()
                    parser.skipValue(0)
                }
            }
            parser.skipWs()
            if (parser.consumeIf(',')) continue
            parser.expect('}')
            break
        }
    }
    parser.skipWs()
    if (!parser.atEnd()) parser.fail("лишние данные после JSON")

    requireNotNull(date) { "В ответе нет поля date" }
    requireNotNull(rates) { "В ответе нет поля usd" }

    val perBase =
        if (BASE in rates) {
            rates
        } else {
            LinkedHashMap<CurrencyCode, Num>().apply {
                put(BASE, Num(Decimal.ONE))
                putAll(rates)
            }
        }
    return RatesSnapshot(date, perBase)
}

private fun parseDate(text: String): LocalDate =
    try {
        LocalDate.parse(text)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException(
            "Неверная дата в поле date: \"$text\"",
            e,
        )
    }

/**
 * Читает объект `usd`: `{"eur":0.86,...}`. Мусорные записи молча
 * пропускает.
 */
private fun readRates(p: JsonReader): Map<CurrencyCode, Num> {
    val result = LinkedHashMap<CurrencyCode, Num>()
    p.expect('{')
    p.skipWs()
    if (p.consumeIf('}')) return result
    while (true) {
        p.skipWs()
        val key = p.readString()
        p.skipWs()
        p.expect(':')
        p.skipWs()
        val number =
            if (p.peekIsNumberStart()) p.readNumberToken() else null
        if (number == null) p.skipValue(1)
        val value = number?.let(Decimal::parseOrNull)
        if (
            value != null &&
                value.signum() > 0 &&
                key.isNotEmpty() &&
                key.all(::isCodeChar)
        ) {
            result[CurrencyCode(key.uppercase())] = Num(value)
        }
        p.skipWs()
        if (p.consumeIf(',')) continue
        p.expect('}')
        return result
    }
}

private fun isCodeChar(c: Char) =
    c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9'

/**
 * Минимальный читатель JSON: ровно столько, сколько нужно, чтобы прочитать
 * нужные поля и пропустить остальные.
 */
private class JsonReader(private val s: String) {
    private var pos = 0

    fun atEnd() = pos >= s.length

    fun fail(message: String): Nothing =
        throw IllegalArgumentException(
            "Некорректный JSON: $message (позиция $pos)"
        )

    fun skipWs() {
        while (
            pos < s.length &&
                (s[pos] == ' ' ||
                    s[pos] == '\n' ||
                    s[pos] == '\r' ||
                    s[pos] == '\t')
        ) pos++
    }

    fun consumeIf(c: Char): Boolean {
        if (pos < s.length && s[pos] == c) {
            pos++
            return true
        }
        return false
    }

    fun expect(c: Char) {
        if (!consumeIf(c))
            fail(
                if (atEnd()) "ожидался '$c', конец данных"
                else "ожидался '$c', найден '${s[pos]}'"
            )
    }

    fun peekIsNumberStart() =
        pos < s.length && (s[pos] == '-' || s[pos] in '0'..'9')

    /** Читает строку в кавычках с escape-последовательностями. */
    fun readString(): String {
        expect('"')
        val sb = StringBuilder()
        while (true) {
            if (pos >= s.length) fail("не закрыта строка")
            val c = s[pos++]
            when (c) {
                '"' -> return sb.toString()
                '\\' -> {
                    if (pos >= s.length)
                        fail("оборвана escape-последовательность")
                    when (val e = s[pos++]) {
                        '"',
                        '\\',
                        '/' -> sb.append(e)
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 > s.length)
                                fail(
                                    "оборвана escape-последовательность \\u"
                                )
                            val code =
                                s.substring(pos, pos + 4).toIntOrNull(16)
                                    ?: fail("неверный \\u-код")
                            sb.append(code.toChar())
                            pos += 4
                        }
                        else -> fail("неизвестный escape '\\$e'")
                    }
                }
                else -> sb.append(c)
            }
        }
    }

    /** Читает число по грамматике JSON и возвращает его исходный текст. */
    fun readNumberToken(): String {
        val start = pos
        consumeIf('-')
        if (consumeIf('0')) {
            // ведущий ноль: дальше только дробь или экспонента
        } else if (pos < s.length && s[pos] in '1'..'9') {
            digits()
        } else fail("ожидалась цифра")
        if (consumeIf('.')) {
            if (digits() == 0) fail("нет цифр после точки")
        }
        if (pos < s.length && (s[pos] == 'e' || s[pos] == 'E')) {
            pos++
            if (pos < s.length && (s[pos] == '+' || s[pos] == '-')) pos++
            if (digits() == 0) fail("нет цифр в экспоненте")
        }
        return s.substring(start, pos)
    }

    private fun digits(): Int {
        val start = pos
        while (pos < s.length && s[pos] in '0'..'9') pos++
        return pos - start
    }

    /**
     * Пропускает любое значение: строку, число, true/false/null, объект,
     * массив.
     */
    fun skipValue(depth: Int) {
        if (depth > MAX_DEPTH) fail("слишком глубокая вложенность")
        if (atEnd()) fail("ожидалось значение, конец данных")
        when (s[pos]) {
            '"' -> readString()
            '{' -> {
                pos++
                skipWs()
                if (consumeIf('}')) return
                while (true) {
                    skipWs()
                    readString()
                    skipWs()
                    expect(':')
                    skipWs()
                    skipValue(depth + 1)
                    skipWs()
                    if (consumeIf(',')) continue
                    expect('}')
                    return
                }
            }
            '[' -> {
                pos++
                skipWs()
                if (consumeIf(']')) return
                while (true) {
                    skipWs()
                    skipValue(depth + 1)
                    skipWs()
                    if (consumeIf(',')) continue
                    expect(']')
                    return
                }
            }
            't' -> literal("true")
            'f' -> literal("false")
            'n' -> literal("null")
            else ->
                if (peekIsNumberStart()) readNumberToken()
                else fail("неожиданный символ '${s[pos]}'")
        }
    }

    private fun literal(word: String) {
        if (!s.startsWith(word, pos)) fail("ожидалось $word")
        pos += word.length
    }
}
