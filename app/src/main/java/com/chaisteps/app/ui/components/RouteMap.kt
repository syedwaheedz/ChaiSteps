package com.chaisteps.app.ui.components

import android.graphics.Paint
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.chaisteps.app.BuildConfig
import com.chaisteps.app.R
import com.chaisteps.app.data.RoutePoint
import com.chaisteps.app.ui.theme.Accents
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline as OsmPolyline
import kotlin.math.cos

/** Tests turn real maps off and get the vector sketch instead. */
val LocalMapsEnabled = compositionLocalOf { true }

private var playServicesOk: Boolean? = null

/**
 * Shows a GPS route on Google Maps (when the app was built with a Maps API key and Google Play
 * services is available) or on a restyled OpenStreetMap map otherwise (no key needed). With [follow] the camera keeps
 * the latest point in view (live walk); otherwise it fits the whole route.
 */
@Composable
fun RouteMap(segments: List<List<RoutePoint>>, modifier: Modifier = Modifier, follow: Boolean = false, interactive: Boolean = true) {
    if (!LocalMapsEnabled.current) {
        RouteSketch(segments, modifier)
        return
    }
    val context = LocalContext.current
    val useGoogle = remember {
        BuildConfig.HAS_MAPS_KEY && (playServicesOk ?: (GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS).also { playServicesOk = it })
    }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.3f
    if (useGoogle) GoogleRouteMap(segments, modifier, follow, interactive, dark)
    else OsmRouteMap(segments, modifier, follow, interactive, dark)
}

private fun List<List<RoutePoint>>.latLngs() = map { seg -> seg.map { LatLng(it.lat, it.lon) } }

@Composable
private fun GoogleRouteMap(segments: List<List<RoutePoint>>, modifier: Modifier, follow: Boolean, interactive: Boolean, dark: Boolean) {
    val context = LocalContext.current
    val camera = rememberCameraPositionState {
        val first = segments.flatten().firstOrNull()
        position = CameraPosition.fromLatLngZoom(first?.let { LatLng(it.lat, it.lon) } ?: LatLng(0.0, 0.0), if (first == null) 2f else 16f)
    }
    var loaded by remember { mutableStateOf(false) }
    val points = segments.flatten()
    val style = remember(dark) { MapStyleOptions.loadRawResourceStyle(context, if (dark) R.raw.map_style_dark else R.raw.map_style_light) }

    LaunchedEffect(loaded, points.size, follow) {
        if (!loaded || points.isEmpty()) return@LaunchedEffect
        if (follow) {
            val last = points.last()
            val zoom = camera.position.zoom.takeIf { it >= 14f } ?: 17f
            camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(last.lat, last.lon), zoom), 600)
        } else {
            val bounds = LatLngBounds.builder().apply { points.forEach { include(LatLng(it.lat, it.lon)) } }.build()
            runCatching { camera.move(CameraUpdateFactory.newLatLngBounds(bounds, 120)) }
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = camera,
        properties = MapProperties(mapStyleOptions = style, isBuildingEnabled = false, isIndoorEnabled = false),
        uiSettings = MapUiSettings(
            compassEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            zoomControlsEnabled = false,
            indoorLevelPickerEnabled = false,
            scrollGesturesEnabled = interactive,
            zoomGesturesEnabled = interactive,
            rotationGesturesEnabled = interactive,
            tiltGesturesEnabled = false,
        ),
        onMapLoaded = { loaded = true },
    ) {
        val routeColor = Accents.route
        segments.latLngs().filter { it.size >= 2 }.forEach { seg ->
            // A white casing under the line keeps it readable on any map colour.
            Polyline(points = seg, color = Color.White, width = 22f, jointType = JointType.ROUND, startCap = RoundCap(), endCap = RoundCap())
            Polyline(points = seg, color = routeColor, width = 14f, jointType = JointType.ROUND, startCap = RoundCap(), endCap = RoundCap())
        }
        points.firstOrNull()?.let {
            Circle(center = LatLng(it.lat, it.lon), radius = 5.0, fillColor = Accents.steps, strokeColor = Color.White, strokeWidth = 6f)
        }
        points.lastOrNull()?.takeIf { points.size > 1 }?.let {
            Circle(center = LatLng(it.lat, it.lon), radius = 5.0, fillColor = if (follow) Accents.walk else Accents.route, strokeColor = Color.White, strokeWidth = 6f)
        }
    }
}

/** Softer, less saturated OpenStreetMap tiles for a cleaner look. */
private fun lightFilter() = ColorMatrixColorFilter(
    ColorMatrix().apply {
        setSaturation(0.55f)
        postConcat(ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, 10f, 0f, 1f, 0f, 0f, 10f, 0f, 0f, 1f, 0f, 10f, 0f, 0f, 0f, 1f, 0f)))
    },
)

/** Grey, inverted and dimmed tiles: a dark map that matches the dark theme. */
private fun darkFilter() = ColorMatrixColorFilter(
    ColorMatrix().apply {
        setSaturation(0f)
        postConcat(ColorMatrix(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f)))
        postConcat(ColorMatrix().apply { setScale(0.82f, 0.82f, 0.86f, 1f) })
    },
)

@Composable
private fun OsmRouteMap(segments: List<List<RoutePoint>>, modifier: Modifier, follow: Boolean, interactive: Boolean, dark: Boolean) {
    val context = LocalContext.current
    val routeColor = Accents.route.toArgb()
    val mapView = remember {
        MapView(context).apply {
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
    Box(modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                if (map.tileProvider.tileSource.name() != TileSourceFactory.MAPNIK.name()) map.setTileSource(TileSourceFactory.MAPNIK)
                map.overlayManager.tilesOverlay.setColorFilter(if (dark) darkFilter() else lightFilter())
                map.setOnTouchListener(if (interactive) null else View.OnTouchListener { _, _ -> true })
                map.overlays.removeAll { it is OsmPolyline }
                segments.filter { it.size >= 2 }.forEach { seg ->
                    val pts = seg.map { GeoPoint(it.lat, it.lon) }
                    listOf(android.graphics.Color.WHITE to 20f, routeColor to 12f).forEach { (c, w) ->
                        map.overlays.add(
                            OsmPolyline(map).apply {
                                setPoints(pts)
                                outlinePaint.color = c
                                outlinePaint.strokeWidth = w
                                outlinePaint.strokeCap = Paint.Cap.ROUND
                                outlinePaint.strokeJoin = Paint.Join.ROUND
                                outlinePaint.isAntiAlias = true
                            },
                        )
                    }
                }
                val points = segments.flatten()
                if (points.isNotEmpty()) {
                    if (follow) {
                        val last = points.last()
                        map.controller.animateTo(GeoPoint(last.lat, last.lon))
                    } else if (map.getTag(R.id.map_fitted) != true) {
                        map.setTag(R.id.map_fitted, true)
                        val box = boundingBox(points)
                        map.addOnFirstLayoutListener { _, _, _, _, _ -> map.zoomToBoundingBox(box, false, 80) }
                        if (map.width > 0) map.zoomToBoundingBox(box, false, 80)
                    }
                }
                map.invalidate()
            },
        )
        Text(
            "© OpenStreetMap contributors",
            Modifier.align(Alignment.BottomStart).padding(6.dp)
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)).padding(horizontal = 4.dp),
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun boundingBox(points: List<RoutePoint>): BoundingBox {
    var n = points.maxOf { it.lat }
    var s = points.minOf { it.lat }
    var e = points.maxOf { it.lon }
    var w = points.minOf { it.lon }
    val minSpan = 0.002
    if (n - s < minSpan) { val c = (n + s) / 2; n = c + minSpan / 2; s = c - minSpan / 2 }
    if (e - w < minSpan) { val c = (e + w) / 2; e = c + minSpan / 2; w = c - minSpan / 2 }
    return BoundingBox(n, e, s, w)
}

/** Vector drawing of a route without map tiles (list thumbnails, offline, tests). */
@Composable
fun RouteSketch(segments: List<List<RoutePoint>>, modifier: Modifier = Modifier, strokeWidth: Float = 5f) {
    val bg = MaterialTheme.colorScheme.surfaceContainerHigh
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    Box(modifier.background(bg)) {
        Canvas(Modifier.fillMaxSize()) {
            // A faint street-like grid so the sketch reads as a map.
            val step = 28.dp.toPx()
            var x = 0f
            while (x < size.width) { drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
            var y = 0f
            while (y < size.height) { drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f); y += step }

            val pts = segments.flatten()
            if (pts.isEmpty()) return@Canvas
            val pad = minOf(size.width, size.height) * 0.14f
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
                drawPath(path, Color.White, style = Stroke((strokeWidth + 3).dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, Accents.route, style = Stroke(strokeWidth.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            drawCircle(Color.White, (strokeWidth + 2).dp.toPx(), map(pts.first()))
            drawCircle(Accents.steps, strokeWidth.dp.toPx(), map(pts.first()))
            drawCircle(Color.White, (strokeWidth + 2).dp.toPx(), map(pts.last()))
            drawCircle(Accents.route, strokeWidth.dp.toPx(), map(pts.last()))
        }
    }
}
