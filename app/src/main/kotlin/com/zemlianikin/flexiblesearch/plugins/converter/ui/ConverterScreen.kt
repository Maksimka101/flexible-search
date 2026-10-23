package com.zemlianikin.flexiblesearch.plugins.converter.ui

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zemlianikin.flexiblesearch.plugins.converter.calc.CalcError
import com.zemlianikin.flexiblesearch.plugins.converter.calc.Calculation
import com.zemlianikin.flexiblesearch.plugins.converter.calc.Value
import com.zemlianikin.flexiblesearch.plugins.converter.core.CurrencyCode
import com.zemlianikin.flexiblesearch.plugins.converter.core.RateTable
import com.zemlianikin.flexiblesearch.plugins.converter.core.today
import com.zemlianikin.flexiblesearch.plugins.converter.rates.RatesState
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * Вместо результатов поиска, снизу вверх по ходу руки: главный результат у
 * ввода, над ним та же сумма в остальных валютах в порядке frecency, сверху
 * строка о курсах. [ratesState] — откуда курсы и что с ними: по нему строка
 * о курсах и «загружаем курсы» вместо ошибки «нет курса».
 */
@Composable
fun ConverterResults(
    state: CalculatorState,
    ratesState: RatesState,
    format: ValueFormatter,
    modifier: Modifier = Modifier,
) {
    // Курсов ещё нет, но грузим: NoRate тут не ошибка ввода.
    val loadingRates = ratesState.cached == null && ratesState.refreshing
    val clipboard = LocalClipboard.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val copy = { value: String, feedback: HapticFeedbackType ->
        haptic.performHapticFeedback(feedback)
        scope.launch { clipboard.setClipEntry(plainTextEntry(value)) }
        Unit
    }
    Column(modifier) {
        Text(
            ratesStatus(ratesState),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(8.dp))
        Conversions(
            state.result,
            state.shown,
            state.ranking,
            state.rates,
            loadingRates,
            format,
            copy,
            Modifier.weight(1f, fill = false),
        )
    }
}

/**
 * Ряд над клавиатурой с переключателем режима: в numpad — чипы валют и под
 * ним сам numpad высотой системной клавиатуры; с клавиатурой — ряд
 * операторов. Режимы сменяются без анимации. [visible] — конвертер открыт,
 * [spacing] — отступ ряда от того, что над ним.
 */
@Composable
fun ConverterKeys(
    state: CalculatorState,
    keyboard: KeyboardLayout,
    format: ValueFormatter,
    visible: Boolean,
    spacing: Dp,
    onBackspace: () -> Unit,
) {
    // Кнопка «+» рядом с чипами: валюта по названию или стране
    // вписывается в ввод и засчитывается во frecency.
    val directory = remember { CurrencyDirectory() }
    var picking by remember { mutableStateOf(false) }
    if (picking) {
        CurrencyPickerDialog(
            directory,
            state.ranking,
            onPick = {
                picking = false
                state.pickCurrency(it)
            },
            onDismiss = { picking = false },
        )
    }
    if (!visible) return
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(top = spacing).height(KEY_ROW_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModeToggle(keyboard.numpad, keyboard.toggle)
            Spacer(Modifier.width(8.dp))
            KeyRow(state, keyboard.numpad, onAdd = { picking = true })
        }
        if (keyboard.numpad) {
            Numpad(
                onKey = state::type,
                onBackspace = onBackspace,
                decimal = format.decimalSeparator,
                height = keyboard.space,
            )
        }
    }
}

/** Numpad или системная клавиатура: `ABC` / `123`, как было в поле. */
@Composable
private fun ModeToggle(numpad: Boolean, onToggle: () -> Unit) {
    val label = if (numpad) "Text keyboard" else "Numpad"
    FilledTonalIconButton(
        onClick = onToggle,
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        Text(
            if (numpad) "ABC" else "123",
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun RowScope.KeyRow(
    state: CalculatorState,
    numpad: Boolean,
    onAdd: () -> Unit,
) {
    Box(Modifier.weight(1f)) {
        if (numpad) {
            // Ряд валют не прыгает: остаются все чипы топа,
            // неподходящие к месту курсора неактивны.
            val ranking = state.ranking
            val suggestions = state.suggestions
            val chips =
                remember(ranking, suggestions) {
                    (ranking.take(CHIP_LIMIT) + suggestions.codes)
                        .distinct()
                }
            CurrencyChips(
                chips,
                enabled = suggestions.codes.toSet(),
                onPick = state::pickSuggestion,
                onAdd = onAdd,
            )
        } else {
            OperatorKeys(onKey = state::type)
        }
    }
}

/**
 * Сколько карточек конвертаций считается и рисуется сразу и на сколько
 * больше — при подходе к краю прокрутки.
 */
private const val CARDS_PAGE = 12
private const val CARDS_LOAD_MARGIN_PX = 400

/**
 * Сумма в валютах рейтинга (ленивая подгрузка по прокрутке), кроме валюты
 * главной строки; безразмерный результат — без списка. Главный результат —
 * последняя карточка того же wrap.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Conversions(
    result: Calculation,
    shown: Shown?,
    ranking: List<CurrencyCode>,
    rates: RateTable,
    loadingRates: Boolean,
    format: ValueFormatter,
    copy: (String, HapticFeedbackType) -> Unit,
    modifier: Modifier,
) {
    val money = shown?.ok?.value as? Value.Money
    // В рейтинге сотни валют, считать и рисовать их все на каждое нажатие
    // — лаг. Берём первые [count] по рейтингу, остальные подгружаются,
    // когда прокрутка доходит до края. Пустой ввод сбрасывает подгруженное.
    var count by remember(money == null) { mutableIntStateOf(CARDS_PAGE) }
    val rows =
        remember(money, ranking, rates, count) {
            if (money == null) emptyList()
            else conversions(money, ranking, rates).take(count).toList()
        }
    val scroll = rememberScrollState()
    LaunchedEffect(scroll, ranking.size) {
        // reverseScrolling: конец прокрутки — верх списка. Подгружаем,
        // пока до него меньше запаса (в паре с count, чтобы после подгрузки
        // проверка повторилась, если карточек всё ещё мало).
        snapshotFlow {
            (scroll.maxValue - scroll.value < CARDS_LOAD_MARGIN_PX) to count
        }
            .collect { (nearEnd, shownCount) ->
                if (nearEnd && shownCount < ranking.size) {
                    count = shownCount + CARDS_PAGE
                }
            }
    }
    // Карточки переносятся по строкам и прижаты к правому нижнему углу:
    // самая частая валюта перед результатом, сам результат последний, у
    // большого пальца. Не влезло — прокрутка, начало снизу.
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomEnd) {
        FlowRow(
            Modifier.fillMaxWidth()
                .verticalScroll(scroll, reverseScrolling = true),
            horizontalArrangement =
                Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (row in rows.asReversed()) {
                ConversionCard(row.currency.code, format.amount(row.amount))
            }
            ResultText(result, shown, loadingRates, format, copy)
        }
    }
}

/**
 * Главный результат. Тап копирует его, долгий тап — «выражение =
 * результат». Ввод невалиден — показываем последний валидный тусклым; если
 * валидного ещё не было, текст ошибки (неполный ввод не показывает ничего).
 */
@Composable
private fun ResultText(
    result: Calculation,
    shown: Shown?,
    loadingRates: Boolean,
    format: ValueFormatter,
    copy: (String, HapticFeedbackType) -> Unit,
) {
    when {
        shown != null -> {
            val value = format.value(shown.ok.value)
            ResultCard(
                value,
                isError = false,
                dim = result !is Calculation.Ok,
                onClick = { copy(value, HapticFeedbackType.Confirm) },
                onLongClick = {
                    copy(
                        "${shown.expression} = $value",
                        HapticFeedbackType.LongPress,
                    )
                },
                clickLabel = "Copy result",
                longClickLabel = "Copy with expression",
            )
        }
        result is Calculation.Failed &&
            result.error == CalcError.NoRate &&
            loadingRates ->
            ResultCard("Loading rates…", isError = false, dim = true)
        result is Calculation.Failed ->
            ResultCard(errorText(result.error), isError = true)
    }
}

/** Строка над списком: дата курсов, загрузка или сбой. */
private fun ratesStatus(state: RatesState): String {
    val cached = state.cached
    return when {
        cached == null ->
            if (state.refreshing) "Loading rates…"
            else "Couldn't load rates"
        state.failed ->
            "Rates for ${ratesDay(cached.snapshot.date)} · " +
                "couldn't update"
        else -> "Rates for ${ratesDay(cached.snapshot.date)}"
    }
}

/**
 * Дата курсов словом: сегодня, вчера; раньше (и «из будущего» при сбитых
 * часах) — датой.
 */
private fun ratesDay(date: LocalDate): String =
    when (date.daysUntil(today())) {
        0 -> "today"
        1 -> "yesterday"
        else -> date.toString()
    }

/** Текст ошибки для главного результата и карточки общего поиска. */
fun errorText(error: CalcError): String =
    when (error) {
        CalcError.UnknownWord -> "Unknown word"
        is CalcError.AmbiguousCurrency ->
            error.options.joinToString("/", "Which currency: ", "?") {
                it.code
            }
        CalcError.BadNumber -> "Invalid number"
        CalcError.UnexpectedToken -> "Doesn't fit here"
        CalcError.MissingOperator -> "Missing operator"
        CalcError.TwoCurrencies -> "Two currencies for one number"
        CalcError.MixedNumberMoney -> "Can't add a number to money"
        CalcError.MoneyTimesMoney -> "Can't multiply money by money"
        CalcError.DivideByMoney -> "Can't divide a number by money"
        CalcError.MoneyPercent -> "No percent after a currency"
        CalcError.DivideByZero -> "Division by zero"
        CalcError.ConvertRatio -> "A ratio can't be converted"
        CalcError.NoRate -> "No rate for this currency"
    }

/** Текст для буфера обмена. */
private fun plainTextEntry(text: String) =
    ClipEntry(ClipData.newPlainText(null, text))
