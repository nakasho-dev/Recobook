package org.ukky.recobook

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import java.nio.charset.StandardCharsets

@Composable
actual fun rememberTextFileTransferManager(
    onFileImported: (ImportedTextFile) -> Unit,
    onError: (String) -> Unit,
): TextFileTransferManager {
    val context = LocalContext.current
    val currentOnFileImported by rememberUpdatedState(onFileImported)
    val currentOnError by rememberUpdatedState(onError)
    var pendingExport by remember { mutableStateOf<LineExportFile?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(CreateDocument("*/*")) { uri ->
        val exportFile = pendingExport
        pendingExport = null
        if (uri != null && exportFile != null) {
            runCatching { context.writeExport(uri, exportFile) }
                .onFailure { currentOnError(it.message ?: "Failed to export file.") }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.readImportedFile(uri) }
                .onSuccess(currentOnFileImported)
                .onFailure { currentOnError(it.message ?: "Failed to import file.") }
        }
    }

    return TextFileTransferManager(
        exportFile = { exportFile ->
            pendingExport = exportFile
            exportLauncher.launch(exportFile.suggestedFileName)
        },
        importFile = { request ->
            val mimeTypes = request.acceptedMimeTypes.ifEmpty { listOf("*/*") }
            importLauncher.launch(mimeTypes.toTypedArray())
        },
    )
}

private fun Context.writeExport(
    uri: Uri,
    exportFile: LineExportFile,
) {
    contentResolver.openOutputStream(uri)?.bufferedWriter(StandardCharsets.UTF_8)?.use { writer ->
        exportFile.lines.forEachIndexed { index, line ->
            if (index > 0) writer.newLine()
            writer.write(line)
        }
    } ?: error("Unable to open destination file.")
}

private fun Context.readImportedFile(uri: Uri): ImportedTextFile {
    val fileName = uri.lastPathSegment?.substringAfterLast('/')
    val content = contentResolver.openInputStream(uri)?.bufferedReader(StandardCharsets.UTF_8)?.use { reader ->
        reader.readText()
    } ?: error("Unable to open selected file.")
    return ImportedTextFile(
        fileName = fileName,
        content = content,
    )
}
