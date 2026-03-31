package org.ukky.recobook.storage

import io.github.xxfast.kstore.KStore
import org.ukky.recobook.data.BookCollection

expect fun createBookStore(): KStore<BookCollection>
