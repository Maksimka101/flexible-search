package com.zemlianikin.flexiblesearch.plugins.converter.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * Главный результат крупной карточкой по размеру текста: встаёт в тот же
 * wrap, что и карточки конвертаций. [dim] — результат устарел (ввод сейчас
 * невалиден). Тап и долгий тап ([onClick], [onLongClick]) — по желанию,
 * подписи для озвучки — [clickLabel], [longClickLabel].
 */
@Composable
fun ResultCard(
    text: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
    dim: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    clickLabel: String? = null,
    longClickLabel: String? = null,
) {
    val alpha by
        animateFloatAsState(if (dim) 0.5f else 1f, Motion.effects())
    val shape = MaterialTheme.shapes.extraLarge
    Surface(
        shape = shape,
        color =
            if (isError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.primaryContainer,
        contentColor =
            if (isError) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onPrimaryContainer,
        modifier =
            modifier
                .alpha(alpha)
                .animateContentSize(Motion.spatial<IntSize>())
                .clip(shape)
                .then(
                    if (onClick == null) Modifier
                    else
                        Modifier.combinedClickable(
                            onClick = onClick,
                            onLongClick = onLongClick,
                            onClickLabel = clickLabel,
                            onLongClickLabel = longClickLabel,
                        )
                ),
    ) {
        Text(
            text,
            style =
                if (isError) MaterialTheme.typography.titleMedium
                else
                    MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )
    }
}

/**
 * Карточка «валюта над суммой» из блока конвертаций: узкая, чтобы несколько
 * встали в одну строку.
 */
@Composable
fun ConversionCard(
    code: String,
    amount: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                code,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(amount, style = MaterialTheme.typography.titleLarge)
        }
    }
}
