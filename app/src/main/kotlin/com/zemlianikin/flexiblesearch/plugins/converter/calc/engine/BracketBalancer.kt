package com.zemlianikin.flexiblesearch.plugins.converter.calc.engine

import com.zemlianikin.flexiblesearch.plugins.converter.calc.Span

/**
 * Дополняет скобки до парных. R1b: `)` без пары получает синтетическую `(`
 * в начале строки или после предыдущей синтетической `)`. R2: незакрытые
 * `(` закрываются в конце строки. Один проход, O(n). Ничего не бросает:
 * `)`, которую нельзя спарить, остаётся в потоке, ошибку на ней даст
 * парсер.
 *
 * Явная `)` остаётся как есть, синтетическая `(` вставляется перед
 * операндом (точка привязки). Вставки собираем по индексам и применяем
 * вторым проходом, чтобы не сдвигать список.
 */
class BracketBalancer : Balancer {

    /**
     * Что ещё остаётся снаружи новой группы сразу после синтетически
     * закрытой `)`.
     */
    private enum class Tail {
        Postfix,
        Done,
    }

    override fun balance(tokens: List<Token>): Balanced {
        val openBefore = BooleanArray(tokens.size)
        var depth = 0
        var anchor =
            0 // индекс первого токена сегмента: сюда встанет синтетическая
        // '('
        var tail = Tail.Done

        for ((i, token) in tokens.withIndex()) {
            if (depth > 0) {
                if (token.isOpen()) depth++
                else if (token.isClose()) depth--
                continue
            }
            when {
                token.isOpen() -> {
                    depth++
                    tail = Tail.Done
                }
                token.isClose() -> {
                    // Непарная `)`: пустой кусок или кусок на операторе
                    // пары не получает. Такая `)`
                    // остаётся барьером: следующий сегмент начинается после
                    // неё.
                    val paired =
                        i > anchor && !tokens[i - 1].expectsOperand()
                    if (paired) openBefore[anchor] = true
                    anchor = i + 1
                    tail = if (paired) Tail.Postfix else Tail.Done
                }
                tail == Tail.Postfix ->
                    when {
                        // `%` и валюта — суффиксы закрытой группы, оператор
                        // — её связка со следующей.
                        token is Token.Percent || token is Token.Currency ->
                            anchor = i + 1
                        token.isInfix() -> {
                            anchor = i + 1
                            tail = Tail.Done
                        }
                        else -> tail = Tail.Done
                    }
            }
        }

        val out = ArrayList<Token>(tokens.size + depth)
        val virtualParens = ArrayList<Int>()
        for ((i, token) in tokens.withIndex()) {
            if (openBefore[i]) {
                val p = token.span.start
                out +=
                    Token.Paren(
                        open = true,
                        synthetic = true,
                        span = Span(p, p),
                    )
                virtualParens += p
            }
            out += token
        }
        // R2: незакрытые `(` закрываются в конце строки.
        if (depth > 0) {
            val p = tokens.last().span.end
            repeat(depth) {
                out +=
                    Token.Paren(
                        open = false,
                        synthetic = true,
                        span = Span(p, p),
                    )
            }
        }
        return Balanced(out, virtualParens)
    }
}

// Классы токенов: инфиксный (`+ - * / of`), постфиксный (`%`, валюта), всё
// остальное — операнд.

private fun Token.isOpen() = this is Token.Paren && open

private fun Token.isClose() = this is Token.Paren && !open

/** Инфиксный оператор: `+ - * /` и `of`. */
private fun Token.isInfix() = this is Token.Operator || this is Token.Of

/**
 * После токена обязательно нужен правый операнд: инфиксный оператор или
 * `to`.
 */
private fun Token.expectsOperand() = isInfix() || this is Token.To
