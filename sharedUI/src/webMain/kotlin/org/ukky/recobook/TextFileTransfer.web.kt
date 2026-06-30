@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.ukky.recobook

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.document
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.files.FileReader
import kotlin.js.JsAny
import kotlin.js.toJsArray
import kotlin.js.toJsString

@Composable
actual fun rememberTextFileTransferManager(
    onFileImported: (ImportedTextFile) -> Unit,
    onError: (String) -> Unit,
): TextFileTransferManager {
    val currentOnFileImported by rememberUpdatedState(onFileImported)
    val currentOnError by rememberUpdatedState(onError)

    return TextFileTransferManager(
        exportFile = { exportFile ->
            runCatching {
                val content = exportFile.lines.joinToString(separator = "\n")
                val parts = listOf<JsAny?>(content.toJsString()).toJsArray()
                val blob = Blob(
                    parts,
                    BlobPropertyBag(type = exportFile.mimeType),
                )
                val url = URL.createObjectURL(blob)
                val anchor = document.createElement("a") as HTMLAnchorElement
                anchor.href = url
                anchor.download = exportFile.suggestedFileName
                document.body?.appendChild(anchor)
                anchor.click()
                document.body?.removeChild(anchor)
                URL.revokeObjectURL(url)
            }.onFailure {
                currentOnError(it.message ?: "Failed to export file.")
            }
        },
        importFile = { request ->
            val input = document.createElement("input") as HTMLInputElement
            input.type = "file"
            input.accept = buildAcceptValue(request)
            input.onchange = {
                val file = input.files?.item(0)
                if (file != null) {
                    val reader = FileReader()
                    reader.onload = { _ ->
                        val result = reader.result?.toString()
                        if (!result.isNullOrEmpty()) {
                            currentOnFileImported(
                                ImportedTextFile(
                                    fileName = file.name,
                                    content = result,
                                ),
                            )
                        } else {
                            currentOnError("Failed to import file.")
                        }
                    }
                    reader.onerror = { _ ->
                        currentOnError("Failed to import file.")
                    }
                    reader.readAsText(file)
                }
            }
            input.click()
        },
    )
}

private fun buildAcceptValue(request: TextImportRequest): String {
    val extensions = request.acceptedExtensions.map { extension ->
        if (extension.startsWith(".")) extension else ".$extension"
    }
    return (extensions + request.acceptedMimeTypes).joinToString(",")
}
