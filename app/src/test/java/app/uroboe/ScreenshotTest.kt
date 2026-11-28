package app.uroboe

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
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
                    onWillDo = {}, onComplete = {},
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
    fun home_withReunion() {
        val reunionMemo = TestFixtures.reunionMemo()
        composeRule.setContent {
            AppShell {
                SingleScreen(
                    data = StoreData(TestFixtures.sampleMemos() + reunionMemo),
                    now = TestFixtures.NOW,
                    saveFailed = false,
                    reunion = MemoLogic.Reunion(reunionMemo, MemoLogic.ReunionKind.DAYS_100),
                    onReunionRestart = {}, onReunionDismiss = {},
                    onOpenDetail = {}, onDefer = {}, onMoveToSomeday = {},
                    onMoveToActive = {}, onRequestDelete = {},
                    onWillDo = {}, onComplete = {},
                    onWipe = {}, onAbout = {}, onExport = {},
                )
            }
        }
        capture("home_with_reunion")
        assertNodeHealthy("きょうの再会")
        assertNodeHealthy("確定申告の控えを机の引き出しにしまう")
        assertNodeHealthy("100日前のきみが書いたメモだよ。おかえり。")
        assertNodeHealthy("そろそろやろうに戻す")
        assertNodeHealthy("とじる")
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
                    onWillDo = {}, onComplete = {},
                    onWipe = {}, onAbout = {}, onExport = {},
                )
            }
        }
        capture("home_empty")
        assertNodeHealthy("まだメモがありません")
    }

    /**
     * v0.8.0（M3 Expressive 試行②）：モーフィング型ローディング指標が
     * 潰れずに描画されること（形の画素ではなく、48dp の描画領域で判定する）。
     */
    @Test
    fun expressive_loadingIndicator() {
        composeRule.setContent {
            AppShell {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    MorphingLoadingIndicator()
                }
            }
        }
        capture("expressive_loading")
        val node = composeRule.onNodeWithContentDescription("読み込み中")
            .assertIsDisplayed()
            .fetchSemanticsNode()
        assertTrue("ローディング指標の高さが0です", node.boundsInRoot.height > 0f)
        assertTrue("ローディング指標の幅が0です", node.boundsInRoot.width > 0f)
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

    /**
     * v0.7.1（U-5）：狭幅（360dp）＋フォント拡大（1.3倍）でも、詳細画面の
     * 主ラベルとキャプション（最長の「次は約60日後に浮上」）が欠けずに読めることを確かめる。
     */
    @Test
    @Config(qualifiers = "w360dp-h914dp-420dpi")
    fun editScreen_largeFont_longCaption() {
        val dayMs = 24L * 3_600_000L
        val memo = TestFixtures.dueMemo().copy(
            reviewStage = 5,
            createdAt = TestFixtures.NOW - 40 * dayMs,
            lastReviewedAt = TestFixtures.NOW - 40 * dayMs,
            enteredActiveAt = TestFixtures.NOW - 40 * dayMs,
        )
        composeRule.setContent {
            AppShell {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f),
                ) {
                    EditScreen(
                        memo = memo,
                        now = TestFixtures.NOW,
                        onBack = {}, onEdit = { _, _, _ -> }, onWillDo = {},
                        onDefer = {}, onComplete = {}, onToggleLowPriority = {},
                        onRequestDelete = {},
                    )
                }
            }
        }
        capture("edit_largefont")
        assertNodeHealthy("次は約60日後に浮上", substring = true)
        assertNodeHealthy("まだやらない")
        assertNodeHealthy("やった")
    }
}
