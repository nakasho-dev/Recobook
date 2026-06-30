package org.ukky.recobook.data

import kotlinx.serialization.Serializable
import kotlin.time.Clock

@Serializable
data class Book(
    val id: String,
    val isbn: String,
    val isbn10: String? = null,
    val isbn13: String? = null,
    val title: String,
    val authors: List<String> = emptyList(),
    val publisher: String? = null,
    val publishedDate: String? = null,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val pageCount: Int? = null,
    val categories: List<String> = emptyList(),
    val addedAt: Long = Clock.System.now().toEpochMilliseconds(),
)

@Serializable
data class BookCollection(
    val items: List<Book> = emptyList(),
)
