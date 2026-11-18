package com.saiyanhabits.ui.games

import androidx.compose.runtime.Composable
import com.saiyanhabits.domain.MiniGame

/** Opens the screen for [game]. */
@Composable
fun GameHost(game: MiniGame, onBack: () -> Unit) {
    when (game) {
        else -> ComingSoon(game, onBack)
    }
}
