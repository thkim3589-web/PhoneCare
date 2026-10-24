package com.phonecare.app.data

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseNote(val version: String, val code: Int, val date: String, val items: List<String>)

/**
 * 업데이트 내역.
 * - 앱 안: assets/changelog.json (설치된 버전까지의 내역)
 * - 업데이트 배너: 저장소의 최신 changelog.json을 받아 새 버전 내용을 미리 보여줌
 */
object Changelog {

    private const val REMOTE =
        "https://raw.githubusercontent.com/thkim3589-web/PhoneCare/main/app/src/main/assets/changelog.json"
    private const val PREFS = "changelog"
    private const val KEY_SEEN = "seen_code"

    private fun parse(text: String): List<ReleaseNote> = try {
        val arr = JSONArray(text)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val items = o.getJSONArray("items")
            ReleaseNote(
                version = o.getString("version"),
                code = o.getInt("code"),
                date = o.optString("date", ""),
                items = (0 until items.length()).map { items.getString(it) },
            )
        }.sortedByDescending { it.code }
    } catch (e: Exception) {
        emptyList()
    }

    fun local(context: Context): List<ReleaseNote> = try {
        parse(context.assets.open("changelog.json").bufferedReader().use { it.readText() })
    } catch (e: Exception) {
        emptyList()
    }

    /** 저장소의 최신 내역 (실패하면 빈 목록). 백그라운드에서 호출 */
    fun remote(): List<ReleaseNote> = try {
        val conn = (URL(REMOTE).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
        }
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        parse(text)
    } catch (e: Exception) {
        emptyList()
    }

    fun lastSeen(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_SEEN, 0)

    fun markSeen(context: Context, code: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_SEEN, code).apply()
    }

    fun toShareText(notes: List<ReleaseNote>): String = buildString {
        append("📱 폰케어 업데이트 소식\n")
        notes.forEach { n ->
            append("\n[v${n.version}] ${n.date}\n")
            n.items.forEach { append("• ").append(it).append('\n') }
        }
        append("\n휴대폰 저장공간·배터리·성능을 한 번에 관리하는 앱이에요.")
    }

    /** 문자·카톡 등으로 공유 */
    fun share(context: Context, notes: List<ReleaseNote>) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, toShareText(notes))
        context.startActivity(Intent.createChooser(send, "업데이트 내용 공유").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
