package com.zemlianikin.flexiblesearch.plugins

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import com.zemlianikin.flexiblesearch.core.ActionIcon
import com.zemlianikin.flexiblesearch.core.ActionScope
import com.zemlianikin.flexiblesearch.core.Plugin
import com.zemlianikin.flexiblesearch.core.PluginScope
import com.zemlianikin.flexiblesearch.core.ResultsBuilder
import com.zemlianikin.flexiblesearch.core.SearchBar
import com.zemlianikin.flexiblesearch.core.materialIcon
import java.math.BigDecimal
import java.math.MathContext

/**
 * `2+2*2`, `(1,5+2)/3`, `2^10`, `15%` считаются сразу. Префикс `calc ` или
 * `=` (кнопка в ряду ставит его же) включает нампад и карточку, видную и
 * без выражения. По нажатию или Enter результат копируется.
 */
class CalculatorPlugin : Plugin {
    override val id = "calculator"

    override fun install(scope: PluginScope) {
        val calculator =
            scope.trigger(
                "Calculator",
                "calc",
                "=",
                placeholder = "2+2",
                icon = ActionIcon.Vector(CalculatorIcon),
            )
        scope.override(SearchBar) { props, style, original ->
            val numpad = props.copy(keyboardType = KeyboardType.Number)
            original(
                if (props.trigger == calculator) numpad else props,
                style,
            )
        }
        scope.quickAction(
            "Calculator",
            ActionIcon.Vector(CalculatorIcon),
            isSelected = { it.trigger == calculator },
        ) {
            // Повторное нажатие убирает префикс, набранное остаётся.
            if (query.trigger == calculator) setQuery(query.text)
            else setQuery("calc ${query.text}")
        }
        scope.results { query ->
            val value = Calculator.evaluate(query) ?: return@results
            resultCard(query, value)
        }
        scope.results(calculator) { query ->
            val value = Calculator.evaluate(query, requireOperation = false)
            if (value != null) {
                resultCard(query, value)
            } else {
                card(query.ifBlank { "0" }, "Calculator", id = "result") {}
            }
        }
    }

    // Постоянный id: при наборе карточка не переезжает в конец.
    private fun ResultsBuilder.resultCard(query: String, value: Double) {
        val text = Calculator.format(value)
        card("= $text", "${query.trim()} · tap to copy", id = "result") {
            copy(text)
        }
    }

    private fun ActionScope.copy(text: String) {
        context
            .getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Result", text))
        // С Android 13 система сама показывает, что скопировано.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, "Copied $text", Toast.LENGTH_SHORT)
                .show()
        }
    }
}

// Material Symbols «calculate».
private val CalculatorIcon: ImageVector =
    materialIcon(
        "Calculator",
        "M19,3H5C3.9,3 3,3.9 3,5v14c0,1.1 0.9,2 2,2h14" +
            "c1.1,0 2,-0.9 2,-2V5C21,3.9 20.1,3 19,3z" +
            "M13.03,7.06L14.09,6l1.41,1.41L16.91,6l1.06,1.06" +
            "l-1.41,1.41 1.41,1.41 -1.06,1.06 -1.41,-1.4" +
            "l-1.41,1.41 -1.06,-1.06 1.41,-1.41L13.03,7.06z" +
            "M6.25,7.72h5v1.5h-5V7.72z" +
            "M11.5,16h-2v2H8v-2H6v-1.5h2v-2h1.5v2h2V16z" +
            "M18,17.25h-5v-1.5h5V17.25z" +
            "M18,14.75h-5v-1.5h5V14.75z",
    )

/**
 * Рекурсивный спуск по грамматике:
 * ```
 * sum     = product (("+" | "-") product)*
 * product = unary (("*" | "/") unary)*
 * unary   = "-" unary | power
 * power   = percent ("^" unary)?
 * percent = primary "%"*
 * primary = number | "(" sum ")"
 * ```
 *
 * Минус — `-` или `−` (его ставят числовые клавиатуры), умножение и деление
 * — также `×` и `÷`.
 *
 * Незакрытые скобки в конце допускаются: пока набираешь `(2+3`, результат
 * уже виден.
 */
object Calculator {
    /**
     * null, если выражение не разбирается, результат не число или, при
     * [requireOperation], в нём нет ни одной операции (`42` — не пример).
     */
    fun evaluate(input: String, requireOperation: Boolean = true): Double? {
        val parser = Parser(input.filterNot { it.isWhitespace() })
        val value =
            try {
                parser.parse()
            } catch (_: IllegalArgumentException) {
                return null
            }
        return value.takeIf {
            it.isFinite() && (parser.operations > 0 || !requireOperation)
        }
    }

    fun format(value: Double): String {
        val rounded =
            BigDecimal(value).round(MathContext(12)).stripTrailingZeros()
        val magnitude = rounded.abs()
        val scientific =
            magnitude.signum() != 0 &&
                (magnitude >= BigDecimal("1e15") ||
                    magnitude < BigDecimal("1e-9"))
        return if (scientific) rounded.toString()
        else rounded.toPlainString()
    }

    private class Parser(private val text: String) {
        private var position = 0
        var operations = 0
            private set

        fun parse(): Double {
            require(text.isNotEmpty())
            val value = sum()
            require(position == text.length)
            return value
        }

        private fun sum(): Double {
            var value = product()
            while (true) {
                value =
                    when {
                        accept('+') -> value + product()
                        accept('-', '−') -> value - product()
                        else -> return value
                    }
            }
        }

        private fun product(): Double {
            var value = unary()
            while (true) {
                value =
                    when {
                        accept('*', '×') -> value * unary()
                        accept('/', '÷') -> value / unary()
                        else -> return value
                    }
            }
        }

        // Минус перед числом — не операция: `-5` калькулятор не показывает.
        private fun unary(): Double =
            if (skip('-', '−')) -unary() else power()

        private fun power(): Double {
            val base = percent()
            return if (accept('^')) Math.pow(base, unary()) else base
        }

        private fun percent(): Double {
            var value = primary()
            while (accept('%')) value /= 100
            return value
        }

        private fun primary(): Double {
            if (peek() == '(') {
                position++
                val value = sum()
                if (peek() == ')') position++
                else require(position == text.length)
                return value
            }
            val start = position
            while (
                peek()?.let { it.isDigit() || it == '.' || it == ',' } ==
                    true
            ) position++
            val number = text.substring(start, position).replace(',', '.')
            return requireNotNull(number.toDoubleOrNull())
        }

        private fun peek(): Char? = text.getOrNull(position)

        /** Съесть символ операции, если он следующий. */
        private fun accept(vararg chars: Char): Boolean =
            skip(*chars).also { if (it) operations++ }

        private fun skip(vararg chars: Char): Boolean {
            val next = peek() ?: return false
            if (next !in chars) return false
            position++
            return true
        }
    }
}
