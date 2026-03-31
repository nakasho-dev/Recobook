package org.ukky.recobook.data

fun normalizeIsbn(raw: String): String {
    return raw.uppercase().filter { it.isDigit() || it == 'X' }
}

fun isIsbnLengthValid(isbn: String): Boolean {
    return isbn.length == 10 || isbn.length == 13
}
