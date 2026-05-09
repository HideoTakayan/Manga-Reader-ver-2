package com.example.manga_readerver2.core.utils

import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

fun GET(
    url: String,
    headers: Headers = Headers.Builder().build(),
    cache: CacheControl = CacheControl.Builder().build()
): Request {
    return Request.Builder()
        .url(url)
        .headers(headers)
        .cacheControl(cache)
        .build()
}

suspend fun Call.await(): Response {
    return suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                continuation.resumeWithException(e)
            }
        })

        continuation.invokeOnCancellation {
            try {
                cancel()
            } catch (ex: Throwable) {
                // Ignore
            }
        }
    }
}

suspend fun Call.awaitSuccess(): Response {
    val response = await()
    if (!response.isSuccessful) {
        response.close()
        throw Exception("HTTP error: ${response.code}")
    }
    return response
}

inline fun <reified T> Json.decodeFromResponse(response: Response): T {
    val body = response.body?.string() ?: throw Exception("Empty response body")
    return this.decodeFromString(body)
}
