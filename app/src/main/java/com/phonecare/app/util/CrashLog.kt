package com.phonecare.app.util

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 앱이 비정상 종료되면 원인을 파일로 남기고, 다음 실행 때 보여준다 */
object CrashLog {

    private fun file(c: Context) = File(c.filesDir, "crash.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        if (prev is Handler) return
        Thread.setDefaultUncaughtExceptionHandler(Handler(app, prev))
    }

    fun read(c: Context): String? = try {
        file(c).takeIf { it.exists() }?.readText()
    } catch (e: Exception) {
        null
    }

    fun clear(c: Context) {
        file(c).delete()
    }

    private class Handler(
        private val app: Context,
        private val prev: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                @Suppress("DEPRECATION")
                val version = try {
                    app.packageManager.getPackageInfo(app.packageName, 0).versionName
                } catch (ex: Exception) {
                    "?"
                }
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date())
                file(app).writeText(
                    "폰케어 $version · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}\n$time\n\n$sw",
                )
            } catch (ex: Exception) {
            }
            prev?.uncaughtException(t, e)
        }
    }
}
