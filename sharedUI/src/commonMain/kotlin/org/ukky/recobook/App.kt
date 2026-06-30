package org.ukky.recobook

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.network.ktor3.KtorNetworkFetcherFactory
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import org.ukky.recobook.data.*
import org.ukky.recobook.network.createRecobookHttpClient
import org.ukky.recobook.storage.createBookStore
import org.ukky.recobook.storage.createBorrowerHistoryStore
import org.ukky.recobook.theme.AppTheme
import org.ukky.recobook.theme.LocalThemeIsDark
import recobook.sharedui.generated.resources.*

private sealed interface AppScreen {
    data object Shelf : AppScreen
    data object Management : AppScreen
    data class Details(val bookId: String) : AppScreen
}

@Preview
@Composable
fun App(
    onThemeChanged: @Composable (isDark: Boolean) -> Unit = {},
) = AppTheme(onThemeChanged) {
    val httpClient = rememberAppHttpClient()
    val repository = rememberBooksRepository(httpClient)
    val imageLoader = rememberImageLoader(httpClient)
    val books by repository.books.collectAsState(emptyList())
    val borrowerHistory by repository.borrowerHistory.collectAsState(emptyList())
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isbnInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf<AppScreen>(AppScreen.Shelf) }

    val scanner = rememberIsbnScanner { scanned ->
        isbnInput = scanned
        coroutineScope.launch { submitIsbn(scanned, repository, snackbarHostState, onLoading = { isLoading = it }) }
    }

    val fileTransferManager = rememberTextFileTransferManager(
        onFileImported = { importedFile ->
            coroutineScope.launch {
                try {
                    val summary = repository.importBackupJsonl(importedFile.content.lineSequence())
                    val sourceLabel = if (summary.format == BackupImportFormat.BackupJsonl) {
                        "backup"
                    } else {
                        "books JSONL"
                    }
                    snackbarHostState.showSnackbar(
                        "Imported ${summary.importedBooks} books from $sourceLabel.",
                    )
                } catch (error: Exception) {
                    snackbarHostState.showSnackbar(error.message ?: "Import failed.")
                }
            }
        },
        onError = { message ->
            coroutineScope.launch { snackbarHostState.showSnackbar(message) }
        },
    )

    val selectedBook = when (val currentScreen = screen) {
        is AppScreen.Details -> books.firstOrNull { it.id == currentScreen.bookId }
        else -> null
    }

    LaunchedEffect(books, screen) {
        if (screen is AppScreen.Details && selectedBook == null) {
            screen = AppScreen.Shelf
        }
    }

    fun onSubmit(raw: String = isbnInput) {
        coroutineScope.launch {
            submitIsbn(raw, repository, snackbarHostState, onLoading = { isLoading = it })
        }
    }

    fun launchExport(buildFile: suspend () -> LineExportFile) {
        coroutineScope.launch {
            try {
                fileTransferManager.exportFile(buildFile())
            } catch (error: Exception) {
                snackbarHostState.showSnackbar(error.message ?: "Export failed.")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val colors = MaterialTheme.colorScheme
        val backgroundBrush = Brush.verticalGradient(
            colors = listOf(
                colors.surface,
                colors.primary.copy(alpha = 0.08f),
                colors.tertiary.copy(alpha = 0.1f),
            ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(padding)
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (screen) {
                    AppScreen.Shelf -> {
                        HeaderRow(onOpenManagement = { screen = AppScreen.Management })
                        IsbnInputCard(
                            isbnInput = isbnInput,
                            isLoading = isLoading,
                            scannerAvailable = scanner.isAvailable,
                            onIsbnChanged = { isbnInput = it },
                            onScan = { scanner.launch() },
                            onSubmit = { onSubmit() },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        if (books.isEmpty()) {
                            Box(modifier = Modifier.weight(1f)) {
                                EmptyState()
                            }
                        } else {
                            BookList(
                                books = books,
                                imageLoader = imageLoader,
                                onOpenDetails = { book -> screen = AppScreen.Details(book.id) },
                                onRemove = { book ->
                                    coroutineScope.launch {
                                        repository.removeById(book.id)
                                        snackbarHostState.showSnackbar("Removed from your shelf.")
                                    }
                                },
                                onReorder = { from, to -> coroutineScope.launch { repository.reorderBooks(from, to) } },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    is AppScreen.Details -> {
                        if (selectedBook != null) {
                            BookDetailScreen(
                                book = selectedBook,
                                imageLoader = imageLoader,
                                borrowerHistory = borrowerHistory,
                                onBack = { screen = AppScreen.Shelf },
                                onUpdateLoan = { borrowerName ->
                                    coroutineScope.launch {
                                        repository.updateLoan(selectedBook.id, borrowerName)
                                        val message = if (borrowerName.isNullOrBlank()) {
                                            "Marked as available."
                                        } else {
                                            "Saved lending details."
                                        }
                                        snackbarHostState.showSnackbar(message)
                                    }
                                },
                                onRemove = {
                                    coroutineScope.launch {
                                        repository.removeById(selectedBook.id)
                                        screen = AppScreen.Shelf
                                        snackbarHostState.showSnackbar("Removed from your shelf.")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    AppScreen.Management -> {
                        ManagementScreen(
                            books = books,
                            borrowerHistoryCount = borrowerHistory.size,
                            onBack = { screen = AppScreen.Shelf },
                            onExportBackup = { launchExport { repository.exportBackupJsonl() } },
                            onImportBackup = { fileTransferManager.importFile(backupImportRequest) },
                            onExportBooksJsonl = { launchExport { repository.exportBooksJsonl() } },
                            onExportMarkdown = { launchExport { repository.exportShareMarkdown() } },
                            onExportCsv = { launchExport { repository.exportShareCsv() } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(
    onOpenManagement: () -> Unit,
) {
    val titleFont = FontFamily(Font(Res.font.IndieFlower_Regular))
    var isDark by LocalThemeIsDark.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.app_name),
                fontFamily = titleFont,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.title_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onOpenManagement) {
            Text(stringResource(Res.string.admin_manage))
        }
        TextButton(onClick = { isDark = !isDark }) {
            Text(stringResource(Res.string.theme))
        }
    }
}

@Composable
private fun IsbnInputCard(
    isbnInput: String,
    isLoading: Boolean,
    scannerAvailable: Boolean,
    onIsbnChanged: (String) -> Unit,
    onScan: () -> Unit,
    onSubmit: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = isbnInput,
                onValueChange = onIsbnChanged,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                label = { Text(stringResource(Res.string.isbn_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onSubmit,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.searching))
                    } else {
                        Text(stringResource(Res.string.search))
                    }
                }
                if (scannerAvailable) {
                    ElevatedButton(
                        onClick = onScan,
                        enabled = !isLoading,
                        modifier = Modifier.widthIn(min = 120.dp),
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Text(stringResource(Res.string.scan))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(Res.string.empty_state),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun BookList(
    books: List<Book>,
    imageLoader: ImageLoader,
    onOpenDetails: (Book) -> Unit,
    onRemove: (Book) -> Unit,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // ドラッグ&ドロップ中の視覚的フィードバック用ローカルリスト
    val dragItems = remember { mutableStateListOf<Book>() }
    var isDragging by remember { mutableStateOf(false) }
    var dragStartIndex by remember { mutableStateOf<Int?>(null) }
    var dragOriginalItems by remember { mutableStateOf<List<Book>>(emptyList()) }

    // ハプティックフィードバック（recomposition をまたいで最新値を保持）
    val hapticFeedback = rememberUpdatedState(LocalHapticFeedback.current)

    // ドラッグ中でない時のみストアと同期する
    LaunchedEffect(books) {
        if (!isDragging) {
            dragItems.clear()
            dragItems.addAll(books)
        }
    }

    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val dragDropState = remember(lazyListState, scope) {
        DragDropState(
            lazyListState = lazyListState,
            scope = scope,
            onMove = { from, to ->
                dragItems.apply { add(to, removeAt(from)) }
            },
        )
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(dragDropState) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        // 長押しが確定した瞬間にハプティックを発火
                        hapticFeedback.value.performHapticFeedback(HapticFeedbackType.LongPress)
                        val info = lazyListState.layoutInfo.visibleItemsInfo
                            .firstOrNull { offset.y.toInt() in it.offset..(it.offset + it.size) }
                        dragStartIndex = info?.index
                        dragOriginalItems = dragItems.toList()
                        dragDropState.onDragStart(offset)
                        isDragging = true
                    },
                    onDrag = { change, delta ->
                        change.consume()
                        dragDropState.onDrag(delta)
                    },
                    onDragEnd = {
                        val startIdx = dragStartIndex
                        val endIdx = dragDropState.draggingItemIndex
                        if (startIdx != null && endIdx != null && startIdx != endIdx) {
                            scope.launch { onReorder(startIdx, endIdx) }
                        }
                        dragDropState.onDragEnd()
                        isDragging = false
                        dragStartIndex = null
                    },
                    onDragCancel = {
                        // キャンセル時はローカルリストをドラッグ開始前の状態に戻す
                        dragItems.clear()
                        dragItems.addAll(dragOriginalItems)
                        dragDropState.onDragCancel()
                        isDragging = false
                        dragStartIndex = null
                    },
                )
            },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(dragItems, key = { _, book -> book.id }) { index, book ->
            BookCard(
                book = book,
                imageLoader = imageLoader,
                onOpenDetails = { onOpenDetails(book) },
                onRemove = { onRemove(book) },
                modifier = Modifier.draggableItem(dragDropState, index),
            )
        }
    }
}

@Composable
private fun BookCard(
    book: Book,
    imageLoader: ImageLoader,
    onOpenDetails: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetails),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DragHandleIcon()
            if (book.thumbnailUrl != null) {
                AsyncImage(
                    model = book.thumbnailUrl,
                    contentDescription = null,
                    imageLoader = imageLoader,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No Cover", style = MaterialTheme.typography.labelMedium)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book.authors.isNotEmpty()) {
                    Text(
                        text = book.authors.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val details = listOfNotNull(book.publisher, book.publishedDate).joinToString(" • ")
                if (details.isNotBlank()) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "ISBN ${book.isbn}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                if (book.loanInfo != null) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = "${stringResource(Res.string.lent_to_label)} ${book.loanInfo.borrowerName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            TextButton(onClick = onRemove) {
                Text(stringResource(Res.string.remove))
            }
        }
    }
}

@Composable
private fun BookDetailScreen(
    book: Book,
    imageLoader: ImageLoader,
    borrowerHistory: List<String>,
    onBack: () -> Unit,
    onUpdateLoan: (String?) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var borrowerName by remember(book.id, book.loanInfo) {
        mutableStateOf(book.loanInfo?.borrowerName.orEmpty())
    }
    val filteredBorrowerHistory = remember(borrowerHistory, borrowerName) {
        val query = borrowerName.trim()
        borrowerHistory.filter { candidate ->
            query.isBlank() || candidate.contains(query, ignoreCase = true)
        }
    }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(Res.string.back_to_list))
            }
            Text(
                text = stringResource(Res.string.book_details_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BookDetailHeader(book = book, imageLoader = imageLoader)
                BookMetadata(book = book)
            }
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.lending_section_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (book.loanInfo == null) {
                        stringResource(Res.string.lending_status_available)
                    } else {
                        "${stringResource(Res.string.lent_to_label)} ${book.loanInfo.borrowerName}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = borrowerName,
                    onValueChange = { borrowerName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.borrower_name_label)) },
                    placeholder = { Text(stringResource(Res.string.borrower_name_placeholder)) },
                    singleLine = true,
                )
                if (filteredBorrowerHistory.isNotEmpty()) {
                    Text(
                        text = stringResource(Res.string.borrower_history_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filteredBorrowerHistory, key = { it }) { name ->
                            AssistChip(
                                onClick = { borrowerName = name },
                                label = { Text(name) },
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { onUpdateLoan(borrowerName.trim()) },
                        enabled = borrowerName.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            if (book.loanInfo == null) {
                                stringResource(Res.string.lend_book)
                            } else {
                                stringResource(Res.string.save_borrower)
                            },
                        )
                    }
                    if (book.loanInfo != null) {
                        OutlinedButton(
                            onClick = { onUpdateLoan(null) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(Res.string.mark_returned))
                        }
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onRemove,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.remove))
        }
    }
}

@Composable
private fun BookDetailHeader(
    book: Book,
    imageLoader: ImageLoader,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (book.thumbnailUrl != null) {
            AsyncImage(
                model = book.thumbnailUrl,
                contentDescription = null,
                imageLoader = imageLoader,
                modifier = Modifier
                    .size(width = 112.dp, height = 160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(width = 112.dp, height = 160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text("No Cover", style = MaterialTheme.typography.labelMedium)
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.headlineSmall,
            )
            if (book.authors.isNotEmpty()) {
                Text(
                    text = book.authors.joinToString(", "),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (book.loanInfo == null) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = stringResource(Res.string.lending_status_available),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = stringResource(Res.string.lending_status_loaned),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BookMetadata(book: Book) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailLine(label = stringResource(Res.string.isbn_label), value = book.isbn)
        book.publisher?.takeIf { it.isNotBlank() }?.let {
            DetailLine(label = stringResource(Res.string.publisher_label), value = it)
        }
        book.publishedDate?.takeIf { it.isNotBlank() }?.let {
            DetailLine(label = stringResource(Res.string.published_date_label), value = it)
        }
        book.pageCount?.let {
            DetailLine(label = stringResource(Res.string.page_count_label), value = it.toString())
        }
        if (book.categories.isNotEmpty()) {
            DetailLine(
                label = stringResource(Res.string.categories_label),
                value = book.categories.joinToString(", "),
            )
        }
        book.description?.takeIf { it.isNotBlank() }?.let {
            DetailLine(label = stringResource(Res.string.description_label), value = it)
        }
    }
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** 2 × 3 ドットのドラッグハンドルアイコン */
@Composable
private fun DragHandleIcon(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.size(width = 16.dp, height = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(2) {
                    Box(
                        Modifier
                            .size(4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                shape = CircleShape,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberAppHttpClient(): io.ktor.client.HttpClient {
    val json = remember {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
    val client = remember { createRecobookHttpClient(json) }
    DisposableEffect(Unit) {
        onDispose { client.close() }
    }
    return client
}

@Composable
private fun rememberBooksRepository(client: io.ktor.client.HttpClient): BooksRepository {
    val store = remember { createBookStore() }
    val borrowerHistoryStore = remember { createBorrowerHistoryStore() }
    return remember(client, store, borrowerHistoryStore) {
        BooksRepository(store, borrowerHistoryStore, BooksApi(client))
    }
}

@Composable
private fun rememberImageLoader(client: io.ktor.client.HttpClient): ImageLoader {
    val context = LocalPlatformContext.current
    val loader = remember(context, client) {
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory(client)) }
            .build()
    }
    DisposableEffect(loader) {
        onDispose { loader.shutdown() }
    }
    return loader
}

private suspend fun submitIsbn(
    raw: String,
    repository: BooksRepository,
    snackbarHostState: SnackbarHostState,
    onLoading: (Boolean) -> Unit,
) {
    val normalized = normalizeIsbn(raw)
    if (normalized.isBlank()) {
        snackbarHostState.showSnackbar("Enter an ISBN.")
        return
    }
    if (!isIsbnLengthValid(normalized)) {
        snackbarHostState.showSnackbar("ISBN must be 10 or 13 characters.")
        return
    }
    onLoading(true)
    try {
        when (val result = repository.addByIsbn(normalized)) {
            is BookAddResult.Success -> {
                val message = if (result.updated) {
                    "Updated existing entry."
                } else {
                    "Added to your shelf."
                }
                snackbarHostState.showSnackbar(message)
            }
            is BookAddResult.NotFound -> snackbarHostState.showSnackbar("No book found for that ISBN.")
            is BookAddResult.Error -> snackbarHostState.showSnackbar(result.message)
        }
    } finally {
        onLoading(false)
    }
}

private val backupImportRequest = TextImportRequest(
    acceptedExtensions = listOf("jsonl", "json", "txt"),
    acceptedMimeTypes = listOf(
        "application/x-ndjson",
        "application/json",
        "text/plain",
    ),
)
