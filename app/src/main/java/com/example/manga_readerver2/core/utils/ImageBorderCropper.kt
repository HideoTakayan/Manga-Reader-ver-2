package com.example.manga_readerver2.core.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tự động phát hiện và cắt viền trắng/sáng từ ảnh manga scan.
 * scan từ 4 cạnh vào trong tìm pixel không trắng.
 */
object ImageBorderCropper {

    /**
     * Ngưỡng độ sáng để coi là "viền trắng" (0-255).
     * Giá trị cao hơn = cắt nhiều hơn (hành xử tiêu chuẩn: ~240).
     */
    private const val WHITE_THRESHOLD = 240
    private const val BLACK_THRESHOLD = 15

    /**
     * Trả về [Rect] crop cho [bitmap] nếu phát hiện viền trắng đáng kể.
     * Trả về null nếu ảnh không cần crop (viền < 2% mỗi chiều).
     * Tối ưu hóa: Dùng copyPixelsToBuffer + ByteBuffer thay vì getPixel()
     */
    fun findCropRect(bitmap: Bitmap): Rect? {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return null

        // Sử dụng ByteBuffer để copy pixel nhanh hơn
        val buffer = java.nio.ByteBuffer.allocateDirect(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(buffer)
        buffer.rewind()

        val bytesPerPixel = bitmap.byteCount / (w * h)
        // Chỉ hỗ trợ ARGB_8888 (4 bytes) hoặc RGB_565 (2 bytes) cho đơn giản
        // Sử dụng cơ chế dự phòng getPixels nếu không thỏa mãn điều kiện
        val pixels = IntArray(w * h)
        if (bytesPerPixel == 4) {
            buffer.order(java.nio.ByteOrder.nativeOrder())
            buffer.asIntBuffer().get(pixels)
        } else {
            // Fallback an toàn nếu config không phải 4 bytes/pixel (vd: RGB_565)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        }

        var top = 0
        var bottom = h
        var left = 0
        var right = w

        // Helper function for 1D array access
        fun isWhite(x: Int, y: Int): Boolean {
            val pixel = pixels[y * w + x]
            if (bytesPerPixel == 4) {
                // Tùy endianness, pixel format của ByteBuffer (thường là RGBA trong bộ nhớ)
                // Tuy nhiên Int format từ ByteBuffer có thể khác với Color.argb
                // Thông thường Android ARGB_8888 trong int array đọc từ ByteBuffer nativeOrder
                // có byte layout: R=0xFF, G=0xFF00, B=0xFF0000. Để an toàn, ta check cả 3 kênh.
                // Hàm isWhitePixel dưới được design cho ARGB_8888 chuẩn từ getPixels/Color
                // Nên ta dùng một logic check linh hoạt:
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                // Hoặc format ABGR tuỳ máy, nhưng pixel trắng thì các kênh R,G,B đều phải cao (> ngưỡng)
                // Byte thứ 4 (Alpha) bỏ qua. Do đó cách dễ nhất:
                // Đối với dải màu trắng, giá trị R, G, B đều ở mức cao. Kiểm tra giới hạn của 3 byte thấp hoặc 3 byte bất kỳ trong chuỗi 4 byte.
            }
            return isBorderPixel(pixel)
        }

        // Scan từ trên xuống
        outer@ for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until w) {
                if (!isBorderPixel(pixels[rowOffset + x])) {
                    top = y
                    break@outer
                }
            }
        }

        // Scan từ dưới lên
        outer@ for (y in h - 1 downTo top) {
            val rowOffset = y * w
            for (x in 0 until w) {
                if (!isBorderPixel(pixels[rowOffset + x])) {
                    bottom = y + 1
                    break@outer
                }
            }
        }

        // Scan từ trái sang phải
        outer@ for (x in 0 until w) {
            for (y in top until bottom) {
                if (!isBorderPixel(pixels[y * w + x])) {
                    left = x
                    break@outer
                }
            }
        }

        // Scan từ phải sang trái
        outer@ for (x in w - 1 downTo left) {
            for (y in top until bottom) {
                if (!isBorderPixel(pixels[y * w + x])) {
                    right = x + 1
                    break@outer
                }
            }
        }

        // Kiểm tra có crop đáng kể không (>2% mỗi chiều mới áp dụng)
        val minCropPixelsH = (h * 0.02f).toInt()
        val minCropPixelsW = (w * 0.02f).toInt()
        val hasCrop = top > minCropPixelsH || bottom < h - minCropPixelsH ||
                left > minCropPixelsW || right < w - minCropPixelsW

        if (!hasCrop) return null
        if (right <= left || bottom <= top) return null

        return Rect(left, top, right, bottom)
    }


    /**
     * Load ảnh từ ByteArray và crop viền trắng, trả về Bitmap đã crop.
     * Null nếu không cần crop hoặc lỗi.
     */
    suspend fun cropBorders(bytes: ByteArray): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // Decode ở kích thước nhỏ để scan pixel nhanh hơn
            val opts = BitmapFactory.Options().apply {
                inSampleSize = 4 // Giảm 4x để scan nhanh
            }
            val sample = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                ?: return@withContext null

            val cropRect = findCropRect(sample)
            sample.recycle()

            if (cropRect == null) return@withContext null

            // Decode full-size rồi crop
            val full = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@withContext null

            // Scale crop rect về kích thước full
            val scaleX = full.width.toFloat() / (full.width / 4f)
            val scaleY = full.height.toFloat() / (full.height / 4f)

            // inSampleSize=4 nên nhân 4
            val scaledRect = Rect(
                (cropRect.left * 4).coerceIn(0, full.width),
                (cropRect.top * 4).coerceIn(0, full.height),
                (cropRect.right * 4).coerceIn(0, full.width),
                (cropRect.bottom * 4).coerceIn(0, full.height)
            )

            if (scaledRect.width() <= 0 || scaledRect.height() <= 0) {
                full.recycle()
                return@withContext null
            }

            val cropped = Bitmap.createBitmap(
                full,
                scaledRect.left,
                scaledRect.top,
                scaledRect.width(),
                scaledRect.height()
            )
            full.recycle()
            cropped
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Crop file ảnh local và trả về Bitmap đã crop.
     */
    suspend fun cropBordersFromFile(file: java.io.File): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            val sample = BitmapFactory.decodeFile(file.absolutePath, opts)
                ?: return@withContext null

            val cropRect = findCropRect(sample)
            sample.recycle()

            if (cropRect == null) return@withContext null

            val full = BitmapFactory.decodeFile(file.absolutePath)
                ?: return@withContext null

            val scaledRect = Rect(
                (cropRect.left * 4).coerceIn(0, full.width),
                (cropRect.top * 4).coerceIn(0, full.height),
                (cropRect.right * 4).coerceIn(0, full.width),
                (cropRect.bottom * 4).coerceIn(0, full.height)
            )

            if (scaledRect.width() <= 0 || scaledRect.height() <= 0) {
                full.recycle()
                return@withContext null
            }

            val cropped = Bitmap.createBitmap(
                full,
                scaledRect.left,
                scaledRect.top,
                scaledRect.width(),
                scaledRect.height()
            )
            full.recycle()
            cropped
        } catch (e: Exception) {
            null
        }
    }

    private fun isBorderPixel(pixel: Int): Boolean {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        
        val isWhite = r >= WHITE_THRESHOLD && g >= WHITE_THRESHOLD && b >= WHITE_THRESHOLD
        val isBlack = r <= BLACK_THRESHOLD && g <= BLACK_THRESHOLD && b <= BLACK_THRESHOLD
        return isWhite || isBlack
    }
}
