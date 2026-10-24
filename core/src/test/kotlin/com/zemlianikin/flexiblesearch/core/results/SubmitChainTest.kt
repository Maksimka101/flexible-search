package com.zemlianikin.flexiblesearch.core.results

import android.content.Context
import com.zemlianikin.flexiblesearch.core.screens.Screen
import com.zemlianikin.flexiblesearch.core.triggers.SearchQuery
import kotlin.test.Test
import kotlin.test.assertEquals

class SubmitChainTest {
    private val log = mutableListOf<String>()

    private val scope =
        object : ActionScope {
            override val context: Context
                get() = error("no context")

            override val query = SearchQuery("q", null)

            override fun setQuery(input: String) {}

            override fun setQuery(query: SearchQuery) {}

            override fun close() {}

            override fun open(screen: Screen) {}

            override fun back() {}
        }

    private fun state(vararg ids: String) =
        SearchState(
            listOf(
                ResultGroup(
                    key = "g",
                    pluginId = "p",
                    title = null,
                    icon = null,
                    items =
                        ids.map { id ->
                            ResultItem.Card(id, id, null) { log += id }
                        },
                    fullList = null,
                    complete = false,
                    loading = false,
                )
            ),
            loading = false,
        )

    @Test
    fun `without handlers runs first result`() {
        submitChain(emptyList(), scope, state("a", "b"))
        assertEquals(listOf("a"), log)
    }

    @Test
    fun `later handler is outer`() {
        val handlers =
            listOf<SubmitHandler>(
                { _, next ->
                    log += "inner"
                    next()
                },
                { _, next ->
                    log += "outer"
                    next()
                },
            )
        submitChain(handlers, scope, state("a"))
        assertEquals(listOf("outer", "inner", "a"), log)
    }

    @Test
    fun `handler without next stops chain`() {
        val handlers =
            listOf<SubmitHandler>(
                { _, next -> next() },
                { _, _ -> log += query.text },
            )
        submitChain(handlers, scope, state("a"))
        assertEquals(listOf("q"), log)
    }

    @Test
    fun `handler sees results`() {
        val handler: SubmitHandler = { results, next ->
            if (results.items.isEmpty()) log += "fallback" else next()
        }
        submitChain(listOf(handler), scope, SearchState.Empty)
        submitChain(listOf(handler), scope, state("a"))
        assertEquals(listOf("fallback", "a"), log)
    }
}
