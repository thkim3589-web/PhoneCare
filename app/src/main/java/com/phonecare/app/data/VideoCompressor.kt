package com.phonecare.app.data

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

data class VideoItem(
    val path: String,
    val name: String,
    val size: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val modified: Long,
) {
    val shortSide: Int get() = minOf(width, height).takeIf { it > 0 } ?: maxOf(width, height)
}

/** 압축 화질 */
enum class VideoPreset(val label: String, val shortSide: Int, val bitrate: Int) {
    HIGH("고화질 1080p", 1080, 6_000_000),
    NORMAL("보통 720p", 720, 3_000_000),
}

sealed class CompressResult {
    data class Done(val oldSize: Long, val newSize: Long) : CompressResult()
    data class NotSmaller(val reason: String) : CompressResult()
    data class Failed(val reason: String) : CompressResult()
}

/** 큰 동영상 찾기 + 화질을 조금 낮춰 용량 줄이기 (원본은 휴지통으로) */
object VideoCompressor {

    private const val MIN_SIZE = 50L * 1024 * 1024
    private const val AUDIO_BITRATE = 128_000

    /** 압축하면 예상되는 용량 */
    fun estimate(v: VideoItem, preset: VideoPreset): Long {
        if (v.durationMs <= 0) return v.size
        val originalBitrate = v.size * 8 * 1000 / v.durationMs
        val target = minOf(preset.bitrate.toLong(), originalBitrate)
        return (target + AUDIO_BITRATE) * v.durationMs / 1000 / 8
    }

    /** 압축하면 원본의 80% 이하로 줄어드는 50MB 이상 동영상 (큰 순) */
    @Suppress("DEPRECATION")
    fun findCandidates(context: Context, protectedFolders: List<String>, preset: VideoPreset): List<VideoItem> {
        val cols = arrayOf(
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_MODIFIED,
        )
        val out = ArrayList<VideoItem>()
        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cols, "${cols[1]} >= ?", arrayOf(MIN_SIZE.toString()), null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val path = c.getString(0) ?: continue
                    if (path.contains("/.") || ProtectedFolders.isProtected(path, protectedFolders)) continue
                    if (!File(path).exists()) continue
                    val v = VideoItem(
                        path = path,
                        name = path.substringAfterLast('/'),
                        size = c.getLong(1),
                        durationMs = c.getLong(2),
                        width = c.getInt(3),
                        height = c.getInt(4),
                        modified = c.getLong(5) * 1000,
                    )
                    if (v.durationMs > 0 && estimate(v, preset) < v.size * 0.8) out.add(v)
                }
            }
        } catch (e: Exception) {
        }
        return out.sortedByDescending { it.size }
    }

    /** 실제 압축 (진행률 0~1). 메인 스레드 Looper에서 변환기를 돌림 */
    suspend fun compress(
        context: Context,
        v: VideoItem,
        preset: VideoPreset,
        onProgress: (Float) -> Unit,
    ): CompressResult {
        val outFile = File(context.cacheDir, "compress_${System.currentTimeMillis()}.mp4")
        val error: String? = withContext(Dispatchers.Main) { runTransformer(context, v, preset, outFile, onProgress) }
        if (error != null) {
            outFile.delete()
            return CompressResult.Failed(error)
        }
        return withContext(Dispatchers.IO) { finish(context, v, outFile) }
    }

    private suspend fun runTransformer(
        context: Context,
        v: VideoItem,
        preset: VideoPreset,
        outFile: File,
        onProgress: (Float) -> Unit,
    ): String? = suspendCancellableCoroutine { cont ->
        val handler = Handler(Looper.getMainLooper())
        var transformer: Transformer? = null
        val poll = object : Runnable {
            val holder = ProgressHolder()
            override fun run() {
                val t = transformer ?: return
                if (t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
                if (cont.isActive) handler.postDelayed(this, 500)
            }
        }
        try {
            val originalBitrate = if (v.durationMs > 0) (v.size * 8 * 1000 / v.durationMs).toInt() else preset.bitrate
            val encoder = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(
                    VideoEncoderSettings.Builder().setBitrate(minOf(preset.bitrate, originalBitrate)).build(),
                )
                .build()
            val t = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(encoder)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        handler.removeCallbacks(poll)
                        if (cont.isActive) cont.resume(null)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        handler.removeCallbacks(poll)
                        if (cont.isActive) cont.resume(exportException.errorCodeName)
                    }
                })
                .build()
            transformer = t
            // 짧은 변이 목표보다 크면 줄이고, 이미 작으면 해상도는 그대로 두고 용량만 낮춤
            val effects = if (v.shortSide > preset.shortSide) {
                Effects(listOf(), listOf(Presentation.createForShortSide(preset.shortSide)))
            } else {
                Effects.EMPTY
            }
            val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(File(v.path))))
                .setEffects(effects)
                .build()
            t.start(item, outFile.absolutePath)
            handler.postDelayed(poll, 500)
            cont.invokeOnCancellation {
                handler.post {
                    handler.removeCallbacks(poll)
                    try {
                        t.cancel()
                    } catch (e: Exception) {
                    }
                }
            }
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(e.message ?: "변환을 시작할 수 없어요")
        }
    }

    /** 압축본이 충분히 작으면: 원본 → 휴지통, 압축본 → 원래 자리 */
    private fun finish(context: Context, v: VideoItem, outFile: File): CompressResult {
        val newSize = outFile.length()
        if (newSize <= 0 || newSize > v.size * 0.9) {
            outFile.delete()
            return CompressResult.NotSmaller("압축해도 거의 줄지 않아서 원본을 그대로 뒀어요")
        }
        val original = File(v.path)
        val (moved, _) = TrashManager.moveToTrash(context, listOf(v.path))
        if (v.path !in moved) {
            outFile.delete()
            return CompressResult.Failed("원본을 옮기지 못해서 취소했어요")
        }
        val dest = File(original.parentFile, original.nameWithoutExtension + ".mp4").let {
            if (it.exists()) File(original.parentFile, original.nameWithoutExtension + "_압축.mp4") else it
        }
        return try {
            outFile.copyTo(dest, overwrite = false)
            dest.setLastModified(v.modified)
            outFile.delete()
            // 원본 용량 전체가 '확보'로 기록됐으므로 새 파일 크기만큼 빼서 실제 절약량으로 맞춤
            History.recordFreed(context, -newSize, 0)
            MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), null, null)
            CompressResult.Done(v.size, newSize)
        } catch (e: Exception) {
            outFile.delete()
            CompressResult.Failed("압축본을 저장하지 못했어요. 원본은 휴지통에서 복원할 수 있어요")
        }
    }
}
