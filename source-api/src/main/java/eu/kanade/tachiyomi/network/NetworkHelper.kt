package eu.kanade.tachiyomi.network

import android.content.Context
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class NetworkHelper(context: Context) {

    val cookieJar: CookieJar = CookieJar.NO_COOKIES // Minimal implementation, replace with real one if needed

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Deprecated("Use client instead")
    val cloudflareClient: OkHttpClient = client
    
    val nonCloudflareClient: OkHttpClient = client
}
