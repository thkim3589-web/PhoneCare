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
class PhoneCareWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = build(context)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        private var lastUpdate = 0L

        fun hasWidgets(context: Context): Boolean = try {
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, PhoneCareWidget::class.java)).isNotEmpty()
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
                val ids = manager.getAppWidgetIds(ComponentName(context, PhoneCareWidget::class.java))
                if (ids.isEmpty()) return
                val views = build(context)
                ids.forEach { manager.updateAppWidget(it, views) }
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

        private fun build(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_phonecare)
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

            views.setTextViewText(
                R.id.w_battery,
                b?.takeIf { it.level >= 0 }?.let { "${it.level}%" + if (it.charging) "⚡" else "" } ?: "-",
            )
            views.setTextViewText(R.id.w_storage, s?.let { "${(it.usedRatio * 100).toInt()}%" } ?: "-")
            views.setTextViewText(
                R.id.w_memory,
                m?.takeIf { it.total > 0 }?.let { "${((1f - it.available.toFloat() / it.total) * 100).toInt()}%" } ?: "-",
            )
            val temp = b?.temperatureC
            views.setTextViewText(R.id.w_temp, temp?.let { String.format(Locale.US, "%.0f°", it) } ?: "-")
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
