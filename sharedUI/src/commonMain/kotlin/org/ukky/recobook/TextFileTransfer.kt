package org.ukky.recobook

import androidx.compose.runtime.Composable

data class LineExportFile(
    val suggestedFileName: String,
    val mimeType: String,
    val lines: Sequence<String>,
)

data class ImportedTextFile(
    val fileName: String?,
    val content: String,
)

data class TextImportRequest(
    val acceptedExtensions: List<String> = emptyList(),
    val acceptedMimeTypes: List<String> = listOf("text/plain"),
)

data class TextFileTransferManager(
    val exportFile: (LineExportFile) -> Unit,
    val importFile: (TextImportRequest) -> Unit,
)

@Composable
expect fun rememberTextFileTransferManager(
    onFileImported: (ImportedTextFile) -> Unit,
    onError: (String) -> Unit,
): TextFileTransferManager
