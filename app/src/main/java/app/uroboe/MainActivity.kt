package app.uroboe

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** アート方針（docs/ART_DIRECTION.md）の色。強い色はピンク1点だけ。 */
object Palette {
    val Paper = Color(0xFFFFFBF3)
    val Ink = Color(0xFF2B2620)
    val InkSoft = Color(0xFF6F6659)
    val Line = Color(0xFFE9DFCF)
    val CardWhite = Color(0xFFFFFFFF)
    val Pink = Color(0xFFC2185B)
    val PinkBg = Color(0xFFFCE7F1)
    val Orange = Color(0xFFE65100)
    val OrangeBg = Color(0xFFFFEDD9)
    val Purple = Color(0xFF6A1B9A)
    val PurpleBg = Color(0xFFF1E6F7)
}

private fun fmtDate(ms: Long): String =
    SimpleDateFormat("M月d日", Locale.JAPAN).format(Date(ms))

private fun fmtDateFull(ms: Long): String =
    SimpleDateFormat("yyyy年M月d日", Locale.JAPAN).format(Date(ms))

/** 目のアイコン（v0.3.1〜「見た」ボタン用）。線と瞳だけで描く。 */
@Composable
private fun EyeIcon(color: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.04f, h * 0.5f)
            quadraticBezierTo(w * 0.5f, h * -0.18f, w * 0.96f, h * 0.5f)
            quadraticBezierTo(w * 0.5f, h * 1.18f, w * 0.04f, h * 0.5f)
            close()
        }
        drawPath(path, color = color, style = Stroke(width = w * 0.07f))
        drawCircle(color = color, radius = w * 0.13f, center = center)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // v0.3.3：明るい背景でもステータスバーの時刻・電池が読めるよう、アイコンは濃色で固定する
        // （既定だと白アイコンになり、クリーム色の背景に溶けて読めなかった）
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = androidx.activity.SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT,
            ),
        )
        val store = MemoStore(applicationContext)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Palette.Pink,
                    background = Palette.Paper,
                    surface = Palette.CardWhite,
                    onBackground = Palette.Ink,
                    onSurface = Palette.Ink,
                    onPrimary = Color.White,
                )
            ) {
                UroboeApp(store)
            }
        }
    }
}

@Composable
fun UroboeApp(store: MemoStore) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("uroboe_prefs", Context.MODE_PRIVATE) }
    val data by store.data.collectAsState()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    // v0.3.6（遷移レビュー）：画面回転などの再生成でも、開いている画面が消えないよう保持する
    var writeOpen by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmDeleteId by remember { mutableStateOf<Long?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showOnboarding by remember { mutableStateOf(!prefs.getBoolean("onboarded", false)) }
    // v0.3 B4：保存・暗記の直後に小さな一言フィードバックを出す
    var notice by remember { mutableStateOf<String?>(null) }
    // v0.3.2 修正：同じ文言が続くとタイマーが再起動せず一瞬で消えたため、連番で必ず再表示する
    var noticeKey by remember { mutableStateOf(0) }
    fun showNotice(text: String) {
        notice = text
        noticeKey++
    }
    LaunchedEffect(noticeKey) {
        if (notice != null) {
            delay(2800)
            notice = null
        }
    }

    // v0.3.2 修正：バックグラウンドから戻った直後に評価と表示を最新化する
    // （従来は60秒タイマーを待つ間、前日の再会が残って見えた）
    val activity = LocalContext.current as? ComponentActivity
    DisposableEffect(activity) {
        if (activity == null) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                store.refresh()
                now = System.currentTimeMillis()
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            store.refresh()
            now = System.currentTimeMillis()
        }
    }

    if (showOnboarding) {
        OnboardingScreen(
            onDone = {
                prefs.edit().putBoolean("onboarded", true).apply()
                showOnboarding = false
            },
            // v0.3.6（遷移レビュー）：「使い方」からの再表示では、戻る操作でホームへ帰れるようにする
            canExitToHome = prefs.getBoolean("onboarded", false),
            onExit = { showOnboarding = false },
            // v0.3.10：選んだタイプを保存しておく（将来の出し分け用）
            onSelectType = { prefs.edit().putInt("user_type", it).apply() },
        )
        return
    }

    // v0.3.1：新規メモはダイアログでなく全画面で書く（本人の指示）
    if (writeOpen) {
        WriteScreen(
            onDismiss = { writeOpen = false },
            onSave = { body, q, a ->
                store.addMemo(body, q, a)
                writeOpen = false
                showNotice("メモしました。忘れかけた頃、また出会えます")
            },
        )
        return
    }

    // v0.3.9（本人の指示）：メモを開くときはダイアログでなく、書く画面と同じ全画面で開く
    val detailMemo = detailId?.let { id -> data.memos.firstOrNull { it.id == id } }
    if (detailMemo != null) {
        EditScreen(
            memo = detailMemo, now = now,
            onBack = { detailId = null },
            onEdit = { body, q, a -> store.editBody(detailMemo.id, body, q, a) },
            onMemorize = {
                store.update({ MemoLogic.startLearning(it, System.currentTimeMillis()) }, detailMemo.id)
                detailId = null
                showNotice("残しました。明日、定着チェックで出会えます")
            },
            onDeleteNow = {
                detailId = null
                confirmDeleteId = detailMemo.id
            },
        )
        return
    }

    Scaffold(
        containerColor = Palette.Paper,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { writeOpen = true },
                // v0.3.3 デザイン整備：形の言語を「ボタンは全丸」に合わせ、FABも円形にする
                shape = CircleShape,
                containerColor = Palette.Pink,
                contentColor = Color.White,
            ) { Text("＋", fontSize = 24.sp) }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            SingleScreen(
                data = data, now = now, store = store,
                onOpenDetail = { detailId = it },
                onConfirmDelete = { confirmDeleteId = it },
                onShowHelp = { showOnboarding = true },
                onWipe = { confirmWipe = true },
                onAbout = { showAbout = true },
                // v0.3.4（見直しY-2）：控えの書き出しは共有シートへ（端末内完結の範囲内）
                onExport = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "うろぼえ データの控え")
                        putExtra(Intent.EXTRA_TEXT, store.exportJson())
                    }
                    context.startActivity(Intent.createChooser(send, "データの控えを送る"))
                },
                onNotice = { showNotice(it) },
            )
            notice?.let { text ->
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = Palette.PinkBg,
                    shadowElevation = 2.dp,
                ) {
                    Text(
                        text, color = Palette.Pink, fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }

    confirmDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDeleteId = null },
            containerColor = Palette.CardWhite,
            title = { Text("このメモを消しますか？") },
            text = { Text("余韻を経ずに、すぐに完全に消えます。元に戻せません。") },
            confirmButton = {
                TextButton(onClick = { store.deleteNow(id); confirmDeleteId = null }) {
                    Text("消す", color = Palette.Orange)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteId = null }) { Text("やめる") }
            },
        )
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            containerColor = Palette.CardWhite,
            title = { Text("データをすべて消しますか？") },
            text = { Text("すべてのメモと記録が完全に消えます。元に戻せません。") },
            confirmButton = {
                TextButton(onClick = { store.deleteAll(); confirmWipe = false }) {
                    Text("すべて消す", color = Palette.Orange)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) { Text("やめる") }
            },
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            containerColor = Palette.CardWhite,
            title = { Text("うろぼえ について") },
            text = {
                Text("メモを不要にするメモ帳。\n覚えたいものだけ残る、消えていくメモアプリ。\nバージョン 0.3.10\n\n期限を切るほどではない「いつかやろう」のためのメモ帳。忘れかけた頃に顔を出して、「いつか」を「そろそろ」に変えていきます。\n\n書いたメモは忘れかけた頃にまた顔を出し、「残す」を押したものだけが残ります。押さなかったものは30日で消え、7日間だけ「余韻」から戻せます。データはこの端末の中だけにあります。\n\nアプリを削除（アンインストール）すると、メモもすべて消えます。大事なメモは、画面いちばん下の「データを書き出す」から控えを取っておけます。")
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("閉じる") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String, color: Color = Palette.Pink, sub: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
        if (sub != null) Text(sub, color = Palette.InkSoft, fontSize = 12.sp)
    }
}

@Composable
private fun RemainingBar(remaining: Int) {
    LinearProgressIndicator(
        progress = { remaining.coerceIn(0, 30) / 30f },
        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
        color = Palette.Pink,
        trackColor = Palette.Line,
    )
}

@Composable
private fun MemoCardShell(
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Palette.CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(13.dp)) { content() }
    }
}

// ---------------- 単一画面（v0.2〜：タブ廃止、1スクロールに全部） ----------------
// 並びは上から「今日やること → 流れるもの → 静かなもの」。
// 空のセクションは見出しごと出さない。

@Composable
fun SingleScreen(
    data: StoreData,
    now: Long,
    store: MemoStore,
    onOpenDetail: (Long) -> Unit,
    onConfirmDelete: (Long) -> Unit,
    onShowHelp: () -> Unit,
    onWipe: () -> Unit,
    onAbout: () -> Unit,
    onExport: () -> Unit,
    onNotice: (String) -> Unit,
) {
    // v0.3.2：日付は端末タイムゾーンのオフセットで数える（UTC基準の朝9時切替を防ぐ）
    val dayOffset = java.util.TimeZone.getDefault().getOffset(now).toLong()
    val today = MemoLogic.epochDay(now, dayOffset)
    val candidates = remember(data.memos, now) { MemoLogic.resurfaceCandidates(data.memos, now, dayOffset) }
    var sessionIds by remember { mutableStateOf(emptySet<Long>()) }
    var sessionDay by remember { mutableStateOf(Long.MIN_VALUE) }
    // v0.3.2 修正：セッション表示は「今日」だけに効かせる（前日の再会が翌日も残る不具合）
    val effectiveSessionIds = if (sessionDay == today) sessionIds else emptySet()
    LaunchedEffect(candidates.map { it.id }, today) {
        if (sessionDay != today) {
            sessionDay = today
            sessionIds = emptySet()
        }
        val newIds = candidates.map { it.id }.filterNot { it in sessionIds }
        if (newIds.isNotEmpty()) {
            sessionIds = sessionIds + newIds
            store.consumeCheckpoints(newIds)
        }
    }
    val candidateIds = candidates.map { it.id }.toSet()
    val resurface = data.memos.filter {
        it.state == MemoState.FLOWING && (it.id in effectiveSessionIds || it.id in candidateIds)
    }.sortedBy { MemoLogic.remainingDays(it, now) }
    val resurfaceIds = resurface.map { it.id }.toSet()

    // v0.3 A1：再会に出ているメモは「流れるメモ」から外す（同一メモの二重表示をなくす）。
    // 「そろそろ消える」は独立セクションをやめ、流れるメモ内の強調カードへ統合した。
    val flowingAll = data.memos.filter { it.state == MemoState.FLOWING }
    val flowing = flowingAll.filter { it.id !in resurfaceIds }
        .sortedByDescending { it.createdAt }
    val due = remember(data.memos, now) { MemoLogic.dueLearning(data.memos, now) }
    val learning = data.memos.filter { it.state == MemoState.LEARNING }
    val currentCheck = due.firstOrNull()
    val mastered = data.memos.filter { it.state == MemoState.MASTERED }
        .sortedByDescending { it.updatedAt }
    val glow = data.memos.filter { it.state == MemoState.AFTERGLOW }
        .sortedByDescending { it.afterglowAt ?: 0L }

    val cal = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val monthStart = cal.timeInMillis
    val masteredThisMonth = data.masteredEvents.count { it >= monthStart }
    val deletedThisMonth = data.deletedEvents.count { it >= monthStart }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 90.dp),
    ) {
        // ヘッダー
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("うろぼえ", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Palette.Ink)
                    Text(
                        SimpleDateFormat("M月d日（E）", Locale.JAPAN).format(Date(now)),
                        color = Palette.InkSoft, fontSize = 13.sp,
                    )
                }
                TextButton(onClick = onShowHelp) { Text("使い方", color = Palette.InkSoft) }
            }
        }

        // 今日の再会
        if (resurface.isNotEmpty()) {
            item { SectionTitle("今日の再会", sub = "忘れかけの頃に ${resurface.size}件") }
            items(resurface, key = { "re_${it.id}" }) { memo ->
                val remaining = MemoLogic.remainingDays(memo, now)
                // v0.3 A2：回数は消化後の checkpointsDone そのもの（消化=1回に1つ進む）。
                val serveNo = memo.checkpointsDone.coerceAtLeast(1)
                MemoCardShell(onClick = { onOpenDetail(memo.id) }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "${MemoLogic.ageDays(memo, now)}日前に書いた ・ ${serveNo}回目の再会",
                            fontSize = 11.5.sp, color = Palette.InkSoft,
                        )
                        Text(
                            "残り${remaining}日", fontSize = 11.5.sp,
                            color = if (remaining <= 2) Palette.Orange else Palette.InkSoft,
                            fontWeight = if (remaining <= 2) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Column(Modifier.alpha(MemoLogic.opacityFor(MemoLogic.ageDays(memo, now)))) {
                        Text(memo.title, fontWeight = FontWeight.Bold, fontSize = 15.5.sp, color = Palette.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        // v0.3 B5：クイズ形式の再会では答えを出さない（思い出す練習を守る）
                        if (!memo.isQuiz && memo.displayBody.isNotBlank() && memo.displayBody != memo.title) {
                            // v0.3.2：長い本文でカードが巨大化しないよう行数を絞る（全文は詳細で読める）
                            Text(memo.displayBody, fontSize = 13.sp, color = Palette.InkSoft, maxLines = 6, overflow = TextOverflow.Ellipsis)
                        }
                        if (memo.isQuiz) {
                            Text("クイズ形式 ・ 答えはタップで開いて確認", fontSize = 11.5.sp, color = Palette.InkSoft)
                        }
                    }
                    // v0.3.4（見直しY-4）：［見た］と［流す］は完全に同一動作だったため［見た］に統合。
                    // 再会の場のボタンは1つだけにし、残す判断は詳細側に置く（v0.3.1の方針は維持）。
                    Button(
                        onClick = { sessionIds = sessionIds - memo.id },
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.Pink),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
                    ) {
                        EyeIcon(Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("見た", fontSize = 13.5.sp)
                    }
                    Spacer(Modifier.height(9.dp))
                    RemainingBar(remaining)
                }
            }
        }

        // 定着チェック（到来分があるときだけ）
        if (currentCheck != null) {
            item { SectionTitle("定着チェック", color = Palette.Purple, sub = "あと ${due.size}件") }
            item {
                LearningCard(
                    memo = currentCheck,
                    onAnswer = { remembered -> store.answerLearning(currentCheck.id, remembered) },
                )
            }
        }

        // 流れるメモ（残り2日以内は「そろそろ消える」を統合した強調カードになる）
        item { SectionTitle("流れるメモ", color = Palette.InkSoft, sub = "新しい順") }
        if (flowingAll.isEmpty()) {
            item {
                // v0.3.5（本人の指摘「文字ばかりで見づらい」）：空状態は文章でなく、
                // 薄れモチーフ（オンボーディングと共通の図形）＋短い2行で見せる。
                Column(
                    Modifier.fillMaxWidth().padding(top = 64.dp, bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        listOf(1.0f to 150, 0.55f to 116, 0.28f to 82).forEach { (a, w) ->
                            Box(
                                Modifier.width(w.dp).height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Palette.Ink.copy(alpha = a * 0.45f))
                            )
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Text("まだ何も流れていません", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                    Spacer(Modifier.height(5.dp))
                    Text("右下の＋から、「いつかやろう」を1行書いてみて", fontSize = 13.5.sp, color = Palette.InkSoft)
                }
            }
        } else {
            items(flowing, key = { "fl_${it.id}" }) { memo ->
                val remaining = MemoLogic.remainingDays(memo, now)
                val urgent = remaining <= 2
                MemoCardShell(onClick = { onOpenDetail(memo.id) }) {
                    // v0.3 A1/M-1：救う場面のカードは薄れさせない（濃さ100%）
                    Column(Modifier.alpha(if (urgent) 1f else MemoLogic.opacityFor(MemoLogic.ageDays(memo, now)))) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${MemoLogic.ageDays(memo, now)}日前", fontSize = 11.5.sp, color = Palette.InkSoft)
                            Text(
                                if (urgent) "あと${remaining}日で消える" else "残り${remaining}日",
                                fontSize = 11.5.sp,
                                color = if (urgent) Palette.Orange else Palette.InkSoft,
                                fontWeight = if (urgent) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(memo.title, fontSize = 15.sp, color = Palette.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (urgent) {
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    store.update({ MemoLogic.startLearning(it, System.currentTimeMillis()) }, memo.id)
                                    onNotice("残しました。明日、定着チェックで出会えます")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Palette.Pink),
                                shape = RoundedCornerShape(999.dp),
                            ) { Text("残す", fontSize = 13.5.sp) }
                            OutlinedButton(
                                onClick = { onConfirmDelete(memo.id) },
                                shape = RoundedCornerShape(999.dp),
                            ) { Text("このまま消す", fontSize = 13.5.sp, color = Palette.InkSoft) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    RemainingBar(remaining)
                }
            }
        }

        // 定着中（静かな1行リスト）
        if (learning.isNotEmpty()) {
            item { SectionTitle("定着中", color = Palette.Purple, sub = "${learning.size}件") }
            items(learning.sortedBy { it.nextDueAt ?: Long.MAX_VALUE }, key = { "ln_${it.id}" }) { memo ->
                Row(
                    // v0.3 B2：行タップで詳細（本文確認・編集）を開ける
                    Modifier.fillMaxWidth().clickable { onOpenDetail(memo.id) }.padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(memo.title, fontSize = 14.sp, color = Palette.Ink, modifier = Modifier.weight(1f))
                    Text(
                        "次は ${memo.nextDueAt?.let { fmtDate(it) } ?: "-"}",
                        fontSize = 11.5.sp, color = Palette.InkSoft,
                    )
                    TextButton(onClick = {
                        store.update({ MemoLogic.stopLearning(it, System.currentTimeMillis()) }, memo.id)
                        store.refresh()
                    }) { Text("やめる", fontSize = 12.sp, color = Palette.InkSoft) }
                }
            }
        }

        // 余韻（消えたメモ）
        if (glow.isNotEmpty()) {
            item { SectionTitle("余韻", color = Palette.InkSoft, sub = "消えたメモ ・ 7日間だけ戻せる") }
            items(glow, key = { "gl_${it.id}" }) { memo ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(memo.title, fontSize = 14.sp, color = Palette.InkSoft, modifier = Modifier.weight(1f))
                    Text("あと${MemoLogic.afterglowRemainingDays(memo, now)}日", fontSize = 11.5.sp, color = Palette.InkSoft)
                    TextButton(onClick = { store.update({ MemoLogic.revive(it, System.currentTimeMillis()) }, memo.id) }) {
                        Text("復活", fontSize = 12.5.sp, color = Palette.Pink)
                    }
                }
            }
        }

        // 身についたメモ＋今月の成果（数字とタイトルだけ、静かに）
        // v0.3 B6：卒業0件のときは見出しごと出さない（空セクション規則に統一）
        if (mastered.isNotEmpty()) {
            item { SectionTitle("身についたメモ", color = Palette.Purple, sub = "${mastered.size}件") }
            items(mastered.take(10), key = { "ms_${it.id}" }) { memo ->
                Text(
                    memo.title, fontSize = 14.sp, color = Palette.InkSoft,
                    // v0.3 B2：卒業メモもタップで本文を確認できる（閲覧のみ）
                    modifier = Modifier.fillMaxWidth().clickable { onOpenDetail(memo.id) }.padding(vertical = 3.dp),
                )
            }
            if (mastered.size > 10) {
                item { Text("ほか ${mastered.size - 10}件", fontSize = 12.5.sp, color = Palette.InkSoft) }
            }
        }

        // v0.3.4（見直しY-3）：今月の成果は卒業メモの有無から切り離して出す。
        // 手放すだけが進んでいる人こそ「手放した件数」の肯定が要る（仕様§1）。
        if (masteredThisMonth > 0 || deletedThisMonth > 0) {
            item {
                Text(
                    "今月：身についた ${masteredThisMonth}件 ／ 手放した ${deletedThisMonth}件 — どちらも頭が軽くなった証拠",
                    fontSize = 12.5.sp, color = Palette.InkSoft,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        // フッター（設定は小さく一番下）
        item {
            Spacer(Modifier.height(26.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
            // v0.3.5 修正：横並び3つでは「データをすべて消す」が画面外へはみ出したため、縦並びに変更
            Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                // v0.3.3 デザイン整備：「使い方」はヘッダーと重複していたためフッターからは外した
                TextButton(onClick = onAbout) { Text("このアプリについて", fontSize = 12.5.sp, color = Palette.InkSoft) }
                // v0.3.4（見直しY-2）：控えの書き出し導線をフッターに置く
                TextButton(onClick = onExport) { Text("データを書き出す", fontSize = 12.5.sp, color = Palette.InkSoft) }
                TextButton(onClick = onWipe) { Text("データをすべて消す", fontSize = 12.5.sp, color = Palette.Orange) }
            }
        }
    }
}

@Composable
private fun LearningCard(memo: Memo, onAnswer: (Boolean) -> Unit) {
    var revealed by remember(memo.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Palette.CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                MemoLogic.STAGE_INTERVAL_DAYS.forEachIndexed { i, days ->
                    val bg = when {
                        i < memo.stage -> Palette.PurpleBg
                        i == memo.stage -> Palette.Purple
                        else -> Palette.Line
                    }
                    val fg = when {
                        i < memo.stage -> Palette.Purple
                        i == memo.stage -> Color.White
                        else -> Palette.InkSoft
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(bg)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            if (i < memo.stage) "${days}日 ✓" else if (i == memo.stage) "${days}日・今ここ" else "${days}日",
                            fontSize = 10.5.sp, color = fg,
                            fontWeight = if (i == memo.stage) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (memo.isQuiz) {
                Text("ヒント", fontSize = 11.5.sp, color = Palette.InkSoft, letterSpacing = 1.sp)
                Text(
                    memo.question ?: "", fontSize = 19.sp, fontWeight = FontWeight.Bold,
                    color = Palette.Ink, modifier = Modifier.padding(vertical = 8.dp),
                )
                if (!revealed) {
                    TextButton(onClick = { revealed = true }) { Text("タップで答えを表示", color = Palette.Pink) }
                } else {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
                    Text(
                        memo.answer ?: "", fontSize = 17.sp, fontWeight = FontWeight.Bold,
                        color = Palette.Pink, modifier = Modifier.padding(top = 10.dp),
                    )
                }
            } else {
                Text(
                    memo.body, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Palette.Ink,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onAnswer(true) },
                    enabled = revealed || !memo.isQuiz,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Pink),
                    shape = RoundedCornerShape(999.dp),
                ) { Text("覚えてた") }
                OutlinedButton(
                    onClick = { onAnswer(false) },
                    enabled = revealed || !memo.isQuiz,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(999.dp),
                ) { Text("あやしい", color = Palette.Orange) }
            }
        }
    }
}

// ---------------- 書く（v0.3.1〜 全画面／v0.3.5〜 Google Keep風の枠なし） ----------------

@Composable
fun WriteScreen(onDismiss: () -> Unit, onSave: (String, String?, String?) -> Unit) {
    // v0.3.6（遷移レビュー）：下書きは再生成（回転など）でも保持する
    var body by rememberSaveable { mutableStateOf("") }
    var quiz by rememberSaveable { mutableStateOf(false) }
    var question by rememberSaveable { mutableStateOf("") }
    var answer by rememberSaveable { mutableStateOf("") }
    var confirmDiscard by remember { mutableStateOf(false) }
    // v0.3.8（安定性レビュー）：保存の連打で同じメモが二重に登録されないよう、送信は1回だけ
    var submitted by remember { mutableStateOf(false) }
    val canSave = if (quiz) question.isNotBlank() && answer.isNotBlank() else body.isNotBlank()
    // v0.3.6（遷移レビュー）：この画面はダイアログでなく画面差し替えのため、戻る操作を受け取る
    // 仕組みが無く、システムの戻るでアプリが終了して下書きも消えていた。戻る＝画面の［←］と
    // 同じ扱いにし、書いたものがあるときは破棄の確認を挟む。
    val hasDraft = body.isNotBlank() || question.isNotBlank() || answer.isNotBlank()
    fun tryDismiss() {
        if (hasDraft) confirmDiscard = true else onDismiss()
    }
    BackHandler { tryDismiss() }
    // v0.3.5：開いたらすぐ書けるよう、入力欄へ自動でフォーカスする（Keepと同じ所作）
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(quiz) { runCatching { focusRequester.requestFocus() } }
    Surface(color = Palette.Paper, modifier = Modifier.fillMaxSize()) {
        // v0.3.2 修正：全画面でもステータスバー／ナビバー／キーボードに被らないよう余白を取る
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp)
        ) {
            // v0.3.5（本人の指示・Google Keep参照）：道具は上部バーの「戻る」と「保存」だけに減らす。
            // 見出し文と枠線フィールドをやめ、画面そのものを紙として書く。
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { tryDismiss() }) { Text("←", fontSize = 20.sp, color = Palette.InkSoft) }
                TextButton(
                    onClick = {
                        if (!submitted) {
                            submitted = true
                            if (quiz) onSave("", question.trim(), answer.trim())
                            else onSave(body.trim(), null, null)
                        }
                    },
                    enabled = canSave && !submitted,
                ) {
                    Text(
                        "保存", fontSize = 15.5.sp, fontWeight = FontWeight.Bold,
                        color = if (canSave) Palette.Pink else Palette.InkSoft.copy(alpha = 0.45f),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            if (quiz) {
                BasicTextField(
                    value = question,
                    onValueChange = { question = it.take(1000) },
                    textStyle = TextStyle(fontSize = 18.sp, color = Palette.Ink, fontWeight = FontWeight.Bold, lineHeight = 27.sp),
                    cursorBrush = SolidColor(Palette.Pink),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    decorationBox = { inner ->
                        if (question.isEmpty()) {
                            Text("質問", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.InkSoft.copy(alpha = 0.55f))
                        }
                        inner()
                    },
                )
                Box(Modifier.fillMaxWidth().padding(vertical = 14.dp).height(1.dp).background(Palette.Line))
                BasicTextField(
                    value = answer,
                    onValueChange = { answer = it.take(1000) },
                    textStyle = TextStyle(fontSize = 16.5.sp, color = Palette.Ink, lineHeight = 26.sp),
                    cursorBrush = SolidColor(Palette.Pink),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    decorationBox = { inner ->
                        if (answer.isEmpty()) {
                            Text("答え", fontSize = 16.5.sp, color = Palette.InkSoft.copy(alpha = 0.55f))
                        }
                        inner()
                    },
                )
            } else {
                BasicTextField(
                    value = body,
                    onValueChange = { body = it.take(1000) },
                    textStyle = TextStyle(fontSize = 17.sp, color = Palette.Ink, lineHeight = 27.sp),
                    cursorBrush = SolidColor(Palette.Pink),
                    modifier = Modifier.fillMaxWidth().weight(1f).focusRequester(focusRequester),
                    decorationBox = { inner ->
                        if (body.isEmpty()) {
                            Text("メモ", fontSize = 17.sp, color = Palette.InkSoft.copy(alpha = 0.55f))
                        }
                        inner()
                    },
                )
                // 上限が近いときだけ残り字数を出す（無言の切捨てを防ぐ）
                if (body.length >= 900) {
                    Text(
                        "あと${1000 - body.length}字まで書けます",
                        fontSize = 11.5.sp, color = Palette.InkSoft,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            // 下部バー：クイズ切替はチップ1つに小さく置く
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp))
                        .background(if (quiz) Palette.PinkBg else Palette.CardWhite)
                        .border(1.dp, if (quiz) Palette.Pink else Palette.Line, RoundedCornerShape(999.dp))
                        .clickable { quiz = !quiz }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        if (quiz) "クイズ形式 ✓" else "クイズ形式にする",
                        fontSize = 12.5.sp,
                        color = if (quiz) Palette.Pink else Palette.InkSoft,
                        fontWeight = if (quiz) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
            Text(
                "分類はしなくてOK。このメモは明日・3日後・7日後にまた顔を出して、30日後に消えます。",
                fontSize = 11.5.sp, color = Palette.InkSoft,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            )
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            containerColor = Palette.CardWhite,
            title = { Text("書いたメモを破棄しますか？") },
            text = { Text("まだ保存されていません。このまま戻ると、書いたものは消えます。") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onDismiss() }) {
                    Text("破棄する", color = Palette.Orange)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("書き続ける") }
            },
        )
    }
}

// ---------------- メモを開く（v0.3.9〜：詳細ダイアログを廃止し、書く画面と同じ全画面に） ----------------
// 本人の指示：メモをタップしたら「メモの詳細」ダイアログでなく、作成時と同じ見た目の
// 全画面を開く。違いは下部に［残す］などの操作ボタンを固定表示すること。
// 保存ボタンはなく、入力のたびに自動保存される。開いている間は［戻す］で編集を戻せる。

@Composable
fun EditScreen(
    memo: Memo,
    now: Long,
    onBack: () -> Unit,
    onEdit: (String, String?, String?) -> Unit,
    onMemorize: () -> Unit,
    onDeleteNow: () -> Unit,
) {
    var body by remember(memo.id) { mutableStateOf(memo.body) }
    var question by remember(memo.id) { mutableStateOf(memo.question ?: "") }
    var answer by remember(memo.id) { mutableStateOf(memo.answer ?: "") }
    // v0.3.8（安定性レビュー）：［残す］の連打で処理が二重に走らないよう、送信は1回だけ
    var submitted by remember { mutableStateOf(false) }
    val editable = memo.state != MemoState.MASTERED
    // 編集の履歴（［戻す］用）。「同じ欄を続けて打つ間は1かたまり」として、
    // 欄の切り替えか1秒の間隔で区切る。戻した内容も自動保存される。
    var undoStack by remember(memo.id) { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) }
    var lastField by remember(memo.id) { mutableStateOf(-1) }
    var lastPushAt by remember(memo.id) { mutableStateOf(0L) }
    fun persist(b: String, q: String, a: String) {
        // 空の内容は保存しない（最後の有効な内容を残す）
        if (memo.isQuiz) {
            if (q.isNotBlank() && a.isNotBlank()) onEdit("", q, a)
        } else if (b.isNotBlank()) {
            onEdit(b, null, null)
        }
    }
    fun pushHistory(field: Int) {
        val t = System.currentTimeMillis()
        if (field != lastField || t - lastPushAt > 1000) {
            undoStack = (undoStack + Triple(body, question, answer)).takeLast(50)
            lastPushAt = t
            lastField = field
        }
    }
    fun undo() {
        val prev = undoStack.lastOrNull() ?: return
        undoStack = undoStack.dropLast(1)
        body = prev.first; question = prev.second; answer = prev.third
        lastField = -1
        persist(prev.first, prev.second, prev.third)
    }
    // 自動保存なので、戻る操作はそのままホームへ帰るだけでよい（失われるものはない）
    BackHandler { onBack() }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(memo.id) { if (editable) runCatching { focusRequester.requestFocus() } }
    Surface(color = Palette.Paper, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp)
        ) {
            // 上部バー：書く画面と同じ並び（左に戻る、右に［戻す］）
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("←", fontSize = 20.sp, color = Palette.InkSoft) }
                if (editable) {
                    TextButton(onClick = { undo() }, enabled = undoStack.isNotEmpty()) {
                        Text(
                            "↩ 戻す", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color = if (undoStack.isNotEmpty()) Palette.Pink else Palette.InkSoft.copy(alpha = 0.45f),
                        )
                    }
                }
            }
            // 状態の1行（作成日・残り・再会回数・状態）
            val info = buildString {
                append("作成：${fmtDateFull(memo.createdAt)}")
                if (memo.state == MemoState.FLOWING) append(" ・ 残り${MemoLogic.remainingDays(memo, now)}日")
                append(" ・ 再会 ${memo.reunions}回")
                append(" ・ 状態：")
                append(
                    when (memo.state) {
                        MemoState.FLOWING -> "流れる"
                        MemoState.LEARNING -> "定着中"
                        MemoState.MASTERED -> "身についた"
                        MemoState.AFTERGLOW -> "余韻"
                    }
                )
            }
            Text(info, fontSize = 12.sp, color = Palette.InkSoft, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
            if (!editable) {
                // 身についたメモは閲覧のみ（仕様 §6.2）。書く画面と同じ紙の見た目で見せる。
                if (memo.isQuiz) {
                    Text(memo.question ?: "", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, lineHeight = 27.sp)
                    Box(Modifier.fillMaxWidth().padding(vertical = 14.dp).height(1.dp).background(Palette.Line))
                    Text(memo.answer ?: "", fontSize = 16.5.sp, color = Palette.Ink, lineHeight = 26.sp)
                } else {
                    Text(memo.body, fontSize = 17.sp, color = Palette.Ink, lineHeight = 27.sp)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "身についたメモは、見るだけです。",
                    fontSize = 11.5.sp, color = Palette.InkSoft,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            } else if (memo.isQuiz) {
                BasicTextField(
                    value = question,
                    onValueChange = { val v = it.take(1000); pushHistory(1); question = v; persist(body, v, answer) },
                    textStyle = TextStyle(fontSize = 18.sp, color = Palette.Ink, fontWeight = FontWeight.Bold, lineHeight = 27.sp),
                    cursorBrush = SolidColor(Palette.Pink),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    decorationBox = { inner ->
                        if (question.isEmpty()) {
                            Text("質問", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.InkSoft.copy(alpha = 0.55f))
                        }
                        inner()
                    },
                )
                Box(Modifier.fillMaxWidth().padding(vertical = 14.dp).height(1.dp).background(Palette.Line))
                BasicTextField(
                    value = answer,
                    onValueChange = { val v = it.take(1000); pushHistory(2); answer = v; persist(body, question, v) },
                    textStyle = TextStyle(fontSize = 16.5.sp, color = Palette.Ink, lineHeight = 26.sp),
                    cursorBrush = SolidColor(Palette.Pink),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    decorationBox = { inner ->
                        if (answer.isEmpty()) {
                            Text("答え", fontSize = 16.5.sp, color = Palette.InkSoft.copy(alpha = 0.55f))
                        }
                        inner()
                    },
                )
            } else {
                BasicTextField(
                    value = body,
                    onValueChange = { val v = it.take(1000); pushHistory(0); body = v; persist(v, question, answer) },
                    textStyle = TextStyle(fontSize = 17.sp, color = Palette.Ink, lineHeight = 27.sp),
                    cursorBrush = SolidColor(Palette.Pink),
                    modifier = Modifier.fillMaxWidth().weight(1f).focusRequester(focusRequester),
                    decorationBox = { inner ->
                        if (body.isEmpty()) {
                            Text("メモ", fontSize = 17.sp, color = Palette.InkSoft.copy(alpha = 0.55f))
                        }
                        inner()
                    },
                )
            }
            if (editable) {
                Text(
                    "入力すると自動で保存されます。編集しても、消える日（作成から30日）は延びません。",
                    fontSize = 11.5.sp, color = Palette.InkSoft,
                    modifier = Modifier.padding(top = 8.dp),
                )
                // 下部の固定ボタン（本人の指示）：作成画面との違いはこの操作列
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (memo.state == MemoState.FLOWING) {
                        Button(
                            onClick = { if (!submitted) { submitted = true; onMemorize() } },
                            colors = ButtonDefaults.buttonColors(containerColor = Palette.Pink),
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.weight(1f),
                        ) { Text("残す", fontSize = 15.sp) }
                    }
                    OutlinedButton(
                        onClick = onDeleteNow,
                        shape = RoundedCornerShape(999.dp),
                        modifier = if (memo.state == MemoState.FLOWING) Modifier else Modifier.weight(1f),
                    ) { Text("今すぐ消す", fontSize = 13.5.sp, color = Palette.Orange) }
                }
            }
        }
    }
}

// ---------------- オンボーディング ----------------

@Composable
fun OnboardingScreen(onDone: () -> Unit, canExitToHome: Boolean = false, onExit: () -> Unit = {}, onSelectType: (Int) -> Unit = {}) {
    // v0.3.6（遷移レビュー）：ページ位置は再生成でも保持する
    var page by rememberSaveable { mutableStateOf(0) }
    // v0.3.10（本人の指示）：2枚目はタイプ選択。選んだタイプを覚えておく。
    var selectedType by rememberSaveable { mutableStateOf(-1) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    // v0.3 B3：スキップしても「30日で消える」告知は必ず1行届ける
    var confirmSkip by remember { mutableStateOf(false) }
    // v0.3.6（遷移レビュー）：戻る操作の行き先を画面遷移として正す。
    // 途中ページでは前のページへ、1枚目では「使い方」再表示ならホームへ帰り、
    // 初回ではスキップと同じ告知確認を出す（告知をすり抜ける経路を作らない）。
    // 告知ダイアログ表示中はそちらの戻る処理に任せるため、このハンドラは止める。
    BackHandler(enabled = !confirmSkip) {
        when {
            page > 0 -> page--
            canExitToHome -> onExit()
            else -> confirmSkip = true
        }
    }
    val pages = listOf(
        Triple("書くだけでいい", "思いついたら、1行だけ書く。\n分類も、フォルダも、整理もいりません。", 1.0f),
        // v0.3.10：本文はタイプ選択UIに差し替えるため空にしておく
        Triple("あなたは、どっちのタイプ？", "", 0.7f),
        Triple("忘れかけた頃、また出会える", "メモは忘却曲線に合わせて、\n画面の上にそっと浮かび上がります。\n見返す努力は、ゼロでいい。", 0.45f),
        Triple("残したいものだけ、残る", "「残す」を押したメモだけが、\n少しずつ間を空けて出てきて、身につきます。\n押さなかったメモは、30日でそっと消えます。\nアプリを削除すると、メモもすべて消えます。", 0.25f),
    )
    // v0.3.10（本人の指示）：タイプごとに「あなたに最適」が返る選択肢
    val types = listOf(
        "よく忘れる" to "忘れて、いいんです。\nこのアプリが覚えていて、「そろそろ」の頃にそっと出してきます。\nよく忘れるあなたに、最適です。",
        "マルチタスクが苦手" to "同時に抱えなくて、いいんです。\n頭の中の「いつかやろう」は、ぜんぶここに預けてください。\n1つずつ向き合いたいあなたに、最適です。",
        "期限に追われたくない" to "締め切りは、ありません。\n「いつかやろう」が「そろそろやろう」に変わるまで、静かに待っています。\n期限が苦手なあなたに、最適です。",
    )
    Surface(color = Palette.Paper, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(1.0f, 0.55f, 0.28f).forEachIndexed { i, a ->
                    Box(
                        Modifier.width((190 - i * 28).dp).height(9.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Palette.Ink.copy(alpha = a * pages[page].third.coerceAtLeast(0.28f)))
                    )
                }
            }
            Spacer(Modifier.height(30.dp))
            // v0.3.7（本人の言葉）：キャッチフレーズは入口の1枚目にだけ、小さくピンクで置く
            if (page == 0) {
                Text("メモを不要にするメモ帳", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.Pink, letterSpacing = 2.sp)
                Spacer(Modifier.height(10.dp))
            }
            Text(pages[page].first, fontSize = 26.sp, fontWeight = FontWeight.Black, color = Palette.Ink)
            Spacer(Modifier.height(12.dp))
            if (page == 1) {
                // v0.3.10：タイプ選択（タップで触覚フィードバック＋「あなたに最適」の返し）
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    types.forEachIndexed { i, type ->
                        val selected = selectedType == i
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(if (selected) Palette.PinkBg else Palette.CardWhite)
                                .border(1.dp, if (selected) Palette.Pink else Palette.Line, RoundedCornerShape(16.dp))
                                .clickable {
                                    selectedType = i
                                    onSelectType(i)
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                }
                                .padding(horizontal = 16.dp, vertical = 13.dp)
                        ) {
                            Text(
                                if (selected) "${type.first} ✓" else type.first,
                                fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                color = if (selected) Palette.Pink else Palette.Ink,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (selectedType >= 0) {
                    androidx.compose.animation.AnimatedVisibility(visible = true) {
                        Text(types[selectedType].second, fontSize = 15.sp, color = Palette.InkSoft, lineHeight = 25.sp)
                    }
                } else {
                    Text("当てはまるものを、タップしてみて。", fontSize = 13.5.sp, color = Palette.InkSoft)
                }
            } else {
                Text(pages[page].second, fontSize = 16.sp, color = Palette.InkSoft, lineHeight = 26.sp)
            }
            Spacer(Modifier.height(34.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pages.indices.forEach { i ->
                    Box(
                        Modifier.width(if (i == page) 22.dp else 8.dp).height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (i == page) Palette.Pink else Palette.Line)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            if (page < pages.lastIndex) {
                Button(
                    // v0.3.8（安定性レビュー）：連打でページ番号が範囲を超えて落ちないよう上限で止める
                    onClick = { page = (page + 1).coerceAtMost(pages.lastIndex) },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Pink),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("つぎへ", fontSize = 16.sp) }
                TextButton(onClick = { confirmSkip = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("スキップ", color = Palette.InkSoft)
                }
            } else {
                Button(
                    onClick = onDone,
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Pink),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("はじめる", fontSize = 16.sp) }
            }
        }
    }
    if (confirmSkip) {
        AlertDialog(
            onDismissRequest = { confirmSkip = false },
            containerColor = Palette.CardWhite,
            title = { Text("ひとつだけ、知っておいてください") },
            text = { Text("「残す」を押さなかったメモは、30日でそっと消えていきます。それが、このアプリの約束です。") },
            confirmButton = {
                TextButton(onClick = { confirmSkip = false; onDone() }) { Text("わかってはじめる") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSkip = false }) { Text("もどる") }
            },
        )
    }
}
