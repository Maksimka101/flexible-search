package com.zemlianikin.flexiblesearch.plugins.converter.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zemlianikin.flexiblesearch.plugins.converter.core.CurrencyCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Окно поиска валюты по названию, коду или стране (системная клавиатура: в
 * numpad букв нет). Только вид: выбор отдаёт [onPick], что с ним делать —
 * решает вызывающий. Пустой запрос показывает валюты из [ranking].
 */
@Composable
fun CurrencyPickerDialog(
    directory: CurrencyDirectory,
    ranking: List<CurrencyCode>,
    onPick: (CurrencyCode) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // Не на главном потоке: первый поиск строит справочник названий и
    // стран, это заметное время.
    val matches by
        produceState(emptyList<CurrencyMatch>(), query, ranking) {
            value =
                withContext(Dispatchers.Default) {
                    directory.search(query, ranking)
                }
        }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Currency or country") },
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                        ),
                    modifier =
                        Modifier.fillMaxWidth().focusRequester(focus),
                )
                if (matches.isEmpty() && query.isNotBlank()) {
                    Text(
                        "Nothing found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier =
                            Modifier.padding(top = 16.dp, start = 8.dp),
                    )
                }
                LazyColumn(
                    Modifier.heightIn(max = 360.dp).padding(top = 8.dp)
                ) {
                    items(matches, key = { it.code.code }) { match ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { onPick(match.code) }
                                .padding(
                                    horizontal = 8.dp,
                                    vertical = 12.dp,
                                )
                        ) {
                            Text(
                                match.code.code,
                                style =
                                    MaterialTheme.typography.titleMedium,
                                modifier = Modifier.width(56.dp),
                            )
                            Column {
                                Text(
                                    match.name,
                                    style =
                                        MaterialTheme.typography.bodyLarge,
                                )
                                match.country?.let { country ->
                                    Text(
                                        country,
                                        style =
                                            MaterialTheme.typography
                                                .bodyMedium,
                                        color =
                                            MaterialTheme.colorScheme
                                                .onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
