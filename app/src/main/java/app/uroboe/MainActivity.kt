package app.uroboe

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
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
    /** M3 の outline 用の中間トーン（紙色とのコントラスト約3.4:1）。 */
    val Outline = Color(0xFF8A7D68)
}

/**
 * v0.6.0：Material Design 3 のテーマ正本。
 * ブランド色（Palette）を M3 の色の役割（colorScheme）へ割り当て、
 * 文字階層（Typography）と形（Shapes）も M3 のスケールで統一する。
 * error 役割にはブランド方針（赤は使わない）に沿って濃い橙を当てる。
 * 画面側は原則として MaterialTheme の役割経由で色・文字・形を引く。
 */
private val UroboeTypography = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 32.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 27.sp),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 21.sp),
    titleSmall = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp, letterSpacing = 0.8.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp),
    labelMedium = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp),
)

@Composable
fun UroboeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Palette.Pink,
            onPrimary = Color.White,
            primaryContainer = Palette.PinkBg,
            onPrimaryContainer = Palette.Pink,
            secondary = Palette.InkSoft,
            onSecondary = Color.White,
            secondaryContainer = Palette.SomedayCard,
            onSecondaryContainer = Palette.Ink,
            tertiary = Palette.Orange,
            onTertiary = Color.White,
            tertiaryContainer = Palette.OrangeBg,
            onTertiaryContainer = Palette.Orange,
            error = Palette.Orange,
            onError = Color.White,
            errorContainer = Palette.OrangeBg,
            onErrorContainer = Palette.Orange,
            background = Palette.Paper,
            onBackground = Palette.Ink,
            surface = Palette.CardWhite,
            onSurface = Palette.Ink,
            surfaceVariant = Palette.SomedayCard,
            onSurfaceVariant = Palette.InkSoft,
            surfaceContainerLowest = Palette.CardWhite,
            surfaceContainerLow = Palette.Paper,
            surfaceContainer = Palette.SomedayCard,
            surfaceContainerHigh = Palette.Line,
            surfaceContainerHighest = Palette.Line,
            outline = Palette.Outline,
            outlineVariant = Palette.Line,
            inverseSurface = Palette.Ink,
            inverseOnSurface = Palette.Paper,
            inversePrimary = Palette.PinkBg,
        ),
        typography = UroboeTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
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
            UroboeTheme {
                UroboeApp(store)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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

    val detailMemo = detailId?.let { id -> data.memos.firstOrNull { it.id == id } }

    // v0.6.0：画面遷移は M3 モーション（フェード＋わずかな横スライド）でつなぐ
    val screenKey = when {
        showOnboarding -> 0
        writeOpen -> 1
        detailMemo != null -> 2
        else -> 3
    }
    AnimatedContent(
        targetState = screenKey,
        transitionSpec = {
            ((fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it / 10 }) togetherWith
                fadeOut(tween(180))) using SizeTransform(clip = false)
        },
        label = "screenTransition",
    ) { key ->
        when (key) {
            0 -> OnboardingScreen(
                onDone = {
                    prefs.edit().putBoolean("onboarded", true).apply()
                    showOnboarding = false
                },
                canExitToHome = prefs.getBoolean("onboarded", false),
                onExit = { showOnboarding = false },
                onSelectType = { prefs.edit().putInt("user_type", it).apply() },
            )
            // v0.5.1：保存ボタンは廃止し、書く画面も自動保存にした（詳細画面と同方式）
            1 -> WriteScreen(
                onDismiss = { writeOpen = false; now = store.effectiveNow() },
                onCreate = { body -> store.addMemo(body).id },
                onUpdate = { id, body -> store.editBody(id, body, null, null) },
                onDiscardNew = { id -> store.deleteNow(id) },
            )
            2 -> if (detailMemo != null) {
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
                // v0.6.0：タイトルは M3 の TopAppBar へ、追加ボタンは Extended FAB（ラベル付き）へ
                Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("うろぼえ", style = MaterialTheme.typography.headlineSmall) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Palette.Paper,
                        titleContentColor = Palette.Ink,
                    ),
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { writeOpen = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    icon = { Text("＋", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                    text = { Text("メモを書く", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.semantics { contentDescription = "新しいメモを書く" },
                )
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
                // v0.6.0：通知は M3 スナックバーの見た目（暗色面＋明色文字）にし、下からスライドインさせる。
                // 退場アニメーションのために最後の通知を保持しておく（中身が先に消えると空の箱が下がってしまうため）
                var lastNotice by remember { mutableStateOf<UndoNotice?>(null) }
                LaunchedEffect(notice) { if (notice != null) lastNotice = notice }
                AnimatedVisibility(
                    visible = notice != null,
                    enter = slideInVertically(tween(250)) { it } + fadeIn(tween(250)),
                    exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200)),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    lastNotice?.let { current ->
                        Surface(
                            modifier = Modifier
                                .padding(start = 16.dp, end = 16.dp, bottom = 88.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.inverseSurface,
                            shadowElevation = 6.dp,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    end = if (current.snapshot != null) 8.dp else 16.dp,
                                    top = 8.dp,
                                    bottom = 8.dp,
                                ),
                            ) {
                                Text(
                                    current.text,
                                    color = MaterialTheme.colorScheme.inverseOnSurface,
                                    style = MaterialTheme.typography.bodyMedium,
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
                                            color = MaterialTheme.colorScheme.inversePrimary,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }
                                }
                            }
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
                    "「いつかやろう」を「そろそろやろう」に変えるメモ帳。\nバージョン 0.6.0\n\n" +
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
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(countLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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
        shape = MaterialTheme.shapes.large,
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
                    .clip(MaterialTheme.shapes.large)
                    .background(bg)
                    .padding(horizontal = 20.dp),
                contentAlignment = align,
            ) {
                if (dir != SwipeToDismissBoxValue.Settled) {
                    Text(label, color = fg, style = MaterialTheme.typography.labelMedium)
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

    // v0.5.2：本人の指示でメモは二列グリッド表示に変更（見出し類は全幅）
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ヘッダー（アプリ名）は v0.6.0 で M3 の TopAppBar 側へ移した
        if (saveFailed) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        "保存に失敗しました。空き容量を確認し、念のため一番下の「その他」→「データを書き出す」で控えを取ってください。",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        if (data.memos.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 84.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FloatingMemoMotif(centered = true, elevatedTop = true)
                    Spacer(Modifier.height(22.dp))
                    Text("まだメモがありません", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "右下の「メモを書く」から、「いつかやろう」を1行書いてみて",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            // 上段：そろそろやろう（忘却曲線順）
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(
                    text = "そろそろやろう",
                    countLabel = "${activeMemos.size}件",
                    color = Palette.Pink,
                )
            }
            if (activeMemos.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "いま上段にあるメモはありません。右下の「メモを書く」で追加するか、下段から戻せます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
            } else {
                items(activeMemos, key = { "active_${it.id}" }) { memo ->
                    val showDuePill = memo.id in highlightedDueIds
                    SwipeableMemoWrapper(
                        modifier = Modifier.animateItem().fillMaxSize(),
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
                        MemoCardShell(onClick = { onOpenDetail(memo.id) }, modifier = Modifier.fillMaxSize()) {
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
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            pillText,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(5.dp))
                            }
                            Text(
                                memo.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
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
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    // v0.5.3：二列化で横幅が狭くなり1行では本文がほぼ読めないため3行まで出す（本人の指示）
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (memo.isQuiz) {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    "クイズ形式 ・ タップで答えを見る",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // 下段：いつかやればいい（自動浮上なしの保留）
            if (somedayMemos.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionTitle(
                        // v0.5：ヒント文は消したが「自動浮上なし」は下段の役割そのものなので見出しに残す（SPEC §4）
                        text = "いつかやればいい（自動浮上なし）",
                        countLabel = "${somedayMemos.size}件",
                        color = Palette.InkSoft,
                    )
                }
                items(somedayMemos, key = { "someday_${it.id}" }) { memo ->
                    SwipeableMemoWrapper(
                        modifier = Modifier.animateItem().fillMaxSize(),
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
                            modifier = Modifier.fillMaxSize(),
                            containerColor = Palette.SomedayCard,
                            elevationDp = 0f,
                        ) {
                            // v0.5.2：二列化で横幅が狭くなるため、題名と経過日数は横並びから縦積みに変更
                            Text(
                                memo.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // v0.5.3：下段にも本文プレビューを出す（本人の指示）。上段と同じく題名行を除いた残りを2行まで
                            val somedayPreview = if (memo.isQuiz) "" else memo.body.lineSequence()
                                .dropWhile { it.isBlank() }
                                .drop(1)
                                .joinToString("\n")
                                .trim()
                            if (somedayPreview.isNotBlank()) {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    somedayPreview,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                MemoLogic.elapsedLabel(memo.createdAt, now),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // フッター（v0.5：低頻度の管理は「その他」に集約し、常時表示から外した）
        item(span = { GridItemSpan(maxLineSpan) }) {
            var menuOpen by remember { mutableStateOf(false) }
            Spacer(Modifier.height(26.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
            Box(Modifier.padding(top = 6.dp)) {
                TextButton(onClick = { menuOpen = true }) {
                    Text("その他", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // v0.6.0：メニューの面色は M3 標準（surfaceContainer）に任せる
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("このアプリについて") },
                        onClick = { menuOpen = false; onAbout() },
                    )
                    DropdownMenuItem(
                        text = { Text("データを書き出す") },
                        onClick = { menuOpen = false; onExport() },
                    )
                    DropdownMenuItem(
                        text = { Text("データをすべて消す", color = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; onWipe() },
                    )
                }
            }
        }
    }
}

// ---------------- 書く（全画面・Google Keep風の枠なし1欄） ----------------

@OptIn(ExperimentalMaterial3Api::class)
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
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().imePadding()
        ) {
            // v0.6.0：戻るは M3 の TopAppBar に集約
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(
                        onClick = { onDismiss() },
                        modifier = Modifier.semantics { contentDescription = "前の画面に戻る" },
                    ) { Text("←", fontSize = 22.sp, color = Palette.InkSoft) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Palette.Paper,
                    titleContentColor = Palette.Ink,
                ),
            )
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
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
}

private enum class DetailButtonKind { Filled, Tonal, Outlined, Danger }

/**
 * 詳細画面の操作ボタン（v0.6.0）。
 * 「ボタンが何か分からない」という本人の声に応え、主ラベルに
 * 「押すとどうなるか」の一言キャプションを2段で添える。強調は M3 の階層
 * （Filled＝主役／Tonal＝完了／Outlined＝副次／Danger＝削除）に従う。
 */
@Composable
private fun DetailActionButton(
    text: String,
    caption: String,
    onClick: () -> Unit,
    kind: DetailButtonKind,
    modifier: Modifier = Modifier,
) {
    val content: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    val padding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
    when (kind) {
        DetailButtonKind.Filled -> Button(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            contentPadding = padding,
        ) { content() }
        DetailButtonKind.Tonal -> FilledTonalButton(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
            contentPadding = padding,
        ) { content() }
        DetailButtonKind.Outlined -> OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            contentPadding = padding,
        ) { content() }
        DetailButtonKind.Danger -> OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
            contentPadding = padding,
        ) { content() }
    }
}

/** 忘却曲線インターバルの短い表記（1時間／1日／3日／…）。 */
private fun intervalShortLabel(ms: Long): String = when {
    ms <= MemoLogic.HOUR_MS -> "1時間"
    ms < 2 * MemoLogic.DAY_MS -> "1日"
    else -> "${ms / MemoLogic.DAY_MS}日"
}

// ---------------- メモを開く（全画面・閲覧と編集・明示的な区切り操作） ----------------

@OptIn(ExperimentalMaterial3Api::class)
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

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().imePadding()
        ) {
            // v0.6.0：戻る／元に戻すは M3 の TopAppBar に集約
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = "前の画面に戻る" },
                    ) { Text("←", fontSize = 22.sp, color = Palette.InkSoft) }
                },
                actions = {
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Palette.Paper,
                    titleContentColor = Palette.Ink,
                ),
            )
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
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
                // v0.6.0：各ボタンに「押すとどうなるか」の一言を添えた（本人の「何か良くわからない」への対応）。
                // 主役は「そろそろやる／まだやらない」の選択（v0.4.3）。完了は「やった」。
                val committed = memo.willDoAt != null
                // 「まだやらない」を押した後に次に浮上する時期（節目到達時は次ステージの間隔、未到達時は今の間隔）
                val nextStage = if (MemoLogic.isDueForReview(memo, now)) {
                    (memo.reviewStage + 1).coerceAtMost(MemoLogic.FORGETTING_INTERVALS_MS.lastIndex)
                } else {
                    memo.reviewStage
                }
                val deferCaption = "次は約${intervalShortLabel(MemoLogic.FORGETTING_INTERVALS_MS[nextStage])}後に浮上"
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (committed) {
                        DetailActionButton(
                            text = "やった",
                            caption = "一覧から消える",
                            onClick = onComplete,
                            kind = DetailButtonKind.Filled,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        DetailActionButton(
                            text = "そろそろやる",
                            caption = "いちばん上に固定",
                            onClick = onWillDo,
                            kind = DetailButtonKind.Filled,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    DetailActionButton(
                        text = "まだやらない",
                        caption = if (committed) "固定を外して後まわし" else deferCaption,
                        onClick = onDefer,
                        kind = DetailButtonKind.Outlined,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DetailActionButton(
                        text = "下段へ",
                        caption = "いつかやればいいへ",
                        onClick = { onToggleLowPriority(true) },
                        kind = DetailButtonKind.Outlined,
                        modifier = Modifier.weight(1f),
                    )
                    if (!committed) {
                        DetailActionButton(
                            text = "やった",
                            caption = "一覧から消える",
                            onClick = onComplete,
                            kind = DetailButtonKind.Tonal,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DetailActionButton(
                        text = "上段へ戻す",
                        caption = "上段の先頭に戻る",
                        onClick = { onToggleLowPriority(false) },
                        kind = DetailButtonKind.Tonal,
                        modifier = Modifier.weight(1f),
                    )
                    DetailActionButton(
                        text = "削除する",
                        caption = "完全削除・戻せない",
                        onClick = onRequestDelete,
                        kind = DetailButtonKind.Danger,
                        modifier = Modifier.weight(1f),
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
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            // v0.6.0：ページ切り替えは M3 モーション（フェード＋わずかなスライド）でつなぐ
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    (fadeIn(tween(250)) + slideInHorizontally(tween(250)) { it / 12 }) togetherWith
                        fadeOut(tween(150))
                },
                label = "onboardingPage",
            ) { currentPage ->
            Column {
            FloatingMemoMotif(centered = false, elevatedTop = pages[currentPage].third)
            Spacer(Modifier.height(28.dp))
            if (currentPage == 0) {
                Text(
                    "「いつかやろう」を「そろそろやろう」に",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(10.dp))
            }
            Text(pages[currentPage].first, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))
            if (currentPage == 1) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    types.forEachIndexed { i, type ->
                        val selected = selectedType == i
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(MaterialTheme.shapes.large)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    MaterialTheme.shapes.large,
                                )
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
                                style = MaterialTheme.typography.titleMedium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                AnimatedVisibility(visible = selectedType >= 0) {
                    val idx = selectedType.coerceIn(0, types.lastIndex)
                    Text(types[idx].second, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (selectedType < 0) {
                    Text("当てはまるものを、タップしてみて。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text(pages[currentPage].second, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            }
            }
            Spacer(Modifier.height(34.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pages.indices.forEach { i ->
                    // v0.6.0：ドットの伸縮もアニメーションさせる
                    val dotWidth by animateDpAsState(
                        targetValue = if (i == page) 22.dp else 8.dp,
                        animationSpec = tween(250),
                        label = "pageDot$i",
                    )
                    Box(
                        Modifier.width(dotWidth).height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (i == page) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            if (page < pages.lastIndex) {
                Button(
                    onClick = { page = (page + 1).coerceAtMost(pages.lastIndex) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("つぎへ") }
                TextButton(onClick = { confirmSkip = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("スキップ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("はじめる") }
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
