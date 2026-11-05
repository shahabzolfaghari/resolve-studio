package com.example.model

import java.io.Serializable

enum class InputColorSpace(val displayName: String) {
    DAVINCI_WIDE_GAMUT("DaVinci Wide Gamut (DWG)"),
    SAMSUNG_S24_GAMUT("Samsung S24 Log (BT.2020)"),
    ARRI_WIDE_GAMUT_4("ARRI Wide Gamut 4"),
    SONY_SGAMUT3("Sony S-Gamut3.Cine"),
    REC709("Rec.709")
}

enum class InputGamma(val displayName: String) {
    DAVINCI_INTERMEDIATE("DaVinci Intermediate"),
    SAMSUNG_LOG("Samsung S24 Ultra Log"),
    ARRI_LOGC4("ARRI LogC4"),
    LINEAR("Linear"),
    REC709("Rec.709")
}

enum class OutputColorSpace(val displayName: String) {
    REC709("Rec.709 (HD / SDR Broadcast)"),
    DCI_P3("DCI-P3 (Cinema Display)"),
    REC2020("Rec.2020 (UHD Master)"),
    SRGB("sRGB (Web & Mobile)")
}

enum class OutputGamma(val displayName: String) {
    GAMMA_2_4("Gamma 2.4 (Studio Monitor Default)"),
    REC709("Rec.709"),
    GAMMA_2_2("Gamma 2.2 (Standard PC Display)"),
    SRGB("sRGB")
}

enum class ToneMappingMethod(val displayName: String) {
    DAVINCI("DaVinci (Saturation Preserving)"),
    KNEE_COMPRESSION("Soft Shoulder Knee Compression"),
    LUMINANCE_ONLY("Luminance Mapping"),
    OFF("None (Hard Clip)")
}

enum class GamutMappingMethod(val displayName: String) {
    SATURATION_COMPRESSION("DaVinci Saturation Compression"),
    NONE("None (Clip Out-of-Gamut)")
}

/**
 * DaVinci Resolve Studio Color Space Transform (CST) Node configuration.
 * Placed as the final transformation node in the grading pipeline to map
 * wide-gamut log footage into standardized delivery color spaces (Rec.709).
 */
data class CstNodeSettings(
    val enabled: Boolean = true,
    val inputColorSpace: InputColorSpace = InputColorSpace.DAVINCI_WIDE_GAMUT,
    val inputGamma: InputGamma = InputGamma.DAVINCI_INTERMEDIATE,
    val outputColorSpace: OutputColorSpace = OutputColorSpace.REC709,
    val outputGamma: OutputGamma = OutputGamma.GAMMA_2_4,
    val toneMapping: ToneMappingMethod = ToneMappingMethod.DAVINCI,
    val gamutMapping: GamutMappingMethod = GamutMappingMethod.SATURATION_COMPRESSION,
    val kneeThreshold: Float = 0.75f,
    val saturationLimit: Float = 1.0f
) : Serializable
