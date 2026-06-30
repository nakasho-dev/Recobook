package org.ukky.recobook.storage

import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.ukky.recobook.data.BookCollection
import org.ukky.recobook.data.BorrowerHistory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

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

actual fun createBorrowerHistoryStore(): KStore<BorrowerHistory> {
    val paths = NSSearchPathForDirectoriesInDomains(
        directory = NSDocumentDirectory,
        domainMask = NSUserDomainMask,
        expandTilde = true,
    )
    val documentsPath = paths.firstOrNull() ?: "."
    val dirPath = "$documentsPath/recobook"
    SystemFileSystem.createDirectories(Path(dirPath), mustCreate = false)
    val file = Path("$dirPath/borrowers.json")
    return storeOf(file = file, default = BorrowerHistory())
}
