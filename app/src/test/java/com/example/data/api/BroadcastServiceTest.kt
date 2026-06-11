package com.example.data.api

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BroadcastServiceTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var service: BroadcastService

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        service = BroadcastService(
            telegramBaseUrl = mockWebServer.url("/").toString().trimEnd('/')
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // --- escapeForJson ---

    @Test
    fun `escapeForJson escapes backslashes`() {
        assertEquals("hello\\\\world", service.escapeForJson("hello\\world"))
    }

    @Test
    fun `escapeForJson escapes double quotes`() {
        assertEquals("say \\\"hi\\\"", service.escapeForJson("say \"hi\""))
    }

    @Test
    fun `escapeForJson escapes newlines and tabs`() {
        assertEquals("line1\\nline2\\ttab", service.escapeForJson("line1\nline2\ttab"))
    }

    @Test
    fun `escapeForJson escapes carriage return`() {
        assertEquals("a\\rb", service.escapeForJson("a\rb"))
    }

    @Test
    fun `escapeForJson handles combined special characters`() {
        val input = "He said \"hello\\world\"\nNew\tline\r"
        val expected = "He said \\\"hello\\\\world\\\"\\nNew\\tline\\r"
        assertEquals(expected, service.escapeForJson(input))
    }

    @Test
    fun `escapeForJson leaves plain text unchanged`() {
        assertEquals("hello world 123", service.escapeForJson("hello world 123"))
    }

    // --- sendTelegram validation ---

    @Test
    fun `sendTelegram returns failure when token is blank`() = runBlocking {
        val result = service.sendTelegram(token = "", chatId = "123", message = "test")
        assertTrue(result is ServiceResult.Failure)
        assertTrue((result as ServiceResult.Failure).error.contains("Eksik Telegram parametreleri"))
    }

    @Test
    fun `sendTelegram returns failure when chatId is blank`() = runBlocking {
        val result = service.sendTelegram(token = "tok123", chatId = "  ", message = "test")
        assertTrue(result is ServiceResult.Failure)
        assertTrue((result as ServiceResult.Failure).error.contains("Eksik Telegram parametreleri"))
    }

    @Test
    fun `sendTelegram returns failure when both token and chatId are blank`() = runBlocking {
        val result = service.sendTelegram(token = "", chatId = "", message = "test")
        assertTrue(result is ServiceResult.Failure)
    }

    // --- sendTelegram text-only via MockWebServer ---

    @Test
    fun `sendTelegram text-only returns success on 200`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))

        val result = service.sendTelegram(token = "testtoken", chatId = "12345", message = "Hello")

        assertTrue(result is ServiceResult.Success)
        assertTrue((result as ServiceResult.Success).info.contains("Telegram"))

        val request = mockWebServer.takeRequest()
        assertEquals("/bottesttoken/sendMessage", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"chat_id\": \"12345\""))
        assertTrue(body.contains("\"text\": \"Hello\""))
    }

    @Test
    fun `sendTelegram text-only returns failure on 400`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(400).setBody("Bad Request"))

        val result = service.sendTelegram(token = "tok", chatId = "123", message = "msg")

        assertTrue(result is ServiceResult.Failure)
        assertTrue((result as ServiceResult.Failure).error.contains("Telegram API Hatası"))
        assertTrue(result.error.contains("400"))
    }

    // --- sendTelegram with photo URL ---

    @Test
    fun `sendTelegram with photoUrl sends sendPhoto endpoint`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))

        val result = service.sendTelegram(
            token = "mytoken",
            chatId = "999",
            message = "Photo caption",
            photoUrl = "https://example.com/img.jpg"
        )

        assertTrue(result is ServiceResult.Success)
        val request = mockWebServer.takeRequest()
        assertEquals("/botmytoken/sendPhoto", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"photo\": \"https://example.com/img.jpg\""))
        assertTrue(body.contains("\"caption\": \"Photo caption\""))
    }

    // --- sendTelegram with photo bytes (multipart) ---

    @Test
    fun `sendTelegram with photoBytes sends multipart`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))

        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val result = service.sendTelegram(
            token = "tok",
            chatId = "42",
            message = "img msg",
            photoBytes = bytes
        )

        assertTrue(result is ServiceResult.Success)
        val request = mockWebServer.takeRequest()
        assertEquals("/bottok/sendPhoto", request.path)
        assertTrue(request.getHeader("Content-Type")!!.contains("multipart/form-data"))
    }

    // --- sendSlack validation ---

    @Test
    fun `sendSlack returns failure when webhookUrl is blank`() = runBlocking {
        val result = service.sendSlack(webhookUrl = "", message = "test")
        assertTrue(result is ServiceResult.Failure)
        assertTrue((result as ServiceResult.Failure).error.contains("Geçersiz Slack Webhook URL"))
    }

    @Test
    fun `sendSlack returns failure when webhookUrl does not start with http`() = runBlocking {
        val result = service.sendSlack(webhookUrl = "ftp://invalid", message = "test")
        assertTrue(result is ServiceResult.Failure)
        assertTrue((result as ServiceResult.Failure).error.contains("Geçersiz Slack Webhook URL"))
    }

    // --- sendSlack via MockWebServer ---

    @Test
    fun `sendSlack text-only returns success on 200`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val url = mockWebServer.url("/slack-webhook").toString()
        val result = service.sendSlack(webhookUrl = url, message = "Hello Slack")

        assertTrue(result is ServiceResult.Success)
        assertTrue((result as ServiceResult.Success).info.contains("Slack"))

        val request = mockWebServer.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"text\": \"Hello Slack\""))
    }

    @Test
    fun `sendSlack with photoUrl includes Block Kit image block`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val url = mockWebServer.url("/slack-hook").toString()
        val result = service.sendSlack(
            webhookUrl = url,
            message = "Announcement",
            photoUrl = "https://example.com/pic.png"
        )

        assertTrue(result is ServiceResult.Success)
        val request = mockWebServer.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"type\": \"image\""))
        assertTrue(body.contains("https://example.com/pic.png"))
        assertTrue(body.contains("\"type\": \"section\""))
    }

    @Test
    fun `sendSlack returns failure on 500`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("internal error"))

        val url = mockWebServer.url("/hook").toString()
        val result = service.sendSlack(webhookUrl = url, message = "msg")

        assertTrue(result is ServiceResult.Failure)
        assertTrue((result as ServiceResult.Failure).error.contains("Slack API Hatası"))
        assertTrue(result.error.contains("500"))
    }

    @Test
    fun `sendSlack with photoBytes but no photoUrl sends text-only`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val url = mockWebServer.url("/hook").toString()
        val result = service.sendSlack(
            webhookUrl = url,
            message = "No image block",
            photoBytes = byteArrayOf(1, 2, 3)
        )

        assertTrue(result is ServiceResult.Success)
        val request = mockWebServer.takeRequest()
        val body = request.body.readUtf8()
        assertFalse(body.contains("\"type\": \"image\""))
        assertTrue(body.contains("\"text\": \"No image block\""))
    }

    // --- escapeForJson integration with payloads ---

    @Test
    fun `sendSlack properly escapes special characters in message`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val url = mockWebServer.url("/hook").toString()
        service.sendSlack(webhookUrl = url, message = "Line1\nLine2\t\"quoted\"")

        val request = mockWebServer.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(body.contains("Line1\\nLine2\\t\\\"quoted\\\""))
    }
}
