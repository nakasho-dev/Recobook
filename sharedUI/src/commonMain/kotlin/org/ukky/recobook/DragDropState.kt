package org.ukky.recobook

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 長押しドラッグによる並べ替えを管理する状態クラス。
 * [androidx.compose.foundation.lazy.LazyColumn] と組み合わせて使用する。
 *
 * @property lazyListState ドラッグ対象リストの [LazyListState]
 */
class DragDropState internal constructor(
    val lazyListState: LazyListState,
    private val scope: CoroutineScope,
    private val onMove: (fromIndex: Int, toIndex: Int) -> Unit,
) {    /** 現在ドラッグ中のアイテムのリスト内インデックス（ドラッグ中でない場合は null） */
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    private var draggingItemDelta by mutableStateOf(0f)
    private var draggingItemInitialOffset = 0

    /**
     * ドラッグ中アイテムに適用する Y 方向の視覚オフセット（px）。
     * アイテムのレイアウト位置からの相対ずれ量。
     */
    val draggingItemVisualOffset: Float
        get() = draggingItemLayoutInfo?.let { info ->
            draggingItemInitialOffset + draggingItemDelta - info.offset
        } ?: 0f

    private val draggingItemLayoutInfo: LazyListItemInfo?
        get() = lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == draggingItemIndex }

    /** ドラッグ開始時に呼び出す。[offset] は LazyColumn 可視領域内のタッチ座標。 */
    internal fun onDragStart(offset: Offset) {
        lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { offset.y.toInt() in it.offset..(it.offset + it.size) }
            ?.let {
                draggingItemIndex = it.index
                draggingItemInitialOffset = it.offset
                draggingItemDelta = 0f
            }
    }

    /** ドラッグ中に呼び出す。[delta] はフレーム間の移動量。 */
    internal fun onDrag(delta: Offset) {
        draggingItemDelta += delta.y

        val draggingItem = draggingItemLayoutInfo ?: return
        val startOffset = draggingItem.offset + draggingItemVisualOffset
        val endOffset = startOffset + draggingItem.size
        val middleOffset = (startOffset + endOffset) / 2f

        val targetItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.index != draggingItemIndex &&
                middleOffset.toInt() in item.offset..(item.offset + item.size)
        }

        if (targetItem != null) {
            onMove(draggingItem.index, targetItem.index)
            draggingItemIndex = targetItem.index
        } else {
            // エッジ付近では自動スクロール
            val overscroll = when {
                draggingItemDelta > 0 ->
                    (endOffset - lazyListState.layoutInfo.viewportEndOffset).coerceAtLeast(0f)
                draggingItemDelta < 0 ->
                    (startOffset - lazyListState.layoutInfo.viewportStartOffset).coerceAtMost(0f)
                else -> 0f
            }
            if (overscroll != 0f) {
                scope.launch { lazyListState.scrollBy(overscroll) }
            }
        }
    }

    /** ドラッグ終了時に呼び出す。 */
    internal fun onDragEnd() {
        draggingItemIndex = null
        draggingItemDelta = 0f
        draggingItemInitialOffset = 0
    }

    /** ドラッグキャンセル時に呼び出す。 */
    internal fun onDragCancel() = onDragEnd()
}

/**
 * [DragDropState] を生成してキャッシュする Composable ファクトリ。
 *
 * @param lazyListState ドラッグ対象の [LazyListState]（省略時は自動生成）
 * @param onMove アイテムが別の位置に移動したときに呼ばれるコールバック
 */
@Composable
fun rememberDragDropState(
    lazyListState: LazyListState = rememberLazyListState(),
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
): DragDropState {
    val scope = rememberCoroutineScope()
    return remember(lazyListState, scope) {
        DragDropState(lazyListState = lazyListState, scope = scope, onMove = onMove)
    }
}

/**
 * ドラッグ中のアイテムに適用する Modifier。
 * 該当アイテムは他のアイテムより前面に表示され、ドラッグ量に応じて Y 軸方向にオフセットされる。
 *
 * @param state [DragDropState]
 * @param index このアイテムのリスト内インデックス
 */
fun Modifier.draggableItem(state: DragDropState, index: Int): Modifier =
    if (state.draggingItemIndex == index) {
        this
            .zIndex(1f)
            .offset { IntOffset(0, state.draggingItemVisualOffset.roundToInt()) }
    } else {
        this
    }

