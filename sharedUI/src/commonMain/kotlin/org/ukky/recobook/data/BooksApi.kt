package org.ukky.recobook.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class BooksApi(
    private val client: HttpClient,
) {
    suspend fun fetchByIsbn(isbn: String): Book? {
        val response: VolumeResponse = client
            .get("https://www.googleapis.com/books/v1/volumes") {
                parameter("q", "isbn:$isbn")
                parameter("maxResults", "1")
            }
            .body()
        val item = response.items.firstOrNull() ?: return null
        return item.toBook(isbn)
    }
}

private fun VolumeItem.toBook(fallbackIsbn: String): Book {
    val identifiers = volumeInfo.industryIdentifiers
    val isbn13 = identifiers.firstOrNull { it.type == "ISBN_13" }?.identifier
    val isbn10 = identifiers.firstOrNull { it.type == "ISBN_10" }?.identifier
    val canonicalIsbn = isbn13 ?: isbn10 ?: fallbackIsbn
    val thumbnail = volumeInfo.imageLinks?.thumbnail ?: volumeInfo.imageLinks?.smallThumbnail
    val safeThumbnail = thumbnail?.replace("http://", "https://")
    val title = volumeInfo.title.ifBlank { canonicalIsbn }
    return Book(
        id = if (id.isNotBlank()) id else canonicalIsbn,
        isbn = canonicalIsbn,
        isbn10 = isbn10,
        isbn13 = isbn13,
        title = title,
        authors = volumeInfo.authors,
        publisher = volumeInfo.publisher,
        publishedDate = volumeInfo.publishedDate,
        description = volumeInfo.description,
        thumbnailUrl = safeThumbnail,
        pageCount = volumeInfo.pageCount,
        categories = volumeInfo.categories,
    )
}
