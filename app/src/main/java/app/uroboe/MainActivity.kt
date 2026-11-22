package app.uroboe

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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

/** アート方針（docs/ART_DIRECTION.md）の色。全テキストで WCAG AA 4.5:1 以上を確保する。 */
object Palette {
    val Paper = Color(0xFFFFFBF3)
    val Ink = Color(0xFF2B2620)
    val InkSoft = Color(0xFF665D51)
    val Line = Color(0xFFE9DFCF)
    val CardWhite = Color(0xFFFFFFFF)
    val SomedayCard = Color(0xFFF6F0E4)
    val Pink = Color(0xFFC2185B)
    val PinkBg = Color(0xFFFCE7F1)
    /** 落ち着いた濃い橙（対白 5.5:1 / 対紙色 5.3:1 / 対 OrangeBg 4.9:1）。赤は使わない。 */
    val Orange = Color(0xFFB83B00)
    val OrangeBg = Color(0xFFFFF0E3)
}

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
        val store = MemoStoreHolder.get(applicationContext)
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
    val notice by store.notice.collectAsState()
    val lastSaveFailed by store.lastSaveFailed.collectAsState()
    var now by remember { mutableStateOf(store.effectiveNow()) }
    var isStarted by remember { mutableStateOf(true) }

    var writeOpen by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmWipe by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showOnboarding by rememberSaveable { mutableStateOf(!prefs.getBoolean("onboarded", false)) }

    // バナー自動消去（別画面にいる間はタイマーを進めず、一覧に戻ってからカウント）
    val onHome = !showOnboarding && !writeOpen && detailId == null
    LaunchedEffect(notice?.id, onHome) {
        val current = notice
        if (current != null && onHome) {
            delay(if (current.snapshot != null) 4500 else 2800)
            store.dismissNotice(current.id)
        }
    }

    val activity = LocalContext.current as? ComponentActivity
    DisposableEffect(activity) {
        if (activity == null) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    isStarted = true
                    now = store.effectiveNow()
                }
                Lifecycle.Event.ON_RESUME -> {
                    now = store.effectiveNow()
                }
                Lifecycle.Event.ON_STOP -> {
                    isStarted = false
                }
                else -> Unit
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    // フォアグラウンド表示中のみ60秒ごとに画面の経過時刻を更新（ディスク保存は行わない）
    LaunchedEffect(isStarted) {
        while (isStarted) {
            delay(60_000)
            now = store.effectiveNow()
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
        // v0.5.1：保存ボタンは廃止し、書く画面も自動保存にした（詳細画面と同方式）
        WriteScreen(
            onDismiss = { writeOpen = false; now = store.effectiveNow() },
            onCreate = { body -> store.addMemo(body).id },
            onUpdate = { id, body -> store.editBody(id, body, null, null) },
            onDiscardNew = { id -> store.deleteNow(id) },
        )
        return
    }

    val detailMemo = detailId?.let { id -> data.memos.firstOrNull { it.id == id } }
    if (detailMemo != null) {
        EditScreen(
            memo = detailMemo,
            now = now,
            onBack = {
                detailId = null
                now = store.effectiveNow()
            },
            onEdit = { body, q, a -> store.editBody(detailMemo.id, body, q, a) },
            onWillDo = {
                val snapshot = detailMemo
                store.setWillDo(detailMemo.id)
                detailId = null
                now = store.effectiveNow()
                store.postNotice("「そろそろやる」に決めました", "元に戻す", snapshot)
            },
            onDefer = {
                val snapshot = detailMemo
                store.deferToNextStage(detailMemo.id)
                detailId = null
                now = store.effectiveNow()
                store.postNotice("「まだやらない」にしました", "元に戻す", snapshot)
            },
            onComplete = {
                val snapshot = detailMemo
                store.deleteNow(detailMemo.id)
                detailId = null
                now = store.effectiveNow()
                store.postNotice("「やった」で片づけました", "元に戻す", snapshot)
            },
            onToggleLowPriority = { lowPriority ->
                val snapshot = detailMemo
                store.setLowPriority(detailMemo.id, lowPriority)
                detailId = null
                now = store.effectiveNow()
                if (lowPriority) {
                    store.postNotice("「いつかやればいい」へ保留しました", "元に戻す", snapshot)
                } else {
                    store.postNotice("「そろそろやろう」へ戻しました", "元に戻す", snapshot)
                }
            },
            onRequestDelete = {
                confirmDeleteId = detailMemo.id
            },
        )
    } else {
        Scaffold(
            containerColor = Palette.Paper,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { writeOpen = true },
                    shape = CircleShape,
                    containerColor = Palette.Pink,
                    contentColor = Color.White,
                    modifier = Modifier.semantics { contentDescription = "新しいメモを書く" },
                ) { Text("＋", fontSize = 24.sp) }
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                SingleScreen(
                    data = data,
                    now = now,
                    saveFailed = lastSaveFailed,
                    onOpenDetail = { detailId = it },
                    onDefer = { memo ->
                        store.deferToNextStage(memo.id)
                        now = store.effectiveNow()
                        store.postNotice("「またあとで」に送りました", "元に戻す", memo)
                    },
                    onMoveToSomeday = { memo ->
                        store.setLowPriority(memo.id, true)
                        now = store.effectiveNow()
                        store.postNotice("「いつかやればいい」へ保留しました", "元に戻す", memo)
                    },
                    onMoveToActive = { memo ->
                        store.setLowPriority(memo.id, false)
                        now = store.effectiveNow()
                        store.postNotice("「そろそろやろう」へ戻しました", "元に戻す", memo)
                    },
                    onRequestDelete = { memo ->
                        confirmDeleteId = memo.id
                    },
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
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 16.dp, end = 84.dp, bottom = 18.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        shape = RoundedCornerShape(999.dp),
                        color = Palette.PinkBg,
                        shadowElevation = 3.dp,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(
                                start = 16.dp,
                                end = if (current.snapshot != null) 8.dp else 16.dp,
                                top = 6.dp,
                                bottom = 6.dp,
                            ),
                        ) {
                            Text(
                                current.text,
                                color = Palette.Pink,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (current.undoLabel != null && current.snapshot != null) {
                                Spacer(Modifier.width(6.dp))
                                TextButton(
                                    onClick = {
                                        store.undoLatestNotice()
                                        now = store.effectiveNow()
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        current.undoLabel,
                                        color = Palette.Pink,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.5.sp,
                                    )
                                }
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
                TextButton(
                    onClick = {
                        store.deleteNow(id)
                        if (detailId == id) detailId = null
                        confirmDeleteId = null
                        now = store.effectiveNow()
                    }
                ) {
                    Text("削除する", color = Palette.Orange, fontWeight = FontWeight.Bold)
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
                    Text("すべて消す", color = Palette.Orange, fontWeight = FontWeight.Bold)
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
                Text(
                    "「いつかやろう」を「そろそろやろう」に変えるメモ帳。\nバージョン 0.5.1\n\n" +
                        "書いたメモは勝手には消えず、忘却曲線に沿って忘れかけたものが上へ浮かびます。\n\n" +
                        "上段のメモは右スワイプで「またあとで」、左スワイプで「いつかやればいい」（自動浮上なしの保留）へ分けられます。データはこの端末の中だけにあります。\n\n" +
                        "アプリを削除（アンインストール）すると、メモもすべて消えます。大事なメモは、画面いちばん下の「その他」→「データを書き出す」から控えを取っておけます。"
                )
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("閉じる") }
            },
            dismissButton = {
                // v0.5：「使い方」はヘッダーから移し、ここから開けるようにした
                TextButton(onClick = { showAbout = false; showOnboarding = true }) { Text("使い方を見る") }
            },
        )
    }
}

/**
 * 新コンセプト「忘れかけた1枚が静かに上へ浮かぶ」を表す共通モチーフ。
 * highlightIndex = 0 のとき最上段がピンクでふわりと浮上する。
 */
@Composable
private fun FloatingMemoMotif(
    centered: Boolean = true,
    elevatedTop: Boolean = true,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(7.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        // 最上段：浮かび上がる1枚
        Box(
            Modifier
                .width(if (elevatedTop) 164.dp else 148.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (elevatedTop) Palette.Pink else Palette.Ink.copy(alpha = 0.72f))
        )
        // 下の2枚：静かに控えるメモ
        Box(
            Modifier
                .width(132.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Palette.Line)
        )
        Box(
            Modifier
                .width(104.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Palette.Line)
        )
    }
}

@Composable
private fun SectionTitle(
    text: String,
    countLabel: String,
    color: Color = Palette.Pink,
) {
    // v0.5：見出し下のヒント文は整理で削除した
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 12.5.sp,
            letterSpacing = 0.8.sp,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(countLabel, color = Palette.InkSoft, fontSize = 12.5.sp)
    }
}

@Composable
private fun MemoCardShell(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    containerColor: Color = Palette.CardWhite,
    elevationDp: Float = 1f,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevationDp.dp),
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
    val haptic = LocalHapticFeedback.current
    val currentOnStartToEnd by rememberUpdatedState(onStartToEnd)
    val currentOnEndToStart by rememberUpdatedState(onEndToStart)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> enableStartToEnd
                SwipeToDismissBoxValue.EndToStart -> enableEndToStart
                SwipeToDismissBoxValue.Settled -> true
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.40f },
    )
    LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnStartToEnd()
                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
            }
            SwipeToDismissBoxValue.EndToStart -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                    Text(label, color = fg, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
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
    saveFailed: Boolean,
    onOpenDetail: (Long) -> Unit,
    onDefer: (Memo) -> Unit,
    onMoveToSomeday: (Memo) -> Unit,
    onMoveToActive: (Memo) -> Unit,
    onRequestDelete: (Memo) -> Unit,
    onWipe: () -> Unit,
    onAbout: () -> Unit,
    onExport: () -> Unit,
) {
    val activeMemos = remember(data.memos, now) { MemoLogic.sortActiveByForgettingCurve(data.memos, now) }
    val highlightedDueIds = remember(activeMemos, now) { MemoLogic.highlightedDueIds(activeMemos, now) }
    val somedayMemos = remember(data.memos) { MemoLogic.sortSomeday(data.memos) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        // ヘッダー（v0.5：日付と「使い方」は整理で削除。使い方は「その他」→Aboutから開ける）
        item {
            Text(
                "うろぼえ",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = Palette.Ink,
                modifier = Modifier.padding(top = 14.dp),
            )
        }

        if (saveFailed) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Palette.OrangeBg)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        "保存に失敗しました。空き容量を確認し、念のため一番下の「その他」→「データを書き出す」で控えを取ってください。",
                        color = Palette.Orange,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        if (data.memos.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(top = 84.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FloatingMemoMotif(centered = true, elevatedTop = true)
                    Spacer(Modifier.height(22.dp))
                    Text("まだメモがありません", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "右下の＋から、「いつかやろう」を1行書いてみて",
                        fontSize = 12.5.sp,
                        color = Palette.InkSoft,
                    )
                }
            }
        } else {
            // 上段：そろそろやろう（忘却曲線順）
            item {
                SectionTitle(
                    text = "そろそろやろう",
                    countLabel = "${activeMemos.size}件",
                    color = Palette.Pink,
                )
            }
            if (activeMemos.isEmpty()) {
                item {
                    Text(
                        "いま上段にあるメモはありません。右下の＋で追加するか、下段から戻せます。",
                        fontSize = 12.5.sp,
                        color = Palette.InkSoft,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
            } else {
                items(activeMemos, key = { "active_${it.id}" }) { memo ->
                    val showDuePill = memo.id in highlightedDueIds
                    SwipeableMemoWrapper(
                        modifier = Modifier.animateItem(),
                        enableStartToEnd = true,
                        enableEndToStart = true,
                        startToEndLabel = "またあとで →",
                        startToEndBg = Palette.PinkBg,
                        startToEndColor = Palette.Pink,
                        endToStartLabel = "↓ いつかやればいいへ",
                        endToStartBg = Palette.Line,
                        endToStartColor = Palette.Ink,
                        onStartToEnd = { onDefer(memo) },
                        onEndToStart = { onMoveToSomeday(memo) },
                    ) {
                        MemoCardShell(onClick = { onOpenDetail(memo.id) }) {
                            // v0.5：経過日数は整理で削除。ピルがある時だけ右上に出す
                            val pillText = when {
                                memo.willDoAt != null -> "そろそろやる"
                                showDuePill -> "忘れかけ"
                                else -> null
                            }
                            if (pillText != null) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(Palette.PinkBg)
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            pillText,
                                            fontSize = 12.5.sp,
                                            color = Palette.Pink,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(5.dp))
                            }
                            Text(
                                memo.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Palette.Ink,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // v0.4.2（B-2修正）：プレビューは題名（最初の空でない行）を除いた残りを表示し、1行目の二重表示を防ぐ
                            val previewBody = if (memo.isQuiz) "" else memo.body.lineSequence()
                                .dropWhile { it.isBlank() }
                                .drop(1)
                                .joinToString("\n")
                                .trim()
                            if (previewBody.isNotBlank()) {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    previewBody,
                                    fontSize = 12.5.sp,
                                    color = Palette.InkSoft,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (memo.isQuiz) {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    "クイズ形式 ・ タップで答えを見る",
                                    fontSize = 12.5.sp,
                                    color = Palette.InkSoft,
                                )
                            }
                        }
                    }
                }
            }

            // 下段：いつかやればいい（自動浮上なしの保留）
            if (somedayMemos.isNotEmpty()) {
                item {
                    SectionTitle(
                        // v0.5：ヒント文は消したが「自動浮上なし」は下段の役割そのものなので見出しに残す（SPEC §4）
                        text = "いつかやればいい（自動浮上なし）",
                        countLabel = "${somedayMemos.size}件",
                        color = Palette.InkSoft,
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
                        onEndToStart = { onRequestDelete(memo) },
                    ) {
                        MemoCardShell(
                            onClick = { onOpenDetail(memo.id) },
                            containerColor = Palette.SomedayCard,
                            elevationDp = 0f,
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    memo.title,
                                    fontSize = 12.5.sp,
                                    color = Palette.Ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    MemoLogic.elapsedLabel(memo.createdAt, now),
                                    fontSize = 12.5.sp,
                                    color = Palette.InkSoft,
                                )
                            }
                        }
                    }
                }
            }
        }

        // フッター（v0.5：低頻度の管理は「その他」に集約し、常時表示から外した）
        item {
            var menuOpen by remember { mutableStateOf(false) }
            Spacer(Modifier.height(26.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
            Box(Modifier.padding(top = 6.dp)) {
                TextButton(onClick = { menuOpen = true }) {
                    Text("その他", fontSize = 12.5.sp, color = Palette.InkSoft)
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = Palette.CardWhite,
                ) {
                    DropdownMenuItem(
                        text = { Text("このアプリについて", fontSize = 12.5.sp, color = Palette.Ink) },
                        onClick = { menuOpen = false; onAbout() },
                    )
                    DropdownMenuItem(
                        text = { Text("データを書き出す", fontSize = 12.5.sp, color = Palette.Ink) },
                        onClick = { menuOpen = false; onExport() },
                    )
                    DropdownMenuItem(
                        text = { Text("データをすべて消す", fontSize = 12.5.sp, color = Palette.Orange) },
                        onClick = { menuOpen = false; onWipe() },
                    )
                }
            }
        }
    }
}

// ---------------- 書く（全画面・Google Keep風の枠なし1欄） ----------------

@Composable
fun WriteScreen(
    onDismiss: () -> Unit,
    onCreate: (String) -> Long,
    onUpdate: (Long, String) -> Unit,
    onDiscardNew: (Long) -> Unit,
) {
    var body by rememberSaveable { mutableStateOf("") }
    // v0.5.1：自動保存。最初の入力でメモを作り、以後はその場で更新する。
    // 全部消したら作りかけのメモも消す（＝実質なかったことになる）。戻る操作はそのまま確定。
    var createdId by rememberSaveable { mutableStateOf<Long?>(null) }
    BackHandler { onDismiss() }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    Surface(color = Palette.Paper, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { onDismiss() },
                    modifier = Modifier.semantics { contentDescription = "前の画面に戻る" },
                ) { Text("←", fontSize = 20.sp, color = Palette.InkSoft) }
            }
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                BasicTextField(
                    value = body,
                    onValueChange = { raw ->
                        val text = raw.take(1000)
                        body = text
                        val trimmed = text.trim()
                        val id = createdId
                        if (trimmed.isNotEmpty()) {
                            if (id == null) createdId = onCreate(trimmed) else onUpdate(id, trimmed)
                        } else if (id != null) {
                            onDiscardNew(id)
                            createdId = null
                        }
                    },
                    textStyle = TextStyle(fontSize = 17.sp, color = Palette.Ink, lineHeight = 27.sp),
                    cursorBrush = SolidColor(Palette.Pink),
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 200.dp).focusRequester(focusRequester),
                    decorationBox = { inner ->
                        if (body.isEmpty()) {
                            Text("メモ", fontSize = 17.sp, color = Palette.InkSoft)
                        }
                        inner()
                    },
                )
            }
            if (body.length >= 900) {
                Text(
                    "あと${1000 - body.length}字まで書けます",
                    fontSize = 12.5.sp, color = Palette.InkSoft,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            // v0.5：底の説明文は整理で削除した（オンボーディングで伝える）
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ---------------- メモを開く（全画面・閲覧と編集・明示的な区切り操作） ----------------

@Composable
fun EditScreen(
    memo: Memo,
    now: Long,
    onBack: () -> Unit,
    onEdit: (String, String?, String?) -> Unit,
    onWillDo: () -> Unit,
    onDefer: () -> Unit,
    onComplete: () -> Unit,
    onToggleLowPriority: (Boolean) -> Unit,
    onRequestDelete: () -> Unit,
) {
    var body by rememberSaveable(memo.id) { mutableStateOf(memo.body) }
    var question by rememberSaveable(memo.id) { mutableStateOf(memo.question ?: "") }
    var answer by rememberSaveable(memo.id) { mutableStateOf(memo.answer ?: "") }
    var quizAnswerRevealed by rememberSaveable(memo.id) { mutableStateOf(false) }
    var undoStack by remember(memo.id) { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) }
    var lastField by remember(memo.id) { mutableStateOf(-1) }
    var lastPushAt by remember(memo.id) { mutableStateOf(0L) }

    val isEmptyWarning = if (memo.isQuiz) {
        question.isBlank() || answer.isBlank()
    } else {
        body.isBlank()
    }

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

    Surface(color = Palette.Paper, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.semantics { contentDescription = "前の画面に戻る" },
                ) { Text("←", fontSize = 20.sp, color = Palette.InkSoft) }
                TextButton(
                    onClick = { undo() },
                    enabled = undoStack.isNotEmpty(),
                    modifier = Modifier.semantics { contentDescription = "編集を1つ元に戻す" },
                ) {
                    Text(
                        "↩ 戻す", fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                        color = if (undoStack.isNotEmpty()) Palette.Pink else Palette.InkSoft.copy(alpha = 0.45f),
                    )
                }
            }
            // v0.5：状態は1行に統合（日付 ・ 寝かせ具合）。西暦と「作成：」は落とした
            val dueLabel = MemoLogic.dueStatusLabel(memo, now)
            val dueEmphasis = memo.willDoAt != null || MemoLogic.isDueForReview(memo, now)
            val dateShort = SimpleDateFormat("M月d日", Locale.JAPAN).format(Date(memo.createdAt))
            val statusLine = if (memo.lowPriority) {
                "$dateShort ・ いつかやればいい"
            } else {
                "$dateShort ・ ${dueLabel ?: "そろそろやろう"}"
            }
            Text(
                statusLine,
                fontSize = 12.5.sp,
                fontWeight = if (dueEmphasis) FontWeight.Bold else FontWeight.Normal,
                color = if (dueEmphasis) Palette.Pink else Palette.InkSoft,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (memo.isQuiz) {
                    BasicTextField(
                        value = question,
                        onValueChange = { val v = it.take(1000); pushHistory(1); question = v; persist(body, v, answer) },
                        textStyle = TextStyle(fontSize = 17.sp, color = Palette.Ink, fontWeight = FontWeight.Bold, lineHeight = 27.sp),
                        cursorBrush = SolidColor(Palette.Pink),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (question.isEmpty()) {
                                Text("質問", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.InkSoft)
                            }
                            inner()
                        },
                    )
                    Box(Modifier.fillMaxWidth().padding(vertical = 14.dp).height(1.dp).background(Palette.Line))
                    if (!quizAnswerRevealed) {
                        OutlinedButton(
                            onClick = { quizAnswerRevealed = true },
                            shape = RoundedCornerShape(999.dp),
                        ) {
                            Text("タップで答えを表示・編集", color = Palette.Pink, fontSize = 12.5.sp)
                        }
                    } else {
                        BasicTextField(
                            value = answer,
                            onValueChange = { val v = it.take(1000); pushHistory(2); answer = v; persist(body, question, v) },
                            textStyle = TextStyle(fontSize = 17.sp, color = Palette.Ink, lineHeight = 26.sp),
                            cursorBrush = SolidColor(Palette.Pink),
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 160.dp),
                            decorationBox = { inner ->
                                if (answer.isEmpty()) {
                                    Text("答え", fontSize = 17.sp, color = Palette.InkSoft)
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
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 200.dp),
                        decorationBox = { inner ->
                            if (body.isEmpty()) {
                                Text("メモ", fontSize = 17.sp, color = Palette.InkSoft)
                            }
                            inner()
                        },
                    )
                }
            }
            // v0.5：「自動で保存されます」の常駐注記は整理で削除。空の警告だけ後出しで出す
            if (isEmptyWarning) {
                Text(
                    "空のままでは保存されません（元の内容が残ります）。",
                    fontSize = 12.5.sp,
                    color = Palette.Orange,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (!memo.lowPriority) {
                // v0.4.3：主役は「そろそろやる／まだやらない」の選択。完了は「やった」に改名。
                val committed = memo.willDoAt != null
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (committed) {
                        Button(
                            onClick = onComplete,
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Palette.Pink),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                "やった",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        Button(
                            onClick = onWillDo,
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Palette.Pink),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                "そろそろやる",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onDefer,
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            "まだやらない",
                            fontSize = 12.5.sp,
                            color = Palette.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { onToggleLowPriority(true) },
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            "下段へ",
                            fontSize = 12.5.sp,
                            color = Palette.InkSoft,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!committed) {
                        OutlinedButton(
                            onClick = onComplete,
                            shape = RoundedCornerShape(999.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                "やった",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Palette.Pink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { onToggleLowPriority(false) },
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            "上段へ戻す",
                            fontSize = 12.5.sp,
                            color = Palette.Pink,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OutlinedButton(
                        onClick = onRequestDelete,
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            "削除する",
                            fontSize = 12.5.sp,
                            color = Palette.Orange,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ---------------- オンボーディング ----------------

@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    canExitToHome: Boolean = false,
    onExit: () -> Unit = {},
    onSelectType: (Int) -> Unit = {},
) {
    var page by rememberSaveable { mutableStateOf(0) }
    var selectedType by rememberSaveable { mutableStateOf(-1) }
    val haptic = LocalHapticFeedback.current
    var confirmSkip by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = !confirmSkip) {
        when {
            page > 0 -> page--
            canExitToHome -> onExit()
            else -> confirmSkip = true
        }
    }
    val pages = listOf(
        Triple(
            "書くだけでいい",
            "思いついたら、1行だけ書く。\n分類も、フォルダも、整理もいりません。",
            false,
        ),
        Triple(
            "どのタイプに近い？",
            "",
            false,
        ),
        Triple(
            "忘れかけた頃、上へ浮かぶ",
            "書いたメモは勝手には消えません。\n忘却曲線に沿って、忘れかけたものが\n自動で上へ浮かび上がります。",
            true,
        ),
        Triple(
            "さばき方は、スワイプひとつ",
            "右にスワイプで「またあとで」、\n左にスワイプで「いつかやればいい」（保留）へ。\n開いた画面では「そろそろやる」か「まだやらない」かを選べて、やれたら「やった」で完了です。\n※アプリを削除すると、メモもすべて消えます。",
            true,
        ),
    )
    val types = listOf(
        "よく忘れる" to "忘れて、いいんです。\nこのアプリが覚えていて、「そろそろ」の頃に上へ出してきます。\nよく忘れるあなたに、最適です。",
        "マルチタスクが苦手" to "同時に抱えなくて、いいんです。\n頭の中の「いつかやろう」は、ぜんぶここに預けてください。\n1つずつ向き合いたいあなたに、最適です。",
        "期限に追われたくない" to "締め切りは、ありません。\n「いつかやろう」が「そろそろやろう」に変わるまで、静かに並べて待っています。\n期限が苦手なあなたに、最適です。",
    )
    Surface(color = Palette.Paper, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            FloatingMemoMotif(centered = false, elevatedTop = pages[page].third)
            Spacer(Modifier.height(28.dp))
            if (page == 0) {
                Text(
                    "「いつかやろう」を「そろそろやろう」に",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Palette.Pink,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(10.dp))
            }
            Text(pages[page].first, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Palette.Ink)
            Spacer(Modifier.height(12.dp))
            if (page == 1) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    types.forEachIndexed { i, type ->
                        val selected = selectedType == i
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selected) Palette.PinkBg else Palette.CardWhite)
                                .border(1.dp, if (selected) Palette.Pink else Palette.Line, RoundedCornerShape(16.dp))
                                .selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = {
                                        selectedType = i
                                        onSelectType(i)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                )
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                if (selected) "${type.first} ✓" else type.first,
                                fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                color = if (selected) Palette.Pink else Palette.Ink,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                AnimatedVisibility(visible = selectedType >= 0) {
                    val idx = selectedType.coerceIn(0, types.lastIndex)
                    Text(types[idx].second, fontSize = 15.sp, color = Palette.InkSoft, lineHeight = 25.sp)
                }
                if (selectedType < 0) {
                    Text("当てはまるものを、タップしてみて。", fontSize = 12.5.sp, color = Palette.InkSoft)
                }
            } else {
                Text(pages[page].second, fontSize = 15.sp, color = Palette.InkSoft, lineHeight = 26.sp)
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
                ) { Text("つぎへ", fontSize = 15.sp) }
                TextButton(onClick = { confirmSkip = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("スキップ", color = Palette.InkSoft)
                }
            } else {
                Button(
                    onClick = onDone,
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Pink),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("はじめる", fontSize = 15.sp) }
            }
        }
    }
    if (confirmSkip) {
        AlertDialog(
            onDismissRequest = { confirmSkip = false },
            containerColor = Palette.CardWhite,
            title = { Text("ひとつだけ、知っておいてください") },
            text = {
                Text(
                    "書いたメモは消えずに一覧に残り、忘れかけた頃に上へ浮かびます。\n" +
                        "※アプリを削除（アンインストール）するとメモもすべて消えます。"
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmSkip = false; onDone() }) { Text("わかってはじめる") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSkip = false }) { Text("もどる") }
            },
        )
    }
}
