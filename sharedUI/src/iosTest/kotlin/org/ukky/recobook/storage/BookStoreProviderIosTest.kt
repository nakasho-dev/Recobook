package org.ukky.recobook.storage

import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.ukky.recobook.data.Book
import org.ukky.recobook.data.BookCollection
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BookStoreProviderIosTest {

    /** iOS ドキュメントディレクトリのパスを取得する */
    private val documentsPath: String
        get() {
            val paths = NSSearchPathForDirectoriesInDomains(
                directory = NSDocumentDirectory,
                domainMask = NSUserDomainMask,
                expandTilde = true,
            )
            return (paths.firstOrNull() as? String) ?: "."
        }

    private val recobookDirPath get() = "$documentsPath/recobook"
    private val storeFilePath get() = "$recobookDirPath/books.json"

    @BeforeTest
    fun setUp() {
        // テスト前にストアファイルを削除してクリーンな状態にする
        val filePath = Path(storeFilePath)
        if (SystemFileSystem.exists(filePath)) {
            SystemFileSystem.delete(filePath)
        }
    }

    @AfterTest
    fun tearDown() {
        // テスト後にストアファイルを削除してクリーンアップする
        val filePath = Path(storeFilePath)
        if (SystemFileSystem.exists(filePath)) {
            SystemFileSystem.delete(filePath)
        }
    }

    // ─── 基本動作 ────────────────────────────────────────────

    /** createBookStore() が null でないストアインスタンスを返すこと */
    @Test
    fun createBookStore_returnsNonNull() {
        val store = createBookStore()
        assertNotNull(store)
    }

    /** createBookStore() を呼ぶと recobook ディレクトリが作成されること */
    @Test
    fun createBookStore_createsRecobookDirectory() {
        createBookStore()
        val dirPath = Path(recobookDirPath)
        assertTrue(
            SystemFileSystem.exists(dirPath),
            "recobook ディレクトリが作成されていること。期待パス: $recobookDirPath",
        )
    }

    // ─── デフォルト値 ──────────────────────────────────────

    /** ストアファイルが存在しない場合、get() が空の BookCollection を返すこと */
    @Test
    fun createBookStore_defaultValueIsEmptyCollection() = runTest {
        val store = createBookStore()
        val collection = store.get()
        assertNotNull(collection, "デフォルト値は null ではないこと")
        assertEquals(emptyList(), collection.items, "デフォルト値は空のコレクションであること")
    }

    // ─── 読み書き ──────────────────────────────────────────

    /** set() で保存した Book を get() で取得できること */
    @Test
    fun createBookStore_canStoreAndRetrieveBooks() = runTest {
        val store = createBookStore()
        val testBook = Book(
            id = "test-id-001",
            isbn = "9784873119038",
            title = "テスト書籍",
            authors = listOf("著者A"),
        )

        store.set(BookCollection(listOf(testBook)))
        val retrieved = store.get()

        assertNotNull(retrieved)
        assertEquals(1, retrieved.items.size)
        assertEquals(testBook.id, retrieved.items[0].id)
        assertEquals(testBook.title, retrieved.items[0].title)
        assertEquals(testBook.authors, retrieved.items[0].authors)
    }

    /** update() で既存コレクションから Book を削除できること */
    @Test
    fun createBookStore_canRemoveBookViaUpdate() = runTest {
        val book1 = Book(id = "book-1", isbn = "isbn-1", title = "本1")
        val book2 = Book(id = "book-2", isbn = "isbn-2", title = "本2")
        val store = createBookStore()

        store.set(BookCollection(listOf(book1, book2)))
        store.update { current ->
            BookCollection(current?.items.orEmpty().filterNot { it.id == "book-1" })
        }

        val retrieved = store.get()
        assertNotNull(retrieved)
        assertEquals(1, retrieved.items.size)
        assertEquals("book-2", retrieved.items[0].id)
    }

    /** 複数の Book を正しい順序で保存・取得できること */
    @Test
    fun createBookStore_preservesInsertionOrder() = runTest {
        val books = listOf(
            Book(id = "id-1", isbn = "isbn-1", title = "本A"),
            Book(id = "id-2", isbn = "isbn-2", title = "本B"),
            Book(id = "id-3", isbn = "isbn-3", title = "本C"),
        )
        val store = createBookStore()

        store.set(BookCollection(books))
        val retrieved = store.get()

        assertNotNull(retrieved)
        assertEquals(3, retrieved.items.size)
        assertEquals("id-1", retrieved.items[0].id)
        assertEquals("id-2", retrieved.items[1].id)
        assertEquals("id-3", retrieved.items[2].id)
    }

    // ─── 永続化 ────────────────────────────────────────────

    /** 別のインスタンスを生成しても同じファイルを参照し、データが永続化されること */
    @Test
    fun createBookStore_persistenceAcrossInstances() = runTest {
        val testBook = Book(
            id = "persist-test-001",
            isbn = "9784873119039",
            title = "永続化テスト書籍",
            authors = listOf("著者B"),
        )

        // 1つ目のインスタンスで保存
        createBookStore().set(BookCollection(listOf(testBook)))

        // 2つ目のインスタンスで読み込み
        val retrieved = createBookStore().get()

        assertNotNull(retrieved)
        assertEquals(1, retrieved.items.size)
        assertEquals(testBook.id, retrieved.items[0].id)
        assertEquals(testBook.title, retrieved.items[0].title)
    }

    // ─── ファイルパス ──────────────────────────────────────

    /** ストアファイルが iOS ドキュメントディレクトリの recobook/ 以下に生成されること */
    @Test
    fun createBookStore_storeFileIsInDocumentsDirectory() = runTest {
        val store = createBookStore()
        store.set(BookCollection()) // 書き込みを行いファイルを生成する

        val filePath = Path(storeFilePath)
        assertTrue(
            SystemFileSystem.exists(filePath),
            "ストアファイルが作成されていること。期待パス: $storeFilePath",
        )
        assertTrue(
            storeFilePath.contains("/Documents/recobook/books.json"),
            "ストアファイルのパスが Documents/recobook/books.json を含むこと。実際のパス: $storeFilePath",
        )
    }
}

