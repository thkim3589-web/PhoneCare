package com.phonecare.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import java.io.File
import java.security.MessageDigest

data class PhotoItem(
    val path: String,
    val size: Long,
    val taken: Long,
    val width: Int,
    val height: Int,
)

enum class GroupType(val label: String) {
    EXACT("완전히 같은 사진"),
    SIMILAR("비슷한 사진"),
}

/** photos[0]이 남기기 추천 사진 */
data class PhotoGroup(val id: String, val type: GroupType, val photos: List<PhotoItem>) {
    val keep: PhotoItem get() = photos.first()
    val removable: List<PhotoItem> get() = photos.drop(1)
}

object PhotoDuplicates {

    /** 연속 촬영으로 볼 최대 간격 */
    private const val BURST_WINDOW_MS = 10 * 60 * 1000L

    /** dHash 차이가 이 값 이하면 비슷한 사진 (64비트 중) */
    private const val SIMILAR_THRESHOLD = 8

    /**
     * 갤러리 사진에서 중복·비슷한 사진 묶음을 찾는다. (시간이 걸리므로 백그라운드에서 호출)
     * onProgress(처리한 수, 전체 수)
     */
    fun scan(
        context: Context,
        protectedFolders: List<String>,
        isActive: () -> Boolean = { true },
        onProgress: (Int, Int) -> Unit,
    ): List<PhotoGroup> {
        val photos = queryPhotos(context).filter {
            !ProtectedFolders.isProtected(it.path, protectedFolders) &&
                !it.path.contains("/.") && // 숨김 폴더(휴지통 등) 제외
                File(it.path).exists()
        }
        val total = photos.size
        var done = 0
        val groups = ArrayList<PhotoGroup>()
        val inExact = HashSet<String>()

        // 1) 완전히 같은 사진: 크기가 같은 것끼리만 해시 비교
        photos.groupBy { it.size }.values.filter { it.size > 1 }.forEach { sameSize ->
            if (!isActive()) return emptyList()
            sameSize.groupBy { md5(it.path) }
                .filter { (hash, list) -> hash != null && list.size > 1 }
                .values
                .forEach { list ->
                    // 카메라 폴더 원본을 남기고, 그다음은 오래된 것 우선
                    val sorted = list.sortedWith(
                        compareBy<PhotoItem>({ if (it.path.contains("/DCIM/")) 0 else 1 }, { it.taken }),
                    )
                    groups.add(PhotoGroup("e_${sorted.first().path}", GroupType.EXACT, sorted))
                    list.forEach { inExact.add(it.path) }
                }
        }

        // 2) 비슷한 사진: 촬영 시간 순으로 보고, 바로 앞 사진과 짧은 간격 + 모양이 비슷하면 같은 묶음
        val remaining = photos.filter { it.path !in inExact }.sortedBy { it.taken }
        done = total - remaining.size
        onProgress(done, total)

        var cluster = ArrayList<PhotoItem>()
        var prevHash: Long? = null
        var prevTaken = 0L

        fun closeCluster() {
            if (cluster.size > 1) {
                // 해상도가 가장 크고, 같으면 용량이 큰(대체로 더 선명한) 사진을 남김
                val sorted = cluster.sortedWith(
                    compareByDescending<PhotoItem> { it.width.toLong() * it.height }.thenByDescending { it.size },
                )
                groups.add(PhotoGroup("s_${sorted.first().path}", GroupType.SIMILAR, sorted))
            }
            cluster = ArrayList()
        }

        for (p in remaining) {
            if (!isActive()) return emptyList()
            val h = dHash(p.path)
            done++
            if (done % 20 == 0) onProgress(done, total)
            val ph = prevHash
            val similar = h != null && ph != null &&
                p.taken - prevTaken <= BURST_WINDOW_MS &&
                java.lang.Long.bitCount(h xor ph) <= SIMILAR_THRESHOLD
            if (!similar) closeCluster()
            cluster.add(p)
            prevHash = h
            prevTaken = p.taken
        }
        closeCluster()
        onProgress(total, total)

        // 정리 효과가 큰 묶음부터
        return groups.sortedByDescending { g -> g.removable.sumOf { it.size } }
    }

    @Suppress("DEPRECATION")
    private fun queryPhotos(context: Context): List<PhotoItem> {
        val projection = arrayOf(
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
        )
        val result = ArrayList<PhotoItem>()
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, null,
            )?.use { c ->
                val iData = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val iSize = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val iTaken = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val iMod = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val iW = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val iH = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                while (c.moveToNext()) {
                    val path = c.getString(iData) ?: continue
                    val size = c.getLong(iSize)
                    if (size <= 0) continue
                    val taken = c.getLong(iTaken).takeIf { it > 0 } ?: (c.getLong(iMod) * 1000)
                    result.add(PhotoItem(path, size, taken, c.getInt(iW), c.getInt(iH)))
                }
            }
        } catch (e: Exception) {
        }
        return result
    }

    private fun md5(path: String): String? = try {
        val md = MessageDigest.getInstance("MD5")
        File(path).inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        null
    }

    /** 차이 해시(dHash): 사진을 9x8 흑백으로 줄여 밝기 변화를 64비트로 표현 */
    private fun dHash(path: String): Long? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 64 && bounds.outHeight / (sample * 2) >= 64) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val src = BitmapFactory.decodeFile(path, opts) ?: return null
            val small = Bitmap.createScaledBitmap(src, 9, 8, true)
            if (small !== src) src.recycle()
            val px = IntArray(72)
            small.getPixels(px, 0, 9, 0, 0, 9, 8)
            small.recycle()
            var hash = 0L
            var bit = 0
            for (y in 0 until 8) {
                for (x in 0 until 8) {
                    if (luma(px[y * 9 + x]) > luma(px[y * 9 + x + 1])) hash = hash or (1L shl bit)
                    bit++
                }
            }
            hash
        } catch (e: Throwable) {
            null
        }
    }

    private fun luma(c: Int): Int = ((c shr 16 and 0xFF) * 299 + (c shr 8 and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
}
