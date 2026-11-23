package app.uroboe

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

    @Test
    fun home_tapCard_opensDetail() {
        var openedId: Long? = null
        composeRule.setContent {
            MaterialTheme {
                SingleScreen(
                    data = StoreData(TestFixtures.sampleMemos()),
                    now = TestFixtures.NOW,
                    saveFailed = false,
                    onOpenDetail = { openedId = it }, onDefer = {}, onMoveToSomeday = {},
                    onMoveToActive = {}, onRequestDelete = {},
                    onWipe = {}, onAbout = {}, onExport = {},
                )
            }
        }
        composeRule.onNodeWithText("Devinの契約をどうするか決める").performClick()
        assertEquals(TestFixtures.willDoMemo().id, openedId)
    }

    @Test
    fun home_otherMenu_exportIsReachable() {
        var exported = false
        composeRule.setContent {
            MaterialTheme {
                SingleScreen(
                    data = StoreData(TestFixtures.sampleMemos()),
                    now = TestFixtures.NOW,
                    saveFailed = false,
                    onOpenDetail = {}, onDefer = {}, onMoveToSomeday = {},
                    onMoveToActive = {}, onRequestDelete = {},
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
            MaterialTheme {
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
    fun onboarding_advancesAndFinishes() {
        var done = false
        composeRule.setContent {
            MaterialTheme {
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
