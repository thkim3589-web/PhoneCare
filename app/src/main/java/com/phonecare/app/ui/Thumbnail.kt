package com.phonecare.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private object ThumbCache {
    val cache = LruCache<String, ImageBitmap>(300)
}

private val videoExt = setOf("mp4", "mkv", "mov", "avi", "3gp", "webm")
private val imageExt = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp")

fun isMediaFile(path: String): Boolean {
    val ext = path.substringAfterLast('.', "").lowercase()
    return ext in imageExt || ext in videoExt
}

private fun loadThumb(path: String): ImageBitmap? = try {
    val file = File(path)
    val ext = path.substringAfterLast('.', "").lowercase()
    val bmp: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // 사진 회전(EXIF)도 자동 반영됨
        if (ext in videoExt) {
            ThumbnailUtils.createVideoThumbnail(file, Size(256, 256), null)
        } else {
            ThumbnailUtils.createImageThumbnail(file, Size(256, 256), null)
        }
    } else {
        if (ext in videoExt) {
            @Suppress("DEPRECATION")
            ThumbnailUtils.createVideoThumbnail(path, MediaStore.Images.Thumbnails.MINI_KIND)
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 256 && bounds.outHeight / (sample * 2) >= 256) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
    bmp?.asImageBitmap()
} catch (e: Throwable) {
    null
}

/** 파일 썸네일 (사진·동영상). 그 외 파일은 확장자 글자를 보여줌 */
@Composable
fun FileThumbnail(path: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(ThumbCache.cache.get(path), path) {
        value = ThumbCache.cache.get(path)
            ?: if (isMediaFile(path)) {
                withContext(Dispatchers.IO) { loadThumb(path) }?.also { ThumbCache.cache.put(path, it) }
            } else {
                null
            }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            Image(b, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(
                path.substringAfterLast('.', "").uppercase().take(4),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
