package com.phonecare.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.widget.RemoteViews
import com.phonecare.app.MainActivity
import com.phonecare.app.R
import com.phonecare.app.data.DeviceInfo
import java.util.Locale

/** 홈 화면 위젯: 관리 점수 + 배터리·저장공간·메모리·온도 */
/** 2×2 작은 위젯: 같은 내용을 정사각형 배치로 */
class PhoneCareWidgetSmall : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = PhoneCareWidget.build(context, R.layout.widget_phonecare_small)
        ids.forEach { manager.updateAppWidget(it, views) }
    }
}

class PhoneCareWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = build(context, R.layout.widget_phonecare)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        private var lastUpdate = 0L

        /** 위젯 종류별 (화면 구성, 위젯 클래스) */
        private val kinds = listOf(
            R.layout.widget_phonecare to PhoneCareWidget::class.java,
            R.layout.widget_phonecare_small to PhoneCareWidgetSmall::class.java,
        )

        fun hasWidgets(context: Context): Boolean = try {
            val manager = AppWidgetManager.getInstance(context)
            kinds.any { (_, cls) -> manager.getAppWidgetIds(ComponentName(context, cls)).isNotEmpty() }
        } catch (e: Exception) {
            false
        }

        /** 앱 화면을 열 때·배터리 상태가 바뀔 때 호출 (1분에 한 번으로 제한) */
        fun updateAll(context: Context, force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (!force && now - lastUpdate < 60_000) return
            lastUpdate = now
            try {
                val manager = AppWidgetManager.getInstance(context)
                for ((layout, cls) in kinds) {
                    val ids = manager.getAppWidgetIds(ComponentName(context, cls))
                    if (ids.isEmpty()) continue
                    val views = build(context, layout)
                    ids.forEach { manager.updateAppWidget(it, views) }
                }
            } catch (e: Exception) {
            }
        }

        /** 홈 화면에 위젯 추가 요청 (지원하는 런처에서만). 성공하면 true */
        fun requestPin(context: Context): Boolean = try {
            val manager = AppWidgetManager.getInstance(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.isRequestPinAppWidgetSupported) {
                manager.requestPinAppWidget(ComponentName(context, PhoneCareWidget::class.java), null, null)
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }

        fun build(context: Context, layout: Int): RemoteViews {
            val views = RemoteViews(context.packageName, layout)
            val b = DeviceInfo.battery(context)
            val m = try {
                DeviceInfo.memory(context)
            } catch (e: Exception) {
                null
            }
            val s = try {
                DeviceInfo.storage()
            } catch (e: Exception) {
                null
            }
            val score = DeviceInfo.score(b, m, s)
            val scoreColor = when {
                score >= 80 -> Color.parseColor("#2DE1C2")
                score >= 60 -> Color.parseColor("#F59E0B")
                else -> Color.parseColor("#EF4444")
            }
            val label = when {
                score >= 80 -> "아주 좋아요"
                score >= 60 -> "점검 필요"
                else -> "관리 필요"
            }
            views.setTextViewText(R.id.w_score, score.toString())
            views.setTextColor(R.id.w_score, scoreColor)
            views.setTextViewText(R.id.w_label, label)

            // 작은 위젯은 이름표가 없어서 아이콘을 앞에 붙임
            val small = layout == R.layout.widget_phonecare_small
            fun tag(icon: String) = if (small) "$icon " else ""
            views.setTextViewText(
                R.id.w_battery,
                tag("🔋") + (b?.takeIf { it.level >= 0 }?.let { "${it.level}%" + if (it.charging) "⚡" else "" } ?: "-"),
            )
            views.setTextViewText(R.id.w_storage, tag("💾") + (s?.let { "${(it.usedRatio * 100).toInt()}%" } ?: "-"))
            views.setTextViewText(
                R.id.w_memory,
                tag("🧠") + (m?.takeIf { it.total > 0 }?.let { "${((1f - it.available.toFloat() / it.total) * 100).toInt()}%" } ?: "-"),
            )
            val temp = b?.temperatureC
            views.setTextViewText(R.id.w_temp, tag("🌡") + (temp?.let { String.format(Locale.US, "%.0f°", it) } ?: "-"))
            if (temp != null) {
                views.setTextColor(
                    R.id.w_temp,
                    when {
                        temp >= 42f -> Color.parseColor("#EF4444")
                        temp >= 38f -> Color.parseColor("#F59E0B")
                        else -> Color.WHITE
                    },
                )
            }

            val open = PendingIntent.getActivity(
                context, 10,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.w_root, open)
            return views
        }
    }
}
