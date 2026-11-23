package app.uroboe

/** 画面検証用の固定データ。時刻は NOW を基準に相対で作る（実行日に依存しない）。 */
object TestFixtures {
    /** 固定の「現在」: 2027-01-15T00:00:00Z 相当の任意の固定点 */
    const val NOW: Long = 1_800_000_000_000L

    private const val HOUR = 3_600_000L
    private const val DAY = 24 * HOUR

    fun willDoMemo() = Memo(
        id = 1L,
        body = "Devinの契約をどうするか決める",
        createdAt = NOW - 2 * DAY,
        lastReviewedAt = NOW - 2 * DAY,
        enteredActiveAt = NOW - 2 * DAY,
        reviewStage = 1,
        willDoAt = NOW - HOUR,
    )

    fun dueMemo() = Memo(
        id = 2L,
        body = "確定申告の領収書をまとめる",
        createdAt = NOW - 2 * DAY,
        lastReviewedAt = NOW - 2 * DAY,
        enteredActiveAt = NOW - 2 * DAY,
        reviewStage = 0,
    )

    fun freshMemo() = Memo(
        id = 3L,
        body = "新しいメモのかたちを考える",
        createdAt = NOW - 5 * 60_000L,
        lastReviewedAt = NOW - 5 * 60_000L,
        enteredActiveAt = NOW - 5 * 60_000L,
        reviewStage = 0,
    )

    fun waitingMemo() = Memo(
        id = 4L,
        body = "読みかけの本の続きを読む\n第3章から。付箋のところ。",
        createdAt = NOW - 3 * HOUR,
        lastReviewedAt = NOW - 3 * HOUR,
        enteredActiveAt = NOW - 3 * HOUR,
        reviewStage = 2,
    )

    fun somedayMemo() = Memo(
        id = 5L,
        body = "いつか行きたい温泉リストを作る",
        createdAt = NOW - 10 * DAY,
        lastReviewedAt = NOW - 10 * DAY,
        lowPriority = true,
        lowPriorityAt = NOW - DAY,
    )

    fun sampleMemos() = listOf(willDoMemo(), dueMemo(), freshMemo(), waitingMemo(), somedayMemo())
}
