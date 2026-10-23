package com.zemlianikin.flexiblesearch.plugins.converter.ui

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring

/**
 * Пружины как в expressive-motion: пространственные с лёгким отскоком, для
 * цвета — без отскока.
 */
object Motion {
    fun <T> spatial(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.6f, stiffness = 400f)

    fun <T> fastSpatial(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.6f, stiffness = 1400f)

    fun <T> effects(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = 1600f)
}
