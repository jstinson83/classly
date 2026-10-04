package com.classly

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

const val GEMINI_MODEL = "gemini-3.6-flash"

@Serializable
data class GeminiInlineData(val mimeType: String, val data: String)

@Serializable
data class GeminiPart(val text: String? = null, val inlineData: GeminiInlineData? = null)

@Serializable
data class GeminiContent(val parts: List<GeminiPart> = emptyList())

@Serializable
data class GeminiGenerationConfig(val responseMimeType: String)

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null,
)

@Serializable
data class GeminiCandidate(val content: GeminiContent? = null)

@Serializable
data class GeminiResponse(val candidates: List<GeminiCandidate>? = null)

interface GeminiClient {
    /** Sends [prompt] to Gemini and returns the model's text reply. */
    suspend fun generate(prompt: String): String

    /** Sends [prompt] and returns the model's reply, requested as JSON. */
    suspend fun generateJson(prompt: String): String =
        throw UnsupportedOperationException("JSON generation not supported")

    /** Sends [prompt] plus an image (base64 [imageBase64]) and returns the model's reply, requested as JSON. */
    suspend fun generateJsonFromImage(prompt: String, mimeType: String, imageBase64: String): String =
        throw UnsupportedOperationException("Image input not supported")
}

class RestGeminiClient(private val httpClient: HttpClient, private val apiKey: String) : GeminiClient {
    override suspend fun generate(prompt: String): String =
        call(GeminiRequest(listOf(GeminiContent(listOf(GeminiPart(text = prompt))))))

    override suspend fun generateJson(prompt: String): String =
        call(
            GeminiRequest(
                listOf(GeminiContent(listOf(GeminiPart(text = prompt)))),
                GeminiGenerationConfig("application/json"),
            )
        )

    override suspend fun generateJsonFromImage(prompt: String, mimeType: String, imageBase64: String): String =
        call(
            GeminiRequest(
                listOf(GeminiContent(listOf(GeminiPart(text = prompt), GeminiPart(inlineData = GeminiInlineData(mimeType, imageBase64))))),
                GeminiGenerationConfig("application/json"),
            )
        )

    private suspend fun call(request: GeminiRequest): String {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("GEMINI_API_KEY environment variable is not configured.")
        }

        val response: HttpResponse = httpClient.post("https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent") {
            url { parameters.append("key", apiKey) }
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        if (response.status != HttpStatusCode.OK) {
            throw Exception("Gemini error (${response.status.value}): ${response.bodyAsText()}")
        }

        return response.body<GeminiResponse>()
            .candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
            ?: throw Exception("Gemini returned no text.")
    }
}
