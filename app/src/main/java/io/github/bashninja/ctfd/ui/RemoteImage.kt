package io.github.bashninja.ctfd.ui

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.caverock.androidsvg.SVG
import io.github.bashninja.ctfd.data.CtfdApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

private sealed interface Loaded {
    data object Loading : Loaded
    data class Done(val bitmap: Bitmap) : Loaded
    data class Failed(val why: String) : Loaded
}

private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

/** Big enough to read a puzzle image when zoomed, small enough not to exhaust memory. */
private const val MAX_SIDE = 2400

/** An image from a description, full width; tap it to zoom. */
@Composable
fun RemoteImage(src: String, alt: String, api: CtfdApi?) {
    val state by produceState<Loaded>(cache.get(src)?.let { Loaded.Done(it) } ?: Loaded.Loading, src) {
        if (value is Loaded.Done) return@produceState
        value = try {
            val url = api?.fileUrl(src) ?: error("no CTF")
            val (bytes, type) = api.fetch(url)
            val bitmap = withContext(Dispatchers.Default) { decode(bytes, type, src) }
            cache.put(src, bitmap)
            Loaded.Done(bitmap)
        } catch (e: Exception) {
            Loaded.Failed(e.message ?: e.javaClass.simpleName)
        }
    }
    var zoomed by remember { mutableStateOf(false) }

    when (val s = state) {
        Loaded.Loading -> Box(
            Modifier.fillMaxWidth().height(160.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        is Loaded.Failed -> Box(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(16.dp),
        ) {
            Text(
                "Image didn't load${alt.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()}: ${s.why}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(Icons.Filled.BrokenImage, null, Modifier.align(Alignment.TopEnd))
        }
        is Loaded.Done -> {
            Image(
                s.bitmap.asImageBitmap(),
                contentDescription = alt.ifBlank { null },
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    // SVGs and transparent PNGs are often drawn for a white page.
                    .background(Color.White)
                    .clickable(onClickLabel = "Zoom image") { zoomed = true },
            )
            if (zoomed) ZoomDialog(s.bitmap, alt) { zoomed = false }
        }
    }
}

@Composable
private fun ZoomDialog(bitmap: Bitmap, alt: String, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { _: Offset, zoom: Float, pan: Offset, _: Float ->
        scale = (scale * zoom).coerceIn(1f, 8f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Image(
                bitmap.asImageBitmap(),
                contentDescription = alt.ifBlank { null },
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = if (bitmap.hasAlpha()) 1f else 0f))
                    .transformable(transform)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 8.dp)) {
                Icon(Icons.Filled.Close, "Close", tint = Color.White)
            }
        }
    }
}

private fun decode(bytes: ByteArray, type: String?, src: String): Bitmap {
    val isSvg = type?.contains("svg") == true || src.substringBefore('?').endsWith(".svg", ignoreCase = true)
    if (isSvg) {
        val svg = SVG.getFromString(String(bytes))
        val width = svg.documentWidth.takeIf { it > 0 } ?: svg.documentViewBox?.width() ?: 512f
        val height = svg.documentHeight.takeIf { it > 0 } ?: svg.documentViewBox?.height() ?: 512f
        // Drawn at a size that stays sharp when zoomed.
        val scale = (MAX_SIDE / maxOf(width, height)).coerceAtMost(4f)
        val bitmap = Bitmap.createBitmap((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        svg.setDocumentWidth("100%")
        svg.setDocumentHeight("100%")
        if (svg.documentViewBox == null) svg.setDocumentViewBox(0f, 0f, width, height)
        svg.renderToCanvas(android.graphics.Canvas(bitmap))
        return bitmap
    }
    return ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
        val side = maxOf(info.size.width, info.size.height)
        if (side > MAX_SIDE) decoder.setTargetSampleSize((side + MAX_SIDE - 1) / MAX_SIDE)
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
}
