package com.example.manga_readerver2.core.utils

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

sealed class ReaderBlock {
    data class Text(val text: String) : ReaderBlock()
    data class Image(val url: String, val altText: String = "") : ReaderBlock()
}

object HtmlParser {
    /**
     * Parse HTML content thành danh sách các block (Text/Image).
     * Xử lý đúng cả nội dung truyện chữ lẫn ảnh minh họa.
     * Input: HTML string (có thể là raw HTML hoặc plain text)
     */
    fun parseToBlocks(html: String, baseUri: String = ""): List<ReaderBlock> {
        // Trả về trực tiếp văn bản thuần túy (plain text) nếu không phát hiện các thẻ HTML
        if (!html.contains('<')) {
            return html.split("\n")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { ReaderBlock.Text(it) }
        }
        
        val blocks = mutableListOf<ReaderBlock>()
        val doc = Jsoup.parseBodyFragment(html, baseUri)
        val currentText = StringBuilder()
        
        fun flushText() {
            val t = currentText.toString().replace(Regex("[ \\t]+"), " ").trim()
            if (t.isNotBlank()) {
                blocks.add(ReaderBlock.Text(t))
            }
            currentText.clear()
        }
        
        fun getImageUrl(el: Element): String {
            val fallback = el.attr("abs:data-src").takeIf { it.startsWith("http") }
                ?: el.attr("data-src").takeIf { it.isNotBlank() && !it.startsWith("data:") }
                ?: el.attr("abs:data-original").takeIf { it.startsWith("http") }
                ?: el.attr("data-original").takeIf { it.isNotBlank() && !it.startsWith("data:") }
                ?: el.attr("abs:data-lazy-src").takeIf { it.startsWith("http") }
                ?: el.attr("data-lazy-src").takeIf { it.isNotBlank() && !it.startsWith("data:") }
                ?: el.attr("abs:src").takeIf { it.startsWith("http") }
                ?: el.attr("src").takeIf { it.isNotBlank() && !it.startsWith("data:") }
            return fallback ?: ""
        }
        
        fun traverse(node: Node) {
            when {
                // Khởi tạo khối hình ảnh từ thẻ img
                node is Element && node.tagName() == "img" -> {
                    flushText()
                    val src = getImageUrl(node)
                    val alt = node.attr("alt")
                    if (src.isNotBlank()) {
                        blocks.add(ReaderBlock.Image(src, alt))
                    }
                }
                // Nhận diện thẻ br để xử lý ngắt đoạn
                node.nodeName() == "br" -> {
                    // Đánh dấu ký tự ngắt dòng (\n) vào bộ đệm văn bản
                    currentText.append("\n")
                }
                // Phân tích và thu thập nội dung từ Text Node
                node is TextNode -> {
                    currentText.append(node.wholeText)
                }
                // Xử lý đệ quy các thẻ con khác
                node is Element -> {
                    val isBlock = node.isBlock
                    if (isBlock) flushText()
                    for (child in node.childNodes()) {
                        traverse(child)
                    }
                    if (isBlock) flushText()
                }
            }
        }
        
        for (child in doc.body().childNodes()) {
            traverse(child)
        }
        
        flushText()
        
        return blocks
    }
    
    /**
     * Trích xuất toàn bộ text sạch từ HTML (dùng cho TTS)
     */
    fun extractCleanText(html: String): String {
        if (!html.contains('<')) return html
        return Jsoup.parseBodyFragment(html).body().text()
    }
}
