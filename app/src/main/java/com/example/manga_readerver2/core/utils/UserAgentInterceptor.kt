package com.example.manga_readerver2.core.utils

import okhttp3.Interceptor
import okhttp3.Response

class UserAgentInterceptor : Interceptor {
    private val defaultUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        // Chỉ thêm User-Agent nếu yêu cầu chưa có sẵn Header này
        if (originalRequest.header("User-Agent") != null) {
            return chain.proceed(originalRequest)
        }

        val requestWithUserAgent = originalRequest.newBuilder()
            .header("User-Agent", defaultUserAgent)
            .build()
        return chain.proceed(requestWithUserAgent)
    }
}
