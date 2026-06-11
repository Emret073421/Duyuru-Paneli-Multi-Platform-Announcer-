package com.example.ui.viewmodel

import com.example.data.api.BroadcastService
import com.example.data.api.ServiceResult
import com.example.data.database.AnnouncementDao
import com.example.data.database.AnnouncementEntity
import com.example.data.database.ChannelDao
import com.example.data.database.ChannelEntity
import com.example.data.repository.AnnouncementRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnnouncementViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeChannelDao: FakeChannelDao
    private lateinit var fakeAnnouncementDao: FakeAnnouncementDao
    private lateinit var fakeBroadcastService: FakeBroadcastService
    private lateinit var repository: AnnouncementRepository
    private lateinit var viewModel: AnnouncementViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeChannelDao = FakeChannelDao()
        fakeAnnouncementDao = FakeAnnouncementDao()
        fakeBroadcastService = FakeBroadcastService()
        repository = AnnouncementRepository(fakeChannelDao, fakeAnnouncementDao, fakeBroadcastService)
        viewModel = AnnouncementViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Initial state ---

    @Test
    fun `initial uiState has empty text and no broadcasting`() = runTest {
        val state = viewModel.uiState.first()
        assertEquals("", state.announcementText)
        assertFalse(state.isBroadcasting)
        assertNull(state.broadcastSuccess)
        assertEquals("", state.broadcastResultMessage)
        assertEquals("", state.attachmentUrl)
        assertNull(state.attachmentBytes)
        assertNull(state.attachmentUriString)
        assertFalse(state.isTesting)
        assertNull(state.testResult)
    }

    // --- onAnnouncementTextChange ---

    @Test
    fun `onAnnouncementTextChange updates text in uiState`() = runTest {
        viewModel.onAnnouncementTextChange("Hello World")
        assertEquals("Hello World", viewModel.uiState.first().announcementText)
    }

    // --- onAttachmentUrlChange ---

    @Test
    fun `onAttachmentUrlChange updates attachmentUrl in uiState`() = runTest {
        viewModel.onAttachmentUrlChange("https://example.com/img.png")
        assertEquals("https://example.com/img.png", viewModel.uiState.first().attachmentUrl)
    }

    // --- setLocalAttachment ---

    @Test
    fun `setLocalAttachment updates bytes and uri`() = runTest {
        val bytes = byteArrayOf(1, 2, 3)
        viewModel.setLocalAttachment("content://photo/1", bytes)

        val state = viewModel.uiState.first()
        assertEquals("content://photo/1", state.attachmentUriString)
        assertArrayEquals(bytes, state.attachmentBytes)
    }

    // --- removeAttachment ---

    @Test
    fun `removeAttachment clears all attachment fields`() = runTest {
        viewModel.onAttachmentUrlChange("https://url.com")
        viewModel.setLocalAttachment("uri", byteArrayOf(1))
        viewModel.removeAttachment()

        val state = viewModel.uiState.first()
        assertEquals("", state.attachmentUrl)
        assertNull(state.attachmentBytes)
        assertNull(state.attachmentUriString)
    }

    // --- toggleChannelSelection ---

    @Test
    fun `toggleChannelSelection adds and removes channel`() = runTest {
        advanceUntilIdle()

        viewModel.toggleChannelSelection(5)
        assertEquals(setOf(5), viewModel.selectedChannelIds.first())

        viewModel.toggleChannelSelection(5)
        assertEquals(emptySet<Int>(), viewModel.selectedChannelIds.first())
    }

    @Test
    fun `toggleChannelSelection adds multiple channels`() = runTest {
        advanceUntilIdle()

        viewModel.toggleChannelSelection(1)
        viewModel.toggleChannelSelection(2)
        viewModel.toggleChannelSelection(3)

        assertEquals(setOf(1, 2, 3), viewModel.selectedChannelIds.first())
    }

    // --- selectAllChannels / deselectAllChannels ---

    @Test
    fun `selectAllChannels selects all known channels`() = runTest {
        fakeChannelDao.channelsFlow.value = listOf(
            ChannelEntity(id = 10, name = "A", platformType = "Telegram"),
            ChannelEntity(id = 20, name = "B", platformType = "Slack")
        )
        val job = backgroundScope.launch { viewModel.channels.collect {} }
        advanceUntilIdle()

        viewModel.selectAllChannels()
        assertEquals(setOf(10, 20), viewModel.selectedChannelIds.first())
        job.cancel()
    }

    @Test
    fun `deselectAllChannels clears selection`() = runTest {
        advanceUntilIdle()

        viewModel.toggleChannelSelection(1)
        viewModel.deselectAllChannels()
        assertEquals(emptySet<Int>(), viewModel.selectedChannelIds.first())
    }

    // --- broadcastAnnouncement ---

    @Test
    fun `broadcastAnnouncement with blank text does nothing`() = runTest {
        viewModel.onAnnouncementTextChange("   ")
        viewModel.broadcastAnnouncement()
        advanceUntilIdle()

        assertNull(viewModel.uiState.first().broadcastSuccess)
    }

    @Test
    fun `broadcastAnnouncement with no selected channels shows error`() = runTest {
        fakeChannelDao.channelsFlow.value = listOf(
            ChannelEntity(id = 1, name = "Ch", platformType = "Telegram")
        )
        advanceUntilIdle()

        viewModel.deselectAllChannels()
        viewModel.onAnnouncementTextChange("Hello")
        viewModel.broadcastAnnouncement()
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertEquals(false, state.broadcastSuccess)
        assertTrue(state.broadcastResultMessage.contains("kanal seçilmedi"))
    }

    @Test
    fun `broadcastAnnouncement success clears text`() = runTest {
        fakeChannelDao.channelsFlow.value = listOf(
            ChannelEntity(id = 1, name = "Ch", platformType = "Telegram",
                telegramToken = "t", telegramChatId = "c")
        )
        val job = backgroundScope.launch { viewModel.channels.collect {} }
        advanceUntilIdle()

        viewModel.selectAllChannels()
        viewModel.onAnnouncementTextChange("Broadcast me")
        fakeBroadcastService.telegramResult = ServiceResult.Success("OK")
        viewModel.broadcastAnnouncement()
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertEquals(true, state.broadcastSuccess)
        assertEquals("", state.announcementText)
        job.cancel()
    }

    @Test
    fun `broadcastAnnouncement failure keeps text`() = runTest {
        fakeChannelDao.channelsFlow.value = listOf(
            ChannelEntity(id = 1, name = "Ch", platformType = "Telegram",
                telegramToken = "t", telegramChatId = "c")
        )
        val job = backgroundScope.launch { viewModel.channels.collect {} }
        advanceUntilIdle()

        viewModel.selectAllChannels()
        viewModel.onAnnouncementTextChange("Keep this")
        fakeBroadcastService.telegramResult = ServiceResult.Failure("Başarısız: network error")
        viewModel.broadcastAnnouncement()
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertEquals(false, state.broadcastSuccess)
        assertEquals("Keep this", state.announcementText)
        job.cancel()
    }

    // --- dismissBroadcastResult ---

    @Test
    fun `dismissBroadcastResult clears result`() = runTest {
        fakeChannelDao.channelsFlow.value = listOf(
            ChannelEntity(id = 1, name = "Ch", platformType = "Telegram",
                telegramToken = "t", telegramChatId = "c")
        )
        val job = backgroundScope.launch { viewModel.channels.collect {} }
        advanceUntilIdle()

        viewModel.selectAllChannels()
        viewModel.onAnnouncementTextChange("msg")
        fakeBroadcastService.telegramResult = ServiceResult.Success("OK")
        viewModel.broadcastAnnouncement()
        advanceUntilIdle()

        viewModel.dismissBroadcastResult()
        val state = viewModel.uiState.first()
        assertNull(state.broadcastSuccess)
        assertEquals("", state.broadcastResultMessage)
        job.cancel()
    }

    // --- clearTestResult ---

    @Test
    fun `clearTestResult sets testResult to null`() = runTest {
        viewModel.clearTestResult()
        assertNull(viewModel.uiState.first().testResult)
    }

    // --- saveChannel / deleteChannel ---

    @Test
    fun `saveChannel delegates to repository`() = runTest {
        val channel = ChannelEntity(id = 0, name = "New", platformType = "Slack", webhookUrl = "http://x")
        viewModel.saveChannel(channel)
        advanceUntilIdle()
        assertEquals(channel, fakeChannelDao.lastSaved)
    }

    @Test
    fun `deleteChannel delegates to repository`() = runTest {
        val channel = ChannelEntity(id = 5, name = "Del", platformType = "Telegram")
        viewModel.deleteChannel(channel)
        advanceUntilIdle()
        assertEquals(channel, fakeChannelDao.lastDeleted)
    }

    // --- toggleChannelEnabled ---

    @Test
    fun `toggleChannelEnabled flips isEnabled and saves`() = runTest {
        val channel = ChannelEntity(id = 1, name = "T", platformType = "Telegram", isEnabled = true)
        viewModel.toggleChannelEnabled(channel)
        advanceUntilIdle()

        assertNotNull(fakeChannelDao.lastSaved)
        assertFalse(fakeChannelDao.lastSaved!!.isEnabled)
    }

    // --- clearHistory ---

    @Test
    fun `clearHistory delegates to repository`() = runTest {
        viewModel.clearHistory()
        advanceUntilIdle()
        assertTrue(fakeAnnouncementDao.cleared)
    }

    // --- auto-select enabled channels on init ---

    @Test
    fun `init auto-selects enabled channels`() = runTest {
        fakeChannelDao.channelsFlow.value = listOf(
            ChannelEntity(id = 1, name = "A", platformType = "Telegram", isEnabled = true),
            ChannelEntity(id = 2, name = "B", platformType = "Slack", isEnabled = false),
            ChannelEntity(id = 3, name = "C", platformType = "Telegram", isEnabled = true)
        )
        val job = backgroundScope.launch { viewModel.channels.collect {} }
        advanceUntilIdle()

        val selected = viewModel.selectedChannelIds.first()
        assertTrue(selected.contains(1))
        assertFalse(selected.contains(2))
        assertTrue(selected.contains(3))
        job.cancel()
    }
}

// --- Fakes ---

private class FakeChannelDao : ChannelDao {
    val channelsFlow = MutableStateFlow<List<ChannelEntity>>(emptyList())
    var lastSaved: ChannelEntity? = null
    var lastDeleted: ChannelEntity? = null

    override fun getAllChannels(): Flow<List<ChannelEntity>> = channelsFlow

    override suspend fun saveChannel(channel: ChannelEntity) {
        lastSaved = channel
    }

    override suspend fun deleteChannel(channel: ChannelEntity) {
        lastDeleted = channel
    }
}

private class FakeAnnouncementDao : AnnouncementDao {
    val insertedAnnouncements = mutableListOf<AnnouncementEntity>()
    var cleared = false

    override fun getAllAnnouncements(): Flow<List<AnnouncementEntity>> = flowOf(emptyList())

    override suspend fun insertAnnouncement(announcement: AnnouncementEntity): Long {
        insertedAnnouncements.add(announcement)
        return insertedAnnouncements.size.toLong()
    }

    override suspend fun clearHistory() {
        cleared = true
    }
}

private class FakeBroadcastService : BroadcastService() {
    var telegramResult: ServiceResult = ServiceResult.Success("Mock Telegram OK")
    var slackResult: ServiceResult = ServiceResult.Success("Mock Slack OK")

    override suspend fun sendTelegram(
        token: String,
        chatId: String,
        message: String,
        photoUrl: String?,
        photoBytes: ByteArray?
    ): ServiceResult = telegramResult

    override suspend fun sendSlack(
        webhookUrl: String,
        message: String,
        photoUrl: String?,
        photoBytes: ByteArray?
    ): ServiceResult = slackResult
}
