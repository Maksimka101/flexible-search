package com.zemlianikin.flexiblesearch.core

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.key
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

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

data class ResultsStyle(
    val spacing: Dp,
    val maxRowsPerGroup: Int,
    /** Жёсткость пружины анимации групп: больше — быстрее. */
    val animationStiffness: Float,
)

/**
 * Жёсткость пружин анимации результатов по умолчанию: в 1,5 раза быстрее
 * стандартной у `animateItem` (время пружины ~ 1/√жёсткости).
 */
const val ResultAnimationStiffness = Spring.StiffnessMediumLow * 2.25f

/** `animateItem` с одной жёсткостью для появления, сдвига и исчезания. */
fun LazyItemScope.itemAnimation(stiffness: Float): Modifier =
    Modifier.animateItem(
        fadeInSpec = spring(stiffness = stiffness),
        placementSpec =
            spring(
                stiffness = stiffness,
                visibilityThreshold = IntOffset.VisibilityThreshold,
            ),
        fadeOutSpec = spring(stiffness = stiffness),
    )

val Results =
    Component<ResultsProps, ResultsStyle>(
        name = "Results",
        defaultStyle = {
            ResultsStyle(
                spacing = 8.dp,
                maxRowsPerGroup = 3,
                animationStiffness = ResultAnimationStiffness,
            )
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
                // Группы, которые появились или пропали, не сдвигают
                // соседей рывком.
                Box(itemAnimation(style.animationStiffness)) {
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
    }

// --- Виджеты пустой строки: снизу вверх, первый — у клавиатуры ---

class WidgetAreaProps(
    val widgets: List<ResultGroup>,
    val onClick: (ResultItem) -> Unit,
    val onFill: (String) -> Unit,
)

data class WidgetAreaStyle(val spacing: Dp, val animationStiffness: Float)

/** Список; pager или сетку можно сделать плагином через `override`. */
val WidgetArea =
    Component<WidgetAreaProps, WidgetAreaStyle>(
        name = "WidgetArea",
        defaultStyle = {
            WidgetAreaStyle(
                spacing = 8.dp,
                animationStiffness = ResultAnimationStiffness,
            )
        },
    ) { props, style ->
        LazyColumn(
            reverseLayout = true,
            verticalArrangement =
                Arrangement.spacedBy(style.spacing, Alignment.Bottom),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(props.widgets, key = { it.key }) { widget ->
                Box(itemAnimation(style.animationStiffness)) {
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
