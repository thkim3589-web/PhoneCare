package com.phonecare.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val DAY_MS = 86_400_000L

fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "-"
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var i = 0
    while (value >= 1024 && i < units.size - 1) {
        value /= 1024
        i++
    }
    val pattern = if (value >= 100) "%.0f %s" else "%.1f %s"
    return String.format(Locale.US, pattern, value, units[i])
}

fun daysAgo(time: Long): Long = ((System.currentTimeMillis() - time) / DAY_MS).coerceAtLeast(0)

fun formatDate(time: Long): String =
    SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).format(Date(time))

/** 설정 화면을 연다. 기기에 해당 화면이 없으면 기본 설정 화면으로 대신 연다. */
fun Context.openSettings(intent: Intent) {
    val withFlags = intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(withFlags)
    } catch (e: ActivityNotFoundException) {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (ignored: Exception) {
        }
    } catch (ignored: SecurityException) {
    }
}

/** "방금", "5분 전", "3시간 전", "2일 전" */
fun agoText(time: Long): String {
    val min = (System.currentTimeMillis() - time) / 60_000
    return when {
        min < 1 -> "방금"
        min < 60 -> "${min}분 전"
        min < 60 * 24 -> "${min / 60}시간 전"
        else -> "${min / (60 * 24)}일 전"
    }
}

/** 파일을 알맞은 앱(갤러리·동영상·문서 뷰어)으로 열어 미리 보기 */
fun Context.openFilePreview(path: String) {
    try {
        val file = java.io.File(path)
        if (!file.exists()) {
            android.widget.Toast.makeText(this, "파일을 찾을 수 없어요", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val ext = path.substringAfterLast('.', "").lowercase()
        val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(Intent.createChooser(view, "미리 보기").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        android.widget.Toast.makeText(this, "이 파일을 열 수 있는 앱이 없어요", android.widget.Toast.LENGTH_SHORT).show()
    }
}

/** 기간 선택 (오늘 / 7일 / 30일) */
enum class Period(val days: Int, val label: String) {
    TODAY(1, "오늘"),
    WEEK(7, "7일"),
    MONTH(30, "30일");

    /** 기간 시작 시각: 오늘은 0시부터, 나머지는 지금부터 N일 전 */
    fun start(): Long {
        if (this == TODAY) {
            return java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        return System.currentTimeMillis() - days * DAY_MS
    }
}
