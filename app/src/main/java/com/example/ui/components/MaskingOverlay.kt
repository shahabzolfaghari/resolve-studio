package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BezierControlPoint
import com.example.model.MaskType
import com.example.model.PowerWindowMask
import com.example.model.defaultBezierPoints
import com.example.ui.theme.*
import kotlin.math.*

private enum class DragHandle {
    CENTER,
    HANDLE_TOP,
    HANDLE_BOTTOM,
    HANDLE_LEFT,
    HANDLE_RIGHT,
    HANDLE_TOP_LEFT,
    HANDLE_TOP_RIGHT,
    HANDLE_BOTTOM_LEFT,
    HANDLE_BOTTOM_RIGHT,
    BEZIER_ANCHOR,
    BEZIER_HANDLE_IN,
    BEZIER_HANDLE_OUT,
    BEZIER_WHOLE,
    NONE
}

/**
 * Interactive 'MaskingOverlay' component placed directly over the video preview.
 * Allows drawing, resizing, feathering, and repositioning elliptical, rectangular,
 * linear, and custom cubic Bezier power windows for localized color grading (DaVinci Resolve style).
 */
@Composable
fun MaskingOverlay(
    mask: PowerWindowMask,
    onMaskChange: (PowerWindowMask) -> Unit,
    modifier: Modifier = Modifier
) {
    if (mask.type == MaskType.NONE) return

    var activeHandle by remember { mutableStateOf(DragHandle.NONE) }
    var activeBezierIndex by remember { mutableStateOf(0) }
    var showQuickToolbar by remember { mutableStateOf(true) }

    val currentMask by rememberUpdatedState(mask)
    val currentOnMaskChange by rememberUpdatedState(onMaskChange)

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(mask.type) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        val m = currentMask
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        if (m.type == MaskType.CUSTOM_BEZIER) {
                            // Insert a new point at double-tap location
                            val pts = m.bezierPoints.toMutableList()
                            val tapX = (tapOffset.x / w).coerceIn(0.05f, 0.95f)
                            val tapY = (tapOffset.y / h).coerceIn(0.05f, 0.95f)
                            val newPoint = BezierControlPoint(
                                x = tapX,
                                y = tapY,
                                handleInX = (tapX - 0.05f).coerceIn(0.02f, 0.98f),
                                handleInY = tapY,
                                handleOutX = (tapX + 0.05f).coerceIn(0.02f, 0.98f),
                                handleOutY = tapY
                            )
                            pts.add(newPoint)
                            currentOnMaskChange(m.copy(bezierPoints = pts, selectedPointIndex = pts.size - 1))
                        } else {
                            // Reset standard mask to default center
                            currentOnMaskChange(m.copy(centerX = 0.5f, centerY = 0.5f, radiusX = 0.3f, radiusY = 0.3f))
                        }
                    },
                    onTap = { tapOffset ->
                        val m = currentMask
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        if (m.type == MaskType.CUSTOM_BEZIER) {
                            val hitRadius = 32.dp.toPx()
                            var hitIndex = -1
                            m.bezierPoints.forEachIndexed { i, pt ->
                                val px = pt.x * w
                                val py = pt.y * h
                                if (sqrt((tapOffset.x - px).pow(2) + (tapOffset.y - py).pow(2)) < hitRadius) {
                                    hitIndex = i
                                }
                            }
                            if (hitIndex != -1) {
                                currentOnMaskChange(m.copy(selectedPointIndex = hitIndex))
                            } else {
                                showQuickToolbar = !showQuickToolbar
                            }
                        } else {
                            showQuickToolbar = !showQuickToolbar
                        }
                    }
                )
            }
            .pointerInput(mask.type) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        val m = currentMask
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val hitRadius = 28.dp.toPx()

                        if (m.type == MaskType.CUSTOM_BEZIER) {
                            val pts = m.bezierPoints
                            fun isHit(px: Float, py: Float) =
                                sqrt((startOffset.x - px).pow(2) + (startOffset.y - py).pow(2)) < hitRadius

                            // 1. Check if clicked an anchor point
                            var foundAnchor = -1
                            pts.forEachIndexed { i, p ->
                                if (isHit(p.x * w, p.y * h)) foundAnchor = i
                            }
                            if (foundAnchor != -1) {
                                activeBezierIndex = foundAnchor
                                activeHandle = DragHandle.BEZIER_ANCHOR
                                currentOnMaskChange(m.copy(selectedPointIndex = foundAnchor))
                                return@detectDragGestures
                            }

                            // 2. Check tangent handles of selected point
                            val selIdx = m.selectedPointIndex.coerceIn(0, pts.size - 1)
                            val selPt = pts[selIdx]
                            if (isHit(selPt.handleInX * w, selPt.handleInY * h)) {
                                activeBezierIndex = selIdx
                                activeHandle = DragHandle.BEZIER_HANDLE_IN
                                return@detectDragGestures
                            }
                            if (isHit(selPt.handleOutX * w, selPt.handleOutY * h)) {
                                activeBezierIndex = selIdx
                                activeHandle = DragHandle.BEZIER_HANDLE_OUT
                                return@detectDragGestures
                            }

                            // 3. Whole shape drag
                            activeHandle = DragHandle.BEZIER_WHOLE
                        } else {
                            val cx = m.centerX * w
                            val cy = m.centerY * h
                            val rx = m.radiusX * w
                            val ry = m.radiusY * h

                            fun isHit(px: Float, py: Float) =
                                sqrt((startOffset.x - px).pow(2) + (startOffset.y - py).pow(2)) < hitRadius

                            activeHandle = when {
                                isHit(cx, cy - ry) -> DragHandle.HANDLE_TOP
                                isHit(cx, cy + ry) -> DragHandle.HANDLE_BOTTOM
                                isHit(cx - rx, cy) -> DragHandle.HANDLE_LEFT
                                isHit(cx + rx, cy) -> DragHandle.HANDLE_RIGHT
                                isHit(cx - rx, cy - ry) -> DragHandle.HANDLE_TOP_LEFT
                                isHit(cx + rx, cy - ry) -> DragHandle.HANDLE_TOP_RIGHT
                                isHit(cx - rx, cy + ry) -> DragHandle.HANDLE_BOTTOM_LEFT
                                isHit(cx + rx, cy + ry) -> DragHandle.HANDLE_BOTTOM_RIGHT
                                isHit(cx, cy) -> DragHandle.CENTER
                                (startOffset.x - cx).pow(2) / rx.pow(2) + (startOffset.y - cy).pow(2) / ry.pow(2) <= 1.0f ->
                                    DragHandle.CENTER
                                else -> DragHandle.CENTER
                            }
                        }
                    },
                    onDragEnd = { activeHandle = DragHandle.NONE },
                    onDragCancel = { activeHandle = DragHandle.NONE }
                ) { change, dragAmount ->
                    change.consume()
                    val m = currentMask
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val dx = dragAmount.x / w
                    val dy = dragAmount.y / h

                    if (m.type == MaskType.CUSTOM_BEZIER) {
                        val pts = m.bezierPoints.toMutableList()
                        val idx = activeBezierIndex.coerceIn(0, pts.size - 1)
                        val p = pts[idx]

                        when (activeHandle) {
                            DragHandle.BEZIER_ANCHOR -> {
                                val nx = (p.x + dx).coerceIn(0.02f, 0.98f)
                                val ny = (p.y + dy).coerceIn(0.02f, 0.98f)
                                val shiftInX = (p.handleInX + dx).coerceIn(0.02f, 0.98f)
                                val shiftInY = (p.handleInY + dy).coerceIn(0.02f, 0.98f)
                                val shiftOutX = (p.handleOutX + dx).coerceIn(0.02f, 0.98f)
                                val shiftOutY = (p.handleOutY + dy).coerceIn(0.02f, 0.98f)
                                pts[idx] = p.copy(
                                    x = nx,
                                    y = ny,
                                    handleInX = shiftInX,
                                    handleInY = shiftInY,
                                    handleOutX = shiftOutX,
                                    handleOutY = shiftOutY
                                )
                                currentOnMaskChange(m.copy(bezierPoints = pts))
                            }
                            DragHandle.BEZIER_HANDLE_IN -> {
                                val ninX = (p.handleInX + dx).coerceIn(0.02f, 0.98f)
                                val ninY = (p.handleInY + dy).coerceIn(0.02f, 0.98f)
                                pts[idx] = p.copy(handleInX = ninX, handleInY = ninY)
                                currentOnMaskChange(m.copy(bezierPoints = pts))
                            }
                            DragHandle.BEZIER_HANDLE_OUT -> {
                                val noutX = (p.handleOutX + dx).coerceIn(0.02f, 0.98f)
                                val noutY = (p.handleOutY + dy).coerceIn(0.02f, 0.98f)
                                pts[idx] = p.copy(handleOutX = noutX, handleOutY = noutY)
                                currentOnMaskChange(m.copy(bezierPoints = pts))
                            }
                            DragHandle.BEZIER_WHOLE -> {
                                val shifted = pts.map { pt ->
                                    pt.copy(
                                        x = (pt.x + dx).coerceIn(0.02f, 0.98f),
                                        y = (pt.y + dy).coerceIn(0.02f, 0.98f),
                                        handleInX = (pt.handleInX + dx).coerceIn(0.02f, 0.98f),
                                        handleInY = (pt.handleInY + dy).coerceIn(0.02f, 0.98f),
                                        handleOutX = (pt.handleOutX + dx).coerceIn(0.02f, 0.98f),
                                        handleOutY = (pt.handleOutY + dy).coerceIn(0.02f, 0.98f)
                                    )
                                }
                                currentOnMaskChange(m.copy(bezierPoints = shifted))
                            }
                            else -> {}
                        }
                    } else {
                        when (activeHandle) {
                            DragHandle.CENTER -> {
                                val newCx = (m.centerX + dx).coerceIn(0.05f, 0.95f)
                                val newCy = (m.centerY + dy).coerceIn(0.05f, 0.95f)
                                currentOnMaskChange(m.copy(centerX = newCx, centerY = newCy))
                            }
                            DragHandle.HANDLE_RIGHT -> {
                                val newRx = (m.radiusX + dx).coerceIn(0.05f, 0.5f)
                                currentOnMaskChange(m.copy(radiusX = newRx))
                            }
                            DragHandle.HANDLE_LEFT -> {
                                val newRx = (m.radiusX - dx).coerceIn(0.05f, 0.5f)
                                currentOnMaskChange(m.copy(radiusX = newRx))
                            }
                            DragHandle.HANDLE_BOTTOM -> {
                                val newRy = (m.radiusY + dy).coerceIn(0.05f, 0.5f)
                                currentOnMaskChange(m.copy(radiusY = newRy))
                            }
                            DragHandle.HANDLE_TOP -> {
                                val newRy = (m.radiusY - dy).coerceIn(0.05f, 0.5f)
                                currentOnMaskChange(m.copy(radiusY = newRy))
                            }
                            DragHandle.HANDLE_BOTTOM_RIGHT -> {
                                val newRx = (m.radiusX + dx).coerceIn(0.05f, 0.5f)
                                val newRy = (m.radiusY + dy).coerceIn(0.05f, 0.5f)
                                currentOnMaskChange(m.copy(radiusX = newRx, radiusY = newRy))
                            }
                            DragHandle.HANDLE_TOP_RIGHT -> {
                                val newRx = (m.radiusX + dx).coerceIn(0.05f, 0.5f)
                                val newRy = (m.radiusY - dy).coerceIn(0.05f, 0.5f)
                                currentOnMaskChange(m.copy(radiusX = newRx, radiusY = newRy))
                            }
                            DragHandle.HANDLE_BOTTOM_LEFT -> {
                                val newRx = (m.radiusX - dx).coerceIn(0.05f, 0.5f)
                                val newRy = (m.radiusY + dy).coerceIn(0.05f, 0.5f)
                                currentOnMaskChange(m.copy(radiusX = newRx, radiusY = newRy))
                            }
                            DragHandle.HANDLE_TOP_LEFT -> {
                                val newRx = (m.radiusX - dx).coerceIn(0.05f, 0.5f)
                                val newRy = (m.radiusY - dy).coerceIn(0.05f, 0.5f)
                                currentOnMaskChange(m.copy(radiusX = newRx, radiusY = newRy))
                            }
                            else -> {}
                        }
                    }
                }
            }
    ) {
        // ==========================================
        // 1. CANVAS: Guides & Bezier Spline
        // ==========================================
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = mask.centerX * w
            val cy = mask.centerY * h
            val rx = mask.radiusX * w
            val ry = mask.radiusY * h

            when (mask.type) {
                MaskType.CUSTOM_BEZIER -> {
                    val pts = mask.bezierPoints
                    if (pts.size >= 2) {
                        val path = Path()
                        path.moveTo(pts[0].x * w, pts[0].y * h)
                        for (i in 0 until pts.size) {
                            val p0 = pts[i]
                            val pNext = pts[(i + 1) % pts.size]
                            path.cubicTo(
                                p0.handleOutX * w, p0.handleOutY * h,
                                pNext.handleInX * w, pNext.handleInY * h,
                                pNext.x * w, pNext.y * h
                            )
                        }
                        path.close()

                        // Solid Amber outline for active power window
                        drawPath(path, color = ResolveAmber, style = Stroke(width = 2.dp.toPx()))

                        // Dashed feather aura
                        drawPath(
                            path,
                            color = ResolveCyan.copy(alpha = 0.5f),
                            style = Stroke(
                                width = 1.2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                            )
                        )

                        // Render Tangent Handles for Selected Node
                        val selIdx = mask.selectedPointIndex.coerceIn(0, pts.size - 1)
                        val selPt = pts[selIdx]
                        val selAnchor = Offset(selPt.x * w, selPt.y * h)
                        val inHandle = Offset(selPt.handleInX * w, selPt.handleInY * h)
                        val outHandle = Offset(selPt.handleOutX * w, selPt.handleOutY * h)

                        // Tangent lines
                        drawLine(
                            color = ResolveCyan.copy(alpha = 0.8f),
                            start = inHandle,
                            end = selAnchor,
                            strokeWidth = 1.2.dp.toPx()
                        )
                        drawLine(
                            color = ResolveCyan.copy(alpha = 0.8f),
                            start = selAnchor,
                            end = outHandle,
                            strokeWidth = 1.2.dp.toPx()
                        )

                        // Diamond tangent handle markers
                        drawCircle(color = ResolveCyan, radius = 4.dp.toPx(), center = inHandle)
                        drawCircle(color = Color.White, radius = 2.dp.toPx(), center = inHandle)
                        drawCircle(color = ResolveCyan, radius = 4.dp.toPx(), center = outHandle)
                        drawCircle(color = Color.White, radius = 2.dp.toPx(), center = outHandle)

                        // Draw Anchor Points
                        pts.forEachIndexed { i, pt ->
                            val ptCenter = Offset(pt.x * w, pt.y * h)
                            val isSel = i == selIdx

                            if (isSel) {
                                // Cyan selection halo
                                drawCircle(
                                    color = ResolveCyan.copy(alpha = 0.35f),
                                    radius = 12.dp.toPx(),
                                    center = ptCenter
                                )
                                drawCircle(
                                    color = ResolveCyan,
                                    radius = 7.dp.toPx(),
                                    center = ptCenter
                                )
                            } else {
                                drawCircle(
                                    color = ResolveAmber,
                                    radius = 5.5.dp.toPx(),
                                    center = ptCenter
                                )
                            }
                            drawCircle(color = Color.White, radius = 3.dp.toPx(), center = ptCenter)
                        }
                    }
                }

                MaskType.ELLIPTICAL -> {
                    // Main solid boundary ellipse
                    drawOval(
                        color = ResolveAmber,
                        topLeft = Offset(cx - rx, cy - ry),
                        size = Size(rx * 2f, ry * 2f),
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Feather boundary (outer dashed ellipse)
                    val featherRx = rx * (1f + mask.feather)
                    val featherRy = ry * (1f + mask.feather)
                    drawOval(
                        color = ResolveCyan.copy(alpha = 0.65f),
                        topLeft = Offset(cx - featherRx, cy - featherRy),
                        size = Size(featherRx * 2f, featherRy * 2f),
                        style = Stroke(
                            width = 1.2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                        )
                    )

                    val handles = listOf(
                        Offset(cx + rx, cy),
                        Offset(cx - rx, cy),
                        Offset(cx, cy - ry),
                        Offset(cx, cy + ry)
                    )
                    for (handle in handles) {
                        drawCircle(color = ResolveAmber, radius = 5.dp.toPx(), center = handle)
                        drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = handle)
                    }

                    // Center Crosshair Reticle
                    drawCircle(color = ResolveAmber, radius = 4.dp.toPx(), center = Offset(cx, cy))
                    drawLine(color = ResolveAmber, start = Offset(cx - 10.dp.toPx(), cy), end = Offset(cx + 10.dp.toPx(), cy), strokeWidth = 1.5.dp.toPx())
                    drawLine(color = ResolveAmber, start = Offset(cx, cy - 10.dp.toPx()), end = Offset(cx, cy + 10.dp.toPx()), strokeWidth = 1.5.dp.toPx())
                }

                MaskType.RECTANGULAR -> {
                    // Main solid boundary rectangle
                    drawRoundRect(
                        color = ResolveAmber,
                        topLeft = Offset(cx - rx, cy - ry),
                        size = Size(rx * 2f, ry * 2f),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Feather boundary (outer dashed rectangle)
                    val featherRx = rx * (1f + mask.feather)
                    val featherRy = ry * (1f + mask.feather)
                    drawRoundRect(
                        color = ResolveCyan.copy(alpha = 0.65f),
                        topLeft = Offset(cx - featherRx, cy - featherRy),
                        size = Size(featherRx * 2f, featherRy * 2f),
                        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                        style = Stroke(
                            width = 1.2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                        )
                    )

                    val corners = listOf(
                        Offset(cx - rx, cy - ry),
                        Offset(cx + rx, cy - ry),
                        Offset(cx - rx, cy + ry),
                        Offset(cx + rx, cy + ry)
                    )
                    for (corner in corners) {
                        drawRect(
                            color = ResolveAmber,
                            topLeft = Offset(corner.x - 4.dp.toPx(), corner.y - 4.dp.toPx()),
                            size = Size(8.dp.toPx(), 8.dp.toPx())
                        )
                    }

                    drawCircle(color = ResolveAmber, radius = 4.dp.toPx(), center = Offset(cx, cy))
                    drawLine(color = ResolveAmber, start = Offset(cx - 10.dp.toPx(), cy), end = Offset(cx + 10.dp.toPx(), cy), strokeWidth = 1.5.dp.toPx())
                    drawLine(color = ResolveAmber, start = Offset(cx, cy - 10.dp.toPx()), end = Offset(cx, cy + 10.dp.toPx()), strokeWidth = 1.5.dp.toPx())
                }

                MaskType.TRIANGLE -> {
                    val apex = Offset(cx, cy - ry)
                    val bottomRight = Offset(cx + rx, cy + ry)
                    val bottomLeft = Offset(cx - rx, cy + ry)

                    val triPath = Path().apply {
                        moveTo(apex.x, apex.y)
                        lineTo(bottomRight.x, bottomRight.y)
                        lineTo(bottomLeft.x, bottomLeft.y)
                        close()
                    }
                    drawPath(
                        path = triPath,
                        color = ResolveAmber,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Outer dashed feather triangle
                    val featherRx = rx * (1f + mask.feather)
                    val featherRy = ry * (1f + mask.feather)
                    val featherTriPath = Path().apply {
                        moveTo(cx, cy - featherRy)
                        lineTo(cx + featherRx, cy + featherRy)
                        lineTo(cx - featherRx, cy + featherRy)
                        close()
                    }
                    drawPath(
                        path = featherTriPath,
                        color = ResolveCyan.copy(alpha = 0.65f),
                        style = Stroke(
                            width = 1.2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                        )
                    )

                    // 3 Vertex Handles
                    listOf(apex, bottomRight, bottomLeft).forEach { vtx ->
                        drawCircle(color = ResolveAmber, radius = 5.5.dp.toPx(), center = vtx)
                        drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = vtx)
                    }

                    // Center Crosshair Reticle
                    drawCircle(color = ResolveAmber, radius = 4.dp.toPx(), center = Offset(cx, cy))
                    drawLine(color = ResolveAmber, start = Offset(cx - 10.dp.toPx(), cy), end = Offset(cx + 10.dp.toPx(), cy), strokeWidth = 1.5.dp.toPx())
                    drawLine(color = ResolveAmber, start = Offset(cx, cy - 10.dp.toPx()), end = Offset(cx, cy + 10.dp.toPx()), strokeWidth = 1.5.dp.toPx())
                }

                MaskType.LINEAR -> {
                    val lineY = cy
                    val featherDist = ry * mask.feather

                    drawLine(color = ResolveAmber, start = Offset(0f, lineY), end = Offset(w, lineY), strokeWidth = 2.dp.toPx())
                    drawLine(
                        color = ResolveCyan.copy(alpha = 0.6f),
                        start = Offset(0f, lineY - featherDist),
                        end = Offset(w, lineY - featherDist),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    )
                    drawLine(
                        color = ResolveCyan.copy(alpha = 0.6f),
                        start = Offset(0f, lineY + featherDist),
                        end = Offset(w, lineY + featherDist),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    )
                    drawCircle(color = ResolveAmber, radius = 5.dp.toPx(), center = Offset(cx, cy))
                }

                MaskType.NONE -> {}
            }

            // ==========================================================
            // MOTION TRACKER TRAJECTORY PATH & POINT CLOUD RETICLE
            // ==========================================================
            if (mask.type != MaskType.NONE && (mask.trackingKeyframes.isNotEmpty() || mask.trackingEnabled)) {
                val kfs = mask.trackingKeyframes
                if (kfs.size >= 2) {
                    val trackPath = Path().apply {
                        moveTo(kfs.first().centerX * w, kfs.first().centerY * h)
                        for (i in 1 until kfs.size) {
                            lineTo(kfs[i].centerX * w, kfs[i].centerY * h)
                        }
                    }
                    drawPath(
                        path = trackPath,
                        color = Color(0xFF10B981).copy(alpha = 0.85f),
                        style = Stroke(width = 1.8.dp.toPx())
                    )
                }
                kfs.forEach { kf ->
                    drawCircle(
                        color = Color(0xFF10B981),
                        radius = 2.5.dp.toPx(),
                        center = Offset(kf.centerX * w, kf.centerY * h)
                    )
                }

                // DaVinci Resolve Point-Cloud Lock Crosshair around active center
                val lockRadius = 18.dp.toPx()
                drawCircle(
                    color = Color(0xFF10B981).copy(alpha = 0.75f),
                    radius = lockRadius,
                    center = Offset(cx, cy),
                    style = Stroke(
                        width = 1.4.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))
                    )
                )
            }
        }

        // ==========================================
        // 2. FLOATING HUD TOOLBAR OVER VIDEO
        // ==========================================
        if (showQuickToolbar) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xDD121620))
                    .border(1.dp, Color(0xFF2B3547), RoundedCornerShape(20.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                // Shape Toggle: Elliptical
                IconButton(
                    onClick = { onMaskChange(mask.copy(type = MaskType.ELLIPTICAL)) },
                    modifier = Modifier.size(28.dp).testTag("quick_mask_elliptical")
                ) {
                    Icon(
                        imageVector = Icons.Default.Circle,
                        contentDescription = "Oval Window",
                        tint = if (mask.type == MaskType.ELLIPTICAL) ResolveAmber else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Shape Toggle: Rectangular
                IconButton(
                    onClick = { onMaskChange(mask.copy(type = MaskType.RECTANGULAR)) },
                    modifier = Modifier.size(28.dp).testTag("quick_mask_rectangular")
                ) {
                    Icon(
                        imageVector = Icons.Default.CropSquare,
                        contentDescription = "Rect Window",
                        tint = if (mask.type == MaskType.RECTANGULAR) ResolveAmber else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Shape Toggle: Triangle
                IconButton(
                    onClick = { onMaskChange(mask.copy(type = MaskType.TRIANGLE)) },
                    modifier = Modifier.size(28.dp).testTag("quick_mask_triangle")
                ) {
                    Icon(
                        imageVector = Icons.Default.ChangeHistory,
                        contentDescription = "Triangle Window",
                        tint = if (mask.type == MaskType.TRIANGLE) ResolveAmber else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Shape Toggle: Linear
                IconButton(
                    onClick = { onMaskChange(mask.copy(type = MaskType.LINEAR)) },
                    modifier = Modifier.size(28.dp).testTag("quick_mask_linear")
                ) {
                    Icon(
                        imageVector = Icons.Default.HorizontalRule,
                        contentDescription = "Linear Window",
                        tint = if (mask.type == MaskType.LINEAR) ResolveAmber else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Shape Toggle: Custom Bezier
                IconButton(
                    onClick = { onMaskChange(mask.copy(type = MaskType.CUSTOM_BEZIER)) },
                    modifier = Modifier.size(28.dp).testTag("quick_mask_bezier")
                ) {
                    Icon(
                        imageVector = Icons.Default.Gesture,
                        contentDescription = "Bezier Window",
                        tint = if (mask.type == MaskType.CUSTOM_BEZIER) ResolveAmber else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                VerticalDivider(color = Color(0xFF334155), modifier = Modifier.height(16.dp).padding(horizontal = 4.dp))

                // Invert Toggle
                IconButton(
                    onClick = { onMaskChange(mask.copy(invert = !mask.invert)) },
                    modifier = Modifier.size(28.dp).testTag("quick_mask_invert")
                ) {
                    Icon(
                        imageVector = Icons.Default.Flip,
                        contentDescription = "Invert Mask",
                        tint = if (mask.invert) ResolveAmber else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Highlight / Matte View Toggle (DaVinci Resolve Shift+H mode)
                IconButton(
                    onClick = { onMaskChange(mask.copy(showMatte = !mask.showMatte)) },
                    modifier = Modifier.size(28.dp).testTag("quick_mask_matte_toggle")
                ) {
                    Icon(
                        imageVector = if (mask.showMatte) Icons.Default.Contrast else Icons.Default.Tonality,
                        contentDescription = "Show Alpha Matte",
                        tint = if (mask.showMatte) ResolveCyan else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
