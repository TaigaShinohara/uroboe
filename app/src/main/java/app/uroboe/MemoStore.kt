package app.uroboe

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class StoreData(
    val memos: List<Memo> = emptyList(),
)

/**
 * 直前の操作を「元に戻す」ための通知状態。
 * Application スコープの MemoStore に保持することで、画面回転で Undo 機会が失われるのを防ぐ。
 */
data class UndoNotice(
    val id: Long,
    val text: String,
    val undoLabel: String? = null,
    val snapshot: Memo? = null,
)

/** Application スコープで MemoStore を共有し、画面回転時の再生成・不要I/Oを防ぐ。 */
object MemoStoreHolder {
    @Volatile
    private var instance: MemoStore? = null

    fun get(context: Context): MemoStore =
        instance ?: synchronized(this) {
            instance ?: MemoStore(context.applicationContext).also { instance = it }
        }
}

/**
 * 端末内ファイル保存（JSON）。
 * 自動消滅は廃止し、メモは明示的に削除・完了されるまで永続化する。
 * 旧バージョン（v0.3.x）の memos.json からの自動移行に対応する。
 */
class MemoStore(private val baseDir: File) {
    constructor(context: Context) : this(context.filesDir)

    private val file = File(baseDir, "memos.json")
    private val prevFile = File(baseDir, "memos.json.prev")
    private val tmpFile = File(baseDir, "memos.json.tmp")

    private val _data = MutableStateFlow(StoreData())
    val data: StateFlow<StoreData> = _data

    private val _notice = MutableStateFlow<UndoNotice?>(null)
    val notice: StateFlow<UndoNotice?> = _notice

    private val _lastSaveFailed = MutableStateFlow(false)
    val lastSaveFailed: StateFlow<Boolean> = _lastSaveFailed

    private var maxSeen: Long = 0L
    private var noticeSeq: Long = 0L

    init {
        baseDir.mkdirs()
        load()
        advanceClockGuard(System.currentTimeMillis())
    }

    /** 端末時刻の逆行に備え、単調増加の現在時刻を返す（ディスク保存は行わない）。 */
    @Synchronized
    fun effectiveNow(rawNow: Long = System.currentTimeMillis()): Long {
        if (rawNow > maxSeen) {
            maxSeen = rawNow
        }
        return maxSeen
    }

    @Synchronized
    private fun advanceClockGuard(rawNow: Long) {
        if (rawNow > maxSeen) {
            maxSeen = rawNow
        }
    }

    @Synchronized
    fun postNotice(text: String, undoLabel: String? = null, snapshot: Memo? = null) {
        noticeSeq++
        _notice.value = UndoNotice(
            id = noticeSeq,
            text = text,
            undoLabel = undoLabel,
            snapshot = snapshot,
        )
    }

    @Synchronized
    fun dismissNotice(id: Long? = null) {
        val current = _notice.value ?: return
        if (id == null || current.id == id) {
            _notice.value = null
        }
    }

    @Synchronized
    fun undoLatestNotice(): Boolean {
        val current = _notice.value ?: return false
        val snap = current.snapshot ?: return false
        restoreSnapshot(snap)
        _notice.value = null
        return true
    }

    @Synchronized
    fun addMemo(body: String, question: String? = null, answer: String? = null, now: Long = effectiveNow()): Memo {
        advanceClockGuard(now)
        val nanoPart = (System.nanoTime() and Long.MAX_VALUE) % 1000
        val maxExisting = _data.value.memos.maxOfOrNull { it.id } ?: 0L
        val memo = Memo(
            id = maxOf(now * 1000 + nanoPart, maxExisting + 1),
            body = body,
            question = question,
            answer = answer,
            createdAt = now,
            updatedAt = now,
            lowPriority = false,
            lowPriorityAt = null,
            reviewStage = 0,
            lastReviewedAt = now,
            enteredActiveAt = now,
            reunions = 0,
        )
        _data.value = _data.value.copy(memos = _data.value.memos + memo)
        save()
        return memo
    }

    @Synchronized
    fun update(transform: (Memo) -> Memo, id: Long) {
        _data.value = _data.value.copy(
            memos = _data.value.memos.map { if (it.id == id) transform(it) else it }
        )
        save()
    }

    @Synchronized
    fun editBody(id: Long, body: String, question: String?, answer: String?, now: Long = effectiveNow()) {
        advanceClockGuard(now)
        update({ it.copy(body = body, question = question, answer = answer, updatedAt = now) }, id)
    }

    @Synchronized
    fun setLowPriority(id: Long, lowPriority: Boolean, now: Long = effectiveNow()) {
        advanceClockGuard(now)
        update(
            {
                if (lowPriority) MemoLogic.moveToSomeday(it, now)
                else MemoLogic.moveToActive(it, now)
            },
            id,
        )
    }

    @Synchronized
    fun deferToNextStage(id: Long, now: Long = effectiveNow()) {
        advanceClockGuard(now)
        update({ MemoLogic.deferToNextStage(it, now) }, id)
    }

    @Synchronized
    fun deleteNow(id: Long) {
        _data.value = _data.value.copy(memos = _data.value.memos.filterNot { it.id == id })
        save()
    }

    /** 「元に戻す」用。元のスナップショットをそのまま復元する。 */
    @Synchronized
    fun restoreSnapshot(memo: Memo) {
        val exists = _data.value.memos.any { it.id == memo.id }
        val next = if (exists) {
            _data.value.memos.map { if (it.id == memo.id) memo else it }
        } else {
            _data.value.memos + memo
        }
        _data.value = _data.value.copy(memos = next)
        save()
    }

    @Synchronized
    fun deleteAll() {
        _data.value = StoreData()
        _notice.value = null
        save()
    }

    // ---- 永続化 ----

    fun exportJson(): String = buildJson().toString()

    private fun buildJson(): JSONObject {
        val root = JSONObject()
        root.put("maxSeen", maxSeen)
        root.put("schemaVersion", 2)
        val arr = JSONArray()
        for (m in _data.value.memos) {
            val o = JSONObject()
            o.put("id", m.id)
            o.put("body", m.body)
            o.put("question", m.question ?: JSONObject.NULL)
            o.put("answer", m.answer ?: JSONObject.NULL)
            o.put("createdAt", m.createdAt)
            o.put("updatedAt", m.updatedAt)
            o.put("lowPriority", m.lowPriority)
            o.put("lowPriorityAt", m.lowPriorityAt ?: JSONObject.NULL)
            o.put("reviewStage", m.reviewStage)
            o.put("lastReviewedAt", m.lastReviewedAt)
            o.put("enteredActiveAt", m.enteredActiveAt)
            o.put("reunions", m.reunions)
            arr.put(o)
        }
        root.put("memos", arr)
        return root
    }

    private data class Parsed(val data: StoreData, val maxSeen: Long, val skipped: Int)

    private fun parseFile(f: File): Parsed? {
        return try {
            val root = JSONObject(f.readText())
            if (root.has("memos") && root.optJSONArray("memos") == null) return null
            val arr = root.optJSONArray("memos") ?: JSONArray()
            val memos = ArrayList<Memo>()
            var skipped = 0
            for (i in 0 until arr.length()) {
                val o = runCatching { arr.getJSONObject(i) }.getOrNull()
                val parsed = o?.let {
                    runCatching {
                        val createdAt = it.getLong("createdAt")
                        val updatedAt = it.optLong("updatedAt", createdAt)
                        val hasNewSchema = it.has("lowPriority")
                        val legacyState = it.optString("state", "FLOWING")
                        val lowPriority = if (hasNewSchema) {
                            it.optBoolean("lowPriority", false)
                        } else {
                            legacyState == "AFTERGLOW" || legacyState == "MASTERED"
                        }
                        val lowPriorityAt = if (hasNewSchema) {
                            if (it.isNull("lowPriorityAt")) null else it.optLong("lowPriorityAt")
                        } else if (lowPriority) {
                            if (it.isNull("afterglowAt")) updatedAt else it.optLong("afterglowAt", updatedAt)
                        } else {
                            null
                        }
                        val maxStage = MemoLogic.FORGETTING_INTERVALS_MS.lastIndex
                        val reviewStage = if (hasNewSchema) {
                            it.optInt("reviewStage", 0).coerceIn(0, maxStage)
                        } else {
                            when (legacyState) {
                                "LEARNING" -> (it.optInt("stage", 0) + 1).coerceIn(0, maxStage)
                                else -> it.optInt("checkpointsDone", 0).coerceIn(0, maxStage)
                            }
                        }
                        val lastReviewedAt = if (hasNewSchema) {
                            it.optLong("lastReviewedAt", updatedAt)
                        } else {
                            updatedAt
                        }
                        val enteredActiveAt = if (hasNewSchema && it.has("enteredActiveAt")) {
                            it.optLong("enteredActiveAt", createdAt)
                        } else {
                            createdAt
                        }

                        val rawQuestion = if (it.isNull("question")) null else it.optString("question").ifBlank { null }
                        val rawAnswer = if (it.isNull("answer")) null else it.optString("answer").ifBlank { null }
                        val question = if (hasNewSchema) rawQuestion else rawQuestion?.takeIf { s -> s != "null" }
                        val answer = if (hasNewSchema) rawAnswer else rawAnswer?.takeIf { s -> s != "null" }

                        Memo(
                            id = it.getLong("id"),
                            body = it.optString("body", ""),
                            question = question,
                            answer = answer,
                            createdAt = createdAt,
                            updatedAt = updatedAt,
                            lowPriority = lowPriority,
                            lowPriorityAt = lowPriorityAt,
                            reviewStage = reviewStage,
                            lastReviewedAt = lastReviewedAt,
                            enteredActiveAt = enteredActiveAt,
                            reunions = it.optInt("reunions", 0).coerceAtLeast(0),
                        )
                    }.getOrNull()
                }
                if (parsed != null) memos += parsed else skipped++
            }
            Parsed(
                data = StoreData(memos = memos.distinctBy { it.id }),
                maxSeen = root.optLong("maxSeen", 0L),
                skipped = skipped,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun isBroken(parsed: Parsed?): Boolean =
        parsed == null || (parsed.data.memos.isEmpty() && parsed.skipped > 0)

    private fun load() {
        if (file.exists()) {
            runCatching {
                file.copyTo(File(baseDir, "memos.json.snapshot"), overwrite = true)
            }
            val parsed = parseFile(file)
            if (!isBroken(parsed)) {
                val ok = parsed!!
                if (ok.skipped > 0) {
                    runCatching {
                        file.copyTo(File(baseDir, "memos.json.skipped-${System.currentTimeMillis()}"), overwrite = false)
                    }
                }
                maxSeen = ok.maxSeen
                _data.value = ok.data
                return
            }
            // 本体が破損していたら退避しておく
            runCatching {
                val backup = File(baseDir, "memos.json.corrupt-${System.currentTimeMillis()}")
                file.renameTo(backup)
            }
        }

        // 本体が不在または破損時は prevFile → tmpFile の順で自動復旧を試みる
        for (candidate in listOf(prevFile, tmpFile)) {
            if (!candidate.exists()) continue
            val recovered = parseFile(candidate)
            if (!isBroken(recovered)) {
                val ok = recovered!!
                runCatching { candidate.copyTo(file, overwrite = true) }
                maxSeen = ok.maxSeen
                _data.value = ok.data
                return
            }
        }
        _data.value = StoreData()
    }

    /**
     * memos.json が不在になる瞬間を作らない安全な保存：
     * 1) tmpFile に書き出して fsync
     * 2) 既存 file を消さずに prevFile へコピー
     * 3) tmpFile を file へアトミック置換
     */
    private fun save(): Boolean {
        return try {
            val payload = buildJson().toString().toByteArray(Charsets.UTF_8)
            FileOutputStream(tmpFile).use { fos ->
                fos.write(payload)
                fos.flush()
                runCatching { fos.fd.sync() }
            }
            if (file.exists()) {
                runCatching { file.copyTo(prevFile, overwrite = true) }
            }
            val replaced = tmpFile.renameTo(file) || runCatching {
                tmpFile.copyTo(file, overwrite = true)
                true
            }.getOrDefault(false)
            if (tmpFile.exists()) {
                runCatching { tmpFile.delete() }
            }
            _lastSaveFailed.value = !replaced
            replaced
        } catch (_: Exception) {
            _lastSaveFailed.value = true
            false
        }
    }
}
