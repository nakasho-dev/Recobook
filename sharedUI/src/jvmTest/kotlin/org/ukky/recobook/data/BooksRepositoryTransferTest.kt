package org.ukky.recobook.data

import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json
import kotlin.test.*

class BooksRepositoryTransferTest {

    private val booksFile = java.io.File.createTempFile("kstore-books-transfer", ".json")
    private val borrowersFile = java.io.File.createTempFile("kstore-borrowers-transfer", ".json")
    private val testJson = Json { ignoreUnknownKeys = true; isLenient = true }

    @BeforeTest
    fun setUp() {
        booksFile.delete()
        borrowersFile.delete()
    }

    @AfterTest
    fun tearDown() {
        booksFile.delete()
        borrowersFile.delete()
    }

    @Test
    fun exportBackupJsonl_roundTripsBooksAndBorrowerHistory() = runTest {
        val sourceRepo = newRepository()
        seedSourceData(sourceRepo)

        val exportFile = sourceRepo.exportBackupJsonl()
        val lines = exportFile.lines.toList()

        assertTrue(exportFile.suggestedFileName.endsWith(".jsonl"))
        assertEquals("application/x-ndjson", exportFile.mimeType)
        assertTrue(lines.first().contains("\"type\":\"meta\""))
        assertTrue(lines.any { it.contains("\"type\":\"borrower\"") })
        assertTrue(lines.any { it.contains("\"type\":\"book\"") })

        val importedRepo = newRepository(
            store = newStore(file = siblingFile(booksFile, "import-books.json")),
            borrowerStore = newBorrowerStore(file = siblingFile(borrowersFile, "import-borrowers.json")),
        )
        val summary = importedRepo.importBackupJsonl(lines.asSequence())

        assertEquals(BackupImportFormat.BackupJsonl, summary.format)
        assertEquals(2, summary.importedBooks)
        assertEquals(3, summary.importedBorrowerHistoryCount)

        val books = importedRepo.books.first()
        assertEquals(listOf("id-1", "id-2"), books.map { it.id })
        assertEquals("Alice", books.first().loanInfo?.borrowerName)
        assertEquals(listOf("Alice", "Bob", "Charlie"), importedRepo.borrowerHistory.first())
    }

    @Test
    fun exportBooksJsonl_canBeImportedAndKeepsLoanInfo() = runTest {
        val sourceRepo = newRepository()
        seedSourceData(sourceRepo)

        val exportFile = sourceRepo.exportBooksJsonl()
        val lines = exportFile.lines.toList()

        assertEquals(2, lines.size)
        assertTrue(lines.first().contains("\"loanInfo\":{\"borrowerName\":\"Alice\"}"))

        val importedRepo = newRepository(
            store = newStore(file = siblingFile(booksFile, "books-only-import.json")),
            borrowerStore = newBorrowerStore(file = siblingFile(borrowersFile, "books-only-borrowers.json")),
        )
        val summary = importedRepo.importBackupJsonl(lines.asSequence())

        assertEquals(BackupImportFormat.BooksJsonl, summary.format)
        assertEquals(2, summary.importedBooks)
        assertEquals(listOf("Alice"), importedRepo.borrowerHistory.first())
    }

    @Test
    fun exportShareMarkdown_hidesBorrowerName_butShowsLoanStatus() = runTest {
        val repository = newRepository()
        seedSourceData(repository)

        val content = repository.exportShareMarkdown().lines.toList().joinToString("\n")

        assertTrue(content.contains("Lent out"))
        assertTrue(content.contains("Available"))
        assertFalse(content.contains("Charlie"))
        assertFalse(content.contains("Alice"))
    }

    @Test
    fun exportShareCsv_hidesBorrowerName_butShowsLoanStatus() = runTest {
        val repository = newRepository()
        seedSourceData(repository)

        val content = repository.exportShareCsv().lines.toList().joinToString("\n")

        assertTrue(content.contains("\"Lent out\""))
        assertTrue(content.contains("\"Available\""))
        assertFalse(content.contains("Charlie"))
        assertFalse(content.contains("Alice"))
    }

    private suspend fun seedSourceData(repository: BooksRepository) {
        repository.importBackupJsonl(
            sequenceOf(
                """{"type":"meta","version":1,"format":"recobook-backup-jsonl","exportedAt":"2026-07-01T00:00:00Z"}""",
                """{"type":"borrower","name":"Bob"}""",
                """{"type":"borrower","name":"Charlie"}""",
                """{"type":"book","book":{"id":"id-1","isbn":"isbn-1","title":"Book 1","loanInfo":{"borrowerName":"Alice"}}}""",
                """{"type":"book","book":{"id":"id-2","isbn":"isbn-2","title":"Book 2","authors":["Author"],"publisher":"Publisher"}}""",
            ),
        )
    }

    private fun newRepository(
        store: KStore<BookCollection> = newStore(booksFile),
        borrowerStore: KStore<BorrowerHistory> = newBorrowerStore(borrowersFile),
    ): BooksRepository {
        return BooksRepository(store, borrowerStore, unusedApi())
    }

    private fun newStore(file: java.io.File) = storeOf(
        file = Path(file.absolutePath),
        default = BookCollection(),
    )

    private fun newBorrowerStore(file: java.io.File) = storeOf(
        file = Path(file.absolutePath),
        default = BorrowerHistory(),
    )

    private fun unusedApi(): BooksApi {
        val engine = MockEngine {
            error("BooksApi should not be called in transfer tests.")
        }
        return BooksApi(HttpClient(engine) {
            install(ContentNegotiation) { json(testJson) }
        })
    }

    private fun siblingFile(
        original: java.io.File,
        name: String,
    ) = java.io.File(original.parentFile, name)
}
