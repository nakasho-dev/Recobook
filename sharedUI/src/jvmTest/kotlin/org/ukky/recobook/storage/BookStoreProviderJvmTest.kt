package org.ukky.recobook.storage

import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.ukky.recobook.data.Book
import org.ukky.recobook.data.BookCollection
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BookStoreProviderJvmTest {

    private val homeDir: String get() = System.getProperty("user.home") ?: "."
    private val storeDirPath: String get() = "$homeDir/.recobook"
    private val storeFilePath: String get() = "$storeDirPath/books.json"

    @BeforeTest
    fun setUp() {
        val filePath = Path(storeFilePath)
        if (SystemFileSystem.exists(filePath)) SystemFileSystem.delete(filePath)
    }

    @AfterTest
    fun tearDown() {
        val filePath = Path(storeFilePath)
        if (SystemFileSystem.exists(filePath)) SystemFileSystem.delete(filePath)
    }

    // --- 基本動作 ---

    @Test
    fun createBookStore_returnsNonNull() {
        assertNotNull(createBookStore())
    }

    @Test
    fun createBookStore_createsRecobookDirectory() {
        createBookStore()
        assertTrue(
            SystemFileSystem.exists(Path(storeDirPath)),
            ".recobook ディレクトリが作成されていること。期待パス: $storeDirPath",
        )
    }

    // --- デフォルト値 ---

    @Test
    fun createBookStore_defaultValueIsEmptyCollection() = runTest {
        val store = createBookStore()
        val collection = store.get()
        assertNotNull(collection)
        assertEquals(emptyList(), collection.items)
    }

    // --- 読み書き ---

    @Test
    fun createBookStore_canStoreAndRetrieveBooks() = runTest {
        val store = createBookStore()
        val testBook = Book(id = "jvm-001", isbn = "9784873119038", title = "JVM テスト書籍")
        store.set(BookCollection(listOf(testBook)))

        val retrieved = store.get()
        assertNotNull(retrieved)
        assertEquals(1, retrieved.items.size)
        assertEquals(testBook.id, retrieved.items[0].id)
        assertEquals(testBook.title, retrieved.items[0].title)
    }

    // --- 永続化 ---

    @Test
    fun createBookStore_persistenceAcrossInstances() = runTest {
        val testBook = Book(id = "persist-jvm-001", isbn = "9784873119039", title = "JVM 永続化テスト")
        createBookStore().set(BookCollection(listOf(testBook)))
        val retrieved = createBookStore().get()
        assertNotNull(retrieved)
        assertEquals(1, retrieved.items.size)
        assertEquals(testBook.id, retrieved.items[0].id)
    }

    // --- ファイルパス ---

    @Test
    fun createBookStore_storeFileIsUnderHomeDirectory() = runTest {
        createBookStore().set(BookCollection())
        assertTrue(
            SystemFileSystem.exists(Path(storeFilePath)),
            "ストアファイルが作成されていること。期待パス: $storeFilePath",
        )
        assertTrue(
            storeFilePath.endsWith("/.recobook/books.json"),
            "ストアファイルパスが ~/.recobook/books.json で終わること。実際のパス: $storeFilePath",
        )
    }
}
