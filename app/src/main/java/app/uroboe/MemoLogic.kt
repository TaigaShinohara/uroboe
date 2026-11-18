package app.uroboe

/** メモの状態。仕様書 §3 の状態遷移が正本。 */
enum class MemoState { FLOWING, LEARNING, MASTERED, AFTERGLOW }

data class Memo(
    val id: Long,
    val body: String,
    val question: String? = null,
    val answer: String? = null,
    val state: MemoState = MemoState.FLOWING,
    val createdAt: Long,
    val checkpointsDone: Int = 0,
    val stage: Int = 0,
    val nextDueAt: Long? = null,
    val afterglowAt: Long? = null,
    val updatedAt: Long = createdAt,
    val reunions: Int = 0,
    /** 最後に再会を消化した日（epoch day）。v0.3〜：間隔を保つため1日1つまでしか消化しない。 */
    val lastReunionEpochDay: Long = 0,
) {
    val isQuiz: Boolean get() = !question.isNullOrBlank()
    /** 一覧・余韻で見せる題名（質問か本文の最初の空でない行）。 */
    val title: String get() = (question ?: body).lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    /** 表示本文（クイズ形式なら答え、通常は本文）。 */
    val displayBody: String get() = if (isQuiz) (answer ?: "") else body
}

/**
 * 純粋ロジック（Android非依存・単体テスト対象）。仕様書 §4 / §5 が正本。
 * 時刻はすべて epoch ミリ秒で受け取る。
 */
object MemoLogic {
    const val HOUR_MS = 3_600_000L
    const val DAY_MS = 86_400_000L
    const val EXPIRE_DAYS = 30
    const val AFTERGLOW_DAYS = 7
    const val MAX_RESURFACE = 5

    /** 再会チェックポイント（作成からの経過）。 */
    val CHECKPOINT_OFFSETS_MS = listOf(HOUR_MS, DAY_MS, 3 * DAY_MS, 7 * DAY_MS, 14 * DAY_MS)
    val CHECKPOINT_LABELS = listOf("1時間後", "翌日", "3日後", "7日後", "14日後")

    /** 定着ステージの間隔（日数）。 */
    val STAGE_INTERVAL_DAYS = listOf(1, 3, 7, 14, 30, 60)

    fun ageDays(memo: Memo, now: Long): Int =
        ((now - memo.createdAt) / DAY_MS).toInt().coerceAtLeast(0)

    fun remainingDays(memo: Memo, now: Long): Int = EXPIRE_DAYS - ageDays(memo, now)

    /** 薄れ表現： opacity = 1.0 − 0.78 × (経過日数/30)、下限 0.22。 */
    fun opacityFor(ageDays: Int): Float {
        val ratio = ageDays.coerceIn(0, EXPIRE_DAYS).toFloat() / EXPIRE_DAYS.toFloat()
        return (1.0f - 0.78f * ratio).coerceAtLeast(0.22f)
    }

    /** 期限到来済みのチェックポイント数。 */
    fun dueCheckpointCount(memo: Memo, now: Long): Int {
        val elapsed = now - memo.createdAt
        return CHECKPOINT_OFFSETS_MS.count { it <= elapsed }
    }

    data class EvalResult(
        val memos: List<Memo>,
        /** 今回の評価で完全削除されたメモの id（成果カウント用）。 */
        val deletedIds: List<Long>,
    )

    /**
     * 起動時の一括評価：FLOWING の30日経過 → AFTERGLOW、余韻7日経過 → 完全削除。
     * effectiveNow は時刻ガード適用済みの時刻（呼び出し側で max(現在時刻, 記録済み最大時刻) を渡す）。
     */
    fun evaluate(memos: List<Memo>, effectiveNow: Long): EvalResult {
        val kept = ArrayList<Memo>(memos.size)
        val deleted = ArrayList<Long>()
        for (m in memos) {
            when (m.state) {
                MemoState.FLOWING -> {
                    val expireAt = m.createdAt + EXPIRE_DAYS * DAY_MS
                    if (effectiveNow >= expireAt) {
                        val glowEnd = expireAt + AFTERGLOW_DAYS * DAY_MS
                        if (effectiveNow >= glowEnd) {
                            deleted += m.id
                        } else {
                            kept += m.copy(
                                state = MemoState.AFTERGLOW,
                                afterglowAt = expireAt,
                                updatedAt = effectiveNow,
                            )
                        }
                    } else {
                        kept += m
                    }
                }
                MemoState.AFTERGLOW -> {
                    val glowStart = m.afterglowAt ?: m.createdAt + EXPIRE_DAYS * DAY_MS
                    if (effectiveNow >= glowStart + AFTERGLOW_DAYS * DAY_MS) {
                        deleted += m.id
                    } else {
                        kept += m
                    }
                }
                else -> kept += m
            }
        }
        return EvalResult(kept, deleted)
    }

    /** 既定の日付オフセット（日本時間）。端末のタイムゾーンが分かる呼び出し側は実オフセットを渡す。 */
    const val JST_OFFSET_MS = 9 * HOUR_MS

    /**
     * その土地の日付番号。v0.3.2〜：UTC基準だと日本では朝9時に日が切り替わってしまうため、
     * タイムゾーンのオフセットを加えてから日数に割る。
     */
    fun epochDay(ms: Long, offsetMs: Long = JST_OFFSET_MS): Long = (ms + offsetMs) / DAY_MS

    /**
     * 今日の再会：未消化チェックポイントがある FLOWING を優先順位つきで最大5件。
     * v0.3〜：今日すでに再会したメモは出さない（間隔の保持）。
     */
    fun resurfaceCandidates(memos: List<Memo>, now: Long, dayOffsetMs: Long = JST_OFFSET_MS): List<Memo> =
        memos.filter {
            it.state == MemoState.FLOWING &&
                dueCheckpointCount(it, now) > it.checkpointsDone &&
                it.lastReunionEpochDay < epochDay(now, dayOffsetMs)
        }
            .sortedWith(
                compareBy<Memo> { remainingDays(it, now) }
                    .thenByDescending { now - it.createdAt }
            )
            .take(MAX_RESURFACE)

    /**
     * 再会の消化（v0.3〜）：チェックポイントは1回に1つだけ進める。
     * 不定期に開いても間隔が潰れず、翌日以降に残りが順に出る。
     * 消化後の checkpointsDone がそのまま「N回目の再会」の N になる（表示と一致）。
     */
    fun consumeCheckpoint(memo: Memo, now: Long, dayOffsetMs: Long = JST_OFFSET_MS): Memo {
        val due = dueCheckpointCount(memo, now)
        if (due <= memo.checkpointsDone) return memo
        return memo.copy(
            checkpointsDone = memo.checkpointsDone + 1,
            reunions = memo.reunions + 1,
            lastReunionEpochDay = epochDay(now, dayOffsetMs),
            updatedAt = now,
        )
    }

    /** 「暗記する」：定着モードへ。次回出題は翌日。 */
    fun startLearning(memo: Memo, now: Long): Memo =
        memo.copy(
            state = MemoState.LEARNING,
            stage = 0,
            nextDueAt = now + STAGE_INTERVAL_DAYS[0] * DAY_MS,
            updatedAt = now,
        )

    /** 定着をやめる：FLOWING に戻す（30日タイマーは作成日基準のまま）。 */
    fun stopLearning(memo: Memo, now: Long): Memo =
        memo.copy(state = MemoState.FLOWING, stage = 0, nextDueAt = null, updatedAt = now)

    fun dueLearning(memos: List<Memo>, now: Long): List<Memo> =
        memos.filter { it.state == MemoState.LEARNING && (it.nextDueAt ?: Long.MAX_VALUE) <= now }
            .sortedBy { it.nextDueAt ?: Long.MAX_VALUE }

    /** 「覚えてた」：ステージ前進。最終ステージ（60日）で覚えていたら卒業。 */
    fun answerRemembered(memo: Memo, now: Long): Memo =
        if (memo.stage >= STAGE_INTERVAL_DAYS.lastIndex) {
            memo.copy(state = MemoState.MASTERED, nextDueAt = null, updatedAt = now)
        } else {
            val nextStage = memo.stage + 1
            memo.copy(
                stage = nextStage,
                nextDueAt = now + STAGE_INTERVAL_DAYS[nextStage] * DAY_MS,
                updatedAt = now,
            )
        }

    /** 「あやしい」：ステージを1つ戻し、翌日に再出題。 */
    fun answerUnsure(memo: Memo, now: Long): Memo =
        memo.copy(
            stage = maxOf(0, memo.stage - 1),
            nextDueAt = now + DAY_MS,
            updatedAt = now,
        )

    /** 余韻からの復活：残り最低3日を保証するため作成日時を27日前へ補正する（仕様 §6.3）。 */
    fun revive(memo: Memo, now: Long): Memo =
        memo.copy(
            state = MemoState.FLOWING,
            createdAt = now - (EXPIRE_DAYS - 3) * DAY_MS,
            afterglowAt = null,
            checkpointsDone = CHECKPOINT_OFFSETS_MS.size,
            updatedAt = now,
        )

    fun afterglowRemainingDays(memo: Memo, now: Long): Int {
        val start = memo.afterglowAt ?: return AFTERGLOW_DAYS
        val elapsed = ((now - start) / DAY_MS).toInt().coerceAtLeast(0)
        return (AFTERGLOW_DAYS - elapsed).coerceAtLeast(0)
    }
}
