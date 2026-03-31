package org.ukky.recobook.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsbnUtilsTest {

    // ─── normalizeIsbn ─────────────────────────────────────

    /** 数字のみの入力はそのまま返される */
    @Test
    fun normalizeIsbn_digitsOnly_unchanged() {
        assertEquals("9784873119038", normalizeIsbn("9784873119038"))
    }

    /** ハイフン区切りは除去される */
    @Test
    fun normalizeIsbn_withHyphens_removesHyphens() {
        assertEquals("9784873119038", normalizeIsbn("978-4-87311-903-8"))
    }

    /** スペース区切りは除去される */
    @Test
    fun normalizeIsbn_withSpaces_removesSpaces() {
        assertEquals("9784873119038", normalizeIsbn("978 4873119038"))
    }

    /** 小文字の 'x' は大文字 'X' に変換される（ISBN-10 チェックディジット） */
    @Test
    fun normalizeIsbn_lowercaseX_convertedToUppercase() {
        assertEquals("085950192X", normalizeIsbn("0-85950-192-x"))
    }

    /** 数字と X 以外の文字（アルファベット・記号など）はすべて除去される */
    @Test
    fun normalizeIsbn_nonDigitNonX_removed() {
        assertEquals("123456789X", normalizeIsbn("1-234-56789-x!@#"))
    }

    /** 空文字列は空文字列を返す */
    @Test
    fun normalizeIsbn_emptyString_returnsEmpty() {
        assertEquals("", normalizeIsbn(""))
    }

    /** 数字以外しかない文字列は空文字列を返す */
    @Test
    fun normalizeIsbn_noDigitsOrX_returnsEmpty() {
        assertEquals("", normalizeIsbn("---   "))
    }

    // ─── isIsbnLengthValid ─────────────────────────────────

    /** 10 桁は有効 */
    @Test
    fun isIsbnLengthValid_length10_returnsTrue() {
        assertTrue(isIsbnLengthValid("1234567890"))
    }

    /** 13 桁は有効 */
    @Test
    fun isIsbnLengthValid_length13_returnsTrue() {
        assertTrue(isIsbnLengthValid("9784873119038"))
    }

    /** 9 桁は無効 */
    @Test
    fun isIsbnLengthValid_length9_returnsFalse() {
        assertFalse(isIsbnLengthValid("123456789"))
    }

    /** 11 桁は無効 */
    @Test
    fun isIsbnLengthValid_length11_returnsFalse() {
        assertFalse(isIsbnLengthValid("12345678901"))
    }

    /** 12 桁は無効 */
    @Test
    fun isIsbnLengthValid_length12_returnsFalse() {
        assertFalse(isIsbnLengthValid("123456789012"))
    }

    /** 14 桁は無効 */
    @Test
    fun isIsbnLengthValid_length14_returnsFalse() {
        assertFalse(isIsbnLengthValid("12345678901234"))
    }

    /** 空文字列は無効 */
    @Test
    fun isIsbnLengthValid_emptyString_returnsFalse() {
        assertFalse(isIsbnLengthValid(""))
    }

    // ─── normalizeIsbn + isIsbnLengthValid の組み合わせ ────

    /** ハイフン付き ISBN-13 を正規化すると有効な長さになる */
    @Test
    fun combinedUsage_hyphenatedIsbn13_validAfterNormalize() {
        val normalized = normalizeIsbn("978-4-87311-903-8")
        assertTrue(isIsbnLengthValid(normalized))
    }

    /** ハイフン付き ISBN-10 を正規化すると有効な長さになる */
    @Test
    fun combinedUsage_hyphenatedIsbn10_validAfterNormalize() {
        val normalized = normalizeIsbn("4-87311-903-0")
        assertTrue(isIsbnLengthValid(normalized))
    }
}

