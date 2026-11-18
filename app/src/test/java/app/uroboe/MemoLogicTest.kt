package app.uroboe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoLogicTest {
    private val t0 = 1_700_000_000_000L
    private val day = MemoLogic.DAY_MS

    private fun memo(
        state: MemoState = MemoState.FLOWING,
        createdAt: Long = t0,
        checkpointsDone: Int = 0,
        stage: Int = 0,
        nextDueAt: Long? = null,
        afterglowAt: Long? = null,
    ) = Memo(
        id = 1L, body = "テストメモ", state = state, createdAt = createdAt,
        checkpointsDone = checkpointsDone, stage = stage, nextDueAt = nextDueAt,
        afterglowAt = afterglowAt, updatedAt = createdAt,
    )

    @Test
    fun opacityFadesOver30Days() {
        assertEquals(1.0f, MemoLogic.opacityFor(0), 0.001f)
        assertEquals(0.61f, MemoLogic.opacityFor(15), 0.001f)
        assertEquals(0.22f, MemoLogic.opacityFor(30), 0.001f)
        assertEquals(0.22f, MemoLogic.opacityFor(45), 0.001f)
    }

    @Test
    fun remainingDaysCountsDownFromCreation() {
        assertEquals(30, MemoLogic.remainingDays(memo(), t0))
        assertEquals(15, MemoLogic.remainingDays(memo(), t0 + 15 * day))
        assertEquals(0, MemoLogic.remainingDays(memo(), t0 + 30 * day))
    }

    @Test
    fun checkpointsBecomeDueOnSchedule() {
        assertEquals(0, MemoLogic.dueCheckpointCount(memo(), t0 + 30 * 60_000L))
        assertEquals(1, MemoLogic.dueCheckpointCount(memo(), t0 + MemoLogic.HOUR_MS))
        assertEquals(2, MemoLogic.dueCheckpointCount(memo(), t0 + day))
        assertEquals(3, MemoLogic.dueCheckpointCount(memo(), t0 + 3 * day))
        assertEquals(4, MemoLogic.dueCheckpointCount(memo(), t0 + 7 * day))
        assertEquals(5, MemoLogic.dueCheckpointCount(memo(), t0 + 14 * day))
    }

    @Test
    fun resurfaceCandidatesExcludeConsumedAndCapAt5() {
        val memos = (1..7).map { i ->
            memo(createdAt = t0).copy(id = i.toLong())
        }
        val now = t0 + day // 2 checkpoints due for all
        val candidates = MemoLogic.resurfaceCandidates(memos, now)
        assertEquals(5, candidates.size)
        val consumed = memos.map { MemoLogic.consumeCheckpoint(it, now) }
        assertTrue(MemoLogic.resurfaceCandidates(consumed, now).isEmpty())
    }

    @Test
    fun consumeAdvancesOneCheckpointPerDayToPreserveSpacing() {
        // 3日目に初めて開いた場合：3つ到期していても、その日に進むのは1つだけ（v0.3 B1）
        var m = memo()
        val day3 = t0 + 3 * day
        m = MemoLogic.consumeCheckpoint(m, day3)
        assertEquals(1, m.checkpointsDone)
        assertEquals(1, m.reunions)
        // 同じ日には再び候補に出ない
        assertTrue(MemoLogic.resurfaceCandidates(listOf(m), day3).isEmpty())
        // 翌日には残りのチェックポイントが候補に出て、1つ進む
        val day4 = t0 + 4 * day
        assertEquals(listOf(1L), MemoLogic.resurfaceCandidates(listOf(m), day4).map { it.id })
        m = MemoLogic.consumeCheckpoint(m, day4)
        assertEquals(2, m.checkpointsDone)
    }

    @Test
    fun epochDayUsesLocalOffsetNotUtc() {
        // 日本時間では 15:00 UTC = 翌日0時。UTC基準だと朝9時まで前日扱いになる（v0.3.2修正）
        val jst = 9 * MemoLogic.HOUR_MS
        assertEquals(0L, MemoLogic.epochDay(0L, jst))
        assertEquals(0L, MemoLogic.epochDay(14 * MemoLogic.HOUR_MS, jst))
        assertEquals(1L, MemoLogic.epochDay(15 * MemoLogic.HOUR_MS, jst))
    }

    @Test
    fun titleSkipsLeadingBlankLines() {
        val m = memo().copy(body = "\n\n一行目が題名\n二行目")
        assertEquals("一行目が題名", m.title)
    }

    @Test
    fun consumeWithoutDueCheckpointChangesNothing() {
        val m = memo()
        val after = MemoLogic.consumeCheckpoint(m, t0 + 30 * 60_000L) // 30分後：未到期
        assertEquals(0, after.checkpointsDone)
        assertEquals(0, after.reunions)
    }

    @Test
    fun flowingExpiresToAfterglowThenDeletes() {
        val m = memo()
        val at30 = MemoLogic.evaluate(listOf(m), t0 + 30 * day)
        assertEquals(MemoState.AFTERGLOW, at30.memos.single().state)
        assertTrue(at30.deletedIds.isEmpty())
        val glow = at30.memos.single()
        val at37 = MemoLogic.evaluate(listOf(glow), t0 + 37 * day)
        assertTrue(at37.memos.isEmpty())
        assertEquals(listOf(1L), at37.deletedIds)
    }

    @Test
    fun learningMemosNeverExpire() {
        val m = memo(state = MemoState.LEARNING, nextDueAt = t0 + day)
        val result = MemoLogic.evaluate(listOf(m), t0 + 90 * day)
        assertEquals(MemoState.LEARNING, result.memos.single().state)
    }

    @Test
    fun learningProgressionAndGraduation() {
        var m = MemoLogic.startLearning(memo(), t0)
        assertEquals(MemoState.LEARNING, m.state)
        assertEquals(t0 + day, m.nextDueAt)
        // 1 -> 3 -> 7 -> 14 -> 30 -> 60
        val expectedNext = listOf(3L, 7L, 14L, 30L, 60L)
        var cursor = t0
        for (days in expectedNext) {
            m = MemoLogic.answerRemembered(m, cursor)
            assertEquals(cursor + days * day, m.nextDueAt)
            cursor += days * day
        }
        m = MemoLogic.answerRemembered(m, cursor)
        assertEquals(MemoState.MASTERED, m.state)
    }

    @Test
    fun answerUnsureStepsBackAndRetriesTomorrow() {
        var m = MemoLogic.startLearning(memo(), t0)
        m = MemoLogic.answerRemembered(m, t0) // stage 1
        m = MemoLogic.answerRemembered(m, t0) // stage 2
        val unsure = MemoLogic.answerUnsure(m, t0 + 5 * day)
        assertEquals(1, unsure.stage)
        assertEquals(t0 + 6 * day, unsure.nextDueAt)
        val floor = MemoLogic.answerUnsure(MemoLogic.startLearning(memo(), t0), t0)
        assertEquals(0, floor.stage)
    }

    @Test
    fun reviveGuaranteesThreeDays() {
        val glow = memo(state = MemoState.AFTERGLOW, afterglowAt = t0 + 30 * day)
        val revived = MemoLogic.revive(glow, t0 + 32 * day)
        assertEquals(MemoState.FLOWING, revived.state)
        assertEquals(3, MemoLogic.remainingDays(revived, t0 + 32 * day))
    }

    @Test
    fun dueLearningFiltersByDate() {
        val due = memo(state = MemoState.LEARNING, nextDueAt = t0 + day)
        val notYet = memo(state = MemoState.LEARNING, nextDueAt = t0 + 3 * day).copy(id = 2L)
        val result = MemoLogic.dueLearning(listOf(due, notYet), t0 + 2 * day)
        assertEquals(listOf(1L), result.map { it.id })
    }
}
