package com.zemlianikin.flexiblesearch.poc.components.core

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// Компоненты по умолчанию. Плагин меняет их через PluginScope.style /
// override.

// --- Экран: только расставляет детей, как Scaffold. Строку вниз —
// переопределить его. ---

class SearchLayoutProps(
    val searchBar: @Composable () -> Unit,
    val loading: @Composable () -> Unit,
    val results: @Composable () -> Unit,
    /** Виджеты на пустой строке, над результатами. */
    val widgets: @Composable () -> Unit,
    /**
     * Строка пустая и без префикса: показать [widgets]. Результаты тоже
     * бывают — от провайдеров с `onEmpty`.
     */
    val showWidgets: Boolean,
    /** Ряд кнопок у клавиатуры, виден всегда. */
    val quickActions: @Composable () -> Unit,
    val onDismiss: () -> Unit,
)

data class SearchLayoutStyle(
    val scrim: Color,
    val padding: PaddingValues,
    val spacing: Dp,
)

val SearchLayout =
    Component<SearchLayoutProps, SearchLayoutStyle>(
        name = "SearchLayout",
        defaultStyle = {
            SearchLayoutStyle(
                Color.Black.copy(alpha = 0.45f),
                PaddingValues(12.dp),
                12.dp,
            )
        },
    ) { props, style ->
        Box(
            Modifier.fillMaxSize()
                .background(style.scrim)
                // Нажатие мимо результатов закрывает поиск, как в Pixel
                // Search.
                .clickable(
                    remember { MutableInteractionSource() },
                    indication = null,
                    onClick = props.onDismiss,
                )
                .safeDrawingPadding()
                .imePadding()
                .padding(style.padding)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(style.spacing)
            ) {
                props.searchBar()
                // Под строкой: появляясь, он сжимает область результатов
                // сверху, а прижатые к низу результаты не двигаются.
                props.loading()
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (props.showWidgets) {
                        Column(
                            verticalArrangement =
                                Arrangement.spacedBy(style.spacing)
                        ) {
                            // Результаты у клавиатуры меряются первыми,
                            // виджетам — что осталось.
                            Box(Modifier.weight(1f, fill = false)) {
                                props.widgets()
                            }
                            props.results()
                        }
                    } else {
                        props.results()
                    }
                }
                props.quickActions()
            }
        }
    }

// --- Загрузка ---

class LoadingIndicatorProps(val loading: Boolean)

data class LoadingIndicatorStyle(
    val color: Color,
    val trackColor: Color,
    /** Быстрые ответы не показывают индикатор вовсе. */
    val showDelayMs: Long,
    /** Показанный индикатор не мелькает, даже если загрузка кончилась. */
    val minVisibleMs: Long,
)

val LoadingIndicator =
    Component<LoadingIndicatorProps, LoadingIndicatorStyle>(
        name = "LoadingIndicator",
        defaultStyle = {
            LoadingIndicatorStyle(
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainer,
                showDelayMs = 300,
                minVisibleMs = 500,
            )
        },
    ) { props, style ->
        val visible =
            delayedLoading(
                props.loading,
                style.showDelayMs,
                style.minVisibleMs,
            )
        if (visible) {
            LinearProgressIndicator(
                color = style.color,
                trackColor = style.trackColor,
                modifier =
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )
        }
    }

/**
 * Видна ли загрузка: появляется, если [loading] держится дольше
 * [showDelayMs], и не пропадает раньше [minVisibleMs].
 */
@Composable
fun delayedLoading(
    loading: Boolean,
    showDelayMs: Long,
    minVisibleMs: Long,
): Boolean {
    var visible by remember { mutableStateOf(false) }
    var shownAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(loading) {
        if (loading && !visible) {
            delay(showDelayMs)
            visible = true
            shownAt = SystemClock.uptimeMillis()
        } else if (!loading && visible) {
            delay(shownAt + minVisibleMs - SystemClock.uptimeMillis())
            visible = false
        }
    }
    return visible
}

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

private val SearchIcon: ImageVector =
    materialIcon(
        "Search",
        "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 " +
            "16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16" +
            "c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99" +
            "L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5" +
            "S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z",
    )

// --- Список результатов: группы снизу вверх, первая — у клавиатуры ---

class ResultsProps(
    val groups: List<ResultGroup>,
    /** Индекс среди всех результатов того, что сработает по Enter. */
    val targetIndex: Int = 0,
    val onClick: (ResultItem) -> Unit,
    /** `↖` у подсказки: подставить текст в строку. */
    val onFill: (String) -> Unit,
    /** «All ›» у группы: включить её триггер. */
    val onShowAll: (Trigger) -> Unit,
)

data class ResultsStyle(val spacing: Dp, val maxRowsPerGroup: Int)

val Results =
    Component<ResultsProps, ResultsStyle>(
        name = "Results",
        defaultStyle = {
            ResultsStyle(spacing = 8.dp, maxRowsPerGroup = 3)
        },
    ) { props, style ->
        val target =
            props.groups.flatMap { it.items }.getOrNull(props.targetIndex)
        // Обычно снизу вверх: первая группа у клавиатуры. Полный список
        // группы (её префикс) — сверху вниз, иначе виден его конец.
        val reverse = props.groups.none { it.complete }
        val groups = props.groups.withSharedAppRow()
        LazyColumn(
            reverseLayout = reverse,
            verticalArrangement =
                Arrangement.spacedBy(
                    style.spacing,
                    if (reverse) Alignment.Bottom else Alignment.Top,
                ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(groups, key = { it.key }) { group ->
                ResultSection(
                    ResultSectionProps(
                        group,
                        target,
                        maxRows =
                            if (group.complete) Int.MAX_VALUE
                            else style.maxRowsPerGroup,
                        props.onClick,
                        props.onFill,
                        props.onShowAll,
                    )
                )
            }
        }
    }

// --- Виджеты пустой строки: снизу вверх, первый — у клавиатуры ---

class WidgetAreaProps(
    val widgets: List<ResultGroup>,
    val onClick: (ResultItem) -> Unit,
    val onFill: (String) -> Unit,
)

data class WidgetAreaStyle(val spacing: Dp)

/** Список; pager или сетку можно сделать плагином через `override`. */
val WidgetArea =
    Component<WidgetAreaProps, WidgetAreaStyle>(
        name = "WidgetArea",
        defaultStyle = { WidgetAreaStyle(spacing = 8.dp) },
    ) { props, style ->
        LazyColumn(
            reverseLayout = true,
            verticalArrangement =
                Arrangement.spacedBy(style.spacing, Alignment.Bottom),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(props.widgets, key = { it.key }) { widget ->
                ResultSection(
                    ResultSectionProps(
                        widget,
                        target = null,
                        maxRows = Int.MAX_VALUE,
                        props.onClick,
                        props.onFill,
                        onShowAll = {},
                    )
                )
            }
        }
    }

class ResultSectionProps(
    val group: ResultGroup,
    val target: ResultItem?,
    val maxRows: Int,
    val onClick: (ResultItem) -> Unit,
    val onFill: (String) -> Unit,
    val onShowAll: (Trigger) -> Unit,
)

data class ResultSectionStyle(
    val containerColor: Color,
    val shape: Shape,
    val spacing: Dp,
)

/**
 * Группа одного провайдера: приложения — рядом иконок, карточки — сами по
 * себе, строки и подсказки — в общей подложке с заголовком.
 */
val ResultSection =
    Component<ResultSectionProps, ResultSectionStyle>(
        name = "ResultSection",
        defaultStyle = {
            ResultSectionStyle(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(24.dp),
                spacing = 8.dp,
            )
        },
    ) { props, style ->
        val group = props.group
        val apps = group.items.filterIsInstance<ResultItem.App>()
        // Карточки и своя отрисовка — каждая сама по себе, по порядку.
        val blocks =
            group.items.filter {
                it is ResultItem.Card || it is ResultItem.Custom
            }
        val rows =
            group.items.filter {
                it is ResultItem.Row || it is ResultItem.Suggestion
            }
        Column(verticalArrangement = Arrangement.spacedBy(style.spacing)) {
            for (block in blocks) {
                when (block) {
                    is ResultItem.Card ->
                        ResultCard(
                            ResultCardProps(
                                block,
                                highlighted = block == props.target,
                            ) {
                                props.onClick(block)
                            }
                        )
                    is ResultItem.Custom ->
                        block.content(LocalActionScope.current)
                    else -> {}
                }
            }
            if (rows.isNotEmpty()) {
                Surface(
                    color = style.containerColor,
                    shape = style.shape,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        val fullList = group.fullList
                        group.title?.let { title ->
                            val hidden = rows.size > props.maxRows
                            GroupHeader(
                                GroupHeaderProps(
                                    title,
                                    group.icon,
                                    group.loading,
                                    onShowAll =
                                        fullList
                                            ?.takeIf { hidden }
                                            ?.let {
                                                { props.onShowAll(it) }
                                            },
                                )
                            )
                        }
                        for (row in rows.take(props.maxRows)) {
                            val highlighted = row == props.target
                            when (row) {
                                is ResultItem.Row ->
                                    ResultRow(
                                        ResultRowProps(
                                            row,
                                            key =
                                                "${group.pluginId}/${row.id}",
                                            highlighted,
                                        ) {
                                            props.onClick(row)
                                        }
                                    )
                                is ResultItem.Suggestion ->
                                    SuggestionRow(
                                        SuggestionRowProps(
                                            row,
                                            highlighted,
                                            onFill = {
                                                props.onFill(row.text)
                                            },
                                        ) {
                                            props.onClick(row)
                                        }
                                    )
                                else -> {}
                            }
                        }
                    }
                }
            }
            if (apps.isNotEmpty()) {
                AppRow(AppRowProps(apps, props.target, props.onClick))
            }
        }
    }

class GroupHeaderProps(
    val title: String,
    val icon: ActionIcon?,
    /** Провайдер группы ещё догружает результаты. */
    val loading: Boolean,
    /** null — вся группа уже видна или полного списка нет. */
    val onShowAll: (() -> Unit)?,
)

data class GroupHeaderStyle(
    val titleStyle: TextStyle,
    val titleColor: Color,
    /** Тайминги индикатора догрузки, как у [LoadingIndicatorStyle]. */
    val loadingShowDelayMs: Long,
    val loadingMinVisibleMs: Long,
)

val GroupHeader =
    Component<GroupHeaderProps, GroupHeaderStyle>(
        name = "GroupHeader",
        defaultStyle = {
            GroupHeaderStyle(
                titleStyle = MaterialTheme.typography.labelLarge,
                titleColor = MaterialTheme.colorScheme.primary,
                loadingShowDelayMs = 300,
                loadingMinVisibleMs = 500,
            )
        },
    ) { props, style ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier.fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp)
                    .height(36.dp),
        ) {
            props.icon?.let {
                ActionIconImage(
                    it,
                    Modifier.size(18.dp),
                    tint = style.titleColor,
                )
                Spacer(Modifier.size(8.dp))
            }
            Text(
                props.title,
                style = style.titleStyle,
                color = style.titleColor,
                modifier = Modifier.weight(1f),
            )
            val loading =
                delayedLoading(
                    props.loading,
                    style.loadingShowDelayMs,
                    style.loadingMinVisibleMs,
                )
            if (loading) {
                CircularProgressIndicator(
                    color = style.titleColor,
                    strokeWidth = 2.dp,
                    modifier = Modifier.padding(end = 12.dp).size(16.dp),
                )
            }
            props.onShowAll?.let {
                TextButton(onClick = it) { Text("All ›") }
            }
        }
    }

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
private fun ActionIconImage(
    icon: ActionIcon,
    modifier: Modifier,
    tint: Color = LocalContentColor.current,
) {
    when (icon) {
        is ActionIcon.Bitmap -> Image(icon.image, null, modifier)
        is ActionIcon.Vector -> Icon(icon.image, null, modifier, tint)
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
)

val AppRow =
    Component<AppRowProps, AppRowStyle>(
        name = "AppRow",
        defaultStyle = {
            AppRowStyle(
                MaterialTheme.colorScheme.surfaceContainer,
                RoundedCornerShape(24.dp),
                maxApps = 5,
            )
        },
    ) { props, style ->
        Surface(
            color = style.containerColor,
            shape = style.shape,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(8.dp)) {
                for (app in props.apps.take(style.maxApps)) {
                    AppIcon(
                        AppIconProps(
                            app,
                            app == props.target,
                            Modifier.weight(1f),
                        ) {
                            props.onClick(app)
                        }
                    )
                }
                // Пустые места, чтобы иконки не растягивались при 1–4
                // результатах.
                repeat(style.maxApps - props.apps.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
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
                size = 52.dp,
                iconSize = 28.dp,
                containerColor = colors.surfaceContainer,
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
