package com.zemlianikin.flexiblesearch.core

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.key
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// --- Приложения ---

class AppRowProps(
    val apps: List<ResultItem.App>,
    val target: ResultItem?,
    val onClick: (ResultItem) -> Unit,
)

data class AppRowStyle(
    val containerColor: Color,
    val shape: Shape,
    val maxApps: Int,
    /** Жёсткость пружины, с которой иконки едут и проявляются. */
    val animationStiffness: Float,
)

val AppRow =
    Component<AppRowProps, AppRowStyle>(
        name = "AppRow",
        defaultStyle = {
            AppRowStyle(
                MaterialTheme.colorScheme.surfaceContainer,
                RoundedCornerShape(24.dp),
                maxApps = 5,
                animationStiffness = ResultAnimationStiffness,
            )
        },
    ) { props, style ->
        Surface(
            color = style.containerColor,
            shape = style.shape,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Ленивый ряд ради `animateItem`: иконка, оставшаяся в
            // результатах, едет на новое место, а не перескакивает.
            LazyRow(
                userScrollEnabled = false,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
            ) {
                items(
                    keyedApps(props.apps.take(style.maxApps)),
                    key = { it.first },
                ) { (_, app) ->
                    AppIcon(
                        AppIconProps(
                            app,
                            app == props.target,
                            // Ширина места фиксирована: при 1–4 результатах
                            // иконки не растягиваются.
                            Modifier.fillParentMaxWidth(1f / style.maxApps)
                                .then(
                                    itemAnimation(style.animationStiffness)
                                ),
                        ) {
                            props.onClick(app)
                        }
                    )
                }
            }
        }
    }

/**
 * Ключи плиток для ленивого ряда. В общий ряд попадают плитки разных
 * плагинов, и `id` может совпасть — такие получают номер.
 */
private fun keyedApps(
    apps: List<ResultItem.App>
): List<Pair<String, ResultItem.App>> {
    val seen = mutableMapOf<String, Int>()
    return apps.map { app ->
        val count = seen.getOrElse(app.id) { 0 }
        seen[app.id] = count + 1
        "${app.id}#$count" to app
    }
}

class AppIconProps(
    val app: ResultItem.App,
    val highlighted: Boolean,
    val modifier: Modifier,
    val onClick: () -> Unit,
)

data class AppIconStyle(
    val iconSize: Dp,
    val highlightColor: Color,
    val highlightShape: Shape,
    val labelStyle: TextStyle,
    val labelColor: Color,
)

val AppIcon =
    Component<AppIconProps, AppIconStyle>(
        name = "AppIcon",
        defaultStyle = {
            val colors = MaterialTheme.colorScheme
            AppIconStyle(
                iconSize = 48.dp,
                highlightColor = colors.secondaryContainer,
                highlightShape = RoundedCornerShape(16.dp),
                labelStyle = MaterialTheme.typography.labelMedium,
                labelColor = colors.onSurface,
            )
        },
    ) { props, style ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                props.modifier
                    .clip(style.highlightShape)
                    .background(
                        if (props.highlighted) style.highlightColor
                        else Color.Transparent,
                        style.highlightShape,
                    )
                    .clickable(onClick = props.onClick)
                    .padding(vertical = 8.dp, horizontal = 2.dp),
        ) {
            val icon = props.app.icon
            if (icon != null) {
                Image(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(style.iconSize),
                )
            } else {
                Spacer(Modifier.size(style.iconSize))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                props.app.label,
                style = style.labelStyle,
                color = style.labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
