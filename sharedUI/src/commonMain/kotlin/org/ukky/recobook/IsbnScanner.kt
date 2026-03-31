package org.ukky.recobook

import androidx.compose.runtime.Composable

data class IsbnScanner(
    val isAvailable: Boolean,
    val launch: () -> Unit,
)

@Composable
expect fun rememberIsbnScanner(onScanned: (String) -> Unit): IsbnScanner
