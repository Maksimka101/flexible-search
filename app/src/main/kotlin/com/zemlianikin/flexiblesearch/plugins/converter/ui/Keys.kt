package com.zemlianikin.flexiblesearch.plugins.converter.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class KeyKind {
    Digit,
    Operator,
    Word,
    Backspace,
}

/** Клавиша: подпись, набираемый текст (`null` — backspace) и вид. */
class Key(val label: String, val text: String?, val kind: KeyKind) {
    companion object {
        fun digit(c: Char) = Key(c.toString(), c.toString(), KeyKind.Digit)

        fun op(label: String, text: String = label) =
            Key(label, text, KeyKind.Operator)

        val To = Key("to", "to", KeyKind.Word)
        val Backspace = Key("⌫", null, KeyKind.Backspace)
    }
}

private val OPERATOR_ROW =
    listOf(
        Key.op("+"),
        Key.op("−", "-"),
        Key.op("×", "*"),
        Key.op("÷", "/"),
        Key.op("("),
        Key.op(")"),
        Key.op("%"),
        Key.To,
    )

private val OPERATOR_KEY_HEIGHT = 52.dp
private val OPERATOR_ROW_PADDING = 8.dp

/**
 * Высота ряда [OperatorKeys]. Ряд чипов над numpad той же высоты, чтобы
 * поле ввода не сдвигалось при смене режима.
 */
val KEY_ROW_HEIGHT = OPERATOR_KEY_HEIGHT + OPERATOR_ROW_PADDING * 2

/**
 * Ряд кнопок над системной клавиатурой, как extra-keys в Termux: `+ − × ÷ (
 * ) % to`. Только вид: набор текста делает [onKey].
 */
@Composable
fun OperatorKeys(onKey: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(vertical = OPERATOR_ROW_PADDING),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (key in OPERATOR_ROW) {
            KeyButton(
                key,
                onPress = { key.text?.let(onKey) },
                pressOnTouch = false,
                corner = 16.dp,
                textStyle = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).height(OPERATOR_KEY_HEIGHT),
            )
        }
    }
}

/**
 * Кнопка-клавиша: цвет по виду ([KeyKind]), при нажатии скругляется сильнее
 * ([corner] + 10 dp) и даёт отклик. [pressOnTouch] — срабатывает сразу при
 * касании, а `⌫` повторяется, пока держат; иначе обычный клик.
 */
@Composable
fun KeyButton(
    key: Key,
    onPress: () -> Unit,
    pressOnTouch: Boolean,
    corner: Dp,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val (container, content) =
        when (key.kind) {
            KeyKind.Digit -> colors.surfaceContainerHigh to colors.onSurface
            KeyKind.Operator ->
                colors.secondaryContainer to colors.onSecondaryContainer
            KeyKind.Word -> colors.primary to colors.onPrimary
            KeyKind.Backspace ->
                colors.tertiaryContainer to colors.onTertiaryContainer
        }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val radius by
        animateDpAsState(
            if (pressed) corner + 10.dp else corner,
            Motion.fastSpatial(),
        )
    val shape = RoundedCornerShape(radius)
    val haptic = LocalHapticFeedback.current
    val label =
        @Composable {
            Box(contentAlignment = Alignment.Center) {
                Text(key.label, style = textStyle)
            }
        }
    if (!pressOnTouch) {
        Surface(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onPress()
            },
            interactionSource = interaction,
            shape = shape,
            color = container,
            contentColor = content,
            modifier = modifier,
            content = label,
        )
        return
    }
    val current by rememberUpdatedState(onPress)
    val repeats = key.kind == KeyKind.Backspace
    Surface(
        shape = shape,
        color = container,
        contentColor = content,
        modifier =
            modifier
                .semantics {
                    role = Role.Button
                    onClick {
                        current()
                        true
                    }
                }
                .pointerInput(repeats) {
                    detectTapGestures(
                        onPress = { offset ->
                            val press = PressInteraction.Press(offset)
                            interaction.emit(press)
                            haptic.performHapticFeedback(
                                HapticFeedbackType.VirtualKey
                            )
                            current()
                            // Повтор живёт внутри жеста: оборвался жест
                            // (отпустили, отмена, кнопка ушла с экрана) —
                            // повтор тоже.
                            val released = coroutineScope {
                                val repeat =
                                    if (repeats)
                                        launch {
                                            delay(400)
                                            while (true) {
                                                current()
                                                delay(60)
                                            }
                                        }
                                    else null
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    repeat?.cancel()
                                }
                            }
                            interaction.emit(
                                if (released)
                                    PressInteraction.Release(press)
                                else PressInteraction.Cancel(press)
                            )
                        }
                    )
                },
        content = label,
    )
}
