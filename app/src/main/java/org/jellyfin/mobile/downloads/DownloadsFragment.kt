package org.jellyfin.mobile.downloads

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.jellyfin.mobile.R
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.data.entity.DownloadStatus
import org.jellyfin.mobile.databinding.FragmentDownloadsBinding
import org.jellyfin.mobile.downloads.ui.DownloadItemOptionsDialog
import org.jellyfin.mobile.events.ActivityEvent
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.utils.applyWindowInsetsAsMargins
import org.jellyfin.mobile.utils.extensions.requireMainActivity
import org.jellyfin.mobile.utils.withThemedContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.UUID
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DownloadsFragment : Fragment(), KoinComponent {
    private val viewModel: DownloadsViewModel by inject()
    private val activityEventHandler: ActivityEventHandler by inject()
    private val apiClient: ApiClient by inject()
    private lateinit var adapter: DownloadsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val localInflater = inflater.withThemedContext(requireContext(), R.style.AppTheme_Settings)
        val binding = FragmentDownloadsBinding.inflate(localInflater, container, false)
        binding.root.applyWindowInsetsAsMargins()
        binding.toolbar.setTitle(R.string.downloads)

        requireMainActivity().apply {
            setSupportActionBar(binding.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }

        adapter = DownloadsAdapter(
            apiClient,
            onItemClick = { download -> onDownloadItemClick(download) },
            onItemHold = { download -> onDownloadItemHold(download) },
        )
        binding.recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.downloads.collect { downloads ->
                        adapter.submitList(downloads)
                    }
                }
                launch {
                    viewModel.validationErrors.collect { errors ->
                        adapter.setFileValidationErrors(errors)
                    }
                }
            }
        }

        return binding.root
    }

    private fun onDownloadItemClick(download: DownloadEntity) {
        val hasValidationError = viewModel.validationErrors.value.containsKey(download.itemId)
        if (hasValidationError || download.downloadStatus == DownloadStatus.FAILED) {
            activityEventHandler.emit(ActivityEvent.RemoveDownload(download.mediaSource))
            return
        }

        if (download.downloadStatus != DownloadStatus.COMPLETED) {
            return
        }

        val playOptions = PlayOptions(
            ids = listOf(download.mediaSource.itemId),
            mediaSourceId = download.mediaSource.id,
            startIndex = 0,
            startPosition = null,
            audioStreamIndex = 1,
            subtitleStreamIndex = -1,
            playFromDownloads = true,
        )
        activityEventHandler.emit(ActivityEvent.LaunchNativePlayer(playOptions))
    }

    private fun onDownloadItemHold(download: DownloadEntity) {
        val hasValidationError = viewModel.validationErrors.value.containsKey(download.itemId)

        DownloadItemOptionsDialog(
            context = requireContext(),
            download = download,
            hasValidationError = hasValidationError,
            callback = object : DownloadItemOptionsDialog.Callback {
                override fun onPlayOffline(download: DownloadEntity) {
                    playDownload(download)
                }

                override fun onDeleteDownload(download: DownloadEntity) {
                    val itemId = UUID.fromString(download.itemId.replaceFirst(
                        "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})".toRegex(),
                        "$1-$2-$3-$4-$5"
                    ))
                    viewModel.deleteDownload(itemId)
                }

                override fun onExtendExpiration(download: DownloadEntity) {
                    DownloadItemOptionsDialog.showExtendExpirationDialog(requireContext()) { days ->
                        val itemId = UUID.fromString(download.itemId.replaceFirst(
                            "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})".toRegex(),
                            "$1-$2-$3-$4-$5"
                        ))
                        viewModel.extendExpiration(itemId, days)
                    }
                }

                override fun onRedownload(download: DownloadEntity) {
                    val item = download.mediaSource.item ?: return
                    activityEventHandler.emit(
                        ActivityEvent.ShowDownloadQualitySheet(
                            itemId = download.mediaSource.itemId,
                            itemName = item.name ?: "",
                            itemType = item.type,
                            durationTicks = item.runTimeTicks ?: 0L,
                        )
                    )
                }
            },
        ).show()
    }

    private fun playDownload(download: DownloadEntity) {
        val playOptions = PlayOptions(
            ids = listOf(download.mediaSource.itemId),
            mediaSourceId = download.mediaSource.id,
            startIndex = 0,
            startPosition = null,
            audioStreamIndex = 1,
            subtitleStreamIndex = -1,
            playFromDownloads = true,
        )
        activityEventHandler.emit(ActivityEvent.LaunchNativePlayer(playOptions))
    }
}
