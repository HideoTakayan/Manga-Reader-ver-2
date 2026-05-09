package com.example.manga_readerver2.domain.repository

import kotlinx.coroutines.flow.Flow

data class ExtensionRepo(
    val baseUrl: String,
    val name: String,
    val shortName: String?,
    val website: String,
    val signingKeyFingerprint: String,
)

interface ExtensionRepoRepository {

    fun subscribeAll(): Flow<List<ExtensionRepo>>

    suspend fun getAll(): List<ExtensionRepo>

    suspend fun getRepo(baseUrl: String): ExtensionRepo?

    suspend fun getRepoBySigningKeyFingerprint(fingerprint: String): ExtensionRepo?

    fun getCount(): Flow<Int>

    suspend fun insertRepo(
        baseUrl: String,
        name: String,
        shortName: String?,
        website: String,
        signingKeyFingerprint: String,
    )

    suspend fun upsertRepo(
        baseUrl: String,
        name: String,
        shortName: String?,
        website: String,
        signingKeyFingerprint: String,
    )

    suspend fun deleteRepo(baseUrl: String)
}
