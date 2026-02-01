package org.jellyfin.mobile.downloads

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.error
import coil3.request.fallback
import org.jellyfin.mobile.R
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.data.entity.DownloadStatus
import org.jellyfin.mobile.databinding.DownloadItemExtendedBinding
import org.jellyfin.mobile.utils.extensions.toFileSize
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class DownloadsAdapter(
    private val apiClient: ApiClient,
    private val onItemClick: (DownloadEntity) -> Unit,
    private val onItemHold: (DownloadEntity) -> Unit,
) : ListAdapter<DownloadEntity, DownloadsAdapter.DownloadViewHolder>(
    DownloadDiffCallback(),
) {
    private val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
    private var fileValidationErrors: Map<String, String> = emptyMap()

    fun setFileValidationErrors(errors: Map<String, String>) {
        fileValidationErrors = errors
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder {
        val binding = DownloadItemExtendedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DownloadViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DownloadViewHolder(private val binding: DownloadItemExtendedBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(downloadEntity: DownloadEntity) {
            val context = itemView.context

            val mediaItem: BaseItemDto? = downloadEntity.mediaSource.item
            binding.textViewName.text = downloadEntity.mediaSource.getName(context)
            binding.textViewDescription.text = when {
                mediaItem?.seriesName != null -> context.getString(
                    R.string.tv_show_desc,
                    mediaItem.seriesName,
                    mediaItem.parentIndexNumber,
                    mediaItem.indexNumber,
                )
                mediaItem?.productionYear != null -> mediaItem.productionYear.toString()
                else -> downloadEntity.mediaSource.id
            }

            bindFileSize(downloadEntity)
            bindQualityBadge(downloadEntity)
            bindExpiration(context, downloadEntity)
            bindStatus(context, downloadEntity)
            bindProgress(downloadEntity)

            itemView.setOnClickListener {
                onItemClick(downloadEntity)
            }
            itemView.setOnLongClickListener {
                onItemHold(downloadEntity)
                true
            }

            val thumbnailUrl = getThumbnailUrl(context, downloadEntity.mediaSource.itemId)
            binding.imageViewThumbnail.load(thumbnailUrl) {
                fallback(R.drawable.ic_local_movies_white_64)
                error(R.drawable.ic_local_movies_white_64)
            }
        }

        private fun bindFileSize(downloadEntity: DownloadEntity) {
            val sizeBytes = downloadEntity.fileSizeBytes ?: downloadEntity.mediaSource.downloadSize
            binding.textViewFileSize.text = sizeBytes.toFileSize()
        }

        private fun bindQualityBadge(downloadEntity: DownloadEntity) {
            val maxHeight = downloadEntity.qualityMaxHeight
            if (maxHeight != null && maxHeight > 0) {
                binding.textViewQualityBadge.visibility = View.VISIBLE
                binding.textViewQualityBadge.text = "${maxHeight}p"
            } else {
                binding.textViewQualityBadge.visibility = View.GONE
            }
        }

        private fun bindExpiration(context: Context, downloadEntity: DownloadEntity) {
            val expiration = downloadEntity.expirationTimestamp
            if (expiration != null) {
                binding.dividerExpiration.visibility = View.VISIBLE
                binding.textViewExpiration.visibility = View.VISIBLE

                val now = System.currentTimeMillis()
                if (expiration <= now) {
                    binding.textViewExpiration.text = context.getString(R.string.download_expired)
                    binding.textViewExpiration.setTextColor(context.getColor(R.color.download_status_error))
                } else {
                    val dateStr = dateFormat.format(Date(expiration))
                    binding.textViewExpiration.text = context.getString(R.string.download_expires, dateStr)
                    binding.textViewExpiration.setTextColor(context.getColor(android.R.color.secondary_text_dark))
                }
            } else {
                binding.dividerExpiration.visibility = View.GONE
                binding.textViewExpiration.visibility = View.GONE
            }
        }

        private fun bindStatus(context: Context, downloadEntity: DownloadEntity) {
            val validationError = fileValidationErrors[downloadEntity.itemId]

            when {
                validationError != null -> {
                    binding.textViewStatus.visibility = View.VISIBLE
                    binding.textViewStatus.text = validationError
                    binding.textViewStatus.setTextColor(context.getColor(R.color.download_status_error))
                }
                downloadEntity.downloadStatus == DownloadStatus.FAILED -> {
                    binding.textViewStatus.visibility = View.VISIBLE
                    binding.textViewStatus.text = context.getString(R.string.download_failed)
                    binding.textViewStatus.setTextColor(context.getColor(R.color.download_status_error))
                }
                downloadEntity.downloadStatus == DownloadStatus.PENDING -> {
                    binding.textViewStatus.visibility = View.VISIBLE
                    binding.textViewStatus.text = context.getString(R.string.download_pending)
                    binding.textViewStatus.setTextColor(context.getColor(R.color.download_status_warning))
                }
                else -> {
                    binding.textViewStatus.visibility = View.GONE
                }
            }
        }

        private fun bindProgress(downloadEntity: DownloadEntity) {
            val isDownloading = downloadEntity.downloadStatus == DownloadStatus.DOWNLOADING
            binding.progressBarDownload.isVisible = isDownloading
            if (isDownloading) {
                binding.progressBarDownload.progress = (downloadEntity.downloadProgress * 100).toInt()
            }
        }
    }

    fun getThumbnailUrl(context: Context, mediaSourceItemId: UUID): String? {
        val size = context.resources.getDimensionPixelSize(R.dimen.movie_thumbnail_list_size)

        return apiClient.imageApi.getItemImageUrl(
            itemId = mediaSourceItemId,
            imageType = ImageType.PRIMARY,
            maxWidth = size,
            maxHeight = size,
        )
    }
}
