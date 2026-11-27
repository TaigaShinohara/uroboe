package app.uroboe

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 実機なし検証の標準（2026-11-23）：画面遷移と操作のコールバック配線を JVM 上で確認する。
 * スワイプそのものの判定は既存の単体テスト（MemoLogicTest）と実機確認に委ね、
 * ここでは「タップで正しい行き先につながるか」を見る。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-420dpi")
class UiInteractionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * v0.6.0 の回帰防止：画面振り分け（AnimatedContent）にホームの受け皿が無く、
     * 起動しても何も表示されない不具合があった。個別画面ではなくアプリ本体
     * （UroboeApp）を組み立てて、ホームに実際に内容が出ることを確認する。
     */
    @Test
    fun app_home_rendersContent() {
        val context = composeRule.activity
        context.getSharedPreferences("uroboe_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("onboarded", true).apply()
        val store = MemoStore(context)
        composeRule.setContent {
            UroboeTheme {
                UroboeApp(store)
            }
        }
        composeRule.onNodeWithText("うろぼえ").assertIsDisplayed()
        composeRule.onNodeWithText("まだメモがありません").assertIsDisplayed()
    }

    @Test
    fun home_tapCard_opensDetail() {
        var openedId: Long? = null
        composeRule.setContent {
            UroboeTheme {
                SingleScreen(
                    data = StoreData(TestFixtures.sampleMemos()),
                    now = TestFixtures.NOW,
                    saveFailed = false,
                    onOpenDetail = { openedId = it }, onDefer = {}, onMoveToSomeday = {},
                    onMoveToActive = {}, onRequestDelete = {},
                    onWillDo = {}, onComplete = {},
                    onWipe = {}, onAbout = {}, onExport = {},
                )
            }
        }
        composeRule.onNodeWithText("Devinの契約をどうするか決める").performClick()
        assertEquals(TestFixtures.willDoMemo().id, openedId)
    }

    @Test
    fun home_reunionCard_restartAndDismissCallbacks() {
        var restartedId: Long? = null
        var dismissedId: Long? = null
        val reunionMemo = TestFixtures.reunionMemo()
        composeRule.setContent {
            UroboeTheme {
                SingleScreen(
                    data = StoreData(TestFixtures.sampleMemos() + reunionMemo),
                    now = TestFixtures.NOW,
                    saveFailed = false,
                    reunion = MemoLogic.Reunion(reunionMemo, MemoLogic.ReunionKind.DAYS_100),
                    onReunionRestart = { restartedId = it.id },
                    onReunionDismiss = { dismissedId = it.id },
                    onOpenDetail = {}, onDefer = {}, onMoveToSomeday = {},
                    onMoveToActive = {}, onRequestDelete = {},
                    onWillDo = {}, onComplete = {},
                    onWipe = {}, onAbout = {}, onExport = {},
                )
            }
        }
        composeRule.onNodeWithText("そろそろやろうに戻す").performClick()
        assertEquals(reunionMemo.id, restartedId)
        composeRule.onNodeWithText("とじる").performClick()
        assertEquals(reunionMemo.id, dismissedId)
    }

    @Test
    fun home_otherMenu_exportIsReachable() {
        var exported = false
        composeRule.setContent {
            UroboeTheme {
                SingleScreen(
                    data = StoreData(TestFixtures.sampleMemos()),
                    now = TestFixtures.NOW,
                    saveFailed = false,
                    onOpenDetail = {}, onDefer = {}, onMoveToSomeday = {},
                    onMoveToActive = {}, onRequestDelete = {},
                    onWillDo = {}, onComplete = {},
                    onWipe = {}, onAbout = {}, onExport = { exported = true },
                )
            }
        }
        composeRule.onNodeWithText("その他").performClick()
        composeRule.onNodeWithText("データを書き出す").assertIsDisplayed().performClick()
        assertTrue(exported)
    }

    @Test
    fun write_autoSave_createUpdateDiscard() {
        val created = mutableListOf<String>()
        val updated = mutableListOf<Pair<Long, String>>()
        val discarded = mutableListOf<Long>()
        composeRule.setContent {
            UroboeTheme {
                WriteScreen(
                    onDismiss = {},
                    onCreate = { body -> created.add(body); 42L },
                    onUpdate = { id, body -> updated.add(id to body) },
                    onDiscardNew = { id -> discarded.add(id) },
                )
            }
        }
        // 編集可能ノードを直接掴む（ヒント文字で探すと再構成でノードが入れ替わり入力に失敗する）
        composeRule.onNode(hasSetTextAction()).performTextInput("買い物")
        assertEquals(listOf("買い物"), created)
        composeRule.onNode(hasSetTextAction()).performTextInput("リスト")
        assertTrue(updated.isNotEmpty())
        assertEquals(42L to "買い物リスト", updated.last())
        // 全部消すと作りかけは破棄される
        composeRule.onNode(hasSetTextAction()).performTextClearance()
        assertEquals(listOf(42L), discarded)
    }

    @Test
    fun home_longPressCard_opensMenuAndMovesToSomeday() {
        var somedayId: Long? = null
        composeRule.setContent {
            UroboeTheme {
                SingleScreen(
                    data = StoreData(TestFixtures.sampleMemos()),
                    now = TestFixtures.NOW,
                    saveFailed = false,
                    onOpenDetail = {}, onDefer = {},
                    onMoveToSomeday = { somedayId = it.id },
                    onMoveToActive = {}, onRequestDelete = {},
                    onWillDo = {}, onComplete = {},
                    onWipe = {}, onAbout = {}, onExport = {},
                )
            }
        }
        // v0.6.2：スワイプ廃止。長押しでメニューが開き、「いつかやればいいへ」で保留へ回る
        composeRule.onNodeWithText("確定申告の領収書をまとめる")
            .performSemanticsAction(SemanticsActions.OnLongClick)
        composeRule.onNodeWithText("いつかやればいいへ").performClick()
        assertEquals(TestFixtures.dueMemo().id, somedayId)
    }

    @Test
    fun onboarding_advancesAndFinishes() {
        var done = false
        composeRule.setContent {
            UroboeTheme {
                OnboardingScreen(onDone = { done = true })
            }
        }
        composeRule.onNodeWithText("書くだけでいい").assertIsDisplayed()
        composeRule.onNodeWithText("つぎへ").performClick()
        composeRule.onNodeWithText("つぎへ").performClick()
        composeRule.onNodeWithText("つぎへ").performClick()
        composeRule.onNodeWithText("はじめる").performClick()
        assertTrue(done)
    }
}
