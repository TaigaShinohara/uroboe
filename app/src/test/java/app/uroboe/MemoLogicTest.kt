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
    fun dueMemosSortByHighestForgettingRatioFirst() {
        // stage 0 (interval 1h): 3h elapsed => ratio 3.0
        val m1 = memo(id = 1L, createdAt = t0 - 3 * hour, reviewStage = 0)
        // stage 1 (interval 1d): 2d elapsed => ratio 2.0
        val m2 = memo(id = 2L, createdAt = t0 - 2 * day, reviewStage = 1)
        // stage 0 (interval 1h): 5h elapsed => ratio 5.0
        val m3 = memo(id = 3L, createdAt = t0 - 5 * hour, reviewStage = 0)

        val sorted = MemoLogic.sortActiveByForgettingCurve(listOf(m1, m2, m3), t0)
        assertEquals(listOf(3L, 1L, 2L), sorted.map { it.id })
    }

    @Test
    fun waitingMemosSortClosestToNextMilestoneFirst() {
        // stage 1 (24h): 20h elapsed => ratio 20/24 = 0.83
        val closer = memo(id = 1L, createdAt = t0 - 20 * hour, reviewStage = 1)
        // stage 1 (24h): 2h elapsed => ratio 2/24 = 0.08
        val newer = memo(id = 2L, createdAt = t0 - 2 * hour, reviewStage = 1)

        val sorted = MemoLogic.sortActiveByForgettingCurve(listOf(newer, closer), t0)
        assertEquals(listOf(1L, 2L), sorted.map { it.id })
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
    fun markReviewedAdvancesStageOnlyWhenMilestoneReached() {
        var m = memo(createdAt = t0)
        // 5分後に誤字修正などで開いても、ステージも基準時刻も飛ばない
        m = MemoLogic.markReviewed(m, t0 + 5 * min)
        assertEquals(0, m.reviewStage)
        assertEquals(t0, m.lastReviewedAt)
        assertEquals(0, m.reunions)

        // 30分後（15分超・1時間未満）：ステージ0のまま起点のみ更新
        m = MemoLogic.markReviewed(m, t0 + 30 * min)
        assertEquals(0, m.reviewStage)
        assertEquals(t0 + 30 * min, m.lastReviewedAt)

        // 1時間経過後に開くとステージ1（次は1日）へ進む
        val intervals = listOf(hour, day, 3 * day, 7 * day, 14 * day, 30 * day)
        var cursor = t0 + 30 * min
        for ((idx, interval) in intervals.withIndex()) {
            cursor += interval
            m = MemoLogic.markReviewed(m, cursor)
            assertEquals(idx + 1, m.reviewStage)
            assertEquals(idx + 1, m.reunions)
        }
        // 最大ステージ（60日）を超えない
        cursor += 60 * day
        m = MemoLogic.markReviewed(m, cursor)
        assertEquals(MemoLogic.FORGETTING_INTERVALS_MS.lastIndex, m.reviewStage)
    }

    @Test
    fun moveToSomedayAndBackToActive() {
        val m = memo(createdAt = t0, reviewStage = 3)
        val someday = MemoLogic.moveToSomeday(m, t0 + day)
        assertTrue(someday.lowPriority)
        assertEquals(t0 + day, someday.lowPriorityAt)

        val restored = MemoLogic.moveToActive(someday, t0 + 2 * day)
        assertFalse(restored.lowPriority)
        assertNull(restored.lowPriorityAt)
        assertEquals(0, restored.reviewStage)
        assertEquals(t0 + 2 * day, restored.lastReviewedAt)
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
