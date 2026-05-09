package com.example.manga_readerver2.core.utils

import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Lớp hỗ trợ đọc các file lưu trữ (CBZ, ZIP) theo phong cách Mihon.
 * Không giải nén toàn bộ, chỉ mở luồng dữ liệu (Stream) khi cần thiết.
 */
object ArchiveReader {

    private val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".webp")

    /**
     * Lấy danh sách các entry ảnh đã được sắp xếp tự nhiên.
     */
    fun getOrderedImageEntries(file: File): List<String> {
        if (!file.exists()) return emptyList()
        try {
            ZipFile(file).use { zip ->
                return zip.entries().asSequence()
                    .filter { !it.isDirectory && isImageFile(it.name) }
                    .map { it.name }
                    .sortedWith(Comparator { o1, o2 -> naturalCompare(o1, o2) })
                    .toList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    /**
     * Lấy dữ liệu mảng byte của một trang cụ thể.
     * Đảm bảo ZipFile được đóng ngay sau khi đọc xong.
     */
    fun getPageBytes(file: File, entryName: String): ByteArray? {
        try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry(entryName) ?: return null
                return zip.getInputStream(entry).readBytes()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Mở luồng dữ liệu của một trang cụ thể.
     * CẢNH BÁO: Người gọi phải tự quản lý việc đóng InputStream và ZipFile nếu cần.
     * Khuyên dùng getPageBytes cho các tác vụ đơn lẻ.
     */
    fun getPageStream(zip: ZipFile, entryName: String): InputStream? {
        return try {
            val entry = zip.getEntry(entryName)
            zip.getInputStream(entry)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Thuật toán so sánh tên file tự nhiên (1, 2, 10 thay vì 1, 10, 2).
     */
    private fun naturalCompare(a: String, b: String): Int {
        val pattern = Regex("(\\d+)|(\\D+)")
        val aMatches = pattern.findAll(a).map { it.value }.toList()
        val bMatches = pattern.findAll(b).map { it.value }.toList()
        
        val size = minOf(aMatches.size, bMatches.size)
        for (i in 0 until size) {
            val aPart = aMatches[i]
            val bPart = bMatches[i]
            
            if (aPart != bPart) {
                val aInt = aPart.toIntOrNull()
                val bInt = bPart.toIntOrNull()
                
                return if (aInt != null && bInt != null) {
                    aInt.compareTo(bInt)
                } else {
                    aPart.compareTo(bPart)
                }
            }
        }
        return a.length.compareTo(b.length)
    }

    fun getFirstImageBytes(file: File): ByteArray? {
        try {
            ZipFile(file).use { zip ->
                val firstEntry = zip.entries().asSequence()
                    .filter { !it.isDirectory && isImageFile(it.name) }
                    .sortedWith(Comparator { o1, o2 -> naturalCompare(o1.name, o2.name) })
                    .firstOrNull() ?: return null
                return zip.getInputStream(firstEntry).readBytes()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun isImageFile(name: String): Boolean {
        val lowerName = name.lowercase()
        return imageExtensions.any { lowerName.endsWith(it) }
    }
}
