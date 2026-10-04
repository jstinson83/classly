package com.classly

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HelloGeminiTest {
    @Test
    fun helloGeminiReturnsReply() = testApplication {
        application { module(gemini = object : GeminiClient {
            override suspend fun generate(prompt: String) = "Hi there!"
        }) }
        val response = client.post("/api/hello-gemini")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"reply":"Hi there!"}""", response.bodyAsText())
    }

    @Test
    fun helloGeminiReturnsBadGatewayOnFailure() = testApplication {
        application { module(gemini = object : GeminiClient {
            override suspend fun generate(prompt: String): String = throw Exception("boom")
        }) }
        val response = client.post("/api/hello-gemini")
        assertEquals(HttpStatusCode.BadGateway, response.status)
        assertTrue(response.bodyAsText().contains("boom"))
    }

    @Test
    fun homePageLinksToCalendar() = testApplication {
        application { module(gemini = object : GeminiClient {
            override suspend fun generate(prompt: String) = ""
        }) }
        val home = client.get("/")
        assertEquals(HttpStatusCode.OK, home.status)
        assertTrue(home.bodyAsText().contains("href=\"/calendar.html\""))
        assertTrue(home.bodyAsText().contains("href=\"/schedule.html\""))
        assertTrue(home.bodyAsText().contains("href=\"/chat.html\""))
        assertTrue(client.get("/chat.html").bodyAsText().contains("AI chat"))
        assertTrue(client.get("/schedule.html").bodyAsText().contains("Take photo of schedule"))
        val response = client.get("/calendar.html")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("id=\"grid\""))
        assertTrue(body.contains("Hello Gemini"))
    }
}
