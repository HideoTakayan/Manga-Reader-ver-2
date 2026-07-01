package com.example.manga_readerver2.source_js.engine

import app.cash.quickjs.QuickJs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import logcat.LogPriority
import logcat.logcat
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import java.io.File

class VBookEngine(
    private val client: OkHttpClient,
    private val rootDir: File,
) : AutoCloseable {

    private val quickJs = QuickJs.create()
    private val jsEnv = JsEnvironment(client)
    private val elementsMap = mutableMapOf<Int, org.jsoup.select.Elements>()
    private var nextId = 0
    private val mutex = Mutex()

    init {
        setupBindings()
    }

    private fun setupBindings() {
        quickJs.set("AndroidApp", AndroidAppBridge::class.java, object : AndroidAppBridge {
            override fun fetch(url: String, optionsJson: String): String {
                val options = parseRequestOptions(optionsJson)
                val response = jsEnv.fetch(url, options)
                return buildString {
                    append("{")
                    append("\"ok\":${response.ok},")
                    append("\"status\":${response.status},")
                    append("\"headers\":${Json.encodeToString(response.headers)},")
                    append("\"text\":${org.json.JSONObject.quote(response.text())}")
                    append("}")
                }
            }

            override fun fetchBase64(url: String, optionsJson: String): String {
                return try {
                    val options = parseRequestOptions(optionsJson)
                    val bytes = jsEnv.fetchBytes(url, options)
                    android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                } catch (e: Exception) {
                    ""
                }
            }

            override fun load(path: String): String {
                val srcFile = File(File(rootDir, "src"), path)
                if (srcFile.exists()) return srcFile.readText()

                val rootFile = File(rootDir, path)
                if (rootFile.exists()) return rootFile.readText()

                return ""
            }

            override fun jsoupParse(html: String, baseUri: String): Int {
                val doc = Jsoup.parse(html, baseUri)
                val id = nextId++
                elementsMap[id] = org.jsoup.select.Elements(doc)
                return id
            }

            override fun jsoupSelect(id: Int, selector: String): Int {
                val parent = elementsMap[id] ?: return -1
                val childId = nextId++
                elementsMap[childId] = parent.select(selector)
                return childId
            }

            override fun jsoupText(id: Int): String {
                return elementsMap[id]?.text() ?: ""
            }

            override fun jsoupHtml(id: Int): String {
                return elementsMap[id]?.html() ?: ""
            }

            override fun jsoupAttr(id: Int, name: String): String {
                val els = elementsMap[id] ?: return ""
                if (name == "src" && els.first()?.tagName() == "img") {
                    // Ưu tiên abs: version để resolve relative URL về absolute
                    // Filter startsWith("http") để loại bỏ data:image/... placeholder
                    val fallback = els.attr("abs:data-src").takeIf { it.startsWith("http") }
                        ?: els.attr("data-src").takeIf { it.isNotBlank() && !it.startsWith("data:") }
                        ?: els.attr("abs:data-original").takeIf { it.startsWith("http") }
                        ?: els.attr("data-original").takeIf { it.isNotBlank() && !it.startsWith("data:") }
                        ?: els.attr("abs:data-lazy-src").takeIf { it.startsWith("http") }
                        ?: els.attr("data-lazy-src").takeIf { it.isNotBlank() && !it.startsWith("data:") }
                    if (fallback != null) return fallback
                }
                if (name.equals("href", ignoreCase = true) || name.equals("src", ignoreCase = true)) {
                    val absUrl = els.attr("abs:$name")
                    if (absUrl.isNotEmpty()) return absUrl
                }
                return els.attr(name)
            }

            override fun jsoupRemove(id: Int) {
                elementsMap[id]?.remove()
            }

            private fun createEmptyElements(): Int {
                val childId = nextId++
                elementsMap[childId] = org.jsoup.select.Elements()
                return childId
            }

            override fun jsoupFirst(id: Int): Int {
                val parent = elementsMap[id] ?: return createEmptyElements()
                val first = parent.first() ?: return createEmptyElements()
                val childId = nextId++
                elementsMap[childId] = org.jsoup.select.Elements(first)
                return childId
            }

            override fun jsoupLast(id: Int): Int {
                val parent = elementsMap[id] ?: return createEmptyElements()
                val last = parent.last() ?: return createEmptyElements()
                val childId = nextId++
                elementsMap[childId] = org.jsoup.select.Elements(last)
                return childId
            }

            override fun jsoupSize(id: Int): Int {
                return elementsMap[id]?.size ?: 0
            }

            override fun jsoupGet(id: Int, index: Int): Int {
                val parent = elementsMap[id] ?: return createEmptyElements()
                val element = parent.getOrNull(index) ?: return createEmptyElements()
                val childId = nextId++
                elementsMap[childId] = org.jsoup.select.Elements(element)
                return childId
            }

            override fun sleep(ms: Int) {
                Thread.sleep(ms.toLong().coerceAtLeast(0L))
            }
        })

        quickJs.evaluate(
            """
            // Graphics stub – desktop-only API, not available on Android
            var Graphics = {
                createImage: function(base64) {
                    return { width: 0, height: 0, _base64: base64 };
                },
                createCanvas: function(w, h) {
                    return {
                        width: w, height: h, _parts: [],
                        drawImage: function(img, sx, sy, sw, sh, dx, dy, dw, dh) {
                            this._parts.push({ img: img, sx: sx, sy: sy, sw: sw, sh: sh, dx: dx, dy: dy, dw: dw, dh: dh });
                        },
                        capture: function() { return null; }
                    };
                }
            };

            function wrapJsElement(id) {
                if (id < 0) return null;
                var wrapper = {
                    text: function() { return AndroidApp.jsoupText(id); },
                    html: function() { return AndroidApp.jsoupHtml(id); },
                    attr: function(name) { return AndroidApp.jsoupAttr(id, name); },
                    select: function(selector) { return wrapJsElement(AndroidApp.jsoupSelect(id, selector)); },
                    remove: function() { AndroidApp.jsoupRemove(id); },
                    first: function() { return wrapJsElement(AndroidApp.jsoupFirst(id)); },
                    last: function() { return wrapJsElement(AndroidApp.jsoupLast(id)); },
                    size: function() { return AndroidApp.jsoupSize(id); },
                    get: function(index) { return wrapJsElement(AndroidApp.jsoupGet(id, index)); },
                    forEach: function(callback) {
                        var size = AndroidApp.jsoupSize(id);
                        for (var i = 0; i < size; i++) {
                            callback(wrapJsElement(AndroidApp.jsoupGet(id, i)), i);
                        }
                    },
                    get length() { return AndroidApp.jsoupSize(id); }
                };

                return new Proxy(wrapper, {
                    get: function(target, prop) {
                        if (typeof prop === "string" && /^\d+$/.test(prop)) {
                            var index = parseInt(prop, 10);
                            if (index >= 0 && index < AndroidApp.jsoupSize(id)) {
                                return wrapJsElement(AndroidApp.jsoupGet(id, index));
                            }
                            return undefined;
                        }
                        return target[prop];
                    }
                });
            }

            function normalizeUrl(url) {
                if (url && url.startsWith("/") && typeof BASE_URL !== "undefined") {
                    return BASE_URL + url;
                }
                return url;
            }

            function fetch(url, options) {
                url = normalizeUrl(url);
                var _opts = JSON.stringify(options || {});
                var raw = AndroidApp.fetch(url, _opts);
                var obj = JSON.parse(raw);
                return {
                    ok: obj.ok,
                    status: obj.status,
                    headers: obj.headers || {},
                    text: function() { return obj.text; },
                    html: function() { return wrapJsElement(AndroidApp.jsoupParse(obj.text, url)); },
                    json: function() { return JSON.parse(obj.text); },
                    base64: function() { return AndroidApp.fetchBase64(url, _opts); }
                };
            }

            function load(path) {
                var script = AndroidApp.load(path);
                if (script) {
                    eval(script);
                }
            }

            function createHttpRequest(method, url) {
                var options = { method: method || "GET" };
                return {
                    headers: function(headers) {
                        options.headers = Object.assign({}, options.headers || {}, headers || {});
                        return this;
                    },
                    params: function(params) {
                        options.queries = Object.assign({}, options.queries || {}, params || {});
                        return this;
                    },
                    body: function(body) {
                        options.body = body;
                        return this;
                    },
                    submit: function() {
                        return fetch(url, options);
                    },
                    text: function() {
                        return fetch(url, options).text();
                    },
                    string: function() {
                        return fetch(url, options).text();
                    },
                    html: function() {
                        return fetch(url, options).html();
                    },
                    json: function() {
                        return fetch(url, options).json();
                    }
                };
            }

            var Response = {
                success: function(data, next) {
                    return JSON.stringify({ data: data, next: next });
                },
                error: function(msg) {
                    return JSON.stringify({ error: msg });
                }
            };

            var Http = {
                get: function(url) { return createHttpRequest("GET", url); },
                post: function(url, options) {
                    var request = createHttpRequest("POST", url);
                    if (options && options.headers) request.headers(options.headers);
                    if (options && options.queries) request.params(options.queries);
                    if (options && options.body !== undefined) request.body(options.body);
                    return request;
                },
                put: function(url) { return createHttpRequest("PUT", url); },
                delete: function(url) { return createHttpRequest("DELETE", url); },
                patch: function(url) { return createHttpRequest("PATCH", url); }
            };

            var Console = {
                log: function(msg) { }
            };
            var console = Console;

            var UserAgent = {
                chrome: function() { return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"; },
                android: function() { return "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"; },
                system: function() { return "okhttp/4.12.0"; }
            };

            function sleep(ms) {
                AndroidApp.sleep(Math.max(0, ms | 0));
            }

            var Html = {
                parse: function(htmlStr) {
                    return wrapJsElement(AndroidApp.jsoupParse(htmlStr, ""));
                },
                clean: function(htmlStr) {
                    return htmlStr;
                }
            };
            """.trimIndent(),
        )
    }

    private fun parseRequestOptions(optionsJson: String): JsRequestOptions {
        if (optionsJson.isBlank()) return JsRequestOptions()
        return try {
            val obj = Json.parseToJsonElement(optionsJson) as? JsonObject ?: return JsRequestOptions()
            JsRequestOptions(
                method = obj["method"]?.jsonPrimitiveOrNull()?.content ?: "GET",
                headers = obj["headers"].jsonObjectOrNull()
                    ?.mapValues { it.value.jsonPrimitiveOrNull()?.content ?: it.value.toString() }
                    ?: emptyMap(),
                queries = obj["queries"].jsonObjectOrNull()
                    ?.mapValues { it.value.jsonPrimitiveOrNull()?.content ?: it.value.toString() }
                    ?: emptyMap(),
                body = obj["body"],
            )
        } catch (_: Exception) {
            JsRequestOptions()
        }
    }

    private fun JsonElement?.jsonObjectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonElement?.jsonPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive

    private fun preprocessScript(script: String, rootDir: File): String {
        val loadRegex = Regex("""load\(['"](.*?)['"]\);?""")
        var result = script
        var match = loadRegex.find(result)
        var maxDepth = 10

        while (match != null && maxDepth > 0) {
            val path = match.groupValues[1]
            val srcFile = File(File(rootDir, "src"), path)
            val rootFile = File(rootDir, path)

            val content = when {
                srcFile.exists() -> srcFile.readText()
                rootFile.exists() -> rootFile.readText()
                else -> ""
            }

            result = result.replace(match.value, content)
            match = loadRegex.find(result)
            maxDepth--
        }
        return result
    }

    suspend fun execute(script: String, functionName: String, vararg args: Any?): String? =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            mutex.withLock {
                try {
                    val finalScript = preprocessScript(script, rootDir)

                    val jsArgs = args.joinToString(", ") { arg ->
                        when (arg) {
                            is String -> Json.encodeToString(arg)
                            is Number -> arg.toString()
                            is Boolean -> arg.toString()
                            else -> "null"
                        }
                    }

                    val callScript = """
                        (function() {
                            $finalScript
                            if (typeof $functionName === 'function') {
                                return $functionName($jsArgs);
                            } else {
                                return null;
                            }
                        })();
                    """.trimIndent()

                    quickJs.evaluate(callScript)?.toString()
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR) { "JS Error in $functionName: ${e.message}" }
                    null
                }
            }
        }

    override fun close() {
        quickJs.close()
    }

    interface AndroidAppBridge {
        fun fetch(url: String, optionsJson: String): String
        fun fetchBase64(url: String, optionsJson: String): String
        fun load(path: String): String
        fun jsoupParse(html: String, baseUri: String): Int
        fun jsoupSelect(id: Int, selector: String): Int
        fun jsoupText(id: Int): String
        fun jsoupHtml(id: Int): String
        fun jsoupAttr(id: Int, name: String): String
        fun jsoupRemove(id: Int)
        fun jsoupFirst(id: Int): Int
        fun jsoupLast(id: Int): Int
        fun jsoupSize(id: Int): Int
        fun jsoupGet(id: Int, index: Int): Int
        fun sleep(ms: Int)
    }
}
