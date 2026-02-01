package org.jellyfin.mobile.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.sdk.model.UUID
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DownloadsViewModel : ViewModel(), KoinComponent {

    private val downloadDao: DownloadDao by inject()
    private val offlineDownloadManager: OfflineDownloadManager by inject()

    private val _downloads = MutableStateFlow(emptyList<DownloadEntity>())
    val downloads = _downloads.asStateFlow()

    private val _validationErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val validationErrors = _validationErrors.asStateFlow()

    init {
        getAllDownloads()
        validateDownloadsOnLoad()
    }

    private fun getAllDownloads() {
        viewModelScope.launch {
            downloadDao.getAllDownloads().flowOn(Dispatchers.IO).collect { downloads: List<DownloadEntity> ->
                _downloads.update { downloads }
            }
        }
    }

    private fun validateDownloadsOnLoad() {
        viewModelScope.launch(Dispatchers.IO) {
            val results = offlineDownloadManager.validateDownloads()
            val errors = results
                .filter { !it.isValid && it.errorMessage != null }
                .associate { it.itemId to it.errorMessage!! }
            _validationErrors.update { errors }
        }
    }

    fun deleteDownload(itemId: UUID) {
        viewModelScope.launch(Dispatchers.IO) {
            offlineDownloadManager.deleteDownload(itemId)
        }
    }

    fun extendExpiration(itemId: UUID, additionalDays: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            offlineDownloadManager.extendExpiration(itemId, additionalDays)
        }
    }

    fun refreshValidation() {
        validateDownloadsOnLoad()
    }
}
