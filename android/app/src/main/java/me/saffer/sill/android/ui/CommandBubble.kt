package me.saffer.sill.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DesktopMac
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.saffer.sill.android.client.SillClient
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** What the arc's six buttons do, in the order BubbleGeometry lays them out. */
private enum class ArcAction(val label: String) { APPS("Apps"), WINDOWS("Janelas"), MENUS("Menu do Mac"), SCALE("Texto"), KEYBOARD("Teclado"), SETTINGS("Ajustes") }

/**
 * The stream's controls as one bubble instead of a bar at the top (proposal C of the Fold redesign):
 * a 56 dp bubble the person drags to where the thumb reaches and that rests on the nearest edge, half
 * transparent until touched; a tap opens an arc of the six commands toward the inside of the screen
 * (it closes after 6 s, a ring counting down), Windows opens a sheet of thumbnails, and each panel
 * opens beside the bubble. Over the whole picture: only the bubble takes touches that are not the
 * picture's own.
 */
@Composable
fun CommandBubbleLayer(
    client: SillClient,
    overlay: Overlay,
    keyboardUp: Boolean,
    onOpen: (Overlay) -> Unit,
    onCloseOverlay: () -> Unit,
    onKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
    panel: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val d = density.density
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var place by remember { mutableStateOf(BubblePlace.decode(client.uiPref("bubblePlace")) ?: BubblePlace.Default) }
    var arcOpen by remember { mutableStateOf(false) }
    var sheetOpen by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var awake by remember { mutableStateOf(true) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    val bubble = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var placed by remember { mutableStateOf(false) }
    val sheetHeight = 232f

    // The area the bubble may rest in: above the sheet while it is open.
    val areaW = area.width / d
    val areaH = (area.height / d) - if (sheetOpen) sheetHeight + 12f else 0f
    LaunchedEffect(place, area, sheetOpen, dragging) {
        if (area == IntSize.Zero || dragging) return@LaunchedEffect
        val (cx, cy) = BubbleGeometry.center(place, areaW, areaH)
        val target = Offset((cx - BubbleGeometry.BUBBLE / 2f) * d, (cy - BubbleGeometry.BUBBLE / 2f) * d)
        if (!placed) { bubble.snapTo(target); placed = true }
        else bubble.animateTo(target, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
    }

    // Half transparent after a moment alone, so the picture is not covered.
    LaunchedEffect(awake, arcOpen, overlay, sheetOpen, dragging) {
        if (awake && !arcOpen && overlay == Overlay.NONE && !sheetOpen && !dragging) { delay(1400); awake = false }
    }
    // The arc closes by itself after 6 s; its ring counts it down.
    val ring = remember { Animatable(1f) }
    LaunchedEffect(arcOpen) {
        if (arcOpen) { ring.snapTo(1f); ring.animateTo(0f, tween(6000, easing = LinearEasing)); arcOpen = false }
    }
    // A window picked from the sheet closes it.
    var lastActive by remember { mutableStateOf(client.active) }
    LaunchedEffect(client.active) { if (client.active != lastActive) { lastActive = client.active; sheetOpen = false } }

    val progress by animateFloatAsState(if (arcOpen) 1f else 0f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow), label = "arc")
    val bubbleAlpha by animateFloatAsState(if (awake || arcOpen || dragging || overlay != Overlay.NONE || sheetOpen) 1f else 0.4f, label = "bubbleAlpha")
    val currentOverlay by rememberUpdatedState(overlay)
    val closeOverlay by rememberUpdatedState(onCloseOverlay)

    val (cxDp, cyDp) = if (area == IntSize.Zero) 0f to 0f
    else (bubble.value.x / d + BubbleGeometry.BUBBLE / 2f) to (bubble.value.y / d + BubbleGeometry.BUBBLE / 2f)

    Box(modifier.fillMaxSize().onSizeChanged { area = it }) {
        // A touch outside the arc closes it.
        if (arcOpen) Box(Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) { arcOpen = false })

        AnimatedVisibility(
            sheetOpen, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
        ) {
            Column(
                Modifier.padding(12.dp).fillMaxWidth().height(sheetHeight.dp)
                    .clip(RoundedCornerShape(24.dp)).background(SillColors.surface).border(1.dp, SillColors.outline, RoundedCornerShape(24.dp)),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(SillColors.outline))
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Janelas do Mac", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { sheetOpen = false }) { Text("Concluir") }
                }
                Strip(client, Modifier.fillMaxWidth().height(132.dp).padding(horizontal = 14.dp))
            }
        }

        if (overlay != Overlay.NONE && area != IntSize.Zero) {
            AnchoredPanel(place, cxDp * d, cyDp * d, (BubbleGeometry.BUBBLE / 2f + 12f) * d, d) { panel() }
        }

        // The arc: six buttons that leave the bubble toward the inside of the screen, each with its name.
        if (progress > 0.01f && area != IntSize.Zero) {
            ArcAction.entries.forEachIndexed { i, action ->
                val (dx, dy) = BubbleGeometry.arcOffset(i, place.edge)
                val half = BubbleGeometry.ARC_BUTTON / 2f
                val enabled = action != ArcAction.MENUS || client.hasMenus
                val lit = when (action) {
                    ArcAction.APPS -> overlay == Overlay.APPS
                    ArcAction.WINDOWS -> sheetOpen
                    ArcAction.MENUS -> overlay == Overlay.MENUS
                    ArcAction.SCALE -> overlay == Overlay.SCALE
                    ArcAction.KEYBOARD -> keyboardUp
                    ArcAction.SETTINGS -> overlay == Overlay.SETTINGS
                }
                Box(
                    Modifier.offset { IntOffset(((cxDp + dx * progress - half) * d).roundToInt(), ((cyDp + dy * progress - half) * d).roundToInt()) }
                        .alpha(progress.coerceIn(0f, 1f) * if (enabled) 1f else 0.4f)
                        .size(BubbleGeometry.ARC_BUTTON.dp).clip(CircleShape)
                        .background(if (lit) SillColors.accent.copy(alpha = 0.28f) else SillColors.surfaceHigh)
                        .border(1.dp, if (lit) SillColors.accent else SillColors.outline, CircleShape)
                        .clickable(enabled = enabled, onClickLabel = action.label) {
                            arcOpen = false
                            when (action) {
                                ArcAction.APPS -> { sheetOpen = false; onOpen(Overlay.APPS) }
                                ArcAction.WINDOWS -> { closeOverlay(); sheetOpen = !sheetOpen }
                                ArcAction.MENUS -> { sheetOpen = false; onOpen(Overlay.MENUS) }
                                ArcAction.SCALE -> { sheetOpen = false; onOpen(Overlay.SCALE) }
                                ArcAction.KEYBOARD -> { sheetOpen = false; onKeyboard() }
                                ArcAction.SETTINGS -> { sheetOpen = false; onOpen(Overlay.SETTINGS) }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val tint = if (lit) SillColors.accent else Color.White
                    when (action) {
                        ArcAction.SCALE -> Text("Aa", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = tint)
                        else -> Icon(arcIcon(action), action.label, Modifier.size(24.dp), tint = tint)
                    }
                }
                // The name, outward from the button: to its right on a button that points right, to its left when
                // it points left, above or below one that points up or down.
                val r = Math.toRadians(BubbleGeometry.angleDegrees(i, place.edge))
                val cs = cos(r).toFloat(); val sn = sin(r).toFloat()
                val lx = cxDp + cs * (BubbleGeometry.RADIUS + 34f) * progress
                val ly = cyDp + sn * (BubbleGeometry.RADIUS + 34f) * progress
                val ax = if (cs > 0.35f) 0f else if (cs < -0.35f) 1f else 0.5f
                val ay = if (kotlin.math.abs(cs) > 0.35f) 0.5f else if (sn < 0f) 1f else 0f
                Text(
                    action.label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White,
                    modifier = Modifier
                        .alpha((progress * progress).coerceIn(0f, 1f) * if (enabled) 1f else 0.4f)
                        .layout { m, c ->
                            val p = m.measure(c)
                            val x = (lx * d - p.width * ax).coerceIn(6f * d, (area.width - 6f * d - p.width).coerceAtLeast(6f * d))
                            val y = (ly * d - p.height * ay).coerceIn(6f * d, (area.height - 6f * d - p.height).coerceAtLeast(6f * d))
                            layout(p.width, p.height) { p.place(x.roundToInt(), y.roundToInt()) }
                        }
                        .clip(RoundedCornerShape(12.dp)).background(Color(0xF0111214)).border(1.dp, SillColors.outline, RoundedCornerShape(12.dp))
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                )
            }
        }

        // The bubble itself.
        Box(
            Modifier.offset { IntOffset(bubble.value.x.roundToInt(), bubble.value.y.roundToInt()) }
                .alpha(bubbleAlpha).size(BubbleGeometry.BUBBLE.dp),
        ) {
            if (arcOpen) Canvas(Modifier.size((BubbleGeometry.BUBBLE + 18f).dp).align(Alignment.Center)) {
                val stroke = 2.5f * d
                drawArc(
                    SillColors.accent.copy(alpha = 0.75f), startAngle = -90f, sweepAngle = 360f * ring.value, useCenter = false,
                    topLeft = Offset(stroke / 2f, stroke / 2f), size = Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Box(
                Modifier.fillMaxSize().clip(CircleShape).background(SillColors.surfaceHigh)
                    .border(1.5.dp, SillColors.accent.copy(alpha = 0.45f), CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            awake = true
                            when {
                                currentOverlay != Overlay.NONE -> closeOverlay()
                                sheetOpen -> sheetOpen = false
                                else -> arcOpen = !arcOpen
                            }
                        })
                    }
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { awake = true; dragging = true; arcOpen = false; haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                            onDrag = { change, amount -> change.consume(); scope.launch { bubble.snapTo(bubble.value + amount) } },
                            onDragEnd = {
                                val cx = (bubble.value.x / d) + BubbleGeometry.BUBBLE / 2f
                                val cy = (bubble.value.y / d) + BubbleGeometry.BUBBLE / 2f
                                place = BubbleGeometry.snap(cx, cy, areaW, areaH)
                                client.setUiPref("bubblePlace", place.encode())
                                dragging = false
                            },
                            onDragCancel = { dragging = false },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                val open = arcOpen || overlay != Overlay.NONE || sheetOpen
                Icon(
                    if (open) Icons.Filled.Close else Icons.Filled.DesktopMac, if (open) "Fechar" else "Controles do Sill",
                    Modifier.size(26.dp), tint = SillColors.accent,
                )
            }
        }
    }
}

private fun arcIcon(a: ArcAction): ImageVector = when (a) {
    ArcAction.APPS -> Icons.Filled.Apps
    ArcAction.WINDOWS -> Icons.Filled.ViewCarousel
    ArcAction.MENUS -> Icons.Filled.Menu
    ArcAction.KEYBOARD -> Icons.Filled.Keyboard
    ArcAction.SETTINGS -> Icons.Filled.Tune
    ArcAction.SCALE -> Icons.Filled.Tune
}

/**
 * A panel beside the bubble: to its right on the left edge, to its left on the right edge, below or above
 * it on the top and bottom edges, kept inside the area and centred on the bubble along the edge.
 */
@Composable
private fun AnchoredPanel(place: BubblePlace, cx: Float, cy: Float, gap: Float, density: Float, content: @Composable () -> Unit) {
    val margin = 12f * density
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val w = constraints.maxWidth; val h = constraints.maxHeight
        val maxW = when (place.edge) {
            BubbleEdge.LEFT -> w - cx - gap - margin
            BubbleEdge.RIGHT -> cx - gap - margin
            else -> w - 2 * margin
        }.toInt().coerceAtLeast(0)
        val maxH = when (place.edge) {
            BubbleEdge.TOP -> (h - cy - gap - margin).toInt()
            BubbleEdge.BOTTOM -> (cy - gap - margin).toInt()
            else -> (h - 2 * margin).toInt()
        }.coerceAtLeast(0)
        val p = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0, maxWidth = maxW.coerceAtLeast(0), maxHeight = maxH))
        val x: Float; val y: Float
        when (place.edge) {
            BubbleEdge.LEFT -> { x = cx + gap; y = (cy - p.height / 2f).coerceIn(margin, (h - margin - p.height).coerceAtLeast(margin)) }
            BubbleEdge.RIGHT -> { x = cx - gap - p.width; y = (cy - p.height / 2f).coerceIn(margin, (h - margin - p.height).coerceAtLeast(margin)) }
            BubbleEdge.TOP -> { x = (cx - p.width / 2f).coerceIn(margin, (w - margin - p.width).coerceAtLeast(margin)); y = cy + gap }
            BubbleEdge.BOTTOM -> { x = (cx - p.width / 2f).coerceIn(margin, (w - margin - p.width).coerceAtLeast(margin)); y = cy - gap - p.height }
        }
        layout(w, h) { p.place(x.roundToInt(), y.roundToInt()) }
    }
}
