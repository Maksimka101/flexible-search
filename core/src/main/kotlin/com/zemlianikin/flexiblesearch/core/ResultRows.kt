package com.zemlianikin.flexiblesearch.core

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

class ResultRowProps(
    val row: ResultItem.Row,
    /** Ключ для кэша превью, уникален среди всех плагинов. */
    val key: String,
    val highlighted: Boolean,
    val onClick: () -> Unit,
)

data class ResultRowStyle(
    val highlightedColor: Color,
    val contentColor: Color,
    val titleStyle: TextStyle,
    val subtitleStyle: TextStyle,
    val leadingSize: Dp,
    val leadingShape: Shape,
    val placeholderColor: Color,
)

val ResultRow =
    Component<ResultRowProps, ResultRowStyle>(
        name = "ResultRow",
        defaultStyle = {
            val colors = MaterialTheme.colorScheme
            ResultRowStyle(
                highlightedColor = colors.secondaryContainer,
                contentColor = colors.onSurface,
                titleStyle = MaterialTheme.typography.bodyLarge,
                subtitleStyle = MaterialTheme.typography.bodySmall,
                leadingSize = 40.dp,
                leadingShape = RoundedCornerShape(8.dp),
                placeholderColor = colors.surfaceContainerHighest,
            )
        },
    ) { props, style ->
        val row = props.row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier.fillMaxWidth()
                    .background(
                        if (props.highlighted) style.highlightedColor
                        else Color.Transparent
                    )
                    .clickable(onClick = props.onClick)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier.size(style.leadingSize)
                        .clip(style.leadingShape),
            ) {
                val thumbnail = rememberThumbnail(props.key, row.thumbnail)
                when {
                    thumbnail != null ->
                        Image(
                            thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    row.thumbnail != null ->
                        Box(
                            Modifier.fillMaxSize()
                                .background(style.placeholderColor)
                        )
                    row.icon != null ->
                        ActionIconImage(row.icon, Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    highlighted(row.title, row.titleHighlights),
                    style = style.titleStyle,
                    color = style.contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                row.subtitle?.let {
                    Text(
                        it,
                        style = style.subtitleStyle,
                        color = style.contentColor.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

class SuggestionRowProps(
    val suggestion: ResultItem.Suggestion,
    val highlighted: Boolean,
    val onFill: () -> Unit,
    val onClick: () -> Unit,
)

data class SuggestionRowStyle(
    val highlightedColor: Color,
    val contentColor: Color,
    val textStyle: TextStyle,
)

val SuggestionRow =
    Component<SuggestionRowProps, SuggestionRowStyle>(
        name = "SuggestionRow",
        defaultStyle = {
            val colors = MaterialTheme.colorScheme
            SuggestionRowStyle(
                highlightedColor = colors.secondaryContainer,
                contentColor = colors.onSurface,
                textStyle = MaterialTheme.typography.bodyLarge,
            )
        },
    ) { props, style ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier.fillMaxWidth()
                    .background(
                        if (props.highlighted) style.highlightedColor
                        else Color.Transparent
                    )
                    .clickable(onClick = props.onClick)
                    .padding(start = 24.dp, end = 4.dp),
        ) {
            Icon(
                SearchIcon,
                contentDescription = null,
                tint = style.contentColor.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(16.dp))
            Text(
                highlighted(
                    props.suggestion.text,
                    props.suggestion.highlights,
                ),
                style = style.textStyle,
                color = style.contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = props.onFill) {
                Icon(
                    FillIcon,
                    contentDescription = "Fill in",
                    tint = style.contentColor.copy(alpha = 0.7f),
                )
            }
        }
    }

/** Подсветка жирным символов на [positions], например из [NameMatch]. */
fun highlighted(text: String, positions: List<Int>): AnnotatedString =
    buildAnnotatedString {
        append(text)
        for (position in positions) {
            if (position in text.indices) {
                addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold),
                    position,
                    position + 1,
                )
            }
        }
    }

@Composable
internal fun ActionIconImage(
    icon: ActionIcon,
    modifier: Modifier,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null,
) {
    when (icon) {
        is ActionIcon.Bitmap ->
            Image(icon.image, contentDescription, modifier)
        is ActionIcon.Vector ->
            Icon(icon.image, contentDescription, modifier, tint)
        is ActionIcon.Lazy -> {
            val image = rememberThumbnail(icon.key, icon.load)
            if (image != null) Image(image, contentDescription, modifier)
            else Spacer(modifier)
        }
    }
}

// Material Symbols «north_west».
private val FillIcon: ImageVector =
    materialIcon(
        "Fill",
        "M5,15h2V8.41L18.59,20L20,18.59L8.41,7H15V5H5V15z",
    )

class ResultCardProps(
    val card: ResultItem.Card,
    val highlighted: Boolean,
    val onClick: () -> Unit,
)

data class ResultCardStyle(
    val containerColor: Color,
    val highlightedColor: Color,
    val contentColor: Color,
    val shape: Shape,
    val titleStyle: TextStyle,
    val subtitleStyle: TextStyle,
)

val ResultCard =
    Component<ResultCardProps, ResultCardStyle>(
        name = "ResultCard",
        defaultStyle = {
            val colors = MaterialTheme.colorScheme
            ResultCardStyle(
                containerColor = colors.surfaceContainer,
                highlightedColor = colors.secondaryContainer,
                contentColor = colors.onSurface,
                shape = RoundedCornerShape(20.dp),
                titleStyle = MaterialTheme.typography.headlineSmall,
                subtitleStyle = MaterialTheme.typography.bodyMedium,
            )
        },
    ) { props, style ->
        Surface(
            onClick = props.onClick,
            color =
                if (props.highlighted) style.highlightedColor
                else style.containerColor,
            contentColor = style.contentColor,
            shape = style.shape,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Text(
                    props.card.title,
                    style = style.titleStyle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                props.card.subtitle?.let {
                    Text(
                        it,
                        style = style.subtitleStyle,
                        color = style.contentColor.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
