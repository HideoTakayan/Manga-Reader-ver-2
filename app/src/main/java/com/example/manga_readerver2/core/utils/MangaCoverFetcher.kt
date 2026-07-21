package com.example.manga_readerver2.core.utils

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.request.Options
import com.example.manga_readerver2.domain.model.Manga
import com.example.manga_readerver2.core.source.SourceManager
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Call
import okhttp3.Request
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import logcat.logcat
import okio.Path.Companion.toPath

class MangaCoverFetcher(
    private val manga: Manga,
    private val sourceManager: SourceManager,
    private val callFactory: Call.Factory,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val source = sourceManager.get(manga.source)
        
        // 1. Ưu tiên truy xuất và kiểm tra dữ liệu hình ảnh ngoại tuyến (Offline cache)
        if (source != null) {
            try {
                val fileManager = Injekt.get<com.example.manga_readerver2.core.utils.FileManager>()
                val mangaDir = fileManager.getDownloadPath().resolve(source.name).resolve(manga.title + "_" + manga.id)
                val coverFile = java.io.File(mangaDir, "cover.jpg")
                if (coverFile.exists() && coverFile.length() > 0) {
                    return coil3.fetch.SourceFetchResult(
                        source = coil3.decode.ImageSource(
                            file = coverFile.absolutePath.toPath(),
                            fileSystem = okio.FileSystem.SYSTEM
                        ),
                        mimeType = "image/jpeg",
                        dataSource = DataSource.DISK
                    )
                }
            } catch (e: Exception) {
                // Bỏ qua ngoại lệ và chuyển hướng xử lý sang nguồn cấp mạng (Network)
            }
        }

        // 2. Áp dụng cơ chế truy vấn dữ liệu từ mạng (Network Fallback)
        var url = manga.thumbnailUrl ?: return null
        if (url.startsWith("//")) url = "https:$url"
        if (url.startsWith("http").not()) return null

        val jsSource = source as? com.example.manga_readerver2.source_js.JsSource
        val client = (source as? HttpSource)?.client 
            ?: (if (jsSource != null) Injekt.get<eu.kanade.tachiyomi.network.NetworkHelper>().cloudflareClient else Injekt.get<okhttp3.OkHttpClient>())
        val headers = (source as? HttpSource)?.headers 
            ?: jsSource?.headers

        val requestBuilder = Request.Builder().url(url)
        var hasReferer = false
        headers?.let { 
            requestBuilder.headers(it) 
            hasReferer = it["Referer"] != null
        }
        
        // Inject Referer for JS Extensions:
        // Yêu cầu thiết lập domain gốc của trang truyện (manga.url) làm giá trị Referer,
        // NGHIÊM CẤM sử dụng domain của CDN từ đường dẫn ảnh (Image URL).
        // Ví dụ: Hình ảnh lưu trữ tại i200.truyenvua.com bắt buộc phải có Referer = https://truyenqqko.com/ (Trang gốc)
        if (!hasReferer) {
            val refererUrl = when {
                // JS Source: Trích xuất tham số Referer trực tiếp từ đường dẫn trang truyện (manga page URL)
                jsSource != null && manga.url.startsWith("http") -> manga.url
                // Cơ chế dự phòng đối với Non-JS Source: Sử dụng domain trực tiếp từ đường dẫn ảnh
                url.isNotBlank() -> url
                else -> null
            }
            if (refererUrl != null) {
                try {
                    val uri = java.net.URI(refererUrl)
                    if (uri.host != null) {
                        requestBuilder.addHeader("Referer", "${uri.scheme}://${uri.host}/")
                    }
                } catch (_: Exception) { }
            }
        }

        val request = requestBuilder.build()
        
        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return null
            }
            
            val body = response.body ?: return null
            coil3.fetch.SourceFetchResult(
                source = coil3.decode.ImageSource(
                    source = body.source(),
                    fileSystem = okio.FileSystem.SYSTEM
                ),
                mimeType = body.contentType()?.toString(),
                dataSource = DataSource.NETWORK
            )
        } catch (e: Exception) {
            logcat { "MangaCoverFetcher failed: ${e.message}" }
            null
        }
    }

    class Factory(
        private val sourceManager: SourceManager,
        private val callFactory: Call.Factory
    ) : Fetcher.Factory<Manga> {
        override fun create(data: Manga, options: Options, imageLoader: ImageLoader): Fetcher {
            return MangaCoverFetcher(data, sourceManager, callFactory, options)
        }
    }
}
