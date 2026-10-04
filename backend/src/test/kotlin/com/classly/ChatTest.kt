package com.classly

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatTest {
    private fun fake(reply: String, onPrompt: (String) -> Unit = {}) = object : GeminiClient {
        override suspend fun generate(prompt: String) = ""
        override suspend fun generateJson(prompt: String): String { onPrompt(prompt); return reply }
    }

    private suspend fun ApplicationTestBuilder.chat(body: String) = signedInClient().post("/api/chat") {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    @Test
    fun returnsReplyAndValidBlocksOnly() = testApplication {
        application {
            testModule(gemini = fake("""{"reply":"Added two sessions.","events":[
                {"title":"Study Science (1h)","date":"2026-10-08","time":"16:00"},
                {"title":"Essay (45m)","date":"2026-10-09","time":"soon"},
                {"title":"Bad date","date":"Friday"},{"title":"","date":"2026-10-09"}]}"""))
        }
        val r = chat("""{"messages":[{"role":"user","text":"Science test Friday"}],"today":"2026-10-04"}""")
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("Added two sessions.") && body.contains("Study Science") && body.contains("Essay (45m)"))
        assertTrue(!body.contains("Bad date") && !body.contains("soon"))
    }

    @Test
    fun plainTextReplyIsStillShown() = testApplication {
        application { testModule(gemini = fake("When is it due?")) }
        val r = chat("""{"messages":[{"role":"user","text":"I have an essay"}]}""")
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(r.bodyAsText().contains("When is it due?"))
    }

    @Test
    fun promptIncludesCalendarAndConversation() = testApplication {
        var prompt = ""
        application { testModule(gemini = fake("""{"reply":"ok"}""") { prompt = it }) }
        chat("""{"messages":[{"role":"user","text":"Math quiz Tuesday"}],"today":"2026-10-04",
            "calendar":[{"date":"2026-10-06","time":"","title":"PD Day"}]}""")
        assertTrue(prompt.contains("2026-10-06 all day: PD Day") && prompt.contains("Student: Math quiz Tuesday"))
    }

    @Test
    fun emptyConversationIsBadRequestAndGeminiFailureIsBadGateway() = testApplication {
        application { testModule(gemini = object : GeminiClient {
            override suspend fun generate(prompt: String) = ""
            override suspend fun generateJson(prompt: String): String = throw Exception("boom")
        }) }
        assertEquals(HttpStatusCode.BadRequest, chat("""{"messages":[]}""").status)
        assertEquals(HttpStatusCode.BadGateway, chat("""{"messages":[{"role":"user","text":"hi"}]}""").status)
    }
}
