package org.ukky.recobook

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
actual fun rememberIsbnScanner(onScanned: (String) -> Unit): IsbnScanner {
    val currentOnScanned by rememberUpdatedState(onScanned)
    val launcher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (!contents.isNullOrBlank()) {
            currentOnScanned(contents)
        }
    }
    val launch = {
        val options = ScanOptions().apply {
            setPrompt("Scan ISBN barcode")
            setBeepEnabled(true)
            setOrientationLocked(false)
        }
        launcher.launch(options)
    }
    return IsbnScanner(isAvailable = true, launch = launch)
}
