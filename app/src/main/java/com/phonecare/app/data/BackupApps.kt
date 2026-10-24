package com.phonecare.app.data

import android.content.Context
import android.content.Intent

/** 사진 백업(클라우드) 앱 확인 — 실제 백업 여부는 각 앱에서만 확인할 수 있어서 바로 열어주기만 함 */
object BackupApps {
    private val known = listOf(
        "구글 포토" to "com.google.android.apps.photos",
        "OneDrive" to "com.microsoft.skydrive",
        "네이버 MYBOX" to "com.nhn.android.ndrive",
        "Dropbox" to "com.dropbox.android",
    )

    /** 설치된 백업 앱 (이름, 패키지) */
    fun installed(context: Context): List<Pair<String, String>> =
        known.filter { (_, pkg) -> context.packageManager.getLaunchIntentForPackage(pkg) != null }

    fun open(context: Context, pkg: String) {
        context.packageManager.getLaunchIntentForPackage(pkg)?.let {
            try {
                context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
            }
        }
    }
}
