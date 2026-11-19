package app.uroboe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
