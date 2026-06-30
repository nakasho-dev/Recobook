package org.ukky.recobook.storage

import io.github.xxfast.kstore.KStore
import org.ukky.recobook.data.BookCollection
import org.ukky.recobook.data.BorrowerHistory

expect fun createBookStore(): KStore<BookCollection>

expect fun createBorrowerHistoryStore(): KStore<BorrowerHistory>
