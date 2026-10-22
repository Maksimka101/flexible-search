package com.zemlianikin.flexiblesearch.poc.components.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class SearchEngineTest {
    private fun card(id: String) = ResultItem.Card(id, id, null) {}

    private fun results(vararg ids: String) = ids.map(::card)

    private fun TestScope.engine(
        vararg providers: Pair<String, ResultProvider>
    ) =
        SearchEngine(
            providers.map { (id, provide) ->
                RegisteredProvider(id, null, null, null, provide)
            },
            dispatcher = StandardTestDispatcher(testScheduler),
            providerTimeLimitMs = { 1_000 },
        )

    private fun SearchState.ids() = items.map { it.id }

    private suspend fun Flow<SearchState>.final() = last()

    @Test
    fun groupsFollowRegistrationOrderNotArrival() = runTest {
        val disk: ResultProvider = {
            flow {
                delay(100)
                emit(results("cached"))
                delay(200)
                emit(results("cached", "network"))
            }
        }
        val calculator: ResultProvider = { flowOf(results("sum")) }
        val state =
            engine("disk" to disk, "calculator" to calculator)
                .results(flowOf(SearchQuery("q")))
                .final()
        assertEquals(
            listOf("disk", "calculator"),
            state.groups.map { it.pluginId },
        )
        assertEquals(listOf("cached", "network", "sum"), state.ids())
        assertFalse(state.loading)
    }

    @Test
    fun emptyQueryGoesOnlyToOnEmptyProviders() = runTest {
        val calc = Trigger("calc", "Calc", listOf("calc"), null)
        fun provider(
            id: String,
            trigger: Trigger? = null,
            onEmpty: Boolean = false,
        ) =
            RegisteredProvider(
                id,
                trigger,
                null,
                null,
                { flowOf(results(id)) },
                onEmpty = onEmpty,
            )
        val engine =
            SearchEngine(
                listOf(
                    provider("recent", onEmpty = true),
                    provider("files"),
                    provider("history", calc, onEmpty = true),
                ),
                dispatcher = StandardTestDispatcher(testScheduler),
            )
        val empty = engine.results(flowOf(SearchQuery(""))).final()
        assertEquals(listOf("recent"), empty.ids())
        val typed = engine.results(flowOf(SearchQuery("q"))).final()
        assertEquals(listOf("recent", "files"), typed.ids())
    }

    @Test
    fun onlyTriggeredGroupsAreComplete() = runTest {
        val files = Trigger("files", "Files", listOf("files"), null)
        val provider: ResultProvider = { flowOf(results("a")) }
        val engine =
            SearchEngine(
                listOf(
                    RegisteredProvider(
                        "files",
                        null,
                        "Files",
                        files,
                        provider,
                    ),
                    RegisteredProvider(
                        "files",
                        files,
                        "Files",
                        null,
                        provider,
                    ),
                ),
                dispatcher = StandardTestDispatcher(testScheduler),
            )
        suspend fun group(query: SearchQuery) =
            engine.results(flowOf(query)).final().groups.single()
        assertFalse(group(SearchQuery("a")).complete)
        assertEquals(files, group(SearchQuery("a")).fullList)
        assertTrue(group(SearchQuery("a", files)).complete)
    }

    @Test
    fun loadingUntilEveryProviderAnswers() = runTest {
        val fast: ResultProvider = { flowOf(results("fast")) }
        val slow: ResultProvider = {
            flow {
                delay(500)
                emit(results("slow"))
            }
        }
        val states = mutableListOf<SearchState>()
        backgroundScope.launch {
            engine("fast" to fast, "slow" to slow)
                .results(flowOf(SearchQuery("q")))
                .collect { states += it }
        }
        advanceTimeBy(100)
        runCurrent()
        assertEquals(listOf("fast"), states.last().ids())
        assertTrue(states.last().loading)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(listOf("fast", "slow"), states.last().ids())
        assertFalse(states.last().loading)
    }

    @Test
    fun laterEmitsDoNotHoldLoading() = runTest {
        val disk: ResultProvider = {
            flow {
                emit(results("cached"))
                delay(500)
                emit(results("cached", "disk"))
            }
        }
        val states = mutableListOf<SearchState>()
        backgroundScope.launch {
            engine("disk" to disk)
                .results(flowOf(SearchQuery("q")))
                .collect { states += it }
        }
        advanceTimeBy(100)
        runCurrent()
        assertEquals(listOf("cached"), states.last().ids())
        assertFalse(states.last().loading)
        assertTrue(states.last().groups.single().loading)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(listOf("cached", "disk"), states.last().ids())
        assertFalse(states.last().groups.single().loading)
    }

    @Test
    fun silentProviderHoldsLoadingUntilTimeLimit() = runTest {
        val silent: ResultProvider = { flow { awaitCancellation() } }
        val states = mutableListOf<SearchState>()
        backgroundScope.launch {
            engine("silent" to silent)
                .results(flowOf(SearchQuery("q")))
                .collect { states += it }
        }
        advanceTimeBy(900)
        runCurrent()
        assertTrue(states.last().loading)
        advanceTimeBy(200)
        runCurrent()
        assertFalse(states.last().loading)
    }

    @Test
    fun timeLimitIsReadForEveryQuery() = runTest {
        var limitMs = 1_000L
        val slow: ResultProvider = {
            flow {
                delay(2_000)
                emit(results("slow"))
            }
        }
        val engine =
            SearchEngine(
                listOf(RegisteredProvider("slow", null, null, null, slow)),
                dispatcher = StandardTestDispatcher(testScheduler),
                providerTimeLimitMs = { limitMs },
            )
        suspend fun ids() =
            engine.results(flowOf(SearchQuery("q"))).final().ids()
        assertEquals(emptyList(), ids())
        limitMs = 3_000
        assertEquals(listOf("slow"), ids())
    }

    @Test
    fun previousResultsStayWhileNewQueryLoads() = runTest {
        val echo: ResultProvider = { query ->
            flow {
                delay(100)
                emit(results(query))
            }
        }
        val requests = MutableStateFlow(SearchQuery("a"))
        val states = mutableListOf<SearchState>()
        backgroundScope.launch {
            engine("echo" to echo).results(requests).collect {
                states += it
            }
        }
        advanceTimeBy(200)
        requests.value = SearchQuery("ab")
        runCurrent()
        assertEquals(listOf("a"), states.last().ids())
        assertTrue(states.last().loading)
        advanceTimeBy(200)
        assertEquals(listOf("ab"), states.last().ids())
    }

    @Test
    fun failedOrStuckProviderKeepsItsLastResults() = runTest {
        val failing: ResultProvider = {
            flow {
                emit(results("before error"))
                error("boom")
            }
        }
        val stuck: ResultProvider = {
            flow {
                emit(results("before limit"))
                awaitCancellation()
            }
        }
        val state =
            engine("failing" to failing, "stuck" to stuck)
                .results(flowOf(SearchQuery("q")))
                .final()
        assertEquals(listOf("before error", "before limit"), state.ids())
        assertFalse(state.loading)
    }

    @Test
    fun sameIdFromDifferentPluginsIsShownTwice() = runTest {
        val provider: ResultProvider = { flowOf(results("same", "same")) }
        val state =
            engine("first" to provider, "second" to provider)
                .results(flowOf(SearchQuery("q")))
                .final()
        assertEquals(listOf("same", "same"), state.ids())
    }

    @Test
    fun triggerRunsOnlyItsProvidersEvenWithEmptyText() = runTest {
        val calc = Trigger("calc", "Calculator", listOf("calc"), null)
        val global: ResultProvider = { flowOf(results("global:$it")) }
        val triggered: ResultProvider = { flowOf(results("calc:$it")) }
        val engine =
            SearchEngine(
                listOf(
                    RegisteredProvider("global", null, null, null, global),
                    RegisteredProvider("calc", calc, null, null, triggered),
                ),
                dispatcher = StandardTestDispatcher(testScheduler),
            )
        suspend fun ids(query: SearchQuery) =
            engine.results(flowOf(query)).final().ids()
        assertEquals(listOf("calc:"), ids(SearchQuery("", calc)))
        assertEquals(listOf("calc:2"), ids(SearchQuery("2", calc)))
        assertEquals(listOf("global:2"), ids(SearchQuery("2")))
        assertEquals(emptyList(), ids(SearchQuery(" ")))
    }
}
