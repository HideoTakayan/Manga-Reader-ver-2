package com.example.manga_readerver2.source_js.engine

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import logcat.logcat

/**
 * Provides host functions for QuickJS to interact with Android.
 * Cloned from VBook's bridge logic.
 */
class JsEnvironment(private val client: OkHttpClient) {

    fun fetch(url: String): JsResponse {
        return try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            JsResponse(
                ok = response.isSuccessful,
                status = response.code,
                content = response.body?.string() ?: ""
            )
        } catch (e: Exception) {
            logcat { "Fetch error: ${e.message}" }
            JsResponse(false, 500, "")
        }
    }

    fun parseHtml(html: String): JsDocument {
        return JsDocumentImpl(Jsoup.parse(html))
    }
}

class JsResponse(
    val ok: Boolean,
    val status: Int,
    private val content: String
) {
    fun text() = content
    fun html() = JsDocumentImpl(Jsoup.parse(content))
}

interface JsDocument {
    fun select(selector: String): Array<JsElement>
}

interface JsElement {
    fun text(): String
    fun attr(name: String): String
    fun select(selector: String): Array<JsElement>
}

class JsDocumentImpl(private val document: org.jsoup.nodes.Document) : JsDocument {
    override fun select(selector: String): Array<JsElement> {
        return document.select(selector).map { JsElementImpl(it) }.toTypedArray()
    }
}

class JsElementImpl(private val element: org.jsoup.nodes.Element) : JsElement {
    override fun text() = element.text()
    override fun attr(name: String) = element.attr(name)
    override fun select(selector: String): Array<JsElement> {
        return element.select(selector).map { JsElementImpl(it) }.toTypedArray()
    }
}
