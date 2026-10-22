package com.zemlianikin.flexiblesearch.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class WidgetEngineTest {
    private fun card(id: String) = ResultItem.Card(id, id, null) {}

    private fun TestScope.engine(
        vararg widgets: Pair<String, () -> Flow<List<ResultItem>>>
    ) =
        WidgetEngine(
            widgets.map { (id, provide) ->
                RegisteredWidget(id, null, null, provide)
            },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

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
}
