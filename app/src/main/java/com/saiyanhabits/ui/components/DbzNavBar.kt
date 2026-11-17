package com.saiyanhabits.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.saiyanhabits.art.ArtKey
import com.saiyanhabits.ui.theme.SaiyanColors

/** One destination in the [DbzNavBar]. */
data class NavItem(val label: String, @DrawableRes val icon: Int, val selected: Boolean, val onClick: () -> Unit)

/**
 * The bottom bar: orange under a gold edge, clouds in the corners, and the
 * current screen raised on a gold badge.
 */
@Composable
fun DbzNavBar(items: List<NavItem>, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val top = 8.dp.toPx()
                drawRect(
                    Brush.verticalGradient(listOf(SaiyanColors.BannerOrangeTop, SaiyanColors.BannerOrangeBottom), startY = top),
                    topLeft = Offset(0f, top),
                    size = Size(size.width, size.height - top),
                )
                // Gold edge along the top, with a dark line above it.
                drawRect(SaiyanColors.FrameDark, Offset(0f, top - 1.5.dp.toPx()), Size(size.width, 1.5.dp.toPx()))
                drawRect(
                    Brush.verticalGradient(listOf(SaiyanColors.FrameGoldLight, SaiyanColors.FrameGold), startY = top, endY = top + 3.dp.toPx()),
                    Offset(0f, top),
                    Size(size.width, 3.dp.toPx()),
                )
            },
    ) {
        // Clouds tucked into the bottom corners, the right one mirrored.
        ArtImage(
            key = ArtKey.CLOUD_ORNAMENT,
            contentDescription = null,
            alpha = 0.9f,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .offset(x = (-24).dp, y = 16.dp)
                .size(width = 64.dp, height = 38.dp),
        )
        ArtImage(
            key = ArtKey.CLOUD_ORNAMENT,
            contentDescription = null,
            alpha = 0.9f,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .offset(x = 24.dp, y = 16.dp)
                .size(width = 64.dp, height = 38.dp)
                .graphicsLayer { scaleX = -1f },
        )
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 4.dp)
                .height(76.dp),
        ) {
            items.forEach { item -> NavButton(item, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun NavButton(item: NavItem, modifier: Modifier = Modifier) {
    val badge = RoundedCornerShape(16.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .padding(horizontal = 2.dp)
            .height(if (item.selected) 72.dp else 64.dp)
            .semantics { selected = item.selected }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = item.onClick,
            ),
    ) {
        if (item.selected) {
            Box(
                Modifier
                    .matchParentSize()
                    .offset(y = (-6).dp)
                    .drawBehind {
                        drawPanel(
                            PanelStyle(
                                fillTop = SaiyanColors.TabGoldTop,
                                fillBottom = SaiyanColors.TabGoldBottom,
                                frame = listOf(Color(0xFFFFF6C8), SaiyanColors.FrameGold, SaiyanColors.FrameGoldDark),
                            ),
                            badge,
                        )
                    },
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = if (item.selected) (-6).dp else 0.dp),
        ) {
            Icon(
                painterResource(item.icon),
                contentDescription = null,
                tint = if (item.selected) Color(0xFFE5260E) else Color.White,
                modifier = Modifier.size(if (item.selected) 30.dp else 26.dp),
            )
            OutlinedText(
                item.label,
                MaterialTheme.typography.labelLarge,
                outlineWidth = 3.dp,
            )
        }
    }
}
