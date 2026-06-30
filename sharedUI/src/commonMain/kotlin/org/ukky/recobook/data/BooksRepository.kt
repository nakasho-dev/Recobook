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
    private val borrowerHistoryStore: KStore<BorrowerHistory>,
    private val api: BooksApi,
) {
    val books: Flow<List<Book>> = store.updates.map { it?.items.orEmpty() }
    val borrowerHistory: Flow<List<String>> = borrowerHistoryStore.updates.map { history ->
        history?.names
            .orEmpty()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
    }

    suspend fun addByIsbn(isbn: String): BookAddResult {
        return try {
            val book = api.fetchByIsbn(isbn) ?: return BookAddResult.NotFound(isbn)
            var persistedBook = book
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
                    book.copy(
                        addedAt = items[index].addedAt,
                        loanInfo = items[index].loanInfo,
                    )
                } else {
                    book
                }
                persistedBook = updatedBook
                val newItems = if (index >= 0) {
                    items.toMutableList().apply { set(index, updatedBook) }
                } else {
                    listOf(updatedBook) + items
                }
                BookCollection(newItems)
            }
            BookAddResult.Success(persistedBook, updated)
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

    /**
     * [fromIndex] 位置のアイテムを [toIndex] 位置に移動する。
     * インデックスが範囲外の場合やインデックスが同一の場合は何もしない。
     */
    suspend fun reorderBooks(fromIndex: Int, toIndex: Int) {
        store.update { current ->
            val items = current?.items.orEmpty().toMutableList()
            if (fromIndex == toIndex ||
                fromIndex !in items.indices ||
                toIndex !in items.indices
            ) {
                return@update current
            }
            val item = items.removeAt(fromIndex)
            items.add(toIndex, item)
            BookCollection(items)
        }
    }

    suspend fun updateLoan(bookId: String, borrowerName: String?) {
        val trimmedBorrowerName = borrowerName?.trim().orEmpty()
        val loanInfo = trimmedBorrowerName.takeIf { it.isNotEmpty() }?.let(::LoanInfo)
        var updated = false
        store.update { current ->
            val items = current?.items.orEmpty()
            val index = items.indexOfFirst { it.id == bookId }
            if (index < 0) {
                return@update current
            }
            updated = true
            val updatedItems = items.toMutableList()
            updatedItems[index] = items[index].copy(loanInfo = loanInfo)
            BookCollection(updatedItems)
        }
        if (updated && loanInfo != null) {
            borrowerHistoryStore.update { history ->
                val names = history?.names.orEmpty() + loanInfo.borrowerName
                BorrowerHistory(
                    names = names
                        .map(String::trim)
                        .filter(String::isNotEmpty)
                        .distinctBy { it.lowercase() }
                        .sortedBy { it.lowercase() },
                )
            }
        }
    }
}
