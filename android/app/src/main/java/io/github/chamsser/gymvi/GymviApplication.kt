package io.github.chamsser.gymvi

import android.app.Application
import com.naver.maps.map.NaverMapSdk
import io.github.chamsser.gymvi.data.AiConversationSnapshotStore
import io.github.chamsser.gymvi.data.AiLibraryStore
import io.github.chamsser.gymvi.ui.AiConversationController
import java.io.File

class GymviApplication : Application() {
    /**
     * Process-scoped so Activity recreation (rotation) and leaving the AI screen keep the
     * current conversation. It lives as long as the process and is never closed.
     */
    val aiConversationController: AiConversationController by lazy {
        AiConversationController(
            apiBaseUrl = BuildConfig.API_BASE_URL,
            libraryStore = AiLibraryStore(File(noBackupFilesDir, "ai_library_v2.json"),
                File(noBackupFilesDir, "ai_conversation_v1.json")),
        )
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.NAVER_MAPS_CLIENT_ID.isNotBlank()) {
            NaverMapSdk.getInstance(this).client =
                NaverMapSdk.NcpKeyClient(BuildConfig.NAVER_MAPS_CLIENT_ID)
        }
    }
}
