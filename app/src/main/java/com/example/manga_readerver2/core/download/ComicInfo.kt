package com.example.manga_readerver2.core.download

import com.example.manga_readerver2.domain.model.Chapter
import com.example.manga_readerver2.domain.model.Manga
import java.io.File

object ComicInfo {
    
    fun generate(manga: Manga, chapter: Chapter, sourceName: String): String {
        val series = escapeXml(manga.title)
        val title = escapeXml(chapter.name)
        val summary = escapeXml(manga.description ?: "")
        val writer = escapeXml(manga.author ?: "")
        val penciller = escapeXml(manga.artist ?: "")
        val genre = escapeXml(manga.genre?.joinToString(", ") ?: "")
        val number = chapter.chapterNumber.takeIf { it >= 0 }?.toString() ?: ""
        
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <ComicInfo xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                <Title>$title</Title>
                <Series>$series</Series>
                ${if (number.isNotEmpty()) "<Number>$number</Number>" else ""}
                <Summary>$summary</Summary>
                <Writer>$writer</Writer>
                <Penciller>$penciller</Penciller>
                <Genre>$genre</Genre>
                <Web>${escapeXml(manga.url)}</Web>
            </ComicInfo>
        """.trimIndent()
    }

    fun createComicInfoFile(dir: File, manga: Manga, chapter: Chapter, sourceName: String) {
        val file = File(dir, "ComicInfo.xml")
        file.writeText(generate(manga, chapter, sourceName))
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&apos;")
    }
}
