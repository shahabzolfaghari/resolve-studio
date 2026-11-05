package com.example.model

import java.io.Serializable
import java.util.UUID

/**
 * Complete custom color grading preset that can be saved and applied across any video clip.
 */
data class GradePreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val category: String = "Custom",
    val createdAt: Long = System.currentTimeMillis(),
    val isBuiltIn: Boolean = false,
    val settings: ColorGradeSettings
) : Serializable {

    companion object {
        /**
         * Curated factory starter presets designed specifically for Samsung S24 Ultra Log 4K footage.
         */
        val DEFAULT_PRESETS: List<GradePreset> by lazy {
            listOf(
                GradePreset(
                    id = "builtin_s24_golden_warmth",
                    name = "S24 Golden Hour Warmth",
                    description = "Warm amber lift, soft contrast, and Kodak 50D 35mm grain",
                    category = "Cinematic",
                    isBuiltIn = true,
                    settings = ColorGradeSettings(
                        temperature = 0.35f,
                        tint = 0.05f,
                        contrast = 1.15f,
                        saturation = 1.12f,
                        activeLut = LutPreset.SAMSUNG_CINE_WARM,
                        lift = WheelValue(x = 0.12f, y = 0.08f, lum = -0.05f),
                        gain = WheelValue(x = 0.15f, y = 0.10f, lum = 0.08f),
                        effects = FilmEffects(
                            grain = FilmGrainSettings(enabled = true, strength = 0.28f, size = 1.0f, roughness = 0.35f),
                            halationIntensity = 0.25f
                        )
                    )
                ),
                GradePreset(
                    id = "builtin_tokyo_cyber_neon",
                    name = "Cyber Neon Night",
                    description = "Deep crushed blacks, electric cyan shadows, and hot magenta highlights",
                    category = "Stylized",
                    isBuiltIn = true,
                    settings = ColorGradeSettings(
                        contrast = 1.30f,
                        saturation = 1.25f,
                        activeLut = LutPreset.CYBER_NEON_NIGHT,
                        lift = WheelValue(x = -0.25f, y = 0.18f, lum = -0.12f),
                        gain = WheelValue(x = 0.20f, y = -0.15f, lum = 0.10f),
                        effects = FilmEffects(
                            grain = FilmGrainSettings(enabled = true, strength = 0.40f, size = 1.6f, roughness = 0.65f),
                            halationIntensity = 0.45f,
                            vignetteIntensity = 0.35f
                        )
                    )
                ),
                GradePreset(
                    id = "builtin_clean_commercial_punch",
                    name = "Clean Commercial Punch",
                    description = "Neutral white point, +0.3 EV exposure pop, and razor sharp clarity",
                    category = "Commercial",
                    isBuiltIn = true,
                    settings = ColorGradeSettings(
                        exposure = 0.3f,
                        contrast = 1.18f,
                        saturation = 1.15f,
                        activeLut = LutPreset.SAMSUNG_REC709_PUNCH,
                        lift = WheelValue(x = 0f, y = 0f, lum = -0.04f),
                        gain = WheelValue(x = 0f, y = 0f, lum = 0.05f),
                        effects = FilmEffects(
                            grain = FilmGrainSettings(enabled = false)
                        )
                    )
                ),
                GradePreset(
                    id = "builtin_super8_vintage_grit",
                    name = "Super 8 Vintage Grit",
                    description = "Heavy 8mm analog grain, warm sepia shadows, and edge vignette",
                    category = "Vintage",
                    isBuiltIn = true,
                    settings = ColorGradeSettings(
                        contrast = 1.22f,
                        saturation = 0.90f,
                        temperature = 0.25f,
                        activeLut = LutPreset.VINTAGE_SEPIA,
                        effects = FilmEffects(
                            grain = FilmGrainSettings(
                                enabled = true,
                                strength = 0.70f,
                                size = 2.4f,
                                roughness = 0.80f,
                                preset = FilmStockPreset.VINTAGE_8MM
                            ),
                            vignetteIntensity = 0.50f
                        )
                    )
                ),
                GradePreset(
                    id = "builtin_silver_gelatin_bw",
                    name = "Silver Gelatin Noir",
                    description = "Monochrome high contrast film with dramatic tonal range",
                    category = "Monochrome",
                    isBuiltIn = true,
                    settings = ColorGradeSettings(
                        contrast = 1.35f,
                        saturation = 0.0f,
                        activeLut = LutPreset.MONOCHROME_NOIR,
                        effects = FilmEffects(
                            grain = FilmGrainSettings(
                                enabled = true,
                                strength = 0.50f,
                                size = 1.4f,
                                roughness = 0.60f,
                                monochrome = true
                            )
                        )
                    )
                )
            )
        }
    }
}
