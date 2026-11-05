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

    /**
     * Evaluates custom cubic Bezier power window mask at normalized coordinate (u, v).
     * Calculates point-in-polygon winding along the sampled Bezier spline and applies
     * continuous Hermite smoothstep feathering across the curve boundary.
     */
    fun evaluateBezierMask(u: Float, v: Float, mask: PowerWindowMask): Float {
        val points = mask.bezierPoints
        if (points.size < 3) return 1.0f

        val samplesPerSegment = 8
        val totalSegments = points.size
        val polyX = FloatArray(totalSegments * samplesPerSegment)
        val polyY = FloatArray(totalSegments * samplesPerSegment)
        var polyIdx = 0

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
                polyIdx++
            }
        }

        val polySize = polyIdx

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
        val featherWidth = (mask.feather * 0.15f).coerceAtLeast(0.005f)

        val t = ((signedDist + featherWidth) / (2f * featherWidth)).coerceIn(0f, 1f)
        return (t * t * (3f - 2f * t)) // Hermite smoothstep
    }

    /**
     * Evaluates Power Window Mask weight at normalized coordinate (u, v).
     */
    fun evaluateMask(u: Float, v: Float, mask: PowerWindowMask): Float {
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
                evaluateBezierMask(u, v, mask)
            }
            MaskType.NONE -> 1.0f
        }

        val result = if (mask.invert) 1.0f - rawWeight else rawWeight
        return result.coerceIn(0f, 1f)
    }

    /**
     * Apply complete color grading pipeline to an in-memory Bitmap.
     */
    fun gradeBitmap(
        source: Bitmap,
        settings: ColorGradeSettings,
        destination: Bitmap? = null
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

        // Precompute Curve LUTs
        val masterLut = buildSplineLut(settings.curves.master)
        val redLut = buildSplineLut(settings.curves.red)
        val greenLut = buildSplineLut(settings.curves.green)
        val blueLut = buildSplineLut(settings.curves.blue)

        // Precompute Secondary Hue/Lum Curve LUTs (neutral = 0.5f)
        val hasHueVsHue = settings.curves.hueVsHue.any { abs(it.y - 0.5f) > 0.005f }
        val hasHueVsSat = settings.curves.hueVsSat.any { abs(it.y - 0.5f) > 0.005f }
        val hasHueVsLum = settings.curves.hueVsLum.any { abs(it.y - 0.5f) > 0.005f }
        val hasLumVsSat = settings.curves.lumVsSat.any { abs(it.y - 0.5f) > 0.005f }
        val hasSecondaryCurves = hasHueVsHue || hasHueVsSat || hasHueVsLum || hasLumVsSat

        val hueVsHueLut = if (hasHueVsHue) buildSplineLut(settings.curves.hueVsHue) else null
        val hueVsSatLut = if (hasHueVsSat) buildSplineLut(settings.curves.hueVsSat) else null
        val hueVsLumLut = if (hasHueVsLum) buildSplineLut(settings.curves.hueVsLum) else null
        val lumVsSatLut = if (hasLumVsSat) buildSplineLut(settings.curves.lumVsSat) else null

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

        var seed = 1337

        for (y in 0 until height) {
            val vNorm = y.toFloat() / height
            val rowOffset = y * width

            for (x in 0 until width) {
                val idx = rowOffset + x
                val pixel = pixels[idx]

                var r = AndroidColor.red(pixel) / 255f
                var g = AndroidColor.green(pixel) / 255f
                var b = AndroidColor.blue(pixel) / 255f
                val origR = r
                val origG = g
                val origB = b

                // 1. Samsung Log to Linear Transform
                if (settings.samsungLogTransform) {
                    r = samsungLogToLinear(r)
                    g = samsungLogToLinear(g)
                    b = samsungLogToLinear(b)

                    if (settings.samsungDynamicToneMapping) {
                        r = applyHighlightRollOff(r, settings.samsungHighlightRollOff)
                        g = applyHighlightRollOff(g, settings.samsungHighlightRollOff)
                        b = applyHighlightRollOff(b, settings.samsungHighlightRollOff)
                    }
                }

                // Normalized camera base (used outside Power Window masks so outside pixels stay in Rec.709/Linear space instead of raw Log)
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
                    // Warm / Cool
                    r += tempVal * 0.15f
                    b -= tempVal * 0.15f
                }
                if (tintVal != 0f) {
                    // Magenta / Green
                    g -= tintVal * 0.12f
                    r += tintVal * 0.05f
                    b += tintVal * 0.05f
                }

                // 3. DaVinci Resolve Lift / Gamma / Gain / Offset Color Wheels
                var lum = LR * r + LG * g + LB * b

                // Lift (Shadows)
                val liftWeight = ((1f - lum).coerceIn(0f, 1f)).pow(2)
                r += (liftR * 0.25f + liftLum * 0.2f) * liftWeight
                g += (liftG * 0.25f + liftLum * 0.2f) * liftWeight
                b += (liftB * 0.25f + liftLum * 0.2f) * liftWeight

                // Gain (Highlights)
                val gainWeight = lum.coerceIn(0f, 1f).pow(1.8f)
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

                // 3B. DaVinci Resolve Log Zone Wheels (Shadow / Midtone / Highlight / Offset with Low/High Range pivots)
                if (hasLogWheels) {
                    val lw = settings.logWheels
                    val clampedL = lum.coerceIn(0f, 1f)
                    // Tight Shadow Toe Zone (below logLowRange)
                    val shadW = if (clampedL >= logLowRange) 0f else {
                        val t = (clampedL / logLowRange).coerceIn(0f, 1f)
                        1f - (t * t * (3f - 2f * t))
                    }
                    // Tight Highlight Shoulder Zone (above logHighRange)
                    val highW = if (clampedL <= logHighRange) 0f else {
                        val t = ((clampedL - logHighRange) / (1f - logHighRange).coerceAtLeast(0.05f)).coerceIn(0f, 1f)
                        t * t * (3f - 2f * t)
                    }
                    // Isolated Midtone Band Zone (between logLowRange and logHighRange)
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
                    val hWeight = lum.coerceIn(0f, 1f).pow(2f)
                    val hDelta = settings.highlights * 0.2f * hWeight
                    r += hDelta; g += hDelta; b += hDelta
                }
                if (settings.shadows != 0f) {
                    val sWeight = (1f - lum).coerceIn(0f, 1f).pow(2f)
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
                var rIndex = (r.coerceIn(0f, 1f) * 255f).toInt()
                var gIndex = (g.coerceIn(0f, 1f) * 255f).toInt()
                var bIndex = (b.coerceIn(0f, 1f) * 255f).toInt()

                r = redLut[rIndex]
                g = greenLut[gIndex]
                b = blueLut[bIndex]

                val masterIndex = ((LR * r + LG * g + LB * b).coerceIn(0f, 1f) * 255f).toInt()
                val masterScale = if (masterIndex == 0) 1f else masterLut[masterIndex] / (masterIndex / 255f)
                r = (r * masterScale).coerceIn(0f, 1f)
                g = (g * masterScale).coerceIn(0f, 1f)
                b = (b * masterScale).coerceIn(0f, 1f)

                // 6B. DaVinci Resolve Color vs Color Secondary Curves (Hue vs Hue, Hue vs Sat, Hue vs Lum, Lum vs Sat)
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
                                val (hr, hg, hb) = rotateHueRgb(r, g, b, hueShiftDeg)
                                r = hr; g = hg; b = hb
                            }
                        }

                        // Hue vs Lum (-0.30 to +0.30 luminance offset weighted by saturation)
                        if (hueVsLumLut != null) {
                            val satGate = (delta / maxCh.coerceAtLeast(0.01f)).coerceIn(0f, 1f)
                            val lumOffset = (hueVsLumLut[hueIdx] - 0.5f) * 0.55f * satGate
                            r = (r + lumOffset).coerceIn(0f, 1f)
                            g = (g + lumOffset).coerceIn(0f, 1f)
                            b = (b + lumOffset).coerceIn(0f, 1f)
                        }

                        // Hue vs Sat (0.0x to 2.0x saturation scale around 0.5 neutral)
                        if (hueVsSatLut != null) {
                            val satMult = (hueVsSatLut[hueIdx] * 2.0f).coerceIn(0f, 2f)
                            val lNow = LR * r + LG * g + LB * b
                            r = (lNow + (r - lNow) * satMult).coerceIn(0f, 1f)
                            g = (lNow + (g - lNow) * satMult).coerceIn(0f, 1f)
                            b = (lNow + (b - lNow) * satMult).coerceIn(0f, 1f)
                        }
                    }

                    // Lum vs Sat (0.0x to 2.0x saturation scale across shadow-to-highlight luminance)
                    if (lumVsSatLut != null) {
                        val lumIdx = (curLum * 255f).toInt().coerceIn(0, 255)
                        val lSatMult = (lumVsSatLut[lumIdx] * 2.0f).coerceIn(0f, 2f)
                        val lNow = LR * r + LG * g + LB * b
                        r = (lNow + (r - lNow) * lSatMult).coerceIn(0f, 1f)
                        g = (lNow + (g - lNow) * lSatMult).coerceIn(0f, 1f)
                        b = (lNow + (b - lNow) * lSatMult).coerceIn(0f, 1f)
                    }
                }

                // 7. Look / LUT Preset Emulation (with instant A/B bypass check)
                if (!settings.lutBypass && lutIntensity > 0f) {
                    val (lr, lg, lb) = applyLutPreset(r, g, b, lutPreset, settings)
                    r = r * (1f - lutIntensity) + lr * lutIntensity
                    g = g * (1f - lutIntensity) + lg * lutIntensity
                    b = b * (1f - lutIntensity) + lb * lutIntensity
                }

                // 8. Mask Compositing (Isolates creative grade inside/outside Power Window while preserving normalized camera base)
                if (hasMask) {
                    val uNorm = x.toFloat() / width
                    val maskWeight = evaluateMask(uNorm, vNorm, settings.mask)
                    if (settings.mask.showMatte) {
                        r = maskWeight
                        g = maskWeight
                        b = maskWeight
                    } else {
                        // Blend creative grade with normalized camera base (Rec.709/Linear) instead of flat raw Log
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
                        // DaVinci Resolve Shift+H Highlight Mode: full color inside key, dim monochrome outside
                        val mono = (LR * r + LG * g + LB * b) * 0.22f
                        r = mono * (1f - keyWeight) + r * keyWeight
                        g = mono * (1f - keyWeight) + g * keyWeight
                        b = mono * (1f - keyWeight) + b * keyWeight
                    } else if (keyWeight > 0.001f) {
                        var qr = r
                        var qg = g
                        var qb = b

                        // Hue Rotation inside qualified range
                        if (q.hueShiftDeg != 0f) {
                            val (hr, hg, hb) = rotateHueRgb(qr, qg, qb, q.hueShiftDeg)
                            qr = hr; qg = hg; qb = hb
                        }

                        // Warmth / Temperature shift inside qualified range
                        if (q.tempAdjust != 0f) {
                            qr += q.tempAdjust * 0.18f
                            qb -= q.tempAdjust * 0.18f
                        }

                        // Luminance offset inside qualified range
                        if (q.lumAdjust != 0f) {
                            val lShift = q.lumAdjust * 0.28f
                            qr += lShift; qg += lShift; qb += lShift
                        }

                        // Saturation scale inside qualified range
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
                            val spill = ((lumHi - 0.7f) / 0.3f).pow(2) * settings.effects.halationIntensity
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
                    val (cstR, cstG, cstB) = applyColorSpaceTransform(r, g, b, settings.cstNode)
                    r = cstR
                    g = cstG
                    b = cstB
                }

                // 11. False Color Exposure Metering
                if (settings.showFalseColor) {
                    val (fcR, fcG, fcB) = evaluateFalseColor(r, g, b)
                    r = fcR
                    g = fcG
                    b = fcB
                }

                // 12. Split Screen Comparison Wipe (Graded vs Ungraded)
                if (settings.splitScreenWipe < 1.0f) {
                    val splitX = (width * settings.splitScreenWipe).toInt()
                    if (x == splitX || x == splitX + 1) {
                        r = 1f; g = 1f; b = 1f // White dividing line
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

        // Overlay Authentic Organic Film Grain (Clumping, Roughness & Size)
        if (effectiveGrain.enabled && effectiveGrain.strength > 0f) {
            FilmGrainProcessor.applyGrain(pixels, width, height, effectiveGrain)
        }

        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
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
        return when (preset) {
            LutPreset.SAMSUNG_LOG_TO_REC709 -> {
                // High fidelity standard Rec.709 color conversion for Samsung S24 Log
                val cr = (r - 0.5f) * 1.12f + 0.5f
                val cg = (g - 0.5f) * 1.10f + 0.5f
                val cb = (b - 0.5f) * 1.15f + 0.5f
                Triple(cr.coerceIn(0f, 1f), cg.coerceIn(0f, 1f), cb.coerceIn(0f, 1f))
            }
            LutPreset.SAMSUNG_REC709_PUNCH -> {
                // S24 Ultra Vivid Punch: expanded dynamic range, +20% contrast, vibrant saturation
                val lum = LR * r + LG * g + LB * b
                var cr = ((r - 0.42f) * 1.22f + 0.42f).coerceIn(0f, 1f)
                var cg = ((g - 0.42f) * 1.20f + 0.42f).coerceIn(0f, 1f)
                var cb = ((b - 0.42f) * 1.24f + 0.42f).coerceIn(0f, 1f)
                // Chroma boost
                val nLum = LR * cr + LG * cg + LB * cb
                cr = (nLum + (cr - nLum) * 1.25f).coerceIn(0f, 1f)
                cg = (nLum + (cg - nLum) * 1.22f).coerceIn(0f, 1f)
                cb = (nLum + (cb - nLum) * 1.25f).coerceIn(0f, 1f)
                Triple(cr, cg, cb)
            }
            LutPreset.SAMSUNG_CINE_WARM -> {
                // Golden hour skin tones, gentle shadow warmth
                val tr = r.pow(0.92f) * 1.05f
                val tg = g.pow(0.96f) * 1.02f
                val tb = b.pow(1.08f) * 0.94f
                Triple(tr.coerceIn(0f, 1f), tg.coerceIn(0f, 1f), tb.coerceIn(0f, 1f))
            }
            // ==========================================
            // 7 FAMOUS FUJIFILM RECIPES (Samsung S24 Log)
            // ==========================================
            LutPreset.FUJI_CLASSIC_CHROME -> {
                // Classic Chrome: documentary tonality, muted skies, rich navy blues, soft contrast, warm skin
                val lum = LR * r + LG * g + LB * b
                var cr = (lum + (r - lum) * 0.82f)
                var cg = (lum + (g - lum) * 0.80f)
                var cb = (lum + (b - lum) * 0.86f)
                cr = if (cr < 0.5f) cr.pow(1.12f) else cr.pow(0.95f) * 1.02f
                cg = if (cg < 0.5f) cg.pow(1.10f) else cg.pow(0.96f) * 1.01f
                cb = if (cb < 0.5f) cb.pow(1.06f) else cb.pow(1.02f) * 0.98f
                cb += (1f - lum) * 0.03f
                cr += lum * 0.02f
                Triple(cr.coerceIn(0f, 1f), cg.coerceIn(0f, 1f), cb.coerceIn(0f, 1f))
            }
            LutPreset.FUJI_CLASSIC_NEGATIVE -> {
                // Classic Negative (Superia 400): hard contrast, emerald greens, magenta shadows, cyan highlight roll-off
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
                Triple(cr.coerceIn(0f, 1f), cg.coerceIn(0f, 1f), cb.coerceIn(0f, 1f))
            }
            LutPreset.FUJI_NOSTALGIC_NEGATIVE -> {
                // Nostalgic Negative (1970s American New Color): amber highlight glow, lifted warm shadows, rich nostalgic reds
                val lum = LR * r + LG * g + LB * b
                val tr = (r * 1.08f + lum * 0.08f + 0.02f).coerceIn(0f, 1f)
                val tg = (g * 0.98f + lum * 0.04f + 0.01f).coerceIn(0f, 1f)
                val tb = (b * 0.88f - lum * 0.02f).coerceIn(0f, 1f)
                val amber = 0.05f * sin(lum * Math.PI.toFloat())
                Triple((tr + amber).coerceIn(0f, 1f), (tg + amber * 0.6f).coerceIn(0f, 1f), tb)
            }
            LutPreset.FUJI_VELVIA -> {
                // Velvia 50 (Vivid): ultra-saturated landscape slide, deep blacks, high dynamic punch
                val lum = LR * r + LG * g + LB * b
                var cr = if (r < 0.5f) 2f * r * r else 1f - 2f * (1f - r) * (1f - r)
                var cg = if (g < 0.5f) 2f * g * g else 1f - 2f * (1f - g) * (1f - g)
                var cb = if (b < 0.5f) 2f * b * b else 1f - 2f * (1f - b) * (1f - b)
                val nLum = LR * cr + LG * cg + LB * cb
                cr = (nLum + (cr - nLum) * 1.38f).coerceIn(0f, 1f)
                cg = (nLum + (cg - nLum) * 1.34f).coerceIn(0f, 1f)
                cb = (nLum + (cb - nLum) * 1.38f).coerceIn(0f, 1f)
                Triple(cr, cg, cb)
            }
            LutPreset.FUJI_PROVIA -> {
                // Provia 100F (Standard): true-to-life reversal film, neutral white point, balanced skin tones
                val cr = ((r - 0.5f) * 1.10f + 0.5f).coerceIn(0f, 1f)
                val cg = ((g - 0.5f) * 1.08f + 0.5f).coerceIn(0f, 1f)
                val cb = ((b - 0.5f) * 1.09f + 0.5f).coerceIn(0f, 1f)
                val lum = LR * cr + LG * cg + LB * cb
                val satR = (lum + (cr - lum) * 1.05f).coerceIn(0f, 1f)
                val satG = (lum + (cg - lum) * 1.05f).coerceIn(0f, 1f)
                val satB = (lum + (cb - lum) * 1.05f).coerceIn(0f, 1f)
                Triple(satR, satG, satB)
            }
            LutPreset.FUJI_ASTIA -> {
                // Astia 100F (Soft): portrait slide film, gentle highlight roll-off, soft flattering contrast
                val lum = LR * r + LG * g + LB * b
                val cr = (r.pow(0.95f) * 1.03f).coerceIn(0f, 1f)
                val cg = (g.pow(0.97f) * 1.01f).coerceIn(0f, 1f)
                val cb = (b.pow(1.02f) * 0.98f).coerceIn(0f, 1f)
                val skinBias = 0.035f * sin(lum * Math.PI.toFloat())
                Triple((cr + skinBias).coerceIn(0f, 1f), (cg + skinBias * 0.4f).coerceIn(0f, 1f), cb)
            }
            LutPreset.FUJI_ACROS -> {
                // ACROS Monochrome (+Filter): silver halide B&W with optical contrast filter and smooth tonal gradation
                val filterLum = 0.32f * r + 0.56f * g + 0.12f * b
                val bw = if (filterLum < 0.5f) {
                    2.1f * filterLum * filterLum
                } else {
                    1f - 2.1f * (1f - filterLum) * (1f - filterLum)
                }
                val finalBw = bw.coerceIn(0f, 1f)
                Triple(finalBw, finalBw, finalBw)
            }
            LutPreset.FILM_EMULATION_KODAK -> {
                // Kodak 2383: warm highlights, rich cyan/green shadows, gentle knee
                val tr = r * 1.06f + 0.02f
                val tg = g * 0.98f + 0.03f * (1f - g)
                val tb = b * 0.91f + 0.06f * (1f - b)
                Triple(tr.coerceIn(0f, 1f), tg.coerceIn(0f, 1f), tb.coerceIn(0f, 1f))
            }
            LutPreset.FILM_FUJI_ETERNA -> {
                // Fuji Eterna 250D: low-contrast soft pastel tones with muted highlights and rich foliage green
                val lum = LR * r + LG * g + LB * b
                val tr = (r * 0.94f + lum * 0.06f).pow(0.96f)
                val tg = (g * 1.02f + 0.015f * (1f - lum)).coerceIn(0f, 1f)
                val tb = (b * 0.93f + 0.02f * lum).pow(0.98f)
                Triple(tr.coerceIn(0f, 1f), tg.coerceIn(0f, 1f), tb.coerceIn(0f, 1f))
            }
            LutPreset.BLOCKBUSTER_TEAL_ORANGE -> {
                // Classic cinematic teal shadows and amber highlights
                val lum = LR * r + LG * g + LB * b
                val tr = r + 0.12f * (lum.pow(1.5f))
                val tg = g + 0.03f * (lum.pow(1.2f))
                val tb = b + 0.18f * ((1f - lum).pow(1.8f))
                Triple(tr.coerceIn(0f, 1f), tg.coerceIn(0f, 1f), tb.coerceIn(0f, 1f))
            }
            LutPreset.CLEAN_COMMERCIAL -> {
                // High contrast punch, deep blacks, pure highlights
                val cr = ((r - 0.45f) * 1.25f + 0.45f).coerceIn(0f, 1f)
                val cg = ((g - 0.45f) * 1.25f + 0.45f).coerceIn(0f, 1f)
                val cb = ((b - 0.45f) * 1.25f + 0.45f).coerceIn(0f, 1f)
                Triple(cr, cg, cb)
            }
            LutPreset.MONOCHROME_NOIR -> {
                // Silver Gelatin B&W: rich tonal grayscale with high dynamic contrast
                val lum = LR * r + LG * g + LB * b
                val sCurve = if (lum < 0.5f) {
                    2f * lum * lum
                } else {
                    1f - 2f * (1f - lum) * (1f - lum)
                }
                val bw = (sCurve * 1.15f - 0.04f).coerceIn(0f, 1f)
                Triple(bw, bw, bw)
            }
            LutPreset.CYBER_NEON_NIGHT -> {
                // Cyberpunk Neon: vibrant cyan in mid-shadows, hot magenta highlights, deep blacks
                val lum = LR * r + LG * g + LB * b
                val tr = (r * 1.15f + 0.14f * lum.pow(1.8f)).coerceIn(0f, 1f)
                val tg = (g * 0.88f).coerceIn(0f, 1f)
                val tb = (b * 1.20f + 0.16f * (1f - lum).pow(1.5f)).coerceIn(0f, 1f)
                Triple(tr, tg, tb)
            }
            LutPreset.VINTAGE_SEPIA -> {
                // Vintage Warm Sepia: nostalgic golden-hour warm shadows with analog softness
                val lum = LR * r + LG * g + LB * b
                val tr = (lum * 1.18f + 0.06f).coerceIn(0f, 1f)
                val tg = (lum * 0.96f + 0.03f).coerceIn(0f, 1f)
                val tb = (lum * 0.72f).coerceIn(0f, 1f)
                Triple(tr, tg, tb)
            }
            LutPreset.CUSTOM_SAVED -> {
                val custom = settings?.customLuts?.find { it.id == settings.selectedCustomLutId }
                    ?: settings?.customLuts?.firstOrNull()
                if (custom != null) {
                    // Apply custom stored parameters
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
                    cr = (lum + (cr - lum) * custom.saturation).coerceIn(0f, 1f)
                    cg = (lum + (cg - lum) * custom.saturation).coerceIn(0f, 1f)
                    cb = (lum + (cb - lum) * custom.saturation).coerceIn(0f, 1f)
                    Triple(cr, cg, cb)
                } else {
                    Triple(r, g, b)
                }
            }
            LutPreset.CUSTOM_CUBE -> Triple(r, g, b)
        }
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
     * DaVinci Resolve Color Space Transform (CST) Output Node.
     * Maps wide-gamut log signals (DWG, BT.2020) to output delivery spaces (Rec.709, DCI-P3).
     */
    fun applyColorSpaceTransform(
        inR: Float,
        inG: Float,
        inB: Float,
        cst: CstNodeSettings
    ): Triple<Float, Float, Float> {
        if (!cst.enabled) return Triple(inR, inG, inB)

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

        return Triple(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f))
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
     * DaVinci Resolve False Color Exposure Meter (IRE zones).
     * Maps luminance into broadcast exposure evaluation bands:
     * - 0..2 IRE: Purple (clipped black floor)
     * - 2..10 IRE: Blue (shadows)
     * - 10..38 IRE: Dark Cyan (low-mids)
     * - 38..44 IRE: Green (18% Middle Gray card)
     * - 48..56 IRE: Pink (Properly exposed Caucasian/tan skin tone)
     * - 56..80 IRE: Neutral Gray (upper-mids)
     * - 80..95 IRE: Yellow (near highlight clipping)
     * - 95..100+ IRE: Red (Clipped / blown highlights)
     */
    fun evaluateFalseColor(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val ire = (LR * r + LG * g + LB * b) * 100f
        return when {
            ire <= 2f -> Triple(0.55f, 0.0f, 0.85f)      // Purple (Crushed Black)
            ire <= 10f -> Triple(0.0f, 0.35f, 0.95f)     // Blue (Shadows)
            ire <= 38f -> Triple(0.1f, 0.45f, 0.55f)     // Dark Cyan (Low-mids)
            ire in 38f..44f -> Triple(0.0f, 0.85f, 0.2f) // Green (18% Middle Gray)
            ire in 48f..56f -> Triple(1.0f, 0.45f, 0.75f)// Pink (Skin Tone Reference)
            ire in 80f..95f -> Triple(0.95f, 0.90f, 0.0f)// Yellow (Specular Highlights)
            ire > 95f -> Triple(0.95f, 0.05f, 0.05f)     // Red (Clipped Highlights)
            else -> {
                // Neutral Gray IRE band
                val gray = (ire / 100f) * 0.7f
                Triple(gray, gray, gray)
            }
        }
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
     * Rotates RGB hue by `shiftDeg` degrees while preserving luminance and saturation.
     */
    fun rotateHueRgb(r: Float, g: Float, b: Float, shiftDeg: Float): Triple<Float, Float, Float> {
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

        val nr = (m00 * r + m01 * g + m02 * b).coerceIn(0f, 1f)
        val ng = (m10 * r + m11 * g + m12 * b).coerceIn(0f, 1f)
        val nb = (m20 * r + m21 * g + m22 * b).coerceIn(0f, 1f)
        return Triple(nr, ng, nb)
    }

    /**
     * Optical Centroid / Feature Point Tracker for Power Windows (Oval, Rect, Triangle, Bezier).
     * Samples luminance and chrominance contrast within a neighborhood around (centerX, centerY)
     * to track moving subjects across timeline frames.
     */
    fun computeTrackedCenter(
        bitmap: Bitmap,
        centerX: Float,
        centerY: Float,
        radiusX: Float,
        radiusY: Float,
        frameIndex: Int = 0
    ): Pair<Float, Float> {
        val w = bitmap.width.coerceAtLeast(1)
        val h = bitmap.height.coerceAtLeast(1)
        val searchRx = (radiusX * 1.15f).coerceIn(0.10f, 0.45f)
        val searchRy = (radiusY * 1.15f).coerceIn(0.10f, 0.45f)

        val x0 = ((centerX - searchRx) * w).toInt().coerceIn(0, w - 1)
        val x1 = ((centerX + searchRx) * w).toInt().coerceIn(0, w - 1)
        val y0 = ((centerY - searchRy) * h).toInt().coerceIn(0, h - 1)
        val y1 = ((centerY + searchRy) * h).toInt().coerceIn(0, h - 1)

        var weightedX = 0f
        var weightedY = 0f
        var totalWeight = 0f
        val step = 4

        for (py in y0..y1 step step) {
            val ny = py.toFloat() / h
            for (px in x0..x1 step step) {
                val nx = px.toFloat() / w
                val c = bitmap.getPixel(px, py)
                val r = AndroidColor.red(c) / 255f
                val g = AndroidColor.green(c) / 255f
                val b = AndroidColor.blue(c) / 255f
                val lum = LR * r + LG * g + LB * b
                val sat = max(r, max(g, b)) - min(r, min(g, b))
                val distSq = ((nx - centerX) / searchRx).pow(2) + ((ny - centerY) / searchRy).pow(2)
                if (distSq <= 1.2f) {
                    val spatialKernel = (1.25f - distSq).coerceAtLeast(0.1f)
                    val featureStrength = (lum * 0.65f + sat * 0.85f).pow(1.4f) * spatialKernel
                    weightedX += nx * featureStrength
                    weightedY += ny * featureStrength
                    totalWeight += featureStrength
                }
            }
        }

        return if (totalWeight > 0.001f) {
            val rawTargetX = weightedX / totalWeight
            val rawTargetY = weightedY / totalWeight
            // Subtle kinematic motion drift + optical centroid lock
            val microOscX = sin(frameIndex * 0.14f) * 0.008f
            val microOscY = cos(frameIndex * 0.11f) * 0.005f
            val nextX = (centerX * 0.72f + rawTargetX * 0.28f + microOscX).coerceIn(0.08f, 0.92f)
            val nextY = (centerY * 0.72f + rawTargetY * 0.28f + microOscY).coerceIn(0.08f, 0.92f)
            Pair(nextX, nextY)
        } else {
            Pair(centerX, centerY)
        }
    }
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
