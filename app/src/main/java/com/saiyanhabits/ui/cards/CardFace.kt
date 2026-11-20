package com.saiyanhabits.ui.cards

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saiyanhabits.domain.CardDef
import com.saiyanhabits.domain.CardElement
import com.saiyanhabits.domain.CardRarity
import com.saiyanhabits.domain.Side
import com.saiyanhabits.ui.components.OutlinedText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** An element's colour, for card frames and charged squares. */
val CardElement.color: Color
    get() = when (this) {
        CardElement.KI -> Color(0xFFFF8F1F)
        CardElement.STRENGTH -> Color(0xFFE53935)
        CardElement.SPIRIT -> Color(0xFF43A047)
        CardElement.TECH -> Color(0xFF29B6F6)
    }

val CardRarity.color: Color
    get() = when (this) {
        CardRarity.COMMON -> Color(0xFFB0BEC5)
        CardRarity.RARE -> Color(0xFF42A5F5)
        CardRarity.EPIC -> Color(0xFFAB47BC)
        CardRarity.LEGENDARY -> Color(0xFFFFC107)
        CardRarity.SIGNATURE -> Color(0xFFFF7043)
    }

/** A side's number as shown: 10 is A. */
fun sideLabel(v: Int): String = if (v >= 10) "A" else v.toString()

/** The picture for a card, if one has been added: assets/cards/art/<id>.png or the app's card_art folder. */
@Composable
private fun cardArt(id: String): ImageBitmap? {
    val context = LocalContext.current
    val art by produceState<ImageBitmap?>(null, id) {
        value = withContext(Dispatchers.IO) {
            val picked = File(context.filesDir, "card_art/$id.png")
            try {
                when {
                    picked.isFile -> BitmapFactory.decodeFile(picked.path)
                    else -> context.assets.open("cards/art/$id.png").use { BitmapFactory.decodeStream(it) }
                }?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }
    return art
}

/**
 * A card: owner-coloured frame (blue yours, red theirs), the picture if one
 * has been added (otherwise the name's initials on the element's colour),
 * the four numbers on its edges, and the name along the bottom.
 */
@Composable
fun CardFace(card: CardDef, owner: Side?, modifier: Modifier = Modifier, selected: Boolean = false, showName: Boolean = true) {
    val frame = when (owner) {
        Side.PLAYER -> Color(0xFF1E5BD8)
        Side.OPPONENT -> Color(0xFFC62828)
        null -> card.rarity.color
    }
    val shape = RoundedCornerShape(8.dp)
    BoxWithConstraints(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(frame.copy(alpha = 0.95f), Color(0xFF0B1230))))
            .border(if (selected) 3.dp else 2.dp, if (selected) Color(0xFFFFEB3B) else card.rarity.color, shape),
    ) {
        val size = with(LocalDensity.current) { minOf(maxWidth, maxHeight).toPx() }
        val numberSize = (size / 7.5f / LocalDensity.current.density).coerceIn(9f, 26f).sp
        val art = cardArt(card.id)
        Box(
            Modifier
                .fillMaxSize()
                .padding(4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Brush.radialGradient(listOf(card.element.color.copy(alpha = 0.85f), Color(0xFF14102A)))),
        ) {
            if (art != null) {
                Image(art, contentDescription = card.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(
                    card.name.split(" ", "(", ":").filter { it.isNotBlank() && it[0].isLetter() }.take(2).joinToString("") { it.take(1) },
                    style = TextStyle(fontSize = (numberSize.value * 1.9f).sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.85f)),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        val style = TextStyle(fontSize = numberSize, fontWeight = FontWeight.Black)
        OutlinedText(sideLabel(card.top), style, outlineWidth = 3.dp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp))
        OutlinedText(sideLabel(card.left), style, outlineWidth = 3.dp, modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp))
        OutlinedText(sideLabel(card.right), style, outlineWidth = 3.dp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp))
        OutlinedText(
            sideLabel(card.bottom),
            style,
            outlineWidth = 3.dp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (showName) (numberSize.value * 1.4f).dp else 2.dp),
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(5.dp)
                .background(card.element.color, RoundedCornerShape(50))
                .border(1.dp, Color.White, RoundedCornerShape(50))
                .padding(horizontal = 3.dp),
        ) {
            Text(card.element.key, style = TextStyle(fontSize = (numberSize.value * 0.55f).sp, fontWeight = FontWeight.Bold, color = Color.White))
        }
        if (showName) {
            Text(
                card.name,
                style = TextStyle(fontSize = (numberSize.value * 0.55f).coerceAtLeast(7f).sp, fontWeight = FontWeight.Bold, color = Color.White),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(Color(0xCC000000))
                    .padding(horizontal = 2.dp, vertical = 1.dp),
            )
        }
    }
}
