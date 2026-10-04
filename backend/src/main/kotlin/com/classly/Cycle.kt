package com.classly

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One school date from a year-calendar photo. [day] is the rotating-cycle day (1..N), or null for a no-school day. */
@Serializable
data class CycleDay(val date: String, val day: Int? = null, val note: String = "")

/** One class from a rotating-day schedule photo: which cycle [day] it runs on, in [period] order. */
@Serializable
data class CycleClass(val day: Int, val title: String, val time: String = "", val period: Int = 0)

@Serializable
data class CycleDaysResponse(val days: List<CycleDay>)

@Serializable
data class CycleClassesResponse(val classes: List<CycleClass>)

private val isoDate = Regex("""\d{4}-\d{2}-\d{2}""")

internal fun cycleCalendarPrompt(today: String) = """
You are reading a photo of a school's year calendar for a school that runs a rotating day cycle
(for example a 6-day cycle: Day 1, Day 2, ... Day 6, repeating on school days only).
Today's date is ${today.ifBlank { "unknown" }}. Infer the year from the photo, or from today's date when it is not written.
Return a JSON array with one element per date that shows a cycle day number or a no-school label:
- "date": "YYYY-MM-DD"
- "day": the cycle day number as an integer (e.g. 3 for "Day 3", "D3" or a plain 3 in the cycle marker), or null for a no-school day
- "note": for a no-school day, its label (e.g. "PD Day", "Holiday", "March Break"); otherwise ""
Skip weekends and dates with no marking. Return only the JSON array; return [] if nothing is readable.
""".trimIndent()

internal fun cycleSchedulePrompt() = """
You are reading a photo of a student's class schedule that follows a rotating day cycle
(for example Day 1 to Day 6, rather than Monday to Friday).
Return a JSON array with one element per class per cycle day:
- "day": the cycle day number as an integer (1 for "Day 1")
- "title": the class name (add the room or teacher only if it is part of the printed class name)
- "time": the class start time as "HH:MM" in 24-hour format if the photo shows times, else ""
- "period": the period number within that day (1 for the first class of the day, 2 for the next, ...)
Include one element for every day the class appears on, even if it repeats. Skip lunch and blank cells.
Return only the JSON array; return [] if nothing is readable.
""".trimIndent()

private fun String.stripFences() = trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

internal fun parseCycleDays(text: String, json: Json): List<CycleDay> =
    json.decodeFromString<List<CycleDay>>(text.stripFences())
        .filter { isoDate.matches(it.date) && (it.day == null || it.day >= 1) }

internal fun parseCycleClasses(text: String, json: Json): List<CycleClass> =
    json.decodeFromString<List<CycleClass>>(text.stripFences())
        .filter { it.title.isNotBlank() && it.day >= 1 }

fun Route.cycleRoutes(gemini: GeminiClient) {
    val json = Json { ignoreUnknownKeys = true }
    post("/api/import-cycle-calendar") {
        try {
            val req = call.receive<ImportPhotoRequest>()
            val text = gemini.generateJsonFromImage(cycleCalendarPrompt(req.today), req.mimeType, req.image)
            call.respond(CycleDaysResponse(parseCycleDays(text, json)))
        } catch (e: Exception) {
            call.application.environment.log.error("import-cycle-calendar failed", e)
            call.respond(HttpStatusCode.BadGateway, ErrorResponse(e.message ?: "Calendar import failed"))
        }
    }
    post("/api/import-cycle-schedule") {
        try {
            val req = call.receive<ImportPhotoRequest>()
            val text = gemini.generateJsonFromImage(cycleSchedulePrompt(), req.mimeType, req.image)
            call.respond(CycleClassesResponse(parseCycleClasses(text, json)))
        } catch (e: Exception) {
            call.application.environment.log.error("import-cycle-schedule failed", e)
            call.respond(HttpStatusCode.BadGateway, ErrorResponse(e.message ?: "Schedule import failed"))
        }
    }
}
