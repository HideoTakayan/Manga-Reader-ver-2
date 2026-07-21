package com.example.manga_readerver2.core.utils

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipUtil {
    
    /**
     * Nén một thư mục chứa ảnh thành file .cbz tiêu chuẩn.
     * @param sourceDir Thư mục chứa các file ảnh đã tải.
     * @param destFile File đích (thường là .cbz).
     * @param deleteSource Xóa thư mục nguồn sau khi nén thành công.
     * @return true nếu nén thành công, ngược lại false.
     */
    fun zipDirectory(sourceDir: File, destFile: File, deleteSource: Boolean = true): Boolean {
        if (!sourceDir.exists() || !sourceDir.isDirectory) return false
        
        val files = sourceDir.listFiles() ?: return false
        if (files.isEmpty()) return false

        // Xác thực quá trình khởi tạo cấu trúc thư mục đích
        destFile.parentFile?.mkdirs()

        try {
            FileOutputStream(destFile).use { fos ->
                BufferedOutputStream(fos).use { bos ->
                    ZipOutputStream(bos).use { zos ->
                        for (file in files) {
                            if (!file.isFile) continue
                            
                            val entry = ZipEntry(file.name)
                            zos.putNextEntry(entry)
                            
                            FileInputStream(file).use { fis ->
                                BufferedInputStream(fis).use { bis ->
                                    bis.copyTo(zos)
                                }
                            }
                            zos.closeEntry()
                        }
                    }
                }
            }
            
            if (deleteSource) {
                sourceDir.deleteRecursively()
            }
            
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            // Thực thi cơ chế thu hồi (Rollback): Xóa bỏ các tập tin bị lỗi trong quá trình xử lý
            if (destFile.exists()) destFile.delete()
            return false
        }
    }
}
