package com.zemlianikin.flexiblesearch.core.screens

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.zemlianikin.flexiblesearch.core.PluginScope
import com.zemlianikin.flexiblesearch.core.buttons.materialIcon
import com.zemlianikin.flexiblesearch.core.component.Component
import com.zemlianikin.flexiblesearch.core.results.SearchWindow
import kotlinx.coroutines.flow.update

/**
 * Экран плагина поверх поиска: настройки, подробности. Регистрируется через
 * [PluginScreens.add], открывается [Navigator.open]; «назад» возвращает на
 * предыдущий.
 */
class Screen
internal constructor(
    val id: String,
    val title: String,
    internal val content: @Composable ScreenScope.() -> Unit,
)

interface Navigator {
    /** Открыть поверх текущего. */
    fun open(screen: Screen)

    /** Вернуться на предыдущий экран. */
    fun back()
}

/** То, что доступно содержимому [Screen]. */
interface ScreenScope : SearchWindow, Navigator

// --- Стопка экранов и переходы между ними ---

/** Переход между экранами — `ContentTransform` у `AnimatedContent`. */
typealias ScreenTransition =
    AnimatedContentTransitionScope<*>.() -> ContentTransform

/** То же для жеста «назад»; `swipeEdge` — край, с которого он начат. */
typealias PredictiveScreenTransition =
    AnimatedContentTransitionScope<*>.(swipeEdge: Int) -> ContentTransform

class ScreenStackProps(
    /** Id экранов снизу вверх, верхний на экране. */
    val routes: List<String>,
    val onBack: () -> Unit,
    val content: @Composable (route: String) -> Unit,
)

/**
 * [push] — открытие, [pop] — «назад» кнопкой, [predictivePop] — системным
 * жестом или долгим нажатием «назад»: он прокручивается по прогрессу, но
 * только до доли [peek]; отпустили — доигрывается до конца, отменили —
 * обратно. [scrim] — тень верхнего экрана на нижнем, прозрачный — без тени.
 * [corner] — скругление уходящего экрана к доле [peek]. Тень и скругление
 * меняются за [SCREEN_TRANSITION_MS].
 */
data class ScreenStackStyle(
    val push: ScreenTransition,
    val pop: ScreenTransition,
    val predictivePop: PredictiveScreenTransition,
    val peek: Float,
    val scrim: Color,
    val corner: Dp,
)

const val SCREEN_TRANSITION_MS = 300

/**
 * Экраны друг над другом. Новый въезжает справа, нижний отъезжает влево на
 * четверть и темнеет. «Назад» — верхний уменьшается к точке у правого края,
 * с отступом от него, и тает; нижний стоит на месте.
 */
val ScreenStack =
    Component<ScreenStackProps, ScreenStackStyle>(
        name = "ScreenStack",
        defaultStyle = {
            ScreenStackStyle(
                SlidePush,
                ScalePop,
                { scalePop() },
                peek = DEFAULT_PEEK,
                scrim = Color.Black.copy(alpha = 0.25f),
                corner = 28.dp,
            )
        },
    ) { props, style ->
        // NavDisplay прокручивает переход по прогрессу «назад» один к
        // одному, и прогресс 1 (долгое нажатие кнопки) уводит экран
        // целиком. Поэтому он слушает свой диспетчер, а системный «назад»
        // приходит в него с прогрессом, урезанным до peek. Экранам —
        // системный диспетчер, их обработчики «назад» работают как раньше.
        val system =
            checkNotNull(LocalNavigationEventDispatcherOwner.current)
        val own = rememberNavigationEventDispatcherOwner(parent = null)
        val peek by rememberUpdatedState(style.peek)
        val handler = remember(own) { PeekBackHandler(own) { peek } }
        SideEffect { handler.isBackEnabled = props.routes.size > 1 }
        DisposableEffect(system, handler) {
            system.navigationEventDispatcher.addHandler(handler)
            onDispose { handler.remove() }
        }
        // Верхний — тот, что въезжает или уходит: тень и скругление только
        // у него. Пока жест не завершён, это последний; после «назад» —
        // снятые со стопки.
        val popped = remember { PoppedRoutes() }.update(props.routes)
        val isTop = { route: String ->
            if (popped.isEmpty()) route == props.routes.last()
            else route in popped
        }
        CompositionLocalProvider(
            LocalNavigationEventDispatcherOwner provides own
        ) {
            NavDisplay(
                backStack = props.routes,
                onBack = props.onBack,
                transitionSpec = style.push,
                popTransitionSpec = style.pop,
                predictivePopTransitionSpec = style.predictivePop,
                entryProvider = { route ->
                    NavEntry(route) {
                        CompositionLocalProvider(
                            LocalNavigationEventDispatcherOwner provides
                                system
                        ) {
                            Box(
                                Modifier.fillMaxSize()
                                    .transitionChrome(
                                        isTop(route),
                                        style,
                                    )
                            ) {
                                props.content(route)
                            }
                        }
                    }
                },
            )
        }
    }

/** Снятые со стопки последним изменением [ScreenStackProps.routes]. */
private class PoppedRoutes {
    private var routes = emptyList<String>()
    private var popped = emptySet<String>()

    fun update(next: List<String>): Set<String> {
        if (next != routes) {
            popped = routes.toSet() - next.toSet()
            routes = next
        }
        return popped
    }
}

/** Пересылает «назад» в [own] с прогрессом, умноженным на [peek]. */
private class PeekBackHandler(
    own: NavigationEventDispatcherOwner,
    private val peek: () -> Float,
) :
    NavigationEventHandler<NavigationEventInfo>(
        NavigationEventInfo.None,
        isBackEnabled = false,
    ) {
    private val input =
        DirectNavigationEventInput().also {
            own.navigationEventDispatcher.addInput(it)
        }

    override fun onBackStarted(event: NavigationEvent) =
        input.backStarted(event.scaled())

    override fun onBackProgressed(event: NavigationEvent) =
        input.backProgressed(event.scaled())

    override fun onBackCompleted() = input.backCompleted()

    override fun onBackCancelled() = input.backCancelled()

    private fun NavigationEvent.scaled() =
        NavigationEvent(
            swipeEdge,
            progress * peek(),
            touchX,
            touchY,
            frameTimeMillis,
        )
}

private const val DEFAULT_PEEK = 0.25f

// Медленный старт: первый кадр нового экрана долгий, и часы анимации
// убегают за него вперёд — при быстром старте он появляется уже на трети
// пути.
private val SlidePush: ScreenTransition = {
    val spec =
        tween<IntOffset>(SCREEN_TRANSITION_MS, easing = FastOutSlowInEasing)
    slideInHorizontally(spec) { it } togetherWith
        slideOutHorizontally(spec) { -it / 4 }
}

// Точка, к которой уменьшается уходящий экран: правее центра, и справа
// остаётся отступ — (1 - 0.8) от уменьшения, 2% ширины при 0.9.
private val PopOrigin = TransformOrigin(0.8f, 0.5f)

/**
 * Экран уменьшается равномерно весь переход, к доле [peek] — до 0.9, это
 * видно, пока держат «назад». После [peek] равномерно тает, продолжая
 * уменьшаться, и пропадает к 70% перехода.
 */
private fun scalePop(peek: Float = DEFAULT_PEEK): ContentTransform {
    val peekMs = (SCREEN_TRANSITION_MS * peek).toInt()
    return EnterTransition.None togetherWith
        scaleOut(
            tween(SCREEN_TRANSITION_MS, easing = LinearEasing),
            targetScale = 1f - 0.1f / peek,
            transformOrigin = PopOrigin,
        ) +
            fadeOut(
                keyframes {
                    durationMillis = SCREEN_TRANSITION_MS
                    1f at peekMs using LinearEasing
                    0f at SCREEN_TRANSITION_MS * 7 / 10
                }
            )
}

private val ScalePop: ScreenTransition = { scalePop() }

/**
 * Тень и скругление верхнего экрана, пока он въезжает или уходит. Тень — за
 * ним с запасом во все стороны: видна там, где он не закрывает нижний, —
 * слева, пока въезжает, и вокруг, пока уменьшается.
 */
@Composable
private fun Modifier.transitionChrome(
    isTop: Boolean,
    style: ScreenStackStyle,
): Modifier {
    if (!isTop) return this
    val transition = LocalNavAnimatedContentScope.current.transition
    val shown by
        transition.animateFloat({
            tween(SCREEN_TRANSITION_MS, easing = LinearEasing)
        }) {
            if (it == EnterExitState.Visible) 1f else 0f
        }
    val leaving by
        transition.animateFloat({
            keyframes {
                durationMillis = SCREEN_TRANSITION_MS
                1f at (SCREEN_TRANSITION_MS * style.peek).toInt()
            }
        }) {
            if (it == EnterExitState.PostExit) 1f else 0f
        }
    val moving = transition.currentState != transition.targetState
    val scrim = style.scrim
    return drawBehind {
        if (moving && scrim.alpha > 0f) {
            drawRect(
                scrim.copy(alpha = scrim.alpha * shown),
                topLeft = Offset(-size.width, -size.height),
                size = size * 3f,
            )
        }
    }
        .graphicsLayer {
            if (leaving > 0f) {
                shape = RoundedCornerShape(style.corner * leaving)
                clip = true
            }
        }
}

// --- Обёртка экрана: заголовок и «назад» ---

class ScreenLayoutProps(
    val title: String,
    val onBack: () -> Unit,
    val content: @Composable () -> Unit,
)

data class ScreenLayoutStyle(
    val background: Color,
    val titleColor: Color,
)

@OptIn(ExperimentalMaterial3Api::class)
val ScreenLayout =
    Component<ScreenLayoutProps, ScreenLayoutStyle>(
        name = "ScreenLayout",
        defaultStyle = {
            val colors = MaterialTheme.colorScheme
            ScreenLayoutStyle(colors.surface, colors.onSurface)
        },
    ) { props, style ->
        Scaffold(
            containerColor = style.background,
            topBar = {
                TopAppBar(
                    title = { Text(props.title) },
                    navigationIcon = {
                        IconButton(onClick = props.onBack) {
                            Icon(BackIcon, contentDescription = "Back")
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = style.background,
                            titleContentColor = style.titleColor,
                            navigationIconContentColor = style.titleColor,
                        ),
                )
            },
        ) { padding ->
            Box(
                Modifier.fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
            ) {
                props.content()
            }
        }
    }

// Material Icons «arrow_back».
private val BackIcon: ImageVector =
    materialIcon(
        "Back",
        "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z",
    )

/** Экраны плагина и его настройки, `PluginScope.screens`. */
class PluginScreens internal constructor(private val scope: PluginScope) {
    /**
     * Свой экран поверх поиска, с заголовком [title] и «назад»:
     * ```
     * val details = screens.add("Details") { Text("...") }
     * buttons.quickAction("Details", icon) { open(details) }
     * ```
     */
    fun add(
        title: String,
        id: String = title,
        content: @Composable ScreenScope.() -> Unit,
    ): Screen =
        Screen(scope.ownId(id), title, content).also { screen ->
            scope.register {
                scope.host.screenMap.update { it + (screen.id to screen) }
            }
        }

    /**
     * Нажатие на плагин в списке плагинов; без него строка только выключает
     * плагин. Обычно — свой экран:
     * ```
     * val settings = screens.add("Settings") { ... }
     * screens.settings { open(settings) }
     * ```
     */
    fun settings(action: ScreenScope.() -> Unit) = scope.register {
        scope.host.settingsMap.update { it + (scope.pluginId to action) }
    }
}
