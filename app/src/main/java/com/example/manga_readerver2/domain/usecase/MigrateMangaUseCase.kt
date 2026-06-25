package com.example.manga_readerver2.domain.usecase

import com.example.manga_readerver2.domain.model.Manga
import com.example.manga_readerver2.domain.repository.MangaRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrateMangaUseCase(
    private val mangaRepository: MangaRepository = Injekt.get()
) {
    suspend operator fun invoke(
        oldMangaId: Long,
        newMangaId: Long,
        copyReadStatus: Boolean = true,
        copyCategories: Boolean = true,
        removeOldManga: Boolean = true
    ) {
        val oldManga = mangaRepository.getMangaById(oldMangaId) ?: return
        val newManga = mangaRepository.getMangaById(newMangaId) ?: return

        // 1. Đặt newManga làm favorite
        mangaRepository.updateMangaFavorite(newMangaId, true)

        val oldChapters = mangaRepository.getChaptersByMangaId(oldMangaId)
        val newChapters = mangaRepository.getChaptersByMangaId(newMangaId)

        // 2. Chuyển trạng thái đọc
        if (copyReadStatus) {
            val maxReadChapter = oldChapters.filter { it.read }.maxOfOrNull { it.chapterNumber } ?: -1f

            val updatedChaptersToSave = mutableListOf<com.example.manga_readerver2.domain.model.Chapter>()
            newChapters.forEach { newChapter ->
                var updated = false
                var mutableChapter = newChapter
                val matchOld = oldChapters.find { it.chapterNumber == newChapter.chapterNumber && it.chapterNumber != -1f }
                
                if (matchOld != null) {
                    if (matchOld.read) {
                        mutableChapter = mutableChapter.copy(read = true)
                        updated = true
                    }
                    if (matchOld.bookmark) {
                        mutableChapter = mutableChapter.copy(bookmark = true)
                        updated = true
                    }
                } else if (maxReadChapter >= 0f && newChapter.chapterNumber <= maxReadChapter && newChapter.chapterNumber != -1f) {
                    mutableChapter = mutableChapter.copy(read = true)
                    updated = true
                }

                if (updated) {
                    mangaRepository.updateChapterReadStatus(mutableChapter)
                    mangaRepository.updateChapter(mutableChapter)
                }
            }
        }

        // 3. Chuyển Categories
        if (copyCategories) {
            val oldCategoryIds = mangaRepository.getMangaCategoryIds(oldMangaId)
            oldCategoryIds.forEach { catId ->
                mangaRepository.addMangaToCategory(newMangaId, catId)
            }
        }

        // 4. Xóa cũ
        if (removeOldManga) {
            mangaRepository.updateMangaFavorite(oldMangaId, false)
            mangaRepository.removeAllCategoriesFromManga(oldMangaId)
        }
    }
}
