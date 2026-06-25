package com.example.manga_readerver2.core.track

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AniListManager {

    private val trackPreferences by lazy { Injekt.get<TrackPreferences>() }
    private val client by lazy { Injekt.get<OkHttpClient>() }
    
    companion object {
        const val CLIENT_ID = "3596"
        const val AUTH_URL = "https://anilist.co/api/v2/oauth/authorize?client_id=$CLIENT_ID&response_type=token"
        const val API_URL = "https://graphql.anilist.co"
    }

    suspend fun updateProgress(title: String, chapter: Int): Boolean = withContext(Dispatchers.IO) {
        val token = trackPreferences.anilistToken.get()
        if (token.isEmpty()) return@withContext false

        try {
            // 1. Search manga ID
            val searchQuery = """
                query (${'$'}search: String) {
                    Page(page: 1, perPage: 1) {
                        media(search: ${'$'}search, type: MANGA) {
                            id
                            title {
                                romaji
                                english
                            }
                        }
                    }
                }
            """.trimIndent()

            val searchVariables = JSONObject().put("search", title)
            val searchPayload = JSONObject()
                .put("query", searchQuery)
                .put("variables", searchVariables)

            val searchRequest = Request.Builder()
                .url(API_URL)
                .post(searchPayload.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .build()

            val searchResponse = client.newCall(searchRequest).execute()
            if (!searchResponse.isSuccessful) return@withContext false
            val responseBody = searchResponse.body?.string() ?: ""
            if (responseBody.isEmpty()) return@withContext false
            val searchJson = JSONObject(responseBody)
            val mediaArray = searchJson.optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media")
            if (mediaArray == null || mediaArray.length() == 0) return@withContext false
            val mediaId = mediaArray.getJSONObject(0).getInt("id")

            // 2. Update progress
            val updateQuery = """
                mutation (${'$'}mediaId: Int, ${'$'}progress: Int) {
                    SaveMediaListEntry(mediaId: ${'$'}mediaId, progress: ${'$'}progress) {
                        id
                        progress
                    }
                }
            """.trimIndent()

            val updateVariables = JSONObject()
                .put("mediaId", mediaId)
                .put("progress", chapter)
            
            val updatePayload = JSONObject()
                .put("query", updateQuery)
                .put("variables", updateVariables)

            val updateRequest = Request.Builder()
                .url(API_URL)
                .post(updatePayload.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .build()

            val updateResponse = client.newCall(updateRequest).execute()
            return@withContext updateResponse.isSuccessful
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "AniList update error: ${'$'}{e.message}" }
            return@withContext false
        }
    }
}
