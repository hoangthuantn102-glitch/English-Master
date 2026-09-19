package com.example.api

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val systemInstruction: Content? = null,
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
    val temperature: Float? = null,
    val maxOutputTokens: Int? = null
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
        @POST("v1beta/models/gemini-3.1-flash-lite-preview:generateContent")
        suspend fun generateLiteContent(
            @Query("key") apiKey: String,
            @Body request: GeminiRequest
        ): ResponseBody

        @POST("v1beta/models/gemini-flash-latest:generateContent")
        suspend fun generateFlashLatestContent(
            @Query("key") apiKey: String,
            @Body request: GeminiRequest
        ): ResponseBody

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
    suspend fun getGeminiResponse(prompt: String, customApiKey: String? = null, jsonMode: Boolean = false): String = withContext(Dispatchers.IO) {
        val apiKey = if (!customApiKey.isNullOrEmpty()) customApiKey else BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "API_KEY_MISSING"
        }

        val request = GeminiRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt)))
            ),
            generationConfig = if (jsonMode) GenerationConfig(responseMimeType = "application/json") else null
        )

        try {
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

    // Helper for custom API Key Chat conversations with message history (accelerated & detailed)
    suspend fun getGeminiChatResponse(
        history: List<Content>,
        customApiKey: String,
        systemInstruction: Content? = null
    ): String = withContext(Dispatchers.IO) {
        if (customApiKey.isEmpty()) {
            return@withContext "API_KEY_MISSING"
        }

        val request = GeminiRequest(
            contents = history,
            systemInstruction = systemInstruction,
            generationConfig = GenerationConfig(
                temperature = 0.2f,
                maxOutputTokens = 1600
            )
        )

        // 1. First prioritize gemini-3.1-flash-lite-preview for ultra-fast response speed
        try {
            val responseBody = service.generateLiteContent(customApiKey, request)
            val jsonString = responseBody.string()
            val candidateText = parseCandidateText(jsonString)
            if (candidateText != null) return@withContext candidateText
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Second try gemini-flash-latest if lite is busy
        try {
            val responseBody = service.generateFlashLatestContent(customApiKey, request)
            val jsonString = responseBody.string()
            val candidateText = parseCandidateText(jsonString)
            if (candidateText != null) return@withContext candidateText
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Fallback to standard 3.5-flash
        try {
            val responseBody = service.generateContent(customApiKey, request)
            val jsonString = responseBody.string()
            val candidateText = parseCandidateText(jsonString)
            candidateText ?: "No response from Gemini"
        } catch (e: Exception) {
            e.printStackTrace()
            "ERROR: ${e.localizedMessage ?: "Unknown error occurred"}"
        }
    }

    // Ultra-fast helper optimized specifically for real-time voice & oral communication room
    suspend fun getGeminiFastChatResponse(
        history: List<Content>,
        customApiKey: String,
        systemInstruction: Content? = null
    ): String = withContext(Dispatchers.IO) {
        if (customApiKey.isEmpty()) {
            return@withContext "API_KEY_MISSING"
        }

        val request = GeminiRequest(
            contents = history,
            systemInstruction = systemInstruction,
            generationConfig = GenerationConfig(
                temperature = 0.4f,
                maxOutputTokens = 85
            )
        )

        try {
            // First try the ultra-fast lite model for near-instant conversational latency
            val responseBody = service.generateLiteContent(customApiKey, request)
            val jsonString = responseBody.string()
            val candidateText = parseCandidateText(jsonString)
            if (candidateText != null) return@withContext candidateText
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            // Second try gemini-flash-latest if lite is temporarily unavailable
            val responseBody = service.generateFlashLatestContent(customApiKey, request)
            val jsonString = responseBody.string()
            val candidateText = parseCandidateText(jsonString)
            if (candidateText != null) return@withContext candidateText
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback to standard 3.5-flash
        try {
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
        // First try standard JSONObject parsing for complete unicode, escape, and multi-part support
        try {
            val jsonObject = org.json.JSONObject(json)
            val candidates = jsonObject.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val sb = StringBuilder()
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        sb.append(part.optString("text", ""))
                    }
                    val res = sb.toString().trim()
                    if (res.isNotEmpty()) return res
                }
            }
        } catch (e: Exception) {
            // Fallback to manual string scanning if needed
        }

        try {
            // Fallback manual scanning
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
