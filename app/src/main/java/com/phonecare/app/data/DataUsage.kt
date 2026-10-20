package com.phonecare.app.data

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Process
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.util.Calendar

data class DataEntry(
    val key: String,
    val label: String,
    val packageName: String?, // 앱 정보로 이동할 수 있으면 패키지명
    val icon: ImageBitmap?,
    val rx: Long,
    val tx: Long,
) {
    val total: Long get() = rx + tx
}

/** 이번 달 앱별 데이터 사용량 (사용 기록 접근 권한 필요) */
object DataUsage {

    fun monthStart(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** wifi=false면 모바일 데이터. 실패하면 null */
    fun load(c: Context, wifi: Boolean): List<DataEntry>? {
        val nsm = c.getSystemService(NetworkStatsManager::class.java) ?: return null
        @Suppress("DEPRECATION")
        val type = if (wifi) ConnectivityManager.TYPE_WIFI else ConnectivityManager.TYPE_MOBILE
        val rxByUid = HashMap<Int, Long>()
        val txByUid = HashMap<Int, Long>()
        try {
            val stats = nsm.querySummary(type, null, monthStart(), System.currentTimeMillis())
            val b = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(b)
                rxByUid[b.uid] = (rxByUid[b.uid] ?: 0L) + b.rxBytes
                txByUid[b.uid] = (txByUid[b.uid] ?: 0L) + b.txBytes
            }
            stats.close()
        } catch (e: Exception) {
            return null
        }

        val pm = c.packageManager
        val merged = LinkedHashMap<String, DataEntry>()
        for (uid in rxByUid.keys) {
            val rx = rxByUid[uid] ?: 0L
            val tx = txByUid[uid] ?: 0L
            if (rx + tx <= 0) continue
            val pkgs = try {
                pm.getPackagesForUid(uid)
            } catch (e: Exception) {
                null
            }
            val entry: DataEntry = when {
                uid == NetworkStats.Bucket.UID_REMOVED -> DataEntry("removed", "삭제된 앱", null, null, rx, tx)
                uid == NetworkStats.Bucket.UID_TETHERING -> DataEntry("tether", "핫스팟(테더링)", null, null, rx, tx)
                uid == Process.SYSTEM_UID || pkgs.isNullOrEmpty() -> DataEntry("system", "시스템", null, null, rx, tx)
                else -> {
                    val pkg = pkgs?.firstOrNull() ?: ""
                    try {
                        @Suppress("DEPRECATION")
                        val ai = pm.getApplicationInfo(pkg, 0)
                        val icon = try {
                            pm.getApplicationIcon(ai).toBitmap(96, 96).asImageBitmap()
                        } catch (e: Exception) {
                            null
                        }
                        val label = pm.getApplicationLabel(ai).toString() + if ((pkgs?.size ?: 1) > 1) " 외 ${(pkgs?.size ?: 1) - 1}" else ""
                        DataEntry(pkg, label, pkg, icon, rx, tx)
                    } catch (e: Exception) {
                        DataEntry("system", "시스템", null, null, rx, tx)
                    }
                }
            }
            val prev = merged[entry.key]
            merged[entry.key] = if (prev == null) entry else prev.copy(rx = prev.rx + entry.rx, tx = prev.tx + entry.tx)
        }
        return merged.values.sortedByDescending { it.total }
    }
}
