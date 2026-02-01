package org.jellyfin.mobile.downloads

import org.jellyfin.mobile.player.qualityoptions.QualityOption

data class DownloadQualityOption(
    val qualityOption: QualityOption,
    val durationTicks: Long,
) {
    val maxHeight: Int get() = qualityOption.maxHeight
    val bitrate: Int get() = qualityOption.bitrate

    val isAuto: Boolean get() = qualityOption.bitrate == 0

    val estimatedFileSizeBytes: Long
        get() {
            if (isAuto) return 0L
            val durationSeconds = durationTicks / TICKS_PER_SECOND
            return (bitrate.toLong() * durationSeconds) / BITS_PER_BYTE
        }

    companion object {
        private const val TICKS_PER_SECOND = 10_000_000L
        private const val BITS_PER_BYTE = 8

        fun fromQualityOption(qualityOption: QualityOption, durationTicks: Long): DownloadQualityOption {
            return DownloadQualityOption(qualityOption, durationTicks)
        }

        fun fromQualityOptions(qualityOptions: List<QualityOption>, durationTicks: Long): List<DownloadQualityOption> {
            return qualityOptions.map { fromQualityOption(it, durationTicks) }
        }
    }
}
