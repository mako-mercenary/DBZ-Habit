package com.cdlstudy.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.cdlstudy.CdlApp
import com.cdlstudy.domain.PageSpan
import com.cdlstudy.manual.ManualStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun manualStore(): ManualStore = (LocalContext.current.applicationContext as CdlApp).manual

/**
 * The pages of your own manual, drawn in the app.
 *
 * A page is drawn only when it comes into view, and the last pages drawn stay
 * in memory. At 2x the page is wider than the screen, so it scrolls sideways.
 */
@Composable
fun ManualPages(
    span: PageSpan,
    zoom: Float,
    modifier: Modifier = Modifier,
) {
    val store = manualStore()
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val widthPx = remember(screenWidthDp, zoom) {
        with(density) { (screenWidthDp.dp.toPx() * zoom).toInt().coerceIn(320, 3000) }
    }

    LazyColumn(modifier = modifier) {
        items(span.pages, key = { it }) { page ->
            PdfPage(store, page, widthPx, zoom)
        }
    }
}

@Composable
private fun PdfPage(store: ManualStore, page: Int, widthPx: Int, zoom: Float) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, page, widthPx) {
        value = withContext(Dispatchers.IO) { store.render(page, widthPx) }
    }
    val scroll = rememberScrollState()

    Column(Modifier.padding(bottom = 16.dp)) {
        val image = bitmap
        if (image == null) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(612f / 792f),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        } else {
            val content: @Composable () -> Unit = {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = "Manual page $page",
                    modifier = with(LocalDensity.current) {
                        Modifier.width(image.width.toDp())
                    },
                    contentScale = ContentScale.FillWidth,
                )
            }
            if (zoom > 1f) {
                Box(Modifier.fillMaxWidth().horizontalScroll(scroll)) { content() }
            } else {
                content()
            }
        }
        Text(
            "PDF page $page",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
