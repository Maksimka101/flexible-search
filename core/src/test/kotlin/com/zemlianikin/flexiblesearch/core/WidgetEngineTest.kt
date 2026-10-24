package com.zemlianikin.flexiblesearch.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class WidgetEngineTest {
    private fun card(id: String) = ResultItem.Card(id, id, null) {}

    private fun TestScope.engine(
        vararg widgets: Pair<String, () -> Flow<List<ResultItem>>>
    ) =
        WidgetEngine(
            flowOf(
                widgets.map { (id, provide) ->
                    RegisteredWidget(id, null, null, provide)
                }
            ),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

    private fun WidgetEngine.groups() = state().map { it.groups }

    private fun List<ResultGroup>.ids() = map { it.pluginId }

    @Test
    fun widgetsFollowRegistrationOrder() = runTest {
        val slow = {
            flow {
                delay(100)
                emit(listOf(card("slow")))
            }
        }
        val fast = { flowOf(listOf(card("fast"))) }
        val groups = engine("slow" to slow, "fast" to fast).groups().last()
        assertEquals(listOf("slow", "fast"), groups.ids())
    }

    @Test
    fun noWidgetsIsReadyAtOnce() = runTest {
        val state = engine().state().toList().single()
        assertEquals(emptyList(), state.groups)
        assertEquals(false, state.loading)
    }

    @Test
    fun emptyListHidesWidget() = runTest {
        val clipboard = {
            flow {
                emit(listOf(card("link")))
                delay(100)
                emit(emptyList())
            }
        }
        val updates = engine("clipboard" to clipboard).groups().toList()
        assertEquals(
            listOf(listOf("clipboard"), emptyList()),
            updates.map { it.ids() },
        )
    }

    @Test
    fun liveWidgetHasNoTimeLimit() = runTest {
        val timer = {
            flow {
                for (second in 1..10) {
                    emit(listOf(card("$second")))
                    delay(60_000)
                }
            }
        }
        val groups = engine("timer" to timer).groups().last()
        assertEquals("10", groups.single().items.single().id)
    }

    @Test
    fun failedWidgetIsHidden() = runTest {
        val broken = {
            flow {
                emit(listOf(card("weather")))
                delay(100)
                error("network")
            }
        }
        val fine = { flowOf(listOf(card("toggles"))) }
        val groups =
            engine("broken" to broken, "fine" to fine).groups().last()
        assertEquals(listOf("fine"), groups.ids())
    }

    @Test
    fun errorHidesOnlyThatWidget() = runTest {
        val todo = { flow<List<ResultItem>> { TODO() } }
        val fine = { flowOf(listOf(card("toggles"))) }
        val groups = engine("todo" to todo, "fine" to fine).groups().last()
        assertEquals(listOf("fine"), groups.ids())
    }

    @Test
    fun lateWidgetIsCollected() = runTest {
        fun widget(id: String) =
            RegisteredWidget(id, null, null) { flowOf(listOf(card(id))) }
        val widgets = MutableStateFlow(listOf(widget("early")))
        val updates = mutableListOf<List<ResultGroup>>()
        backgroundScope.launch {
            WidgetEngine(
                    widgets,
                    dispatcher = StandardTestDispatcher(testScheduler),
                )
                .groups()
                .collect { updates += it }
        }
        runCurrent()
        assertEquals(listOf("early"), updates.last().ids())
        widgets.value += widget("late")
        runCurrent()
        assertEquals(listOf("early", "late"), updates.last().ids())
    }

    @Test
    fun reopenedWindowKeepsPreviousAnswer() = runTest {
        var weather = "sunny"
        val slow = {
            flow {
                delay(100)
                emit(listOf(card(weather)))
            }
        }
        val engine = engine("weather" to slow)
        engine.state().last()
        weather = "rain"
        val updates = engine.state().toList()
        assertEquals(
            listOf("sunny" to true, "rain" to false),
            updates.map {
                it.groups.single().items.single().id to it.loading
            },
        )
    }
}
