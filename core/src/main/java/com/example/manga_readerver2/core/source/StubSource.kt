package com.example.manga_readerver2.core.source

import eu.kanade.tachiyomi.source.Source

data class StubSource(
    override val id: Long,
    override val lang: String,
    override val name: String,
) : Source {
    override suspend fun getMangaDetails(manga: eu.kanade.tachiyomi.source.model.SManga): eu.kanade.tachiyomi.source.model.SManga {
        throw SourceNotInstalledException()
    }

    override suspend fun getChapterList(manga: eu.kanade.tachiyomi.source.model.SManga): List<eu.kanade.tachiyomi.source.model.SChapter> {
        throw SourceNotInstalledException()
    }

    override suspend fun getPageList(chapter: eu.kanade.tachiyomi.source.model.SChapter): List<eu.kanade.tachiyomi.source.model.Page> {
        throw SourceNotInstalledException()
    }
}

class SourceNotInstalledException : Exception()
