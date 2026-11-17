package com.saiyanhabits.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import com.saiyanhabits.art.ArtKey
import com.saiyanhabits.domain.Mentor
import com.saiyanhabits.ui.theme.color
import com.saiyanhabits.ui.theme.formAura

/** A mentor's portrait, cropped to a circle. */
@Composable
fun MentorPortrait(mentor: Mentor, modifier: Modifier = Modifier) {
    ArtImage(
        key = ArtKey.mentor(mentor),
        contentDescription = mentor.displayName,
        // A soft circle in the mentor's colour, so dark hair on a transparent image still reads.
        modifier = modifier
            .clip(CircleShape)
            .background(mentor.color.copy(alpha = 0.22f)),
        contentScale = ContentScale.Crop,
    )
}

/** The player's portrait for a form (0 is the base form), cropped to a circle. */
@Composable
fun PlayerPortrait(form: Int, modifier: Modifier = Modifier) {
    ArtImage(
        key = ArtKey.playerForm(form),
        contentDescription = "You",
        modifier = modifier
            .clip(CircleShape)
            .background(formAura(form).copy(alpha = 0.22f)),
        contentScale = ContentScale.Crop,
    )
}
