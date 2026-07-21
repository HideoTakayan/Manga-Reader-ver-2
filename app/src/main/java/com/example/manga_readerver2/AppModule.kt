package com.example.manga_readerver2

import android.app.Application
import android.content.Context
import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.example.manga_readerver2.core.download.DownloadManager
import com.example.manga_readerver2.core.download.DownloadStore
import com.example.manga_readerver2.core.source.ExtensionApi
import eu.kanade.tachiyomi.network.NetworkHelper
import com.example.manga_readerver2.core.source.*
import com.example.manga_readerver2.core.preference.ReaderPreferences
import com.example.manga_readerver2.core.preference.LibraryPreferences
import com.example.manga_readerver2.core.preference.GeneralPreferences
import com.example.manga_readerver2.core.security.SecurityPreferences
import com.example.manga_readerver2.core.track.TrackPreferences
import com.example.manga_readerver2.core.track.AniListManager
import com.example.manga_readerver2.core.utils.PreferenceStore
import com.example.manga_readerver2.core.utils.FileManager
import com.example.manga_readerver2.core.utils.UserAgentInterceptor
import com.example.manga_readerver2.data.repository.ExtensionRepoRepositoryImpl
import com.example.manga_readerver2.data.repository.MangaRepositoryImpl
import com.example.mangareaderver2.database.Chapters
import com.example.mangareaderver2.database.Database
import com.example.manga_readerver2.domain.repository.ExtensionRepoRepository
import com.example.manga_readerver2.domain.repository.MangaRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json

class AppModule(val app: Application) : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingleton(app)
        addSingleton<Application>(app)
        addSingleton<Context>(app)

        addSingletonFactory {
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        }

        addSingletonFactory {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val cookieJar = com.example.manga_readerver2.core.utils.AndroidCookieJar()
            OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .addInterceptor(UserAgentInterceptor())
                .addInterceptor(com.example.manga_readerver2.core.utils.CloudflareInterceptor(app))
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        }

        addSingletonFactory {
            val driver = AndroidSqliteDriver(
                schema = Database.Schema,
                context = app,
                name = "manga_reader.db"
            )
            Database(
                driver = driver,
                chaptersAdapter = Chapters.Adapter(
                    chapter_numberAdapter = object : ColumnAdapter<Float, Double> {
                        override fun decode(databaseValue: Double): Float = databaseValue.toFloat()
                        override fun encode(value: Float): Double = value.toDouble()
                    }
                )
            )
        }

        addSingletonFactory<MangaRepository> {
            MangaRepositoryImpl(get())
        }

        // Cấu hình hệ thống thành phần mở rộng (Extension System)
        addSingletonFactory {
            PreferenceStore(app)
        }

        addSingletonFactory {
            FileManager(app)
        }

        addSingletonFactory {
            SourcePreferences(get())
        }

        addSingletonFactory {
            ReaderPreferences(get())
        }

        addSingletonFactory {
            SecurityPreferences(get())
        }

        addSingletonFactory {
            TrackPreferences(get())
        }

        addSingletonFactory {
            LocalSource()
        }

        addSingletonFactory {
            AniListManager()
        }

        addSingletonFactory {
            LibraryPreferences(get())
        }

        addSingletonFactory {
            com.example.manga_readerver2.core.preference.DownloadPreferences(get())
        }

        addSingletonFactory {
            com.example.manga_readerver2.core.preference.DisplayPreferences(get())
        }

        addSingletonFactory {
            GeneralPreferences(get())
        }

        addSingletonFactory<ExtensionRepoRepository> {
            ExtensionRepoRepositoryImpl(get())
        }

        addSingletonFactory {
            ExtensionApi()
        }

        addSingletonFactory {
            eu.kanade.tachiyomi.network.NetworkHelper(app, get<OkHttpClient>())
        }

        addSingletonFactory {
            ExtensionInstaller(app)
        }

        addSingletonFactory {
            TrustExtension(get())
        }

        addSingletonFactory {
            ExtensionManager(app, get(), get())
        }

        addSingletonFactory<SourceManager> {
            AndroidSourceManager(get())
        }

        addSingletonFactory {
            com.example.manga_readerver2.core.tts.TtsManager(app)
        }

        addSingletonFactory {
            DownloadStore(app, get(), get())
        }

        addSingletonFactory {
            com.example.manga_readerver2.core.download.DownloadCache(app, get())
        }

        addSingletonFactory {
            DownloadManager(app, get())
        }

        addSingletonFactory {
            com.example.manga_readerver2.core.backup.BackupManager(app, get())
        }

        addSingletonFactory {
            ExtensionRepoService()
        }

        addSingletonFactory {
            com.example.manga_readerver2.domain.usecase.MigrateMangaUseCase(get())
        }
    }
}

