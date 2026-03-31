package org.ukky.recobook

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [DragDropState] の状態管理ロジックを検証するユニットテスト。
 *
 * ハプティックフィードバックは UI レイヤー（[App.kt] の `BookList` 内）で
 * `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress)` を
 * `onDragStart` コールバック先頭で呼び出すことで実現しており、
 * プラットフォーム固有の振動 API（Android: HapticFeedbackConstants、
 * iOS: UIImpactFeedbackGenerator）に委譲される。
 * このため、ハプティック発火の検証は実機 / シミュレーターでの手動テストで行う。
 */
class DragDropStateTest {

    /** テスト用に最小構成の [DragDropState] を生成する。 */
    private fun createState(
        onMove: (Int, Int) -> Unit = { _, _ -> },
    ): DragDropState = DragDropState(
        lazyListState = LazyListState(),
        scope = TestScope(),
        onMove = onMove,
    )

    // ── 初期状態 ─────────────────────────────────────────────

    @Test
    fun initialState_draggingItemIndexIsNull() {
        val state = createState()
        assertNull(state.draggingItemIndex)
    }

    @Test
    fun initialState_visualOffsetIsZero() {
        val state = createState()
        assertEquals(0f, state.draggingItemVisualOffset)
    }

    // ── onDragEnd ─────────────────────────────────────────────

    @Test
    fun onDragEnd_draggingItemIndexIsNull() = runTest {
        val state = createState()
        state.onDragEnd()
        assertNull(state.draggingItemIndex)
    }

    @Test
    fun onDragEnd_visualOffsetIsZero() = runTest {
        val state = createState()
        state.onDragEnd()
        assertEquals(0f, state.draggingItemVisualOffset)
    }

    // ── onDragCancel ──────────────────────────────────────────

    @Test
    fun onDragCancel_draggingItemIndexIsNull() = runTest {
        val state = createState()
        state.onDragCancel()
        assertNull(state.draggingItemIndex)
    }

    @Test
    fun onDragCancel_visualOffsetIsZero() = runTest {
        val state = createState()
        state.onDragCancel()
        assertEquals(0f, state.draggingItemVisualOffset)
    }

    // ── onDragStart（可視アイテムなし） ────────────────────────

    /**
     * レイアウト未接続の [LazyListState] は visibleItemsInfo が空リストを返すため、
     * [DragDropState.onDragStart] を呼んでも draggingItemIndex は null のまま。
     */
    @Test
    fun onDragStart_noVisibleItems_draggingItemIndexRemainsNull() = runTest {
        val state = createState()
        state.onDragStart(Offset(0f, 200f))
        assertNull(state.draggingItemIndex)
    }

    @Test
    fun onDragStart_noVisibleItems_visualOffsetRemainsZero() = runTest {
        val state = createState()
        state.onDragStart(Offset(0f, 200f))
        assertEquals(0f, state.draggingItemVisualOffset)
    }

    // ── onDrag（ドラッグ中アイテムなし） ──────────────────────

    /**
     * draggingItemIndex が null の状態で onDrag を呼び出しても
     * クラッシュせず、状態が変化しないことを確認。
     */
    @Test
    fun onDrag_whenNotDragging_noStateChange() = runTest {
        val state = createState()
        state.onDrag(Offset(0f, 50f)) // draggingItemLayoutInfo が null → 早期リターン
        assertNull(state.draggingItemIndex)
        assertEquals(0f, state.draggingItemVisualOffset)
    }

    // ── onMove コールバック ────────────────────────────────────

    /**
     * onMove が呼ばれないケースの確認（ドラッグ開始なし）。
     * onMove が不要に呼ばれていないことを副作用として検証。
     */
    @Test
    fun onMove_notCalledWhenNoDragStarted() = runTest {
        var callCount = 0
        val state = createState(onMove = { _, _ -> callCount++ })
        // ドラッグ開始せずに終了 → onMove は呼ばれない
        state.onDragEnd()
        assertEquals(0, callCount)
    }
}

