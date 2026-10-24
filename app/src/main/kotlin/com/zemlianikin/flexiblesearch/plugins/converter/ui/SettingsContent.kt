package com.zemlianikin.flexiblesearch.plugins.converter.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.zemlianikin.flexiblesearch.core.materialIcon
import com.zemlianikin.flexiblesearch.plugins.converter.ConverterSettings
import com.zemlianikin.flexiblesearch.plugins.converter.prefixError

/**
 * Экран настроек: кнопка в ряду и префиксы. Новые префиксы — в
 * [onPrefixesChange], он же сохраняет их в [settings].
 */
@Composable
internal fun SettingsContent(
    settings: ConverterSettings,
    onPrefixesChange: (List<String>) -> Unit,
) {
    val quickAction by settings.quickAction.collectAsState()
    val prefixes by settings.prefixes.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun add() {
        val prefix = input.trim()
        error = prefixError(prefix, prefixes)
        if (error == null) {
            onPrefixesChange(prefixes + prefix)
            input = ""
        }
    }
    LazyColumn {
        item {
            ListItem(
                headlineContent = { Text("Calculator button") },
                supportingContent = {
                    Text("In the bottom row, next to the apps")
                },
                trailingContent = {
                    // Нажатие обрабатывает вся строка.
                    Switch(checked = quickAction, onCheckedChange = null)
                },
                modifier =
                    Modifier.toggleable(
                        value = quickAction,
                        role = Role.Switch,
                        onValueChange = settings::setQuickAction,
                    ),
            )
        }
        item {
            Text(
                "Prefixes",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall,
                modifier =
                    Modifier.padding(
                        start = 16.dp,
                        top = 24.dp,
                        end = 16.dp,
                    ),
            )
        }
        items(prefixes, key = { it }) { prefix ->
            ListItem(
                headlineContent = { Text(prefix) },
                supportingContent = { Text(example(prefix)) },
                trailingContent = {
                    IconButton(
                        onClick = { onPrefixesChange(prefixes - prefix) }
                    ) {
                        Icon(RemoveIcon, "Remove $prefix")
                    }
                },
            )
        }
        if (prefixes.isEmpty()) {
            item {
                ListItem(
                    headlineContent = { Text("No prefixes") },
                    supportingContent = {
                        Text("Results still show up as you type")
                    },
                )
            }
        }
        item {
            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                    error = null
                },
                label = { Text("New prefix") },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                trailingIcon = {
                    IconButton(onClick = ::add) { Icon(AddIcon, "Add") }
                },
                keyboardOptions =
                    KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/** Как префикс набирают: после словесного нужен пробел, см. core. */
private fun example(prefix: String): String {
    val separator = if (prefix.last().isLetterOrDigit()) " " else ""
    return "$prefix${separator}2+2"
}

private val RemoveIcon: ImageVector =
    materialIcon(
        "Close",
        "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 " +
            "6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z",
    )

private val AddIcon: ImageVector =
    materialIcon("Add", "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")
