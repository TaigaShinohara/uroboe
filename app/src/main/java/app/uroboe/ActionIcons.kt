package app.uroboe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * v0.8.2：状態変更アクション専用の線アイコン（本人の希望「この4つは専用アイコンで」）。
 * 依存追加なしで揃えるため、24dp グリッドの単純な線画を Canvas で直接描く。
 * 形は意味から決める：上に固定＝上向き矢印／後まわし＝時計／保留＝しおり／完了＝チェック丸／戻す＝巻き戻し矢印／削除＝ゴミ箱。
 */
enum class ActionIconKind { ToTop, Clock, Bookmark, CheckCircle, Undo, Trash }

@Composable
fun ActionIcon(kind: ActionIconKind, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val u = size.width / 24f
        val sw = 2f * u
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        fun line(a: Offset, b: Offset, w: Float = sw) =
            drawLine(color, a, b, strokeWidth = w, cap = StrokeCap.Round)
        fun poly(points: List<Offset>, close: Boolean = false, w: Float = sw) {
            val path = Path()
            points.forEachIndexed { i, pt -> if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y) }
            if (close) path.close()
            drawPath(path, color, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        when (kind) {
            ActionIconKind.ToTop -> {
                line(p(5f, 4.5f), p(19f, 4.5f), 2.2f * u)
                line(p(12f, 20f), p(12f, 8.5f))
                poly(listOf(p(7f, 13.5f), p(12f, 8.5f), p(17f, 13.5f)))
            }
            ActionIconKind.Clock -> {
                drawCircle(color, radius = 8f * u, center = p(12f, 12f), style = stroke)
                line(p(12f, 12f), p(12f, 7.8f))
                line(p(12f, 12f), p(15.6f, 13.8f))
            }
            ActionIconKind.Bookmark -> {
                poly(listOf(p(7f, 4f), p(17f, 4f), p(17f, 20f), p(12f, 16.4f), p(7f, 20f)), close = true)
            }
            ActionIconKind.CheckCircle -> {
                drawCircle(color, radius = 8f * u, center = p(12f, 12f), style = stroke)
                poly(listOf(p(8.4f, 12.3f), p(11.2f, 15.2f), p(15.8f, 9.2f)))
            }
            ActionIconKind.Undo -> {
                drawArc(
                    color = color,
                    startAngle = 195f,
                    sweepAngle = 235f,
                    useCenter = false,
                    topLeft = p(4.5f, 4.5f),
                    size = Size(15f * u, 15f * u),
                    style = stroke,
                )
                // 矢印の先端（弧の始点＝左上あたりに、左向きの角）
                val tip = p(5.6f, 7.6f)
                poly(listOf(p(10.4f, 5.2f), tip, p(8.6f, 11.6f)))
            }
            ActionIconKind.Trash -> {
                line(p(4.8f, 6.2f), p(19.2f, 6.2f))
                poly(listOf(p(9.8f, 6.2f), p(9.8f, 4.2f), p(14.2f, 4.2f), p(14.2f, 6.2f)))
                poly(listOf(p(6.4f, 6.2f), p(7.4f, 19.8f), p(16.6f, 19.8f), p(17.6f, 6.2f)))
                line(p(10.2f, 10f), p(10.5f, 16.8f))
                line(p(13.8f, 10f), p(13.5f, 16.8f))
            }
        }
    }
}
