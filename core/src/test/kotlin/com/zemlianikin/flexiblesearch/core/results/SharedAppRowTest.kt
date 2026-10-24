package com.zemlianikin.flexiblesearch.core.results

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedAppRowTest {
    private fun app(id: String) = ResultItem.App(id, id, null) {}

    private fun card(id: String) = ResultItem.Card(id, id, null) {}

    private fun group(pluginId: String, vararg items: ResultItem) =
        ResultGroup(
            pluginId,
            pluginId,
            null,
            null,
            items.toList(),
            null,
            false,
        )

    private fun List<ResultGroup>.layout() = map { group ->
        group.pluginId to group.items.map { it.id }
    }

    @Test
    fun tilesJoinTheFirstGroupWithTiles() {
        val groups =
            listOf(
                group("calculator", card("sum")),
                group("apps", app("Telegram"), app("Termux")),
                group("contacts", card("Anna"), app("Call Anna")),
                group("shortcuts", app("New note")),
            )
        assertEquals(
            listOf(
                "calculator" to listOf("sum"),
                "apps" to
                    listOf("Telegram", "Termux", "Call Anna", "New note"),
                "contacts" to listOf("Anna"),
            ),
            groups.withSharedAppRow().layout(),
        )
    }

    @Test
    fun withoutTilesNothingChanges() {
        val groups = listOf(group("calculator", card("sum")))
        assertEquals(groups.layout(), groups.withSharedAppRow().layout())
    }
}
