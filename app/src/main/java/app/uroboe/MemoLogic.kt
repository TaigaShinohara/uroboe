package app.uroboe

/**
 * メモのデータモデル。
 * メモは自動消滅せず、通常は上段（「そろそろやろう」・忘却曲線順）、
 * 優先度を下げたものだけが下段（「いつかやればいい」）に並ぶ。
 */
data class Memo(
    val id: Long,
    val body: String,
    val question: String? = null,
    val answer: String? = null,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    /** true のとき「いつかやればいい」（下段）に配置する。 */
    val lowPriority: Boolean = false,
    /** 下段へ下げた時刻（epoch ms）。下段の並び順に使う。 */
    val lowPriorityAt: Long? = null,
    /** 忘却曲線の現在ステージ（0..6：1時間→1日→3日→7日→14日→30日→60日）。 */
    val reviewStage: Int = 0,
    /** 最後に確認（または作成／上段復帰）した時刻（epoch ms）。 */
    val lastReviewedAt: Long = createdAt,
    /** 節目を越えて確認した累計回数。 */
    val reunions: Int = 0,
) {
    val isQuiz: Boolean get() = !question.isNullOrBlank()
    /** 一覧で見せる題名（質問か本文の最初の空でない行）。 */
    val title: String get() = (question ?: body).lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    /** 表示本文（クイズ形式なら答え、通常は本文）。 */
    val displayBody: String get() = if (isQuiz) (answer ?: "") else body
}

/**
 * 純粋ロジック（Android非依存・単体テスト対象）。
 * 時刻はすべて epoch ミリ秒で受け取る。
 */
object MemoLogic {
    const val MINUTE_MS = 60_000L
    const val HOUR_MS = 3_600_000L
    const val DAY_MS = 86_400_000L

    /** 新規作成・上段復帰直後に最上部へ留める猶予時間（15分）。 */
    const val FRESH_WINDOW_MS = 15 * MINUTE_MS

    /** 忘却曲線に沿った再浮上インターバル（1時間・1日・3日・7日・14日・30日・60日）。 */
    val FORGETTING_INTERVALS_MS = listOf(
        HOUR_MS,
        DAY_MS,
        3 * DAY_MS,
        7 * DAY_MS,
        14 * DAY_MS,
        30 * DAY_MS,
        60 * DAY_MS,
    )

    fun currentIntervalMs(memo: Memo): Long =
        FORGETTING_INTERVALS_MS[memo.reviewStage.coerceIn(0, FORGETTING_INTERVALS_MS.lastIndex)]

    /**
     * 忘却度比率（t / S）。エビングハウス保持率 R = exp(-t/S) と単調に対応し、
     * 値が大きいほど忘れかけている。1.0 以上で次の節目へ到達。
     */
    fun forgettingRatio(memo: Memo, now: Long): Double {
        val elapsed = (now - memo.lastReviewedAt).coerceAtLeast(0L).toDouble()
        return elapsed / currentIntervalMs(memo).toDouble()
    }

    /** 忘却曲線の節目に到達し、「忘れかけ」として上へ浮かぶべき状態か。 */
    fun isDueForReview(memo: Memo, now: Long): Boolean =
        !memo.lowPriority && forgettingRatio(memo, now) >= 1.0

    /** 作成・上段復帰直後の猶予時間内か。 */
    fun isFresh(memo: Memo, now: Long): Boolean {
        if (memo.lowPriority || memo.reviewStage != 0) return false
        val elapsed = (now - memo.lastReviewedAt).coerceAtLeast(0L)
        return elapsed < FRESH_WINDOW_MS
    }

    /**
     * 上段（「そろそろやろう」）の並び順：
     * 1. 作成・復帰直後（15分以内・ステージ0）：すぐ確認できるよう最上部（新しい順）
     * 2. 忘却曲線の節目に到達済み（forgettingRatio >= 1.0）：忘れかけ度が高い順
     * 3. 次の節目待ち：節目に近い順（forgettingRatio 降順）
     */
    fun sortActiveByForgettingCurve(memos: List<Memo>, now: Long): List<Memo> =
        memos.filter { !it.lowPriority }
            .sortedWith { a, b ->
                val tierA = tierOf(a, now)
                val tierB = tierOf(b, now)
                if (tierA != tierB) return@sortedWith tierA.compareTo(tierB)
                when (tierA) {
                    0 -> {
                        val c = b.lastReviewedAt.compareTo(a.lastReviewedAt)
                        if (c != 0) c else b.id.compareTo(a.id)
                    }
                    1 -> {
                        val c = forgettingRatio(b, now).compareTo(forgettingRatio(a, now))
                        if (c != 0) c else a.createdAt.compareTo(b.createdAt)
                    }
                    else -> {
                        val c = forgettingRatio(b, now).compareTo(forgettingRatio(a, now))
                        if (c != 0) c else b.createdAt.compareTo(a.createdAt)
                    }
                }
            }

    private fun tierOf(memo: Memo, now: Long): Int = when {
        isFresh(memo, now) -> 0
        isDueForReview(memo, now) -> 1
        else -> 2
    }

    /** 下段（「いつかやればいい」）の並び順：新しく下げた順。 */
    fun sortSomeday(memos: List<Memo>): List<Memo> =
        memos.filter { it.lowPriority }
            .sortedWith(
                compareByDescending<Memo> { it.lowPriorityAt ?: it.updatedAt }
                    .thenByDescending { it.id }
            )

    /**
     * 詳細を開いたときの更新（思い出した扱い）。
     * 忘却曲線の節目に到達済みなら次のインターバルへ進み、未到達（かつ直近15分以降）なら現在ステージのまま計測起点を更新する。
     */
    fun markReviewed(memo: Memo, now: Long): Memo {
        if (memo.lowPriority) return memo
        val elapsed = (now - memo.lastReviewedAt).coerceAtLeast(0L)
        if (memo.reviewStage == 0 && elapsed < FRESH_WINDOW_MS) {
            return memo
        }
        return if (forgettingRatio(memo, now) >= 1.0) {
            memo.copy(
                reviewStage = (memo.reviewStage + 1).coerceAtMost(FORGETTING_INTERVALS_MS.lastIndex),
                lastReviewedAt = now,
                reunions = memo.reunions + 1,
                updatedAt = now,
            )
        } else {
            memo.copy(
                lastReviewedAt = now,
                updatedAt = now,
            )
        }
    }

    /** スワイプ等で「いつかやればいい（下段）」へ下げる。 */
    fun moveToSomeday(memo: Memo, now: Long): Memo =
        memo.copy(
            lowPriority = true,
            lowPriorityAt = now,
            updatedAt = now,
        )

    /** 下段から「そろそろやろう（上段）」へ戻す。最上部に現れるようステージと計測起点を初期化する。 */
    fun moveToActive(memo: Memo, now: Long): Memo =
        memo.copy(
            lowPriority = false,
            lowPriorityAt = null,
            reviewStage = 0,
            lastReviewedAt = now,
            updatedAt = now,
        )

    fun elapsedLabel(fromMs: Long, now: Long): String {
        val diff = (now - fromMs).coerceAtLeast(0L)
        return when {
            diff < MINUTE_MS -> "たった今"
            diff < HOUR_MS -> "${diff / MINUTE_MS}分前"
            diff < DAY_MS -> "${diff / HOUR_MS}時間前"
            else -> "${diff / DAY_MS}日前"
        }
    }
}
