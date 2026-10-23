package com.zemlianikin.flexiblesearch.core

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Иконка без Compose-кода: значок приложения или векторная. */
sealed interface ActionIcon {
    class Bitmap(val image: ImageBitmap) : ActionIcon

    /** Красится цветом содержимого, как Material-иконки. */
    class Vector(val image: ImageVector) : ActionIcon
}

/**
 * Иконка 24×24 из `pathData` Material Symbols, без зависимости от
 * material-icons. Красится цветом содержимого.
 */
fun materialIcon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes(pathData), fill = SolidColor(Color.Black))
        .build()

/** Кнопка в ряду под результатами или в строке поиска, видна всегда. */
class QuickAction(
    val id: String,
    val label: String,
    val icon: ActionIcon,
    /** Подсветить как включённую, например активный префикс. */
    val isSelected: (SearchQuery) -> Boolean,
    val action: Action,
)

// --- Быстрые действия: «искать в…», режимы ---

class QuickActionsProps(
    val actions: List<QuickAction>,
    val query: SearchQuery,
    val onClick: (QuickAction) -> Unit,
)

data class QuickActionsStyle(val spacing: Dp)

val QuickActions =
    Component<QuickActionsProps, QuickActionsStyle>(
        name = "QuickActions",
        defaultStyle = { QuickActionsStyle(spacing = 8.dp) },
    ) { props, style ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(style.spacing),
            modifier = Modifier.fillMaxWidth(),
        ) {
            for (action in props.actions) {
                QuickActionButton(
                    QuickActionButtonProps(
                        action,
                        selected = action.isSelected(props.query),
                    ) {
                        props.onClick(action)
                    }
                )
            }
        }
    }

class QuickActionButtonProps(
    val action: QuickAction,
    val selected: Boolean,
    val onClick: () -> Unit,
)

data class QuickActionButtonStyle(
    val size: Dp,
    val iconSize: Dp,
    val containerColor: Color,
    val selectedColor: Color,
    val contentColor: Color,
    val shape: Shape,
)

val QuickActionButton =
    Component<QuickActionButtonProps, QuickActionButtonStyle>(
        name = "QuickActionButton",
        defaultStyle = {
            val colors = MaterialTheme.colorScheme
            QuickActionButtonStyle(
                size = 44.dp,
                iconSize = 28.dp,
                // Без подложки; выбранная — с ней.
                containerColor = Color.Transparent,
                selectedColor = colors.primaryContainer,
                contentColor = colors.onSurface,
                shape = CircleShape,
            )
        },
    ) { props, style ->
        Surface(
            onClick = props.onClick,
            color =
                if (props.selected) style.selectedColor
                else style.containerColor,
            contentColor = style.contentColor,
            shape = style.shape,
            modifier = Modifier.size(style.size),
        ) {
            Box(contentAlignment = Alignment.Center) {
                val modifier = Modifier.size(style.iconSize)
                ActionIconImage(props.action.icon, modifier)
            }
        }
    }
