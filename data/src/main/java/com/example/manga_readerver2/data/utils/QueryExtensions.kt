package com.example.manga_readerver2.data.utils

import app.cash.sqldelight.Query
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.CoroutineContext

fun <T : Any> Query<T>.subscribeToList(
    context: CoroutineContext = Dispatchers.IO,
): Flow<List<T>> = asFlow().mapToList(context)

fun <T : Any> Query<T>.subscribeToOne(
    context: CoroutineContext = Dispatchers.IO,
): Flow<T> = asFlow().mapToOne(context)

fun <T : Any> Query<T>.subscribeToOneOrNull(
    context: CoroutineContext = Dispatchers.IO,
): Flow<T?> = asFlow().mapToOneOrNull(context)

