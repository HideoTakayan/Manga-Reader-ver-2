package com.example.manga_readerver2.core.utils

import com.example.manga_readerver2.domain.model.Chapter

object ChapterRecognition {

    private const val numberPattern = "([0-9]+)(\\.[0-9]+)?(\\.?[a-z]+)?"
    
    // Bỏ qua các thông tin volume/season: Vol.1, v1, season 2...
    private val unwanted = Regex("\\b(?:v|ver|vol|version|volume|season|s)[^a-z]?[0-9]+", RegexOption.IGNORE_CASE)
    
    // Tìm chuỗi như "ch. 123", "chương 123", "tập 123"
    private val basic = Regex("(?:ch\\.|chap\\.|chapter\\.|c\\.|ch|chap|chapter|c|chương|tập|hồi)\\s*$numberPattern", RegexOption.IGNORE_CASE)
    
    // Tìm số đơn thuần: 123
    private val number = Regex(numberPattern, RegexOption.IGNORE_CASE)
    
    // Loại bỏ khoảng trắng thừa trước các từ khóa extra
    private val unwantedWhiteSpace = Regex("\\s(?=extra|special|omake)", RegexOption.IGNORE_CASE)

    class ChapterParseInfo(val value: Float, val isExtra: Boolean) : Comparable<ChapterParseInfo> {
        override fun compareTo(other: ChapterParseInfo): Int {
            return value.compareTo(other.value)
        }
    }

    /**
     * Parse chapter number as Float from filename.
     * Returns -1f if it cannot be parsed.
     */
    fun parseChapterNumber(filename: String): Float {
        val info = parseFilename(filename)
        return if (info.value == Float.MAX_VALUE) -1f else info.value
    }

    private fun parseFilename(filename: String): ChapterParseInfo {
        var cleanName = filename.lowercase()
        
        // 1. Loại bỏ dấu phẩy, dấu gạch ngang
        cleanName = cleanName.replace(',', '.').replace('-', '.')
        
        // 2. Loại bỏ khoảng trắng nối với từ khóa
        cleanName = cleanName.replace(unwantedWhiteSpace, "")
        
        val isExtra = cleanName.contains("extra") || 
                      cleanName.contains("omake") || 
                      cleanName.contains("special")
                      
        // 3. Loại bỏ thông tin phần (Volume) để tránh nhận diện nhầm số (hiện trạng Vol.1 Ch.5 -> Ch.5)
        val nameWithoutVolume = cleanName.replace(unwanted, "")
        
        // 4. Thử khớp cơ bản (có tiền tố) trên chuỗi đã lọc
        var match = basic.find(nameWithoutVolume)
        if (match != null) {
            return getChapterNumberFromMatch(match, isExtra)
        }
        
        // 5. Nếu không thấy, tìm XEM CÓ BẤT CỨ SỐ NÀO trong chuỗi không
        match = number.find(nameWithoutVolume)
        if (match != null) {
            return getChapterNumberFromMatch(match, isExtra)
        }
        
        // 6. Trường hợp an toàn (Fail-safe): Thử lại trên chuỗi ban đầu
        match = basic.find(cleanName)
        if (match != null) return getChapterNumberFromMatch(match, isExtra)
        
        match = number.find(cleanName)
        if (match != null) return getChapterNumberFromMatch(match, isExtra)
        
        return ChapterParseInfo(Float.MAX_VALUE, isExtra)
    }

    private fun getChapterNumberFromMatch(match: MatchResult, extra: Boolean): ChapterParseInfo {
        val initial = match.groupValues.getOrNull(1)?.toFloatOrNull() ?: 0f
        val subDecimal = match.groupValues.getOrNull(2)
        val subAlpha = match.groupValues.getOrNull(3)
        
        val addition = checkForDecimal(subDecimal, subAlpha)
        return ChapterParseInfo(initial + addition, extra)
    }

    private fun checkForDecimal(decimal: String?, alpha: String?): Float {
        if (!decimal.isNullOrEmpty()) {
            return decimal.toFloatOrNull() ?: 0f
        }
        
        if (!alpha.isNullOrEmpty()) {
            if (alpha.contains("extra")) return 0.99f
            if (alpha.contains("omake")) return 0.98f
            if (alpha.contains("special")) return 0.97f
            
            // Kí tự chữ a -> .1
            val trimmed = alpha.replace(".", "")
            if (trimmed.length == 1) {
                return parseAlphaPostFix(trimmed[0])
            }
        }
        return 0f
    }

    private fun parseAlphaPostFix(char: Char): Float {
        val number = char.code - ('a'.code - 1)
        if (number >= 10) return 0f
        return number / 10f
    }
    
    /**
     * Trả về tên hiển thị chuẩn (VD: "Chương 10.5")
     */
    fun getDisplayTitle(filename: String): String {
        val info = parseFilename(filename)
        if (info.value == Float.MAX_VALUE) {
            return filename.substringBeforeLast(".")
        }
        
        val numberStr = if (info.value % 1f == 0f) {
            info.value.toInt().toString()
        } else {
            info.value.toString()
        }
        
        var display = "Chương $numberStr"
        if (info.isExtra && !display.contains("Extra", ignoreCase = true)) {
            display += " Extra"
        }
        return display
    }
}
