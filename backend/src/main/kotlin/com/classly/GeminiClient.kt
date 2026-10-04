package com.classly

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

const val GEMINI_MODEL = "gemini-3.6-flash"

@Serializable
data class GeminiPart(val text: String? = null)

@Serializable
data class GeminiContent(val parts: List<GeminiPart> = emptyList())

@Serializable
data class GeminiRequest(val contents: List<GeminiContent>)

@Serializable
data class GeminiCandidate(val content: GeminiContent? = null)

@Serializable
data class GeminiResponse(val candidates: List<GeminiCandidate>? = null)

interface GeminiClient {
    /** Sends [prompt] to Gemini and returns the model's text reply. */
    suspend fun generate(prompt: String): String
}

class RestGeminiClient(private val httpClient: HttpClient, private val apiKey: String) : GeminiClient {
    override suspend fun generate(prompt: String): String {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("GEMINI_API_KEY environment variable is not configured.")
        }

        val response: HttpResponse = httpClient.post("https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent") {
            url { parameters.append("key", apiKey) }
            contentType(ContentType.Application.Json)
            setBody(GeminiRequest(listOf(GeminiContent(listOf(GeminiPart(prompt))))))
        }

        if (response.status != HttpStatusCode.OK) {
            throw Exception("Gemini error (${response.status.value}): ${response.bodyAsText()}")
        }

        return response.body<GeminiResponse>()
            .candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
            ?: throw Exception("Gemini returned no text.")
    }
}
