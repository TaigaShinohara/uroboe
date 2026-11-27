package app.uroboe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * MemoStore の永続化・v0.3.x移行・prevFile復旧・破損退避・Undo保護の単体テスト。
 */
class MemoStoreTest {
    @get:Rule
    val tmpDir = TemporaryFolder()

    @Test
    fun recoversFromPrevFileWhenPrimaryFileIsMissing() {
        val dir = tmpDir.newFolder("missing_primary")
        val prev = File(dir, "memos.json.prev")
        prev.writeText(
            """
            {
              "schemaVersion": 2,
              "maxSeen": 1700000000000,
              "memos": [
                {
                  "id": 42,
                  "body": "消えてはいけないメモ",
                  "question": null,
                  "answer": null,
                  "createdAt": 1700000000000,
                  "updatedAt": 1700000000000,
                  "lowPriority": false,
                  "lowPriorityAt": null,
                  "reviewStage": 2,
                  "lastReviewedAt": 1700000000000,
                  "enteredActiveAt": 0,
                  "reunions": 2
                }
              ]
            }
            """.trimIndent()
        )

        val store = MemoStore(dir)
        assertEquals(1, store.data.value.memos.size)
        assertEquals("消えてはいけないメモ", store.data.value.memos.first().body)
        assertTrue(File(dir, "memos.json").exists())
    }

    @Test
    fun recoversFromPrevFileAndArchivesCorruptPrimary() {
        val dir = tmpDir.newFolder("corrupt_primary")
        File(dir, "memos.json").writeText("{broken json")
        File(dir, "memos.json.prev").writeText(
            """
            {
              "schemaVersion": 2,
              "maxSeen": 1700000000000,
              "memos": [
                {
                  "id": 7,
                  "body": "バックアップから復旧",
                  "createdAt": 1700000000000,
                  "updatedAt": 1700000000000,
                  "lowPriority": false,
                  "reviewStage": 0,
                  "lastReviewedAt": 1700000000000,
                  "enteredActiveAt": 1700000000000,
                  "reunions": 0
                }
              ]
            }
            """.trimIndent()
        )

        val store = MemoStore(dir)
        assertEquals(1, store.data.value.memos.size)
        assertEquals("バックアップから復旧", store.data.value.memos.first().body)
        assertTrue(dir.listFiles()?.any { it.name.startsWith("memos.json.corrupt-") } == true)
    }

    @Test
    fun saveMaintainsPrimaryAndPreviousBackupFiles() {
        val dir = tmpDir.newFolder("save_backup")
        val store = MemoStore(dir)
        store.addMemo("1件目", now = 1_700_000_000_000L)
        assertTrue(File(dir, "memos.json").exists())

        store.addMemo("2件目", now = 1_700_000_001_000L)
        assertTrue(File(dir, "memos.json").exists())
        assertTrue(File(dir, "memos.json.prev").exists())
        assertFalse(store.lastSaveFailed.value)
    }

    @Test
    fun migratesAllV03StatesWithoutLoss() {
        val dir = tmpDir.newFolder("legacy_v03")
        File(dir, "memos.json").writeText(
            """
            {
              "maxSeen": 1700000000000,
              "memos": [
                {"id": 1, "body": "流れるメモ", "question": "null", "answer": "null", "state": "FLOWING", "createdAt": 1000, "updatedAt": 1500, "checkpointsDone": 2},
                {"id": 2, "body": "定着中メモ", "state": "LEARNING", "createdAt": 2000, "updatedAt": 2500, "stage": 3},
                {"id": 3, "body": "余韻メモ", "state": "AFTERGLOW", "createdAt": 3000, "updatedAt": 3500, "afterglowAt": 3600},
                {"id": 4, "body": "卒業メモ", "state": "MASTERED", "createdAt": 4000, "updatedAt": 4500}
              ]
            }
            """.trimIndent()
        )

        val store = MemoStore(dir)
        val byId = store.data.value.memos.associateBy { it.id }
        assertEquals(4, byId.size)

        val flowing = byId.getValue(1L)
        assertFalse(flowing.lowPriority)
        assertNull(flowing.question)
        assertNull(flowing.answer)
        assertEquals(2, flowing.reviewStage)

        val learning = byId.getValue(2L)
        assertFalse(learning.lowPriority)
        assertEquals(4, learning.reviewStage) // old stage 3 + 1

        val afterglow = byId.getValue(3L)
        assertTrue(afterglow.lowPriority)
        assertEquals(3600L, afterglow.lowPriorityAt)

        val mastered = byId.getValue(4L)
        assertTrue(mastered.lowPriority)
    }

    @Test
    fun clockRebaseAfterForwardJumpRestoresRealTime() {
        // S-1：時計を大きく未来へ進めて戻すと、ガード値が実時刻へ引き戻され曲線が止まらない
        val dir = tmpDir.newFolder("clock_rebase")
        val store = MemoStore(dir)
        val t0 = 1_900_000_000_000L
        val day = 24L * 60 * 60 * 1000
        val m = store.addMemo("時刻テスト", now = t0)

        // 時計を1年先へ進めて開く（ガード値が未来へ進む）
        assertEquals(t0 + 365 * day, store.effectiveNow(t0 + 365 * day))
        // その間に作られたメモ
        val futureMemo = store.addMemo("未来で作られたメモ", now = t0 + 365 * day)

        // 時計を正しい時刻へ戻す → リベースされる
        val back = t0 + 2 * 60 * 60 * 1000L
        assertEquals(back, store.effectiveNow(back))

        val byId = store.data.value.memos.associateBy { it.id }
        // 通常のメモの経過（createdAt）はそのまま保たれる
        assertEquals(t0, byId.getValue(m.id).createdAt)
        // 未来側にあった時刻は実時刻へ丸められ、永遠に Fresh にはならない
        assertEquals(back, byId.getValue(futureMemo.id).createdAt)
    }

    @Test
    fun smallBackwardJumpKeepsMonotonicNow() {
        // S-1：小さな逆行（30分）ではリベースせず、単調増加を維持する
        val dir = tmpDir.newFolder("clock_small_back")
        val store = MemoStore(dir)
        val t0 = 1_900_000_000_000L
        assertEquals(t0, store.effectiveNow(t0))
        assertEquals(t0, store.effectiveNow(t0 - 30 * 60 * 1000L))
    }

    @Test
    fun restoreSnapshotKeepsEditedBody() {
        // S-2：操作後に本文を編集しても、「元に戻す」で戻るのは状態だけ（本文は守られる）
        val dir = tmpDir.newFolder("restore_merge")
        val store = MemoStore(dir)
        val t0 = 1_900_000_000_000L
        val m = store.addMemo("元の本文", now = t0)
        val snapshot = store.data.value.memos.first()

        store.setLowPriority(m.id, true, now = t0 + 1000)
        assertTrue(store.data.value.memos.first().lowPriority)
        store.editBody(m.id, "編集後の本文", null, null, now = t0 + 2000)

        store.restoreSnapshot(snapshot)
        val restored = store.data.value.memos.first()
        assertFalse(restored.lowPriority)
        assertEquals("編集後の本文", restored.body)
    }

    @Test
    fun noticeStackKeepsEarlierUndo() {
        // S-3：続けて操作しても、前の操作の「元に戻す」機会が残る
        val dir = tmpDir.newFolder("notice_stack")
        val store = MemoStore(dir)
        val t0 = 1_900_000_000_000L
        val a = store.addMemo("メモA", now = t0)
        val b = store.addMemo("メモB", now = t0 + 1)

        store.deleteNow(a.id)
        store.postNotice("「やった」で片づけました", "元に戻す", a)
        store.deleteNow(b.id)
        store.postNotice("「やった」で片づけました", "元に戻す", b)
        assertEquals(0, store.data.value.memos.size)

        assertTrue(store.undoLatestNotice())
        assertEquals(listOf("メモB"), store.data.value.memos.map { it.body })

        assertTrue(store.undoLatestNotice())
        assertEquals(2, store.data.value.memos.size)
        assertNull(store.notice.value)
    }

    @Test
    fun noticeForSameMemoKeepsOnlyLatestUndo() {
        // R2-1：同じメモへの新しい操作が出たら古い通知は破棄され、
        // 「元に戻す」が新しい方の状態変更まで巻き戻すことはない
        val dir = tmpDir.newFolder("notice_same_memo")
        val store = MemoStore(dir)
        val t0 = 1_900_000_000_000L
        val m = store.addMemo("同じメモ", now = t0)

        val snap1 = store.data.value.memos.first()
        store.setWillDo(m.id, now = t0 + 1000)
        store.postNotice("「そろそろやる」に決めました", "元に戻す", snap1)

        val snap2 = store.data.value.memos.first()
        store.setLowPriority(m.id, true, now = t0 + 2000)
        store.postNotice("「いつかやればいい」へ保留しました", "元に戻す", snap2)

        assertTrue(store.undoLatestNotice())
        val restored = store.data.value.memos.first()
        assertFalse(restored.lowPriority)
        assertNotNull(restored.willDoAt)
        assertNull(store.notice.value)
        assertFalse(store.undoLatestNotice())
    }

    @Test
    fun quarantinedMemoSurvivesSaveAndNotifies() {
        // S-10：parse できないメモは黙って消えず、原文が保存し直されても残り、起動時に知らされる
        val dir = tmpDir.newFolder("quarantine")
        File(dir, "memos.json").writeText(
            """
            {
              "schemaVersion": 2,
              "maxSeen": 1700000000000,
              "memos": [
                {"id": 1, "body": "読めるメモ", "createdAt": 1700000000000, "updatedAt": 1700000000000, "lowPriority": false, "reviewStage": 0, "lastReviewedAt": 1700000000000, "enteredActiveAt": 1700000000000, "reunions": 0},
                {"id": 2, "body": "壊れたメモUNIQUE"}
              ]
            }
            """.trimIndent()
        )

        val store = MemoStore(dir)
        assertEquals(1, store.data.value.memos.size)
        assertTrue(store.notice.value?.text?.contains("読み込めませんでした") == true)

        store.addMemo("新しいメモ", now = 1_900_000_000_000L)
        assertTrue(File(dir, "memos.json").readText().contains("壊れたメモUNIQUE"))
    }

    @Test
    fun editBodySaveIsDeferredUntilFlush() {
        // S-4：本文編集の保存は間引かれ、flush で確定する
        val dir = tmpDir.newFolder("deferred_save")
        val store = MemoStore(dir)
        val t0 = 1_900_000_000_000L
        val m = store.addMemo("保存テスト", now = t0)

        store.editBody(m.id, "編集した本文", null, null, now = t0 + 1000)
        assertFalse(File(dir, "memos.json").readText().contains("編集した本文"))

        store.flushPendingSave()
        assertTrue(File(dir, "memos.json").readText().contains("編集した本文"))
    }

    @Test
    fun undoLatestNoticeRestoresDeletedSnapshot() {
        val dir = tmpDir.newFolder("undo_test")
        val store = MemoStore(dir)
        val m = store.addMemo("復元したいメモ", now = 1_700_000_000_000L)
        store.deleteNow(m.id)
        store.postNotice("完了しました", "元に戻す", m)
        assertEquals(0, store.data.value.memos.size)

        assertTrue(store.undoLatestNotice())
        assertEquals(1, store.data.value.memos.size)
        assertEquals("復元したいメモ", store.data.value.memos.first().body)
        assertNull(store.notice.value)
    }
}
