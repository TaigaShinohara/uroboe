package app.uroboe

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

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

    /** v0.7.3：再会カードを「今日だけ閉じた」記録（MemoLogic.reunionDismissKey の文字列）。 */
    private var reunionDismissedKey: String? = null

    private val _reunionDismissed = MutableStateFlow<String?>(null)
    val reunionDismissed: StateFlow<String?> = _reunionDismissed

    private var maxSeen: Long = 0L
    private var noticeSeq: Long = 0L

    /** v0.7.2（S-3）：通知は単一枠ではなくスタックで保持し、前の操作の復元機会が差し替えで消えないようにする。 */
    private val noticeStack = ArrayDeque<UndoNotice>()

    /** v0.7.2（S-10）：parse できなかったメモ要素の原文。次回保存でもそのまま書き戻し、黙って消えないようにする。 */
    private var quarantinedRaw: List<String> = emptyList()

    /** v0.7.2（S-4）：入力中の保存を間引くための遅延保存（単一スレッド・デーモン）。 */
    private val saveExecutor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "uroboe-save").apply { isDaemon = true }
    }

    @Volatile
    private var pendingSave: ScheduledFuture<*>? = null

    init {
        baseDir.mkdirs()
        load()
        effectiveNow()
    }

    /**
     * 端末時刻の逆行に備え、単調増加の現在時刻を返す。
     * v0.7.2（S-1）：実時刻がガード値より大きく（2時間超）遅れている場合は「時計が未来へ進められて
     * 戻された」とみなし、ガード値を実時刻へ引き戻す（リベース）。未来側にあるメモの時刻は
     * 実時刻へ丸め、通常のメモの経過はそのまま保つ。これをしないと前方ジャンプ1回で
     * 忘却曲線が恒常的に止まってしまう。
     */
    @Synchronized
    fun effectiveNow(rawNow: Long = System.currentTimeMillis()): Long {
        if (rawNow > maxSeen) {
            maxSeen = rawNow
        } else if (maxSeen - rawNow > CLOCK_REBASE_THRESHOLD_MS) {
            rebaseClock(rawNow)
        }
        return maxSeen
    }

    @Synchronized
    private fun advanceClockGuard(rawNow: Long) {
        if (rawNow > maxSeen) {
            maxSeen = rawNow
        }
    }

    /** S-1：ガード値を実時刻へ引き戻し、未来側のメモ時刻を実時刻へ丸める（相対的な経過は保たれる）。 */
    private fun rebaseClock(rawNow: Long) {
        fun clamp(t: Long): Long = minOf(t, rawNow)
        _data.value = _data.value.copy(
            memos = _data.value.memos.map { m ->
                m.copy(
                    createdAt = clamp(m.createdAt),
                    updatedAt = clamp(m.updatedAt),
                    lastReviewedAt = clamp(m.lastReviewedAt),
                    enteredActiveAt = clamp(m.enteredActiveAt),
                    willDoAt = m.willDoAt?.let { clamp(it) },
                    lowPriorityAt = m.lowPriorityAt?.let { clamp(it) },
                )
            }
        )
        maxSeen = rawNow
        save()
    }

    @Synchronized
    fun postNotice(text: String, undoLabel: String? = null, snapshot: Memo? = null) {
        noticeSeq++
        // v0.7.2（R2-1）：同じメモへの新しい操作が出たら、そのメモの古い通知は破棄する。
        // 古いスナップショットの復元が、より新しい状態変更まで巻き戻してしまうのを防ぐ
        // （戻せるのは、そのメモについては直近の操作まで）。
        if (snapshot != null) {
            noticeStack.removeAll { it.snapshot?.id == snapshot.id }
        }
        noticeStack.addLast(
            UndoNotice(
                id = noticeSeq,
                text = text,
                undoLabel = undoLabel,
                snapshot = snapshot,
            )
        )
        // 古いものから落とす（保持は直近 MAX_NOTICES 件まで）
        while (noticeStack.size > MAX_NOTICES) {
            noticeStack.removeFirst()
        }
        _notice.value = noticeStack.lastOrNull()
    }

    @Synchronized
    fun dismissNotice(id: Long? = null) {
        if (id == null) {
            if (noticeStack.isNotEmpty()) noticeStack.removeLast()
        } else {
            noticeStack.removeAll { it.id == id }
        }
        _notice.value = noticeStack.lastOrNull()
    }

    @Synchronized
    fun undoLatestNotice(): Boolean {
        val current = noticeStack.lastOrNull() ?: return false
        val snap = current.snapshot ?: return false
        noticeStack.removeLast()
        _notice.value = noticeStack.lastOrNull()
        restoreSnapshot(snap)
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
    fun update(transform: (Memo) -> Memo, id: Long, deferredSave: Boolean = false) {
        _data.value = _data.value.copy(
            memos = _data.value.memos.map { if (it.id == id) transform(it) else it }
        )
        if (deferredSave) scheduleSave() else save()
    }

    @Synchronized
    fun editBody(id: Long, body: String, question: String?, answer: String?, now: Long = effectiveNow()) {
        advanceClockGuard(now)
        // v0.7.2（S-4）：本文入力はキー入力ごとに走るため、保存は間引いて裏でまとめる
        update({ it.copy(body = body, question = question, answer = answer, updatedAt = now) }, id, deferredSave = true)
    }

    /** S-4：遅延保存を予約する（連続入力では最後の1回だけが実行される）。 */
    private fun scheduleSave() {
        pendingSave?.cancel(false)
        pendingSave = saveExecutor.schedule(
            { synchronized(this) { save() } },
            SAVE_DEBOUNCE_MS,
            TimeUnit.MILLISECONDS,
        )
    }

    /** S-4：保留中の保存をその場で確定させる（画面離脱・アプリのバックグラウンド化で呼ぶ）。 */
    fun flushPendingSave() {
        val pending = pendingSave ?: return
        pendingSave = null
        pending.cancel(false)
        synchronized(this) { save() }
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

    /** v0.7.3：再会カードの「そろそろやろうに戻す」。忘却曲線の最初（ステージ0）から静かに再スタートする。 */
    @Synchronized
    fun restartCurve(id: Long, now: Long = effectiveNow()) {
        advanceClockGuard(now)
        update({ MemoLogic.moveToActive(it, now) }, id)
    }

    /** v0.7.3：再会カードを今日だけ閉じる。記録は日付入りのキーなので、翌日には自然に意味を失う。 */
    @Synchronized
    fun dismissReunion(key: String) {
        reunionDismissedKey = key
        _reunionDismissed.value = key
        save()
    }

    /** 「そろそろやる」と決める（v0.4.3）。 */
    @Synchronized
    fun setWillDo(id: Long, now: Long = effectiveNow()) {
        advanceClockGuard(now)
        update({ MemoLogic.setWillDo(it, now) }, id)
    }

    @Synchronized
    fun deleteNow(id: Long) {
        _data.value = _data.value.copy(memos = _data.value.memos.filterNot { it.id == id })
        save()
    }

    /**
     * 「元に戻す」用。
     * v0.7.2（S-2）：メモがまだ存在する場合は、状態フィールドだけを操作前に戻し、
     * 本文（body／question／answer）と更新時刻は現在の値（操作後に編集されていればその内容）を守る。
     * メモが消えている場合（「やった」の取り消し）はスナップショットをそのまま戻す。
     */
    @Synchronized
    fun restoreSnapshot(memo: Memo) {
        val current = _data.value.memos.firstOrNull { it.id == memo.id }
        val next = if (current != null) {
            val merged = memo.copy(
                body = current.body,
                question = current.question,
                answer = current.answer,
                updatedAt = current.updatedAt,
            )
            _data.value.memos.map { if (it.id == memo.id) merged else it }
        } else {
            _data.value.memos + memo
        }
        _data.value = _data.value.copy(memos = next)
        save()
    }

    @Synchronized
    fun deleteAll() {
        _data.value = StoreData()
        noticeStack.clear()
        _notice.value = null
        quarantinedRaw = emptyList()
        reunionDismissedKey = null
        _reunionDismissed.value = null
        save()
    }

    // ---- 永続化 ----

    fun exportJson(): String = buildJson().toString()

    private fun buildJson(): JSONObject {
        val root = JSONObject()
        root.put("maxSeen", maxSeen)
        root.put("schemaVersion", 2)
        root.put("reunionDismissed", reunionDismissedKey ?: JSONObject.NULL)
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
            o.put("willDoAt", m.willDoAt ?: JSONObject.NULL)
            o.put("reunions", m.reunions)
            arr.put(o)
        }
        // S-10：読み込めなかった要素は原文のまま書き戻し、保存のたびに消えていかないようにする
        for (raw in quarantinedRaw) {
            val restored = runCatching { JSONObject(raw) }.getOrNull()
            if (restored != null) arr.put(restored) else arr.put(raw)
        }
        root.put("memos", arr)
        return root
    }

    private data class Parsed(
        val data: StoreData,
        val maxSeen: Long,
        val skipped: Int,
        val quarantined: List<String> = emptyList(),
        val reunionDismissed: String? = null,
    )

    private fun parseFile(f: File): Parsed? {
        return try {
            val root = JSONObject(f.readText())
            if (root.has("memos") && root.optJSONArray("memos") == null) return null
            val arr = root.optJSONArray("memos") ?: JSONArray()
            val memos = ArrayList<Memo>()
            var skipped = 0
            val quarantined = ArrayList<String>()
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
                            // v0.4.3：「そろそろやる」宣言時刻。旧データには無いので null。
                            willDoAt = if (it.isNull("willDoAt")) null else it.optLong("willDoAt").takeIf { v -> v > 0L },
                            reunions = it.optInt("reunions", 0).coerceAtLeast(0),
                        )
                    }.getOrNull()
                }
                if (parsed != null) {
                    memos += parsed
                } else {
                    skipped++
                    // S-10：黙って捨てず、原文を隔離保持する（次回保存でも書き戻される）
                    runCatching { arr.get(i)?.toString() }.getOrNull()?.let { quarantined += it }
                }
            }
            Parsed(
                data = StoreData(memos = memos.distinctBy { it.id }),
                maxSeen = root.optLong("maxSeen", 0L),
                skipped = skipped,
                quarantined = quarantined,
                reunionDismissed = if (root.isNull("reunionDismissed")) null
                else root.optString("reunionDismissed").ifBlank { null },
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun isBroken(parsed: Parsed?): Boolean =
        parsed == null || (parsed.data.memos.isEmpty() && parsed.skipped > 0)

    private fun load() {
        if (file.exists()) {
            // v0.7.2（L-2）：読み取り側のない memos.json.snapshot コピーは廃止した
            val parsed = parseFile(file)
            if (!isBroken(parsed)) {
                val ok = parsed!!
                if (ok.skipped > 0) {
                    runCatching {
                        file.copyTo(File(baseDir, "memos.json.skipped-${System.currentTimeMillis()}"), overwrite = false)
                    }
                }
                maxSeen = ok.maxSeen
                quarantinedRaw = ok.quarantined
                reunionDismissedKey = ok.reunionDismissed
                _reunionDismissed.value = ok.reunionDismissed
                _data.value = ok.data
                if (ok.skipped > 0) {
                    // S-10：黙って消えたことにせず、起動直後に知らせる（データ自体は隔離保持される）
                    postNotice("一部のメモを読み込めませんでした（${ok.skipped}件）。データは残してあります")
                }
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
                quarantinedRaw = ok.quarantined
                reunionDismissedKey = ok.reunionDismissed
                _reunionDismissed.value = ok.reunionDismissed
                _data.value = ok.data
                if (ok.skipped > 0) {
                    postNotice("一部のメモを読み込めませんでした（${ok.skipped}件）。データは残してあります")
                }
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
        // 同期保存が走ったら、保留中の遅延保存は不要になる
        pendingSave?.cancel(false)
        pendingSave = null
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
            // v0.4.2（B-3修正）：予備経路でも file を直接上書きしない。
            // 別名ファイルへ書き出してから rename で置換し、途中中断で本体が壊れる窓を作らない。
            val replaced = tmpFile.renameTo(file) || runCatching {
                val altFile = File(baseDir, "memos.json.alt")
                FileOutputStream(altFile).use { fos ->
                    fos.write(payload)
                    fos.flush()
                    runCatching { fos.fd.sync() }
                }
                val ok = altFile.renameTo(file)
                if (!ok) runCatching { altFile.delete() }
                ok
            }.getOrDefault(false)
            // v0.7.2（S-9）：置換に失敗したときは tmp を残す（次回起動の復旧材料になる）
            if (replaced && tmpFile.exists()) {
                runCatching { tmpFile.delete() }
            }
            _lastSaveFailed.value = !replaced
            replaced
        } catch (_: Exception) {
            _lastSaveFailed.value = true
            false
        }
    }

    companion object {
        /** S-1：実時刻がガード値よりこの幅を超えて遅れていたら、時計が戻されたとみなして引き戻す。 */
        private const val CLOCK_REBASE_THRESHOLD_MS = 2L * 60 * 60 * 1000

        /** S-3：保持する通知（元に戻す機会）の上限。 */
        private const val MAX_NOTICES = 10

        /** S-4：入力中の保存をまとめる間隔。 */
        private const val SAVE_DEBOUNCE_MS = 400L
    }
}
