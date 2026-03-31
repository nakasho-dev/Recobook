package org.ukky.recobook

import androidx.compose.runtime.Composable

@Composable
actual fun rememberIsbnScanner(onScanned: (String) -> Unit): IsbnScanner {
    return IsbnScanner(isAvailable = false, launch = {})
}
