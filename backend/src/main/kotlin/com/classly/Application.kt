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
        staticResources("/", "static", index = "index.html")
    }
}
