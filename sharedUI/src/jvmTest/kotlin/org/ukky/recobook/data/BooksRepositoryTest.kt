package org.ukky.recobook.data

import io.github.xxfast.kstore.file.storeOf
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json
import kotlin.test.*

class BooksRepositoryTest {

    private val tmpFile: java.io.File = java.io.File.createTempFile("kstore-repo-test", ".json")
    private val borrowerTmpFile: java.io.File = java.io.File.createTempFile("kstore-borrowers-test", ".json")
    private val testJson = Json { ignoreUnknownKeys = true; isLenient = true }

    @BeforeTest
    fun setUp() {
        tmpFile.delete()
        borrowerTmpFile.delete()
    }

    @AfterTest
    fun tearDown() {
        tmpFile.delete()
        borrowerTmpFile.delete()
    }

    private fun newStore() = storeOf(file = Path(tmpFile.absolutePath), default = BookCollection())
    private fun newBorrowerStore() = storeOf(file = Path(borrowerTmpFile.absolutePath), default = BorrowerHistory())

    private fun mockApi(responseJson: String? = null, throwOn: Exception? = null): BooksApi {
        val engine = MockEngine { _ ->
            if (throwOn != null) throw throwOn
            respond(
                content = responseJson ?: EMPTY_JSON,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        return BooksApi(HttpClient(engine) { install(ContentNegotiation) { json(testJson) } })
    }

    private fun newRepository(
        store: io.github.xxfast.kstore.KStore<BookCollection> = newStore(),
        borrowerStore: io.github.xxfast.kstore.KStore<BorrowerHistory> = newBorrowerStore(),
        api: BooksApi = mockApi(),
    ) = BooksRepository(store, borrowerStore, api)

    // --- books Flow ---

    @Test
    fun books_initialState_emitsEmptyList() = runTest {
        val repo = newRepository()
        assertEquals(emptyList(), repo.books.first())
    }

    // --- addByIsbn 新規追加 ---

    @Test
    fun addByIsbn_newBook_returnsSuccessNotUpdated() = runTest {
        val repo = newRepository(api = mockApi(SINGLE_BOOK_JSON))
        val result = repo.addByIsbn("9784873119038")
        assertIs<BookAddResult.Success>(result)
        assertEquals(false, result.updated)
        assertEquals("9784873119038", result.book.id)
    }

    @Test
    fun addByIsbn_newBook_prependedToFront() = runTest {
        val store = newStore()
        val existing = Book(id = "existing", isbn = "existing-isbn", title = "既存の本")
        store.set(BookCollection(listOf(existing)))

        val repo = newRepository(store = store, api = mockApi(SINGLE_BOOK_JSON))
        repo.addByIsbn("9784873119038")

        val books = repo.books.first()
        assertEquals(2, books.size)
        assertEquals("9784873119038", books[0].id)
        assertEquals("existing", books[1].id)
    }

    // --- addByIsbn 重複検出 ---

    @Test
    fun addByIsbn_duplicateById_returnsSuccessUpdated() = runTest {
        val store = newStore()
        val originalAddedAt = 12345L
        store.set(BookCollection(listOf(
            Book(id = "9784873119038", isbn = "9784873119038", title = "旧タイトル", addedAt = originalAddedAt)
        )))

        val repo = newRepository(store = store, api = mockApi(SINGLE_BOOK_JSON))
        val result = repo.addByIsbn("9784873119038")

        assertIs<BookAddResult.Success>(result)
        assertTrue(result.updated)
        val books = repo.books.first()
        assertEquals(1, books.size)
        assertEquals(originalAddedAt, books[0].addedAt)
        assertEquals("テスト書籍", books[0].title)
    }

    @Test
    fun addByIsbn_duplicateByIsbn13_returnsSuccessUpdated() = runTest {
        val store = newStore()
        store.set(BookCollection(listOf(
            Book(id = "other-id", isbn = "9784873119038", isbn13 = "9784873119038", title = "旧本")
        )))

        val repo = newRepository(store = store, api = mockApi(SINGLE_BOOK_JSON))
        val result = repo.addByIsbn("9784873119038")

        assertIs<BookAddResult.Success>(result)
        assertTrue(result.updated)
        assertEquals(1, repo.books.first().size)
    }

    @Test
    fun addByIsbn_duplicateByIsbn10_returnsSuccessUpdated() = runTest {
        val store = newStore()
        store.set(BookCollection(listOf(
            Book(id = "other-id", isbn = "4873119030", isbn10 = "4873119030", title = "旧本")
        )))

        val apiJson = SINGLE_BOOK_JSON
        val repo = newRepository(store = store, api = mockApi(apiJson))
        val result = repo.addByIsbn("9784873119038")

        assertIs<BookAddResult.Success>(result)
        assertTrue(result.updated)
        assertEquals(1, repo.books.first().size)
    }

    @Test
    fun addByIsbn_duplicateByCanonicalIsbn_updatesInPlaceAndPreservesAddedAt() = runTest {
        val store = newStore()
        val originalAddedAt = 12345L
        val originalLoanInfo = LoanInfo("Charlie")
        store.set(BookCollection(listOf(
            Book(id = "first", isbn = "first-isbn", title = "先頭の本"),
            Book(
                id = "other-id",
                isbn = "9784873119038",
                title = "旧タイトル",
                addedAt = originalAddedAt,
                loanInfo = originalLoanInfo,
            ),
        )))

        val repo = newRepository(store = store, api = mockApi(SINGLE_BOOK_JSON))
        val result = repo.addByIsbn("9784873119038")

        assertIs<BookAddResult.Success>(result)
        assertTrue(result.updated)
        val books = repo.books.first()
        assertEquals(2, books.size)
        assertEquals("first", books[0].id)
        assertEquals("9784873119038", books[1].id)
        assertEquals("テスト書籍", books[1].title)
        assertEquals(originalAddedAt, books[1].addedAt)
        assertEquals(originalLoanInfo, books[1].loanInfo)
    }

    // --- addByIsbn エラーケース ---

    @Test
    fun addByIsbn_apiReturnsNull_returnsNotFound() = runTest {
        val repo = newRepository(api = mockApi(EMPTY_JSON))
        val result = repo.addByIsbn("0000000000000")
        assertIs<BookAddResult.NotFound>(result)
        assertEquals("0000000000000", result.isbn)
    }

    @Test
    fun addByIsbn_apiThrowsException_returnsError() = runTest {
        val repo = newRepository(api = mockApi(throwOn = RuntimeException("Network error")))
        val result = repo.addByIsbn("9784873119038")
        assertIs<BookAddResult.Error>(result)
        assertEquals("9784873119038", result.isbn)
        assertEquals("Network error", result.message)
    }

    // --- removeById ---

    @Test
    fun removeById_existingBook_removedFromCollection() = runTest {
        val store = newStore()
        store.set(BookCollection(listOf(
            Book(id = "id-1", isbn = "isbn-1", title = "本1"),
            Book(id = "id-2", isbn = "isbn-2", title = "本2"),
        )))

        val repo = newRepository(store = store)
        repo.removeById("id-1")

        val books = repo.books.first()
        assertEquals(1, books.size)
        assertEquals("id-2", books[0].id)
    }

    @Test
    fun removeById_nonExistentId_collectionUnchanged() = runTest {
        val store = newStore()
        val book = Book(id = "id-1", isbn = "isbn-1", title = "本1")
        store.set(BookCollection(listOf(book)))

        val repo = newRepository(store = store)
        repo.removeById("non-existent")

        val books = repo.books.first()
        assertEquals(1, books.size)
        assertEquals("id-1", books[0].id)
    }

    // --- reorderBooks ---

    @Test
    fun reorderBooks_moveForward_itemAtCorrectPosition() = runTest {
        val store = newStore()
        store.set(BookCollection(listOf(
            Book(id = "id-1", isbn = "isbn-1", title = "本1"),
            Book(id = "id-2", isbn = "isbn-2", title = "本2"),
            Book(id = "id-3", isbn = "isbn-3", title = "本3"),
        )))

        val repo = newRepository(store = store)
        repo.reorderBooks(fromIndex = 0, toIndex = 2)

        val books = repo.books.first()
        assertEquals(3, books.size)
        assertEquals("id-2", books[0].id)
        assertEquals("id-3", books[1].id)
        assertEquals("id-1", books[2].id)
    }

    @Test
    fun reorderBooks_moveBackward_itemAtCorrectPosition() = runTest {
        val store = newStore()
        store.set(BookCollection(listOf(
            Book(id = "id-1", isbn = "isbn-1", title = "本1"),
            Book(id = "id-2", isbn = "isbn-2", title = "本2"),
            Book(id = "id-3", isbn = "isbn-3", title = "本3"),
        )))

        val repo = newRepository(store = store)
        repo.reorderBooks(fromIndex = 2, toIndex = 0)

        val books = repo.books.first()
        assertEquals(3, books.size)
        assertEquals("id-3", books[0].id)
        assertEquals("id-1", books[1].id)
        assertEquals("id-2", books[2].id)
    }

    @Test
    fun reorderBooks_adjacentItems_swapped() = runTest {
        val store = newStore()
        store.set(BookCollection(listOf(
            Book(id = "id-1", isbn = "isbn-1", title = "本1"),
            Book(id = "id-2", isbn = "isbn-2", title = "本2"),
        )))

        val repo = newRepository(store = store)
        repo.reorderBooks(fromIndex = 0, toIndex = 1)

        val books = repo.books.first()
        assertEquals(2, books.size)
        assertEquals("id-2", books[0].id)
        assertEquals("id-1", books[1].id)
    }

    @Test
    fun reorderBooks_sameIndex_collectionUnchanged() = runTest {
        val store = newStore()
        store.set(BookCollection(listOf(
            Book(id = "id-1", isbn = "isbn-1", title = "本1"),
            Book(id = "id-2", isbn = "isbn-2", title = "本2"),
        )))

        val repo = newRepository(store = store)
        repo.reorderBooks(fromIndex = 0, toIndex = 0)

        val books = repo.books.first()
        assertEquals("id-1", books[0].id)
        assertEquals("id-2", books[1].id)
    }

    @Test
    fun reorderBooks_outOfBoundsToIndex_collectionUnchanged() = runTest {
        val store = newStore()
        val originals = listOf(
            Book(id = "id-1", isbn = "isbn-1", title = "本1"),
            Book(id = "id-2", isbn = "isbn-2", title = "本2"),
        )
        store.set(BookCollection(originals))

        val repo = newRepository(store = store)
        repo.reorderBooks(fromIndex = 0, toIndex = 5)

        val books = repo.books.first()
        assertEquals("id-1", books[0].id)
        assertEquals("id-2", books[1].id)
    }

    @Test
    fun reorderBooks_negativeFromIndex_collectionUnchanged() = runTest {
        val store = newStore()
        val originals = listOf(
            Book(id = "id-1", isbn = "isbn-1", title = "本1"),
            Book(id = "id-2", isbn = "isbn-2", title = "本2"),
        )
        store.set(BookCollection(originals))

        val repo = newRepository(store = store)
        repo.reorderBooks(fromIndex = -1, toIndex = 1)

        val books = repo.books.first()
        assertEquals("id-1", books[0].id)
        assertEquals("id-2", books[1].id)
    }

    @Test
    fun reorderBooks_singleItem_collectionUnchanged() = runTest {
        val store = newStore()
        store.set(BookCollection(listOf(
            Book(id = "id-1", isbn = "isbn-1", title = "本1"),
        )))

        val repo = newRepository(store = store)
        repo.reorderBooks(fromIndex = 0, toIndex = 0)

        val books = repo.books.first()
        assertEquals(1, books.size)
        assertEquals("id-1", books[0].id)
    }

    @Test
    fun reorderBooks_multipleSequentialMoves_finalOrderCorrect() = runTest {
        val store = newStore()
        store.set(BookCollection(listOf(
            Book(id = "id-A", isbn = "isbn-A", title = "A"),
            Book(id = "id-B", isbn = "isbn-B", title = "B"),
            Book(id = "id-C", isbn = "isbn-C", title = "C"),
            Book(id = "id-D", isbn = "isbn-D", title = "D"),
        )))

        val repo = newRepository(store = store)
        // A を末尾へ: [A,B,C,D] → [B,C,D,A]
        repo.reorderBooks(fromIndex = 0, toIndex = 3)
        // D を先頭へ: [B,C,D,A] → [A,B,C,D] ではなく D=idx2 → 0: [D,B,C,A]
        // (元の store は [B,C,D,A])
        repo.reorderBooks(fromIndex = 2, toIndex = 0)

        val books = repo.books.first()
        assertEquals("id-D", books[0].id)
        assertEquals("id-B", books[1].id)
        assertEquals("id-C", books[2].id)
        assertEquals("id-A", books[3].id)
    }

    @Test
    fun updateLoan_existingBook_setsBorrowerAndStoresHistorySortedByName() = runTest {
        val store = newStore()
        val borrowerStore = newBorrowerStore()
        borrowerStore.set(BorrowerHistory(listOf("Zoe", "alice")))
        store.set(
            BookCollection(
                listOf(
                    Book(id = "id-1", isbn = "isbn-1", title = "本1"),
                ),
            ),
        )

        val repo = newRepository(store = store, borrowerStore = borrowerStore)
        repo.updateLoan("id-1", " Bob ")

        val books = repo.books.first()
        assertEquals("Bob", books.single().loanInfo?.borrowerName)
        assertEquals(listOf("alice", "Bob", "Zoe"), repo.borrowerHistory.first())
    }

    @Test
    fun updateLoan_returnedBook_clearsLoanButKeepsBorrowerHistory() = runTest {
        val store = newStore()
        val borrowerStore = newBorrowerStore()
        borrowerStore.set(BorrowerHistory(listOf("Alice", "Bob")))
        store.set(
            BookCollection(
                listOf(
                    Book(
                        id = "id-1",
                        isbn = "isbn-1",
                        title = "本1",
                        loanInfo = LoanInfo("Alice"),
                    ),
                ),
            ),
        )

        val repo = newRepository(store = store, borrowerStore = borrowerStore)
        repo.updateLoan("id-1", null)

        val books = repo.books.first()
        assertNull(books.single().loanInfo)
        assertEquals(listOf("Alice", "Bob"), repo.borrowerHistory.first())
    }

    companion object {
        val SINGLE_BOOK_JSON = """
            [
              {
                "onix": {
                  "RecordReference": "9784873119038",
                  "ProductIdentifier": {
                    "IDValue": "9784873119038"
                  },
                  "DescriptiveDetail": {
                    "TitleDetail": {
                      "TitleElement": {
                        "TitleText": {
                          "content": "テスト書籍"
                        }
                      }
                    },
                    "Contributor": [
                      {
                        "PersonName": {
                          "content": "著者１"
                        }
                      }
                    ]
                  }
                },
                "summary": {
                  "isbn": "9784873119038",
                  "title": "テスト書籍"
                }
              }
            ]
        """.trimIndent()

        val EMPTY_JSON = "[null]"
    }
}
