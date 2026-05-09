package com.example.manga_readerver2.core.utils

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Công cụ hỗ trợ đọc nội dung văn bản và metadata từ file EPUB.
 */
object EpubReader {

    data class EpubMetadata(
        val title: String? = null,
        val author: String? = null,
        val description: String? = null,
        val coverPath: String? = null,
        val opfPath: String? = null
    )

    data class TocEntry(
        val title: String,
        val href: String // Path relative to EPUB root (already resolved)
    )

    /**
     * Trích xuất mục lục từ file EPUB, theo đúng thứ tự spine của OPF.
     */
    fun getToc(file: File): List<TocEntry> {
        val toc = mutableListOf<TocEntry>()
        try {
            ZipFile(file).use { zip ->
                val meta = getMetadata(file)
                val opfPath = meta.opfPath ?: return emptyList()
                val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""

                val opfEntry = zip.getEntry(opfPath) ?: return emptyList()
                val opfXml = zip.getInputStream(opfEntry).bufferedReader().use { it.readText() }
                val opfDoc = Jsoup.parse(opfXml, "", Parser.xmlParser())

                // 1. Build manifest: id -> href (resolved to root)
                val manifest = mutableMapOf<String, String>()
                opfDoc.select("item").forEach { item ->
                    val id = item.attr("id")
                    val href = item.attr("href")
                    if (id.isNotEmpty() && href.isNotEmpty()) {
                        manifest[id] = resolveHref(opfDir, href)
                    }
                }

                // 2. Thử tìm NCX (EPUB 2)
                val ncxId = opfDoc.select("spine").attr("toc")
                val ncxHref = manifest[ncxId]

                if (ncxHref != null) {
                    val ncxEntry = zip.getEntry(ncxHref)
                    if (ncxEntry != null) {
                        val ncxXml = zip.getInputStream(ncxEntry).bufferedReader().use { it.readText() }
                        val ncxDoc = Jsoup.parse(ncxXml, "", Parser.xmlParser())
                        val ncxDir = if (ncxHref.contains("/")) ncxHref.substringBeforeLast("/") + "/" else ""
                        ncxDoc.select("navPoint").forEach { nav ->
                            val label = nav.select("navLabel > text").first()?.text()
                            val src = nav.select("content").first()?.attr("src")
                            if (label != null && src != null) {
                                // src là relative tới NCX dir → resolve về root
                                val resolved = resolveHref(ncxDir, src)
                                toc.add(TocEntry(label, resolved))
                            }
                        }
                    }
                }

                // 3. Nếu NCX trống, tìm Nav (EPUB 3)
                if (toc.isEmpty()) {
                    val navItem = opfDoc.select("item[properties*=nav]").first()
                    val navHrefRaw = navItem?.attr("href")
                    if (navHrefRaw != null) {
                        val navHref = resolveHref(opfDir, navHrefRaw)
                        val navDir = if (navHref.contains("/")) navHref.substringBeforeLast("/") + "/" else ""
                        val navEntry = zip.getEntry(navHref)
                        if (navEntry != null) {
                            val navHtml = zip.getInputStream(navEntry).bufferedReader().use { it.readText() }
                            val navDoc = Jsoup.parse(navHtml)
                            navDoc.select("nav[epub:type=toc] a, nav#toc a, nav.toc a").forEach { a ->
                                val label = a.text()
                                val href = a.attr("href")
                                if (label.isNotEmpty() && href.isNotEmpty()) {
                                    val resolved = resolveHref(navDir, href)
                                    toc.add(TocEntry(label, resolved))
                                }
                            }
                        }
                    }
                }

                // 4. Fallback: dùng spine order nếu cả NCX và Nav đều không có
                if (toc.isEmpty()) {
                    opfDoc.select("spine > itemref").forEachIndexed { index, itemref ->
                        val idref = itemref.attr("idref")
                        val href = manifest[idref]
                        if (href != null) {
                            val title = "Chương ${index + 1}"
                            toc.add(TocEntry(title, href))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return toc
    }

    /**
     * Lấy nội dung văn bản của một file cụ thể trong EPUB.
     * hrefWithAnchor là đường dẫn đã resolved về root EPUB (có thể có #anchor).
     */
    fun getChapterText(file: File, hrefWithAnchor: String): String {
        return try {
            // Fix BUG-EPUB: Decode URL (e.g. %20 -> " ") vì ZipFile.getEntry cần raw string
            val decodedHref = try {
                java.net.URLDecoder.decode(hrefWithAnchor, "UTF-8")
            } catch (e: Exception) {
                hrefWithAnchor
            }
            
            val path = decodedHref.substringBefore("#")
            ZipFile(file).use { zip ->
                // 1. Thử tìm entry trực tiếp (exact match)
                var entry = zip.getEntry(path)
                
                // 2. Nếu không thấy, thử tìm theo suffix (xử lý relative path hoặc sai khác OEBPS/Text/...)
                if (entry == null) {
                    entry = zip.entries().asSequence().find { 
                        it.name.endsWith(path, ignoreCase = true) || path.endsWith(it.name, ignoreCase = true)
                    }
                }

                if (entry == null) {
                    return "Không tìm thấy nội dung chương: $path"
                }

                val html = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                htmlToText(html)
            }
        } catch (e: Exception) {
            "Lỗi khi đọc chương: ${e.message}"
        }
    }

    /**
     * Lấy toàn bộ nội dung văn bản của file EPUB, theo đúng thứ tự spine OPF.
     */
    fun getFullText(file: File): String {
        if (!file.exists()) return "File không tồn tại."

        val textBuilder = StringBuilder()
        try {
            ZipFile(file).use { zip ->
                // Đọc OPF để lấy spine order
                val meta = getMetadata(file)
                val opfPath = meta.opfPath

                if (opfPath != null) {
                    val opfEntry = zip.getEntry(opfPath)
                    if (opfEntry != null) {
                        val opfXml = zip.getInputStream(opfEntry).bufferedReader().use { it.readText() }
                        val opfDoc = Jsoup.parse(opfXml, "", Parser.xmlParser())
                        val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""

                        // Build manifest id -> href
                        val manifest = mutableMapOf<String, String>()
                        opfDoc.select("item").forEach { item ->
                            val id = item.attr("id")
                            val href = item.attr("href")
                            val mediaType = item.attr("media-type")
                            if (id.isNotEmpty() && href.isNotEmpty() &&
                                (mediaType.contains("html") || mediaType.contains("xhtml") || href.endsWith(".html") || href.endsWith(".xhtml"))
                            ) {
                                manifest[id] = resolveHref(opfDir, href)
                            }
                        }

                        // Đọc theo thứ tự spine
                        val spineItems = opfDoc.select("spine > itemref")
                        var hasContent = false
                        spineItems.forEach { itemref ->
                            val idref = itemref.attr("idref")
                            val rawHref = manifest[idref] ?: return@forEach
                            
                            val href = try {
                                java.net.URLDecoder.decode(rawHref, "UTF-8")
                            } catch (e: Exception) {
                                rawHref
                            }
                            
                            var entry = zip.getEntry(href)
                            if (entry == null) {
                                entry = zip.entries().asSequence().find { 
                                    !it.isDirectory && (it.name.endsWith(href, ignoreCase = true) || href.endsWith(it.name, ignoreCase = true))
                                }
                            }
                            if (entry == null) return@forEach
                            
                            val html = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                            val text = htmlToText(html)
                            if (text.isNotBlank()) {
                                textBuilder.append(text).append("\n\n")
                                hasContent = true
                            } else {
                                // Fallback Jsoup raw text if our extraction yielded blank
                                val rawText = org.jsoup.Jsoup.parse(html).text()
                                if (rawText.isNotBlank()) {
                                    textBuilder.append(rawText).append("\n\n")
                                    hasContent = true
                                }
                            }
                        }

                        if (hasContent) return textBuilder.toString()
                    }
                }

                // Fallback: đọc tất cả HTML/XHTML theo alphabet nếu không parse được OPF
                val htmlEntries = zip.entries().asSequence()
                    .filter { !it.isDirectory && (it.name.endsWith(".html", ignoreCase = true) || it.name.endsWith(".xhtml", ignoreCase = true)) }
                    .sortedBy { it.name }
                    .toList()

                for (entry in htmlEntries) {
                    val html = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    val text = htmlToText(html)
                    if (text.isNotBlank()) {
                        textBuilder.append(text).append("\n\n")
                    }
                }
            }
        } catch (e: Exception) {
            return "Lỗi: ${e.message}"
        }
        return textBuilder.toString()
    }

    /**
     * Chuyển HTML sang plain text, giữ nguyên cấu trúc đoạn văn.
     */
    private fun htmlToText(html: String): String {
        val doc = Jsoup.parse(html)
        doc.select("script, style, nav").remove()

        // Thêm newline sau các block elements để tách đoạn
        doc.select("p, div, li, h1, h2, h3, h4, h5, h6, tr").forEach { el ->
            el.appendText("\n")
        }
        doc.select("br").forEach { el ->
            el.replaceWith(org.jsoup.nodes.TextNode("\n"))
        }

        val body = doc.body()
        return body.wholeText()
            .lines()
            .map { it.trim() }
            .joinToString("\n") { it }
            .replace(Regex("\n{3,}"), "\n\n")  // Gộp nhiều dòng trống thành 1
            .trim()
    }

    /**
     * Resolve đường dẫn relative theo base dir.
     * baseDir phải kết thúc bằng "/" hoặc rỗng.
     */
    private fun resolveHref(baseDir: String, href: String): String {
        // Bỏ qua anchor khi resolve path
        val path = href.substringBefore("#")
        val anchor = href.substringAfter("#", "")

        val resolved = when {
            path.startsWith("/") -> path.trimStart('/')
            path.contains("../") -> {
                // Xử lý ../
                val parts = (baseDir + path).split("/").toMutableList()
                val result = mutableListOf<String>()
                for (part in parts) {
                    if (part == "..") {
                        if (result.isNotEmpty()) result.removeAt(result.size - 1)
                    } else if (part != ".") {
                        result.add(part)
                    }
                }
                result.joinToString("/")
            }
            else -> baseDir + path
        }

        return if (anchor.isNotEmpty()) "$resolved#$anchor" else resolved
    }

    /**
     * Trích xuất metadata từ file EPUB.
     */
    fun getMetadata(file: File): EpubMetadata {
        try {
            ZipFile(file).use { zip ->
                val containerEntry = zip.getEntry("META-INF/container.xml") ?: return EpubMetadata()
                val containerXml = zip.getInputStream(containerEntry).bufferedReader().use { it.readText() }
                val containerDoc = Jsoup.parse(containerXml, "", Parser.xmlParser())
                val opfPath = containerDoc.select("rootfile").attr("full-path")

                if (opfPath.isEmpty()) return EpubMetadata()

                val opfEntry = zip.getEntry(opfPath) ?: return EpubMetadata()
                val opfXml = zip.getInputStream(opfEntry).bufferedReader().use { it.readText() }
                val opfDoc = Jsoup.parse(opfXml, "", Parser.xmlParser())

                val title = opfDoc.select("dc|title").text()
                val author = opfDoc.select("dc|creator").text()
                val description = opfDoc.select("dc|description").text()

                var coverId = opfDoc.select("meta[name=cover]").attr("content")
                if (coverId.isEmpty()) coverId = opfDoc.select("item[properties*=cover]").attr("id")

                var coverPath: String? = null
                if (coverId.isNotEmpty()) {
                    val coverItem = opfDoc.select("item[id=$coverId]").first()
                    if (coverItem != null) {
                        val href = coverItem.attr("href")
                        val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""
                        coverPath = opfDir + href
                    }
                }

                if (coverPath == null) {
                    coverPath = zip.entries().asSequence()
                        .find { it.name.lowercase().contains("cover") && (it.name.endsWith(".jpg") || it.name.endsWith(".png")) }
                        ?.name
                }

                return EpubMetadata(title, author, description, coverPath, opfPath)
            }
        } catch (e: Exception) {
            return EpubMetadata()
        }
    }

    /**
     * Lấy dữ liệu ảnh bìa.
     */
    fun getCoverBytes(file: File, coverInternalPath: String): ByteArray? {
        return try {
            ZipFile(file).use { zip ->
                zip.getInputStream(zip.getEntry(coverInternalPath)).use { it.readBytes() }
            }
        } catch (e: Exception) {
            null
        }
    }
}
