package com.example.manga_readerver2.core.utils

import com.example.manga_readerver2.domain.model.Chapter
import com.example.manga_readerver2.domain.model.Manga
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Công cụ đóng gói truyện chữ thành định dạng EPUB chuẩn.
 */
object EpubExporter {

    fun export(manga: Manga, chapter: Chapter, paragraphs: List<String>, destFile: File): Boolean {
        try {
            destFile.parentFile?.mkdirs()
            FileOutputStream(destFile).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    // 1. Khởi tạo tệp mimetype (Yêu cầu bắt buộc: Phải là tập tin đầu tiên và không áp dụng cơ chế nén)
                    addMimeType(zos)

                    // 2. META-INF/container.xml
                    addContainer(zos)

                    // 3. OEBPS/content.opf
                    addContentOpf(zos, manga, chapter)

                    // 4. OEBPS/toc.ncx
                    addToc(zos, manga, chapter)

                    // 5. OEBPS/chapter.xhtml
                    addChapterContent(zos, chapter, paragraphs)
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            if (destFile.exists()) destFile.delete()
            return false
        }
    }

    private fun addMimeType(zos: ZipOutputStream) {
        val bytes = "application/epub+zip".toByteArray()
        // Tích hợp thuật toán CRC32 động nhằm bảo đảm tính toàn vẹn của cấu trúc EPUB đối với các trình đọc ngoại vi (External Readers)
        val crc = java.util.zip.CRC32().apply { update(bytes) }.value
        val entry = ZipEntry("mimetype").apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            this.crc = crc
        }
        zos.putNextEntry(entry)
        zos.write(bytes)
        zos.closeEntry()
    }

    private fun addContainer(zos: ZipOutputStream) {
        zos.putNextEntry(ZipEntry("META-INF/container.xml"))
        val content = """
            <?xml version="1.0"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                </rootfiles>
            </container>
        """.trimIndent()
        zos.write(content.toByteArray())
        zos.closeEntry()
    }

    private fun addContentOpf(zos: ZipOutputStream, manga: Manga, chapter: Chapter) {
        zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
        val content = """
            <?xml version="1.0" encoding="utf-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="bookid" version="2.0">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>${chapter.name}</dc:title>
                    <dc:creator>${manga.author ?: "Unknown"}</dc:creator>
                    <dc:identifier id="bookid">urn:uuid:${java.util.UUID.randomUUID()}</dc:identifier>
                    <dc:language>vi</dc:language>
                </metadata>
                <manifest>
                    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                </manifest>
                <spine toc="ncx">
                    <itemref idref="chapter"/>
                </spine>
            </package>
        """.trimIndent()
        zos.write(content.toByteArray())
        zos.closeEntry()
    }

    private fun addToc(zos: ZipOutputStream, manga: Manga, chapter: Chapter) {
        zos.putNextEntry(ZipEntry("OEBPS/toc.ncx"))
        val content = """
            <?xml version="1.0" encoding="utf-8"?>
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                <head>
                    <meta name="dtb:uid" content="urn:uuid:12345"/>
                    <meta name="dtb:depth" content="1"/>
                </head>
                <docTitle><text>${manga.title}</text></docTitle>
                <navMap>
                    <navPoint id="navPoint-1" playOrder="1">
                        <navLabel><text>${chapter.name}</text></navLabel>
                        <content src="chapter.xhtml"/>
                    </navPoint>
                </navMap>
            </ncx>
        """.trimIndent()
        zos.write(content.toByteArray())
        zos.closeEntry()
    }

    private fun addChapterContent(zos: ZipOutputStream, chapter: Chapter, paragraphs: List<String>) {
        zos.putNextEntry(ZipEntry("OEBPS/chapter.xhtml"))
        
        // Phân tách khối nội dung văn bản theo từng dòng để khởi tạo các thẻ đoạn văn bản (<p>) độc lập
        val processedBody = paragraphs.flatMap { it.split("\n") }
            .filter { it.isNotBlank() }
            .joinToString("") { "<p>${it.trim().replace("&", "&amp;").replace("<", "&lt;")}</p>" }

        val content = """
            <?xml version="1.0" encoding="utf-8"?>
            <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
            <html xmlns="http://www.w3.org/1999/xhtml">
                <head>
                    <title>${chapter.name}</title>
                    <style>p { margin-bottom: 1em; text-indent: 1.5em; line-height: 1.5; }</style>
                </head>
                <body>
                    <h1>${chapter.name}</h1>
                    $processedBody
                </body>
            </html>
        """.trimIndent()
        zos.write(content.toByteArray())
        zos.closeEntry()
    }
}
