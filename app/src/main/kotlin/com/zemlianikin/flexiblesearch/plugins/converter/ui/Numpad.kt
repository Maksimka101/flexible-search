package com.zemlianikin.flexiblesearch.plugins.converter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zemlianikin.flexiblesearch.plugins.converter.core.CurrencyCode

/**
 * Экранный numpad вместо системной клавиатуры. Нажатие срабатывает сразу
 * при касании, `⌫` повторяется при удержании. Только вид: набор и удаление
 * делают [onKey] и [onBackspace]. [decimal] — разделитель дроби локали.
 * [height] — высота всего блока (равна высоте системной клавиатуры, чтобы
 * режимы не сдвигали экран): кнопки растягиваются до неё; `null` —
 * естественная высота.
 */
@Composable
fun Numpad(
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    decimal: String,
    height: Dp?,
    modifier: Modifier = Modifier,
) {
    val rows = remember(decimal) { numpadRows(decimal) }
    val sized = if (height != null) modifier.height(height) else modifier
    Column(
        sized.fillMaxWidth().padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (row in rows) {
            Row(
                if (height != null) Modifier.weight(1f) else Modifier,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (key in row) {
                    KeyButton(
                        key,
                        onPress = { key.text?.let(onKey) ?: onBackspace() },
                        pressOnTouch = true,
                        corner = 18.dp,
                        textStyle = MaterialTheme.typography.headlineSmall,
                        modifier =
                            Modifier.weight(1f)
                                .then(
                                    if (height != null)
                                        Modifier.fillMaxHeight()
                                    else Modifier.height(56.dp)
                                ),
                    )
                }
            }
        }
    }
}

private fun numpadRows(decimal: String) =
    listOf(
        listOf(Key.op("("), Key.op(")"), Key.op("%"), Key.Backspace),
        listOf(
            Key.digit('1'),
            Key.digit('2'),
            Key.digit('3'),
            Key.op("÷", "/"),
        ),
        listOf(
            Key.digit('4'),
            Key.digit('5'),
            Key.digit('6'),
            Key.op("×", "*"),
        ),
        listOf(
            Key.digit('7'),
            Key.digit('8'),
            Key.digit('9'),
            Key.op("−", "-"),
        ),
        listOf(
            Key(decimal, decimal, KeyKind.Digit),
            Key.digit('0'),
            Key.To,
            Key.op("+"),
        ),
    )

/**
 * Чипы валют над numpad: тап выбирает валюту. Показаны все [codes], а не
 * только подходящие к месту курсора: чипы вне [enabled] неактивны, чтобы
 * ряд не мерцал при наборе. Высота фиксирована ([KEY_ROW_HEIGHT], как у
 * ряда [OperatorKeys]), чтобы поле ввода не сдвигалось ни когда чипов нет,
 * ни при смене режима.
 */
@Composable
fun CurrencyChips(
    codes: List<CurrencyCode>,
    enabled: Set<CurrencyCode>,
    onPick: (CurrencyCode) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().height(KEY_ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Кнопка «+» слева: чипы важнее и остаются у большого пальца.
        val add = "Add currency"
        FilledTonalIconButton(
            onClick = onAdd,
            modifier = Modifier.semantics { contentDescription = add },
        ) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
        // reverseLayout + Alignment.End: чипы прижаты к правому краю, самая
        // частая валюта у большого пальца.
        // Своё arrangement перекрывает умолчание reverseLayout (End),
        // поэтому выравнивание задано явно.
        LazyRow(
            Modifier.weight(1f),
            reverseLayout = true,
            horizontalArrangement =
                Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(codes, key = { it.code }) { code ->
                SuggestionChip(
                    onClick = { onPick(code) },
                    enabled = code in enabled,
                    label = {
                        Text(
                            code.code,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    shape = CircleShape,
                    colors =
                        SuggestionChipDefaults.suggestionChipColors(
                            containerColor =
                                MaterialTheme.colorScheme
                                    .secondaryContainer,
                            labelColor =
                                MaterialTheme.colorScheme
                                    .onSecondaryContainer,
                            disabledContainerColor =
                                MaterialTheme.colorScheme.secondaryContainer
                                    .copy(alpha = 0.4f),
                            disabledLabelColor =
                                MaterialTheme.colorScheme
                                    .onSecondaryContainer
                                    .copy(alpha = 0.4f),
                        ),
                    border = null,
                )
            }
        }
    }
}
