package com.example.manga_readerver2.domain.model

data class Category(
    val id: Long,
    val name: String,
    val sortIndex: Long,
    val flags: Long = 0
)
