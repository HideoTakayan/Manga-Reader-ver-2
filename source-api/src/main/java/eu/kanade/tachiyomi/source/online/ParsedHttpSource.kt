package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

abstract class ParsedHttpSource : HttpSource() {

    protected abstract fun popularMangaSelector(): String
    protected abstract fun popularMangaFromElement(element: Element): SManga
    protected abstract fun popularMangaNextPageSelector(): String?
    override fun popularMangaParse(response: Response): MangasPage = MangasPage(emptyList<SManga>(), false)

    protected abstract fun searchMangaSelector(): String
    protected abstract fun searchMangaFromElement(element: Element): SManga
    protected abstract fun searchMangaNextPageSelector(): String?
    override fun searchMangaParse(response: Response): MangasPage = MangasPage(emptyList<SManga>(), false)

    protected abstract fun latestUpdatesSelector(): String
    protected abstract fun latestUpdatesFromElement(element: Element): SManga
    protected abstract fun latestUpdatesNextPageSelector(): String?
    override fun latestUpdatesParse(response: Response): MangasPage = MangasPage(emptyList<SManga>(), false)

    protected abstract fun mangaDetailsParse(document: Document): SManga
    override fun mangaDetailsParse(response: Response): SManga = SManga.create()

    protected abstract fun chapterListSelector(): String
    protected abstract fun chapterFromElement(element: Element): SChapter
    override fun chapterListParse(response: Response): List<SChapter> = emptyList<SChapter>()

    protected abstract fun pageListParse(document: Document): List<Page>
    override fun pageListParse(response: Response): List<Page> = emptyList<Page>()

    protected abstract fun imageUrlParse(document: Document): String
    override fun imageUrlParse(response: Response): String = ""
}
