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
    fun homePageServesHelloButton() = testApplication {
        application { module(gemini = object : GeminiClient {
            override suspend fun generate(prompt: String) = ""
        }) }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Hello Gemini"))
    }
}
