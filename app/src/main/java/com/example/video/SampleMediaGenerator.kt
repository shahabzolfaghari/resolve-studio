package com.example.video

import android.graphics.*
import kotlin.math.*

enum class SampleScene(val sceneName: String, val subtitle: String) {
    S24_LOG_SUNSET_PORTRAIT("S24 Ultra Log: Sunset Portrait", "Golden hour skin tones, 13 stops dynamic range"),
    S24_LOG_NEON_CITY("S24 Ultra Log: Tokyo Cyber Neon", "High-contrast specular neon highlights & deep shadows"),
    S24_LOG_ALPINE_LAKE("S24 Ultra Log: Mountain Vista", "Sky highlight roll-off, foliage green gamut, water reflections"),
    S24_LOG_CINEMA_INTERIOR("S24 Ultra Log: Film Noir Studio", "Moody key light, warm tungsten & dark shadows")
}

object SampleMediaGenerator {

    private fun getSceneImageFile(scene: SampleScene): java.io.File? {
        val baseDir = java.io.File("/Users/metteore/antigravity/ResolveStudio-2026-11-05-a63fb/media")
        if (!baseDir.exists()) baseDir.mkdirs()
        val names = listOf(
            "scene_${scene.name.lowercase()}.png",
            "scene_${scene.name.lowercase()}.jpg",
            "${scene.name.lowercase()}.png",
            "${scene.name.lowercase()}.jpg",
            when (scene) {
                SampleScene.S24_LOG_SUNSET_PORTRAIT -> "sunset_portrait.png"
                SampleScene.S24_LOG_NEON_CITY -> "neon_city.png"
                SampleScene.S24_LOG_ALPINE_LAKE -> "alpine_lake.png"
                SampleScene.S24_LOG_CINEMA_INTERIOR -> "cinema_interior.png"
            }
        )
        return names.map { java.io.File(baseDir, it) }.firstOrNull { it.exists() }
    }

    /**
     * Generates a realistic Samsung S24 Ultra Log 4K simulated frame (rendered at crisp HD/4K preview resolution).
     * Samsung Log is characterized by flat contrast, raised black level (~15-20% code value),
     * and compressed highlights preserving full detail before grading.
     */
    fun generateLogFrame(scene: SampleScene, frameIndex: Int = 0, width: Int = 1280, height: Int = 720): Bitmap {
        val customFile = getSceneImageFile(scene)
        if (customFile != null) {
            try {
                val decoded = BitmapFactory.decodeFile(customFile.absolutePath)
                if (decoded != null) {
                    val scaled = Bitmap.createScaledBitmap(decoded, width, height, true)
                    val mutableBmp = scaled.copy(Bitmap.Config.ARGB_8888, true)
                    applyFlatLogCurve(mutableBmp)
                    return mutableBmp
                }
            } catch (_: Exception) {}
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        when (scene) {
            SampleScene.S24_LOG_SUNSET_PORTRAIT -> drawSunsetPortrait(canvas, width, height, frameIndex, paint)
            SampleScene.S24_LOG_NEON_CITY -> drawNeonCity(canvas, width, height, frameIndex, paint)
            SampleScene.S24_LOG_ALPINE_LAKE -> drawAlpineLake(canvas, width, height, frameIndex, paint)
            SampleScene.S24_LOG_CINEMA_INTERIOR -> drawCinemaInterior(canvas, width, height, frameIndex, paint)
        }

        // Apply Samsung Log flat tone curve over the raw rendering to accurately simulate flat 10-bit log footage
        applyFlatLogCurve(bitmap)

        return bitmap
    }

    private fun drawSunsetPortrait(canvas: Canvas, w: Int, h: Int, frame: Int, paint: Paint) {
        // Sky gradient
        val skyGrad = LinearGradient(
            0f, 0f, 0f, h * 0.7f,
            intArrayOf(0xFF708090.toInt(), 0xFFC0A080.toInt(), 0xFFD89070.toInt(), 0xFFE0B088.toInt()),
            floatArrayOf(0f, 0.4f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = skyGrad
        canvas.drawRect(0f, 0f, w.toFloat(), h * 0.7f, paint)

        // Low sun disk
        val sunX = w * 0.35f + sin(frame * 0.05f) * 8f
        val sunY = h * 0.42f
        val sunGrad = RadialGradient(
            sunX, sunY, w * 0.22f,
            intArrayOf(0xFFFFF0D0.toInt(), 0xFFF0B080.toInt(), 0x00D09070),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = sunGrad
        canvas.drawCircle(sunX, sunY, w * 0.22f, paint)

        // Distant hills
        paint.shader = null
        paint.color = 0xFF584840.toInt()
        val hillPath = Path()
        hillPath.moveTo(0f, h * 0.65f)
        hillPath.quadTo(w * 0.25f, h * 0.52f, w * 0.5f, h * 0.60f)
        hillPath.quadTo(w * 0.75f, h * 0.50f, w.toFloat(), h * 0.62f)
        hillPath.lineTo(w.toFloat(), h.toFloat())
        hillPath.lineTo(0f, h.toFloat())
        hillPath.close()
        canvas.drawPath(hillPath, paint)

        // Foreground silhouette / portrait subject
        val subjectX = w * 0.62f
        val subjectY = h * 0.45f
        paint.color = 0xFF3D302C.toInt()
        // Head
        canvas.drawCircle(subjectX, subjectY, h * 0.14f, paint)
        // Shoulders
        val bodyPath = Path()
        bodyPath.moveTo(subjectX - w * 0.16f, h.toFloat())
        bodyPath.quadTo(subjectX - w * 0.12f, subjectY + h * 0.12f, subjectX, subjectY + h * 0.12f)
        bodyPath.quadTo(subjectX + w * 0.12f, subjectY + h * 0.12f, subjectX + w * 0.16f, h.toFloat())
        bodyPath.close()
        canvas.drawPath(bodyPath, paint)

        // Golden rim light on subject profile
        paint.color = 0xFFE8C8A0.toInt()
        paint.strokeWidth = 6f
        paint.style = Paint.Style.STROKE
        canvas.drawArc(
            RectF(subjectX - h * 0.14f, subjectY - h * 0.14f, subjectX + h * 0.14f, subjectY + h * 0.14f),
            120f, 130f, false, paint
        )
        paint.style = Paint.Style.FILL
    }

    private fun drawNeonCity(canvas: Canvas, w: Int, h: Int, frame: Int, paint: Paint) {
        // Dark moody urban background
        val bgGrad = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(0xFF181C26.toInt(), 0xFF10141C.toInt(), 0xFF0D0F14.toInt()),
            null, Shader.TileMode.CLAMP
        )
        paint.shader = bgGrad
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        // Building silhouettes with windows
        val bldgColors = intArrayOf(0xFF1E222D.toInt(), 0xFF252B38.toInt(), 0xFF191D26.toInt())
        for (i in 0..8) {
            val bx = i * (w / 8.5f)
            val bw = w / 9.5f
            val bh = h * (0.35f + ((i * 37) % 30) / 100f)
            paint.color = bldgColors[i % 3]
            canvas.drawRect(bx, h - bh, bx + bw, h.toFloat(), paint)

            // Neon signs
            val neonColor = when (i % 4) {
                0 -> 0xFFFF2A85.toInt() // Neon Magenta
                1 -> 0xFF00E5FF.toInt() // Neon Cyan
                2 -> 0xFFFFD600.toInt() // Neon Amber
                else -> 0xFF00FF7F.toInt() // Neon Green
            }
            paint.color = neonColor
            val signY = h - bh + 40f
            canvas.drawRoundRect(RectF(bx + 15f, signY, bx + bw - 15f, signY + 35f), 6f, 6f, paint)

            // Soft neon glow
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 12f
            paint.color = (neonColor and 0x00FFFFFF) or 0x44000000
            canvas.drawRoundRect(RectF(bx + 10f, signY - 5f, bx + bw - 10f, signY + 40f), 10f, 10f, paint)
            paint.style = Paint.Style.FILL
        }

        // Wet asphalt ground reflection
        val groundGrad = LinearGradient(
            0f, h * 0.78f, 0f, h.toFloat(),
            intArrayOf(0xFF151922.toInt(), 0xFF1D2230.toInt()),
            null, Shader.TileMode.CLAMP
        )
        paint.shader = groundGrad
        canvas.drawRect(0f, h * 0.78f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        // Light trails
        paint.color = 0xFFFF3366.toInt()
        paint.strokeWidth = 4f
        paint.style = Paint.Style.STROKE
        val trailY = h * 0.88f
        canvas.drawLine(w * 0.1f, trailY, w * 0.9f, trailY + 12f, paint)
        paint.color = 0xFF33CCFF.toInt()
        canvas.drawLine(w * 0.05f, trailY + 24f, w * 0.85f, trailY + 36f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawAlpineLake(canvas: Canvas, w: Int, h: Int, frame: Int, paint: Paint) {
        // High-altitude sky
        val sky = LinearGradient(
            0f, 0f, 0f, h * 0.5f,
            intArrayOf(0xFF5B8CA8.toInt(), 0xFF87AEC6.toInt(), 0xFFD8E4EC.toInt()),
            null, Shader.TileMode.CLAMP
        )
        paint.shader = sky
        canvas.drawRect(0f, 0f, w.toFloat(), h * 0.5f, paint)

        // Sun with bright highlight roll-off challenge
        val sunX = w * 0.75f
        val sunY = h * 0.18f
        val sunBloom = RadialGradient(
            sunX, sunY, w * 0.3f,
            intArrayOf(0xFFFFFFFF.toInt(), 0x88F0F8FF.toInt(), 0x00E0EEF8),
            floatArrayOf(0f, 0.25f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = sunBloom
        canvas.drawCircle(sunX, sunY, w * 0.3f, paint)

        // Snow-capped alpine mountains
        paint.shader = null
        paint.color = 0xFF5A6878.toInt()
        val mtn = Path()
        mtn.moveTo(0f, h * 0.5f)
        mtn.lineTo(w * 0.22f, h * 0.20f)
        mtn.lineTo(w * 0.45f, h * 0.40f)
        mtn.lineTo(w * 0.68f, h * 0.15f)
        mtn.lineTo(w.toFloat(), h * 0.48f)
        mtn.lineTo(w.toFloat(), h * 0.5f)
        mtn.lineTo(0f, h * 0.5f)
        mtn.close()
        canvas.drawPath(mtn, paint)

        // Pine forest midground
        paint.color = 0xFF2A4232.toInt()
        val forest = Path()
        forest.moveTo(0f, h * 0.55f)
        forest.lineTo(w.toFloat(), h * 0.53f)
        forest.lineTo(w.toFloat(), h * 0.65f)
        forest.lineTo(0f, h * 0.65f)
        forest.close()
        canvas.drawPath(forest, paint)

        // Reflective Lake
        val lake = LinearGradient(
            0f, h * 0.65f, 0f, h.toFloat(),
            intArrayOf(0xFF385868.toInt(), 0xFF263C48.toInt(), 0xFF1C2C35.toInt()),
            null, Shader.TileMode.CLAMP
        )
        paint.shader = lake
        canvas.drawRect(0f, h * 0.65f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
    }

    private fun drawCinemaInterior(canvas: Canvas, w: Int, h: Int, frame: Int, paint: Paint) {
        // Deep shadow film set
        paint.color = 0xFF121418.toInt()
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        // Warm directional tungsten spotlight
        val spotX = w * 0.5f
        val spotY = h * 0.4f
        val lightBeam = RadialGradient(
            spotX, spotY, w * 0.45f,
            intArrayOf(0xFFDCB078.toInt(), 0x55986840.toInt(), 0x0014161C),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = lightBeam
        canvas.drawCircle(spotX, spotY, w * 0.45f, paint)
        paint.shader = null

        // Vintage film reel / clapperboard element
        paint.color = 0xFF222630.toInt()
        canvas.drawRoundRect(RectF(w * 0.35f, h * 0.3f, w * 0.65f, h * 0.7f), 16f, 16f, paint)

        // Warm key highlight
        paint.color = 0xFFF5D5A5.toInt()
        canvas.drawCircle(w * 0.5f, h * 0.42f, 40f, paint)
    }

    /**
     * Converts a pristine linear image into true Samsung Log flat profile:
     * - Elevates shadows to ~15-20% code value
     * - Compresses highlights into logarithmic upper range
     * - Reduces contrast and saturation
     */
    private fun applyFlatLogCurve(bitmap: Bitmap) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val p = pixels[i]
            var r = (p shr 16 and 0xFF) / 255f
            var g = (p shr 8 and 0xFF) / 255f
            var b = (p and 0xFF) / 255f

            // Compress to Log: y = a * ln(x * b + c) + d
            r = (0.235f * ln(r * 5.555f + 0.092f) + 0.540f).coerceIn(0.12f, 0.90f)
            g = (0.235f * ln(g * 5.555f + 0.092f) + 0.540f).coerceIn(0.12f, 0.90f)
            b = (0.235f * ln(b * 5.555f + 0.092f) + 0.540f).coerceIn(0.12f, 0.90f)

            // Desaturate slightly for characteristic flat log look
            val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
            r = lum + (r - lum) * 0.65f
            g = lum + (g - lum) * 0.65f
            b = lum + (b - lum) * 0.65f

            pixels[i] = (0xFF shl 24) or
                    ((r * 255f).toInt() shl 16) or
                    ((g * 255f).toInt() shl 8) or
                    (b * 255f).toInt()
        }

        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }
}
