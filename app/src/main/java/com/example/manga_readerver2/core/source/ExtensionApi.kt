package com.example.manga_readerver2.core.source

import com.example.manga_readerver2.core.utils.GET
import com.example.manga_readerver2.core.utils.awaitSuccess
import com.example.manga_readerver2.core.utils.decodeFromResponse
import com.example.manga_readerver2.source_dex.loader.ExtensionLoader
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import logcat.logcat
import okhttp3.OkHttpClient

/**
 * Xử lý việc lấy danh sách Extension từ Repository (file index.min.json).
 */
class ExtensionApi(
    private val client: OkHttpClient
) {
    private val json = Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true
        isLenient = true
    }

    suspend fun findExtensions(repoUrl: String): List<Extension.Available> {
        val baseUrl = repoUrl.removeSuffix("/").removeSuffix("/index.min.json").removeSuffix("/plugin.json")
        logcat(LogPriority.DEBUG) { "Đang tìm extension tại: $baseUrl" }
        
        return withContext(Dispatchers.IO) {
            // 1. Thử định dạng Tachiyomi/Mihon
            try {
                val indexUrl = "$baseUrl/index.min.json"
                val response = client.newCall(GET(indexUrl)).awaitSuccess()
                val bodyString = response.body?.string() ?: ""
                val jsonObjects = json.decodeFromString<List<ExtensionJsonObject>>(bodyString)
                logcat(LogPriority.DEBUG) { "Phát hiện repo Tachiyomi: ${jsonObjects.size} mục" }
                return@withContext jsonObjects.toExtensions(baseUrl)
            } catch (e: Exception) {
                logcat(LogPriority.DEBUG) { "Không phải repo Tachiyomi ($baseUrl): ${e.message}" }
            }

            // 2. Thử định dạng VBook
            try {
                val vbookUrl = "$baseUrl/plugin.json"
                val response = client.newCall(GET(vbookUrl)).awaitSuccess()
                val bodyString = response.body?.string() ?: ""
                val vbookRepo = json.decodeFromString<VBookRepoObject>(bodyString)
                logcat(LogPriority.DEBUG) { "Phát hiện repo VBook: ${vbookRepo.data.size} mục" }
                
                return@withContext vbookRepo.data.map { item ->
                    // item.path LÀ URL tuyệt đối, ví dụ: https://.../bachngocsach/plugin.zip
                    val downloadUrl = item.path  // Dùng trực tiếp, không ghép thêm baseUrl
                    val folderUrl = downloadUrl.substringBeforeLast("/")  // https://.../bachngocsach

                    // pluginId là tên thư mục trong URL: bachngocsach, truyenfull, v.v.
                    val pluginId = folderUrl.substringAfterLast("/")

                    val lang = normalizeLang(item.locale ?: item.language)

                    // ID phải khớp với JsLoader.sourceId formula
                    val sourceId = 0x5642000000000000L or
                        ((item.name + lang).hashCode().toLong() and 0xFFFFFFFFL)

                    // isVBook = true cho novel, false cho comic (vẫn dùng VBook engine nhưng được hiển thị khác)
                    val isNovel = item.type == "novel"

                    Extension.Available(
                        name = item.name,
                        pkgName = "vbook.$pluginId",
                        versionName = item.version.toString(),
                        versionCode = item.version.toLong(),
                        libVersion = 1.4,
                        lang = lang,
                        isNsfw = item.tag == "nsfw" || item.tag == "18+",
                        sources = listOf(
                            Extension.Available.AvailableSource(
                                id = sourceId,
                                lang = lang,
                                name = item.name,
                                baseUrl = item.source ?: ""
                            )
                        ),
                        // apkName = tên file duy nhất cho mỗi plugin
                        apkName = "${pluginId}_plugin.zip",
                        iconUrl = item.icon ?: "",  // Là URL tuyệt đối rồi
                        // repoUrl = URL thư mục chứa plugin.zip (dùng khi download và lấy icon)
                        repoUrl = downloadUrl,  // Lưu URL download thẳng
                        isVBook = true,  // Tất cả VBook đều dùng JS engine
                        author = item.author
                    )
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Lỗi khi nạp repo từ $repoUrl: ${e.message}" }
                emptyList()
            }
        }
    }

    private fun normalizeLang(code: String?): String {
        if (code.isNullOrBlank()) return "all"
        val normalized = code.trim().lowercase()
        return when {
            normalized == "global" -> "all"
            normalized.contains("_") -> normalized.substringBefore("_")
            normalized.contains("-") -> normalized.substringBefore("-")
            else -> normalized
        }
    }

    private fun List<ExtensionJsonObject>.toExtensions(repoUrl: String): List<Extension.Available> {
        return this
            .filter {
                val libVersion = it.extractLibVersion()
                libVersion >= ExtensionLoader.LIB_VERSION_MIN && libVersion <= ExtensionLoader.LIB_VERSION_MAX
            }
            .map {
                Extension.Available(
                    name = it.name.substringAfter("Tachiyomi: "),
                    pkgName = it.pkg,
                    versionName = it.version,
                    versionCode = it.code,
                    libVersion = it.extractLibVersion(),
                    lang = it.lang,
                    isNsfw = it.nsfw == 1,
                    sources = it.sources?.map { s -> 
                        Extension.Available.AvailableSource(s.id, s.lang, s.name, s.baseUrl) 
                    }.orEmpty(),
                    apkName = it.apk,
                    iconUrl = "$repoUrl/icon/${it.pkg}.png",
                    repoUrl = repoUrl,
                    isVBook = false
                )
            }
    }

    private fun ExtensionJsonObject.extractLibVersion(): Double {
        val parts = version.split('.')
        return if (parts.size >= 2) {
            "${parts[0]}.${parts[1]}".toDoubleOrNull() ?: 0.0
        } else {
            version.toDoubleOrNull() ?: 0.0
        }
    }

    suspend fun fetchRepoDetails(url: String): ExtensionRepoMetadata? {
        val baseUrl = url.removeSuffix("/").removeSuffix("/index.min.json").removeSuffix("/plugin.json").removeSuffix("/repo.json")
        return withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(GET("$baseUrl/repo.json")).awaitSuccess()
                val bodyString = response.body?.string() ?: ""
                val repoJson = json.decodeFromString<ExtensionRepoJson>(bodyString)
                return@withContext ExtensionRepoMetadata(
                    name = repoJson.meta.name,
                    baseUrl = baseUrl,
                    fingerprint = repoJson.meta.signingKeyFingerprint
                )
            } catch (e: Exception) {
                logcat(LogPriority.DEBUG) { "Không thể lấy repo.json từ $baseUrl: ${e.message}" }
                null
            }
        }
    }
}

@Serializable
private data class ExtensionRepoJson(
    val meta: ExtensionRepoMetaJson
)

@Serializable
private data class ExtensionRepoMetaJson(
    val name: String,
    val website: String? = null,
    val signingKeyFingerprint: String? = null
)

data class ExtensionRepoMetadata(
    val name: String,
    val baseUrl: String,
    val fingerprint: String? = null
)

@Serializable
private data class ExtensionJsonObject(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int,
    val sources: List<ExtensionSourceJsonObject>? = null,
)

@Serializable
private data class ExtensionSourceJsonObject(
    val id: Long,
    val lang: String,
    val name: String,
    val baseUrl: String,
)

@Serializable
private data class VBookRepoObject(
    val metadata: VBookMetadataObject? = null,
    val data: List<VBookItemObject>
)

@Serializable
private data class VBookMetadataObject(
    val author: String? = null,
    val description: String? = null
)

@Serializable
private data class VBookItemObject(
    val name: String,
    val path: String,
    val version: Int,
    val author: String? = null,
    val source: String? = null,
    val icon: String? = null,
    val type: String? = null,
    val locale: String? = null,
    val language: String? = null,
    val tag: String? = null,
    val description: String? = null
)
