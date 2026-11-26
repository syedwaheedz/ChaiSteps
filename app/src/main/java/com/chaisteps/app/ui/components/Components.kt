package com.chaisteps.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chaisteps.app.ui.theme.NumberStyle

/** A plain white (or dark grey) card with an optional coloured title. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    accent: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(18.dp),
    ) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(32.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = accent, modifier = Modifier.weight(1f))
                trailing?.invoke(this)
            }
            Spacer(Modifier.height(8.dp))
        }
        content()
    }
}

/** Label above a big value with an optional small unit, no box around it. */
@Composable
fun Metric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    sub: String? = null,
    accent: Color? = null,
    big: Boolean = false,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.Bottom) {
            AutoSizeText(
                value,
                if (big) NumberStyle else MaterialTheme.typography.titleLarge,
                accent ?: MaterialTheme.colorScheme.onSurface,
                Modifier.weight(1f, fill = false),
            )
            if (unit != null) {
                Text(
                    " $unit", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = if (big) 5.dp else 3.dp), maxLines = 1,
                )
            }
        }
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, accent: Color? = null) =
    Metric(label, value, modifier, sub = sub, accent = accent)

/** A row of metrics in equal columns. Each triple is label, value, optional caption. */
@Composable
fun StatRow(vararg tiles: Triple<String, String, String?>, accent: Color? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.forEach { (label, value, sub) -> Metric(label, value, Modifier.weight(1f), sub = sub, accent = accent) }
    }
}

/** Single-line text that shrinks its font until it fits instead of being cut off. */
@Composable
fun AutoSizeText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    var scale by remember(text) { mutableFloatStateOf(1f) }
    var ready by remember(text) { mutableStateOf(false) }
    Text(
        text,
        modifier.drawWithContent { if (ready) drawContent() },
        style = style.copy(fontSize = style.fontSize * scale),
        color = color,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { r ->
            if (r.didOverflowWidth && scale > 0.55f) scale *= 0.9f else ready = true
        },
    )
}

data class Ring(val progress: Float, val color: Color)

/** Concentric progress rings, outermost first. Progress above 1 keeps going with a darker overlap. */
@Composable
fun ActivityRings(
    rings: List<Ring>,
    modifier: Modifier = Modifier,
    size: Dp = 176.dp,
    stroke: Dp = 18.dp,
    gap: Dp = 4.dp,
    content: @Composable () -> Unit = {},
) {
    val animated = rings.map { r -> animateFloatAsState(r.progress.coerceIn(0f, 2f), tween(900), label = "ring").value }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            rings.forEachIndexed { i, ring ->
                val inset = s / 2 + i * (s + gap.toPx())
                val arcSize = Size(this.size.width - 2 * inset, this.size.height - 2 * inset)
                val topLeft = Offset(inset, inset)
                drawArc(ring.color.copy(alpha = 0.18f), -90f, 360f, false, topLeft, arcSize, style = Stroke(s))
                val p = animated[i]
                if (p > 0f) {
                    drawArc(ring.color, -90f, 360f * minOf(p, 1f), false, topLeft, arcSize, style = Stroke(s, cap = StrokeCap.Round))
                    if (p > 1f) {
                        drawArc(Color.Black.copy(alpha = 0.18f), -90f, 360f * (p - 1f), false, topLeft, arcSize, style = Stroke(s, cap = StrokeCap.Round))
                        drawArc(ring.color, -90f, 360f * (p - 1f), false, topLeft, arcSize, style = Stroke(s * 0.86f, cap = StrokeCap.Round))
                    }
                }
            }
        }
        content()
    }
}

@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 168.dp,
    stroke: Dp = 14.dp,
    content: @Composable () -> Unit,
) = ActivityRings(listOf(Ring(progress, color)), modifier, size, stroke, content = content)

/** Coloured dot, label and value for a ring legend. */
@Composable
fun RingLegend(color: Color, label: String, value: String, goal: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
        Row(verticalAlignment = Alignment.Bottom) {
            AutoSizeText(value, MaterialTheme.typography.headlineSmall, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f, fill = false))
            Text(" $goal", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp), maxLines = 1)
        }
    }
}

@Composable
fun BigNumber(value: String, label: String? = null, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = NumberStyle, color = color, textAlign = TextAlign.Center)
        if (label != null) Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun GoalBar(progress: Float, color: Color, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "bar")
    LinearProgressIndicator(
        progress = { animated },
        modifier = modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        color = color,
        trackColor = color.copy(alpha = 0.16f),
        drawStopIndicator = {},
        gapSize = 0.dp,
    )
}

/** Clean bar chart: rounded bars, faint goal line, the last bar in full colour. */
@Composable
fun BarChart(
    values: List<Double>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    labels: List<String>? = null,
    goal: Double? = null,
    highlightLast: Boolean = true,
    description: String = "Bar chart",
) {
    val maxV = maxOf(values.maxOrNull() ?: 0.0, goal ?: 0.0).takeIf { it > 0 } ?: 1.0
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val goalColor = MaterialTheme.colorScheme.outline
    Column(modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            if (values.isEmpty()) return@Canvas
            val n = values.size
            val slot = size.width / n
            val barW = (slot * if (n > 20) 0.6f else 0.5f).coerceIn(2f, 22.dp.toPx())
            val radius = CornerRadius(barW / 2)
            values.forEachIndexed { i, v ->
                val x = i * slot + (slot - barW) / 2
                drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), radius)
                val h = (v / maxV * size.height).toFloat().let { if (v > 0) maxOf(it, barW) else 0f }
                if (h > 0) {
                    val c = if (!highlightLast || i == n - 1) color else color.copy(alpha = 0.55f)
                    drawRoundRect(c, Offset(x, size.height - h), Size(barW, h), radius)
                }
            }
            if (goal != null && goal > 0) {
                val y = (size.height - goal / maxV * size.height).toFloat()
                drawLine(goalColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
        }
        if (labels != null && labels.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            if (labels.size > 8) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    labels.filter { it.isNotEmpty() }.forEach {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            } else Row(Modifier.fillMaxWidth()) {
                labels.forEach {
                    Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
    }
}

/** Smooth line chart with a soft gradient fill. X positions are proportional to [xs]. */
@Composable
fun LineChart(
    xs: List<Double>,
    ys: List<Double>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    goal: Double? = null,
    description: String = "Line chart",
) {
    val goalColor = MaterialTheme.colorScheme.secondary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainer
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        if (ys.isEmpty()) return@Canvas
        val pad = 10.dp.toPx()
        val all = ys + listOfNotNull(goal)
        var lo = all.min()
        var hi = all.max()
        if (hi - lo < 1) { lo -= 0.5; hi += 0.5 }
        val range = hi - lo
        lo -= range * 0.12; hi += range * 0.12
        val x0 = xs.first()
        val xr = (xs.last() - x0).takeIf { it > 0 } ?: 1.0
        fun px(x: Double) = if (xs.size == 1) size.width / 2 else (pad + (x - x0) / xr * (size.width - 2 * pad)).toFloat()
        fun py(y: Double) = (size.height - pad - (y - lo) / (hi - lo) * (size.height - 2 * pad)).toFloat()

        for (i in 0..3) {
            val y = pad + i * (size.height - 2 * pad) / 3
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
        }
        if (goal != null) {
            drawLine(goalColor, Offset(0f, py(goal)), Offset(size.width, py(goal)), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }
        val pts = xs.indices.map { Offset(px(xs[it]), py(ys[it])) }
        if (pts.size > 1) {
            val line = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) {
                    val a = pts[i - 1]; val b = pts[i]
                    val mx = (a.x + b.x) / 2
                    cubicTo(mx, a.y, mx, b.y, b.x, b.y)
                }
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(pts.last().x, size.height)
                lineTo(pts.first().x, size.height)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), color.copy(alpha = 0f))))
            drawPath(line, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
        if (pts.size <= 12) pts.forEach { drawCircle(surface, 5.dp.toPx(), it); drawCircle(color, 3.5.dp.toPx(), it) }
        val last = pts.last()
        drawCircle(color.copy(alpha = 0.2f), 11.dp.toPx(), last)
        drawCircle(surface, 6.dp.toPx(), last)
        drawCircle(color, 4.5.dp.toPx(), last)
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.fillMaxWidth().padding(vertical = 16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Thin divider used between list rows inside cards. */
@Composable
fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}
