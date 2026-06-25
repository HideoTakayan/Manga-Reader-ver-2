package com.example.manga_readerver2.source_js

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import com.example.manga_readerver2.source_js.engine.VBookEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * A Source implementation that delegates to a VBook JS script.
 * This makes JS sources look exactly like Mihon DEX sources to the rest of the app.
 */
class JsSource(
    override val id: Long,
    override val name: String,
    override val lang: String,
    private val engine: VBookEngine,
    private val scripts: Map<String, String>, // Function name to Script content
    val isNovel: Boolean = true // VBook default is novel
) : CatalogueSource {

    override val supportsLatest: Boolean = scripts.containsKey("home")

    private val json = Json { ignoreUnknownKeys = true }

    val headers: okhttp3.Headers = okhttp3.Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .build()

    /**
     * Giải phóng native QuickJS instance.
     * Gọi khi extension bị unload hoặc reload để tránh leak native heap.
     */
    fun closeEngine() {
        try { engine.close() } catch (_: Exception) {}
    }

    override suspend fun getMangaDetails(manga: SManga): SManga {
        val script = scripts["detail"] ?: return manga
        val result = engine.execute(script, "execute", manga.url) ?: return manga
        
        return try {
            val jsonResult = json.parseToJsonElement(result).jsonObject
            val data = jsonResult["data"]?.jsonObject ?: return manga
            
            manga.apply {
                author = data["author"]?.jsonPrimitive?.content ?: manga.author
                artist = data["artist"]?.jsonPrimitive?.content ?: author
                description = data["description"]?.jsonPrimitive?.content ?: manga.description
                
                // Parse genres array or tag
                val genresArray = data["genres"]?.asJsonArray()
                val tagsStr = if (genresArray != null) {
                    genresArray.mapNotNull {
                        if (it is kotlinx.serialization.json.JsonObject) {
                            it["title"]?.jsonPrimitive?.content
                        } else if (it is kotlinx.serialization.json.JsonPrimitive && it.isString) {
                            it.content
                        } else null
                    }.joinToString(", ")
                } else {
                    data["tag"]?.jsonPrimitive?.content
                }
                genre = tagsStr ?: manga.genre
                
                status = when(data["status"]?.jsonPrimitive?.content?.lowercase()) {
                    "ongoing" -> 1
                    "completed" -> 2
                    else -> 0
                }
            }
        } catch (e: Exception) {
            manga
        }
    }

    override suspend fun getChapterList(manga: SManga): List<SChapter> {
        val script = scripts["toc"] ?: return emptyList()
        val result = engine.execute(script, "execute", manga.url) ?: return emptyList()
        
        return try {
            val jsonResult = json.parseToJsonElement(result).jsonObject
            // VBook toc.js returns array directly in data
            val chaptersJson = jsonResult["data"]?.asJsonArray() ?: return emptyList()
            
            chaptersJson.map { it.jsonObject }.map {
                SChapter.create().apply {
                    url = it["url"]?.jsonPrimitive?.content ?: ""
                    name = it["name"]?.jsonPrimitive?.content ?: ""
                    date_upload = it["date"]?.jsonPrimitive?.longOrNull ?: 0L
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val script = scripts["chap"] ?: return emptyList()
        val result = engine.execute(script, "execute", chapter.url) ?: return emptyList()

        return try {
            val jsonResult = json.parseToJsonElement(result).jsonObject
            val data = jsonResult["data"]?.jsonObject ?: return emptyList()

            // Case 1: Truyện tranh — trả về mảng ảnh
            val pagesArray = data["pages"]?.asJsonArray()
            if (pagesArray != null && pagesArray.isNotEmpty()) {
                return pagesArray.mapIndexed { index, element ->
                    Page(index, "", element.jsonPrimitive.content)
                }
            }

            // Case 2: Truyện chữ (VBook novel) — trả về nội dung HTML/text trong một field
            val textContent = data["content"]?.jsonPrimitive?.content
                ?: data["text"]?.jsonPrimitive?.content
                ?: data["html"]?.jsonPrimitive?.content

            if (textContent != null) {
                // Đặt nội dung text vào imageUrl của Page[0] — ReaderScreenModel detect HTML/text từ đây
                return listOf(Page(0, chapter.url, "vbook-text://$textContent"))
            }

            // Case 3: Fallback — nếu kết quả là string thẳng (chứa HTML)
            val rawContent = jsonResult["data"]?.jsonPrimitive?.content
            if (rawContent != null) {
                return listOf(Page(0, chapter.url, "vbook-text://$rawContent"))
            }

            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val script = scripts["home"] ?: return MangasPage(emptyList(), false)
        val homeResult = engine.execute(script, "execute", page.toString()) ?: return MangasPage(emptyList(), false)

        return try {
            val jsonResult = json.parseToJsonElement(homeResult).jsonObject
            val data = jsonResult["data"]?.asJsonArray() ?: return MangasPage(emptyList(), false)
            
            if (data.isEmpty()) return MangasPage(emptyList(), false)

            // VBook home.js returns an array of Tabs. Take the first tab to use as "Popular"
            val firstTab = data[0].jsonObject
            val tabInput = firstTab["input"]?.jsonPrimitive?.content
            val tabScriptName = firstTab["script"]?.jsonPrimitive?.content

            if (tabInput != null && tabScriptName != null) {
                // Execute the tab script
                val tabScript = scripts[tabScriptName.substringBeforeLast(".")] ?: return MangasPage(emptyList(), false)
                val tabResult = engine.execute(tabScript, "execute", tabInput, page.toString()) ?: return MangasPage(emptyList(), false)
                
                val tabJsonResult = json.parseToJsonElement(tabResult).jsonObject
                val mangasJson = tabJsonResult["data"]?.asJsonArray() ?: return MangasPage(emptyList(), false)
                val hasNext = tabJsonResult["next"] != null && tabJsonResult["next"] !is kotlinx.serialization.json.JsonNull

                val mangas = mangasJson.map { it.jsonObject }.map { o ->
                    SManga.create().apply {
                        url = o["link"]?.jsonPrimitive?.content ?: o["url"]?.jsonPrimitive?.content ?: ""
                        title = o["name"]?.jsonPrimitive?.content ?: o["title"]?.jsonPrimitive?.content ?: ""
                        thumbnail_url = o["cover"]?.jsonPrimitive?.content ?: ""
                    }
                }
                return MangasPage(mangas, hasNext)
            } else {
                // Fallback if home.js returned manga list directly
                val mangas = data.map { it.jsonObject }.map { o ->
                    SManga.create().apply {
                        url = o["link"]?.jsonPrimitive?.content ?: o["url"]?.jsonPrimitive?.content ?: ""
                        title = o["name"]?.jsonPrimitive?.content ?: o["title"]?.jsonPrimitive?.content ?: ""
                        thumbnail_url = o["cover"]?.jsonPrimitive?.content ?: ""
                    }
                }
                val hasNext = jsonResult["next"] != null && jsonResult["next"] !is kotlinx.serialization.json.JsonNull
                return MangasPage(mangas, hasNext)
            }
        } catch (e: Exception) {
            MangasPage(emptyList(), false)
        }
    }

    private fun kotlinx.serialization.json.JsonElement.asJsonArray() = this.let {
        if (it is kotlinx.serialization.json.JsonArray) it else emptyList<kotlinx.serialization.json.JsonElement>()
    }

    override suspend fun getSearchManga(page: Int, query: String, filters: eu.kanade.tachiyomi.source.model.FilterList): MangasPage {
        val script = scripts["search"] ?: return MangasPage(emptyList(), false)
        val result = engine.execute(script, "execute", query, page.toString()) ?: return MangasPage(emptyList(), false)

        return try {
            val jsonResult = json.parseToJsonElement(result).jsonObject
            val mangasJson = jsonResult["data"]?.asJsonArray() ?: return MangasPage(emptyList(), false)
            val hasNext = jsonResult["next"] != null && jsonResult["next"] !is kotlinx.serialization.json.JsonNull

            val mangas = mangasJson.map { j -> j.jsonObject }.map { o ->
                SManga.create().apply {
                    url = o["link"]?.jsonPrimitive?.content ?: o["url"]?.jsonPrimitive?.content ?: ""
                    title = o["name"]?.jsonPrimitive?.content ?: o["title"]?.jsonPrimitive?.content ?: ""
                    thumbnail_url = o["cover"]?.jsonPrimitive?.content ?: ""
                }
            }
            MangasPage(mangas, hasNext)
        } catch (e: Exception) {
            MangasPage(emptyList(), false)
        }
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        // Use getPopularManga logic but default to the second tab (usually "Mới Cập Nhật") if it exists
        val script = scripts["home"] ?: return MangasPage(emptyList(), false)
        val homeResult = engine.execute(script, "execute", page.toString()) ?: return MangasPage(emptyList(), false)

        return try {
            val jsonResult = json.parseToJsonElement(homeResult).jsonObject
            val data = jsonResult["data"]?.asJsonArray() ?: return MangasPage(emptyList(), false)
            
            if (data.isEmpty()) return MangasPage(emptyList(), false)

            // Try to use the second tab for "Latest", fallback to first tab
            val tabToUse = if (data.size > 1) data[1].jsonObject else data[0].jsonObject
            val tabInput = tabToUse["input"]?.jsonPrimitive?.content
            val tabScriptName = tabToUse["script"]?.jsonPrimitive?.content

            if (tabInput != null && tabScriptName != null) {
                val tabScript = scripts[tabScriptName.substringBeforeLast(".")] ?: return MangasPage(emptyList(), false)
                val tabResult = engine.execute(tabScript, "execute", tabInput, page.toString()) ?: return MangasPage(emptyList(), false)
                
                val tabJsonResult = json.parseToJsonElement(tabResult).jsonObject
                val mangasJson = tabJsonResult["data"]?.asJsonArray() ?: return MangasPage(emptyList(), false)
                val hasNext = tabJsonResult["next"] != null && tabJsonResult["next"] !is kotlinx.serialization.json.JsonNull

                val mangas = mangasJson.map { it.jsonObject }.map { o ->
                    SManga.create().apply {
                        url = o["link"]?.jsonPrimitive?.content ?: o["url"]?.jsonPrimitive?.content ?: ""
                        title = o["name"]?.jsonPrimitive?.content ?: o["title"]?.jsonPrimitive?.content ?: ""
                        thumbnail_url = o["cover"]?.jsonPrimitive?.content ?: ""
                    }
                }
                return MangasPage(mangas, hasNext)
            } else {
                return MangasPage(emptyList(), false)
            }
        } catch (e: Exception) {
            MangasPage(emptyList(), false)
        }
    }
}
