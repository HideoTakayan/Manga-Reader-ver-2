package eu.kanade.tachiyomi.source.online;

import eu.kanade.tachiyomi.source.CatalogueSource;
import eu.kanade.tachiyomi.source.model.FilterList;
import eu.kanade.tachiyomi.source.MangasPage;
import eu.kanade.tachiyomi.source.model.Page;
import eu.kanade.tachiyomi.source.model.SChapter;
import eu.kanade.tachiyomi.source.model.SManga;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import eu.kanade.tachiyomi.network.RequestsKt;
import rx.Observable;
import rx.functions.Func1;
import java.security.MessageDigest;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class HttpSource implements CatalogueSource {

    public abstract String getBaseUrl();

    public int getVersionId() {
        return 1;
    }

    private final long _id = generateId(getName(), getLang(), getVersionId());

    @Override
    public long getId() {
        return _id;
    }

    protected long generateId(String name, String lang, int versionId) {
        try {
            String key = name.toLowerCase() + "/" + lang + "/" + versionId;
            byte[] bytes = MessageDigest.getInstance("MD5").digest(key.getBytes());
            long id = 0;
            for (int i = 0; i < 8; i++) {
                id |= ((long) bytes[i] & 0xff) << (8 * (7 - i));
            }
            return id & Long.MAX_VALUE;
        } catch (Exception e) {
            return 0;
        }
    }

    protected Headers.Builder headersBuilder() {
        return new Headers.Builder().add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
    }

    public Headers getHeaders() {
        return headersBuilder().build();
    }

    public OkHttpClient getClient() {
        return new OkHttpClient.Builder().build();
    }

    @Override
    public String toString() {
        return getName() + " (" + getLang().toUpperCase() + ")";
    }

    // --- POPULAR ---
    protected abstract Request popularMangaRequest(int page);
    protected abstract MangasPage popularMangaParse(Response response);

    @Nullable
    @Override
    public Object getPopularManga(int page, @NotNull Continuation<? super MangasPage> continuation) {
        try {
            Response response = getClient().newCall(popularMangaRequest(page)).execute();
            return popularMangaParse(response);
        } catch (Exception e) {
            return new MangasPage(Collections.emptyList(), false);
        }
    }

    @Override
    public Observable<MangasPage> fetchPopularManga(int page) {
        return RequestsKt.asObservableSuccess(getClient().newCall(popularMangaRequest(page))).map(new Func1<Response, MangasPage>() {
            @Override
            public MangasPage call(Response response) {
                return popularMangaParse(response);
            }
        });
    }

    // --- SEARCH ---
    protected abstract Request searchMangaRequest(int page, String query, FilterList filters);
    protected abstract MangasPage searchMangaParse(Response response);

    @Nullable
    @Override
    public Object getSearchManga(int page, @NotNull String query, @NotNull FilterList filters, @NotNull Continuation<? super MangasPage> continuation) {
        try {
            Response response = getClient().newCall(searchMangaRequest(page, query, filters)).execute();
            return searchMangaParse(response);
        } catch (Exception e) {
            return new MangasPage(Collections.emptyList(), false);
        }
    }

    @Override
    public Observable<MangasPage> fetchSearchManga(int page, String query, FilterList filters) {
        return RequestsKt.asObservableSuccess(getClient().newCall(searchMangaRequest(page, query, filters))).map(new Func1<Response, MangasPage>() {
            @Override
            public MangasPage call(Response response) {
                return searchMangaParse(response);
            }
        });
    }

    // --- LATEST ---
    protected abstract Request latestUpdatesRequest(int page);
    protected abstract MangasPage latestUpdatesParse(Response response);

    @Nullable
    @Override
    public Object getLatestUpdates(int page, @NotNull Continuation<? super MangasPage> continuation) {
        try {
            Response response = getClient().newCall(latestUpdatesRequest(page)).execute();
            return latestUpdatesParse(response);
        } catch (Exception e) {
            return new MangasPage(Collections.emptyList(), false);
        }
    }

    @Override
    public Observable<MangasPage> fetchLatestUpdates(int page) {
        return RequestsKt.asObservableSuccess(getClient().newCall(latestUpdatesRequest(page))).map(new Func1<Response, MangasPage>() {
            @Override
            public MangasPage call(Response response) {
                return latestUpdatesParse(response);
            }
        });
    }

    // --- MANGA DETAILS ---
    public Request mangaDetailsRequest(SManga manga) {
        return new Request.Builder().url(getBaseUrl() + manga.getUrl()).headers(getHeaders()).build();
    }
    protected abstract SManga mangaDetailsParse(Response response);

    @Nullable
    @Override
    public Object getMangaDetails(@NotNull SManga manga, @NotNull Continuation<? super SManga> continuation) {
        try {
            Response response = getClient().newCall(mangaDetailsRequest(manga)).execute();
            SManga details = mangaDetailsParse(response);
            details.setInitialized(true);
            return details;
        } catch (Exception e) {
            return manga;
        }
    }

    @Override
    public Observable<SManga> fetchMangaDetails(final SManga manga) {
        return RequestsKt.asObservableSuccess(getClient().newCall(mangaDetailsRequest(manga))).map(new Func1<Response, SManga>() {
            @Override
            public SManga call(Response response) {
                SManga details = mangaDetailsParse(response);
                details.setInitialized(true);
                return details;
            }
        });
    }

    // --- CHAPTER LIST ---
    public Request chapterListRequest(SManga manga) {
        return new Request.Builder().url(getBaseUrl() + manga.getUrl()).headers(getHeaders()).build();
    }
    protected abstract List<SChapter> chapterListParse(Response response);

    @Nullable
    @Override
    public Object getChapterList(@NotNull SManga manga, @NotNull Continuation<? super List<? extends SChapter>> continuation) {
        try {
            Response response = getClient().newCall(chapterListRequest(manga)).execute();
            return chapterListParse(response);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public Observable<List<SChapter>> fetchChapterList(SManga manga) {
        return RequestsKt.asObservableSuccess(getClient().newCall(chapterListRequest(manga))).map(new Func1<Response, List<SChapter>>() {
            @Override
            public List<SChapter> call(Response response) {
                return chapterListParse(response);
            }
        });
    }

    // --- PAGE LIST ---
    public Request pageListRequest(SChapter chapter) {
        return new Request.Builder().url(getBaseUrl() + chapter.getUrl()).headers(getHeaders()).build();
    }
    protected abstract List<Page> pageListParse(Response response);

    @Nullable
    @Override
    public Object getPageList(@NotNull SChapter chapter, @NotNull Continuation<? super List<? extends Page>> continuation) {
        try {
            Response response = getClient().newCall(pageListRequest(chapter)).execute();
            return pageListParse(response);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public Observable<List<Page>> fetchPageList(SChapter chapter) {
        return RequestsKt.asObservableSuccess(getClient().newCall(pageListRequest(chapter))).map(new Func1<Response, List<Page>>() {
            @Override
            public List<Page> call(Response response) {
                return pageListParse(response);
            }
        });
    }

    // --- IMAGE URL ---
    public Request imageUrlRequest(Page page) {
        return new Request.Builder().url(page.getUrl()).headers(getHeaders()).build();
    }
    protected abstract String imageUrlParse(Response response);

    @Nullable
    public Object getImageUrl(@NotNull Page page, @NotNull Continuation<? super String> continuation) {
        String imageUrl = page.getImageUrl();
        return imageUrl != null ? imageUrl : "";
    }

    public Observable<String> fetchImageUrl(Page page) {
        return Observable.error(new Exception("Not used"));
    }

    // --- UTILS ---
    public void setUrlWithoutDomain(SChapter chapter, String url) {
        chapter.setUrl(url.replace(getBaseUrl(), ""));
    }

    public void setUrlWithoutDomain(SManga manga, String url) {
        manga.setUrl(url.replace(getBaseUrl(), ""));
    }

    public String getMangaUrl(SManga manga) {
        return mangaDetailsRequest(manga).url().toString();
    }

    public String getChapterUrl(SChapter chapter) {
        return pageListRequest(chapter).url().toString();
    }

    public void prepareNewChapter(SChapter chapter, SManga manga) {
    }

    @NotNull
    @Override
    public FilterList getFilterList() {
        return new FilterList();
    }
    
    @NotNull
    @Override
    public String getLang() {
        return "";
    }
}
