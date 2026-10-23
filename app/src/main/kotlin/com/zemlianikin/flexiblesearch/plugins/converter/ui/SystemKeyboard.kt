package com.zemlianikin.flexiblesearch.plugins.converter.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import kotlinx.coroutines.awaitCancellation

/**
 * При [block] ввод с системной клавиатуры к полю внутри не подключается:
 * она не показывается, каретка и выделение работают.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WithoutSystemKeyboard(block: Boolean, content: @Composable () -> Unit) {
    if (block) {
        InterceptPlatformTextInput({ _, _ -> awaitCancellation() }, content)
    } else {
        content()
    }
}
