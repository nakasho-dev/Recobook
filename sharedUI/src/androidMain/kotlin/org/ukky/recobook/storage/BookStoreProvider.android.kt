package org.ukky.recobook.storage

import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import kotlinx.io.files.Path
import org.ukky.recobook.data.BookCollection
import org.ukky.recobook.platform.AndroidContextHolder

actual fun createBookStore(): KStore<BookCollection> {
    val context = AndroidContextHolder.appContext
    val file = Path(context.filesDir.resolve("recobook_books.json").absolutePath)
    return storeOf(file = file, default = BookCollection())
}
