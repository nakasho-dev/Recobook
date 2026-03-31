package org.ukky.recobook.storage

import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.ukky.recobook.data.BookCollection
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSSearchPathForDirectoriesInDomains

actual fun createBookStore(): KStore<BookCollection> {
    val paths = NSSearchPathForDirectoriesInDomains(
        directory = NSDocumentDirectory,
        domainMask = NSUserDomainMask,
        expandTilde = true,
    )
    val documentsPath = paths.firstOrNull() ?: "."
    val dirPath = "$documentsPath/recobook"
    SystemFileSystem.createDirectories(Path(dirPath), mustCreate = false)
    val file = Path("$dirPath/books.json")
    return storeOf(file = file, default = BookCollection())
}
