package com.zemlianikin.flexiblesearch.plugins.converter.core

import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/** Сегодняшняя дата в поясе устройства. */
fun today(): LocalDate =
    Clock.System.todayIn(TimeZone.currentSystemDefault())
