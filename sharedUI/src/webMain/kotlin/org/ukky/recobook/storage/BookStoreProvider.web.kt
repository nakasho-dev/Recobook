package org.ukky.recobook.storage

import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.storage.storeOf
import org.ukky.recobook.data.BookCollection
import org.ukky.recobook.data.BorrowerHistory

actual fun createBookStore(): KStore<BookCollection> {
    return storeOf(key = "recobook_books", default = BookCollection())
}

actual fun createBorrowerHistoryStore(): KStore<BorrowerHistory> {
    return storeOf(key = "recobook_borrowers", default = BorrowerHistory())
}
