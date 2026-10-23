package com.zemlianikin.flexiblesearch.plugins.converter.calc

import com.zemlianikin.flexiblesearch.plugins.converter.core.RateTable

/**
 * Строка ввода → результат. Чистая функция: без состояния и ввода-вывода.
 * UI вызывает заново при каждом изменении текста или курсов.
 */
interface Calculator {
    fun calculate(text: String, rates: RateTable): Calculation
}
