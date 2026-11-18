package com.saiyanhabits.ui.games

import androidx.compose.runtime.Composable
import com.saiyanhabits.domain.MiniGame

/** Opens the screen for [game]. */
@Composable
fun GameHost(game: MiniGame, onBack: () -> Unit) {
    when (game) {
        MiniGame.HOVER_BIKE -> HoverBikeScreen(onBack)
        MiniGame.MEDITATION -> MeditationScreen(onBack)
        else -> ComingSoon(game, onBack)
    }
}
