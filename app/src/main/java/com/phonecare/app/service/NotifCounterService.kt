package com.phonecare.app.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.phonecare.app.data.NotifStats

/**
 * 앱별 알림 개수만 센다. 알림 제목·내용은 읽거나 저장하지 않는다.
 * (진행 중 표시처럼 계속 떠 있는 알림은 제외)
 */
class NotifCounterService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        if (n.isOngoing || n.packageName == packageName) return
        try {
            NotifStats.record(applicationContext, n.packageName)
        } catch (e: Exception) {
        }
    }
}
