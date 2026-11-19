package app.uroboe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoLogicTest {
    private val t0 = 1_700_000_000_000L
    private val min = MemoLogic.MINUTE_MS
    private val hour = MemoLogic.HOUR_MS
    private val day = MemoLogic.DAY_MS

    private fun memo(
        id: Long = 1L,
        body: String = "テストメモ",
        createdAt: Long = t0,
        lowPriority: Boolean = false,
        lowPriorityAt: Long? = null,
        reviewStage: Int = 0,
        lastReviewedAt: Long = createdAt,
        enteredActiveAt: Long = createdAt,
        reunions: Int = 0,
    ) = Memo(
        id = id,
        body = body,
        createdAt = createdAt,
        updatedAt = createdAt,
        lowPriority = lowPriority,
        lowPriorityAt = lowPriorityAt,
        reviewStage = reviewStage,
        lastReviewedAt = lastReviewedAt,
        enteredActiveAt = enteredActiveAt,
        reunions = reunions,
    )

    @Test
    fun freshMemoStaysAtTopThenBecomesDueAtOneHour() {
        val fresh = memo(id = 1L, createdAt = t0 + 50 * min)
        val waiting = memo(id = 2L, createdAt = t0, reviewStage = 1, lastReviewedAt = t0 + 10 * min)
        val due = memo(id = 3L, createdAt = t0 - 2 * hour, lastReviewedAt = t0 - 2 * hour)
        val now = t0 + 55 * min // fresh is 5m old (< 15m), due is ~3h old (>= 1h), waiting is 45m/24h

        val sorted = MemoLogic.sortActiveByForgettingCurve(listOf(waiting, due, fresh), now)
        assertEquals(listOf(1L, 3L, 2L), sorted.map { it.id })
    }

    @Test
    fun dueMemosSortWithRatioCapSoLongNeglectedMemosSurface() {
        // stage 0 (1h): 1d elapsed -> ratio 24.0 capped to 2.0, lastReviewedAt = t0 - 1d
        val shortNeglected = memo(id = 1L, createdAt = t0 - day, reviewStage = 0)
        // stage 5 (30d): 60d elapsed -> ratio 2.0 capped to 2.0, lastReviewedAt = t0 - 60d
        val longNeglected = memo(id = 2L, createdAt = t0 - 60 * day, reviewStage = 5)
        // stage 1 (1d): 36h elapsed -> ratio 1.5 (< 2.0)
        val moderateDue = memo(id = 3L, createdAt = t0 - 36 * hour, reviewStage = 1)

        val sorted = MemoLogic.sortActiveByForgettingCurve(
            listOf(moderateDue, shortNeglected, longNeglected),
            t0,
        )
        // 同じ cap 2.0 同士では長く放置された stage 5 が最上位、次が stage 0、次が ratio 1.5
        assertEquals(listOf(2L, 1L, 3L), sorted.map { it.id })
    }

    @Test
    fun waitingMemosSortClosestToNextMilestoneFirst() {
        val closer = memo(id = 1L, createdAt = t0 - 20 * hour, reviewStage = 1)
        val newer = memo(id = 2L, createdAt = t0 - 2 * hour, reviewStage = 1)

        val sorted = MemoLogic.sortActiveByForgettingCurve(listOf(newer, closer), t0)
        assertEquals(listOf(1L, 2L), sorted.map { it.id })
    }

    @Test
    fun highlightedDueIdsCapsAtMaxDueHighlights() {
        val memos = (1L..5L).map { idx ->
            memo(id = idx, createdAt = t0 - (idx + 2) * hour, reviewStage = 0)
        }
        val sorted = MemoLogic.sortActiveByForgettingCurve(memos, t0)
        val highlighted = MemoLogic.highlightedDueIds(sorted, t0)
        assertEquals(MemoLogic.MAX_DUE_HIGHLIGHTS, highlighted.size)
        assertEquals(sorted.take(3).map { it.id }.toSet(), highlighted)
    }

    @Test
    fun lowPriorityMemosExcludedFromActiveAndSortedInSomeday() {
        val active = memo(id = 1L, createdAt = t0)
        val lowOld = memo(id = 2L, createdAt = t0, lowPriority = true, lowPriorityAt = t0 + hour)
        val lowNew = memo(id = 3L, createdAt = t0, lowPriority = true, lowPriorityAt = t0 + 2 * hour)

        assertEquals(listOf(1L), MemoLogic.sortActiveByForgettingCurve(listOf(active, lowOld, lowNew), t0 + 3 * hour).map { it.id })
        assertEquals(listOf(3L, 2L), MemoLogic.sortSomeday(listOf(active, lowOld, lowNew)).map { it.id })
    }

    @Test
    fun deferToNextStageAdvancesOnlyAtMilestoneAndClearsFreshWithoutReentry() {
        var m = memo(createdAt = t0)
        assertTrue(MemoLogic.isFresh(m, t0 + 5 * min))

        // 30分後（未到達）に明示的に「またあとで」を押すと、ステージ0のまま起点更新し Fresh へは戻らない
        m = MemoLogic.deferToNextStage(m, t0 + 30 * min)
        assertEquals(0, m.reviewStage)
        assertEquals(t0 + 30 * min, m.lastReviewedAt)
        assertEquals(0, m.reunions)
        assertFalse(MemoLogic.isFresh(m, t0 + 31 * min))

        // 1時間経過後に「またあとで」を押すとステージ1（次は1日）へ進む
        val intervals = listOf(hour, day, 3 * day, 7 * day, 14 * day, 30 * day)
        var cursor = t0 + 30 * min
        for ((idx, interval) in intervals.withIndex()) {
            cursor += interval
            m = MemoLogic.deferToNextStage(m, cursor)
            assertEquals(idx + 1, m.reviewStage)
            assertEquals(idx + 1, m.reunions)
        }
        // 最大ステージ（60日）を超えない
        cursor += 60 * day
        m = MemoLogic.deferToNextStage(m, cursor)
        assertEquals(MemoLogic.FORGETTING_INTERVALS_MS.lastIndex, m.reviewStage)
    }

    @Test
    fun moveToSomedayAndBackToActiveRestoresFresh() {
        val m = memo(createdAt = t0, reviewStage = 3, enteredActiveAt = 0L)
        val someday = MemoLogic.moveToSomeday(m, t0 + day)
        assertTrue(someday.lowPriority)
        assertEquals(t0 + day, someday.lowPriorityAt)

        val restored = MemoLogic.moveToActive(someday, t0 + 2 * day)
        assertFalse(restored.lowPriority)
        assertNull(restored.lowPriorityAt)
        assertEquals(0, restored.reviewStage)
        assertEquals(t0 + 2 * day, restored.lastReviewedAt)
        assertEquals(t0 + 2 * day, restored.enteredActiveAt)
        assertTrue(MemoLogic.isFresh(restored, t0 + 2 * day))
    }

    @Test
    fun titleSkipsLeadingBlankLines() {
        val m = memo().copy(body = "\n\n一行目が題名\n二行目")
        assertEquals("一行目が題名", m.title)
    }

    @Test
    fun elapsedLabelFormatsGradually() {
        assertEquals("たった今", MemoLogic.elapsedLabel(t0, t0 + 30_000L))
        assertEquals("12分前", MemoLogic.elapsedLabel(t0, t0 + 12 * min))
        assertEquals("3時間前", MemoLogic.elapsedLabel(t0, t0 + 3 * hour))
        assertEquals("5日前", MemoLogic.elapsedLabel(t0, t0 + 5 * day))
    }
}
