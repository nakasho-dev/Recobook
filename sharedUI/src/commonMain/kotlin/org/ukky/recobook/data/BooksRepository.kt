package org.ukky.recobook.data

import io.github.xxfast.kstore.KStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.ukky.recobook.LineExportFile
import kotlin.time.Clock

sealed interface BookAddResult {
    data class Success(val book: Book, val updated: Boolean) : BookAddResult
    data class NotFound(val isbn: String) : BookAddResult
    data class Error(val isbn: String, val message: String) : BookAddResult
}

enum class BackupImportFormat {
    BackupJsonl,
    BooksJsonl,
}

data class BackupImportSummary(
    val importedBooks: Int,
    val importedBorrowerHistoryCount: Int,
    val format: BackupImportFormat,
)

class BooksRepository(
    private val store: KStore<BookCollection>,
    private val borrowerHistoryStore: KStore<BorrowerHistory>,
    private val api: BooksApi,
) {
    val books: Flow<List<Book>> = store.updates.map { it?.items.orEmpty() }
    val borrowerHistory: Flow<List<String>> = borrowerHistoryStore.updates.map { history ->
        normalizeBorrowerNames(history?.names.orEmpty())
    }

    suspend fun addByIsbn(isbn: String): BookAddResult {
        return try {
            val book = api.fetchByIsbn(isbn) ?: return BookAddResult.NotFound(isbn)
            var persistedBook = book
            var updated = false
            store.update { current ->
                val items = current?.items.orEmpty()
                val index = items.indexOfDuplicate(book)
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
                BorrowerHistory(names = normalizeBorrowerNames(names))
            }
        }
    }

    suspend fun exportBackupJsonl(): LineExportFile {
        val snapshot = snapshot()
        val fileName = "recobook-backup-${timestampSuffix()}.jsonl"
        return LineExportFile(
            suggestedFileName = fileName,
            mimeType = "application/x-ndjson",
            lines = sequence {
                yield(
                    backupJson.encodeToString(
                        BackupMetaRecord(
                            exportedAt = currentInstantString(),
                        ),
                    ),
                )
                snapshot.borrowerHistory.forEach { name ->
                    yield(backupJson.encodeToString(BackupBorrowerRecord(name = name)))
                }
                snapshot.books.forEach { book ->
                    yield(backupJson.encodeToString(BackupBookRecord(book = book)))
                }
            },
        )
    }

    suspend fun exportBooksJsonl(): LineExportFile {
        val snapshot = snapshot()
        return LineExportFile(
            suggestedFileName = "recobook-books-${timestampSuffix()}.jsonl",
            mimeType = "application/x-ndjson",
            lines = snapshot.books.asSequence().map { json.encodeToString(it) },
        )
    }

    suspend fun exportShareMarkdown(): LineExportFile {
        val snapshot = snapshot()
        return LineExportFile(
            suggestedFileName = "recobook-shelf-${timestampSuffix()}.md",
            mimeType = "text/markdown",
            lines = sequence {
                yield("# Recobook shelf")
                yield("")
                yield("Generated at ${currentInstantString()}")
                yield("")
                if (snapshot.books.isEmpty()) {
                    yield("No books on shelf.")
                } else {
                    yield("| Title | Authors | Publisher | Published | ISBN | Status |")
                    yield("| --- | --- | --- | --- | --- | --- |")
                    snapshot.books.forEach { book ->
                        yield(
                            listOf(
                                book.title.escapeMarkdownCell(),
                                book.authors.joinToString(", ").escapeMarkdownCell().orDash(),
                                book.publisher.escapeMarkdownCell().orDash(),
                                book.publishedDate.escapeMarkdownCell().orDash(),
                                book.isbn.escapeMarkdownCell(),
                                book.shareStatusLabel().escapeMarkdownCell(),
                            ).joinToString(prefix = "| ", separator = " | ", postfix = " |"),
                        )
                    }
                }
            },
        )
    }

    suspend fun exportShareCsv(): LineExportFile {
        val snapshot = snapshot()
        return LineExportFile(
            suggestedFileName = "recobook-shelf-${timestampSuffix()}.csv",
            mimeType = "text/csv",
            lines = sequence {
                yield("title,authors,publisher,published_date,isbn,status")
                snapshot.books.forEach { book ->
                    yield(
                        listOf(
                            book.title.toCsvField(),
                            book.authors.joinToString(", ").toCsvField(),
                            (book.publisher ?: "").toCsvField(),
                            (book.publishedDate ?: "").toCsvField(),
                            book.isbn.toCsvField(),
                            book.shareStatusLabel().toCsvField(),
                        ).joinToString(","),
                    )
                }
            },
        )
    }

    suspend fun importBackupJsonl(lines: Sequence<String>): BackupImportSummary {
        val importedBooks = mutableListOf<Book>()
        val importedBorrowerNames = mutableListOf<String>()
        var detectedFormat: BackupImportFormat? = null
        var seenMeta = false

        lines
            .map(String::trim)
            .filter(String::isNotEmpty)
            .forEachIndexed { index, line ->
                val lineNumber = index + 1
                val element = decodeJsonElement(line, lineNumber)
                val type = (element as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull
                when (type) {
                    null -> {
                        val book = decodeBook(element, lineNumber)
                        importedBooks.upsert(book)
                        book.loanInfo?.borrowerName?.let(importedBorrowerNames::add)
                        if (detectedFormat == null) {
                            detectedFormat = BackupImportFormat.BooksJsonl
                        }
                    }

                    BackupMetaRecord.TYPE -> {
                        val meta = decodeMetaRecord(element, lineNumber)
                        if (meta.version > BACKUP_FORMAT_VERSION) {
                            throw IllegalArgumentException("Line $lineNumber: Backup version ${meta.version} is not supported.")
                        }
                        detectedFormat = BackupImportFormat.BackupJsonl
                        seenMeta = true
                    }

                    BackupBorrowerRecord.TYPE -> {
                        val record = decodeBorrowerRecord(element, lineNumber)
                        importedBorrowerNames += record.name
                        detectedFormat = BackupImportFormat.BackupJsonl
                    }

                    BackupBookRecord.TYPE -> {
                        val record = decodeBackupBookRecord(element, lineNumber)
                        importedBooks.upsert(record.book)
                        record.book.loanInfo?.borrowerName?.let(importedBorrowerNames::add)
                        detectedFormat = BackupImportFormat.BackupJsonl
                    }

                    else -> throw IllegalArgumentException("Line $lineNumber: Unsupported record type '$type'.")
                }
            }

        if (importedBooks.isEmpty()) {
            throw IllegalArgumentException("No book records were found in the selected file.")
        }

        val normalizedBorrowers = normalizeBorrowerNames(importedBorrowerNames)
        store.set(BookCollection(importedBooks))
        borrowerHistoryStore.set(BorrowerHistory(names = normalizedBorrowers))

        return BackupImportSummary(
            importedBooks = importedBooks.size,
            importedBorrowerHistoryCount = normalizedBorrowers.size,
            format = when {
                seenMeta || detectedFormat == BackupImportFormat.BackupJsonl -> BackupImportFormat.BackupJsonl
                else -> BackupImportFormat.BooksJsonl
            },
        )
    }

    private suspend fun snapshot(): RepositorySnapshot {
        val books = store.get()?.items.orEmpty()
        val borrowerNames = normalizeBorrowerNames(
            borrowerHistoryStore.get()?.names.orEmpty() + books.mapNotNull { it.loanInfo?.borrowerName },
        )
        return RepositorySnapshot(
            books = books,
            borrowerHistory = borrowerNames,
        )
    }
}

private data class RepositorySnapshot(
    val books: List<Book>,
    val borrowerHistory: List<String>,
)

@Serializable
private data class BackupMetaRecord(
    val type: String = TYPE,
    val version: Int = BACKUP_FORMAT_VERSION,
    val format: String = BACKUP_FORMAT_NAME,
    val exportedAt: String,
) {
    companion object {
        const val TYPE = "meta"
    }
}

@Serializable
private data class BackupBorrowerRecord(
    val type: String = TYPE,
    val name: String,
) {
    companion object {
        const val TYPE = "borrower"
    }
}

@Serializable
private data class BackupBookRecord(
    val type: String = TYPE,
    val book: Book,
) {
    companion object {
        const val TYPE = "book"
    }
}

private const val BACKUP_FORMAT_NAME = "recobook-backup-jsonl"
private const val BACKUP_FORMAT_VERSION = 1

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
}

private val backupJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

private fun normalizeBorrowerNames(names: Iterable<String>): List<String> {
    return names
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinctBy { it.lowercase() }
        .sortedBy { it.lowercase() }
}

private fun List<Book>.indexOfDuplicate(book: Book): Int {
    return indexOfFirst {
        it.id == book.id ||
            (book.isbn13 != null && it.isbn13 == book.isbn13) ||
            (book.isbn10 != null && it.isbn10 == book.isbn10) ||
            it.isbn == book.isbn
    }
}

private fun MutableList<Book>.upsert(book: Book) {
    val index = indexOfDuplicate(book)
    if (index >= 0) {
        set(index, book)
    } else {
        add(book)
    }
}

private fun decodeJsonElement(
    line: String,
    lineNumber: Int,
) = try {
    json.parseToJsonElement(line)
} catch (_: SerializationException) {
    throw IllegalArgumentException("Line $lineNumber: Invalid JSON.")
}

private fun decodeBook(
    element: kotlinx.serialization.json.JsonElement,
    lineNumber: Int,
) = try {
    json.decodeFromJsonElement(Book.serializer(), element)
} catch (_: SerializationException) {
    throw IllegalArgumentException("Line $lineNumber: Invalid book record.")
}

private fun decodeMetaRecord(
    element: kotlinx.serialization.json.JsonElement,
    lineNumber: Int,
) = try {
    json.decodeFromJsonElement(BackupMetaRecord.serializer(), element)
} catch (_: SerializationException) {
    throw IllegalArgumentException("Line $lineNumber: Invalid backup metadata record.")
}

private fun decodeBorrowerRecord(
    element: kotlinx.serialization.json.JsonElement,
    lineNumber: Int,
) = try {
    json.decodeFromJsonElement(BackupBorrowerRecord.serializer(), element)
} catch (_: SerializationException) {
    throw IllegalArgumentException("Line $lineNumber: Invalid borrower record.")
}

private fun decodeBackupBookRecord(
    element: kotlinx.serialization.json.JsonElement,
    lineNumber: Int,
) = try {
    json.decodeFromJsonElement(BackupBookRecord.serializer(), element)
} catch (_: SerializationException) {
    throw IllegalArgumentException("Line $lineNumber: Invalid backup book record.")
}

private fun Book.shareStatusLabel(): String = if (loanInfo == null) "Available" else "Lent out"

private fun String?.escapeMarkdownCell(): String = this
    ?.replace("\r\n", "\n")
    ?.replace('\n', ' ')
    ?.replace("|", "\\|")
    ?.ifBlank { null }
    ?: ""

private fun String.orDash(): String = if (isBlank()) "-" else this

private fun String.toCsvField(): String {
    val normalized = replace("\r\n", "\n").replace('\r', '\n')
    val escaped = normalized.replace("\"", "\"\"")
    return "\"$escaped\""
}

private fun timestampSuffix(): String {
    val dateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val datePart = dateTime.date.toString().replace("-", "")
    return buildString {
        append(datePart)
        append('-')
        append(dateTime.hour.toString().padStart(2, '0'))
        append(dateTime.minute.toString().padStart(2, '0'))
        append(dateTime.second.toString().padStart(2, '0'))
    }
}

private fun currentInstantString(): String {
    return Clock.System.now().toString()
}
