package com.example.manga_readerver2.core.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import logcat.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CloudflareInterceptor(
    private val context: Context,
    private val defaultUserAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
) : Interceptor {

    private val handler = Handler(Looper.getMainLooper())

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val response = chain.proceed(originalRequest)

        // Đánh giá mã trạng thái (503 hoặc 403) nhằm nhận diện cơ chế bảo vệ Cloudflare Challenge
        if (response.code !in listOf(403, 503)) {
            return response
        }

        val isCloudflare = response.headers("Server").any { it.contains("cloudflare", ignoreCase = true) }
        if (!isCloudflare) {
            return response
        }

        logcat { "Cloudflare challenge detected for ${originalRequest.url}. Starting WebView resolution..." }
        
        // Close the previous response to free up resources before starting the challenge
        response.close()

        val latch = CountDownLatch(1)
        var resolved = false
        var newUserAgent = defaultUserAgent

        handler.post {
            val webView = createWebView()
            
            val checkCookieRunnable = object : Runnable {
                override fun run() {
                    if (resolved || latch.count == 0L) return
                    val cookies = CookieManager.getInstance().getCookie(originalRequest.url.toString())
                    if (cookies != null && cookies.contains("cf_clearance")) {
                        logcat { "Cloudflare challenge resolved successfully via polling!" }
                        resolved = true
                        newUserAgent = webView.settings.userAgentString ?: defaultUserAgent
                        handler.post { webView.destroy() }
                        latch.countDown()
                    } else {
                        // Cấu hình chu kỳ kiểm tra lặp lại với độ trễ 500ms
                        handler.postDelayed(this, 500)
                    }
                }
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.let { wv ->
                        val cookies = CookieManager.getInstance().getCookie(url)
                        if (cookies != null && cookies.contains("cf_clearance")) {
                            if (!resolved) {
                                logcat { "Cloudflare challenge resolved successfully onPageFinished!" }
                                resolved = true
                                newUserAgent = wv.settings.userAgentString ?: defaultUserAgent
                                handler.post { wv.destroy() }
                                latch.countDown()
                            }
                        }
                    }
                }
            }

            webView.loadUrl(originalRequest.url.toString())
            handler.post(checkCookieRunnable) // Kích hoạt cơ chế truy vấn liên tục (Polling Loop)

            // Thiết lập ngưỡng giới hạn thời gian (Timeout) tối đa 30 giây
            handler.postDelayed({
                if (!resolved) {
                    logcat { "Cloudflare resolution timeout!" }
                    handler.post { webView.destroy() }
                    latch.countDown()
                }
            }, 30000)
        }

        latch.await(35, TimeUnit.SECONDS) // Tạm ngưng luồng mạng (Network Thread) với ngưỡng tối đa 35 giây

        if (!resolved) {
            throw IOException("Không thể vượt qua Cloudflare Challenge. Hãy thử mở web bằng trình duyệt.")
        }

        // Khởi tạo yêu cầu mạng (Request) mới tích hợp chuỗi định danh (Cookie) vừa thu thập
        val cookies = CookieManager.getInstance().getCookie(originalRequest.url.toString())
        val newRequest = originalRequest.newBuilder()
            .header("User-Agent", newUserAgent)
            .header("Cookie", cookies ?: "")
            .build()

        return chain.proceed(newRequest)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        val webView = WebView(context)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = defaultUserAgent
        }
        return webView
    }
}
