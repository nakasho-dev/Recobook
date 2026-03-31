package org.ukky.recobook.data

import kotlin.time.Clock
import kotlinx.serialization.Serializable

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

@Serializable
data class VolumeResponse(
    val totalItems: Int = 0,
    val items: List<VolumeItem> = emptyList(),
)

@Serializable
data class VolumeItem(
    val id: String = "",
    val volumeInfo: VolumeInfo = VolumeInfo(),
)

@Serializable
data class VolumeInfo(
    val title: String = "",
    val authors: List<String> = emptyList(),
    val publisher: String? = null,
    val publishedDate: String? = null,
    val description: String? = null,
    val industryIdentifiers: List<IndustryIdentifier> = emptyList(),
    val imageLinks: ImageLinks? = null,
    val pageCount: Int? = null,
    val categories: List<String> = emptyList(),
)

@Serializable
data class IndustryIdentifier(
    val type: String = "",
    val identifier: String = "",
)

@Serializable
data class ImageLinks(
    val thumbnail: String? = null,
    val smallThumbnail: String? = null,
)
