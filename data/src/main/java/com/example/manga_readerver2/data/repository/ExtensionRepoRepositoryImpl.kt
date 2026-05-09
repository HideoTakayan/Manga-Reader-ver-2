package com.example.manga_readerver2.data.repository

import com.example.manga_readerver2.data.utils.subscribeToList
import com.example.manga_readerver2.data.utils.subscribeToOne
import com.example.mangareaderver2.database.Database
import com.example.manga_readerver2.domain.repository.ExtensionRepo
import com.example.manga_readerver2.domain.repository.ExtensionRepoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ExtensionRepoRepositoryImpl(
    private val database: Database,
) : ExtensionRepoRepository {

    override fun subscribeAll(): Flow<List<ExtensionRepo>> {
        return database.extension_reposQueries
            .findAll(::mapExtensionRepo)
            .subscribeToList()
    }

    override suspend fun getAll(): List<ExtensionRepo> {
        return database.extension_reposQueries
            .findAll(::mapExtensionRepo)
            .executeAsList()
    }

    override suspend fun getRepo(baseUrl: String): ExtensionRepo? {
        return database.extension_reposQueries
            .findOne(baseUrl, ::mapExtensionRepo)
            .executeAsOneOrNull()
    }

    override suspend fun getRepoBySigningKeyFingerprint(fingerprint: String): ExtensionRepo? {
        return database.extension_reposQueries
            .findOneBySigningKeyFingerprint(fingerprint, ::mapExtensionRepo)
            .executeAsOneOrNull()
    }

    override fun getCount(): Flow<Int> {
        return database.extension_reposQueries
            .count()
            .subscribeToOne<Long>()
            .map { it.toInt() }
    }

    override suspend fun insertRepo(
        baseUrl: String,
        name: String,
        shortName: String?,
        website: String,
        signingKeyFingerprint: String,
    ) {
        database.extension_reposQueries.insert(
            baseUrl,
            name,
            shortName,
            website,
            if (signingKeyFingerprint.isEmpty()) null else signingKeyFingerprint,
        )
    }

    override suspend fun upsertRepo(
        baseUrl: String,
        name: String,
        shortName: String?,
        website: String,
        signingKeyFingerprint: String,
    ) {
        database.extension_reposQueries.upsert(
            baseUrl,
            name,
            shortName,
            website,
            if (signingKeyFingerprint.isEmpty()) null else signingKeyFingerprint,
        )
    }

    override suspend fun deleteRepo(baseUrl: String) {
        database.extension_reposQueries.delete(baseUrl)
    }

    private fun mapExtensionRepo(
        baseUrl: String,
        name: String,
        shortName: String?,
        website: String,
        signingKeyFingerprint: String?,
    ): ExtensionRepo = ExtensionRepo(
        baseUrl = baseUrl,
        name = name,
        shortName = shortName,
        website = website,
        signingKeyFingerprint = signingKeyFingerprint ?: "",
    )
}



