package org.ukky.recobook

import androidx.compose.runtime.Composable
import java.io.File
import java.nio.charset.StandardCharsets
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberTextFileTransferManager(
    onFileImported: (ImportedTextFile) -> Unit,
    onError: (String) -> Unit,
): TextFileTransferManager {
    return TextFileTransferManager(
        exportFile = { exportFile ->
            runCatching {
                chooseSaveTarget(
                    fileName = exportFile.suggestedFileName,
                    extensions = exportFile.suggestedFileName.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.let(::listOf).orEmpty(),
                )?.writeLines(exportFile.lines)
            }.onFailure {
                onError(it.message ?: "Failed to export file.")
            }
        },
        importFile = { request ->
            runCatching {
                chooseOpenTarget(request.acceptedExtensions)?.let { file ->
                    ImportedTextFile(
                        fileName = file.name,
                        content = file.readText(StandardCharsets.UTF_8),
                    )
                }
            }.onSuccess { imported ->
                if (imported != null) {
                    onFileImported(imported)
                }
            }.onFailure {
                onError(it.message ?: "Failed to import file.")
            }
        },
    )
}

private fun chooseSaveTarget(
    fileName: String,
    extensions: List<String>,
): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Export file"
        selectedFile = File(fileName)
        if (extensions.isNotEmpty()) {
            fileFilter = FileNameExtensionFilter(
                extensions.joinToString(separator = ", ", postfix = " files") { it.uppercase() },
                *extensions.toTypedArray(),
            )
        }
    }
    return if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile
    } else {
        null
    }
}

private fun chooseOpenTarget(extensions: List<String>): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Import file"
        if (extensions.isNotEmpty()) {
            fileFilter = FileNameExtensionFilter(
                extensions.joinToString(separator = ", ", postfix = " files") { it.uppercase() },
                *extensions.toTypedArray(),
            )
        }
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile
    } else {
        null
    }
}

private fun File.writeLines(lines: Sequence<String>) {
    parentFile?.mkdirs()
    bufferedWriter(StandardCharsets.UTF_8).use { writer ->
        lines.forEachIndexed { index, line ->
            if (index > 0) writer.newLine()
            writer.write(line)
        }
    }
}
