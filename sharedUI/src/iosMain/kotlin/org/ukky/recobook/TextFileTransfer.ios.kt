package org.ukky.recobook

import androidx.compose.runtime.*
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.*
import platform.UIKit.*
import platform.UniformTypeIdentifiers.UTTypeText
import platform.darwin.NSObject

@Composable
actual fun rememberTextFileTransferManager(
    onFileImported: (ImportedTextFile) -> Unit,
    onError: (String) -> Unit,
): TextFileTransferManager {
    val currentOnFileImported by rememberUpdatedState(onFileImported)
    val currentOnError by rememberUpdatedState(onError)
    val delegate = remember { DocumentPickerDelegate() }

    SideEffect {
        delegate.onImported = { fileName, content ->
            currentOnFileImported(
                ImportedTextFile(
                    fileName = fileName,
                    content = content,
                ),
            )
        }
        delegate.onError = currentOnError
    }

    return TextFileTransferManager(
        exportFile = { exportFile ->
            runCatching { presentExportSheet(exportFile) }
                .onFailure { currentOnError(it.message ?: "Failed to export file.") }
        },
        importFile = {
            runCatching { presentImportPicker(delegate) }
                .onFailure { currentOnError(it.message ?: "Failed to import file.") }
        },
    )
}

private class DocumentPickerDelegate : NSObject(), UIDocumentPickerDelegateProtocol {
    var onImported: ((String?, String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    @OptIn(ExperimentalForeignApi::class)
    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        if (url == null) {
            onError?.invoke("No file was selected.")
            return
        }
        val text = NSString.stringWithContentsOfURL(
            url = url,
            encoding = NSUTF8StringEncoding,
            error = null,
        )
        if (text == null) {
            onError?.invoke("Failed to read the selected file.")
            return
        }
        onImported?.invoke(url.lastPathComponent, text)
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentAtURL: NSURL,
    ) {
        val text = NSString.stringWithContentsOfURL(
            url = didPickDocumentAtURL,
            encoding = NSUTF8StringEncoding,
            error = null,
        )
        if (text == null) {
            onError?.invoke("Failed to read the selected file.")
            return
        }
        onImported?.invoke(didPickDocumentAtURL.lastPathComponent, text)
    }
}

private fun presentImportPicker(delegate: DocumentPickerDelegate) {
    val controller = topViewController() ?: error("Unable to present file picker.")
    val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeText))
    picker.delegate = delegate
    picker.allowsMultipleSelection = false
    controller.presentViewController(picker, animated = true, completion = null)
}

@OptIn(ExperimentalForeignApi::class)
private fun presentExportSheet(exportFile: LineExportFile) {
    val controller = topViewController() ?: error("Unable to present export sheet.")
    val fileUrl = NSURL.fileURLWithPath(NSTemporaryDirectory())
        .URLByAppendingPathComponent(exportFile.suggestedFileName)
        ?: error("Unable to create export file URL.")
    val content = exportFile.lines.joinToString(separator = "\n")
    if (!content.encodeToByteArray().toNSData().writeToURL(fileUrl, atomically = true)) {
        error("Failed to write export file.")
    }
    val shareSheet = UIActivityViewController(
        activityItems = listOf<Any>(fileUrl),
        applicationActivities = null,
    )
    shareSheet.popoverPresentationController?.sourceView = controller.view
    controller.presentViewController(shareSheet, animated = true, completion = null)
}

private fun topViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return null
    while (controller.presentedViewController != null) {
        controller = controller.presentedViewController!!
    }
    return controller
}

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
}
