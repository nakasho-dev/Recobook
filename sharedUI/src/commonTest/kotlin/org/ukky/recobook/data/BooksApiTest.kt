package org.ukky.recobook.data

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

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

    @Test
    fun fetchByIsbn_validResponse_returnsBook() = runTest {
        val book = createApi(OPENBD_RESPONSE_JSON).fetchByIsbn("9784780802047")

        assertNotNull(book)
        assertEquals("9784780802047", book.id)
        assertEquals("おにぎりレシピ101", book.title)
    }

    @Test
    fun fetchByIsbn_noItems_returnsNull() = runTest {
        val book = createApi(NOT_FOUND_RESPONSE_JSON).fetchByIsbn("0000000000000")

        assertNull(book)
    }

    @Test
    fun fetchByIsbn_sendsOpenBdQueryWithIsbn() = runTest {
        var requestedPath = ""
        var requestedIsbn: String? = null
        val engine = MockEngine { request ->
            requestedPath = request.url.encodedPath
            requestedIsbn = request.url.parameters["isbn"]
            respond(
                content = OPENBD_RESPONSE_JSON,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(testJson) }
        }

        val book = BooksApi(client).fetchByIsbn("9784780802047")

        assertNotNull(book)
        assertEquals("/v1/get", requestedPath)
        assertEquals("9784780802047", requestedIsbn)
    }

    @Test
    fun fetchByIsbn_isbn10Input_populatesIsbn10AndCanonicalIsbn13() = runTest {
        val book = createApi(ISBN10_QUERY_RESPONSE_JSON).fetchByIsbn("4873119030")

        assertNotNull(book)
        assertEquals("9784873119038", book.isbn)
        assertEquals("9784873119038", book.isbn13)
        assertEquals("4873119030", book.isbn10)
    }

    @Test
    fun fetchByIsbn_noIdentifiers_fallbackIsbn() = runTest {
        val book = createApi(NO_IDENTIFIERS_RESPONSE_JSON).fetchByIsbn("fallback-isbn")

        assertNotNull(book)
        assertEquals("fallback-isbn", book.id)
        assertEquals("fallback-isbn", book.isbn)
    }

    @Test
    fun fetchByIsbn_summaryCoverMissing_usesSupportingResource() = runTest {
        val book = createApi(SUPPORTING_RESOURCE_ONLY_RESPONSE_JSON).fetchByIsbn("9784780802047")

        assertNotNull(book)
        assertEquals("https://cover.openbd.jp/9784780802047.jpg", book.thumbnailUrl)
    }

    @Test
    fun fetchByIsbn_noThumbnail_nullUrl() = runTest {
        val book = createApi(NO_THUMBNAIL_RESPONSE_JSON).fetchByIsbn("9784780802047")

        assertNotNull(book)
        assertNull(book.thumbnailUrl)
    }

    @Test
    fun fetchByIsbn_blankTitle_usesIsbnAsFallback() = runTest {
        val book = createApi(BLANK_TITLE_RESPONSE_JSON).fetchByIsbn("9784780802047")

        assertNotNull(book)
        assertEquals("9784780802047", book.title)
    }

    @Test
    fun fetchByIsbn_allFieldsMappedFromOpenBd() = runTest {
        val book = createApi(OPENBD_RESPONSE_JSON).fetchByIsbn("9784780802047")

        assertNotNull(book)
        assertEquals(listOf("山田　玲子", "水野　菜生"), book.authors)
        assertEquals("ポット出版", book.publisher)
        assertEquals("2014-04-08", book.publishedDate)
        assertTrue(book.description?.startsWith("101人いれば、101通りの好みのおにぎりがあります。") == true)
        assertEquals(126, book.pageCount)
        assertEquals(listOf("0077", "07", "WBN", "1FPJ"), book.categories)
    }

    @Test
    fun fetchByIsbn_descriptionPrefersTextType03() = runTest {
        val book = createApi(OPENBD_RESPONSE_JSON).fetchByIsbn("9784780802047")

        assertNotNull(book)
        assertTrue(book.description?.contains("101種類のおにぎりレシピ") == true)
        assertFalse(book.description == "海外でも人気の日本のソウルフード、おにぎり。クッキングアドバイザー・山田玲子が考えた101のレシピを英訳付きでご紹介します。")
    }

    companion object {
        val OPENBD_RESPONSE_JSON = """
            [
              {
                "onix": {
                  "RecordReference": "9784780802047",
                  "ProductIdentifier": {
                    "ProductIDType": "15",
                    "IDValue": "9784780802047"
                  },
                  "DescriptiveDetail": {
                    "TitleDetail": {
                      "TitleElement": {
                        "TitleText": {
                          "content": "おにぎりレシピ101"
                        }
                      }
                    },
                    "Contributor": [
                      {
                        "PersonName": {
                          "content": "山田　玲子"
                        }
                      },
                      {
                        "PersonName": {
                          "content": "水野　菜生"
                        }
                      }
                    ],
                    "Extent": [
                      {
                        "ExtentType": "11",
                        "ExtentValue": "126",
                        "ExtentUnit": "03"
                      }
                    ],
                    "Subject": [
                      {
                        "SubjectCode": "0077"
                      },
                      {
                        "SubjectCode": "07"
                      },
                      {
                        "SubjectCode": "WBN"
                      },
                      {
                        "SubjectCode": "1FPJ"
                      }
                    ]
                  },
                  "CollateralDetail": {
                    "TextContent": [
                      {
                        "TextType": "02",
                        "Text": "海外でも人気の日本のソウルフード、おにぎり。クッキングアドバイザー・山田玲子が考えた101のレシピを英訳付きでご紹介します。"
                      },
                      {
                        "TextType": "03",
                        "Text": "101人いれば、101通りの好みのおにぎりがあります。クッキングアドバイザー・山田玲子が101種類のおにぎりレシピを考えました。"
                      }
                    ],
                    "SupportingResource": [
                      {
                        "ResourceVersion": [
                          {
                            "ResourceLink": "https://cover.openbd.jp/9784780802047.jpg"
                          }
                        ]
                      }
                    ]
                  },
                  "PublishingDetail": {
                    "Imprint": {
                      "ImprintName": "ポット出版"
                    },
                    "PublishingDate": [
                      {
                        "PublishingDateRole": "01",
                        "Date": "20140408"
                      }
                    ]
                  }
                },
                "summary": {
                  "isbn": "9784780802047",
                  "title": "おにぎりレシピ101",
                  "publisher": "ポット出版",
                  "pubdate": "20140408",
                  "cover": "https://cover.openbd.jp/9784780802047.jpg"
                }
              }
            ]
        """.trimIndent()

        val NOT_FOUND_RESPONSE_JSON = """
            [
              null
            ]
        """.trimIndent()

        val ISBN10_QUERY_RESPONSE_JSON = """
            [
              {
                "onix": {
                  "RecordReference": "9784873119038",
                  "ProductIdentifier": {
                    "ProductIDType": "15",
                    "IDValue": "9784873119038"
                  },
                  "DescriptiveDetail": {
                    "TitleDetail": {
                      "TitleElement": {
                        "TitleText": {
                          "content": "Real World HTTP : 歴史とコードに学ぶインターネットとウェブ技術"
                        }
                      }
                    }
                  }
                },
                "summary": {
                  "isbn": "9784873119038",
                  "title": "Real World HTTP : 歴史とコードに学ぶインターネットとウェブ技術"
                }
              }
            ]
        """.trimIndent()

        val NO_IDENTIFIERS_RESPONSE_JSON = """
            [
              {
                "onix": {
                  "DescriptiveDetail": {
                    "TitleDetail": {
                      "TitleElement": {
                        "TitleText": {
                          "content": "識別子なしの本"
                        }
                      }
                    }
                  }
                },
                "summary": {
                  "isbn": ""
                }
              }
            ]
        """.trimIndent()

        val SUPPORTING_RESOURCE_ONLY_RESPONSE_JSON = """
            [
              {
                "onix": {
                  "ProductIdentifier": {
                    "IDValue": "9784780802047"
                  },
                  "DescriptiveDetail": {
                    "TitleDetail": {
                      "TitleElement": {
                        "TitleText": {
                          "content": "表紙だけの本"
                        }
                      }
                    }
                  },
                  "CollateralDetail": {
                    "SupportingResource": [
                      {
                        "ResourceVersion": [
                          {
                            "ResourceLink": "https://cover.openbd.jp/9784780802047.jpg"
                          }
                        ]
                      }
                    ]
                  }
                },
                "summary": {
                  "isbn": "9784780802047",
                  "title": "表紙だけの本",
                  "cover": ""
                }
              }
            ]
        """.trimIndent()

        val NO_THUMBNAIL_RESPONSE_JSON = """
            [
              {
                "onix": {
                  "ProductIdentifier": {
                    "IDValue": "9784780802047"
                  },
                  "DescriptiveDetail": {
                    "TitleDetail": {
                      "TitleElement": {
                        "TitleText": {
                          "content": "サムネイルなしの本"
                        }
                      }
                    }
                  },
                  "CollateralDetail": {}
                },
                "summary": {
                  "isbn": "9784780802047",
                  "title": "サムネイルなしの本",
                  "cover": ""
                }
              }
            ]
        """.trimIndent()

        val BLANK_TITLE_RESPONSE_JSON = """
            [
              {
                "onix": {
                  "ProductIdentifier": {
                    "IDValue": "9784780802047"
                  },
                  "DescriptiveDetail": {
                    "TitleDetail": {
                      "TitleElement": {
                        "TitleText": {
                          "content": ""
                        }
                      }
                    }
                  }
                },
                "summary": {
                  "isbn": "9784780802047",
                  "title": ""
                }
              }
            ]
        """.trimIndent()
    }
}
