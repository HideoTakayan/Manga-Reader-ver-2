package eu.kanade.tachiyomi.util

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import rx.Observable
import rx.Subscriber
import rx.Subscription
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// awaitSingle() đã được định nghĩa sẵn trong lớp RxCoroutineBridge.kt
// Module này hiện chỉ duy trì hàm awaitOne nhằm đảm bảo tính tương thích ngược (Backward Compatibility)

internal suspend fun <T> Observable<T>.awaitOneInternal(): T = suspendCancellableCoroutine { cont ->
    cont.unsubscribeOnCancellation(
        subscribe(
            object : Subscriber<T>() {
                override fun onStart() {
                    request(1)
                }

                override fun onNext(t: T) {
                    cont.resume(t)
                }

                override fun onCompleted() {
                    if (cont.isActive) {
                        cont.resumeWithException(
                            IllegalStateException("Should have invoked onNext")
                        )
                    }
                }

                override fun onError(e: Throwable) {
                    cont.resumeWithException(e)
                }
            }
        )
    )
}

private fun <T> CancellableContinuation<T>.unsubscribeOnCancellation(sub: Subscription) =
    invokeOnCancellation { sub.unsubscribe() }
