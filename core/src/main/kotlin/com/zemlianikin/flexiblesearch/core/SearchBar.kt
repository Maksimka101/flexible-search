package com.zemlianikin.flexiblesearch.core

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// --- Строка поиска ---

/** Data class, чтобы override мог поменять одно поле: `props.copy(...)`. */
data class SearchBarProps(
    /** Текст после чипа триггера, без префикса. */
    val text: String,
    val onTextChange: (String) -> Unit,
    /** Enter на клавиатуре. */
    val onSubmit: () -> Unit,
    val focusRequester: FocusRequester,
    /** Активный префикс, показывается чипом перед текстом. */
    val trigger: Trigger? = null,
    /** Backspace при пустом тексте убирает чип. */
    val onExitTrigger: () -> Unit = {},
    val keyboardType: KeyboardType = KeyboardType.Text,
    /** null — подсказка из стиля. */
    val placeholder: String? = null,
)

data class SearchBarStyle(
    val containerColor: Color,
    val contentColor: Color,
    val shape: Shape,
    val height: Dp,
    val textStyle: TextStyle,
    val placeholder: String,
)

val SearchBar =
    Component<SearchBarProps, SearchBarStyle>(
        name = "SearchBar",
        defaultStyle = {
            val colors = MaterialTheme.colorScheme
            SearchBarStyle(
                containerColor = colors.surfaceContainerHigh,
                contentColor = colors.onSurface,
                shape = RoundedCornerShape(16.dp),
                height = 52.dp,
                textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = "Search apps, calculate",
            )
        },
    ) { props, style ->
        Surface(
            color = style.containerColor,
            contentColor = style.contentColor,
            shape = style.shape,
            modifier = Modifier.fillMaxWidth().height(style.height),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    SearchIcon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.size(12.dp))
                props.trigger?.let {
                    TriggerChip(TriggerChipProps(it, props.onExitTrigger))
                    Spacer(Modifier.size(8.dp))
                }
                Box(
                    Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (props.text.isEmpty()) {
                        Text(
                            props.placeholder ?: style.placeholder,
                            style = style.textStyle,
                            color = style.contentColor.copy(alpha = 0.6f),
                        )
                    }
                    // Текст сменили снаружи (подсказка `↖`, префикс стал
                    // чипом) — курсор в конец; свой набор не трогаем.
                    var edited by remember {
                        mutableStateOf(
                            TextFieldValue(
                                props.text,
                                TextRange(props.text.length),
                            )
                        )
                    }
                    val value =
                        if (edited.text == props.text) edited
                        else
                            TextFieldValue(
                                props.text,
                                TextRange(props.text.length),
                            )
                    BasicTextField(
                        value = value,
                        onValueChange = {
                            edited = it
                            if (it.text != props.text)
                                props.onTextChange(it.text)
                        },
                        singleLine = true,
                        textStyle =
                            style.textStyle.copy(
                                color = style.contentColor
                            ),
                        cursorBrush = SolidColor(style.contentColor),
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = props.keyboardType,
                                imeAction = ImeAction.Go,
                            ),
                        keyboardActions =
                            KeyboardActions(onGo = { props.onSubmit() }),
                        modifier =
                            Modifier.fillMaxWidth()
                                .focusRequester(props.focusRequester)
                                .onPreviewKeyEvent {
                                    // Клавиатура шлёт Backspace событием,
                                    // когда стирать в поле уже нечего.
                                    val exit =
                                        it.key == Key.Backspace &&
                                            it.type ==
                                                KeyEventType.KeyDown &&
                                            props.text.isEmpty() &&
                                            props.trigger != null
                                    if (exit) props.onExitTrigger()
                                    exit
                                },
                    )
                }
            }
        }
    }

class TriggerChipProps(val trigger: Trigger, val onClick: () -> Unit)

data class TriggerChipStyle(
    val containerColor: Color,
    val contentColor: Color,
    val shape: Shape,
    val textStyle: TextStyle,
)

/** Чип префикса в строке поиска; нажатие убирает префикс. */
val TriggerChip =
    Component<TriggerChipProps, TriggerChipStyle>(
        name = "TriggerChip",
        defaultStyle = {
            val colors = MaterialTheme.colorScheme
            TriggerChipStyle(
                containerColor = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer,
                shape = CircleShape,
                textStyle = MaterialTheme.typography.labelLarge,
            )
        },
    ) { props, style ->
        Surface(
            onClick = props.onClick,
            color = style.containerColor,
            contentColor = style.contentColor,
            shape = style.shape,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                props.trigger.icon?.let {
                    ActionIconImage(it, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                }
                Text(props.trigger.label, style = style.textStyle)
            }
        }
    }

internal val SearchIcon: ImageVector =
    materialIcon(
        "Search",
        "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 " +
            "16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16" +
            "c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99" +
            "L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5" +
            "S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z",
    )
