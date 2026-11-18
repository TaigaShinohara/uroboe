package app.uroboe

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class StoreData(
    val memos: List<Memo> = emptyList(),
    val deletedEvents: List<Long> = emptyList(),
    val masteredEvents: List<Long> = emptyList(),
)

/**
 * 端末内ファイル保存（JSON）。仕様 §7 のデータモデルをそのまま保持する。
 * 時刻ガード：記録済みの最大時刻 maxSeen を保持し、端末時刻が巻き戻っても
 * 消滅が早まる事故が起きないよう、評価には max(現在時刻, maxSeen) を使う。
 */
class MemoStore(context: Context) {
    private val file = File(context.filesDir, "memos.json")
    // v0.3.3：前世代の保存ファイル。保存のたびに1世代だけ残し、読込不能時はここから復旧する。
    private val prevFile = File(context.filesDir, "memos.json.prev")
    private val _data = MutableStateFlow(StoreData())
    val data: StateFlow<StoreData> = _data
    private var maxSeen: Long = 0L

    init {
        load()
        refresh()
    }

    /** 起動時・復帰時の一括評価（消滅／余韻明け削除）。 */
    @Synchronized
    fun refresh(now: Long = System.currentTimeMillis()) {
        val effective = maxOf(now, maxSeen)
        if (now > maxSeen) maxSeen = now
        val result = MemoLogic.evaluate(_data.value.memos, effective)
        if (result.deletedIds.isNotEmpty() || result.memos != _data.value.memos) {
            val newDeleted = _data.value.deletedEvents + List(result.deletedIds.size) { effective }
            _data.value = _data.value.copy(memos = result.memos, deletedEvents = newDeleted)
            save()
        } else {
            save() // maxSeen の更新を残す
        }
    }

    @Synchronized
    fun addMemo(body: String, question: String?, answer: String?, now: Long = System.currentTimeMillis()): Memo {
        // v0.3.2 修正：nanoTime は負になり得て ID が衝突し、一覧のキー重複で落ちる恐れがあった。
        // 時刻ベースの下限と「既存の最大ID+1」の大きい方で一意性を保証する。
        val nanoPart = (System.nanoTime() and Long.MAX_VALUE) % 1000
        val maxExisting = _data.value.memos.maxOfOrNull { it.id } ?: 0L
        val memo = Memo(
            id = maxOf(now * 1000 + nanoPart, maxExisting + 1),
            body = body,
            question = question,
            answer = answer,
            createdAt = now,
            updatedAt = now,
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
        // 30日タイマーは作成日基準。編集では createdAt を触らない（仕様 §3 不変ルール）。
        update({ it.copy(body = body, question = question, answer = answer, updatedAt = now) }, id)
    }

    @Synchronized
    fun deleteNow(id: Long) {
        _data.value = _data.value.copy(memos = _data.value.memos.filterNot { it.id == id })
        save()
    }

    @Synchronized
    fun consumeCheckpoints(ids: Collection<Long>, now: Long = System.currentTimeMillis()) {
        if (ids.isEmpty()) return
        val dayOffset = java.util.TimeZone.getDefault().getOffset(now).toLong()
        _data.value = _data.value.copy(
            memos = _data.value.memos.map {
                if (it.id in ids) MemoLogic.consumeCheckpoint(it, now, dayOffset) else it
            }
        )
        save()
    }

    @Synchronized
    fun answerLearning(id: Long, remembered: Boolean, now: Long = System.currentTimeMillis()) {
        val target = _data.value.memos.firstOrNull { it.id == id } ?: return
        // v0.3.8（安定性レビュー）：二度押しや古い画面からの回答でステージが二重に進まないよう、
        // 「定着中かつ期限到来済み」のときだけ回答を受け付ける。1回答えると次回期限は未来に
        // なるため、連続タップの2回目はここで無効になる。
        if (target.state != MemoState.LEARNING) return
        if ((target.nextDueAt ?: Long.MAX_VALUE) > now) return
        val updated = if (remembered) MemoLogic.answerRemembered(target, now) else MemoLogic.answerUnsure(target, now)
        _data.value = _data.value.copy(
            memos = _data.value.memos.map { if (it.id == id) updated else it },
            masteredEvents = if (updated.state == MemoState.MASTERED) _data.value.masteredEvents + now else _data.value.masteredEvents,
        )
        save()
    }

    @Synchronized
    fun deleteAll() {
        _data.value = StoreData()
        save()
    }

    // ---- 永続化 ----

    /**
     * v0.3.4（見直しY-2）：控え用の書き出し。
     * アンインストールや端末故障ではアプリ内データごと消えるため、利用者が自分の手で
     * 控えを外へ持ち出せる唯一の手段として用意する（同期・サーバーは引き続きなし）。
     * v0.3.8（安定性レビュー）：ディスク上のファイルでなくメモリ上の現在状態から組み立てる。
     * 直前の保存が失敗していた場合に、画面と違う古い内容が控えになる事故を防ぐ。
     */
    fun exportJson(): String = buildJson().toString()

    /** 保存と書き出しで共用する、現在状態の JSON 化。 */
    private fun buildJson(): JSONObject {
        val root = JSONObject()
        root.put("maxSeen", maxSeen)
        val arr = JSONArray()
        for (m in _data.value.memos) {
            val o = JSONObject()
            o.put("id", m.id)
            o.put("body", m.body)
            o.put("question", m.question ?: JSONObject.NULL)
            o.put("answer", m.answer ?: JSONObject.NULL)
            o.put("state", m.state.name)
            o.put("createdAt", m.createdAt)
            o.put("checkpointsDone", m.checkpointsDone)
            o.put("stage", m.stage)
            o.put("nextDueAt", m.nextDueAt ?: JSONObject.NULL)
            o.put("afterglowAt", m.afterglowAt ?: JSONObject.NULL)
            o.put("updatedAt", m.updatedAt)
            o.put("reunions", m.reunions)
            o.put("lastReunionEpochDay", m.lastReunionEpochDay)
            arr.put(o)
        }
        root.put("memos", arr)
        root.put("deletedEvents", JSONArray(_data.value.deletedEvents))
        root.put("masteredEvents", JSONArray(_data.value.masteredEvents))
        return root
    }

    private data class Parsed(val data: StoreData, val maxSeen: Long, val skipped: Int)

    /** 1ファイルを解釈する。全体が読めない場合だけ null（呼び出し側で前世代にフォールバック）。 */
    private fun parseFile(f: File): Parsed? {
        return try {
            val root = JSONObject(f.readText())
            val memos = ArrayList<Memo>()
            var skipped = 0
            // v0.3.8（安定性レビュー）：「memos」キーがあるのに配列として読めないファイルは
            // 「メモ0件の正常データ」と誤認せず、全体が壊れているものとして前世代の復旧へ回す。
            if (root.has("memos") && root.optJSONArray("memos") == null) return null
            val arr = root.optJSONArray("memos") ?: JSONArray()
            for (i in 0 until arr.length()) {
                // v0.3.2 修正：1件の破損で全件を失わないよう、読めないメモだけ読み飛ばす
                val o = runCatching { arr.getJSONObject(i) }.getOrNull()
                val parsed = o?.let {
                    runCatching {
                        Memo(
                            id = it.getLong("id"),
                            body = it.optString("body", ""),
                            // v0.3.1 バグ修正：Android の org.json は JSON null を文字列 "null" で返すため、
                            // 通常メモが「クイズ形式・題名 null」と誤表示されていた。isNull で判定し、
                            // 既に "null" 文字列で保存されてしまったデータも読み替えで回復する。
                            question = if (it.isNull("question")) null else it.optString("question").ifBlank { null }?.takeIf { s -> s != "null" },
                            answer = if (it.isNull("answer")) null else it.optString("answer").ifBlank { null }?.takeIf { s -> s != "null" },
                            state = runCatching { MemoState.valueOf(it.optString("state", "FLOWING")) }.getOrDefault(MemoState.FLOWING),
                            createdAt = it.getLong("createdAt"),
                            // v0.3.8（安定性レビュー）：数値が壊れて範囲外でも想定外の遷移や表示に
                            // ならないよう、読み込み時に正規の範囲へ丸める。
                            checkpointsDone = it.optInt("checkpointsDone", 0)
                                .coerceIn(0, MemoLogic.CHECKPOINT_OFFSETS_MS.size),
                            stage = it.optInt("stage", 0)
                                .coerceIn(0, MemoLogic.STAGE_INTERVAL_DAYS.lastIndex),
                            nextDueAt = if (it.isNull("nextDueAt")) null else it.optLong("nextDueAt"),
                            afterglowAt = if (it.isNull("afterglowAt")) null else it.optLong("afterglowAt"),
                            updatedAt = it.optLong("updatedAt", it.getLong("createdAt")),
                            reunions = it.optInt("reunions", 0).coerceAtLeast(0),
                            lastReunionEpochDay = it.optLong("lastReunionEpochDay", 0L).coerceAtLeast(0L),
                        )
                    }.getOrNull()
                }
                if (parsed != null) memos += parsed else skipped++
            }
            Parsed(
                data = StoreData(
                    // v0.3.2：万一IDが重複していたら先勝ちで一意化する（一覧のキー重複クラッシュ防止）
                    memos = memos.distinctBy { it.id },
                    deletedEvents = root.optJSONArray("deletedEvents").toLongList(),
                    masteredEvents = root.optJSONArray("masteredEvents").toLongList(),
                ),
                maxSeen = root.optLong("maxSeen", 0L),
                skipped = skipped,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun load() {
        if (!file.exists()) return
        // v0.3.3：上書きされる前に、読み込んだ原本の控えを残す（原因調査と復旧のため）。
        runCatching {
            file.copyTo(File(file.parentFile, "memos.json.snapshot"), overwrite = true)
        }
        val parsed = parseFile(file)
        // 全体が読めない／全件読み飛ばしになった場合は「壊れた」とみなす（正当な全消去は
        // 有効な空配列として読めるので、ここには来ない）。
        val broken = parsed == null || (parsed.data.memos.isEmpty() && parsed.skipped > 0)
        if (!broken && parsed != null) {
            if (parsed.skipped > 0) {
                // 一部だけ読めなかった：原本を証拠として残してから続行する。
                runCatching {
                    file.copyTo(File(file.parentFile, "memos.json.skipped-${System.currentTimeMillis()}"), overwrite = false)
                }
            }
            maxSeen = parsed.maxSeen
            _data.value = parsed.data
            return
        }
        // v0.3.3：いきなり空で始めず、前世代ファイルからの復旧を試す。
        val prev = if (prevFile.exists()) parseFile(prevFile) else null
        if (prev != null && prev.data.memos.isNotEmpty()) {
            runCatching { prevFile.copyTo(file, overwrite = true) }
            maxSeen = prev.maxSeen
            _data.value = prev.data
            return
        }
        // v0.3〜（見直しA3）：破損したファイルは退避してから新規開始する。
        // 「自然に消えた」のか「壊れた」のか分からない全消失を防ぐ。
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
            // v0.3〜（見直しA3）：一時ファイルへ書いてから rename する原子的保存。
            // 書込み中断で本体が壊れ、全メモが消える事故を防ぐ。
            // v0.3.3：差し替え前に現行ファイルを memos.json.prev へ1世代残す（読込不能時の復旧元）。
            val tmp = File(file.parentFile, "memos.json.tmp")
            tmp.writeText(root.toString())
            if (file.exists()) {
                prevFile.delete()
                file.renameTo(prevFile)
            }
            if (!tmp.renameTo(file)) {
                // v0.3.8（安定性レビュー）：rename 失敗時は、まず一時ファイルの内容（最新）を
                // 本体へ写す。それも失敗したときだけ前世代の復旧＋直接書きへ落とす。
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
            // 保存失敗時はメモリ上の状態を維持する（MVPでは握りつぶし、次回保存で再試行）
        }
    }

    private fun JSONArray?.toLongList(): List<Long> {
        if (this == null) return emptyList()
        val out = ArrayList<Long>(length())
        for (i in 0 until length()) out += optLong(i)
        return out
    }
}
