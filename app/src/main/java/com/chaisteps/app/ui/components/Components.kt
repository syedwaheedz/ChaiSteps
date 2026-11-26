package com.chaisteps.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chaisteps.app.ui.theme.NumberStyle

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    trailing: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(accent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp)) }
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    trailing?.invoke(this)
                }
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, accent: Color? = null) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        AutoSizeText(value, MaterialTheme.typography.titleLarge, accent ?: MaterialTheme.colorScheme.onSurface)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

@Composable
fun StatRow(vararg tiles: Triple<String, String, String?>, accent: Color? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.forEach { (label, value, sub) -> StatTile(label, value, Modifier.weight(1f), sub, accent) }
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
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "ring")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val arcSize = Size(this.size.width - s, this.size.height - s)
            val topLeft = Offset(s / 2, s / 2)
            drawArc(track, -90f, 360f, false, topLeft, arcSize, style = Stroke(s, cap = StrokeCap.Round))
            drawArc(color, -90f, 360f * animated, false, topLeft, arcSize, style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
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
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(600), label = "bar")
    LinearProgressIndicator(
        progress = { animated },
        modifier = modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        drawStopIndicator = {},
    )
}

/**
 * Simple bar chart. [labels] (optional) are shown under the bars; [goal] draws a dashed line.
 * The last bar is highlighted.
 */
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
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val goalColor = MaterialTheme.colorScheme.outline
    Column(modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            if (values.isEmpty()) return@Canvas
            val n = values.size
            val gap = if (n > 20) 2.dp.toPx() else 6.dp.toPx()
            val barW = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
            val radius = CornerRadius(minOf(barW / 2, 6.dp.toPx()))
            values.forEachIndexed { i, v ->
                val x = i * (barW + gap)
                drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), radius, alpha = 0.5f)
                val h = (v / maxV * size.height).toFloat()
                if (h > 0) {
                    val c = if (!highlightLast || i == n - 1) color else color.copy(alpha = 0.6f)
                    drawRoundRect(c, Offset(x, size.height - h), Size(barW, h), radius)
                }
            }
            if (goal != null && goal > 0) {
                val y = (size.height - goal / maxV * size.height).toFloat()
                drawLine(goalColor, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            }
        }
        if (labels != null && labels.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            if (labels.size > 8) {
                // Too many bars for one label each: spread the non-empty labels across the width.
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

/** Line chart for weight over time. X positions are proportional to [xs] (e.g. epoch days). */
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
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        if (ys.isEmpty()) return@Canvas
        val pad = 8.dp.toPx()
        val all = ys + listOfNotNull(goal)
        var lo = all.min()
        var hi = all.max()
        if (hi - lo < 1) { lo -= 0.5; hi += 0.5 }
        val range = hi - lo
        lo -= range * 0.1; hi += range * 0.1
        val x0 = xs.first()
        val xr = (xs.last() - x0).takeIf { it > 0 } ?: 1.0
        fun px(x: Double) = if (xs.size == 1) size.width / 2 else (pad + (x - x0) / xr * (size.width - 2 * pad)).toFloat()
        fun py(y: Double) = (size.height - pad - (y - lo) / (hi - lo) * (size.height - 2 * pad)).toFloat()

        for (i in 0..3) {
            val y = pad + i * (size.height - 2 * pad) / 3
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f, alpha = 0.6f)
        }
        if (goal != null) {
            drawLine(goalColor, Offset(0f, py(goal)), Offset(size.width, py(goal)), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
        }
        if (ys.size > 1) {
            val line = Path()
            val fill = Path()
            xs.indices.forEach { i ->
                val p = Offset(px(xs[i]), py(ys[i]))
                if (i == 0) { line.moveTo(p.x, p.y); fill.moveTo(p.x, size.height); fill.lineTo(p.x, p.y) } else { line.lineTo(p.x, p.y); fill.lineTo(p.x, p.y) }
            }
            fill.lineTo(px(xs.last()), size.height)
            fill.close()
            drawPath(fill, color.copy(alpha = 0.12f))
            drawPath(line, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
        val dot = if (ys.size > 12) 0f else 4.dp.toPx()
        xs.indices.forEach { i ->
            val p = Offset(px(xs[i]), py(ys[i]))
            if (dot > 0) {
                drawCircle(surface, dot + 2.dp.toPx(), p)
                drawCircle(color, dot, p)
            }
        }
        val last = Offset(px(xs.last()), py(ys.last()))
        drawCircle(color, 6.dp.toPx(), last)
        drawCircle(surface, 3.dp.toPx(), last)
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
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
