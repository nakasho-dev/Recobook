package org.ukky.recobook.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BooksApiTest {

    private val testJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun createApi(responseJson: String, status: HttpStatusCode = HttpStatusCode.OK): BooksApi {
        val engine = MockEngine { _ ->
            respond(
                content = responseJson,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(testJson) }
        }
        return BooksApi(client)
    }

    // --- 基本動作 ---

    @Test
    fun fetchByIsbn_validResponse_returnsBook() = runTest {
        val book = createApi(SINGLE_BOOK_JSON).fetchByIsbn("9784873119038")
        assertNotNull(book)
        assertEquals("test-vol-id", book.id)
        assertEquals("テスト書籍", book.title)
    }

    @Test
    fun fetchByIsbn_noItems_returnsNull() = runTest {
        val book = createApi(EMPTY_RESPONSE_JSON).fetchByIsbn("0000000000000")
        assertNull(book)
    }

    // --- ISBN フィールドのマッピング ---

    @Test
    fun fetchByIsbn_isbn13UsedAsCanonical() = runTest {
        val book = createApi(SINGLE_BOOK_JSON).fetchByIsbn("9784873119038")
        assertNotNull(book)
        assertEquals("9784873119038", book.isbn)
        assertEquals("9784873119038", book.isbn13)
        assertEquals("4873119030", book.isbn10)
    }

    @Test
    fun fetchByIsbn_noIsbn13_isbn10UsedAsCanonical() = runTest {
        val book = createApi(ISBN10_ONLY_JSON).fetchByIsbn("4873119030")
        assertNotNull(book)
        assertEquals("4873119030", book.isbn)
        assertNull(book.isbn13)
        assertEquals("4873119030", book.isbn10)
    }

    @Test
    fun fetchByIsbn_noIdentifiers_fallbackIsbn() = runTest {
        val book = createApi(NO_IDENTIFIERS_JSON).fetchByIsbn("fallback-isbn")
        assertNotNull(book)
        assertEquals("fallback-isbn", book.isbn)
    }

    // --- サムネイル URL ---

    @Test
    fun fetchByIsbn_httpThumbnail_convertedToHttps() = runTest {
        val book = createApi(SINGLE_BOOK_JSON).fetchByIsbn("9784873119038")
        assertNotNull(book)
        assertTrue(
            book.thumbnailUrl?.startsWith("https://") == true,
            "thumbnailUrl は https:// で始まること。実際の値: ${book.thumbnailUrl}",
        )
    }

    @Test
    fun fetchByIsbn_noThumbnail_nullUrl() = runTest {
        val book = createApi(NO_THUMBNAIL_JSON).fetchByIsbn("9784873119038")
        assertNotNull(book)
        assertNull(book.thumbnailUrl)
    }

    // --- タイトル ---

    @Test
    fun fetchByIsbn_blankTitle_usesIsbnAsFallback() = runTest {
        val book = createApi(BLANK_TITLE_JSON).fetchByIsbn("9784873119038")
        assertNotNull(book)
        assertEquals("9784873119038", book.title)
    }

    // --- 全フィールドのマッピング ---

    @Test
    fun fetchByIsbn_allFieldsMapped() = runTest {
        val book = createApi(SINGLE_BOOK_JSON).fetchByIsbn("9784873119038")
        assertNotNull(book)
        assertEquals(listOf("著者１", "著者２"), book.authors)
        assertEquals("テスト出版社", book.publisher)
        assertEquals("2023-01-01", book.publishedDate)
        assertEquals("テストの説明", book.description)
        assertEquals(300, book.pageCount)
        assertEquals(listOf("Computers"), book.categories)
    }

    companion object {
        val SINGLE_BOOK_JSON = """
            {
              "totalItems": 1,
              "items": [
                {
                  "id": "test-vol-id",
                  "volumeInfo": {
                    "title": "テスト書籍",
                    "authors": ["著者１", "著者２"],
                    "publisher": "テスト出版社",
                    "publishedDate": "2023-01-01",
                    "description": "テストの説明",
                    "industryIdentifiers": [
                      {"type": "ISBN_13", "identifier": "9784873119038"},
                      {"type": "ISBN_10", "identifier": "4873119030"}
                    ],
                    "imageLinks": {
                      "thumbnail": "http://books.google.com/books?id=test&zoom=1",
                      "smallThumbnail": "http://books.google.com/books?id=test&zoom=5"
                    },
                    "pageCount": 300,
                    "categories": ["Computers"]
                  }
                }
              ]
            }
        """.trimIndent()

        val EMPTY_RESPONSE_JSON = """{"totalItems": 0, "items": []}"""

        val BLANK_TITLE_JSON = """
            {
              "totalItems": 1,
              "items": [
                {
                  "id": "vol-id",
                  "volumeInfo": {
                    "title": "",
                    "industryIdentifiers": [
                      {"type": "ISBN_13", "identifier": "9784873119038"}
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val ISBN10_ONLY_JSON = """
            {
              "totalItems": 1,
              "items": [
                {
                  "id": "vol-id",
                  "volumeInfo": {
                    "title": "ISBN10のみの本",
                    "industryIdentifiers": [
                      {"type": "ISBN_10", "identifier": "4873119030"}
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val NO_IDENTIFIERS_JSON = """
            {
              "totalItems": 1,
              "items": [
                {
                  "id": "vol-id",
                  "volumeInfo": {
                    "title": "識別子なしの本",
                    "industryIdentifiers": []
                  }
                }
              ]
            }
        """.trimIndent()

        val NO_THUMBNAIL_JSON = """
            {
              "totalItems": 1,
              "items": [
                {
                  "id": "vol-id",
                  "volumeInfo": {
                    "title": "サムネイルなしの本",
                    "industryIdentifiers": [
                      {"type": "ISBN_13", "identifier": "9784873119038"}
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
    }
}
