package com.saiyanhabits.domain

import com.saiyanhabits.GameConfig

/** Transformations. Form 0 is the base form; the rest unlock at overall-streak thresholds. */
object Forms {
    const val BASE = 0
    val HIGHEST: Int get() = GameConfig.FORM_STREAK_THRESHOLDS.size

    private val names = listOf("Base form", "Super Saiyan", "Super Saiyan 2", "Super Saiyan 3")

    fun name(form: Int): String = names.getOrElse(form) { "Form $form" }
}
