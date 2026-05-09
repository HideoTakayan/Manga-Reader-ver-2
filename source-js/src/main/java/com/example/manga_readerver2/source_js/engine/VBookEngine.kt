package com.example.manga_readerver2.source_js.engine

import app.cash.quickjs.QuickJs
import logcat.LogPriority
import logcat.logcat
import okhttp3.OkHttpClient
import java.io.File
import org.jsoup.Jsoup
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Engine to run VBook JS extensions.
 * Optimized with Mutex for thread safety and Json for safe data bridging.
 */
class VBookEngine(
    private val client: OkHttpClient,
    private val srcDir: File
) {

    private val quickJs = QuickJs.create()
    private val environment = JsEnvironment(client)
    private val mutex = Mutex()

    init {
        setupBindings()
    }

    private fun setupBindings() {
        quickJs.set("AndroidApp", AndroidAppBridge::class.java, object : AndroidAppBridge {
            override fun fetch(url: String): String {
                val resp = environment.fetch(url)
                return Json.encodeToString(
                    buildJsonObject {
                        put("ok", resp.ok)
                        put("status", resp.status)
                        put("text", resp.text())
                    }
                )
            }

            override fun load(path: String): String {
                val file = File(srcDir, path)
                return if (file.exists()) file.readText() else ""
            }
            
            override fun jsoupParse(html: String): JsDocument {
                return environment.parseHtml(html)
            }
        })

        quickJs.evaluate("""
            function fetch(url) {
                var json = AndroidApp.fetch(url);
                var obj = JSON.parse(json);
                return {
                    ok: obj.ok,
                    status: obj.status,
                    text: function() { return obj.text; },
                    html: function() { 
                        return AndroidApp.jsoupParse(obj.text);
                    }
                };
            }
            
            function load(path) {
                var script = AndroidApp.load(path);
                if (script) {
                    eval(script);
                }
            }
            
            var Response = {
                success: function(data, next) {
                    return JSON.stringify({ data: data, next: next });
                },
                error: function(msg) {
                    return JSON.stringify({ error: msg });
                }
            };
        """.trimIndent())
    }

    // Fix BUG-11: quickJs.evaluate() là blocking call, phải dispatch sang Dispatchers.IO
    // tránh block coroutine thread (Main hoặc Default).
    suspend fun execute(script: String, functionName: String, vararg args: Any?): String? =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            mutex.withLock {
                try {
                    quickJs.evaluate(script)

                    val jsArgs = args.joinToString(", ") { arg ->
                        when (arg) {
                            is String -> Json.encodeToString(arg)
                            is Number -> arg.toString()
                            is Boolean -> arg.toString()
                            else -> "null"
                        }
                    }

                    val callScript = "$functionName($jsArgs);"
                    val result = quickJs.evaluate(callScript)

                    result?.toString()
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR) { "JS Error in $functionName: ${e.message}" }
                    null
                }
            }
        }

    fun close() {
        quickJs.close()
    }
    
    interface AndroidAppBridge {
        fun fetch(url: String): String
        fun load(path: String): String
        fun jsoupParse(html: String): JsDocument
    }
}
