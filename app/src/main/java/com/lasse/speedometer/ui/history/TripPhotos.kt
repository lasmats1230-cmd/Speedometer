package com.lasse.speedometer.ui.history

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lasse.speedometer.R
import com.lasse.speedometer.data.db.TripPhotoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val THUMBNAIL = 96.dp

/**
 * The pictures taken on a ride, along the bottom of its detail screen.
 *
 * Long-press removes one — a tap would be too easy to trigger while scrolling
 * past, and there is nowhere else for a delete to live on a strip this small.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PhotoStrip(
    photos: List<TripPhotoEntity>,
    onAdd: () -> Unit,
    onRemove: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item("add") {
            Surface(
                onClick = onAdd,
                modifier = Modifier.size(THUMBNAIL),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.AddAPhoto,
                        contentDescription = stringResource(R.string.photo_add),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(photos, key = { it.id }) { photo ->
            val bitmap = rememberPhotoThumbnail(photo.uri)
            Surface(
                modifier = Modifier
                    .size(THUMBNAIL)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { onRemove(photo.id) },
                    ),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = stringResource(R.string.photo_of_trip),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(THUMBNAIL),
                    )
                } else {
                    Box(
                        Modifier
                            .size(THUMBNAIL)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        // The picture is gone — deleted from the gallery, or on
                        // a card that is no longer in the phone.
                        Icon(
                            imageVector = Icons.Filled.BrokenImage,
                            contentDescription = stringResource(R.string.photo_missing),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Decodes a thumbnail off the main thread.
 *
 * Hand-rolled rather than pulling in an image-loading library for one strip of
 * pictures: `inSampleSize` reads a fraction of the pixels, which is the part
 * that matters, and there is no cache to invalidate because the composition
 * already remembers the result for as long as the row is on screen.
 */
@Composable
fun rememberPhotoThumbnail(uri: String): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            decodeThumbnail(context, uri)?.asImageBitmap()
        }
    }
    return bitmap
}

private fun decodeThumbnail(context: Context, uri: String): Bitmap? = runCatching {
    val parsed = Uri.parse(uri)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(parsed)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    }

    val target = 256
    var sample = 1
    while (bounds.outWidth / sample > target && bounds.outHeight / sample > target) {
        sample *= 2
    }

    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    context.contentResolver.openInputStream(parsed)?.use {
        BitmapFactory.decodeStream(it, null, options)
    }
}.getOrNull()
