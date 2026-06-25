package eu.kanade.tachiyomi.network

import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import rx.Producer
import rx.Subscription
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

// --- OkHttp Extensions for RxJava ---

fun Call.asObservable(): Observable<Response> {
    return Observable.unsafeCreate { subscriber ->
        val call = clone()
        val requestArbiter = object : Producer, Subscription {
            val boolean = AtomicBoolean(false)
            override fun request(n: Long) {
                if (n == 0L || !boolean.compareAndSet(false, true)) return
                try {
                    val response = call.execute()
                    if (!subscriber.isUnsubscribed) {
                        subscriber.onNext(response)
                        subscriber.onCompleted()
                    }
                } catch (e: Exception) {
                    if (!subscriber.isUnsubscribed) {
                        subscriber.onError(e)
                    }
                }
            }
            override fun unsubscribe() { call.cancel() }
            override fun isUnsubscribed(): Boolean = call.isCanceled()
        }
        subscriber.add(requestArbiter)
        subscriber.setProducer(requestArbiter)
    }
}

fun Call.asObservableSuccess(): Observable<Response> {
    return asObservable().doOnNext { response: Response ->
        if (!response.isSuccessful) {
            response.close()
            throw Exception("HTTP error ${response.code}")
        }
    }
}

// Based on https://github.com/gildor/kotlin-coroutines-okhttp
private suspend fun Call.await(callStack: Array<StackTraceElement>): Response {
    return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        val callback = object : okhttp3.Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) {
                    response.body?.close()
                }
            }

            override fun onFailure(call: Call, e: java.io.IOException) {
                if (continuation.isCancelled) return
                val exception = java.io.IOException(e.message, e).apply { stackTrace = callStack }
                continuation.resumeWithException(exception)
            }
        }

        enqueue(callback)

        continuation.invokeOnCancellation {
            try {
                cancel()
            } catch (ex: Throwable) {
                // Ignore cancel exception
            }
        }
    }
}

suspend fun Call.await(): Response {
    val callStack = Exception().stackTrace.run { copyOfRange(1, size) }
    return await(callStack)
}

/**
 * @since extensions-lib 1.5
 */
suspend fun Call.awaitSuccess(): Response {
    val callStack = Exception().stackTrace.run { copyOfRange(1, size) }
    val response = await(callStack)
    if (!response.isSuccessful) {
        response.close()
        throw Exception("HTTP error ${response.code}").apply { stackTrace = callStack }
    }
    return response
}

fun okhttp3.OkHttpClient.newCachelessCallWithProgress(request: Request, listener: ProgressListener): Call {
    val progressClient = newBuilder()
        .cache(null)
        .addNetworkInterceptor { chain ->
            val originalResponse = chain.proceed(chain.request())
            originalResponse.newBuilder()
                .body(ProgressResponseBody(originalResponse.body!!, listener))
                .build()
        }
        .build()

    return progressClient.newCall(request)
}
