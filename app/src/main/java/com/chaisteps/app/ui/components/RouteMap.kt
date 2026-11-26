package com.chaisteps.app.ui.components

import android.graphics.Paint
import android.view.View
import com.chaisteps.app.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.chaisteps.app.data.RoutePoint
import com.chaisteps.app.ui.theme.Accents
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import kotlin.math.cos

/** Tests turn real map tiles off and get the vector sketch instead. */
val LocalMapsEnabled = compositionLocalOf { true }

/**
 * Shows a GPS route on an OpenStreetMap map. With [follow] the map keeps the latest point in view
 * (live walk); otherwise it zooms to fit the whole route.
 */
@Composable
fun RouteMap(segments: List<List<RoutePoint>>, modifier: Modifier = Modifier, follow: Boolean = false, interactive: Boolean = true) {
    val shape = MaterialTheme.shapes.large
    if (!LocalMapsEnabled.current) {
        RouteSketch(segments, modifier.clip(shape))
        return
    }
    val context = LocalContext.current
    val routeColor = Accents.route.toArgb()
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 3.0
            maxZoomLevel = 20.0
            controller.setZoom(16.0)
            setTag(R.id.map_fitted, false)
        }
    }
    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }
    AndroidView(
        factory = { mapView },
        modifier = modifier.clip(shape),
        update = { map ->
            map.setOnTouchListener(if (interactive) null else View.OnTouchListener { _, _ -> true })
            map.overlays.removeAll { it is Polyline }
            segments.filter { it.size >= 2 }.forEach { seg ->
                map.overlays.add(
                    Polyline(map).apply {
                        setPoints(seg.map { GeoPoint(it.lat, it.lon) })
                        outlinePaint.color = routeColor
                        outlinePaint.strokeWidth = 12f
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                        outlinePaint.isAntiAlias = true
                    },
                )
            }
            val points = segments.flatten()
            if (points.isNotEmpty()) {
                if (follow) {
                    val last = points.last()
                    map.controller.animateTo(GeoPoint(last.lat, last.lon))
                } else if (map.getTag(R.id.map_fitted) != true) {
                    map.setTag(R.id.map_fitted, true)
                    val box = boundingBox(points)
                    map.addOnFirstLayoutListener { _, _, _, _, _ -> map.zoomToBoundingBox(box, false, 64) }
                    if (map.width > 0) map.zoomToBoundingBox(box, false, 64)
                }
            }
            map.invalidate()
        },
    )
}

private fun boundingBox(points: List<RoutePoint>): BoundingBox {
    var n = points.maxOf { it.lat }
    var s = points.minOf { it.lat }
    var e = points.maxOf { it.lon }
    var w = points.minOf { it.lon }
    // Avoid a zero-size box (single point) which osmdroid can't zoom to.
    val minSpan = 0.002
    if (n - s < minSpan) { val c = (n + s) / 2; n = c + minSpan / 2; s = c - minSpan / 2 }
    if (e - w < minSpan) { val c = (e + w) / 2; e = c + minSpan / 2; w = c - minSpan / 2 }
    return BoundingBox(n, e, s, w)
}

/** Vector drawing of a route without map tiles (offline / tests / thumbnails). */
@Composable
fun RouteSketch(segments: List<List<RoutePoint>>, modifier: Modifier = Modifier) {
    val bg = MaterialTheme.colorScheme.surfaceContainerHighest
    val start = MaterialTheme.colorScheme.secondary
    val end = MaterialTheme.colorScheme.tertiary
    Box(modifier.background(bg)) {
        Canvas(Modifier.fillMaxSize()) {
            val pts = segments.flatten()
            if (pts.isEmpty()) return@Canvas
            val pad = 16.dp.toPx()
            val latC = pts.map { it.lat }.average()
            val kx = cos(Math.toRadians(latC))
            val xs = pts.map { it.lon * kx }
            val ys = pts.map { it.lat }
            val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
            val span = maxOf(maxX - minX, maxY - minY).takeIf { it > 0 } ?: 1e-4
            val scale = minOf(size.width - 2 * pad, size.height - 2 * pad) / span
            val ox = (size.width - (maxX - minX) * scale) / 2
            val oy = (size.height - (maxY - minY) * scale) / 2
            fun map(p: RoutePoint) = Offset(
                (ox + (p.lon * kx - minX) * scale).toFloat(),
                (size.height - oy - (p.lat - minY) * scale).toFloat(),
            )
            segments.filter { it.isNotEmpty() }.forEach { seg ->
                val path = Path()
                seg.forEachIndexed { i, p -> val o = map(p); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
                drawPath(path, Accents.route, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            drawCircle(start, 6.dp.toPx(), map(pts.first()))
            drawCircle(end, 6.dp.toPx(), map(pts.last()))
        }
    }
}
