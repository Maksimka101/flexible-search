package com.zemlianikin.flexiblesearch.plugins.mocks

import android.app.SearchManager
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.zemlianikin.flexiblesearch.core.PluginScope
import com.zemlianikin.flexiblesearch.core.materialIcon

/**
 * Мок погоды на пустой строке: виджет со своей отрисовкой. Нажатие —
 * веб-поиск «weather».
 */
internal object WeatherMock {
    private class Hour(val time: String, val temperature: String)

    private val hours =
        listOf(
            Hour("15:00", "13°"),
            Hour("16:00", "12°"),
            Hour("17:00", "10°"),
        )

    fun install(scope: PluginScope) {
        scope.widget {
            custom("now") {
                Weather("+12°", "Cloudy · feels like +9°", hours) {
                    val intent =
                        Intent(Intent.ACTION_WEB_SEARCH)
                            .putExtra(SearchManager.QUERY, "weather")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(intent)
                        close()
                    } catch (e: Exception) {
                        Log.e("WeatherMock", "Can't start $intent", e)
                    }
                }
            }
        }
    }

    @Composable
    private fun Weather(
        temperature: String,
        summary: String,
        hours: List<Hour>,
        onClick: () -> Unit,
    ) {
        Surface(
            onClick = onClick,
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(CloudIcon, null, Modifier.size(36.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        temperature,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            LocalContentColor.current.copy(alpha = 0.7f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (hour in hours) {
                        Column(
                            horizontalAlignment =
                                Alignment.CenterHorizontally
                        ) {
                            Text(
                                hour.time,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text(
                                hour.temperature,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

// Material Icons «cloud».
private val CloudIcon: ImageVector =
    materialIcon(
        "Cloud",
        "M19.35,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 " +
            "2.34,8.36 0,10.91 0,14c0,3.31 2.69,6 6,6h13c2.76,0 5,-2.24 " +
            "5,-5 0,-2.64 -2.05,-4.78 -4.65,-4.96z",
    )
