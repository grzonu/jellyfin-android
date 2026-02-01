package org.jellyfin.mobile.downloads.ui

import android.os.Bundle
import android.os.Parcelable
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.parcelize.Parcelize
import org.jellyfin.mobile.R
import org.jellyfin.mobile.databinding.BottomSheetDownloadQualityBinding
import org.jellyfin.mobile.downloads.DownloadQualityOption
import org.jellyfin.mobile.player.qualityoptions.QualityOptionsProvider
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.UUID

class DownloadQualityBottomSheet : BottomSheetDialogFragment() {

    @Parcelize
    data class DownloadRequest(
        val itemId: UUID,
        val itemName: String,
        val itemType: BaseItemKind,
        val durationTicks: Long,
    ) : Parcelable

    interface Callback {
        fun onDownloadConfirmed(
            itemId: UUID,
            quality: DownloadQualityOption,
            expirationDays: Int?,
        )
        fun onDownloadCancelled()
    }

    private var _binding: BottomSheetDownloadQualityBinding? = null
    private val binding get() = _binding!!

    private lateinit var request: DownloadRequest
    private var callback: Callback? = null
    private var qualityOptions: List<DownloadQualityOption> = emptyList()
    private var selectedQualityIndex = 0
    private var selectedExpirationDays: Int? = null

    private val expirationOptions = listOf(
        null to R.string.download_auto_delete_never,
        7 to R.plurals.download_auto_delete_days,
        14 to R.plurals.download_auto_delete_days,
        30 to R.plurals.download_auto_delete_days,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        request = requireArguments().getParcelable(ARG_REQUEST)
            ?: throw IllegalStateException("DownloadRequest is required")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetDownloadQualityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textViewItemName.text = request.itemName

        setupQualityDropdown()
        setupExpirationDropdown()
        setupButtons()
    }

    private fun setupQualityDropdown() {
        val qualityOptionsProvider = QualityOptionsProvider()
        val baseQualityOptions = qualityOptionsProvider.getApplicableQualityOptions(
            videoWidth = Int.MAX_VALUE,
            videoHeight = Int.MAX_VALUE,
        ).filter { it.bitrate > 0 }

        qualityOptions = DownloadQualityOption.fromQualityOptions(baseQualityOptions, request.durationTicks)

        val qualityLabels = qualityOptions.map { option ->
            buildQualityLabel(option)
        }

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            qualityLabels,
        )
        binding.qualityDropdown.setAdapter(adapter)

        if (qualityOptions.isNotEmpty()) {
            val defaultIndex = qualityOptions.indexOfFirst { it.maxHeight == DEFAULT_QUALITY_HEIGHT }
                .takeIf { it >= 0 } ?: 0
            selectedQualityIndex = defaultIndex
            binding.qualityDropdown.setText(qualityLabels[defaultIndex], false)
            updateEstimatedSize()
        }

        binding.qualityDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedQualityIndex = position
            updateEstimatedSize()
        }
    }

    private fun buildQualityLabel(option: DownloadQualityOption): String {
        val heightLabel = "${option.maxHeight}p"
        val bitrateLabel = formatBitrate(option.bitrate)
        return "$heightLabel - $bitrateLabel"
    }

    private fun formatBitrate(bitrate: Int): String {
        return when {
            bitrate >= 1_000_000 -> "${bitrate / 1_000_000} Mbps"
            bitrate >= 1_000 -> "${bitrate / 1_000} Kbps"
            else -> "$bitrate bps"
        }
    }

    private fun updateEstimatedSize() {
        val selectedOption = qualityOptions.getOrNull(selectedQualityIndex) ?: return
        val formattedSize = Formatter.formatFileSize(requireContext(), selectedOption.estimatedFileSizeBytes)
        binding.textViewEstimatedSize.text = getString(R.string.download_estimated_size, formattedSize)
    }

    private fun setupExpirationDropdown() {
        val labels = expirationOptions.map { (days, resId) ->
            if (days == null) {
                getString(resId)
            } else {
                resources.getQuantityString(resId, days, days)
            }
        }

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            labels,
        )
        binding.expirationDropdown.setAdapter(adapter)
        binding.expirationDropdown.setText(labels[0], false)

        binding.expirationDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedExpirationDays = expirationOptions[position].first
        }
    }

    private fun setupButtons() {
        binding.buttonCancel.setOnClickListener {
            callback?.onDownloadCancelled()
            dismiss()
        }

        binding.buttonDownload.setOnClickListener {
            val selectedQuality = qualityOptions.getOrNull(selectedQualityIndex) ?: return@setOnClickListener
            callback?.onDownloadConfirmed(
                request.itemId,
                selectedQuality,
                selectedExpirationDays,
            )
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun setCallback(callback: Callback) {
        this.callback = callback
    }

    companion object {
        const val TAG = "DownloadQualityBottomSheet"
        private const val ARG_REQUEST = "request"
        private const val DEFAULT_QUALITY_HEIGHT = 720

        fun newInstance(request: DownloadRequest): DownloadQualityBottomSheet {
            return DownloadQualityBottomSheet().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_REQUEST, request)
                }
            }
        }
    }
}
