package com.zemlianikin.flexiblesearch

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Просьба о доступе ко всем файлам, пока его нет: без него не прочитать
 * обои для фона ([BlurredWallpaper]). Строка в настройках над плагинами.
 */
@Composable
fun WallpaperAccess() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    var granted by remember {
        mutableStateOf(Environment.isExternalStorageManager())
    }
    LifecycleResumeEffect(Unit) {
        granted = Environment.isExternalStorageManager()
        onPauseOrDispose {}
    }
    if (granted) return
    val context = LocalContext.current
    ListItem(
        headlineContent = { Text("Blurred wallpaper") },
        supportingContent = {
            Text("Allow access to all files to read the wallpaper")
        },
        modifier =
            Modifier.clickable {
                context.startActivity(
                    Intent(
                        Settings
                            .ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.fromParts("package", context.packageName, null),
                    )
                )
            },
    )
}
