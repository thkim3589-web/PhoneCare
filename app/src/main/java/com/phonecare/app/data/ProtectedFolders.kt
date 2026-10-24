package com.phonecare.app.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract

/** 정리 후보·중복 사진 검사에서 제외할 폴더 목록 */
object ProtectedFolders {

    private const val PREFS = "protect"
    private const val KEY = "folders"

    private val root: String get() = Environment.getExternalStorageDirectory().absolutePath

    fun list(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet())
            ?.toList()
            ?.sorted()
            ?: emptyList()

    fun add(context: Context, path: String) {
        val updated = list(context).toMutableSet().apply { add(path.trimEnd('/')) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, updated).apply()
    }

    fun remove(context: Context, path: String) {
        val updated = list(context).toMutableSet().apply { remove(path) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, updated).apply()
    }

    fun isProtected(path: String, folders: List<String>): Boolean =
        folders.any { path == it || path.startsWith("$it/") }

    /** 화면에 보여줄 짧은 경로 */
    fun display(path: String): String = path.removePrefix(root).ifEmpty { "/" }

    /**
     * 폴더 선택기(SAF)가 돌려준 주소를 실제 경로로 바꾼다.
     * 내장 저장소만 지원하고, 저장소 전체(최상위)는 보호할 수 없게 막는다.
     */
    fun pathFromTreeUri(uri: Uri): String? = try {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val parts = docId.split(":", limit = 2)
        val rel = parts.getOrElse(1) { "" }.trim('/')
        if (parts[0] != "primary" || rel.isEmpty()) null else "$root/$rel"
    } catch (e: Exception) {
        null
    }

    private val workKeywords = listOf(
        "현장", "공사", "작업", "업무", "회사", "점검", "시공", "설계", "도면", "준공", "안전",
        "work", "site", "project", "company", "office",
    )

    /**
     * 이름으로 봐서 업무용 같은 폴더 추천 (최상위와 DCIM·Pictures·Download·Documents 아래 2단계까지).
     * 이미 보호 중인 폴더는 뺌. 백그라운드에서 호출.
     */
    fun suggestWorkFolders(context: Context): List<String> {
        val current = list(context)
        val rootDir = Environment.getExternalStorageDirectory()
        val bases = listOf(rootDir) + listOf("DCIM", "Pictures", "Download", "Documents").map { java.io.File(rootDir, it) }
        val found = LinkedHashSet<String>()
        fun check(dir: java.io.File, depth: Int) {
            val children = try {
                dir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") && f.name != "Android" }
            } catch (e: Exception) {
                null
            } ?: return
            for (child in children) {
                val name = child.name.lowercase()
                if (workKeywords.any { name.contains(it) }) {
                    val path = child.absolutePath
                    if (!isProtected(path, current)) found.add(path)
                } else if (depth < 2) {
                    check(child, depth + 1)
                }
            }
        }
        bases.forEach { if (it.isDirectory) check(it, if (it == rootDir) 2 else 1) }
        return found.take(10)
    }
}
