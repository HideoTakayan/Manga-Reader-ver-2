package com.example.manga_readerver2.core.utils

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class AndroidCookieJar : CookieJar {
    private val manager = CookieManager.getInstance()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val urlString = url.toString()
        for (cookie in cookies) {
            manager.setCookie(urlString, cookie.toString())
        }
        manager.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val urlString = url.toString()
        val cookiesString = manager.getCookie(urlString)
        if (cookiesString != null && cookiesString.isNotEmpty()) {
            return cookiesString.split(";").mapNotNull {
                Cookie.parse(url, it)
            }
        }
        return emptyList()
    }
}
