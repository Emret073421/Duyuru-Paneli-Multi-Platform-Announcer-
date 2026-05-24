package com.example.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.api.ServiceResult
import com.example.data.database.AnnouncementEntity
import com.example.data.database.ChannelEntity
import com.example.data.repository.AnnouncementRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Görev/Amaç: Gönderim ekranının durumunu (UI State) temsil eder.
 * Kullanıcı metni, aktif görsel ekleri (yerel resim ve uzak URL linki) ve gönderim durumlarını barındırır.
 */
data class BroadcastUiState(
    val announcementText: String = "",
    val isBroadcasting: Boolean = false,
    val broadcastSuccess: Boolean? = null,
    val broadcastResultMessage: String = "",
    
    // Görsel ekleme alanları (Remote url ve yerel byte verisi)
    val attachmentUrl: String = "",
    val attachmentBytes: ByteArray? = null,
    val attachmentUriString: String? = null, // UI'da seçilen yerel görseli önizlemek için
    
    // Test bağlantısı durumları
    val isTesting: Boolean = false,
    val testResult: String? = null
)

class AnnouncementViewModel(private val repository: AnnouncementRepository) : ViewModel() {

    // Main UI state
    private val _uiState = MutableStateFlow(BroadcastUiState())
    val uiState: StateFlow<BroadcastUiState> = _uiState.asStateFlow()

    // Channels stored in database
    val channels: StateFlow<List<ChannelEntity>> = repository.channels
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // History logs
    val history: StateFlow<List<AnnouncementEntity>> = repository.history
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Set of selected channel IDs for broadcasting
    private val _selectedChannelIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedChannelIds: StateFlow<Set<Int>> = _selectedChannelIds.asStateFlow()

    // Auto-select enabled channels when channels flow resolves for the first time
    init {
        viewModelScope.launch {
            repository.channels.collect { channelList ->
                // Keep selections that still exist, and auto-select new channels by default if set is empty
                _selectedChannelIds.update { currentSet ->
                    val existingIds = channelList.map { it.id }.toSet()
                    if (currentSet.isEmpty()) {
                        channelList.filter { it.isEnabled }.map { it.id }.toSet()
                    } else {
                        currentSet.filter { it in existingIds }.toSet()
                    }
                }
            }
        }
    }

    fun toggleChannelSelection(channelId: Int) {
        _selectedChannelIds.update { current ->
            if (current.contains(channelId)) {
                current - channelId
            } else {
                current + channelId
            }
        }
    }

    fun selectAllChannels() {
        val allIds = channels.value.map { it.id }.toSet()
        _selectedChannelIds.value = allIds
    }

    fun deselectAllChannels() {
        _selectedChannelIds.value = emptySet()
    }

    fun onAnnouncementTextChange(text: String) {
        _uiState.update { it.copy(announcementText = text) }
    }

    /**
     * Kullanıcı web üzerinden bir görsel linki yapıştırdığında çalışır.
     */
    fun onAttachmentUrlChange(url: String) {
        _uiState.update { it.copy(attachmentUrl = url) }
    }

    /**
     * Galeriden yerel bir görsel seçildiğinde byte'ları ve URI adresini kaydeder.
     */
    fun setLocalAttachment(uriString: String?, bytes: ByteArray?) {
        _uiState.update { it.copy(attachmentUriString = uriString, attachmentBytes = bytes) }
    }

    /**
     * Seçilmiş olan görsel ekini (hem url hem de yerel dosya) tamamen temizler.
     */
    fun removeAttachment() {
        _uiState.update { it.copy(attachmentUrl = "", attachmentBytes = null, attachmentUriString = null) }
    }

    // CRUD: Add or update a channel
    fun saveChannel(channel: ChannelEntity) {
        viewModelScope.launch {
            repository.saveChannel(channel)
        }
    }

    // Toggle active switch of channel
    fun toggleChannelEnabled(channel: ChannelEntity) {
        viewModelScope.launch {
            repository.saveChannel(channel.copy(isEnabled = !channel.isEnabled))
        }
    }

    // CRUD: Delete channel
    fun deleteChannel(channel: ChannelEntity) {
        viewModelScope.launch {
            repository.deleteChannel(channel)
        }
    }

    // Clear history logs
    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    // Test specific integration segment before saving
    fun testTelegramConnection(token: String, chatId: String) {
        _uiState.update { it.copy(isTesting = true, testResult = null) }
        viewModelScope.launch {
            val res = repository.testTelegram(token, chatId)
            _uiState.update { state ->
                state.copy(
                    isTesting = false,
                    testResult = when (res) {
                        is ServiceResult.Success -> "Telegram Test Başarılı!"
                        is ServiceResult.Failure -> "Hata: ${res.error}"
                    }
                )
            }
        }
    }

    fun testDiscordConnection(webhookUrl: String) {
        _uiState.update { it.copy(isTesting = true, testResult = null) }
        viewModelScope.launch {
            val res = repository.testDiscord(webhookUrl)
            _uiState.update { state ->
                state.copy(
                    isTesting = false,
                    testResult = when (res) {
                        is ServiceResult.Success -> "Discord Test Başarılı!"
                        is ServiceResult.Failure -> "Hata: ${res.error}"
                    }
                )
            }
        }
    }

    fun testSlackConnection(webhookUrl: String) {
        _uiState.update { it.copy(isTesting = true, testResult = null) }
        viewModelScope.launch {
            val res = repository.testSlack(webhookUrl)
            _uiState.update { state ->
                state.copy(
                    isTesting = false,
                    testResult = when (res) {
                        is ServiceResult.Success -> "Slack Test Başarılı!"
                        is ServiceResult.Failure -> "Hata: ${res.error}"
                    }
                )
            }
        }
    }

    fun clearTestResult() {
        _uiState.update { it.copy(testResult = null) }
    }

    /**
     * Seçilen kanallara duyuru gönderim işlemini tetikler.
     * Gövde metniyle beraber varsa eklenmiş görseli (link veya yerel dosya) de gönderir.
     */
    fun broadcastAnnouncement() {
        val text = _uiState.value.announcementText
        if (text.isBlank()) return

        val targets = channels.value.filter { it.id in _selectedChannelIds.value }
        if (targets.isEmpty()) {
            _uiState.update {
                it.copy(
                    broadcastSuccess = false,
                    broadcastResultMessage = "Gönderilecek hiçbir kanal seçilmedi! Lütfen en az bir adet aktif kanal kutucuğunu işaretleyin."
                )
            }
            return
        }

        val photoUrl = _uiState.value.attachmentUrl.ifBlank { null }
        val photoBytes = _uiState.value.attachmentBytes

        _uiState.update { it.copy(isBroadcasting = true, broadcastSuccess = null, broadcastResultMessage = "") }

        viewModelScope.launch {
            val log = repository.broadcastAnnouncement(
                message = text, 
                selectedChannels = targets,
                photoUrl = photoUrl,
                photoBytes = photoBytes
            )
            
            val isAllSuccessful = !log.resultSummary.contains("Başarısız")
            
            _uiState.update {
                it.copy(
                    isBroadcasting = false,
                    broadcastSuccess = isAllSuccessful,
                    broadcastResultMessage = log.resultSummary,
                    // Eğer tamamen başarılıysa girdileri sıfırla, hata varsa kullanıcının düzenlemesi için koru
                    announcementText = if (isAllSuccessful) "" else text,
                    attachmentUrl = if (isAllSuccessful) "" else it.attachmentUrl,
                    attachmentBytes = if (isAllSuccessful) null else it.attachmentBytes,
                    attachmentUriString = if (isAllSuccessful) null else it.attachmentUriString
                )
            }
        }
    }

    fun dismissBroadcastResult() {
        _uiState.update { it.copy(broadcastSuccess = null, broadcastResultMessage = "") }
    }
}

class AnnouncementViewModelFactory(private val repository: AnnouncementRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AnnouncementViewModel::class.java)) {
            return AnnouncementViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
