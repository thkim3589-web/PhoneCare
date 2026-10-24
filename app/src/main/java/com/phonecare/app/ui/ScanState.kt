package com.phonecare.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.phonecare.app.data.AppItem
import com.phonecare.app.data.AppStorage
import com.phonecare.app.data.FoundFile
import com.phonecare.app.data.MsgFile
import com.phonecare.app.data.PhotoGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/**
 * 검사 결과와 진행 중인 작업을 화면과 분리해서 보관.
 * 다른 탭이나 하위 화면으로 옮겨도 결과가 사라지거나 검사가 취소되지 않음.
 */
object ScanState {
    /** 화면이 사라져도 계속 도는 작업용 */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // 저장공간 탭에서 보고 있는 하위 화면 (홈에서 바로 휴지통으로 보낼 때도 사용)
    var storageSection by mutableIntStateOf(0)

    // 정리 후보
    var cleanup by mutableStateOf<List<FoundFile>?>(null)
    var cleanupScanning by mutableStateOf(false)
    var cleanupCount by mutableIntStateOf(0)
    var cleanupAt by mutableLongStateOf(0L)
    var cleanupJob: Job? = null

    // 중복 사진
    var dupGroups by mutableStateOf<List<PhotoGroup>?>(null)
    var dupScanning by mutableStateOf(false)
    var dupProgress by mutableStateOf(0 to 0)
    var dupSelected by mutableStateOf(setOf<String>())
    var dupJob: Job? = null

    // 메신저
    var msgFiles by mutableStateOf<List<MsgFile>?>(null)
    var msgKakao by mutableStateOf<AppStorage?>(null)
    var msgProtectedKey: List<String>? = null

    // 안 쓰는 앱 목록 (아이콘·용량 읽기가 느려서 기억해 둠)
    var apps by mutableStateOf<List<AppItem>?>(null)
    var appsLoadedAt = 0L

    /** 파일이 휴지통으로 옮겨지면 모든 결과에서 빼기 */
    fun removePaths(paths: Set<String>) {
        if (paths.isEmpty()) return
        cleanup = cleanup?.filterNot { it.path in paths }
        dupGroups = dupGroups
            ?.map { g -> g.copy(photos = g.photos.filterNot { it.path in paths }) }
            ?.filter { it.photos.size > 1 }
        dupSelected = dupSelected - paths
        msgFiles = msgFiles?.filterNot { it.path in paths }
    }
}
