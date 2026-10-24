package com.phonecare.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** 앱 전체 설정 (화면이 바로 반영되도록 상태로 보관) */
object AppSettings {
    const val THEME_AUTO = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2
    private const val PREFS = "settings"

    var themeMode by mutableIntStateOf(THEME_AUTO)
        private set

    fun load(context: Context) {
        themeMode = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("theme", THEME_AUTO)
    }

    fun setTheme(context: Context, mode: Int) {
        themeMode = mode
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("theme", mode).apply()
    }
}
