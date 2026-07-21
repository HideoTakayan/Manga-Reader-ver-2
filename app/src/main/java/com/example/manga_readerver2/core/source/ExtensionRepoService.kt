package com.example.manga_readerver2.core.source

import com.example.manga_readerver2.core.utils.awaitSuccess
import com.example.manga_readerver2.core.utils.parseAs
import com.example.manga_readerver2.domain.repository.ExtensionRepo
import eu.kanade.tachiyomi.network.GET
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import logcat.logcat
import okhttp3.OkHttpClient
import uy.kohesive.injekt.injectLazy

class ExtensionRepoService {
    private val client: OkHttpClient by injectLazy()
    private val json: Json by injectLazy()

    suspend fun fetchRepoDetails(repo: String): ExtensionRepo? {
        return withContext(Dispatchers.IO) {
            try {
                // Xác minh xem URL có dẫn trực tiếp đến tệp tin JSON hay không
                val isDirectJson = repo.endsWith(".json", ignoreCase = true)
                
                if (isDirectJson) {
                    val response = client.newCall(GET(repo)).execute()
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: ""
                        // Tiến hành phân tích cú pháp JSON (Parse)
                        try {
                            val meta = json.decodeFromString<ExtensionRepoDto>(responseBody).meta
                            return@withContext ExtensionRepo(
                                baseUrl = repo,
                                name = meta.name,
                                shortName = meta.shortName,
                                website = meta.website,
                                signingKeyFingerprint = meta.signingKeyFingerprint
                            )
                        } catch (e: Exception) {}
                        
                        // Áp dụng định dạng kho lưu trữ JS (VBook)
                        return@withContext ExtensionRepo(
                            baseUrl = repo,
                            name = "VBook Extensions",
                            shortName = "VBook",
                            website = repo.substringBeforeLast("/"),
                            signingKeyFingerprint = ""
                        )
                    }
                    return@withContext null
                }

                // Đối với Base URL, tiến hành truy xuất tệp repo.json theo định dạng chuẩn
                val mihonResponse = client.newCall(GET("$repo/repo.json")).execute()
                if (mihonResponse.isSuccessful) {
                    val meta = mihonResponse.parseAs<ExtensionRepoDto>(json).meta
                    return@withContext ExtensionRepo(
                        baseUrl = repo,
                        name = meta.name,
                        shortName = meta.shortName,
                        website = meta.website,
                        signingKeyFingerprint = meta.signingKeyFingerprint
                    )
                }

                // Trong trường hợp truy xuất thất bại, chuyển hướng truy xuất tệp plugin.json (định dạng vBook JS) làm phương án thay thế
                val vbookResponse = client.newCall(GET("$repo/plugin.json")).execute()
                if (vbookResponse.isSuccessful) {
                    return@withContext ExtensionRepo(
                        baseUrl = repo,
                        name = "VBook Extensions",
                        shortName = "VBook",
                        website = repo,
                        signingKeyFingerprint = ""
                    )
                }
                
                null
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to fetch repo details from $repo: ${e.message}" }
                null
            }
        }
    }
}

@Serializable
private data class ExtensionRepoDto(
    val meta: ExtensionRepoMetaDto
)

@Serializable
private data class ExtensionRepoMetaDto(
    val name: String,
    val shortName: String? = null,
    val website: String,
    val signingKeyFingerprint: String
)

@Serializable
internal data class VBookPluginJson(
    val data: List<VBookExtensionItemDto> = emptyList()
)

@Serializable
internal data class VBookExtensionItemDto(
    val name: String,
    val author: String? = null
)
