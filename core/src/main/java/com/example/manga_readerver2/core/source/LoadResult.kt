package com.example.manga_readerver2.core.source

import com.example.manga_readerver2.core.source.Extension

sealed interface LoadResult {
    data class Success(val extension: Extension.Installed) : LoadResult
    data class Untrusted(val extension: Extension.Untrusted) : LoadResult
    data object Error : LoadResult
}
