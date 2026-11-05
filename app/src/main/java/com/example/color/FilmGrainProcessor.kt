package com.example.color

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import com.example.model.FilmGrainSettings
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Authentic Organic Film Grain Processor.
 * Simulates physical silver halide crystals and dye cloud emulsion textures
 * with controls for particle size, fractal roughness, and exposure-dependent density.
 */
object FilmGrainProcessor {

    // Precomputed organic permutation table for fast coherent noise synthesis
    private val PERM = IntArray(512)
    private val GRAD_X = FloatArray(512)
    private val GRAD_Y = FloatArray(512)

    init {
        val p = intArrayOf(
            151,160,137,91,90,15,131,13,201,95,96,53,194,233,7,225,140,36,103,30,69,142,
            8,99,37,240,21,10,23,190,6,148,247,120,234,75,0,26,197,62,94,252,219,203,117,
            35,11,32,57,177,33,88,237,149,56,87,174,20,125,136,171,168,68,175,74,165,71,
            134,139,48,27,166,77,146,158,231,83,111,229,122,60,211,133,230,220,105,92,41,
            55,46,245,40,244,102,143,54,65,25,63,161,1,216,80,73,209,76,132,187,208,89,
            18,169,200,196,135,130,116,188,159,86,164,100,109,198,173,186,3,64,52,217,226,
            250,124,123,5,202,38,147,118,126,255,82,85,212,207,206,59,227,47,16,58,17,182,
            189,28,42,223,183,170,213,119,248,152,2,44,154,163,70,221,153,101,155,167,43,
            172,9,129,22,39,253,19,98,108,110,79,113,224,232,178,185,112,104,218,246,97,
            228,251,34,242,193,238,210,144,12,191,179,162,241,81,51,145,235,249,14,239,
            107,49,192,214,31,181,199,106,157,184,84,204,176,115,121,50,45,127,4,150,254,
            138,236,205,93,222,114,67,29,24,72,243,141,128,195,78,66,215,61,156,180
        )
        for (i in 0 until 256) {
            val v = p[i]
            PERM[i] = v
            PERM[256 + i] = v
            val angle = (v / 256f) * 6.2831853f
            GRAD_X[i] = cos(angle)
            GRAD_Y[i] = sin(angle)
            GRAD_X[256 + i] = GRAD_X[i]
            GRAD_Y[256 + i] = GRAD_Y[i]
        }
    }

    /**
     * 2D gradient noise function for coherent organic clumping.
     */
    private fun organicNoise(x: Float, y: Float): Float {
        val xi = x.toInt().let { if (x < 0) it - 1 else it } and 255
        val yi = y.toInt().let { if (y < 0) it - 1 else it } and 255

        val xf = x - (if (x < 0) x.toInt() - 1 else x.toInt())
        val yf = y - (if (y < 0) y.toInt() - 1 else y.toInt())

        // Quintic smoothstep for smooth organic grain clusters
        val u = xf * xf * xf * (xf * (xf * 6f - 15f) + 10f)
        val v = yf * yf * yf * (yf * (yf * 6f - 15f) + 10f)

        val g00 = PERM[xi + PERM[yi]]
        val g10 = PERM[xi + 1 + PERM[yi]]
        val g01 = PERM[xi + PERM[yi + 1]]
        val g11 = PERM[xi + 1 + PERM[yi + 1]]

        val d00 = GRAD_X[g00] * xf + GRAD_Y[g00] * yf
        val d10 = GRAD_X[g10] * (xf - 1f) + GRAD_Y[g10] * yf
        val d01 = GRAD_X[g01] * xf + GRAD_Y[g01] * (yf - 1f)
        val d11 = GRAD_X[g11] * (xf - 1f) + GRAD_Y[g11] * (yf - 1f)

        val nx0 = d00 + u * (d10 - d00)
        val nx1 = d01 + u * (d11 - d01)
        return nx0 + v * (nx1 - nx0)
    }

    /**
     * Computes the optical density of film grain relative to pixel luminance.
     * Film grain naturally clusters heavily in midtones and shadows,
     * dropping off smoothly in pure clipped highlights and zero-exposure blacks.
     */
    fun computeEmulsionDensity(lum: Float, midtoneBias: Float = 0.75f): Float {
        // Bell curve centered on midtones (4 * L * (1-L))
        val midtoneCurve = 4f * lum * (1f - lum)
        // Shadow density shoulder
        val shadowShoulder = (1f - lum) * (1f - lum)
        val density = midtoneCurve * midtoneBias + shadowShoulder * (1f - midtoneBias)
        return density.coerceIn(0.05f, 1.0f)
    }

    /**
     * Synthesizes an authentic organic grain value for a given (x, y) coordinate,
     * blending multi-scale coherent clusters with high-frequency crystalline roughness.
     */
    fun sampleGrainValue(
        x: Int,
        y: Int,
        size: Float,
        roughness: Float,
        channelOffset: Float = 0f
    ): Float {
        val safeSize = size.coerceIn(0.5f, 3.5f) * 1.6f

        // Octave 1: Large organic silver halide clumps
        val nx1 = (x + channelOffset * 37f) / (safeSize * 2.2f)
        val ny1 = (y + channelOffset * 53f) / (safeSize * 2.2f)
        val clumpNoise = organicNoise(nx1, ny1)

        // Octave 2: Mid-frequency crystalline clusters
        val nx2 = (x + channelOffset * 19f) / safeSize
        val ny2 = (y + channelOffset * 29f) / safeSize
        val midNoise = organicNoise(nx2, ny2)

        // Octave 3: High-frequency salt roughness / individual grain speckles
        val hash = (x * 73856093 xor y * 19349663 xor (channelOffset * 83492791f).toInt()) and 0x7fffffff
        val microNoise = (hash % 1000) / 500f - 1.0f

        // Blend octaves based on roughness parameter
        // Low roughness = smooth organic 65mm/35mm clumping; High roughness = gritty 16mm/8mm salt
        val macro = clumpNoise * 0.45f + midNoise * 0.55f
        val blended = macro * (1.0f - roughness * 0.65f) + microNoise * (roughness * 0.75f)

        return blended.coerceIn(-1.5f, 1.5f)
    }

    /**
     * Overlay authentic organic grain onto a bitmap's pixel array.
     */
    fun applyGrain(
        pixels: IntArray,
        width: Int,
        height: Int,
        settings: FilmGrainSettings
    ) {
        if (!settings.enabled && settings.strength <= 0f) return

        val strength = settings.strength
        val size = settings.size
        val roughness = settings.roughness
        val monochrome = settings.monochrome
        val midtoneBias = settings.midtoneBias

        val lr = 0.2126f
        val lg = 0.7152f
        val lb = 0.0722f

        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val idx = row + x
                val p = pixels[idx]

                var r = (p shr 16 and 0xFF) / 255f
                var g = (p shr 8 and 0xFF) / 255f
                var b = (p and 0xFF) / 255f

                val lum = lr * r + lg * g + lb * b
                val density = computeEmulsionDensity(lum, midtoneBias)

                if (monochrome) {
                    val grainVal = sampleGrainValue(x, y, size, roughness, 0f)
                    val delta = grainVal * density * strength * 0.18f

                    r = (r + delta).coerceIn(0f, 1f)
                    g = (g + delta).coerceIn(0f, 1f)
                    b = (b + delta).coerceIn(0f, 1f)
                } else {
                    // 3-layer color dye clouds
                    val grainR = sampleGrainValue(x, y, size, roughness, 0.0f)
                    val grainG = sampleGrainValue(x, y, size, roughness, 1.3f)
                    val grainB = sampleGrainValue(x, y, size, roughness, 2.7f)

                    val deltaR = grainR * density * strength * 0.18f
                    val deltaG = grainG * density * strength * 0.18f
                    val deltaB = grainB * density * strength * 0.18f

                    r = (r + deltaR).coerceIn(0f, 1f)
                    g = (g + deltaG).coerceIn(0f, 1f)
                    b = (b + deltaB).coerceIn(0f, 1f)
                }

                val ir = (r * 255f).toInt().coerceIn(0, 255)
                val ig = (g * 255f).toInt().coerceIn(0, 255)
                val ib = (b * 255f).toInt().coerceIn(0, 255)

                pixels[idx] = (0xFF shl 24) or (ir shl 16) or (ig shl 8) or ib
            }
        }
    }

    /**
     * Generates a preview texture sample swatch of the current film grain configuration
     * for real-time visualization in the UI.
     */
    fun generateGrainTextureSample(
        width: Int = 160,
        height: Int = 80,
        settings: FilmGrainSettings
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        val size = settings.size
        val roughness = settings.roughness
        val strength = settings.strength.coerceAtLeast(0.15f)
        val monochrome = settings.monochrome

        for (y in 0 until height) {
            val row = y * width
            // Mid-gray background (18% cinema gray)
            for (x in 0 until width) {
                var r = 0.5f
                var g = 0.5f
                var b = 0.5f

                if (monochrome) {
                    val gVal = sampleGrainValue(x, y, size, roughness, 0f)
                    val delta = gVal * strength * 0.28f
                    r = (r + delta).coerceIn(0f, 1f)
                    g = (g + delta).coerceIn(0f, 1f)
                    b = (b + delta).coerceIn(0f, 1f)
                } else {
                    val gr = sampleGrainValue(x, y, size, roughness, 0.0f)
                    val gg = sampleGrainValue(x, y, size, roughness, 1.3f)
                    val gb = sampleGrainValue(x, y, size, roughness, 2.7f)
                    r = (r + gr * strength * 0.28f).coerceIn(0f, 1f)
                    g = (g + gg * strength * 0.28f).coerceIn(0f, 1f)
                    b = (b + gb * strength * 0.28f).coerceIn(0f, 1f)
                }

                val ir = (r * 255f).toInt().coerceIn(0, 255)
                val ig = (g * 255f).toInt().coerceIn(0, 255)
                val ib = (b * 255f).toInt().coerceIn(0, 255)

                pixels[row + x] = (0xFF shl 24) or (ir shl 16) or (ig shl 8) or ib
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}
