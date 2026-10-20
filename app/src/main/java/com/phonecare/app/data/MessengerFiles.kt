package com.phonecare.app.data

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Environment
import android.os.Process
import java.io.File

enum class MsgKind(val label: String) {
    PHOTO("사진"),
    VIDEO("동영상"),
    DOC("문서·기타"),
}

data class MsgFile(
    val path: String,
    val name: String,
    val size: Long,
    val modified: Long,
    val kind: MsgKind,
    val app: String,
)

data class MessengerApp(val name: String, val pkg: String, val folders: List<String>)

data class AppStorage(val app: Long, val data: Long, val cache: Long) {
    val total: Long get() = app + data
}

/** 메신저로 받아서 저장된 파일(사진·동영상·문서) 찾기 */
object MessengerFiles {

    val apps = listOf(
        MessengerApp(
            "카카오톡", "com.kakao.talk",
            listOf("KakaoTalkDownload", "Download/KakaoTalk", "Pictures/KakaoTalk", "Movies/KakaoTalk", "DCIM/KakaoTalk", "Documents/KakaoTalk"),
        ),
        MessengerApp("텔레그램", "org.telegram.messenger", listOf("Telegram", "Download/Telegram", "Pictures/Telegram", "Movies/Telegram")),
        MessengerApp("라인", "jp.naver.line.android", listOf("Pictures/LINE", "Movies/LINE", "Download/LINE")),
        MessengerApp("밴드", "com.nhn.android.band", listOf("BAND", "Pictures/BAND", "Download/BAND")),
        MessengerApp("왓츠앱", "com.whatsapp", listOf("WhatsApp/Media", "Android/media/com.whatsapp/WhatsApp/Media")),
    )

    private val photoExt = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp")
    private val videoExt = setOf("mp4", "mkv", "mov", "avi", "3gp", "webm", "m4v")

    private fun kindOf(name: String): MsgKind {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            in photoExt -> MsgKind.PHOTO
            in videoExt -> MsgKind.VIDEO
            else -> MsgKind.DOC
        }
    }

    /** 백그라운드에서 호출 */
    fun scan(protectedFolders: List<String>): List<MsgFile> {
        val root = Environment.getExternalStorageDirectory()
        val seen = HashSet<String>()
        val out = ArrayList<MsgFile>()
        for (app in apps) {
            for (rel in app.folders) {
                val dir = File(root, rel)
                if (!dir.isDirectory) continue
                if (ProtectedFolders.isProtected(dir.absolutePath, protectedFolders)) continue
                dir.walkTopDown()
                    .onEnter { d ->
                        !ProtectedFolders.isProtected(d.absolutePath, protectedFolders) &&
                            (d == dir || !d.name.startsWith("."))
                    }
                    .forEach { f ->
                        if (!f.isFile || f.name.startsWith(".")) return@forEach
                        val path = f.absolutePath
                        if (!seen.add(path)) return@forEach
                        val size = f.length()
                        if (size <= 0) return@forEach
                        out.add(MsgFile(path, f.name, size, f.lastModified(), kindOf(f.name), app.name))
                    }
            }
        }
        return out.sortedByDescending { it.size }
    }

    /** 메신저 앱 자체가 차지하는 용량 (사용 기록 접근 필요). 설치 안 돼 있거나 실패하면 null */
    fun appStorage(context: Context, pkg: String): AppStorage? = try {
        @Suppress("DEPRECATION")
        val ai = context.packageManager.getApplicationInfo(pkg, 0)
        val s = context.getSystemService(StorageStatsManager::class.java)
            .queryStatsForPackage(ai.storageUuid, pkg, Process.myUserHandle())
        AppStorage(s.appBytes, s.dataBytes, s.cacheBytes)
    } catch (e: Exception) {
        null
    }

    fun isInstalled(context: Context, pkg: String): Boolean = AppUsage.isInstalled(context, pkg)
}
