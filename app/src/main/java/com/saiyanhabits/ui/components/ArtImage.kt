package com.saiyanhabits.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saiyanhabits.SaiyanApp
import com.saiyanhabits.art.ArtKey
import com.saiyanhabits.art.ArtRepository

@Composable
private fun artRepository(): ArtRepository = (LocalContext.current.applicationContext as SaiyanApp).art

/**
 * One of the app's images, by key: the user's picked image if there is one,
 * otherwise the bundled file. Takes up its space while loading, so layouts
 * don't jump.
 */
@Composable
fun ArtImage(
    key: ArtKey,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null,
) {
    val art = artRepository()
    val version by art.version.collectAsStateWithLifecycle()
    val image by produceState<ImageBitmap?>(initialValue = null, key, version) { value = art.image(key) }
    val loaded = image
    if (loaded == null) {
        Box(modifier.semantics { if (contentDescription != null) this.contentDescription = contentDescription })
    } else {
        Image(
            bitmap = loaded,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            alpha = alpha,
            colorFilter = colorFilter,
        )
    }
}
