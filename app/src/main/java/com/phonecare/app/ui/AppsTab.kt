package com.phonecare.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 앱 탭: 안 쓰는 앱 / 권한 점검 전환 */
@Composable
fun AppsTab(resumeTick: Int) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("📦 안 쓰는 앱", "🔐 권한 점검").forEachIndexed { i, label ->
                FilterChip(selected = section == i, onClick = { section = i }, label = { Text(label) })
            }
        }
        Box(Modifier.weight(1f)) {
            if (section == 0) AppsScreen(resumeTick) else PermissionAuditScreen(resumeTick)
        }
    }
}
