package com.zemlianikin.flexiblesearch.poc.components.plugins

import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.MediaStore
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.zemlianikin.flexiblesearch.poc.components.core.ActionScope
import com.zemlianikin.flexiblesearch.poc.components.core.Plugin
import com.zemlianikin.flexiblesearch.poc.components.core.PluginScope
import com.zemlianikin.flexiblesearch.poc.components.core.buildResults
import com.zemlianikin.flexiblesearch.poc.components.core.materialIcon
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Живой виджет на пустой строке: фонарик со своим состоянием и кнопки
 * таймера, будильника и камеры.
 */
class TogglesPlugin : Plugin {
    override val id = "toggles"

    override fun install(scope: PluginScope) {
        val cameras =
            scope.context.getSystemService(CameraManager::class.java)
        val torchId =
            cameras.cameraIdList.firstOrNull {
                cameras
                    .getCameraCharacteristics(it)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        scope.widgetFlow {
            val torch =
                if (torchId != null) torchState(cameras, torchId)
                else flowOf(null)
            torch.map { on ->
                buildResults {
                    custom("toggles") {
                        Toggles(
                            torchOn = on,
                            onTorch = { on ->
                                try {
                                    torchId?.let {
                                        cameras.setTorchMode(it, on)
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Torch", e)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    /** Включён ли фонарик; меняется и из шторки. */
    private fun torchState(
        cameras: CameraManager,
        torchId: String,
    ): Flow<Boolean> = callbackFlow {
        val callback =
            object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(id: String, on: Boolean) {
                    if (id == torchId) trySend(on)
                }
            }
        // Колбэк сразу сообщает текущее состояние.
        cameras.registerTorchCallback(
            callback,
            Handler(Looper.getMainLooper()),
        )
        awaitClose { cameras.unregisterTorchCallback(callback) }
    }

    /** [torchOn] = null — фонарика нет. */
    @Composable
    private fun ActionScope.Toggles(
        torchOn: Boolean?,
        onTorch: (Boolean) -> Unit,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                if (torchOn != null) {
                    Labeled("Torch") {
                        FilledIconToggleButton(torchOn, onTorch) {
                            Icon(TorchIcon, null)
                        }
                    }
                }
                for ((label, icon, intent) in intents) {
                    Labeled(label) {
                        FilledTonalIconButton(onClick = { start(intent) }) {
                            Icon(icon, null)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Labeled(label: String, button: @Composable () -> Unit) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            button()
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }

    private val intents =
        listOf(
            Triple(
                "Timer",
                TimerIcon,
                // Без длительности часы спрашивают её сами.
                Intent(AlarmClock.ACTION_SET_TIMER),
            ),
            Triple(
                "Alarm",
                AlarmIcon,
                Intent(AlarmClock.ACTION_SHOW_ALARMS),
            ),
            Triple(
                "Camera",
                CameraIcon,
                Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            ),
        )

    private fun ActionScope.start(intent: Intent) {
        try {
            context.startActivity(
                Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            close()
        } catch (e: Exception) {
            Log.e(TAG, "Can't start $intent", e)
        }
    }

    private companion object {
        const val TAG = "TogglesPlugin"
    }
}

// Material Icons «flashlight_on».
private val TorchIcon: ImageVector =
    materialIcon(
        "Torch",
        "M6,2h12v3H6V2z" +
            "M6,7v1l2,3v11h8V11l2-3V7H6z" +
            "M12,15.5c-0.83,0 -1.5,-0.67 -1.5,-1.5s0.67,-1.5 1.5,-1.5" +
            " 1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5z",
    )

// Material Icons «timer».
private val TimerIcon: ImageVector =
    materialIcon(
        "Timer",
        "M15,1H9v2h6V1z" +
            "M11,14h2V8h-2V14z" +
            "M19.03,7.39l1.42,-1.42c-0.43,-0.51 -0.9,-0.99 -1.41,-1.41" +
            "l-1.42,1.42C16.07,4.74 14.12,4 12,4c-4.97,0 -9,4.03 -9,9" +
            "s4.02,9 9,9 9,-4.03 9,-9c0,-2.12 -0.74,-4.07 -1.97,-5.61z" +
            "M12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7" +
            " -3.13,7 -7,7z",
    )

// Material Icons «alarm».
private val AlarmIcon: ImageVector =
    materialIcon(
        "Alarm",
        "M22,5.72l-4.6,-3.86 -1.29,1.53 4.6,3.86L22,5.72z" +
            "M7.88,3.39L6.6,1.86 2,5.71l1.29,1.53 4.59,-3.85z" +
            "M12.5,8L11,8v6l4.75,2.85 0.75,-1.23 -4,-2.37L12.5,8z" +
            "M12,4c-4.97,0 -9,4.03 -9,9s4.02,9 9,9c4.97,0 9,-4.03 9,-9" +
            "s-4.03,-9 -9,-9z" +
            "M12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7" +
            " -3.13,7 -7,7z",
    )

// Material Icons «photo_camera».
private val CameraIcon: ImageVector =
    materialIcon(
        "Camera",
        "M12,12m-3.2,0a3.2,3.2 0,1 1,6.4 0a3.2,3.2 0,1 1,-6.4 0" +
            "M9,2L7.17,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16" +
            "c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2h-3.17L15,2H9z" +
            "M12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5" +
            " -2.24,5 -5,5z",
    )
