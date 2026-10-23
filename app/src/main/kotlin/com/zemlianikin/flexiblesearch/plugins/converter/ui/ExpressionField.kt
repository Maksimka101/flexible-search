package com.zemlianikin.flexiblesearch.plugins.converter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.zemlianikin.flexiblesearch.core.SearchBarStyle
import com.zemlianikin.flexiblesearch.core.Trigger
import com.zemlianikin.flexiblesearch.core.TriggerChip
import com.zemlianikin.flexiblesearch.core.TriggerChipProps
import kotlinx.coroutines.awaitCancellation

/**
 * Поле ввода выражения вместо строки поиска, в её стиле ([style]): чип
 * [trigger], текст, `✕` и переключатель numpad / системная клавиатура.
 * Только внешний вид: правки текста и подчёркивание ошибки приходят снаружи
 * ([onValueChange], [visualTransformation]).
 */
@Composable
fun ExpressionField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    visualTransformation: VisualTransformation,
    onClear: () -> Unit,
    numpad: Boolean,
    onToggleMode: () -> Unit,
    focusRequester: FocusRequester,
    trigger: Trigger,
    onExitTrigger: () -> Unit,
    placeholder: String,
    style: SearchBarStyle,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = style.containerColor,
        contentColor = style.contentColor,
        shape = style.shape,
        modifier = modifier.fillMaxWidth().height(style.height),
    ) {
        // Справа меньше, чем слева: у кнопки 48 dp области касания,
        // подпись внутри по центру добавляет ещё ~10 dp.
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TriggerChip(TriggerChipProps(trigger, onExitTrigger))
            Spacer(Modifier.size(8.dp))
            // В numpad поле остаётся обычным (иначе не рисуется каретка),
            // но системный ввод к нему не подключается. Сессия ввода
            // живёт, пока поле в композиции, поэтому при смене режима поле
            // пересоздаётся.
            key(numpad) {
                WithoutSystemKeyboard(numpad) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = true,
                        textStyle =
                            style.textStyle.copy(
                                color = style.contentColor
                            ),
                        cursorBrush = SolidColor(style.contentColor),
                        keyboardOptions =
                            KeyboardOptions(
                                capitalization =
                                    KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                keyboardType = ExpressionKeyboardType,
                                showKeyboardOnFocus = !numpad,
                            ),
                        visualTransformation = visualTransformation,
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (value.text.isEmpty()) {
                                    Text(
                                        placeholder,
                                        style = style.textStyle,
                                        color =
                                            style.contentColor.copy(
                                                alpha = 0.6f
                                            ),
                                        maxLines = 1,
                                    )
                                }
                                inner()
                            }
                        },
                        modifier =
                            Modifier.weight(1f)
                                .focusRequester(focusRequester)
                                .onPreviewKeyEvent {
                                    // Клавиатура шлёт Backspace событием,
                                    // когда стирать в поле уже нечего.
                                    val exit =
                                        it.key == Key.Backspace &&
                                            it.type ==
                                                KeyEventType.KeyDown &&
                                            value.text.isEmpty()
                                    if (exit) onExitTrigger()
                                    exit
                                },
                    )
                }
            }
            if (value.text.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier =
                        Modifier.semantics { contentDescription = "Clear" },
                ) {
                    Text(
                        "✕",
                        style = MaterialTheme.typography.titleLarge,
                        color = style.contentColor.copy(alpha = 0.7f),
                    )
                }
            }
            val toggle = if (numpad) "Text keyboard" else "Numpad"
            IconButton(
                onClick = onToggleMode,
                modifier =
                    Modifier.semantics { contentDescription = toggle },
            ) {
                Text(
                    if (numpad) "ABC" else "123",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// «Видимый пароль», как в Termux: Gboard показывает над буквами ряд цифр
// (у обычного текста он зависит от настройки) и не подсказывает ввод.
// Обычный Password зовёт менеджер паролей.
private val ExpressionKeyboardType = KeyboardType.PasswordVisible

/**
 * При [block] ввод с системной клавиатуры к полю не подключается: она не
 * показывается, каретка и выделение работают.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun WithoutSystemKeyboard(
    block: Boolean,
    content: @Composable () -> Unit,
) {
    if (block) {
        InterceptPlatformTextInput({ _, _ -> awaitCancellation() }, content)
    } else {
        content()
    }
}
