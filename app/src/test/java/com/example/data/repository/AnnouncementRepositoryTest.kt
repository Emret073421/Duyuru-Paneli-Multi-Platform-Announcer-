package com.example.data.repository

import com.example.data.api.BroadcastService
import com.example.data.api.ServiceResult
import com.example.data.database.AnnouncementDao
import com.example.data.database.AnnouncementEntity
import com.example.data.database.ChannelDao
import com.example.data.database.ChannelEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AnnouncementRepositoryTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var broadcastService: BroadcastService
    private lateinit var fakeChannelDao: FakeChannelDao
    private lateinit var fakeAnnouncementDao: FakeAnnouncementDao
    private lateinit var repository: AnnouncementRepository

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        broadcastService = BroadcastService(
            telegramBaseUrl = mockWebServer.url("/").toString().trimEnd('/')
        )
        fakeChannelDao = FakeChannelDao()
        fakeAnnouncementDao = FakeAnnouncementDao()
        repository = AnnouncementRepository(fakeChannelDao, fakeAnnouncementDao, broadcastService)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // --- broadcastAnnouncement ---

    @Test
    fun `broadcastAnnouncement with empty channels logs no channel message`() = runBlocking {
        val result = repository.broadcastAnnouncement("Hello", emptyList())

        assertEquals("Hello", result.message)
        assertTrue(result.resultSummary.contains("Hiçbir hedef kanal seçilmedi"))
        assertEquals(1, fakeAnnouncementDao.insertedAnnouncements.size)
    }

    @Test
    fun `broadcastAnnouncement with Telegram channel sends and logs`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))

        val channel = ChannelEntity(
            id = 1,
            name = "MyTelegram",
            platformType = "Telegram",
            telegramToken = "tok123",
            telegramChatId = "456"
        )

        val result = repository.broadcastAnnouncement("Test msg", listOf(channel))

        assertTrue(result.resultSummary.contains("MyTelegram"))
        assertTrue(result.resultSummary.contains("Başarılı"))
        assertEquals(1, fakeAnnouncementDao.insertedAnnouncements.size)
    }

    @Test
    fun `broadcastAnnouncement with Slack channel sends and logs`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val webhookUrl = mockWebServer.url("/slack").toString()
        val channel = ChannelEntity(
            id = 2,
            name = "MySlack",
            platformType = "Slack",
            webhookUrl = webhookUrl
        )

        val result = repository.broadcastAnnouncement("Slack msg", listOf(channel))

        assertTrue(result.resultSummary.contains("MySlack"))
        assertTrue(result.resultSummary.contains("Başarılı"))
    }

    @Test
    fun `broadcastAnnouncement with failed Telegram reports failure`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(403).setBody("forbidden"))

        val channel = ChannelEntity(
            id = 1,
            name = "FailTG",
            platformType = "Telegram",
            telegramToken = "badtoken",
            telegramChatId = "123"
        )

        val result = repository.broadcastAnnouncement("msg", listOf(channel))

        assertTrue(result.resultSummary.contains("Başarısız"))
        assertTrue(result.resultSummary.contains("FailTG"))
    }

    @Test
    fun `broadcastAnnouncement with unknown platform reports failure`() = runBlocking {
        val channel = ChannelEntity(
            id = 3,
            name = "Unknown",
            platformType = "Discord"
        )

        val result = repository.broadcastAnnouncement("msg", listOf(channel))

        assertTrue(result.resultSummary.contains("Başarısız"))
        assertTrue(result.resultSummary.contains("Bilinmeyen platform"))
    }

    @Test
    fun `broadcastAnnouncement with multiple channels logs all`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val tgChannel = ChannelEntity(
            id = 1, name = "TG", platformType = "Telegram",
            telegramToken = "t1", telegramChatId = "c1"
        )
        val slackChannel = ChannelEntity(
            id = 2, name = "SL", platformType = "Slack",
            webhookUrl = mockWebServer.url("/sl").toString()
        )

        val result = repository.broadcastAnnouncement("multi", listOf(tgChannel, slackChannel))

        assertTrue(result.resultSummary.contains("TG"))
        assertTrue(result.resultSummary.contains("SL"))
        assertEquals(2, result.resultSummary.lines().size)
    }

    @Test
    fun `broadcastAnnouncement with photoUrl appends URL to message display`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))

        val channel = ChannelEntity(
            id = 1, name = "Ch", platformType = "Telegram",
            telegramToken = "t", telegramChatId = "c"
        )

        val result = repository.broadcastAnnouncement(
            "msg", listOf(channel), photoUrl = "https://img.com/a.jpg"
        )

        assertTrue(result.message.contains("[Görsel URL: https://img.com/a.jpg]"))
    }

    @Test
    fun `broadcastAnnouncement with photoBytes appends local image text`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))

        val channel = ChannelEntity(
            id = 1, name = "Ch", platformType = "Telegram",
            telegramToken = "t", telegramChatId = "c"
        )

        val result = repository.broadcastAnnouncement(
            "msg", listOf(channel), photoBytes = byteArrayOf(1, 2)
        )

        assertTrue(result.message.contains("[Yerel Görsel İliştirildi]"))
    }

    // --- saveChannel / deleteChannel / clearHistory ---

    @Test
    fun `saveChannel delegates to dao`() = runBlocking {
        val channel = ChannelEntity(id = 1, name = "Ch", platformType = "Telegram")
        repository.saveChannel(channel)
        assertEquals(channel, fakeChannelDao.lastSaved)
    }

    @Test
    fun `deleteChannel delegates to dao`() = runBlocking {
        val channel = ChannelEntity(id = 2, name = "Del", platformType = "Slack")
        repository.deleteChannel(channel)
        assertEquals(channel, fakeChannelDao.lastDeleted)
    }

    @Test
    fun `clearHistory delegates to dao`() = runBlocking {
        repository.clearHistory()
        assertTrue(fakeAnnouncementDao.cleared)
    }

    // --- testTelegram / testSlack ---

    @Test
    fun `testTelegram sends test message`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))

        val result = repository.testTelegram("token1", "chat1")

        assertTrue(result is ServiceResult.Success)
        val request = mockWebServer.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(body.contains("OmniAnnounce Test"))
    }

    @Test
    fun `testSlack sends test message`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val url = mockWebServer.url("/test-slack").toString()
        val result = repository.testSlack(url)

        assertTrue(result is ServiceResult.Success)
    }
}

// --- Fake implementations ---

private class FakeChannelDao : ChannelDao {
    var lastSaved: ChannelEntity? = null
    var lastDeleted: ChannelEntity? = null

    override fun getAllChannels(): Flow<List<ChannelEntity>> = flowOf(emptyList())

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
