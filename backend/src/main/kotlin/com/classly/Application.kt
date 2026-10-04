package com.classly

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

private val geminiHttpClient: HttpClient by lazy {
    HttpClient(CIO) {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 120_000
        }
        engine {
            requestTimeout = 120_000
        }
    }
}

@Serializable
data class HelloResponse(val reply: String)

@Serializable
data class ErrorResponse(val error: String)

@Serializable
data class ImportPhotoRequest(val image: String, val mimeType: String = "image/jpeg", val today: String = "")

/** One item pulled from a photo. Either [date] (YYYY-MM-DD) or [weekday] (0=Sunday..6=Saturday, repeats weekly) is set. */
@Serializable
data class ImportedEvent(
    val title: String,
    val date: String? = null,
    val weekday: Int? = null,
    val time: String = "",
)

@Serializable
data class ImportPhotoResponse(val events: List<ImportedEvent>)

internal fun importPrompt(today: String) = """
You are reading a photo of a student's class schedule (timetable) and/or school agenda/planner.
Today's date is ${today.ifBlank { "unknown" }}.
Extract every item and return a JSON array. Each element has:
- "title": short text, e.g. "Math", "Essay due: English", "Science test"
- "date": "YYYY-MM-DD" if the item is on a specific date (assignments, tests, due dates, events), else null.
  Infer the year from today's date when it is not written.
- "weekday": 0-6 (0=Sunday) if the item repeats every week (a class in a weekly timetable) and has no specific date, else null.
- "time": start time as "HH:MM" in 24-hour format, or "" if there is none.
Set exactly one of "date" or "weekday". Return only the JSON array; return [] if nothing is readable.
""".trimIndent()

internal fun parseImportedEvents(text: String, json: Json): List<ImportedEvent> {
    val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    return json.decodeFromString<List<ImportedEvent>>(cleaned)
        .filter { it.title.isNotBlank() && (it.date != null || it.weekday in 0..6) }
}

fun Application.module(
    gemini: GeminiClient = RestGeminiClient(geminiHttpClient, System.getenv("GEMINI_API_KEY") ?: ""),
) {
    install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }

    routing {
        post("/api/hello-gemini") {
            try {
                val reply = gemini.generate("Say hello to a student using the Classly school organizer app, in one short friendly sentence.")
                call.respond(HelloResponse(reply))
            } catch (e: Exception) {
                call.application.environment.log.error("hello-gemini failed", e)
                call.respond(HttpStatusCode.BadGateway, ErrorResponse(e.message ?: "Gemini call failed"))
            }
        }
        post("/api/import-photo") {
            try {
                val req = call.receive<ImportPhotoRequest>()
                val text = gemini.generateJsonFromImage(importPrompt(req.today), req.mimeType, req.image)
                call.respond(ImportPhotoResponse(parseImportedEvents(text, Json { ignoreUnknownKeys = true })))
            } catch (e: Exception) {
                call.application.environment.log.error("import-photo failed", e)
                call.respond(HttpStatusCode.BadGateway, ErrorResponse(e.message ?: "Photo import failed"))
            }
        }
        cycleRoutes(gemini)
        staticResources("/", "static", index = "index.html")
    }
}
