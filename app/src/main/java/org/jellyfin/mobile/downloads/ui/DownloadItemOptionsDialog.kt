package org.jellyfin.mobile.downloads.ui

import android.app.AlertDialog
import android.content.Context
import org.jellyfin.mobile.R
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.data.entity.DownloadStatus

class DownloadItemOptionsDialog(
    private val context: Context,
    private val download: DownloadEntity,
    private val hasValidationError: Boolean,
    private val callback: Callback,
) {
    interface Callback {
        fun onPlayOffline(download: DownloadEntity)
        fun onDeleteDownload(download: DownloadEntity)
        fun onExtendExpiration(download: DownloadEntity)
        fun onRedownload(download: DownloadEntity)
    }

    fun show() {
        val options = buildOptions()
        val optionLabels = options.map { it.label }.toTypedArray()

        AlertDialog.Builder(context)
            .setTitle(R.string.download_options_title)
            .setItems(optionLabels) { _, which ->
                options[which].action()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun buildOptions(): List<DialogOption> {
        val options = mutableListOf<DialogOption>()

        if (hasValidationError || download.downloadStatus == DownloadStatus.FAILED) {
            options.add(
                DialogOption(
                    label = context.getString(R.string.download_option_redownload),
                    action = { callback.onRedownload(download) },
                ),
            )
        } else if (download.downloadStatus == DownloadStatus.COMPLETED) {
            options.add(
                DialogOption(
                    label = context.getString(R.string.download_option_play),
                    action = { callback.onPlayOffline(download) },
                ),
            )
        }

        if (download.expirationTimestamp != null) {
            options.add(
                DialogOption(
                    label = context.getString(R.string.download_option_extend),
                    action = { callback.onExtendExpiration(download) },
                ),
            )
        }

        options.add(
            DialogOption(
                label = context.getString(R.string.download_option_delete),
                action = { callback.onDeleteDownload(download) },
            ),
        )

        return options
    }

    private data class DialogOption(
        val label: String,
        val action: () -> Unit,
    )

    companion object {
        fun showExtendExpirationDialog(
            context: Context,
            onExtend: (days: Int) -> Unit,
        ) {
            val dayOptions = intArrayOf(7, 14, 30)
            val labels = dayOptions.map { days ->
                context.resources.getQuantityString(R.plurals.download_extend_days, days, days)
            }.toTypedArray()

            AlertDialog.Builder(context)
                .setTitle(R.string.download_option_extend)
                .setItems(labels) { _, which ->
                    onExtend(dayOptions[which])
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
}
