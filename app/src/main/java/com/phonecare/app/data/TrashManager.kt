package com.phonecare.app.data

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.phonecare.app.util.daysAgo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class TrashItem(
    val id: String,
    val originalPath: String,
    val trashPath: String,
    val name: String,
    val size: Long,
    val deletedAt: Long,
) {
    fun daysLeft(keepDays: Int): Long = (keepDays - daysAgo(deletedAt)).coerceAtLeast(0)
    val originalFolder: String
        get() = File(originalPath).parent
            ?.removePrefix(Environment.getExternalStorageDirectory().absolutePath)
            ?.ifEmpty { "/" } ?: ""
}

/**
 * 앱 전용 휴지통. 삭제한 파일을 숨김 폴더(.PhoneCareTrash, 갤러리에 안 보임)로 옮겨
 * 7일 동안 보관하고, 그 뒤 자동으로 영구 삭제한다.
 */
object TrashManager {

    const val DEFAULT_KEEP_DAYS = 7
    val keepOptions = listOf(3, 7, 14, 30)
    private const val PREFS = "trash"

    /** 휴지통 보관 기간 (설정에서 고를 수 있음) */
    fun keepDays(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("keep_days", DEFAULT_KEEP_DAYS)

    fun setKeepDays(context: Context, days: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("keep_days", days).apply()
    }
    private const val INDEX_FILE = "trash_index.json"
    private val lock = Any()

    private fun trashDir(): File {
        val dir = File(Environment.getExternalStorageDirectory(), ".PhoneCareTrash")
        if (!dir.exists()) dir.mkdirs()
        val noMedia = File(dir, ".nomedia")
        if (!noMedia.exists()) {
            try {
                noMedia.createNewFile()
            } catch (e: Exception) {
            }
        }
        return dir
    }

    private fun indexFile(context: Context) = File(context.filesDir, INDEX_FILE)

    private fun load(context: Context): MutableList<TrashItem> {
        val f = indexFile(context)
        if (!f.exists()) return mutableListOf()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                TrashItem(
                    id = o.getString("id"),
                    originalPath = o.getString("orig"),
                    trashPath = o.getString("trash"),
                    name = o.getString("name"),
                    size = o.getLong("size"),
                    deletedAt = o.getLong("at"),
                )
            }.toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun save(context: Context, items: List<TrashItem>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("orig", it.originalPath)
                    .put("trash", it.trashPath)
                    .put("name", it.name)
                    .put("size", it.size)
                    .put("at", it.deletedAt),
            )
        }
        indexFile(context).writeText(arr.toString())
    }

    /** 이동 실패 시(다른 저장소 등) 복사 후 원본 삭제 */
    private fun moveFile(src: File, dest: File): Boolean {
        if (src.renameTo(dest)) return true
        return try {
            src.copyTo(dest, overwrite = true)
            if (src.delete()) {
                true
            } else {
                dest.delete()
                false
            }
        } catch (e: Exception) {
            dest.delete()
            false
        }
    }

    /** 휴지통 목록 (실제 파일이 없어진 항목은 정리) */
    fun list(context: Context): List<TrashItem> = synchronized(lock) {
        val items = load(context)
        val alive = items.filter { File(it.trashPath).exists() }
        if (alive.size != items.size) save(context, alive)
        alive.sortedByDescending { it.deletedAt }
    }

    fun totalSize(context: Context): Long = list(context).sumOf { it.size }

    /** 파일들을 휴지통으로 옮기고, 옮겨진 원래 경로와 확보한 용량을 돌려준다. */
    fun moveToTrash(context: Context, paths: List<String>): Pair<Set<String>, Long> = synchronized(lock) {
        val dir = trashDir()
        val items = load(context)
        val moved = HashSet<String>()
        var freed = 0L
        var n = 0
        for (path in paths) {
            val src = File(path)
            if (!src.exists()) continue
            val size = src.length()
            val id = "${System.currentTimeMillis()}_${n++}"
            val dest = File(dir, "${id}_${src.name}")
            if (moveFile(src, dest)) {
                items.add(TrashItem(id, path, dest.absolutePath, src.name, size, System.currentTimeMillis()))
                moved.add(path)
                freed += size
            }
        }
        save(context, items)
        // 갤러리에서 사라지도록 미디어 목록 갱신
        if (moved.isNotEmpty()) MediaScannerConnection.scanFile(context, moved.toTypedArray(), null, null)
        moved to freed
    }

    /** 원래 자리로 복원. 같은 이름 파일이 있으면 "(복원)"을 붙인다. */
    fun restore(context: Context, ids: Set<String>): Int = synchronized(lock) {
        val items = load(context)
        val restoredPaths = ArrayList<String>()
        val remaining = items.filter { item ->
            if (item.id !in ids) return@filter true
            val src = File(item.trashPath)
            var dest = File(item.originalPath)
            dest.parentFile?.mkdirs()
            if (dest.exists()) {
                val base = dest.nameWithoutExtension
                val ext = dest.extension.let { if (it.isEmpty()) "" else ".$it" }
                dest = File(dest.parentFile, "$base (복원)$ext")
            }
            if (src.exists() && moveFile(src, dest)) {
                restoredPaths.add(dest.absolutePath)
                false
            } else {
                true
            }
        }
        save(context, remaining)
        if (restoredPaths.isNotEmpty()) MediaScannerConnection.scanFile(context, restoredPaths.toTypedArray(), null, null)
        restoredPaths.size
    }

    /** 영구 삭제하고 확보한 용량을 돌려준다. */
    fun deleteForever(context: Context, ids: Set<String>): Long = synchronized(lock) {
        val items = load(context)
        var freed = 0L
        val remaining = items.filter { item ->
            if (item.id !in ids) return@filter true
            val f = File(item.trashPath)
            if (!f.exists() || f.delete()) {
                freed += item.size
                false
            } else {
                true
            }
        }
        save(context, remaining)
        freed
    }

    /** 보관 기간이 지난 항목 자동 삭제 (앱 시작 시 호출) */
    fun purgeExpired(context: Context): Int {
        val expired = list(context).filter { daysAgo(it.deletedAt) >= keepDays(context) }.map { it.id }.toSet()
        if (expired.isEmpty()) return 0
        deleteForever(context, expired)
        return expired.size
    }
}
