package com.example.api

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// Models for Gemini REST API
data class GeminiRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null
)

data class Content(
    val parts: List<Part>,
    val role: String? = null
)

data class Part(
    val text: String
)

data class GenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Float? = null
)

object GeminiClient {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    interface GeminiApi {
        @POST("v1beta/models/gemini-3.5-flash:generateContent")
        suspend fun generateContent(
            @Query("key") apiKey: String,
            @Body request: GeminiRequest
        ): ResponseBody
    }

    val service: GeminiApi by lazy {
        retrofit.create(GeminiApi::class.java)
    }

    // Helper to call Gemini API
    suspend fun getGeminiResponse(prompt: String, customApiKey: String? = null, jsonMode: Boolean = false): String {
        val apiKey = if (!customApiKey.isNullOrEmpty()) customApiKey else BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return "API_KEY_MISSING"
        }

        val request = GeminiRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt)))
            ),
            generationConfig = if (jsonMode) GenerationConfig(responseMimeType = "application/json") else null
        )

        return try {
            val responseBody = service.generateContent(apiKey, request)
            val jsonString = responseBody.string()
            
            // Parse candidate text from raw response
            // Gemini response looks like: {"candidates": [{"content": {"parts": [{"text": "..."}]}}]}
            val candidateText = parseCandidateText(jsonString)
            candidateText ?: "No response from Gemini"
        } catch (e: Exception) {
            e.printStackTrace()
            "ERROR: ${e.localizedMessage ?: "Unknown error occurred"}"
        }
    }

    // Helper for custom API Key Chat conversations with message history
    suspend fun getGeminiChatResponse(history: List<Content>, customApiKey: String): String {
        if (customApiKey.isEmpty()) {
            return "API_KEY_MISSING"
        }

        val request = GeminiRequest(
            contents = history
        )

        return try {
            val responseBody = service.generateContent(customApiKey, request)
            val jsonString = responseBody.string()
            val candidateText = parseCandidateText(jsonString)
            candidateText ?: "No response from Gemini"
        } catch (e: Exception) {
            e.printStackTrace()
            "ERROR: ${e.localizedMessage ?: "Unknown error occurred"}"
        }
    }

    private fun parseCandidateText(json: String): String? {
        try {
            // Since we don't want to define complete complex nested structure classes that can break with API changes,
            // we can parse candidate text using standard simple JSON scanning or direct String manipulation.
            // Let's use direct String scanning to extract the first candidate "text" field, which is extremely robust!
            val textToken = "\"text\":"
            var index = json.indexOf(textToken)
            if (index == -1) return null
            
            index += textToken.length
            // find first quotation mark
            val startQuote = json.indexOf("\"", index)
            if (startQuote == -1) return null
            
            // find closing quotation mark, accounting for escaped quotes
            var endQuote = startQuote + 1
            while (endQuote < json.length) {
                val char = json[endQuote]
                if (char == '\\') {
                    endQuote += 2 // Skip escaped char
                    continue
                }
                if (char == '\"') {
                    break
                }
                endQuote++
            }
            
            if (endQuote >= json.length) return null
            val escapedText = json.substring(startQuote + 1, endQuote)
            
            // Unescape common JSON characters
            return escapedText
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
