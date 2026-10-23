package com.zemlianikin.flexiblesearch.plugins.converter.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.zemlianikin.flexiblesearch.plugins.converter.calc.Calculation
import com.zemlianikin.flexiblesearch.plugins.converter.calc.Calculator
import com.zemlianikin.flexiblesearch.plugins.converter.calc.Value
import com.zemlianikin.flexiblesearch.plugins.converter.core.CurrencyCode
import com.zemlianikin.flexiblesearch.plugins.converter.core.DecayingFrecency
import com.zemlianikin.flexiblesearch.plugins.converter.core.Num
import com.zemlianikin.flexiblesearch.plugins.converter.core.RateTable
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Валидный результат и выражение, из которого он получен. */
data class Shown(val expression: String, val ok: Calculation.Ok)

/**
 * Состояние экрана калькулятора: ввод, результат, рейтинг валют и
 * подсказки. Экран только читает его и зовёт правки ввода; пересчёт — при
 * каждом изменении текста или курсов. Курсы и frecency приходят через
 * [bind]. Упрощение ради PoC: валюты засчитываются сразу, как только во
 * вводе появился новый набор, а не по завершении выражения.
 */
@Stable
class CalculatorState(
    private val calculator: Calculator,
    rates: RateTable,
    private val scope: CoroutineScope,
    input: TextFieldValue = TextFieldValue(),
) {
    var input by mutableStateOf(input)
        private set

    var rates by mutableStateOf(rates)
        private set

    var result by mutableStateOf<Calculation>(Calculation.Incomplete)
        private set

    /**
     * Последний валидный результат: пока ввод невалиден, показываем его
     * тусклым, а копирование берёт именно его выражение. Пустой ввод
     * сбрасывает.
     */
    var shown by mutableStateOf<Shown?>(null)
        private set

    var ranking by mutableStateOf(emptyList<CurrencyCode>())
        private set

    /** Подсказки валют для numpad; считаются, только когда их читают. */
    val suggestions by derivedStateOf {
        suggestCurrencies(this.input, result, ranking)
    }

    /**
     * Ввод изменился правкой (поле, кнопки, чипы), а не через [sync]: туда
     * его отдаёт владелец, например в строку поиска.
     */
    var onEdit: (String) -> Unit = {}

    private var frecency: DecayingFrecency? = null

    // Один набор валют засчитывается один раз, пока ввод не очистят.
    private var recorded: Set<CurrencyCode>? = null

    init {
        settle()
    }

    /**
     * Новые курсы или frecency (пришёл снимок курсов): результат
     * пересчитывается, рейтинг перечитывается.
     */
    fun bind(rates: RateTable, frecency: DecayingFrecency) {
        if (rates !== this.rates) {
            this.rates = rates
            settle()
        }
        if (frecency !== this.frecency) {
            this.frecency = frecency
            scope.launch { ranking = frecency.ranking(Clock.System.now()) }
        }
        recordUsed()
    }

    /** Правка из поля ввода (системная клавиатура, вставка, курсор). */
    fun edit(new: TextFieldValue) =
        update(editInput(input, new, calculator, rates))

    /** Нажатие кнопки, набирающей [text]. */
    fun type(text: String) =
        update(typeInput(input, text, calculator, rates))

    fun backspace() = update(deleteInput(input, calculator, rates))

    fun clear() = update(TextFieldValue())

    /**
     * Текст сменили снаружи (строка поиска, подсказка): курсор в конец.
     * Свой же текст ничего не меняет, курсор остаётся.
     */
    fun sync(text: String) {
        if (text == input.text) return
        update(TextFieldValue(text, TextRange(text.length)), notify = false)
    }

    /** Валюта с чипа: заменяет то, к чему относится подсказка. */
    fun pickSuggestion(code: CurrencyCode) =
        update(
            applySuggestion(
                input,
                suggestions.replace,
                code,
                calculator,
                rates,
            )
        )

    /**
     * Валюта из окна «+»: вписывается в ввод и засчитывается во frecency.
     */
    fun pickCurrency(code: CurrencyCode) {
        update(
            applySuggestion(
                input,
                pickRange(input, suggestions),
                code,
                calculator,
                rates,
            )
        )
        record(setOf(code))
    }

    private fun update(value: TextFieldValue, notify: Boolean = true) {
        val textChanged = value.text != input.text
        input = value
        if (textChanged) {
            settle()
            recordUsed()
            if (notify) onEdit(value.text)
        }
    }

    private fun settle() {
        val text = input.text
        val result = calculator.calculate(text, rates)
        this.result = result
        if (text.isEmpty()) shown = null
        else if (result is Calculation.Ok) shown = Shown(text, result)
    }

    private fun recordUsed() {
        if (input.text.isEmpty()) {
            recorded = null
            return
        }
        val used = (result as? Calculation.Ok)?.currencies.orEmpty()
        if (frecency != null && used.isNotEmpty() && used != recorded) {
            recorded = used
            record(used)
        }
    }

    private fun record(codes: Set<CurrencyCode>) {
        val frecency = frecency ?: return
        scope.launch {
            val now = Clock.System.now()
            frecency.recordUsed(codes, now)
            ranking = frecency.ranking(now)
        }
    }

    companion object {
        /**
         * Переживает пересоздание Activity только ввод, остальное считается
         * из него.
         */
        fun saver(
            calculator: Calculator,
            rates: RateTable,
            scope: CoroutineScope,
        ): Saver<CalculatorState, Any> =
            Saver(
                save = { with(TextFieldValue.Saver) { save(it.input) } },
                restore = { saved ->
                    TextFieldValue.Saver.restore(saved)?.let {
                        CalculatorState(calculator, rates, scope, it)
                    }
                },
            )
    }
}

@Composable
fun rememberCalculatorState(
    calculator: Calculator,
    rates: RateTable,
    frecency: DecayingFrecency,
): CalculatorState {
    val scope = rememberCoroutineScope()
    val state =
        rememberSaveable(
            saver = CalculatorState.saver(calculator, rates, scope)
        ) {
            CalculatorState(calculator, rates, scope)
        }
    LaunchedEffect(state, rates, frecency) { state.bind(rates, frecency) }
    return state
}

/**
 * [money] в валютах [ranking] по порядку, кроме его собственной и тех, для
 * которых нет курса. Лениво: в рейтинге сотни валют, вызывающий берёт
 * столько, сколько покажет.
 */
fun conversions(
    money: Value.Money,
    ranking: List<CurrencyCode>,
    rates: RateTable,
): Sequence<Value.Money> =
    ranking
        .asSequence()
        .filter { it != money.currency }
        .mapNotNull { code ->
            val rate =
                rates.rate(money.currency, code) ?: return@mapNotNull null
            Value.Money(Num(money.amount.value * rate.value), code)
        }
