package com.zemlianikin.flexiblesearch.plugins.converter.calc

import com.zemlianikin.flexiblesearch.plugins.converter.core.CurrencyCode
import com.zemlianikin.flexiblesearch.plugins.converter.core.Num

/**
 * Символы [start, end) в строке ввода. Пустой span — позиция между
 * символами.
 */
data class Span(val start: Int, val end: Int)

/** Результат разбора и вычисления строки ввода. */
sealed interface Calculation {
    /**
     * virtualParens — позиции серых виртуальных '(' в тексте. currencies —
     * все валюты, упомянутые во вводе (`10 usd to eur` → USD, EUR); для
     * frecency.
     */
    data class Ok(
        val value: Value,
        val virtualParens: List<Int>,
        val currencies: Set<CurrencyCode>,
    ) : Calculation

    /**
     * Ввод можно дописать справа до валидного: `5 +`, `10 us`, `13 usd +
     * 8`.
     */
    data object Incomplete : Calculation

    /** span — что подчеркнуть в тексте. */
    data class Failed(val error: CalcError, val span: Span) : Calculation
}

/** Итоговое значение. Процент наружу не выходит — сворачивается в число. */
sealed interface Value {
    /** 90 usd */
    data class Money(val amount: Num, val currency: CurrencyCode) : Value

    /** 2 + 2 → 4 */
    data class Number(val value: Num) : Value

    /** Валюта ÷ валюта: 4500 rub / 45 usd → ×1.11 (+11%) */
    data class Ratio(val value: Num) : Value
}

/** Причина ошибки. Текст сообщения живёт в ресурсах. */
sealed interface CalcError {
    // Лексер

    /** Слова нет в словаре: `10 foo`. */
    data object UnknownWord : CalcError

    /** `100 kr` — уточните: SEK/NOK/DKK. */
    data class AmbiguousCurrency(val options: List<CurrencyCode>) :
        CalcError

    /** Неверная запись числа: `1,000.50`, `1.000.000`. */
    data object BadNumber : CalcError

    // Парсер

    /** Токен, после которого валидного продолжения нет: `10 + )`. */
    data object UnexpectedToken : CalcError

    /** Два операнда подряд: `5 5`, `10 usd 5`. */
    data object MissingOperator : CalcError

    /** Две валюты у одного числа: `$10 eur`. */
    data object TwoCurrencies : CalcError

    // Вычисление

    /** Голое число и валюта в +/-: `10 + 100 usd`, `(100 usd + 10) * 2`. */
    data object MixedNumberMoney : CalcError

    /** `usd * eur`, `(10 usd)(5 eur)`. */
    data object MoneyTimesMoney : CalcError

    /** Число ÷ валюта: `100 / 10 usd`. */
    data object DivideByMoney : CalcError

    /** Процент от суммы как литерал: `10 usd%`. */
    data object MoneyPercent : CalcError

    /** `1 / 0` */
    data object DivideByZero : CalcError

    /** Отношение нельзя конвертировать: `(4500 rub / 45 usd) to eur`. */
    data object ConvertRatio : CalcError

    /** Валюта есть в справочнике, но курса нет. */
    data object NoRate : CalcError
}
