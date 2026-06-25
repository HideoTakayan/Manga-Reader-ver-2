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

class MangaCoverFetcher(
    private val manga: Manga,
    private val sourceManager: SourceManager,
    private val callFactory: Call.Factory,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val url = manga.thumbnailUrl ?: return null
        if (url.startsWith("http").not()) return null

        val source = sourceManager.get(manga.source) as? HttpSource
        val client = source?.client ?: Injekt.get<okhttp3.OkHttpClient>()
        val headers = source?.headers

        val requestBuilder = Request.Builder().url(url)
        headers?.let { requestBuilder.headers(it) }

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
