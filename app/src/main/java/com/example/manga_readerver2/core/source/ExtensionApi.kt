package com.example.manga_readerver2.core.source

import android.content.Context
import com.example.manga_readerver2.domain.repository.ExtensionRepoRepository
import com.example.manga_readerver2.source_dex.loader.ExtensionLoader
import eu.kanade.tachiyomi.network.GET
import com.example.manga_readerver2.core.utils.awaitSuccess
import com.example.manga_readerver2.core.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import logcat.logcat
import okhttp3.OkHttpClient
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ExtensionApi {

    private val client: OkHttpClient = Injekt.get()
    private val repoRepository: ExtensionRepoRepository = Injekt.get()
    private val json: Json = Injekt.get()

    suspend fun findExtensions(): List<Extension.Available> {
        return withContext(Dispatchers.IO) {
            val repos = repoRepository.getAll()
            repos.map { repo ->
                async { getExtensions(repo.baseUrl) }
            }.awaitAll().flatten()
        }
    }

    private suspend fun getExtensions(repoBaseUrl: String): List<Extension.Available> {
        return try {
            val pluginUrl = if (repoBaseUrl.endsWith("/plugin.json", ignoreCase = true)) {
                repoBaseUrl
            } else {
                "${repoBaseUrl.removeSuffix("/")}/plugin.json"
            }
            val pluginResponse = client.newCall(GET(pluginUrl, headers = okhttp3.Headers.Builder().add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)").build())).execute()
            
            if (pluginResponse.isSuccessful) {
                val responseString = pluginResponse.body?.string() ?: ""
                val pluginList = try {
                    json.decodeFromString<VBookExtensionResponse>(responseString).data
                } catch (e: Exception) {
                    // Fallback to direct list if it's an older format
                    try {
                        json.decodeFromString<List<VBookExtensionItemApi>>(responseString)
                    } catch (e2: Exception) {
                        logcat(LogPriority.ERROR) { "Failed to parse plugin.json: ${e2.message}\n$responseString" }
                        emptyList()
                    }
                }
                pluginList.map { vbookExtension ->
                    val slug = vbookExtension.path.substringBeforeLast("/").substringAfterLast("/")
                    Extension.Available(
                        name = vbookExtension.name,
                        pkgName = "js.extension.$slug",
                        versionName = vbookExtension.version.toString(),
                        versionCode = vbookExtension.version.toLong(),
                        libVersion = 1.0, // Mặc định cho JS
                        lang = vbookExtension.locale.substringBefore("_"),
                        isNsfw = false,
                        author = vbookExtension.author,
                        description = vbookExtension.description,
                        sources = listOf(
                            Extension.Available.Source(
                                id = 0L,
                                lang = vbookExtension.locale.substringBefore("_"),
                                name = vbookExtension.name,
                                baseUrl = vbookExtension.source
                            )
                        ),
                        apkName = vbookExtension.path, // Save the full URL here
                        iconUrl = vbookExtension.icon,
                        repoUrl = repoBaseUrl
                    )
                }
            } else {
                val apkUrl = if (repoBaseUrl.endsWith("/index.min.json", ignoreCase = true)) {
                    repoBaseUrl
                } else {
                    "${repoBaseUrl.removeSuffix("/")}/index.min.json"
                }
                val apkResponse = client.newCall(GET(apkUrl)).execute()
                if (apkResponse.isSuccessful) {
                    apkResponse
                        .parseAs<List<ExtensionJsonObject>>(json)
                        .toExtensions(repoBaseUrl)
                } else {
                    emptyList()
                }
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR) { "Failed to get extensions from $repoBaseUrl: ${e.message}" }
            emptyList()
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
                    author = it.author,
                    description = it.description,
                    sources = it.sources?.map(extensionSourceMapper).orEmpty(),
                    apkName = it.apk,
                    iconUrl = "$repoUrl/icon/${it.pkg}.png",
                    repoUrl = repoUrl,
                )
            }
    }

    fun getApkUrl(extension: Extension.Available): String {
        return if (extension.apkName.startsWith("http")) {
            extension.apkName
        } else {
            "${extension.repoUrl}/apk/${extension.apkName}"
        }
    }

    private fun ExtensionJsonObject.extractLibVersion(): Double {
        return version.substringBeforeLast('.').toDouble()
    }
}

@Serializable
private data class ExtensionJsonObject(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int,
    val author: String? = null,
    val description: String? = null,
    val sources: List<ExtensionSourceJsonObject>?,
)

@Serializable
private data class ExtensionSourceJsonObject(
    val id: String,
    val lang: String,
    val name: String,
    val baseUrl: String,
)

private val extensionSourceMapper: (ExtensionSourceJsonObject) -> Extension.Available.Source = {
    Extension.Available.Source(
        id = it.id.toLong(),
        lang = it.lang,
        name = it.name,
        baseUrl = it.baseUrl,
    )
}



@Serializable
private data class VBookExtensionItemApi(
    val name: String,
    val author: String? = null,
    val path: String,
    val version: Int = 1,
    val source: String = "",
    val icon: String = "",
    val description: String? = null,
    val type: String = "",
    val locale: String = "vi"
)

@Serializable
private data class VBookExtensionResponse(
    val data: List<VBookExtensionItemApi>
)
