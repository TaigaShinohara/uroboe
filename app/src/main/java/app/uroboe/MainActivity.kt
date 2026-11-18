package app.uroboe

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
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
}

private data class NoticeState(
    val text: String,
    val undoLabel: String? = null,
    val onUndo: (() -> Unit)? = null,
)

private fun fmtDateFull(ms: Long): String =
    SimpleDateFormat("yyyy年M月d日", Locale.JAPAN).format(Date(ms))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    var writeOpen by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmDeleteId by remember { mutableStateOf<Long?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showOnboarding by remember { mutableStateOf(!prefs.getBoolean("onboarded", false)) }

    var notice by remember { mutableStateOf<NoticeState?>(null) }
    var noticeKey by remember { mutableStateOf(0) }
    fun showNotice(text: String, undoLabel: String? = null, onUndo: (() -> Unit)? = null) {
        notice = NoticeState(text, undoLabel, onUndo)
        noticeKey++
    }
    LaunchedEffect(noticeKey) {
        val current = notice
        if (current != null) {
            delay(if (current.onUndo != null) 4200 else 2800)
            notice = null
        }
    }

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
            canExitToHome = prefs.getBoolean("onboarded", false),
            onExit = { showOnboarding = false },
            onSelectType = { prefs.edit().putInt("user_type", it).apply() },
        )
        return
    }

    if (writeOpen) {
        WriteScreen(
            onDismiss = { writeOpen = false },
            onSave = { body, q, a ->
                store.addMemo(body, q, a)
                writeOpen = false
                now = System.currentTimeMillis()
                showNotice("メモしました。忘れかけた頃に上へ浮かびます")
            },
        )
        return
    }

    val detailMemo = detailId?.let { id -> data.memos.firstOrNull { it.id == id } }
    if (detailMemo != null) {
        EditScreen(
            memo = detailMemo,
            onOpened = { store.markReviewed(detailMemo.id) },
            onBack = {
                detailId = null
                now = System.currentTimeMillis()
            },
            onEdit = { body, q, a -> store.editBody(detailMemo.id, body, q, a) },
            onToggleLowPriority = { lowPriority ->
                val snapshot = detailMemo
                store.setLowPriority(detailMemo.id, lowPriority)
                detailId = null
                now = System.currentTimeMillis()
                if (lowPriority) {
                    showNotice("「いつかやればいい」に下げました", "元に戻す") {
                        store.restoreSnapshot(snapshot)
                    }
                } else {
                    showNotice("「そろそろやろう」に戻しました", "元に戻す") {
                        store.restoreSnapshot(snapshot)
                    }
                }
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
                shape = CircleShape,
                containerColor = Palette.Pink,
                contentColor = Color.White,
            ) { Text("＋", fontSize = 24.sp) }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            SingleScreen(
                data = data,
                now = now,
                onOpenDetail = { detailId = it },
                onMoveToSomeday = { memo ->
                    store.setLowPriority(memo.id, true)
                    now = System.currentTimeMillis()
                    showNotice("「いつかやればいい」に下げました", "元に戻す") {
                        store.restoreSnapshot(memo)
                    }
                },
                onMoveToActive = { memo ->
                    store.setLowPriority(memo.id, false)
                    now = System.currentTimeMillis()
                    showNotice("「そろそろやろう」に戻しました", "元に戻す") {
                        store.restoreSnapshot(memo)
                    }
                },
                onSwipeDelete = { memo ->
                    store.deleteNow(memo.id)
                    now = System.currentTimeMillis()
                    showNotice("メモを削除しました", "元に戻す") {
                        store.restoreSnapshot(memo)
                    }
                },
                onShowHelp = { showOnboarding = true },
                onWipe = { confirmWipe = true },
                onAbout = { showAbout = true },
                onExport = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "うろぼえ データの控え")
                        putExtra(Intent.EXTRA_TEXT, store.exportJson())
                    }
                    context.startActivity(Intent.createChooser(send, "データの控えを送る"))
                },
            )
            notice?.let { current ->
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp, start = 16.dp, end = 16.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = Palette.PinkBg,
                    shadowElevation = 3.dp,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(
                            start = 16.dp,
                            end = if (current.onUndo != null) 8.dp else 16.dp,
                            top = 6.dp,
                            bottom = 6.dp,
                        ),
                    ) {
                        Text(
                            current.text,
                            color = Palette.Pink,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                        if (current.undoLabel != null && current.onUndo != null) {
                            Spacer(Modifier.width(6.dp))
                            TextButton(
                                onClick = {
                                    current.onUndo.invoke()
                                    notice = null
                                    now = System.currentTimeMillis()
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    current.undoLabel,
                                    color = Palette.Pink,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    confirmDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDeleteId = null },
            containerColor = Palette.CardWhite,
            title = { Text("このメモを削除しますか？") },
            text = { Text("一覧から完全に消えます。元に戻せません。") },
            confirmButton = {
                TextButton(onClick = { store.deleteNow(id); confirmDeleteId = null }) {
                    Text("削除する", color = Palette.Orange)
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
                Text("メモを不要にするメモ帳。\nバージョン 0.4.0\n\n期限を切るほどではない「いつかやろう」のためのメモ帳。書いたメモは勝手に消えず、忘却曲線に沿って忘れかけたものが上へ浮かびます。\n\nまだやらなくていいものは、横にスワイプして「いつかやればいい」へ下げられます。データはこの端末の中だけにあります。\n\nアプリを削除（アンインストール）すると、メモもすべて消えます。大事なメモは、画面いちばん下の「データを書き出す」から控えを取っておけます。")
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
private fun MemoCardShell(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    containerColor: Color = Palette.CardWhite,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 5.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(13.dp)) { content() }
    }
}

@Composable
private fun SwipeableMemoWrapper(
    enableStartToEnd: Boolean,
    enableEndToStart: Boolean,
    startToEndLabel: String,
    startToEndBg: Color,
    startToEndColor: Color,
    endToStartLabel: String,
    endToStartBg: Color,
    endToStartColor: Color,
    onStartToEnd: () -> Unit,
    onEndToStart: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val currentOnStartToEnd by rememberUpdatedState(onStartToEnd)
    val currentOnEndToStart by rememberUpdatedState(onEndToStart)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> enableStartToEnd
                SwipeToDismissBoxValue.EndToStart -> enableEndToStart
                SwipeToDismissBoxValue.Settled -> false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.35f },
    )
    LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                currentOnStartToEnd()
                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
            }
            SwipeToDismissBoxValue.EndToStart -> {
                currentOnEndToStart()
                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
            }
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = enableStartToEnd,
        enableDismissFromEndToStart = enableEndToStart,
        backgroundContent = {
            val dir = dismissState.dismissDirection
            val isStartToEnd = dir == SwipeToDismissBoxValue.StartToEnd
            val bg = when (dir) {
                SwipeToDismissBoxValue.StartToEnd -> startToEndBg
                SwipeToDismissBoxValue.EndToStart -> endToStartBg
                SwipeToDismissBoxValue.Settled -> Color.Transparent
            }
            val fg = if (isStartToEnd) startToEndColor else endToStartColor
            val label = if (isStartToEnd) startToEndLabel else endToStartLabel
            val align = if (isStartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(bg)
                    .padding(horizontal = 20.dp),
                contentAlignment = align,
            ) {
                if (dir != SwipeToDismissBoxValue.Settled) {
                    Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        content = { content() },
    )
}

// ---------------- 単一画面（全件一覧：上段＝忘却曲線順、下段＝いつかやればいい） ----------------

@Composable
fun SingleScreen(
    data: StoreData,
    now: Long,
    onOpenDetail: (Long) -> Unit,
    onMoveToSomeday: (Memo) -> Unit,
    onMoveToActive: (Memo) -> Unit,
    onSwipeDelete: (Memo) -> Unit,
    onShowHelp: () -> Unit,
    onWipe: () -> Unit,
    onAbout: () -> Unit,
    onExport: () -> Unit,
) {
    val activeMemos = remember(data.memos, now) { MemoLogic.sortActiveByForgettingCurve(data.memos, now) }
    val somedayMemos = remember(data.memos) { MemoLogic.sortSomeday(data.memos) }

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

        if (data.memos.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(top = 84.dp, bottom = 24.dp),
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
                    Text("まだメモがありません", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                    Spacer(Modifier.height(5.dp))
                    Text("右下の＋から、「そろそろやろう」を1行書いてみて", fontSize = 13.5.sp, color = Palette.InkSoft)
                }
            }
        } else {
            // 上段：そろそろやろう（忘却曲線順）
            item {
                SectionTitle(
                    text = "そろそろやろう",
                    color = Palette.Pink,
                    sub = if (activeMemos.isNotEmpty()) "横スワイプで下へ ・ ${activeMemos.size}件" else "0件",
                )
            }
            if (activeMemos.isEmpty()) {
                item {
                    Text(
                        "いま上段にあるメモはありません。右下の＋で追加するか、下から戻せます。",
                        fontSize = 13.sp,
                        color = Palette.InkSoft,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
            } else {
                items(activeMemos, key = { "active_${it.id}" }) { memo ->
                    val isDue = MemoLogic.isDueForReview(memo, now)
                    SwipeableMemoWrapper(
                        modifier = Modifier.animateItem(),
                        enableStartToEnd = true,
                        enableEndToStart = true,
                        startToEndLabel = "いつかやればいいへ ↓",
                        startToEndBg = Palette.Line,
                        startToEndColor = Palette.InkSoft,
                        endToStartLabel = "↓ いつかやればいいへ",
                        endToStartBg = Palette.Line,
                        endToStartColor = Palette.InkSoft,
                        onStartToEnd = { onMoveToSomeday(memo) },
                        onEndToStart = { onMoveToSomeday(memo) },
                    ) {
                        MemoCardShell(onClick = { onOpenDetail(memo.id) }) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    MemoLogic.elapsedLabel(memo.createdAt, now),
                                    fontSize = 11.5.sp,
                                    color = Palette.InkSoft,
                                )
                                if (isDue) {
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(Palette.PinkBg)
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            "忘れかけ",
                                            fontSize = 10.5.sp,
                                            color = Palette.Pink,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(
                                memo.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.5.sp,
                                color = Palette.Ink,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!memo.isQuiz && memo.displayBody.isNotBlank() && memo.displayBody != memo.title) {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    memo.displayBody,
                                    fontSize = 13.sp,
                                    color = Palette.InkSoft,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (memo.isQuiz) {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    "クイズ形式 ・ タップで答えを見る",
                                    fontSize = 11.5.sp,
                                    color = Palette.InkSoft,
                                )
                            }
                        }
                    }
                }
            }

            // 下段：いつかやればいい（優先度を下げたメモ）
            if (somedayMemos.isNotEmpty()) {
                item {
                    SectionTitle(
                        text = "いつかやればいい",
                        color = Palette.InkSoft,
                        sub = "右で戻す ・ 左で削除 ・ ${somedayMemos.size}件",
                    )
                }
                items(somedayMemos, key = { "someday_${it.id}" }) { memo ->
                    SwipeableMemoWrapper(
                        modifier = Modifier.animateItem(),
                        enableStartToEnd = true,
                        enableEndToStart = true,
                        startToEndLabel = "↑ そろそろやろうへ戻す",
                        startToEndBg = Palette.PinkBg,
                        startToEndColor = Palette.Pink,
                        endToStartLabel = "削除",
                        endToStartBg = Palette.OrangeBg,
                        endToStartColor = Palette.Orange,
                        onStartToEnd = { onMoveToActive(memo) },
                        onEndToStart = { onSwipeDelete(memo) },
                    ) {
                        MemoCardShell(
                            onClick = { onOpenDetail(memo.id) },
                            modifier = Modifier.alpha(0.72f),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    memo.title,
                                    fontSize = 14.5.sp,
                                    color = Palette.Ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    MemoLogic.elapsedLabel(memo.createdAt, now),
                                    fontSize = 11.sp,
                                    color = Palette.InkSoft,
                                )
                            }
                        }
                    }
                }
            }
        }

        // フッター（設定は小さく一番下）
        item {
            Spacer(Modifier.height(26.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
            Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                TextButton(onClick = onAbout) { Text("このアプリについて", fontSize = 12.5.sp, color = Palette.InkSoft) }
                TextButton(onClick = onExport) { Text("データを書き出す", fontSize = 12.5.sp, color = Palette.InkSoft) }
                TextButton(onClick = onWipe) { Text("データをすべて消す", fontSize = 12.5.sp, color = Palette.Orange) }
            }
        }
    }
}

// ---------------- 書く（全画面・Google Keep風の枠なし） ----------------

@Composable
fun WriteScreen(onDismiss: () -> Unit, onSave: (String, String?, String?) -> Unit) {
    var body by rememberSaveable { mutableStateOf("") }
    var quiz by rememberSaveable { mutableStateOf(false) }
    var question by rememberSaveable { mutableStateOf("") }
    var answer by rememberSaveable { mutableStateOf("") }
    var confirmDiscard by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    val canSave = if (quiz) question.isNotBlank() && answer.isNotBlank() else body.isNotBlank()
    val hasDraft = body.isNotBlank() || question.isNotBlank() || answer.isNotBlank()
    fun tryDismiss() {
        if (hasDraft) confirmDiscard = true else onDismiss()
    }
    BackHandler { tryDismiss() }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(quiz) { runCatching { focusRequester.requestFocus() } }
    Surface(color = Palette.Paper, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp)
        ) {
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
                if (body.length >= 900) {
                    Text(
                        "あと${1000 - body.length}字まで書けます",
                        fontSize = 11.5.sp, color = Palette.InkSoft,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
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
                "分類はしなくてOK。忘れかけた頃に上へ浮かびます。後回しにしたいものはスワイプで下げられます。",
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

// ---------------- メモを開く（全画面・自動保存・優先度切替／削除） ----------------

@Composable
fun EditScreen(
    memo: Memo,
    onOpened: () -> Unit,
    onBack: () -> Unit,
    onEdit: (String, String?, String?) -> Unit,
    onToggleLowPriority: (Boolean) -> Unit,
    onDeleteNow: () -> Unit,
) {
    var body by remember(memo.id) { mutableStateOf(memo.body) }
    var question by remember(memo.id) { mutableStateOf(memo.question ?: "") }
    var answer by remember(memo.id) { mutableStateOf(memo.answer ?: "") }
    var quizAnswerRevealed by remember(memo.id) { mutableStateOf(false) }
    var undoStack by remember(memo.id) { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) }
    var lastField by remember(memo.id) { mutableStateOf(-1) }
    var lastPushAt by remember(memo.id) { mutableStateOf(0L) }

    val currentOnOpened by rememberUpdatedState(onOpened)
    LaunchedEffect(memo.id) { currentOnOpened() }

    fun persist(b: String, q: String, a: String) {
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
    BackHandler { onBack() }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(memo.id) {
        if (!memo.isQuiz) runCatching { focusRequester.requestFocus() }
    }
    Surface(color = Palette.Paper, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("←", fontSize = 20.sp, color = Palette.InkSoft) }
                TextButton(onClick = { undo() }, enabled = undoStack.isNotEmpty()) {
                    Text(
                        "↩ 戻す", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = if (undoStack.isNotEmpty()) Palette.Pink else Palette.InkSoft.copy(alpha = 0.45f),
                    )
                }
            }
            val statusText = if (memo.lowPriority) "いつかやればいい" else "そろそろやろう"
            Text(
                "作成：${fmtDateFull(memo.createdAt)} ・ $statusText",
                fontSize = 12.sp,
                color = Palette.InkSoft,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )
            if (memo.isQuiz) {
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
                if (!quizAnswerRevealed) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        OutlinedButton(
                            onClick = { quizAnswerRevealed = true },
                            shape = RoundedCornerShape(999.dp),
                        ) {
                            Text("タップで答えを表示・編集", color = Palette.Pink, fontSize = 13.5.sp)
                        }
                    }
                } else {
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
                }
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
            Text(
                "入力すると自動で保存されます。",
                fontSize = 11.5.sp, color = Palette.InkSoft,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { onToggleLowPriority(!memo.lowPriority) },
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (memo.lowPriority) "「そろそろやろう」に戻す" else "「いつかやればいい」に下げる",
                        fontSize = 13.sp,
                        color = if (memo.lowPriority) Palette.Pink else Palette.InkSoft,
                    )
                }
                OutlinedButton(
                    onClick = onDeleteNow,
                    shape = RoundedCornerShape(999.dp),
                ) { Text("削除する", fontSize = 13.sp, color = Palette.Orange) }
            }
        }
    }
}

// ---------------- オンボーディング ----------------

@Composable
fun OnboardingScreen(onDone: () -> Unit, canExitToHome: Boolean = false, onExit: () -> Unit = {}, onSelectType: (Int) -> Unit = {}) {
    var page by rememberSaveable { mutableStateOf(0) }
    var selectedType by rememberSaveable { mutableStateOf(-1) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var confirmSkip by remember { mutableStateOf(false) }
    BackHandler(enabled = !confirmSkip) {
        when {
            page > 0 -> page--
            canExitToHome -> onExit()
            else -> confirmSkip = true
        }
    }
    val pages = listOf(
        Triple("書くだけでいい", "思いついたら、1行だけ書く。\n分類も、フォルダも、整理もいりません。", 1.0f),
        Triple("あなたは、どっちのタイプ？", "", 0.7f),
        Triple("忘れかけた頃、上へ浮かぶ", "書いたメモは勝手には消えません。\n忘却曲線に沿って、忘れかけたものが\n自動で上へ浮かび上がります。", 0.45f),
        Triple("後回しは、スワイプひとつ", "「今はやらなくていい」と思ったら\n横にスワイプして「いつかやればいい」へ。\n必要なものだけが上段に残ります。\nアプリを削除すると、メモもすべて消えます。", 0.25f),
    )
    val types = listOf(
        "よく忘れる" to "忘れて、いいんです。\nこのアプリが覚えていて、「そろそろ」の頃に上へ出してきます。\nよく忘れるあなたに、最適です。",
        "マルチタスクが苦手" to "同時に抱えなくて、いいんです。\n頭の中の「いつかやろう」は、ぜんぶここに預けてください。\n1つずつ向き合いたいあなたに、最適です。",
        "期限に追われたくない" to "締め切りは、ありません。\n「いつかやろう」が「そろそろやろう」に変わるまで、静かに並べて待っています。\n期限が苦手なあなたに、最適です。",
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
            if (page == 0) {
                Text("メモを不要にするメモ帳", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.Pink, letterSpacing = 2.sp)
                Spacer(Modifier.height(10.dp))
            }
            Text(pages[page].first, fontSize = 26.sp, fontWeight = FontWeight.Black, color = Palette.Ink)
            Spacer(Modifier.height(12.dp))
            if (page == 1) {
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
            text = { Text("書いたメモは消えずにすべて一覧に残り、忘れかけた頃に上へ浮かびます。後回しにしたいものだけスワイプで下げられます。") },
            confirmButton = {
                TextButton(onClick = { confirmSkip = false; onDone() }) { Text("わかってはじめる") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSkip = false }) { Text("もどる") }
            },
        )
    }
}
