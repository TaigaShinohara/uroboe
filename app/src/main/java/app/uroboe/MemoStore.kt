package app.uroboe

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class StoreData(
    val memos: List<Memo> = emptyList(),
)

/**
 * 端末内ファイル保存（JSON）。
 * 自動消滅は廃止し、メモは明示的に削除されるまで永続化する。
 * 旧バージョン（v0.3.x）の memos.json からの自動移行に対応する。
 */
class MemoStore(context: Context) {
    private val file = File(context.filesDir, "memos.json")
    private val prevFile = File(context.filesDir, "memos.json.prev")
    private val _data = MutableStateFlow(StoreData())
    val data: StateFlow<StoreData> = _data
    private var maxSeen: Long = 0L

    init {
        load()
        refresh()
    }

    /** 起動時・復帰時の現在時刻ガード更新（自動削除は行わない）。 */
    @Synchronized
    fun refresh(now: Long = System.currentTimeMillis()) {
        if (now > maxSeen) {
            maxSeen = now
            save()
        }
    }

    @Synchronized
    fun addMemo(body: String, question: String?, answer: String?, now: Long = System.currentTimeMillis()): Memo {
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
    fun editBody(id: Long, body: String, question: String?, answer: String?, now: Long = System.currentTimeMillis()) {
        update({ it.copy(body = body, question = question, answer = answer, updatedAt = now) }, id)
    }

    @Synchronized
    fun setLowPriority(id: Long, lowPriority: Boolean, now: Long = System.currentTimeMillis()) {
        update(
            {
                if (lowPriority) MemoLogic.moveToSomeday(it, now)
                else MemoLogic.moveToActive(it, now)
            },
            id,
        )
    }

    @Synchronized
    fun markReviewed(id: Long, now: Long = System.currentTimeMillis()) {
        update({ MemoLogic.markReviewed(it, now) }, id)
    }

    @Synchronized
    fun deleteNow(id: Long) {
        _data.value = _data.value.copy(memos = _data.value.memos.filterNot { it.id == id })
        save()
    }

    /** スワイプ降格・復帰・削除の「元に戻す」用。元のスナップショットをそのまま復元する。 */
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

                        Memo(
                            id = it.getLong("id"),
                            body = it.optString("body", ""),
                            question = if (it.isNull("question")) null else it.optString("question").ifBlank { null }?.takeIf { s -> s != "null" },
                            answer = if (it.isNull("answer")) null else it.optString("answer").ifBlank { null }?.takeIf { s -> s != "null" },
                            createdAt = createdAt,
                            updatedAt = updatedAt,
                            lowPriority = lowPriority,
                            lowPriorityAt = lowPriorityAt,
                            reviewStage = reviewStage,
                            lastReviewedAt = lastReviewedAt,
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

    private fun load() {
        if (!file.exists()) return
        runCatching {
            file.copyTo(File(file.parentFile, "memos.json.snapshot"), overwrite = true)
        }
        val parsed = parseFile(file)
        val broken = parsed == null || (parsed.data.memos.isEmpty() && parsed.skipped > 0)
        if (!broken) {
            if (parsed.skipped > 0) {
                runCatching {
                    file.copyTo(File(file.parentFile, "memos.json.skipped-${System.currentTimeMillis()}"), overwrite = false)
                }
            }
            maxSeen = parsed.maxSeen
            _data.value = parsed.data
            return
        }
        val prev = if (prevFile.exists()) parseFile(prevFile) else null
        if (prev != null && prev.data.memos.isNotEmpty()) {
            runCatching { prevFile.copyTo(file, overwrite = true) }
            maxSeen = prev.maxSeen
            _data.value = prev.data
            return
        }
        try {
            val backup = File(file.parentFile, "memos.json.corrupt-${System.currentTimeMillis()}")
            file.renameTo(backup)
        } catch (_: Exception) {
        }
        _data.value = StoreData()
    }

    private fun save() {
        try {
            val root = buildJson()
            val tmp = File(file.parentFile, "memos.json.tmp")
            tmp.writeText(root.toString())
            if (file.exists()) {
                prevFile.delete()
                file.renameTo(prevFile)
            }
            if (!tmp.renameTo(file)) {
                val copied = runCatching { tmp.copyTo(file, overwrite = true) }.isSuccess
                if (!copied) {
                    if (!file.exists() && prevFile.exists()) {
                        runCatching { prevFile.copyTo(file, overwrite = true) }
                    }
                    if (!file.exists()) runCatching { file.writeText(root.toString()) }
                }
                tmp.delete()
            }
        } catch (_: Exception) {
        }
    }
}
