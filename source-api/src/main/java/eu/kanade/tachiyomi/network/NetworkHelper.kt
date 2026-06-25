package eu.kanade.tachiyomi.network

import android.content.Context
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class NetworkHelper(context: Context, injectedClient: OkHttpClient? = null) {

    val cookieJar: CookieJar = injectedClient?.cookieJar ?: CookieJar.NO_COOKIES

    val client: OkHttpClient = injectedClient ?: OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Deprecated("Use client instead")
    val cloudflareClient: OkHttpClient = client
    
    val nonCloudflareClient: OkHttpClient = client

    fun defaultUserAgentProvider(): String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.150 Safari/537.36"
}
