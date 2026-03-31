package org.ukky.recobook.storage

import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.storage.storeOf
import org.ukky.recobook.data.BookCollection

actual fun createBookStore(): KStore<BookCollection> {
    return storeOf(key = "recobook_books", default = BookCollection())
}
