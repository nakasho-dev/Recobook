package org.ukky.recobook

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import org.ukky.recobook.data.Book
import org.ukky.recobook.data.BookAddResult
import org.ukky.recobook.data.BooksApi
import org.ukky.recobook.data.BooksRepository
import org.ukky.recobook.data.isIsbnLengthValid
import org.ukky.recobook.data.normalizeIsbn
import org.ukky.recobook.storage.createBookStore
import org.ukky.recobook.theme.AppTheme
import org.ukky.recobook.theme.LocalThemeIsDark
import recobook.sharedui.generated.resources.*

@Preview
@Composable
fun App(
    onThemeChanged: @Composable (isDark: Boolean) -> Unit = {},
) = AppTheme(onThemeChanged) {
    val repository = rememberBooksRepository()
    val imageLoader = rememberImageLoader()
    val books by repository.books.collectAsState(emptyList())
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isbnInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val scanner = rememberIsbnScanner { scanned ->
        isbnInput = scanned
        coroutineScope.launch { submitIsbn(scanned, repository, snackbarHostState, onLoading = { isLoading = it }) }
    }

    fun onSubmit(raw: String = isbnInput) {
        coroutineScope.launch {
            submitIsbn(raw, repository, snackbarHostState, onLoading = { isLoading = it })
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
                HeaderRow()
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
                        onRemove = { book -> coroutineScope.launch { repository.removeById(book.id) } },
                        onReorder = { from, to -> coroutineScope.launch { repository.reorderBooks(from, to) } },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderRow() {
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
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
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
            }
            TextButton(onClick = onRemove) {
                Text(stringResource(Res.string.remove))
            }
        }
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
private fun rememberBooksRepository(): BooksRepository {
    val json = remember {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
    val client = remember {
        HttpClient {
            install(ContentNegotiation) {
                json(json)
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { client.close() }
    }
    val store = remember { createBookStore() }
    return remember { BooksRepository(store, BooksApi(client)) }
}

@Composable
private fun rememberImageLoader(): ImageLoader {
    val context = LocalPlatformContext.current
    val loader = remember(context) {
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
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
