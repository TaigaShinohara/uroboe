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
    /** 最後に「またあとで」（または作成／上段復帰）した時刻（epoch ms）。 */
    val lastReviewedAt: Long = createdAt,
    /** 新規作成または上段復帰で上段に入った時刻。直後15分の最上部維持に使う（0以下で無効）。 */
    val enteredActiveAt: Long = createdAt,
    /** 節目を越えて「またあとで」を送った累計回数。 */
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

    /**
     * Tier 1（節目到達済み）ソート時の忘却度比率の上限。
     * 2.0（インターバルの2倍以上放置）で頭打ちにし、それ以上は最後に確認してからの
     * 経過が長い（lastReviewedAt が古い）メモを優先することで、短周期メモが長期メモを
     * 押し下げ続ける歪みを防ぐ。
     */
    const val MAX_SORT_RATIO = 2.0

    /** 上段で「忘れかけ」ピンクピルを付ける最大件数（画面のピンク氾濫を防ぐ）。 */
    const val MAX_DUE_HIGHLIGHTS = 3

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

    /** 作成・上段復帰直後の猶予時間内か（明示的な「またあとで」後は false）。 */
    fun isFresh(memo: Memo, now: Long): Boolean {
        if (memo.lowPriority || memo.reviewStage != 0 || memo.enteredActiveAt <= 0L) return false
        if (memo.lastReviewedAt != memo.enteredActiveAt) return false
        val elapsed = (now - memo.enteredActiveAt).coerceAtLeast(0L)
        return elapsed < FRESH_WINDOW_MS
    }

    /**
     * 上段（「そろそろやろう」）の並び順：
     * 1. 作成・復帰直後（15分以内・未送り）：すぐ確認できるよう最上部（新しい順）
     * 2. 忘却曲線の節目に到達済み（forgettingRatio >= 1.0）：
     *    上限2.0で頭打ちした忘却度比率の降順、同率時は lastReviewedAt が古い順
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
                        val c = b.enteredActiveAt.compareTo(a.enteredActiveAt)
                        if (c != 0) c else b.id.compareTo(a.id)
                    }
                    1 -> {
                        val ratioA = forgettingRatio(a, now).coerceAtMost(MAX_SORT_RATIO)
                        val ratioB = forgettingRatio(b, now).coerceAtMost(MAX_SORT_RATIO)
                        val c = ratioB.compareTo(ratioA)
                        if (c != 0) c else {
                            val r = a.lastReviewedAt.compareTo(b.lastReviewedAt)
                            if (r != 0) r else a.createdAt.compareTo(b.createdAt)
                        }
                    }
                    else -> {
                        val c = forgettingRatio(b, now).compareTo(forgettingRatio(a, now))
                        if (c != 0) c else b.createdAt.compareTo(a.createdAt)
                    }
                }
            }

    /** 上段メモのうち「忘れかけ」ピルを表示すべきメモID集合（最上位 MAX_DUE_HIGHLIGHTS 件まで）。 */
    fun highlightedDueIds(sortedActiveMemos: List<Memo>, now: Long): Set<Long> =
        sortedActiveMemos
            .asSequence()
            .filter { isDueForReview(it, now) }
            .take(MAX_DUE_HIGHLIGHTS)
            .map { it.id }
            .toSet()

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
     * 明示的な「またあとで」操作（上段右スワイプまたは編集画面の［またあとで］）。
     * 閲覧・編集だけでは呼ばれない。
     * - 節目到達済みなら次のインターバルへ進み、reunions を加算する。
     * - 節目未到達なら現在ステージのまま計測起点を now に更新し、Fresh 猶予は解除する。
     */
    fun deferToNextStage(memo: Memo, now: Long): Memo {
        if (memo.lowPriority) return memo
        return if (forgettingRatio(memo, now) >= 1.0) {
            memo.copy(
                reviewStage = (memo.reviewStage + 1).coerceAtMost(FORGETTING_INTERVALS_MS.lastIndex),
                lastReviewedAt = now,
                enteredActiveAt = 0L,
                reunions = memo.reunions + 1,
                updatedAt = now,
            )
        } else {
            memo.copy(
                lastReviewedAt = now,
                enteredActiveAt = 0L,
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
            enteredActiveAt = now,
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
