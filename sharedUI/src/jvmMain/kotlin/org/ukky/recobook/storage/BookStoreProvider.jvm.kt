package org.ukky.recobook.storage

import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.ukky.recobook.data.BookCollection
import org.ukky.recobook.data.BorrowerHistory

actual fun createBookStore(): KStore<BookCollection> {
    val home = System.getProperty("user.home") ?: "."
    val dir = Path("$home/.recobook")
    SystemFileSystem.createDirectories(dir, mustCreate = false)
    val file = Path("$home/.recobook/books.json")
    return storeOf(file = file, default = BookCollection())
}

actual fun createBorrowerHistoryStore(): KStore<BorrowerHistory> {
    val home = System.getProperty("user.home") ?: "."
    val dir = Path("$home/.recobook")
    SystemFileSystem.createDirectories(dir, mustCreate = false)
    val file = Path("$home/.recobook/borrowers.json")
    return storeOf(file = file, default = BorrowerHistory())
}
