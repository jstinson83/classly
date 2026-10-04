package com.classly

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportPhotoTest {
    private fun fake(reply: String) = object : GeminiClient {
        override suspend fun generate(prompt: String) = ""
        override suspend fun generateJsonFromImage(prompt: String, mimeType: String, imageBase64: String) = reply
    }

    @Test
    fun returnsParsedEventsAndDropsInvalidOnes() = testApplication {
        application {
            module(gemini = fake("""[{"title":"Math","weekday":1,"time":"09:00"},
                {"title":"Essay due","date":"2026-10-09"},{"title":"","date":"2026-10-09"},{"title":"No when"}]"""))
        }
        val response = client.post("/api/import-photo") {
            contentType(ContentType.Application.Json)
            setBody("""{"image":"AAAA","mimeType":"image/jpeg","today":"2026-10-04"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Math") && body.contains("Essay due"))
        assertTrue(!body.contains("No when"))
    }

    @Test
    fun acceptsFencedJson() = testApplication {
        application { module(gemini = fake("```json\n[{\"title\":\"Art\",\"weekday\":2}]\n```")) }
        val response = client.post("/api/import-photo") {
            contentType(ContentType.Application.Json)
            setBody("""{"image":"AAAA"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Art"))
    }

    @Test
    fun badGatewayOnGeminiFailure() = testApplication {
        application { module(gemini = object : GeminiClient {
            override suspend fun generate(prompt: String) = ""
            override suspend fun generateJsonFromImage(prompt: String, mimeType: String, imageBase64: String): String = throw Exception("boom")
        }) }
        val response = client.post("/api/import-photo") {
            contentType(ContentType.Application.Json)
            setBody("""{"image":"AAAA"}""")
        }
        assertEquals(HttpStatusCode.BadGateway, response.status)
    }
}
