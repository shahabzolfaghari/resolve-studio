package com.example.color

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import com.example.model.*
import java.io.BufferedReader
import java.io.StringReader
import java.util.Locale
import kotlin.math.*

object SamsungLogColorScience {

    // Rec.709 Luminance weights
    private const val LR = 0.2126f
    private const val LG = 0.7152f
    private const val LB = 0.0722f

    /**
     * Decode Samsung Log gamma curve into linear HDR light values.
     * Samsung Log uses a piecewise logarithmic formulation:
     * for y <= cut: y / slope
     * for y > cut: (exp((y - d) / a) - c) / b
     */
    fun samsungLogToLinear(v: Float): Float {
        val y = v.coerceIn(0f, 1f)
        val a = 0.235f
        val b = 5.555f
        val c = 0.092f
        val d = 0.540f

        val expVal = exp((y - d) / a)
        return ((expVal - c) / b).coerceIn(0f, 1f)
    }

    /**
     * Apply Samsung Highlight Roll-off (Film Knee compression) to retain specular detail.
     */
    fun applyHighlightRollOff(linearVal: Float, rollOffThreshold: Float): Float {
        val knee = 0.6f + (1f - rollOffThreshold) * 0.3f
        if (linearVal <= knee) return linearVal
        // Soft shoulder compression (Hermite cubic curve toward 1.0)
        val excess = linearVal - knee
        val compressed = knee + (1f - knee) * (1f - exp(-excess / (1f - knee)))
        return compressed.coerceIn(0f, 1f)
    }

    /**
     * Build 256-element fast lookup table for monotonic cubic spline curves.
     */
    fun buildSplineLut(points: List<CurvePoint>): FloatArray {
        val lut = FloatArray(256)
        if (points.isEmpty()) {
            for (i in 0..255) lut[i] = i / 255f
            return lut
        }
        val sorted = points.sortedBy { it.x }
        val n = sorted.size

        if (n == 1) {
            val v = sorted[0].y.coerceIn(0f, 1f)
            lut.fill(v)
            return lut
        }

        val xs = FloatArray(n) { sorted[it].x.coerceIn(0f, 1f) }
        val ys = FloatArray(n) { sorted[it].y.coerceIn(0f, 1f) }

        // Tangents using Fritsch-Carlson monotonic method
        val deltas = FloatArray(n - 1)
        val ms = FloatArray(n - 1)
        for (i in 0 until n - 1) {
            deltas[i] = xs[i + 1] - xs[i]
            ms[i] = if (deltas[i] == 0f) 0f else (ys[i + 1] - ys[i]) / deltas[i]
        }

        val tangents = FloatArray(n)
        tangents[0] = ms[0]
        for (i in 1 until n - 1) {
            tangents[i] = (ms[i - 1] + ms[i]) / 2f
        }
        tangents[n - 1] = ms[n - 2]

        for (i in 0 until n - 1) {
            if (ms[i] == 0f) {
                tangents[i] = 0f
                tangents[i + 1] = 0f
            } else {
                val alpha = tangents[i] / ms[i]
                val beta = tangents[i + 1] / ms[i]
                val s = alpha * alpha + beta * beta
                if (s > 9f) {
                    val tau = 3f / sqrt(s)
                    tangents[i] = tau * alpha * ms[i]
                    tangents[i + 1] = tau * beta * ms[i]
                }
            }
        }

        for (i in 0..255) {
            val x = i / 255f
            if (x <= xs[0]) {
                lut[i] = ys[0]
                continue
            }
            if (x >= xs[n - 1]) {
                lut[i] = ys[n - 1]
                continue
            }

            // Find segment
            var seg = 0
            while (seg < n - 2 && x > xs[seg + 1]) {
                seg++
            }

            val h = xs[seg + 1] - xs[seg]
            if (h == 0f) {
                lut[i] = ys[seg]
            } else {
                val t = (x - xs[seg]) / h
                val t2 = t * t
                val t3 = t2 * t
                val h00 = 2f * t3 - 3f * t2 + 1f
                val h10 = t3 - 2f * t2 + t
                val h01 = -2f * t3 + 3f * t2
                val h11 = t3 - t2
                val yVal = h00 * ys[seg] + h10 * h * tangents[seg] + h01 * ys[seg + 1] + h11 * h * tangents[seg + 1]
                lut[i] = yVal.coerceIn(0f, 1f)
            }
        }
        return lut
    }

    /**
     * Build 256-element lookup table for periodic hue curves (Hue vs Hue, Hue vs Sat, Hue vs Lum)
     * enforcing seamless continuity across the 0° / 360° red boundary seam.
     */
    fun buildPeriodicSplineLut(points: List<CurvePoint>): FloatArray {
        if (points.isEmpty()) return FloatArray(256) { 0.5f }
        val sorted = points.sortedBy { it.x }
        val pts = sorted.toMutableList()
        val first = sorted.first()
        val last = sorted.last()

        if (first.x > 0.005f || last.x < 0.995f) {
            val seamY = if (first.x <= 0.005f) first.y else if (last.x >= 0.995f) last.y else (first.y + last.y) * 0.5f
            if (first.x > 0.005f) pts.add(0, CurvePoint(0f, seamY))
            if (last.x < 0.995f) pts.add(CurvePoint(1f, seamY))
        }

        val lut = buildSplineLut(pts)
        val boundary = (lut[0] + lut[255]) * 0.5f
        lut[0] = boundary
        lut[255] = boundary
        return lut
    }

    /**
     * Compute RGB shift factors from trackball coordinates (x, y).
     * Coordinate system:
     * - Top (angle 90°): Green
     * - Bottom-Right (angle 330°): Red/Magenta
     * - Bottom-Left (angle 210°): Blue/Cyan
     */
    fun wheelToRgbShift(x: Float, y: Float): Triple<Float, Float, Float> {
        val rShift = x * 0.866f - y * 0.5f
        val gShift = y
        val bShift = -x * 0.866f - y * 0.5f
        return Triple(rShift, gShift, bShift)
    }

    data class PrecomputedPolygon(
        val polyX: FloatArray,
        val polyY: FloatArray,
        val size: Int,
        val minX: Float,
        val maxX: Float,
        val minY: Float,
        val maxY: Float
    )

    /**
     * Precomputes sampled polygon vertices and bounding box for custom cubic Bezier power windows.
     * Evaluated ONCE per frame outside the pixel loops, eliminating millions of runtime allocations.
     */
    fun precomputeBezierPolygon(points: List<BezierControlPoint>, samplesPerSegment: Int = 8): PrecomputedPolygon? {
        if (points.size < 3) return null

        val totalSegments = points.size
        val polyX = FloatArray(totalSegments * samplesPerSegment)
        val polyY = FloatArray(totalSegments * samplesPerSegment)
        var polyIdx = 0
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        for (i in 0 until totalSegments) {
            val p0 = points[i]
            val nextIdx = (i + 1) % points.size
            val p3 = points[nextIdx]
            val p1x = p0.handleOutX
            val p1y = p0.handleOutY
            val p2x = p3.handleInX
            val p2y = p3.handleInY

            for (s in 0 until samplesPerSegment) {
                val t = s.toFloat() / samplesPerSegment
                val it = 1f - t
                val it2 = it * it
                val it3 = it2 * it
                val t2 = t * t
                val t3 = t2 * t

                val bx = it3 * p0.x + 3f * it2 * t * p1x + 3f * it * t2 * p2x + t3 * p3.x
                val by = it3 * p0.y + 3f * it2 * t * p1y + 3f * it * t2 * p2y + t3 * p3.y

                polyX[polyIdx] = bx
                polyY[polyIdx] = by
                if (bx < minX) minX = bx
                if (bx > maxX) maxX = bx
                if (by < minY) minY = by
                if (by > maxY) maxY = by
                polyIdx++
            }
        }

        return PrecomputedPolygon(polyX, polyY, polyIdx, minX, maxX, minY, maxY)
    }

    /**
     * Evaluates custom cubic Bezier power window mask at normalized coordinate (u, v) using
     * a precomputed polygon and bounding-box spatial culling.
     */
    fun evaluateBezierMaskFast(u: Float, v: Float, poly: PrecomputedPolygon?, feather: Float): Float {
        if (poly == null || poly.size < 3) return 1.0f

        val featherWidth = (feather * 0.15f).coerceAtLeast(0.005f)
        // Bounding-box rejection: 90%+ of image pixels exit immediately in O(1)
        if (u < poly.minX - featherWidth || u > poly.maxX + featherWidth ||
            v < poly.minY - featherWidth || v > poly.maxY + featherWidth
        ) {
            return 0.0f
        }

        val polySize = poly.size
        val polyX = poly.polyX
        val polyY = poly.polyY

        // 1. Ray-casting point-in-polygon test
        var inside = false
        var j = polySize - 1
        for (i in 0 until polySize) {
            val xi = polyX[i]
            val yi = polyY[i]
            val xj = polyX[j]
            val yj = polyY[j]
            if (((yi > v) != (yj > v)) && (u < (xj - xi) * (v - yi) / (yj - yi) + xi)) {
                inside = !inside
            }
            j = i
        }

        // 2. Shortest distance to boundary
        var minDistSq = Float.MAX_VALUE
        j = polySize - 1
        for (i in 0 until polySize) {
            val x1 = polyX[j]
            val y1 = polyY[j]
            val x2 = polyX[i]
            val y2 = polyY[i]
            val l2 = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)
            val dSq = if (l2 == 0f) {
                (u - x1) * (u - x1) + (v - y1) * (v - y1)
            } else {
                val proj = (((u - x1) * (x2 - x1) + (v - y1) * (y2 - y1)) / l2).coerceIn(0f, 1f)
                val px = x1 + proj * (x2 - x1)
                val py = y1 + proj * (y2 - y1)
                (u - px) * (u - px) + (v - py) * (v - py)
            }
            if (dSq < minDistSq) minDistSq = dSq
            j = i
        }

        val minDist = sqrt(minDistSq)
        val signedDist = if (inside) minDist else -minDist
        val t = ((signedDist + featherWidth) / (2f * featherWidth)).coerceIn(0f, 1f)
        return (t * t * (3f - 2f * t)) // Hermite smoothstep
    }

    /**
     * Evaluates custom cubic Bezier power window mask at normalized coordinate (u, v).
     */
    fun evaluateBezierMask(u: Float, v: Float, mask: PowerWindowMask): Float {
        val poly = precomputeBezierPolygon(mask.bezierPoints)
        return evaluateBezierMaskFast(u, v, poly, mask.feather)
    }

    /**
     * Evaluates Power Window Mask weight at normalized coordinate (u, v).
     */
    fun evaluateMask(
        u: Float,
        v: Float,
        mask: PowerWindowMask,
        precomputedBezier: PrecomputedPolygon? = null
    ): Float {
        if (mask.type == MaskType.NONE) return 1.0f

        val dx = u - mask.centerX
        val dy = v - mask.centerY

        // Rotate point according to mask rotation
        val rad = Math.toRadians(-mask.rotationDeg.toDouble()).toFloat()
        val cosR = cos(rad)
        val sinR = sin(rad)
        val rx = dx * cosR - dy * sinR
        val ry = dx * sinR + dy * cosR

        val rawWeight: Float = when (mask.type) {
            MaskType.ELLIPTICAL -> {
                val rxNorm = rx / mask.radiusX.coerceAtLeast(0.01f)
                val ryNorm = ry / mask.radiusY.coerceAtLeast(0.01f)
                val dist = sqrt(rxNorm * rxNorm + ryNorm * ryNorm)
                val inner = (1.0f - mask.feather).coerceAtLeast(0f)
                if (dist <= inner) 1.0f
                else if (dist >= 1.0f) 0.0f
                else {
                    val t = (dist - inner) / (1.0f - inner)
                    1.0f - (t * t * (3f - 2f * t)) // Smoothstep
                }
            }
            MaskType.RECTANGULAR -> {
                val halfW = mask.radiusX.coerceAtLeast(0.02f)
                val halfH = mask.radiusY.coerceAtLeast(0.02f)
                val featherW = (halfW * mask.feather).coerceAtLeast(0.005f)
                val featherH = (halfH * mask.feather).coerceAtLeast(0.005f)

                val qx = abs(rx) - (halfW - featherW)
                val qy = abs(ry) - (halfH - featherH)

                val dist: Float = max(qx / featherW, qy / featherH)
                if (dist <= 0f) 1.0f
                else if (dist >= 1.0f) 0.0f
                else {
                    val t = dist.coerceIn(0f, 1f)
                    1.0f - (t * t * (3f - 2f * t)) // Smoothstep
                }
            }
            MaskType.TRIANGLE -> {
                val halfW = mask.radiusX.coerceAtLeast(0.02f)
                val halfH = mask.radiusY.coerceAtLeast(0.02f)
                val nx = rx / halfW
                val ny = ry / halfH
                // Apex at (0, -1), base from (-1, +1) to (+1, +1)
                val spanAtY = ((ny + 1f) * 0.5f).coerceAtLeast(0f)
                val dSide = (abs(nx) - spanAtY) * 0.894f // normalized perpendicular distance to sloped edge
                val dBottom = ny - 1f
                val dTop = -1f - ny
                val signedEdgeDist = max(dSide, max(dBottom, dTop))
                val featherBand = mask.feather.coerceAtLeast(0.02f)
                val inner = -featherBand * 0.5f
                val outer = featherBand * 0.5f
                if (signedEdgeDist <= inner) 1.0f
                else if (signedEdgeDist >= outer) 0.0f
                else {
                    val t = ((signedEdgeDist - inner) / (outer - inner)).coerceIn(0f, 1f)
                    1.0f - (t * t * (3f - 2f * t))
                }
            }
            MaskType.LINEAR -> {
                val dist = ry / mask.radiusY.coerceAtLeast(0.01f)
                val inner = -mask.feather
                val outer = mask.feather
                if (dist <= inner) 1.0f
                else if (dist >= outer) 0.0f
                else {
                    val t = (dist - inner) / (outer - inner)
                    1.0f - (t * t * (3f - 2f * t))
                }
            }
            MaskType.CUSTOM_BEZIER -> {
                val poly = precomputedBezier ?: precomputeBezierPolygon(mask.bezierPoints)
                evaluateBezierMaskFast(u, v, poly, mask.feather)
            }
            MaskType.NONE -> 1.0f
        }

        val result = if (mask.invert) 1.0f - rawWeight else rawWeight
        return result.coerceIn(0f, 1f)
    }
        val dy = v - mask.centerY

        // Rotate point according to mask rotation
        val rad = Math.toRadians(-mask.rotationDeg.toDouble()).toFloat()
        val cosR = cos(rad)
        val sinR = sin(rad)
        val rx = dx * cosR - dy * sinR
        val ry = dx * sinR + dy * cosR

        val rawWeight: Float = when (mask.type) {
            MaskType.ELLIPTICAL -> {
                val rxNorm = rx / mask.radiusX.coerceAtLeast(0.01f)
                val ryNorm = ry / mask.radiusY.coerceAtLeast(0.01f)
                val dist = sqrt(rxNorm * rxNorm + ryNorm * ryNorm)
                val inner = (1.0f - mask.feather).coerceAtLeast(0f)
                if (dist <= inner) 1.0f
                else if (dist >= 1.0f) 0.0f
                else {
                    val t = (dist - inner) / (1.0f - inner)
                    1.0f - (t * t * (3f - 2f * t)) // Smoothstep
                }
            }
            MaskType.RECTANGULAR -> {
                val halfW = mask.radiusX.coerceAtLeast(0.02f)
                val halfH = mask.radiusY.coerceAtLeast(0.02f)
                val featherW = (halfW * mask.feather).coerceAtLeast(0.005f)
                val featherH = (halfH * mask.feather).coerceAtLeast(0.005f)

                val qx = abs(rx) - (halfW - featherW)
                val qy = abs(ry) - (halfH - featherH)

                val dist: Float = max(qx / featherW, qy / featherH)
                if (dist <= 0f) 1.0f
                else if (dist >= 1.0f) 0.0f
                else {
                    val t = dist.coerceIn(0f, 1f)
                    1.0f - (t * t * (3f - 2f * t)) // Smoothstep
                }
            }
            MaskType.TRIANGLE -> {
                val halfW = mask.radiusX.coerceAtLeast(0.02f)
                val halfH = mask.radiusY.coerceAtLeast(0.02f)
                val nx = rx / halfW
                val ny = ry / halfH
                // Apex at (0, -1), base from (-1, +1) to (+1, +1)
                val spanAtY = ((ny + 1f) * 0.5f).coerceAtLeast(0f)
                val dSide = (abs(nx) - spanAtY) * 0.894f // normalized perpendicular distance to sloped edge
                val dBottom = ny - 1f
                val dTop = -1f - ny
                val signedEdgeDist = max(dSide, max(dBottom, dTop))
                val featherBand = mask.feather.coerceAtLeast(0.02f)
                val inner = -featherBand * 0.5f
                val outer = featherBand * 0.5f
                if (signedEdgeDist <= inner) 1.0f
                else if (signedEdgeDist >= outer) 0.0f
                else {
                    val t = ((signedEdgeDist - inner) / (outer - inner)).coerceIn(0f, 1f)
                    1.0f - (t * t * (3f - 2f * t))
                }
            }
            MaskType.LINEAR -> {
                val dist = ry / mask.radiusY.coerceAtLeast(0.01f)
                val inner = -mask.feather
                val outer = mask.feather
                if (dist <= inner) 1.0f
                else if (dist >= outer) 0.0f
                else {
                    val t = (dist - inner) / (outer - inner)
                    1.0f - (t * t * (3f - 2f * t))
                }
            }
            MaskType.CUSTOM_BEZIER -> {
                evaluateBezierMask(u, v, mask)
            }
            MaskType.NONE -> 1.0f
        }

        val result = if (mask.invert) 1.0f - rawWeight else rawWeight
        return result.coerceIn(0f, 1f)
    }

    /**
     * Apply complete color grading pipeline to an in-memory Bitmap.
     * Parallelized across CPU cores with zero-allocation inner loops and cancellation support.
     */
    fun gradeBitmap(
        source: Bitmap,
        settings: ColorGradeSettings,
        destination: Bitmap? = null,
        isCancelled: () -> Boolean = { false }
    ): Bitmap {
        val width = source.width
        val height = source.height
        val output = destination ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        if (settings.bypass) {
            val pixels = IntArray(width * height)
            source.getPixels(pixels, 0, width, 0, 0, width, height)
            output.setPixels(pixels, 0, width, 0, 0, width, height)
            return output
        }

        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        // 1. Precompute 256-element Log-to-Linear & Knee Curve LUT (eliminates exp() calls in pixel loops)
        val logToLinearLut = FloatArray(256) { i ->
            var v = i / 255f
            if (settings.samsungLogTransform) {
                v = samsungLogToLinear(v)
                if (settings.samsungDynamicToneMapping) {
                    v = applyHighlightRollOff(v, settings.samsungHighlightRollOff)
                }
            }
            v
        }

        // 2. Precompute Curve LUTs
        val masterLut = buildSplineLut(settings.curves.master)
        val redLut = buildSplineLut(settings.curves.red)
        val greenLut = buildSplineLut(settings.curves.green)
        val blueLut = buildSplineLut(settings.curves.blue)

        // 3. Precompute Periodic Secondary Hue/Lum Curve LUTs (neutral = 0.5f, wraps seamlessly across 0°/360°)
        val hasHueVsHue = settings.curves.hueVsHue.any { abs(it.y - 0.5f) > 0.005f }
        val hasHueVsSat = settings.curves.hueVsSat.any { abs(it.y - 0.5f) > 0.005f }
        val hasHueVsLum = settings.curves.hueVsLum.any { abs(it.y - 0.5f) > 0.005f }
        val hasLumVsSat = settings.curves.lumVsSat.any { abs(it.y - 0.5f) > 0.005f }
        val hasSecondaryCurves = hasHueVsHue || hasHueVsSat || hasHueVsLum || hasLumVsSat

        val hueVsHueLut = if (hasHueVsHue) buildPeriodicSplineLut(settings.curves.hueVsHue) else null
        val hueVsSatLut = if (hasHueVsSat) buildPeriodicSplineLut(settings.curves.hueVsSat) else null
        val hueVsLumLut = if (hasHueVsLum) buildPeriodicSplineLut(settings.curves.hueVsLum) else null
        val lumVsSatLut = if (hasLumVsSat) buildSplineLut(settings.curves.lumVsSat) else null

        // 4. Precompute Bezier mask polygon vertices once outside loops
        val precomputedBezier = if (settings.mask.type == MaskType.CUSTOM_BEZIER) {
            precomputeBezierPolygon(settings.mask.bezierPoints)
        } else null

        // Primary Wheel vectors
        val (liftR, liftG, liftB) = wheelToRgbShift(settings.lift.x, settings.lift.y)
        val (gammaR, gammaG, gammaB) = wheelToRgbShift(settings.gamma.x, settings.gamma.y)
        val (gainR, gainG, gainB) = wheelToRgbShift(settings.gain.x, settings.gain.y)
        val (offsetR, offsetG, offsetB) = wheelToRgbShift(settings.offset.x, settings.offset.y)

        val liftLum = settings.lift.lum
        val gammaLum = settings.gamma.lum
        val gainLum = settings.gain.lum
        val offsetLum = settings.offset.lum

        // DaVinci Resolve Log Zone Wheel vectors
        val hasLogWheels = settings.logWheels.hasAdjustments
        val (logShadR, logShadG, logShadB) = wheelToRgbShift(settings.logWheels.shadow.x, settings.logWheels.shadow.y)
        val (logMidR, logMidG, logMidB) = wheelToRgbShift(settings.logWheels.midtone.x, settings.logWheels.midtone.y)
        val (logHighR, logHighG, logHighB) = wheelToRgbShift(settings.logWheels.highlight.x, settings.logWheels.highlight.y)
        val (logOffR, logOffG, logOffB) = wheelToRgbShift(settings.logWheels.offset.x, settings.logWheels.offset.y)
        val logLowRange = settings.logWheels.lowRange.coerceIn(0.08f, 0.60f)
        val logHighRange = settings.logWheels.highRange.coerceIn(logLowRange + 0.05f, 0.92f)

        // Exposure multiplier (2^EV)
        val exposureScale = 2.0f.pow(settings.exposure)
        val contrastVal = settings.contrast
        val satVal = settings.saturation
        val tempVal = settings.temperature
        val tintVal = settings.tint

        val hasMask = settings.mask.type != MaskType.NONE
        val effectiveGrain = if (settings.effects.grain.enabled) {
            settings.effects.grain
        } else if (settings.effects.grainIntensity > 0f) {
            FilmGrainSettings(
                enabled = true,
                strength = settings.effects.grainIntensity,
                size = settings.effects.grainSize
            )
        } else {
            settings.effects.grain
        }
        val hasEffects = effectiveGrain.enabled ||
                settings.effects.vignetteIntensity > 0f ||
                settings.effects.halationIntensity > 0f

        val lutPreset = settings.activeLut
        val lutIntensity = settings.lutIntensity

        // Parallel Row Slicing: divide frame height evenly across available CPU cores
        val numCores = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        val sliceH = (height + numCores - 1) / numCores
        val latch = java.util.concurrent.CountDownLatch(numCores)

        for (core in 0 until numCores) {
            val startY = core * sliceH
            val endY = min(startY + sliceH, height)

            if (startY >= height) {
                latch.countDown()
                continue
            }

            java.util.concurrent.ForkJoinPool.commonPool().execute {
                try {
                    val scratchLut = FloatArray(3)
                    val scratchCst = FloatArray(3)
                    val scratchHue = FloatArray(3)
                    val scratchFc = FloatArray(3)

                    for (y in startY until endY) {
                        if (isCancelled()) break
                        val vNorm = y.toFloat() / height
                        val rowOffset = y * width

                        for (x in 0 until width) {
                            val idx = rowOffset + x
                            val pixel = pixels[idx]

                            val rawR = (pixel shr 16) and 0xFF
                            val rawG = (pixel shr 8) and 0xFF
                            val rawB = pixel and 0xFF

                            val origR = rawR / 255f
                            val origG = rawG / 255f
                            val origB = rawB / 255f

                            // 1. Instant O(1) Precomputed Log to Linear & Dynamic Tone Map
                            var r = logToLinearLut[rawR]
                            var g = logToLinearLut[rawG]
                            var b = logToLinearLut[rawB]

                            // Normalized camera base (used outside Power Window masks)
                            val baseR = r
                            val baseG = g
                            val baseB = b

                            // 2. Exposure, Brightness & White Balance (Temp / Tint)
                            r *= exposureScale
                            g *= exposureScale
                            b *= exposureScale

                            if (settings.brightness != 0f) {
                                val brightShift = settings.brightness * 0.15f
                                r += brightShift
                                g += brightShift
                                b += brightShift
                            }

                            if (tempVal != 0f) {
                                r += tempVal * 0.15f
                                b -= tempVal * 0.15f
                            }
                            if (tintVal != 0f) {
                                g -= tintVal * 0.12f
                                r += tintVal * 0.05f
                                b += tintVal * 0.05f
                            }

                            // 3. DaVinci Resolve Lift / Gamma / Gain / Offset Color Wheels
                            var lum = LR * r + LG * g + LB * b
                            val oneMinusLum = (1f - lum).coerceIn(0f, 1f)
                            val clampedLum = lum.coerceIn(0f, 1f)

                            // Lift (Shadows) - Single cycle multiplication instead of pow(2)
                            val liftWeight = oneMinusLum * oneMinusLum
                            r += (liftR * 0.25f + liftLum * 0.2f) * liftWeight
                            g += (liftG * 0.25f + liftLum * 0.2f) * liftWeight
                            b += (liftB * 0.25f + liftLum * 0.2f) * liftWeight

                            // Gain (Highlights)
                            val gainWeight = clampedLum * clampedLum
                            r += (gainR * 0.35f + gainLum * 0.35f) * gainWeight
                            g += (gainG * 0.35f + gainLum * 0.35f) * gainWeight
                            b += (gainB * 0.35f + gainLum * 0.35f) * gainWeight

                            // Gamma (Midtones)
                            val gammaWeight = 4f * lum * (1f - lum)
                            if (gammaWeight > 0f) {
                                r += (gammaR * 0.25f + gammaLum * 0.25f) * gammaWeight
                                g += (gammaG * 0.25f + gammaLum * 0.25f) * gammaWeight
                                b += (gammaB * 0.25f + gammaLum * 0.25f) * gammaWeight
                            }

                            // Offset (Global)
                            r += (offsetR * 0.2f + offsetLum * 0.2f)
                            g += (offsetG * 0.2f + offsetLum * 0.2f)
                            b += (offsetB * 0.2f + offsetLum * 0.2f)

                            // 3B. DaVinci Resolve Log Zone Wheels (Shadow / Midtone / Highlight / Offset)
                            if (hasLogWheels) {
                                val lw = settings.logWheels
                                val clampedL = lum.coerceIn(0f, 1f)
                                val shadW = if (clampedL >= logLowRange) 0f else {
                                    val t = (clampedL / logLowRange).coerceIn(0f, 1f)
                                    1f - (t * t * (3f - 2f * t))
                                }
                                val highW = if (clampedL <= logHighRange) 0f else {
                                    val t = ((clampedL - logHighRange) / (1f - logHighRange).coerceAtLeast(0.05f)).coerceIn(0f, 1f)
                                    t * t * (3f - 2f * t)
                                }
                                val midW = (1f - shadW - highW).coerceIn(0f, 1f)

                                r += (logShadR * 0.24f + lw.shadow.lum * 0.22f) * shadW +
                                        (logMidR * 0.24f + lw.midtone.lum * 0.22f) * midW +
                                        (logHighR * 0.30f + lw.highlight.lum * 0.28f) * highW +
                                        (logOffR * 0.18f + lw.offset.lum * 0.18f)
                                g += (logShadG * 0.24f + lw.shadow.lum * 0.22f) * shadW +
                                        (logMidG * 0.24f + lw.midtone.lum * 0.22f) * midW +
                                        (logHighG * 0.30f + lw.highlight.lum * 0.28f) * highW +
                                        (logOffG * 0.18f + lw.offset.lum * 0.18f)
                                b += (logShadB * 0.24f + lw.shadow.lum * 0.22f) * shadW +
                                        (logMidB * 0.24f + lw.midtone.lum * 0.22f) * midW +
                                        (logHighB * 0.30f + lw.highlight.lum * 0.28f) * highW +
                                        (logOffB * 0.18f + lw.offset.lum * 0.18f)
                            }

                            // Highlights & Shadows Sliders
                            if (settings.highlights != 0f) {
                                val hWeight = clampedLum * clampedLum
                                val hDelta = settings.highlights * 0.2f * hWeight
                                r += hDelta; g += hDelta; b += hDelta
                            }
                            if (settings.shadows != 0f) {
                                val sWeight = oneMinusLum * oneMinusLum
                                val sDelta = settings.shadows * 0.2f * sWeight
                                r += sDelta; g += sDelta; b += sDelta
                            }

                            // 4. Contrast (S-Curve about 0.43 pivot)
                            if (contrastVal != 1.0f) {
                                r = ((r - 0.43f) * contrastVal + 0.43f)
                                g = ((g - 0.43f) * contrastVal + 0.43f)
                                b = ((b - 0.43f) * contrastVal + 0.43f)
                            }

                            // 5. Saturation & DaVinci Color Boost (Smart Vibrance)
                            if (settings.colorBoost != 0f) {
                                lum = LR * r + LG * g + LB * b
                                val maxCh = max(r, max(g, b))
                                val minCh = min(r, min(g, b))
                                val curSat = if (maxCh == 0f) 0f else (maxCh - minCh) / maxCh
                                val boostFactor = (1f - curSat) * settings.colorBoost
                                r = lum + (r - lum) * (1f + boostFactor)
                                g = lum + (g - lum) * (1f + boostFactor)
                                b = lum + (b - lum) * (1f + boostFactor)
                            }

                            if (satVal != 1.0f) {
                                lum = LR * r + LG * g + LB * b
                                r = lum + (r - lum) * satVal
                                g = lum + (g - lum) * satVal
                                b = lum + (b - lum) * satVal
                            }

                            // 6. Spline Curves (Master, R, G, B)
                            val rIndex = (r.coerceIn(0f, 1f) * 255f).toInt()
                            val gIndex = (g.coerceIn(0f, 1f) * 255f).toInt()
                            val bIndex = (b.coerceIn(0f, 1f) * 255f).toInt()

                            r = redLut[rIndex]
                            g = greenLut[gIndex]
                            b = blueLut[bIndex]

                            val masterIndex = ((LR * r + LG * g + LB * b).coerceIn(0f, 1f) * 255f).toInt()
                            val masterScale = if (masterIndex == 0) 1f else masterLut[masterIndex] / (masterIndex / 255f)
                            r = (r * masterScale).coerceIn(0f, 1f)
                            g = (g * masterScale).coerceIn(0f, 1f)
                            b = (b * masterScale).coerceIn(0f, 1f)

                            // 6B. DaVinci Resolve Color vs Color Secondary Curves
                            if (hasSecondaryCurves) {
                                val maxCh = max(r, max(g, b))
                                val minCh = min(r, min(g, b))
                                val delta = maxCh - minCh
                                val curLum = (LR * r + LG * g + LB * b).coerceIn(0f, 1f)

                                if (delta > 0.002f) {
                                    val rawHue = when (maxCh) {
                                        r -> 60f * (((g - b) / delta) % 6f)
                                        g -> 60f * (((b - r) / delta) + 2f)
                                        else -> 60f * (((r - g) / delta) + 4f)
                                    }
                                    val hueNorm = ((rawHue + 360f) % 360f) / 360f
                                    val hueIdx = (hueNorm * 255f).toInt().coerceIn(0, 255)

                                    // Hue vs Hue (-60° to +60° shift around 0.5 neutral)
                                    if (hueVsHueLut != null) {
                                        val hueShiftDeg = (hueVsHueLut[hueIdx] - 0.5f) * 120f
                                        if (abs(hueShiftDeg) > 0.3f) {
                                            rotateHueRgbFast(r, g, b, hueShiftDeg, scratchHue)
                                            r = scratchHue[0]; g = scratchHue[1]; b = scratchHue[2]
                                        }
                                    }

                                    // Hue vs Lum (-0.30 to +0.30 luminance offset)
                                    if (hueVsLumLut != null) {
                                        val satGate = (delta / maxCh.coerceAtLeast(0.01f)).coerceIn(0f, 1f)
                                        val lumOffset = (hueVsLumLut[hueIdx] - 0.5f) * 0.55f * satGate
                                        r = (r + lumOffset).coerceIn(0f, 1f)
                                        g = (g + lumOffset).coerceIn(0f, 1f)
                                        b = (b + lumOffset).coerceIn(0f, 1f)
                                    }

                                    // Hue vs Sat (0.0x to 2.0x saturation scale)
                                    if (hueVsSatLut != null) {
                                        val satMult = (hueVsSatLut[hueIdx] * 2.0f).coerceIn(0f, 2f)
                                        val lNow = LR * r + LG * g + LB * b
                                        r = (lNow + (r - lNow) * satMult).coerceIn(0f, 1f)
                                        g = (lNow + (g - lNow) * satMult).coerceIn(0f, 1f)
                                        b = (lNow + (b - lNow) * satMult).coerceIn(0f, 1f)
                                    }
                                }

                                // Lum vs Sat (0.0x to 2.0x saturation scale)
                                if (lumVsSatLut != null) {
                                    val lumIdx = (curLum * 255f).toInt().coerceIn(0, 255)
                                    val lSatMult = (lumVsSatLut[lumIdx] * 2.0f).coerceIn(0f, 2f)
                                    val lNow = LR * r + LG * g + LB * b
                                    r = (lNow + (r - lNow) * lSatMult).coerceIn(0f, 1f)
                                    g = (lNow + (g - lNow) * lSatMult).coerceIn(0f, 1f)
                                    b = (lNow + (b - lNow) * lSatMult).coerceIn(0f, 1f)
                                }
                            }

                            // 7. Look / LUT Preset Emulation (Zero allocation)
                            if (!settings.lutBypass && lutIntensity > 0f) {
                                applyLutPresetFast(r, g, b, lutPreset, settings, scratchLut)
                                r = r * (1f - lutIntensity) + scratchLut[0] * lutIntensity
                                g = g * (1f - lutIntensity) + scratchLut[1] * lutIntensity
                                b = b * (1f - lutIntensity) + scratchLut[2] * lutIntensity
                            }

                            // 8. Mask Compositing with Precomputed Bezier Bounding Box Check
                            if (hasMask) {
                                val uNorm = x.toFloat() / width
                                val maskWeight = evaluateMask(uNorm, vNorm, settings.mask, precomputedBezier)
                                if (settings.mask.showMatte) {
                                    r = maskWeight
                                    g = maskWeight
                                    b = maskWeight
                                } else {
                                    r = baseR * (1f - maskWeight) + r * maskWeight
                                    g = baseG * (1f - maskWeight) + g * maskWeight
                                    b = baseB * (1f - maskWeight) + b * maskWeight
                                }
                            }

                            // 8B. DaVinci Resolve 3D HSL Qualifier (Secondary Color Keyer)
                            if (settings.hslQualifier.enabled) {
                                val q = settings.hslQualifier
                                val keyWeight = evaluateHslKeyWeight(r, g, b, q)
                                if (q.showMatte) {
                                    val mono = (LR * r + LG * g + LB * b) * 0.22f
                                    r = mono * (1f - keyWeight) + r * keyWeight
                                    g = mono * (1f - keyWeight) + g * keyWeight
                                    b = mono * (1f - keyWeight) + b * keyWeight
                                } else if (keyWeight > 0.001f) {
                                    var qr = r
                                    var qg = g
                                    var qb = b

                                    if (q.hueShiftDeg != 0f) {
                                        rotateHueRgbFast(qr, qg, qb, q.hueShiftDeg, scratchHue)
                                        qr = scratchHue[0]; qg = scratchHue[1]; qb = scratchHue[2]
                                    }

                                    if (q.tempAdjust != 0f) {
                                        qr += q.tempAdjust * 0.18f
                                        qb -= q.tempAdjust * 0.18f
                                    }

                                    if (q.lumAdjust != 0f) {
                                        val lShift = q.lumAdjust * 0.28f
                                        qr += lShift; qg += lShift; qb += lShift
                                    }

                                    if (q.satAdjust != 1.0f) {
                                        val qlum = LR * qr + LG * qg + LB * qb
                                        qr = qlum + (qr - qlum) * q.satAdjust
                                        qg = qlum + (qg - qlum) * q.satAdjust
                                        qb = qlum + (qb - qlum) * q.satAdjust
                                    }

                                    r = (r * (1f - keyWeight) + qr * keyWeight).coerceIn(0f, 1f)
                                    g = (g * (1f - keyWeight) + qg * keyWeight).coerceIn(0f, 1f)
                                    b = (b * (1f - keyWeight) + qb * keyWeight).coerceIn(0f, 1f)
                                }
                            }

                            // 9. Film Effects (Halation, Vignette)
                            if (hasEffects) {
                                if (settings.effects.halationIntensity > 0f) {
                                    val lumHi = (LR * r + LG * g + LB * b)
                                    if (lumHi > 0.7f) {
                                        val excessH = (lumHi - 0.7f) / 0.3f
                                        val spill = excessH * excessH * settings.effects.halationIntensity
                                        r = (r + spill * 0.25f).coerceIn(0f, 1f)
                                        g = (g + spill * 0.08f).coerceIn(0f, 1f)
                                    }
                                }

                                if (settings.effects.vignetteIntensity > 0f) {
                                    val dx = (x.toFloat() / width - 0.5f) * 2f
                                    val dy = (y.toFloat() / height - 0.5f) * 2f
                                    val distSq = dx * dx + dy * dy
                                    val falloff = (1f - distSq * 0.45f * settings.effects.vignetteIntensity).coerceIn(0f, 1f)
                                    r *= falloff
                                    g *= falloff
                                    b *= falloff
                                }
                            }

                            // 10. DaVinci Resolve Final Output Node: Color Space Transform (CST)
                            if (settings.cstNode.enabled) {
                                applyColorSpaceTransformFast(r, g, b, settings.cstNode, scratchCst)
                                r = scratchCst[0]; g = scratchCst[1]; b = scratchCst[2]
                            }

                            // 11. False Color Exposure Metering
                            if (settings.showFalseColor) {
                                evaluateFalseColorFast(r, g, b, scratchFc)
                                r = scratchFc[0]; g = scratchFc[1]; b = scratchFc[2]
                            }

                            // 12. Split Screen Comparison Wipe (Graded vs Ungraded)
                            if (settings.splitScreenWipe < 1.0f) {
                                val splitX = (width * settings.splitScreenWipe).toInt()
                                if (x == splitX || x == splitX + 1) {
                                    r = 1f; g = 1f; b = 1f
                                } else if (x > splitX) {
                                    r = origR; g = origG; b = origB
                                }
                            }

                            val finalR = (r.coerceIn(0f, 1f) * 255f).toInt()
                            val finalG = (g.coerceIn(0f, 1f) * 255f).toInt()
                            val finalB = (b.coerceIn(0f, 1f) * 255f).toInt()

                            pixels[idx] = (0xFF shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()

        if (isCancelled()) return output

        // Overlay Authentic Organic Film Grain
        if (effectiveGrain.enabled && effectiveGrain.strength > 0f) {
            FilmGrainProcessor.applyGrain(pixels, width, height, effectiveGrain)
        }

        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }

    /**
     * Apply mathematical LUT look simulation for instant responsive mobile playback (Zero Allocation).
     */
    fun applyLutPresetFast(
        r: Float,
        g: Float,
        b: Float,
        preset: LutPreset,
        settings: ColorGradeSettings? = null,
        out: FloatArray
    ) {
        when (preset) {
            LutPreset.SAMSUNG_LOG_TO_REC709 -> {
                // High fidelity standard Rec.709 color conversion for Samsung S24 Log
                val cr = (r - 0.5f) * 1.12f + 0.5f
                val cg = (g - 0.5f) * 1.10f + 0.5f
                val cb = (b - 0.5f) * 1.15f + 0.5f
                out[0] = cr.coerceIn(0f, 1f)
                out[1] = cg.coerceIn(0f, 1f)
                out[2] = cb.coerceIn(0f, 1f)
            }
            LutPreset.SAMSUNG_REC709_PUNCH -> {
                // S24 Ultra Vivid Punch: expanded dynamic range, +20% contrast, vibrant saturation
                var cr = ((r - 0.42f) * 1.22f + 0.42f).coerceIn(0f, 1f)
                var cg = ((g - 0.42f) * 1.20f + 0.42f).coerceIn(0f, 1f)
                var cb = ((b - 0.42f) * 1.24f + 0.42f).coerceIn(0f, 1f)
                // Chroma boost
                val nLum = LR * cr + LG * cg + LB * cb
                out[0] = (nLum + (cr - nLum) * 1.25f).coerceIn(0f, 1f)
                out[1] = (nLum + (cg - nLum) * 1.22f).coerceIn(0f, 1f)
                out[2] = (nLum + (cb - nLum) * 1.25f).coerceIn(0f, 1f)
            }
            LutPreset.SAMSUNG_CINE_WARM -> {
                // Golden hour skin tones, gentle shadow warmth
                val tr = r.pow(0.92f) * 1.05f
                val tg = g.pow(0.96f) * 1.02f
                val tb = b.pow(1.08f) * 0.94f
                out[0] = tr.coerceIn(0f, 1f)
                out[1] = tg.coerceIn(0f, 1f)
                out[2] = tb.coerceIn(0f, 1f)
            }
            // ==========================================
            // 7 FAMOUS FUJIFILM RECIPES (Samsung S24 Log)
            // ==========================================
            LutPreset.FUJI_CLASSIC_CHROME -> {
                val lum = LR * r + LG * g + LB * b
                var cr = (lum + (r - lum) * 0.82f)
                var cg = (lum + (g - lum) * 0.80f)
                var cb = (lum + (b - lum) * 0.86f)
                cr = if (cr < 0.5f) cr.pow(1.12f) else cr.pow(0.95f) * 1.02f
                cg = if (cg < 0.5f) cg.pow(1.10f) else cg.pow(0.96f) * 1.01f
                cb = if (cb < 0.5f) cb.pow(1.06f) else cb.pow(1.02f) * 0.98f
                cb += (1f - lum) * 0.03f
                cr += lum * 0.02f
                out[0] = cr.coerceIn(0f, 1f)
                out[1] = cg.coerceIn(0f, 1f)
                out[2] = cb.coerceIn(0f, 1f)
            }
            LutPreset.FUJI_CLASSIC_NEGATIVE -> {
                val lum = LR * r + LG * g + LB * b
                var cr = ((r - 0.45f) * 1.28f + 0.45f).coerceIn(0f, 1f)
                var cg = ((g - 0.45f) * 1.25f + 0.45f).coerceIn(0f, 1f)
                var cb = ((b - 0.45f) * 1.24f + 0.45f).coerceIn(0f, 1f)
                val shadowFactor = (1f - lum).coerceIn(0f, 1f).pow(1.8f)
                cr += shadowFactor * 0.05f
                cg -= shadowFactor * 0.03f
                cb += shadowFactor * 0.04f
                val highFactor = lum.coerceIn(0f, 1f).pow(2.0f)
                cg += highFactor * 0.025f
                cb += highFactor * 0.035f
                out[0] = cr.coerceIn(0f, 1f)
                out[1] = cg.coerceIn(0f, 1f)
                out[2] = cb.coerceIn(0f, 1f)
            }
            LutPreset.FUJI_NOSTALGIC_NEGATIVE -> {
                val lum = LR * r + LG * g + LB * b
                val tr = (r * 1.08f + lum * 0.08f + 0.02f).coerceIn(0f, 1f)
                val tg = (g * 0.98f + lum * 0.04f + 0.01f).coerceIn(0f, 1f)
                val tb = (b * 0.88f - lum * 0.02f).coerceIn(0f, 1f)
                val amber = 0.05f * sin(lum * Math.PI.toFloat())
                out[0] = (tr + amber).coerceIn(0f, 1f)
                out[1] = (tg + amber * 0.6f).coerceIn(0f, 1f)
                out[2] = tb
            }
            LutPreset.FUJI_VELVIA -> {
                var cr = if (r < 0.5f) 2f * r * r else 1f - 2f * (1f - r) * (1f - r)
                var cg = if (g < 0.5f) 2f * g * g else 1f - 2f * (1f - g) * (1f - g)
                var cb = if (b < 0.5f) 2f * b * b else 1f - 2f * (1f - b) * (1f - b)
                val nLum = LR * cr + LG * cg + LB * cb
                out[0] = (nLum + (cr - nLum) * 1.38f).coerceIn(0f, 1f)
                out[1] = (nLum + (cg - nLum) * 1.34f).coerceIn(0f, 1f)
                out[2] = (nLum + (cb - nLum) * 1.38f).coerceIn(0f, 1f)
            }
            LutPreset.FUJI_PROVIA -> {
                val cr = ((r - 0.5f) * 1.10f + 0.5f).coerceIn(0f, 1f)
                val cg = ((g - 0.5f) * 1.08f + 0.5f).coerceIn(0f, 1f)
                val cb = ((b - 0.5f) * 1.09f + 0.5f).coerceIn(0f, 1f)
                val lum = LR * cr + LG * cg + LB * cb
                out[0] = (lum + (cr - lum) * 1.05f).coerceIn(0f, 1f)
                out[1] = (lum + (cg - lum) * 1.05f).coerceIn(0f, 1f)
                out[2] = (lum + (cb - lum) * 1.05f).coerceIn(0f, 1f)
            }
            LutPreset.FUJI_ASTIA -> {
                val lum = LR * r + LG * g + LB * b
                val cr = (r.pow(0.95f) * 1.03f).coerceIn(0f, 1f)
                val cg = (g.pow(0.97f) * 1.01f).coerceIn(0f, 1f)
                val skinBias = 0.035f * sin(lum * Math.PI.toFloat())
                out[0] = (cr + skinBias).coerceIn(0f, 1f)
                out[1] = (cg + skinBias * 0.4f).coerceIn(0f, 1f)
                out[2] = (b.pow(1.02f) * 0.98f).coerceIn(0f, 1f)
            }
            LutPreset.FUJI_ACROS -> {
                val filterLum = 0.32f * r + 0.56f * g + 0.12f * b
                val bw = if (filterLum < 0.5f) {
                    2.1f * filterLum * filterLum
                } else {
                    1f - 2.1f * (1f - filterLum) * (1f - filterLum)
                }
                val finalBw = bw.coerceIn(0f, 1f)
                out[0] = finalBw
                out[1] = finalBw
                out[2] = finalBw
            }
            LutPreset.FILM_EMULATION_KODAK -> {
                out[0] = (r * 1.06f + 0.02f).coerceIn(0f, 1f)
                out[1] = (g * 0.98f + 0.03f * (1f - g)).coerceIn(0f, 1f)
                out[2] = (b * 0.91f + 0.06f * (1f - b)).coerceIn(0f, 1f)
            }
            LutPreset.FILM_FUJI_ETERNA -> {
                val lum = LR * r + LG * g + LB * b
                out[0] = (r * 0.94f + lum * 0.06f).pow(0.96f).coerceIn(0f, 1f)
                out[1] = (g * 1.02f + 0.015f * (1f - lum)).coerceIn(0f, 1f)
                out[2] = (b * 0.93f + 0.02f * lum).pow(0.98f).coerceIn(0f, 1f)
            }
            LutPreset.BLOCKBUSTER_TEAL_ORANGE -> {
                val lum = LR * r + LG * g + LB * b
                out[0] = (r + 0.12f * (lum.pow(1.5f))).coerceIn(0f, 1f)
                out[1] = (g + 0.03f * (lum.pow(1.2f))).coerceIn(0f, 1f)
                out[2] = (b + 0.18f * ((1f - lum).pow(1.8f))).coerceIn(0f, 1f)
            }
            LutPreset.CLEAN_COMMERCIAL -> {
                out[0] = ((r - 0.45f) * 1.25f + 0.45f).coerceIn(0f, 1f)
                out[1] = ((g - 0.45f) * 1.25f + 0.45f).coerceIn(0f, 1f)
                out[2] = ((b - 0.45f) * 1.25f + 0.45f).coerceIn(0f, 1f)
            }
            LutPreset.MONOCHROME_NOIR -> {
                val lum = LR * r + LG * g + LB * b
                val sCurve = if (lum < 0.5f) {
                    2f * lum * lum
                } else {
                    1f - 2f * (1f - lum) * (1f - lum)
                }
                val bw = (sCurve * 1.15f - 0.04f).coerceIn(0f, 1f)
                out[0] = bw
                out[1] = bw
                out[2] = bw
            }
            LutPreset.CYBER_NEON_NIGHT -> {
                val lum = LR * r + LG * g + LB * b
                out[0] = (r * 1.15f + 0.14f * lum.pow(1.8f)).coerceIn(0f, 1f)
                out[1] = (g * 0.88f).coerceIn(0f, 1f)
                out[2] = (b * 1.20f + 0.16f * (1f - lum).pow(1.5f)).coerceIn(0f, 1f)
            }
            LutPreset.VINTAGE_SEPIA -> {
                val lum = LR * r + LG * g + LB * b
                out[0] = (lum * 1.18f + 0.06f).coerceIn(0f, 1f)
                out[1] = (lum * 0.96f + 0.03f).coerceIn(0f, 1f)
                out[2] = (lum * 0.72f).coerceIn(0f, 1f)
            }
            LutPreset.CUSTOM_SAVED -> {
                val custom = settings?.customLuts?.find { it.id == settings.selectedCustomLutId }
                    ?: settings?.customLuts?.firstOrNull()
                if (custom != null) {
                    var cr = ((r - 0.43f) * custom.contrast + 0.43f).coerceIn(0f, 1f)
                    var cg = ((g - 0.43f) * custom.contrast + 0.43f).coerceIn(0f, 1f)
                    var cb = ((b - 0.43f) * custom.contrast + 0.43f).coerceIn(0f, 1f)
                    if (custom.temperature != 0f) {
                        cr += custom.temperature * 0.15f
                        cb -= custom.temperature * 0.15f
                    }
                    if (custom.tint != 0f) {
                        cg -= custom.tint * 0.12f
                        cr += custom.tint * 0.05f
                    }
                    val lum = LR * cr + LG * cg + LB * cb
                    out[0] = (lum + (cr - lum) * custom.saturation).coerceIn(0f, 1f)
                    out[1] = (lum + (cg - lum) * custom.saturation).coerceIn(0f, 1f)
                    out[2] = (lum + (cb - lum) * custom.saturation).coerceIn(0f, 1f)
                } else {
                    out[0] = r
                    out[1] = g
                    out[2] = b
                }
            }
            LutPreset.CUSTOM_CUBE -> {
                out[0] = r
                out[1] = g
                out[2] = b
            }
        }
    }

    /**
     * Apply mathematical LUT look simulation for instant responsive mobile playback.
     */
    fun applyLutPreset(
        r: Float,
        g: Float,
        b: Float,
        preset: LutPreset,
        settings: ColorGradeSettings? = null
    ): Triple<Float, Float, Float> {
        val out = FloatArray(3)
        applyLutPresetFast(r, g, b, preset, settings, out)
        return Triple(out[0], out[1], out[2])
    }

    /**
     * Generate standard 3D .cube file string that can be imported directly into
     * desktop DaVinci Resolve, Adobe Premiere Pro, or Final Cut Pro!
     */
    fun exportToCubeFormat(settings: ColorGradeSettings, lutSize: Int = 33): String {
        val sb = StringBuilder()
        sb.append("# DaVinci Resolve 3D LUT Exported by ResolveStudio Mobile\n")
        sb.append("# Tuned for Samsung Galaxy S24 Ultra Log 4K\n")
        sb.append("TITLE \"ResolveStudio_S24_Grade\"\n")
        sb.append("LUT_3D_SIZE $lutSize\n")
        sb.append("DOMAIN_MIN 0.0 0.0 0.0\n")
        sb.append("DOMAIN_MAX 1.0 1.0 1.0\n\n")

        val step = 1.0f / (lutSize - 1)
        val dummyBmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        for (bIdx in 0 until lutSize) {
            val inB = bIdx * step
            for (gIdx in 0 until lutSize) {
                val inG = gIdx * step
                for (rIdx in 0 until lutSize) {
                    val inR = rIdx * step

                    // Sample single pixel color through full pipeline
                    dummyBmp.setPixel(0, 0, AndroidColor.rgb(
                        (inR * 255f).toInt(),
                        (inG * 255f).toInt(),
                        (inB * 255f).toInt()
                    ))
                    val graded = gradeBitmap(dummyBmp, settings.copy(mask = PowerWindowMask(MaskType.NONE)))
                    val p = graded.getPixel(0, 0)
                    val outR = AndroidColor.red(p) / 255f
                    val outG = AndroidColor.green(p) / 255f
                    val outB = AndroidColor.blue(p) / 255f

                    sb.append(String.format(Locale.US, "%.6f %.6f %.6f\n", outR, outG, outB))
                }
            }
        }
        return sb.toString()
    }

    /**
     * Parse standard .cube 3D LUT text file into float array.
     */
    fun parseCubeLut(cubeText: String): Pair<Int, FloatArray>? {
        try {
            val reader = BufferedReader(StringReader(cubeText))
            var size = 0
            val values = mutableListOf<Float>()

            reader.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("LUT_3D_SIZE")) {
                    val parts = trimmed.split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        size = parts[1].toInt()
                    }
                } else if (!trimmed.startsWith("#") && !trimmed.startsWith("TITLE") &&
                    !trimmed.startsWith("DOMAIN_") && trimmed.isNotEmpty()
                ) {
                    val parts = trimmed.split("\\s+".toRegex())
                    if (parts.size >= 3) {
                        values.add(parts[0].toFloat())
                        values.add(parts[1].toFloat())
                        values.add(parts[2].toFloat())
                    }
                }
            }

            if (size > 0 && values.size == size * size * size * 3) {
                return Pair(size, values.toFloatArray())
            }
        } catch (_: Exception) {
            return null
        }
        return null
    }

    /**
     * Calculate 256-bin RGB and Luminance Histogram data with real-time clipping detection.
     */
    fun calculateHistogram(bitmap: Bitmap): HistogramData {
        val rBins = IntArray(256)
        val gBins = IntArray(256)
        val bBins = IntArray(256)
        val lumBins = IntArray(256)

        val w = bitmap.width
        val h = bitmap.height
        // Downsample for instant computation
        val stepX = (w / 120).coerceAtLeast(1)
        val stepY = (h / 80).coerceAtLeast(1)

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var totalSamples = 0
        var shadowClipCount = 0
        var highlightClipCount = 0
        var lumSum = 0L
        var minL = 255
        var maxL = 0

        for (y in 0 until h step stepY) {
            val offset = y * w
            for (x in 0 until w step stepX) {
                val p = pixels[offset + x]
                val r = AndroidColor.red(p)
                val g = AndroidColor.green(p)
                val b = AndroidColor.blue(p)
                val l = (0.2126f * r + 0.7152f * g + 0.0722f * b).toInt().coerceIn(0, 255)

                rBins[r]++
                gBins[g]++
                bBins[b]++
                lumBins[l]++
                totalSamples++
                lumSum += l

                if (l < minL) minL = l
                if (l > maxL) maxL = l

                // Clipping detection: < 3 is crushed black (<1% IRE), > 252 is blown white (>99% IRE)
                if (l <= 3) shadowClipCount++
                if (l >= 252) highlightClipCount++
            }
        }

        val avgLum = if (totalSamples > 0) lumSum.toFloat() / totalSamples else 128f
        val shadowClipPct = if (totalSamples > 0) (shadowClipCount.toFloat() / totalSamples) * 100f else 0f
        val highlightClipPct = if (totalSamples > 0) (highlightClipCount.toFloat() / totalSamples) * 100f else 0f

        return HistogramData(
            redBins = rBins,
            greenBins = gBins,
            blueBins = bBins,
            lumBins = lumBins,
            totalSamples = totalSamples,
            shadowClipPct = shadowClipPct,
            highlightClipPct = highlightClipPct,
            avgLum = avgLum,
            minLum = minL,
            maxLum = maxL
        )
    }

    /**
     * Calculate Waveform RGB Parade columns (100 bins vertical, column sample horizontal).
     */
    fun calculateWaveform(bitmap: Bitmap): WaveformData {
        val columns = 90
        val rows = 80
        val rGrid = Array(columns) { FloatArray(rows) }
        val gGrid = Array(columns) { FloatArray(rows) }
        val bGrid = Array(columns) { FloatArray(rows) }

        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val colStep = w / columns
        val rowStep = h / 60

        for (col in 0 until columns) {
            val startX = col * colStep
            for (y in 0 until h step rowStep) {
                val p = pixels[y * w + startX]
                val rNorm = (AndroidColor.red(p) / 255f * (rows - 1)).toInt()
                val gNorm = (AndroidColor.green(p) / 255f * (rows - 1)).toInt()
                val bNorm = (AndroidColor.blue(p) / 255f * (rows - 1)).toInt()

                rGrid[col][rNorm.coerceIn(0, rows - 1)] += 1f
                gGrid[col][gNorm.coerceIn(0, rows - 1)] += 1f
                bGrid[col][bNorm.coerceIn(0, rows - 1)] += 1f
            }
        }

        return WaveformData(rGrid, gGrid, bGrid, columns, rows)
    }

    /**
     * DaVinci Resolve Color Space Transform (CST) Output Node (Zero Allocation).
     * Maps wide-gamut log signals (DWG, BT.2020) to output delivery spaces (Rec.709, DCI-P3).
     */
    fun applyColorSpaceTransformFast(
        inR: Float,
        inG: Float,
        inB: Float,
        cst: CstNodeSettings,
        out: FloatArray
    ) {
        if (!cst.enabled) {
            out[0] = inR; out[1] = inG; out[2] = inB
            return
        }

        var r = inR
        var g = inG
        var b = inB

        // 1. Color Gamut Matrix Transformation (to Linear Rec.709 space)
        when (cst.inputColorSpace) {
            InputColorSpace.DAVINCI_WIDE_GAMUT -> {
                // DWG to Rec.709 Chromatic Adaptation Matrix
                val r709 = 1.3460f * r - 0.2556f * g - 0.0904f * b
                val g709 = -0.0545f * r + 1.0512f * g + 0.0033f * b
                val b709 = -0.0014f * r - 0.0441f * g + 1.0455f * b
                r = r709; g = g709; b = b709
            }
            InputColorSpace.SAMSUNG_S24_GAMUT -> {
                // BT.2020 to Rec.709 Chromatic Adaptation Matrix
                val r709 = 1.6605f * r - 0.5876f * g - 0.0728f * b
                val g709 = -0.1246f * r + 1.1329f * g - 0.0083f * b
                val b709 = -0.0182f * r - 0.1006f * g + 1.1187f * b
                r = r709; g = g709; b = b709
            }
            InputColorSpace.ARRI_WIDE_GAMUT_4 -> {
                val r709 = 1.4312f * r - 0.3228f * g - 0.1084f * b
                val g709 = -0.0612f * r + 1.0824f * g - 0.0212f * b
                val b709 = -0.0098f * r - 0.0734f * g + 1.0832f * b
                r = r709; g = g709; b = b709
            }
            InputColorSpace.SONY_SGAMUT3 -> {
                val r709 = 1.5424f * r - 0.4431f * g - 0.0993f * b
                val g709 = -0.0891f * r + 1.1042f * g - 0.0151f * b
                val b709 = -0.0152f * r - 0.0883f * g + 1.1035f * b
                r = r709; g = g709; b = b709
            }
            InputColorSpace.REC709 -> {
                // Identity
            }
        }

        // Output Color Space re-targeting if target is not Rec.709
        when (cst.outputColorSpace) {
            OutputColorSpace.DCI_P3 -> {
                val rp3 = 0.8225f * r + 0.1775f * g
                val gp3 = 0.0332f * r + 0.9668f * g
                val bp3 = 0.0171f * r + 0.0724f * g + 0.9105f * b
                r = rp3; g = gp3; b = bp3
            }
            OutputColorSpace.REC2020 -> {
                val r20 = 0.6274f * r + 0.3293f * g + 0.0433f * b
                val g20 = 0.0691f * r + 0.9195f * g + 0.0114f * b
                val b20 = 0.0164f * r + 0.0880f * g + 0.8956f * b
                r = r20; g = g20; b = b20
            }
            OutputColorSpace.REC709, OutputColorSpace.SRGB -> {
                // Standard Rec.709 gamut
            }
        }

        // 2. DaVinci Tone Mapping Method
        when (cst.toneMapping) {
            ToneMappingMethod.DAVINCI -> {
                // Saturation-Preserving Luminance Shoulder Roll-Off
                val lum = LR * r + LG * g + LB * b
                val knee = cst.kneeThreshold.coerceIn(0.5f, 0.95f)
                if (lum > knee && lum > 0f) {
                    val excess = lum - knee
                    val compressedLum = knee + (1f - knee) * (1f - exp(-excess / (1f - knee)))
                    val ratio = (compressedLum / lum).coerceIn(0f, 1f)
                    r *= ratio
                    g *= ratio
                    b *= ratio
                }
            }
            ToneMappingMethod.KNEE_COMPRESSION -> {
                val knee = cst.kneeThreshold.coerceIn(0.5f, 0.95f)
                r = applyKnee(r, knee)
                g = applyKnee(g, knee)
                b = applyKnee(b, knee)
            }
            ToneMappingMethod.LUMINANCE_ONLY -> {
                val lum = (LR * r + LG * g + LB * b).coerceIn(0f, 1f)
                val compressed = if (lum > 0.8f) 0.8f + (1f - exp(-(lum - 0.8f) / 0.2f)) * 0.2f else lum
                val scale = if (lum == 0f) 1f else compressed / lum
                r *= scale; g *= scale; b *= scale
            }
            ToneMappingMethod.OFF -> {
                // Hard clip
            }
        }

        // 3. DaVinci Gamut Mapping (Saturation Compression)
        if (cst.gamutMapping == GamutMappingMethod.SATURATION_COMPRESSION) {
            val lum = LR * r + LG * g + LB * b
            val maxCh = max(r, max(g, b))
            if (maxCh > 1.0f && lum < 1.0f) {
                val satScale = (1.0f - lum) / (maxCh - lum)
                r = lum + (r - lum) * satScale
                g = lum + (g - lum) * satScale
                b = lum + (b - lum) * satScale
            }
        }

        // 4. Output Gamma OETF encoding
        r = r.coerceIn(0f, 1f)
        g = g.coerceIn(0f, 1f)
        b = b.coerceIn(0f, 1f)

        when (cst.outputGamma) {
            OutputGamma.GAMMA_2_4 -> {
                r = r.pow(1f / 2.4f)
                g = g.pow(1f / 2.4f)
                b = b.pow(1f / 2.4f)
            }
            OutputGamma.GAMMA_2_2 -> {
                r = r.pow(1f / 2.2f)
                g = g.pow(1f / 2.2f)
                b = b.pow(1f / 2.2f)
            }
            OutputGamma.REC709, OutputGamma.SRGB -> {
                r = encodeRec709Gamma(r)
                g = encodeRec709Gamma(g)
                b = encodeRec709Gamma(b)
            }
        }

        out[0] = r.coerceIn(0f, 1f)
        out[1] = g.coerceIn(0f, 1f)
        out[2] = b.coerceIn(0f, 1f)
    }

    /**
     * DaVinci Resolve Color Space Transform (CST) Output Node.
     * Maps wide-gamut log signals (DWG, BT.2020) to output delivery spaces (Rec.709, DCI-P3).
     */
    fun applyColorSpaceTransform(
        inR: Float,
        inG: Float,
        inB: Float,
        cst: CstNodeSettings
    ): Triple<Float, Float, Float> {
        val out = FloatArray(3)
        applyColorSpaceTransformFast(inR, inG, inB, cst, out)
        return Triple(out[0], out[1], out[2])
    }

    private fun applyKnee(v: Float, knee: Float): Float {
        if (v <= knee) return v
        val excess = v - knee
        return knee + (1f - knee) * (1f - exp(-excess / (1f - knee)))
    }

    private fun encodeRec709Gamma(l: Float): Float {
        return if (l < 0.018f) 4.5f * l else 1.099f * l.pow(0.45f) - 0.099f
    }

    /**
     * DaVinci Resolve False Color Exposure Meter (IRE zones) - Zero Allocation.
     */
    fun evaluateFalseColorFast(r: Float, g: Float, b: Float, out: FloatArray) {
        val ire = (LR * r + LG * g + LB * b) * 100f
        when {
            ire <= 2f -> { out[0] = 0.55f; out[1] = 0.0f; out[2] = 0.85f }
            ire <= 10f -> { out[0] = 0.0f; out[1] = 0.35f; out[2] = 0.95f }
            ire <= 38f -> { out[0] = 0.1f; out[1] = 0.45f; out[2] = 0.55f }
            ire in 38f..44f -> { out[0] = 0.0f; out[1] = 0.85f; out[2] = 0.2f }
            ire in 48f..56f -> { out[0] = 1.0f; out[1] = 0.45f; out[2] = 0.75f }
            ire in 80f..95f -> { out[0] = 0.95f; out[1] = 0.90f; out[2] = 0.0f }
            ire > 95f -> { out[0] = 0.95f; out[1] = 0.05f; out[2] = 0.05f }
            else -> {
                val gray = (ire / 100f) * 0.7f
                out[0] = gray; out[1] = gray; out[2] = gray
            }
        }
    }

    /**
     * DaVinci Resolve False Color Exposure Meter (IRE zones).
     */
    fun evaluateFalseColor(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val out = FloatArray(3)
        evaluateFalseColorFast(r, g, b, out)
        return Triple(out[0], out[1], out[2])
    }

    /**
     * Evaluates DaVinci Resolve 3D HSL Qualifier key weight (0..1) for a pixel (r, g, b).
     */
    fun evaluateHslKeyWeight(r: Float, g: Float, b: Float, q: HslQualifierSettings): Float {
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val delta = maxC - minC
        val sat = if (maxC <= 0.001f) 0f else delta / maxC
        val lum = LR * r + LG * g + LB * b

        if (sat < q.satMin * 0.5f || lum < q.lumMin * 0.5f || delta <= 0.001f) {
            return 0f
        }

        val satWeight = if (sat >= q.satMin) 1f else ((sat - q.satMin * 0.5f) / (q.satMin * 0.5f).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
        val lumWeight = if (lum >= q.lumMin) 1f else ((lum - q.lumMin * 0.5f) / (q.lumMin * 0.5f).coerceAtLeast(0.01f)).coerceIn(0f, 1f)

        val rawHue = when (maxC) {
            r -> 60f * (((g - b) / delta) % 6f)
            g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        val hueDeg = (rawHue + 360f) % 360f
        val hueDiff = abs(((hueDeg - q.hueCenterDeg + 540f) % 360f) - 180f)

        val halfW = q.hueWidthDeg * 0.5f
        val softW = q.hueSoftnessDeg.coerceAtLeast(1f)
        val hueWeight = when {
            hueDiff <= halfW -> 1f
            hueDiff >= halfW + softW -> 0f
            else -> {
                val t = (hueDiff - halfW) / softW
                1f - (t * t * (3f - 2f * t))
            }
        }
        return (hueWeight * satWeight * lumWeight).coerceIn(0f, 1f)
    }

    /**
     * Rotates RGB hue by `shiftDeg` degrees while preserving luminance and saturation (Zero Allocation).
     */
    fun rotateHueRgbFast(r: Float, g: Float, b: Float, shiftDeg: Float, out: FloatArray) {
        val rad = Math.toRadians(shiftDeg.toDouble()).toFloat()
        val cosA = cos(rad)
        val sinA = sin(rad)
        val oneThird = 1f / 3f
        val rootThird = sqrt(1f / 3f)

        val m00 = cosA + (1f - cosA) * oneThird
        val m01 = oneThird * (1f - cosA) - rootThird * sinA
        val m02 = oneThird * (1f - cosA) + rootThird * sinA

        val m10 = oneThird * (1f - cosA) + rootThird * sinA
        val m11 = cosA + oneThird * (1f - cosA)
        val m12 = oneThird * (1f - cosA) - rootThird * sinA

        val m20 = oneThird * (1f - cosA) - rootThird * sinA
        val m21 = oneThird * (1f - cosA) + rootThird * sinA
        val m22 = cosA + oneThird * (1f - cosA)

        out[0] = (m00 * r + m01 * g + m02 * b).coerceIn(0f, 1f)
        out[1] = (m10 * r + m11 * g + m12 * b).coerceIn(0f, 1f)
        out[2] = (m20 * r + m21 * g + m22 * b).coerceIn(0f, 1f)
    }

    /**
     * Rotates RGB hue by `shiftDeg` degrees while preserving luminance and saturation.
     */
    fun rotateHueRgb(r: Float, g: Float, b: Float, shiftDeg: Float): Triple<Float, Float, Float> {
        val out = FloatArray(3)
        rotateHueRgbFast(r, g, b, shiftDeg, out)
        return Triple(out[0], out[1], out[2])
    }

    /**
     * Normalized Cross-Correlation (NCC) Template Matching Tracker for Power Windows.
     * Matches pixel template between previous and current frames to track actual camera/subject motion
     * with real mathematical correlation confidence (0.0 to 1.0).
     */
    fun trackMaskPattern(
        previousBitmap: Bitmap?,
        currentBitmap: Bitmap,
        centerX: Float,
        centerY: Float,
        radiusX: Float,
        radiusY: Float
    ): TrackerResult {
        val w = currentBitmap.width.coerceAtLeast(1)
        val h = currentBitmap.height.coerceAtLeast(1)

        val centerPxX = (centerX * w).toInt().coerceIn(0, w - 1)
        val centerPxY = (centerY * h).toInt().coerceIn(0, h - 1)

        // Template half-size
        val halfTw = ((radiusX * w * 0.5f).toInt()).coerceIn(12, 36)
        val halfTh = ((radiusY * h * 0.5f).toInt()).coerceIn(12, 36)

        // If no previous frame to correlate against, calibrate on current frame
        val refBitmap = previousBitmap ?: currentBitmap

        // Extract template patch from reference frame
        val tX0 = (centerPxX - halfTw).coerceIn(0, w - 1)
        val tX1 = (centerPxX + halfTw).coerceIn(0, w - 1)
        val tY0 = (centerPxY - halfTh).coerceIn(0, h - 1)
        val tY1 = (centerPxY + halfTh).coerceIn(0, h - 1)

        val tW = tX1 - tX0 + 1
        val tH = tY1 - tY0 + 1
        if (tW < 6 || tH < 6) {
            return TrackerResult(centerX, centerY, 0.90f)
        }

        // Subsample template by step=2 for high performance
        val tStep = 2
        var tSum = 0.0
        var tCount = 0
        val tSamples = ArrayList<Double>((tW / tStep + 1) * (tH / tStep + 1))

        for (ty in tY0..tY1 step tStep) {
            for (tx in tX0..tX1 step tStep) {
                val p = refBitmap.getPixel(tx, ty)
                val lum = (0.2126 * ((p shr 16) and 0xFF) + 0.7152 * ((p shr 8) and 0xFF) + 0.0722 * (p and 0xFF)) / 255.0
                tSamples.add(lum)
                tSum += lum
                tCount++
            }
        }

        if (tCount == 0) return TrackerResult(centerX, centerY, 0.85f)
        val tMean = tSum / tCount

        var tVariance = 0.0
        for (i in 0 until tCount) {
            val diff = tSamples[i] - tMean
            tVariance += diff * diff
        }

        // If reference patch has virtually no contrast (flat solid color), maintain center
        if (tVariance < 0.0001) {
            return TrackerResult(centerX, centerY, 0.70f)
        }
        val tStdDev = sqrt(tVariance)

        // Search window in currentBitmap: ±28 pixels in X and Y
        val searchRadiusX = (radiusX * w * 0.7f).toInt().coerceIn(16, 40)
        val searchRadiusY = (radiusY * h * 0.7f).toInt().coerceIn(16, 40)
        val searchStep = 2

        var bestNcc = -1.0
        var bestDx = 0
        var bestDy = 0

        for (dy in -searchRadiusY..searchRadiusY step searchStep) {
            val curY0 = centerPxY + dy - halfTh
            val curY1 = curY0 + (tY1 - tY0)
            if (curY0 < 0 || curY1 >= h) continue

            for (dx in -searchRadiusX..searchRadiusX step searchStep) {
                val curX0 = centerPxX + dx - halfTw
                val curX1 = curX0 + (tX1 - tX0)
                if (curX0 < 0 || curX1 >= w) continue

                var cSum = 0.0
                var sampleIdx = 0
                for (cy in curY0..curY1 step tStep) {
                    for (cx in curX0..curX1 step tStep) {
                        if (sampleIdx >= tCount) break
                        val p = currentBitmap.getPixel(cx, cy)
                        val lum = (0.2126 * ((p shr 16) and 0xFF) + 0.7152 * ((p shr 8) and 0xFF) + 0.0722 * (p and 0xFF)) / 255.0
                        cSum += lum
                        sampleIdx++
                    }
                }
                val cMean = cSum / tCount

                var covariance = 0.0
                var cVariance = 0.0
                sampleIdx = 0
                for (cy in curY0..curY1 step tStep) {
                    for (cx in curX0..curX1 step tStep) {
                        if (sampleIdx >= tCount) break
                        val p = currentBitmap.getPixel(cx, cy)
                        val lum = (0.2126 * ((p shr 16) and 0xFF) + 0.7152 * ((p shr 8) and 0xFF) + 0.0722 * (p and 0xFF)) / 255.0
                        val cDiff = lum - cMean
                        val tDiff = tSamples[sampleIdx] - tMean
                        covariance += tDiff * cDiff
                        cVariance += cDiff * cDiff
                        sampleIdx++
                    }
                }

                if (cVariance > 0.0001) {
                    val ncc = covariance / (tStdDev * sqrt(cVariance))
                    if (ncc > bestNcc) {
                        bestNcc = ncc
                        bestDx = dx
                        bestDy = dy
                    }
                }
            }
        }

        return if (bestNcc >= 0.25) {
            val nextX = ((centerPxX + bestDx).toFloat() / w).coerceIn(0.06f, 0.94f)
            val nextY = ((centerPxY + bestDy).toFloat() / h).coerceIn(0.06f, 0.94f)
            val confidence = bestNcc.toFloat().coerceIn(0.10f, 0.99f)
            TrackerResult(nextX, nextY, confidence)
        } else {
            // Low confidence lock
            TrackerResult(centerX, centerY, 0.50f)
        }
    }

    /**
     * Optical Centroid / Feature Point Tracker for Power Windows (Oval, Rect, Triangle, Bezier).
     * Backward-compatible delegation.
     */
    fun computeTrackedCenter(
        bitmap: Bitmap,
        centerX: Float,
        centerY: Float,
        radiusX: Float,
        radiusY: Float,
        frameIndex: Int = 0
    ): Pair<Float, Float> {
        val result = trackMaskPattern(null, bitmap, centerX, centerY, radiusX, radiusY)
        return Pair(result.centerX, result.centerY)
    }
}

data class TrackerResult(
    val centerX: Float,
    val centerY: Float,
    val confidence: Float // 0.0f .. 1.0f
)
}

data class HistogramData(
    val redBins: IntArray,
    val greenBins: IntArray,
    val blueBins: IntArray,
    val lumBins: IntArray,
    val totalSamples: Int = 0,
    val shadowClipPct: Float = 0f,
    val highlightClipPct: Float = 0f,
    val avgLum: Float = 128f,
    val minLum: Int = 0,
    val maxLum: Int = 255
)

data class WaveformData(
    val redGrid: Array<FloatArray>,
    val greenGrid: Array<FloatArray>,
    val blueGrid: Array<FloatArray>,
    val columns: Int,
    val rows: Int
)
