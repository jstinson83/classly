package com.classly

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CycleImportTest {
    private fun fake(reply: String) = object : GeminiClient {
        override suspend fun generate(prompt: String) = ""
        override suspend fun generateJsonFromImage(prompt: String, mimeType: String, imageBase64: String) = reply
    }

    private suspend fun ApplicationTestBuilder.post(path: String) = signedInClient().post(path) {
        contentType(ContentType.Application.Json)
        setBody("""{"image":"AAAA","today":"2026-10-04"}""")
    }

    @Test
    fun calendarKeepsCycleAndNoSchoolDaysAndDropsBadDates() = testApplication {
        application {
            testModule(gemini = fake("""[{"date":"2026-10-05","day":3},
                {"date":"2026-10-12","day":null,"note":"PD Day"},
                {"date":"Oct 6","day":4},{"date":"2026-10-07","day":0}]"""))
        }
        val response = post("/api/import-cycle-calendar")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("2026-10-05") && body.contains("PD Day"))
        assertTrue(!body.contains("Oct 6") && !body.contains("2026-10-07"))
    }

    @Test
    fun scheduleKeepsClassesAndDropsInvalidOnes() = testApplication {
        application {
            testModule(gemini = fake("""```json
                [{"day":1,"title":"Math","time":"09:00","period":1},{"day":6,"title":"Art","period":2},
                {"day":2,"title":""},{"day":0,"title":"Ghost"}]
                ```"""))
        }
        val response = post("/api/import-cycle-schedule")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Math") && body.contains("Art"))
        assertTrue(!body.contains("Ghost"))
    }

    @Test
    fun badGatewayOnGeminiFailure() = testApplication {
        application { testModule(gemini = object : GeminiClient {
            override suspend fun generate(prompt: String) = ""
            override suspend fun generateJsonFromImage(prompt: String, mimeType: String, imageBase64: String): String = throw Exception("boom")
        }) }
        assertEquals(HttpStatusCode.BadGateway, post("/api/import-cycle-calendar").status)
        assertEquals(HttpStatusCode.BadGateway, post("/api/import-cycle-schedule").status)
    }
}
