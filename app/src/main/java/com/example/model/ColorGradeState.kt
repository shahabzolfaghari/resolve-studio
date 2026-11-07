package com.example.model

import java.io.Serializable

data class WheelValue(
    val x: Float = 0f,    // -1f (cyan/magenta/etc) to +1f
    val y: Float = 0f,    // -1f to +1f
    val lum: Float = 0f   // -1f (darker) to +1f (brighter)
) : Serializable

data class LogWheelSettings(
    val shadow: WheelValue = WheelValue(),
    val midtone: WheelValue = WheelValue(),
    val highlight: WheelValue = WheelValue(),
    val offset: WheelValue = WheelValue(),
    val lowRange: Float = 0.33f,   // 0.10..0.55 Shadow-to-Midtone crossover pivot
    val highRange: Float = 0.68f   // 0.45..0.90 Midtone-to-Highlight crossover pivot
) : Serializable {
    val hasAdjustments: Boolean
        get() = shadow != WheelValue() || midtone != WheelValue() || highlight != WheelValue() || offset != WheelValue()
}

data class CurvePoint(
    val x: Float,
    val y: Float
) : Serializable

fun defaultFlatHalfCurve(): List<CurvePoint> = listOf(
    CurvePoint(0.0f, 0.5f),
    CurvePoint(0.17f, 0.5f), // Yellow/Skin
    CurvePoint(0.33f, 0.5f), // Green
    CurvePoint(0.50f, 0.5f), // Cyan
    CurvePoint(0.67f, 0.5f), // Blue
    CurvePoint(0.83f, 0.5f), // Magenta
    CurvePoint(1.0f, 0.5f)
)

data class RgbCurves(
    val master: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
    val red: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
    val green: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
    val blue: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
    // DaVinci Resolve Color vs Color Secondary Curves (0.5 = neutral)
    val hueVsHue: List<CurvePoint> = defaultFlatHalfCurve(),
    val hueVsSat: List<CurvePoint> = defaultFlatHalfCurve(),
    val hueVsLum: List<CurvePoint> = defaultFlatHalfCurve(),
    val lumVsSat: List<CurvePoint> = listOf(CurvePoint(0f, 0.5f), CurvePoint(0.5f, 0.5f), CurvePoint(1f, 0.5f))
) : Serializable {
    val isIdentity: Boolean
        get() = isLinearCurve(master) &&
                isLinearCurve(red) &&
                isLinearCurve(green) &&
                isLinearCurve(blue) &&
                isFlatSecondaryCurve(hueVsHue) &&
                isFlatSecondaryCurve(hueVsSat) &&
                isFlatSecondaryCurve(hueVsLum) &&
                isFlatSecondaryCurve(lumVsSat)

    private fun isLinearCurve(pts: List<CurvePoint>): Boolean {
        return pts.size == 2 &&
                kotlin.math.abs(pts[0].x) < 0.001f &&
                kotlin.math.abs(pts[0].y) < 0.001f &&
                kotlin.math.abs(pts[1].x - 1f) < 0.001f &&
                kotlin.math.abs(pts[1].y - 1f) < 0.001f
    }

    private fun isFlatSecondaryCurve(pts: List<CurvePoint>): Boolean {
        return pts.all { kotlin.math.abs(it.y - 0.5f) < 0.002f }
    }
}

enum class MaskType {
    NONE,
    ELLIPTICAL,
    RECTANGULAR,
    TRIANGLE,
    LINEAR,
    CUSTOM_BEZIER;

    companion object {
        val RADIAL = ELLIPTICAL
    }
}

data class MaskTrackerKeyframe(
    val timeMs: Long,
    val centerX: Float,
    val centerY: Float,
    val confidence: Float = 0.98f
) : Serializable

data class BezierControlPoint(
    val x: Float,
    val y: Float,
    val handleInX: Float = x,
    val handleInY: Float = y,
    val handleOutX: Float = x,
    val handleOutY: Float = y
) : Serializable

fun defaultBezierPoints(): List<BezierControlPoint> = listOf(
    BezierControlPoint(0.50f, 0.25f, 0.38f, 0.25f, 0.62f, 0.25f),
    BezierControlPoint(0.75f, 0.50f, 0.75f, 0.38f, 0.75f, 0.62f),
    BezierControlPoint(0.50f, 0.75f, 0.62f, 0.75f, 0.38f, 0.75f),
    BezierControlPoint(0.25f, 0.50f, 0.25f, 0.62f, 0.25f, 0.38f)
)

data class PowerWindowMask(
    val type: MaskType = MaskType.NONE,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val radiusX: Float = 0.35f,
    val radiusY: Float = 0.35f,
    val rotationDeg: Float = 0f,
    val feather: Float = 0.25f,
    val invert: Boolean = false,
    val showMatte: Boolean = false,
    val bezierPoints: List<BezierControlPoint> = defaultBezierPoints(),
    val selectedPointIndex: Int = 0,
    val isClosed: Boolean = true,
    // DaVinci Resolve Cloud / Point Motion Tracker State
    val trackingEnabled: Boolean = false,
    val trackPointLocked: Boolean = false,
    val trackingKeyframes: List<MaskTrackerKeyframe> = emptyList()
) : Serializable

enum class FilmStockPreset(
    val displayName: String,
    val size: Float,
    val roughness: Float,
    val strength: Float
) {
    FINE_35MM("35mm Kodak 50D", 1.0f, 0.35f, 0.28f),
    INDIE_16MM("16mm Kodak 500T", 1.8f, 0.70f, 0.55f),
    VINTAGE_8MM("8mm Super 8", 2.6f, 0.85f, 0.80f),
    IMAX_65MM("65mm IMAX Pristine", 0.6f, 0.20f, 0.16f),
    CUSTOM("Custom Stock", 1.0f, 0.5f, 0.35f)
}

data class FilmGrainSettings(
    val enabled: Boolean = false,
    val strength: Float = 0.35f,          // 0.0..1.0 (How visible the grain is)
    val size: Float = 1.0f,              // 0.5..3.0 (Particle cluster scale)
    val roughness: Float = 0.5f,         // 0.0..1.0 (Sharp high-frequency noise vs soft organic clumps)
    val monochrome: Boolean = true,      // true = silver halide B&W, false = color dye clouds
    val midtoneBias: Float = 0.75f,      // Emulsion bell-curve response peaked in midtones
    val preset: FilmStockPreset = FilmStockPreset.FINE_35MM
) : Serializable

data class FilmEffects(
    val grainIntensity: Float = 0f,       // backwards compatibility
    val grainSize: Float = 1.0f,
    val grain: FilmGrainSettings = FilmGrainSettings(),
    val halationIntensity: Float = 0f,    // 0..1 (warm red edge glow)
    val halationRadius: Float = 12f,      // pixel spread
    val vignetteIntensity: Float = 0f     // 0..1 edge falloff
) : Serializable

enum class LutCategory(val displayName: String) {
    SAMSUNG_LOG("Samsung S24 Log → Rec.709"),
    FUJIFILM("Fujifilm Film Recipes"),
    FILM_PRINT("Cinema & Film Stocks"),
    CREATIVE("Creative Looks"),
    CUSTOM("My Custom LUTs")
}

data class CustomLut(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val description: String = "User created grade",
    val dateCreated: Long = System.currentTimeMillis(),
    val lift: WheelValue = WheelValue(),
    val gamma: WheelValue = WheelValue(),
    val gain: WheelValue = WheelValue(),
    val offset: WheelValue = WheelValue(),
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,
    val temperature: Float = 0.0f,
    val tint: Float = 0.0f,
    val exposure: Float = 0.0f
) : Serializable

enum class LutPreset(
    val displayName: String,
    val description: String,
    val category: LutCategory
) {
    NONE(
        "None (Original)",
        "Original unmodified video signal without any LUT applied",
        LutCategory.SAMSUNG_LOG
    ),
    SAMSUNG_LOG_TO_REC709(
        "Samsung Log → Rec.709 Natural",
        "Official S24 Ultra 10-bit logarithmic recovery with neutral white point",
        LutCategory.SAMSUNG_LOG
    ),
    SAMSUNG_REC709_PUNCH(
        "Samsung S24 Vivid Punch",
        "High dynamic punch with vibrant saturation and clean black floor",
        LutCategory.SAMSUNG_LOG
    ),
    SAMSUNG_CINE_WARM(
        "Samsung Cine Warm",
        "Rich cinematic skin tones with amber highlights and gentle contrast",
        LutCategory.SAMSUNG_LOG
    ),

    // 7 Most Famous Fujifilm Film Recipes (Optimized for Samsung S24 Ultra Log)
    FUJI_CLASSIC_CHROME(
        "Classic Chrome",
        "Muted skies, rich navy blues, soft contrast, warm documentary skin tones",
        LutCategory.FUJIFILM
    ),
    FUJI_CLASSIC_NEGATIVE(
        "Classic Negative",
        "Superia 400 look: hard contrast, rich greens, magenta shadow cast, cyan highlights",
        LutCategory.FUJIFILM
    ),
    FUJI_NOSTALGIC_NEGATIVE(
        "Nostalgic Negative",
        "1970s American New Color: amber highlight glow, lifted warm shadows, rich nostalgic reds",
        LutCategory.FUJIFILM
    ),
    FUJI_VELVIA(
        "Velvia 50 (Vivid)",
        "Legendary landscape slide film: ultra-saturated colors, deep blacks, high dynamic punch",
        LutCategory.FUJIFILM
    ),
    FUJI_PROVIA(
        "Provia 100F (Standard)",
        "True-to-life reversal film: neutral white point, balanced skin tones, clean natural contrast",
        LutCategory.FUJIFILM
    ),
    FUJI_ASTIA(
        "Astia 100F (Soft)",
        "Soft portrait slide film: flattering radiant skin, gentle highlight roll-off, soft contrast",
        LutCategory.FUJIFILM
    ),
    FUJI_ACROS(
        "ACROS Monochrome (+Filter)",
        "Ultra-fine grain silver halide B&W with optical contrast filter and smooth tonal gradation",
        LutCategory.FUJIFILM
    ),

    // Cinema & Creative Stocks
    FILM_EMULATION_KODAK(
        "Kodak 2383 Print Stock",
        "Legendary cinema print stock with gentle highlight roll-off and deep teal shadows",
        LutCategory.FILM_PRINT
    ),
    FILM_FUJI_ETERNA(
        "Fuji Eterna 250D",
        "Subtle desaturated pastels, soft contrast, and rich foliage greens",
        LutCategory.FILM_PRINT
    ),
    BLOCKBUSTER_TEAL_ORANGE(
        "Blockbuster Teal & Orange",
        "Hollywood punch with complementary color separation",
        LutCategory.FILM_PRINT
    ),
    CLEAN_COMMERCIAL(
        "Clean Commercial High-Key",
        "Crisp whites, neutral skin tones, high dynamic clarity",
        LutCategory.FILM_PRINT
    ),
    MONOCHROME_NOIR(
        "Silver Gelatin B&W",
        "Rich black and white tonal curve with deep contrast and metallic highlights",
        LutCategory.CREATIVE
    ),
    CYBER_NEON_NIGHT(
        "Cyberpunk Neon",
        "Electric cyan and magenta separation tuned for night cityscapes",
        LutCategory.CREATIVE
    ),
    VINTAGE_SEPIA(
        "Vintage Warm Sepia",
        "Nostalgic golden-hour warm shadows with analog softness",
        LutCategory.CREATIVE
    ),
    CUSTOM_SAVED(
        "Custom Grade LUT",
        "User created custom color transformation",
        LutCategory.CUSTOM
    ),
    CUSTOM_CUBE(
        "Custom .cube 3D LUT",
        "Imported 3D LUT table",
        LutCategory.CUSTOM
    )
}

data class ColorGradeSettings(
    // Primary Wheels
    val lift: WheelValue = WheelValue(),
    val gamma: WheelValue = WheelValue(),
    val gain: WheelValue = WheelValue(),
    val offset: WheelValue = WheelValue(),

    // DaVinci Resolve Log Zone Wheels (Shadow / Midtone / Highlight / Offset + Low/High Range)
    val logWheels: LogWheelSettings = LogWheelSettings(),

    // Basic Sliders
    val brightness: Float = 0.0f,         // -1.0..1.0 (Black floor / brightness lift)
    val contrast: Float = 1.0f,           // 0.5..1.8
    val saturation: Float = 1.0f,         // 0.0..2.0
    val temperature: Float = 0.0f,       // -1.0..1.0 (Cool to Warm)
    val tint: Float = 0.0f,              // -1.0..1.0 (Green to Magenta)
    val exposure: Float = 0.0f,          // -2.0..2.0 EV
    val highlights: Float = 0.0f,        // -1.0..1.0
    val shadows: Float = 0.0f,           // -1.0..1.0
    val colorBoost: Float = 0.0f,        // -1.0..1.0 (Smart vibrance)

    // DaVinci Resolve Final Output Node: Color Space Transform (CST)
    val cstNode: CstNodeSettings = CstNodeSettings(),

    // Samsung S24 Ultra Log Specific (Disabled by default so imported videos remain 100% unmodified until edited)
    val samsungLogTransform: Boolean = false,
    val samsungHighlightRollOff: Float = 0.75f,
    val samsungDynamicToneMapping: Boolean = false,

    // LUT (None by default so imported videos are never altered unless user chooses a LUT)
    val activeLut: LutPreset = LutPreset.NONE,
    val lutIntensity: Float = 1.0f,
    val lutBypass: Boolean = false,       // Instant A/B preview toggle
    val customLutData: FloatArray? = null,
    val customLutSize: Int = 0,
    val customLuts: List<CustomLut> = emptyList(),
    val selectedCustomLutId: String? = null,

    // Curves
    val curves: RgbCurves = RgbCurves(),

    // Masking / Power Window
    val mask: PowerWindowMask = PowerWindowMask(),

    // DaVinci Resolve 3D HSL Qualifier (Secondary Color Keyer)
    val hslQualifier: HslQualifierSettings = HslQualifierSettings(),

    // Effects
    val effects: FilmEffects = FilmEffects(),

    // Professional Monitor Modes
    val splitScreenWipe: Float = 1.0f,   // 1.0 = Full Graded, < 1.0 = A/B Split Screen Wipe
    val showFalseColor: Boolean = false, // Resolve-grade IRE False Color Exposure tool

    // State Toggles
    val bypass: Boolean = false
) : Serializable {
    val isIdentity: Boolean
        get() = !samsungLogTransform &&
                exposure == 0f && brightness == 0f && contrast == 1f && saturation == 1f &&
                temperature == 0f && tint == 0f && highlights == 0f && shadows == 0f && colorBoost == 0f &&
                lift == WheelValue() && gamma == WheelValue() && gain == WheelValue() && offset == WheelValue() &&
                !logWheels.hasAdjustments &&
                (activeLut == LutPreset.NONE || lutBypass || lutIntensity <= 0f) &&
                !cstNode.enabled &&
                mask.type == MaskType.NONE &&
                !hslQualifier.enabled &&
                !effects.grain.enabled && effects.grainIntensity <= 0f &&
                effects.halationIntensity <= 0f && effects.vignetteIntensity <= 0f &&
                curves.isIdentity

    val hasCustomGrade: Boolean
        get() = !isIdentity
}

enum class HslQualifierPreset(
    val displayName: String,
    val hueCenterDeg: Float,
    val hueWidthDeg: Float,
    val hueSoftnessDeg: Float
) {
    SKIN_TONES("Skin Tones", 24f, 38f, 18f),
    SKY_CYAN_BLUE("Sky & Cyan", 205f, 50f, 22f),
    FOLIAGE_GREEN("Foliage Green", 115f, 55f, 25f),
    NEON_MAGENTA("Neon Magenta", 315f, 48f, 20f),
    GOLDEN_AMBER("Golden Amber", 42f, 40f, 18f)
}

data class HslQualifierSettings(
    val enabled: Boolean = false,
    val showMatte: Boolean = false,
    val hueCenterDeg: Float = 24f,       // 0..360 degrees (24° = natural skin tone line)
    val hueWidthDeg: Float = 38f,        // 5..120 degrees
    val hueSoftnessDeg: Float = 18f,     // 2..60 degrees feather
    val satMin: Float = 0.08f,           // 0..1 minimum saturation threshold
    val lumMin: Float = 0.05f,           // 0..1 minimum luminance threshold
    val hueShiftDeg: Float = 0f,         // -60..+60 degrees hue rotation inside key
    val satAdjust: Float = 1.0f,         // 0.0..2.0 saturation multiplier inside key
    val lumAdjust: Float = 0.0f,         // -0.5..+0.5 luminance offset inside key
    val tempAdjust: Float = 0.0f         // -0.5..+0.5 warmth offset inside key
) : Serializable

data class TimelineClip(
    val id: String = java.util.UUID.randomUUID().toString(),
    val clipNumber: Int,
    val title: String,
    val subtitle: String,
    val sceneName: String = "S24_LOG_SUNSET_PORTRAIT",
    val importedUriString: String? = null,
    val durationMs: Long = 4_000L,
    val settings: ColorGradeSettings = ColorGradeSettings()
) : Serializable
