package com.saiyanhabits.domain

import com.saiyanhabits.GameConfig

/**
 * How hard a version of the habit was done.
 *
 * The enum names are stored in the database: add new values freely, but do not
 * rename existing ones.
 */
enum class Gravity(val label: String, val hint: String, val multiplier: Int) {
    NORMAL("Normal gravity", "Did it as planned", GameConfig.GRAVITY_NORMAL_MULTIPLIER),
    X10("10x gravity", "Did a harder version", GameConfig.GRAVITY_10X_MULTIPLIER),
    X100("100x gravity", "Went all out", GameConfig.GRAVITY_100X_MULTIPLIER),
}
