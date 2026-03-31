package org.ukky.recobook.data

import io.github.xxfast.kstore.KStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed interface BookAddResult {
    data class Success(val book: Book, val updated: Boolean) : BookAddResult
    data class NotFound(val isbn: String) : BookAddResult
    data class Error(val isbn: String, val message: String) : BookAddResult
}

class BooksRepository(
    private val store: KStore<BookCollection>,
    private val api: BooksApi,
) {
    val books: Flow<List<Book>> = store.updates.map { it?.items.orEmpty() }

    suspend fun addByIsbn(isbn: String): BookAddResult {
        return try {
            val book = api.fetchByIsbn(isbn) ?: return BookAddResult.NotFound(isbn)
            var updated = false
            store.update { current ->
                val items = current?.items.orEmpty()
                val index = items.indexOfFirst {
                    it.id == book.id ||
                        (book.isbn13 != null && it.isbn13 == book.isbn13) ||
                        (book.isbn10 != null && it.isbn10 == book.isbn10) ||
                        it.isbn == book.isbn
                }
                val updatedBook = if (index >= 0) {
                    updated = true
                    book.copy(addedAt = items[index].addedAt)
                } else {
                    book
                }
                val newItems = if (index >= 0) {
                    items.toMutableList().apply { set(index, updatedBook) }
                } else {
                    listOf(updatedBook) + items
                }
                BookCollection(newItems)
            }
            BookAddResult.Success(book, updated)
        } catch (error: Exception) {
            BookAddResult.Error(isbn, error.message ?: "Request failed")
        }
    }

    suspend fun removeById(bookId: String) {
        store.update { current ->
            val items = current?.items.orEmpty()
            BookCollection(items.filterNot { it.id == bookId })
        }
    }
}
