package com.zemlianikin.flexiblesearch.poc.components.core

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
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

/** Кнопка в ряду под результатами, видна всегда. */
class QuickAction(
    val id: String,
    val label: String,
    val icon: ActionIcon,
    /** Подсветить как включённую, например активный префикс. */
    val isSelected: (SearchQuery) -> Boolean,
    val action: Action,
)
