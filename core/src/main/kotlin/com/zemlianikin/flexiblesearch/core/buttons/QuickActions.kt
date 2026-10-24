package com.zemlianikin.flexiblesearch.core.buttons

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.zemlianikin.flexiblesearch.core.PluginScope
import com.zemlianikin.flexiblesearch.core.component.Component
import com.zemlianikin.flexiblesearch.core.results.Action
import com.zemlianikin.flexiblesearch.core.results.ActionIconImage
import com.zemlianikin.flexiblesearch.core.results.ThumbnailLoader
import com.zemlianikin.flexiblesearch.core.triggers.SearchQuery
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Иконка без Compose-кода: значок приложения или векторная. */
sealed interface ActionIcon {
    class Bitmap(val image: ImageBitmap) : ActionIcon

    /** Красится цветом содержимого, как Material-иконки. */
    class Vector(val image: ImageVector) : ActionIcon

    /**
     * Грузится в фоне при показе, как превью результатов: не держит
     * установку плагина, а первый кадр окна её дожидается. [key] — ключ в
     * кэше превью, уникальный: `плагин/что`.
     */
    class Lazy(val key: String, val load: ThumbnailLoader) : ActionIcon
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
    /**
     * Убрать из ряда: удержать и утащить из ряда, как из дока macOS. Где
     * хранить скрытое и как вернуть, решает плагин: у него и так свои
     * настройки, там кнопку и включают обратно. null — убрать нельзя.
     */
    val onRemove: (() -> Unit)? = null,
    val action: Action,
)

// --- Быстрые действия: «искать в…», режимы ---
// Ряд прокручивается: кнопок может быть больше, чем влезает.

class QuickActionsProps(
    val actions: List<QuickAction>,
    val query: SearchQuery,
    val onClick: (QuickAction) -> Unit,
)

data class QuickActionsStyle(val spacing: Dp)

/**
 * Кнопку с [QuickAction.onRemove] можно убрать, как из дока macOS:
 * удержать, утащить на [RemoveZone] и отпустить. Зона — посередине между
 * верхом окна и рядом: что под ней, неизвестно, зато близко.
 */
val QuickActions =
    Component<QuickActionsProps, QuickActionsStyle>(
        name = "QuickActions",
        defaultStyle = { QuickActionsStyle(spacing = 8.dp) },
    ) { props, style ->
        val drag = remember { RemoveDrag() }
        Box {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .onGloballyPositioned {
                            drag.row = it.boundsOnScreen()
                        }
                        .horizontalScroll(rememberScrollState())
            ) {
                props.actions.forEachIndexed { index, action ->
                    key(action.id) {
                        RemovableSlot(
                            action,
                            gap = if (index == 0) 0.dp else style.spacing,
                            drag,
                        ) {
                            QuickActionButton(
                                QuickActionButtonProps(
                                    action,
                                    selected =
                                        action.isSelected(props.query),
                                ) {
                                    props.onClick(action)
                                }
                            )
                        }
                    }
                }
            }
            val dragged = drag.action
            if (dragged != null) {
                RemoveOverlay(drag) {
                    QuickActionButton(
                        QuickActionButtonProps(
                            dragged,
                            selected = dragged.isSelected(props.query),
                            onClick = {},
                        )
                    )
                }
            }
        }
    }

/**
 * Место кнопки в ряду. Удержание отрывает кнопку в [RemoveOverlay]; над
 * зоной удаления место схлопывается вместе с отступом [gap] слева, поэтому
 * отступ здесь, а не в `spacedBy` ряда.
 */
@Composable
private fun RemovableSlot(
    action: QuickAction,
    gap: Dp,
    drag: RemoveDrag,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(action)
    val origin = remember { mutableStateOf(Offset.Zero) }
    val dragged = drag.action?.id == action.id
    // Убранная остаётся схлопнутой, пока плагин не уберёт её из списка.
    var removed by remember { mutableStateOf(false) }
    val collapsed = removed || dragged && drag.overZone
    val width = remember { Animatable(1f) }
    LaunchedEffect(collapsed) { width.animateTo(if (collapsed) 0f else 1f) }
    var modifier = Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val gapPx = gap.roundToPx()
        val fullWidth = gapPx + placeable.width
        layout(
            (fullWidth * width.value).roundToInt(),
            placeable.height,
        ) {
            placeable.place(gapPx, 0)
        }
    }
    if (action.onRemove != null) {
        modifier =
            modifier
                .semantics {
                    customActions =
                        listOf(
                            CustomAccessibilityAction(REMOVE_LABEL) {
                                current.onRemove?.invoke()
                                true
                            }
                        )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val longPress =
                            awaitLongPressOrCancellation(down.id)
                                ?: return@awaitEachGesture
                        // Прошлая ещё летит на место или исчезает.
                        if (drag.action != null) return@awaitEachGesture
                        haptics.performHapticFeedback(
                            HapticFeedbackType.LongPress
                        )
                        drag.start(
                            current,
                            origin.value,
                            longPress.position,
                            HIT_SLOP.toPx(),
                            scope,
                        )
                        // В Initial: кнопка не нажмётся, ряд не
                        // прокрутится.
                        while (true) {
                            val change =
                                awaitPointerEvent(PointerEventPass.Initial)
                                    .changes
                                    .firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                change?.consume()
                                break
                            }
                            val wasOverZone = drag.overZone
                            drag.offset += change.positionChange()
                            change.consume()
                            if (!wasOverZone && drag.overZone) {
                                haptics.performHapticFeedback(
                                    HapticFeedbackType
                                        .GestureThresholdActivate
                                )
                            }
                        }
                        val remove = drag.release()
                        scope.launch {
                            if (remove) {
                                haptics.performHapticFeedback(
                                    HapticFeedbackType.Confirm
                                )
                                drag.vanish()
                                removed = true
                                current.onRemove?.invoke()
                            } else drag.settle()
                            drag.end()
                        }
                    }
                }
    }
    Box(modifier) {
        Box(
            Modifier.onGloballyPositioned {
                    origin.value = it.positionOnScreen()
                }
                .graphicsLayer { alpha = if (dragged) 0f else 1f }
        ) {
            content()
        }
    }
}

/**
 * Слой поверх окна на время перетаскивания: [RemoveZone] и кнопка под
 * пальцем. В [Popup], потому что прокрутка ряда и панели обрезают всё за их
 * краями.
 */
@Composable
private fun RemoveOverlay(
    drag: RemoveDrag,
    button: @Composable () -> Unit,
) {
    val zoneAlpha by animateFloatAsState(if (drag.pressed) 1f else 0f)
    Popup(popupPositionProvider = WindowOrigin) {
        Box(
            Modifier.fillMaxSize().onGloballyPositioned {
                drag.overlay = it.positionOnScreen()
            }
        ) {
            Box(
                Modifier.layout { measurable, _ ->
                        val row = drag.row.translate(-drag.overlay)
                        val placeable =
                            measurable.measure(
                                Constraints.fixedWidth(
                                    row.width.roundToInt()
                                )
                            )
                        layout(placeable.width, placeable.height) {
                            placeable.place(
                                row.left.roundToInt(),
                                ((row.top - placeable.height) / 2)
                                    .roundToInt(),
                            )
                        }
                    }
                    .onGloballyPositioned {
                        drag.zone = it.boundsOnScreen()
                    }
                    .graphicsLayer { alpha = zoneAlpha }
            ) {
                RemoveZone(RemoveZoneProps(active = drag.overZone))
            }
            Box(
                Modifier.offset {
                        (drag.origin + drag.offset - drag.overlay).round()
                    }
                    .graphicsLayer {
                        scaleX = drag.scale.value
                        scaleY = drag.scale.value
                        alpha = drag.alpha.value
                    }
            ) {
                button()
            }
        }
    }
}

/**
 * Слой во всё окно. Где именно встанет окно слоя, решает система (с
 * клавиатурой — не в начале окна поиска), поэтому координаты — экранные.
 */
private object WindowOrigin : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ) = IntOffset.Zero
}

/** Оторванная кнопка: где она и как выглядит. Координаты — экранные. */
private class RemoveDrag {
    var action by mutableStateOf<QuickAction?>(null)
        private set

    /** Палец на экране: зона видна. */
    var pressed by mutableStateOf(false)
    var offset by mutableStateOf(Offset.Zero)
    var row by mutableStateOf(Rect.Zero)
    var zone by mutableStateOf(Rect.Zero)

    /** Начало слоя [RemoveOverlay]. */
    var overlay by mutableStateOf(Offset.Zero)

    /** Где была кнопка и где за неё взялись, от её угла. */
    var origin = Offset.Zero
        private set

    private var grab = Offset.Zero
    val scale = Animatable(1f)
    val alpha = Animatable(1f)

    /** Отпущена над зоной и исчезает. */
    var removing by mutableStateOf(false)
        private set

    /** Палец над зоной или рядом с ней, в пределах [HIT_SLOP]. */
    val overZone: Boolean
        get() =
            removing ||
                pressed && (origin + grab + offset) in zone.inflate(hitSlop)

    private var hitSlop = 0f

    fun start(
        action: QuickAction,
        origin: Offset,
        grab: Offset,
        hitSlop: Float,
        scope: CoroutineScope,
    ) {
        this.hitSlop = hitSlop
        this.origin = origin
        this.grab = grab
        offset = Offset.Zero
        pressed = true
        removing = false
        this.action = action
        scope.launch { alpha.snapTo(1f) }
        scope.launch { scale.animateTo(LIFTED_SCALE) }
    }

    /** Палец поднят; true — над зоной, кнопку убрать. */
    fun release(): Boolean {
        removing = overZone
        pressed = false
        return removing
    }

    /** Отпущена мимо зоны: возвращается на место. */
    suspend fun settle() = coroutineScope {
        launch { scale.animateTo(1f) }
        animate(
            typeConverter = Offset.VectorConverter,
            initialValue = offset,
            targetValue = Offset.Zero,
        ) { value, _ ->
            offset = value
        }
    }

    /** Отпущена на зоне: «пуф» — растёт и растворяется. */
    suspend fun vanish() = coroutineScope {
        launch { scale.animateTo(VANISH_SCALE, tween(VANISH_MS)) }
        alpha.animateTo(0f, tween(VANISH_MS))
    }

    fun end() {
        action = null
    }

    private companion object {
        const val LIFTED_SCALE = 1.15f
        const val VANISH_SCALE = 1.8f
        const val VANISH_MS = 200
    }
}

/** Зона удаления ловит палец и чуть мимо неё. */
private val HIT_SLOP = 24.dp

private fun LayoutCoordinates.boundsOnScreen() =
    Rect(positionOnScreen(), size.toSize())

private const val REMOVE_LABEL = "Remove"

// --- Зона удаления ---

class RemoveZoneProps(
    /** Кнопка над зоной: отпущенная, она пропадёт. */
    val active: Boolean
)

/** Ширину и место задаёт [QuickActions]. */
data class RemoveZoneStyle(
    val height: Dp,
    val shape: Shape,
    val color: Color,
    val contentColor: Color,
    val activeColor: Color,
    val activeContentColor: Color,
    val shadowElevation: Dp,
)

val RemoveZone =
    Component<RemoveZoneProps, RemoveZoneStyle>(
        name = "RemoveZone",
        defaultStyle = {
            val colors = MaterialTheme.colorScheme
            RemoveZoneStyle(
                height = 56.dp,
                shape = CircleShape,
                // Непрозрачная: под ней результаты.
                color = colors.errorContainer,
                contentColor = colors.onErrorContainer,
                activeColor = colors.error,
                activeContentColor = colors.onError,
                shadowElevation = 6.dp,
            )
        },
    ) { props, style ->
        val color by
            animateColorAsState(
                if (props.active) style.activeColor else style.color
            )
        val contentColor by
            animateColorAsState(
                if (props.active) style.activeContentColor
                else style.contentColor
            )
        Surface(
            color = color,
            contentColor = contentColor,
            shape = style.shape,
            shadowElevation = style.shadowElevation,
            modifier = Modifier.fillMaxWidth().height(style.height),
        ) {
            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(
                        8.dp,
                        Alignment.CenterHorizontally,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(RemoveIcon, contentDescription = null)
                Text(
                    REMOVE_LABEL,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }

// Material Symbols «delete».
private val RemoveIcon: ImageVector =
    materialIcon(
        "Remove",
        "M16,9v10H8V9h8m-1.5,-6h-5l-1,1H5v2h14V4h-3.5l-1,-1zM18,7H6v12" +
            "c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7z",
    )

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

/** Кнопки плагина в окне поиска, `PluginScope.buttons`. */
class PluginButtons internal constructor(private val scope: PluginScope) {
    /** Кнопка в ряду под результатами, видна всегда. */
    fun quickAction(
        label: String,
        icon: ActionIcon,
        id: String = label,
        isSelected: (SearchQuery) -> Boolean = { false },
        /** См. [QuickAction.onRemove]. */
        onRemove: (() -> Unit)? = null,
        action: Action,
    ) {
        val button =
            QuickAction(
                scope.ownId(id),
                label,
                icon,
                isSelected,
                onRemove,
                action,
            )
        scope.register {
            scope.host.quickActionList.update { it + button }
        }
    }

    /** Кнопка-иконка справа в строке поиска; [label] — для TalkBack. */
    fun searchBar(
        label: String,
        icon: ActionIcon,
        id: String = label,
        action: Action,
    ) {
        val button =
            QuickAction(
                scope.ownId(id),
                label,
                icon,
                isSelected = { false },
                action = action,
            )
        scope.register {
            scope.host.searchBarActionList.update { it + button }
        }
    }
}
