package app.uroboe

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 実機なし検証の標準（2026-11-23）：主要画面を JVM 上で描画し、
 * スクリーンショット（Roborazzi のゴールデン記録／照合）と
 * セマンティクス構造チェック（主要ノードが表示され、高さ 0 に潰れていないこと）で
 * レイアウト崩壊・見切れを交付前に捕まえる。
 *
 * 撮影は Roborazzi core の View.captureRoboImage(file) を直接使う
 * （RoborazziRule／Compose 拡張経由はこの環境では動かなかったため）。
 * 画素レベルの真っ白チェックは Robolectric の描画経路差で空振りが続いたため、
 * レイアウト結果（bounds）で判定する方式に切り替えた。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-420dpi")
class ScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** 実アプリ（MainActivity）と同じテーマと紙背景を再現する（v0.6.0：UroboeTheme が正本） */
    @Composable
    private fun AppShell(content: @Composable () -> Unit) {
        UroboeTheme {
            Box(Modifier.fillMaxSize().background(Palette.Paper)) { content() }
        }
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        composeRule.activity.window.decorView.captureRoboImage(
            file = File("src/test/screenshots/$name.png")
        )
    }

    /** 指定テキストのノードが表示され、高さ 0 に潰れていないことを確認する */
    private fun assertNodeHealthy(text: String, substring: Boolean = false) {
        val node = composeRule.onNodeWithText(text, substring = substring)
            .assertIsDisplayed()
            .fetchSemanticsNode()
        assertTrue("ノードの高さが0です（レイアウト崩壊の疑い）: $text", node.boundsInRoot.height > 0f)
        assertTrue("ノードの幅が0です（レイアウト崩壊の疑い）: $text", node.boundsInRoot.width > 0f)
    }

    @Test
    fun home_withMemos() {
        composeRule.setContent {
            AppShell {
                SingleScreen(
                    data = StoreData(TestFixtures.sampleMemos()),
                    now = TestFixtures.NOW,
                    saveFailed = false,
                    onOpenDetail = {}, onDefer = {}, onMoveToSomeday = {},
                    onMoveToActive = {}, onRequestDelete = {},
                    onWipe = {}, onAbout = {}, onExport = {},
                )
            }
        }
        capture("home_with_memos")
        assertNodeHealthy("そろそろやろう")
        assertNodeHealthy("Devinの契約をどうするか決める")
        assertNodeHealthy("いつか行きたい温泉リストを作る")
    }

    @Test
    fun home_empty() {
        composeRule.setContent {
            AppShell {
                SingleScreen(
                    data = StoreData(emptyList()),
                    now = TestFixtures.NOW,
                    saveFailed = false,
                    onOpenDetail = {}, onDefer = {}, onMoveToSomeday = {},
                    onMoveToActive = {}, onRequestDelete = {},
                    onWipe = {}, onAbout = {}, onExport = {},
                )
            }
        }
        capture("home_empty")
        assertNodeHealthy("まだメモがありません")
    }

    @Test
    fun onboarding_firstPage() {
        composeRule.setContent {
            AppShell {
                OnboardingScreen(onDone = {})
            }
        }
        capture("onboarding_first")
        assertNodeHealthy("書くだけでいい")
        assertNodeHealthy("つぎへ")
    }

    @Test
    fun writeScreen() {
        composeRule.setContent {
            AppShell {
                WriteScreen(
                    onDismiss = {},
                    onCreate = { 1L },
                    onUpdate = { _, _ -> },
                    onDiscardNew = {},
                )
            }
        }
        capture("write")
        val field = composeRule.onNode(hasSetTextAction()).assertIsDisplayed().fetchSemanticsNode()
        assertTrue("入力欄の高さが0です（レイアウト崩壊の疑い）", field.boundsInRoot.height > 0f)
    }

    @Test
    fun editScreen_dueMemo() {
        composeRule.setContent {
            AppShell {
                EditScreen(
                    memo = TestFixtures.dueMemo(),
                    now = TestFixtures.NOW,
                    onBack = {}, onEdit = { _, _, _ -> }, onWillDo = {},
                    onDefer = {}, onComplete = {}, onToggleLowPriority = {},
                    onRequestDelete = {},
                )
            }
        }
        capture("edit_due")
        assertNodeHealthy("忘れかけています", substring = true)
        assertNodeHealthy("そろそろやる")
        assertNodeHealthy("まだやらない")
        assertNodeHealthy("やった")
    }
}
