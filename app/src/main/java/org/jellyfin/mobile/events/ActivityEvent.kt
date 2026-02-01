package org.jellyfin.mobile.events

import android.net.Uri
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
import org.jellyfin.sdk.model.api.BaseItemKind
import org.json.JSONArray
import java.util.UUID

sealed class ActivityEvent {
    class ChangeFullscreen(val isFullscreen: Boolean) : ActivityEvent()
    class LaunchNativePlayer(val playOptions: PlayOptions) : ActivityEvent()
    class OpenUrl(val uri: String) : ActivityEvent()
    class DownloadFile(val uri: Uri, val title: String, val filename: String) : ActivityEvent()
    class RemoveDownload(val download: LocalJellyfinMediaSource, val force: Boolean = false) : ActivityEvent()
    class CastMessage(val action: String, val args: JSONArray) : ActivityEvent()
    class ShowDownloadQualitySheet(
        val itemId: UUID,
        val itemName: String,
        val itemType: BaseItemKind,
        val durationTicks: Long,
    ) : ActivityEvent()
    data object RequestBluetoothPermission : ActivityEvent()
    data object OpenSettings : ActivityEvent()
    data object SelectServer : ActivityEvent()
    data object ExitApp : ActivityEvent()
    data object OpenDownloads : ActivityEvent()
}
