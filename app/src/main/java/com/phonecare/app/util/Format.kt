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
