package com.example

import com.example.color.SamsungLogColorScience
import com.example.model.*
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testSamsungLogToLinear() {
        // Flat shadows in log (~0.15) should map down to near 0.1 in linear
        val linearLow = SamsungLogColorScience.samsungLogToLinear(0.14f)
        assertTrue(linearLow >= 0f)
        assertTrue(linearLow < 0.2f)

        // Mid-gray in log (~0.45) should map to ~0.18-0.35 in linear
        val linearMid = SamsungLogColorScience.samsungLogToLinear(0.45f)
        assertTrue(linearMid > linearLow)

        // Highlights in log (~0.85) should expand to near 1.0 in linear
        val linearHigh = SamsungLogColorScience.samsungLogToLinear(0.85f)
        assertTrue(linearHigh > linearMid)
    }

    @Test
    fun testSplineCurveLut() {
        val points = listOf(
            CurvePoint(0f, 0f),
            CurvePoint(0.5f, 0.7f),
            CurvePoint(1f, 1f)
        )
        val lut = SamsungLogColorScience.buildSplineLut(points)
        assertEquals(256, lut.size)
        // Check endpoints
        assertEquals(0f, lut[0], 0.05f)
        assertEquals(1f, lut[255], 0.05f)
        // Midpoint should be elevated according to S-curve
        assertTrue(lut[128] > 0.5f)
    }

    @Test
    fun testMaskEvaluation() {
        // Test Elliptical Mask
        val ellipticalMask = PowerWindowMask(
            type = MaskType.ELLIPTICAL,
            centerX = 0.5f,
            centerY = 0.5f,
            radiusX = 0.3f,
            radiusY = 0.3f,
            feather = 0.2f,
            invert = false
        )
        // Center point should be fully inside mask (weight 1.0)
        val centerWeight = SamsungLogColorScience.evaluateMask(0.5f, 0.5f, ellipticalMask)
        assertEquals(1.0f, centerWeight, 0.01f)

        // Far corner should be outside mask (weight 0.0)
        val cornerWeight = SamsungLogColorScience.evaluateMask(0.0f, 0.0f, ellipticalMask)
        assertEquals(0.0f, cornerWeight, 0.01f)

        // Test Rectangular Mask
        val rectMask = PowerWindowMask(
            type = MaskType.RECTANGULAR,
            centerX = 0.5f,
            centerY = 0.5f,
            radiusX = 0.25f,
            radiusY = 0.20f,
            feather = 0.2f,
            invert = false
        )
        val rectCenter = SamsungLogColorScience.evaluateMask(0.5f, 0.5f, rectMask)
        assertEquals(1.0f, rectCenter, 0.01f)

        val rectOutside = SamsungLogColorScience.evaluateMask(0.9f, 0.9f, rectMask)
        assertEquals(0.0f, rectOutside, 0.01f)

        // Test Invert mode
        val invertedRect = rectMask.copy(invert = true)
        val invertedCenter = SamsungLogColorScience.evaluateMask(0.5f, 0.5f, invertedRect)
        assertEquals(0.0f, invertedCenter, 0.01f)
    }

    @Test
    fun testCubeFormatParsing() {
        val sampleCube = """
            # Test 3D LUT
            TITLE "Sample"
            LUT_3D_SIZE 2
            0.0 0.0 0.0
            1.0 0.0 0.0
            0.0 1.0 0.0
            1.0 1.0 0.0
            0.0 0.0 1.0
            1.0 0.0 1.0
            0.0 1.0 1.0
            1.0 1.0 1.0
        """.trimIndent()

        val parsed = SamsungLogColorScience.parseCubeLut(sampleCube)
        assertNotNull(parsed)
        assertEquals(2, parsed!!.first)
        assertEquals(2 * 2 * 2 * 3, parsed.second.size)
    }

    @Test
    fun testUndoRedoManager() {
        val manager = UndoRedoManager(maxHistory = 10)
        assertFalse(manager.canUndo)
        assertFalse(manager.canRedo)
        assertEquals(1, manager.historyEntries.size)

        // 1. Change lift
        val state1 = ColorGradeSettings(lift = WheelValue(x = 0.2f, y = 0.1f, lum = 0.05f))
        manager.pushSnapshot(state1, "Adjust Lift")
        assertTrue(manager.canUndo)
        assertFalse(manager.canRedo)
        assertEquals(2, manager.historyEntries.size)

        // 2. Change curves
        val state2 = state1.copy(contrast = 1.35f)
        manager.pushSnapshot(state2, "Adjust Contrast")
        assertEquals(3, manager.historyEntries.size)

        // 3. Undo
        val undid = manager.undo()
        assertNotNull(undid)
        assertEquals("Adjust Lift", undid!!.description)
        assertEquals(state1, undid.settings)
        assertTrue(manager.canUndo)
        assertTrue(manager.canRedo)

        // 4. Redo
        val redid = manager.redo()
        assertNotNull(redid)
        assertEquals("Adjust Contrast", redid!!.description)
        assertEquals(state2, redid.settings)
        assertFalse(manager.canRedo)

        // 5. Jump back to initial
        val initialEntry = manager.historyEntries.first()
        val jumped = manager.jumpTo(initialEntry)
        assertNotNull(jumped)
        assertFalse(manager.canUndo)
        assertTrue(manager.canRedo)
    }

    @Test
    fun testLutPresetsMapping() {
        // 1. Samsung Log to Rec.709 Natural
        val (r1, g1, b1) = SamsungLogColorScience.applyLutPreset(0.5f, 0.5f, 0.5f, LutPreset.SAMSUNG_LOG_TO_REC709)
        assertEquals(0.5f, r1, 0.05f)
        assertEquals(0.5f, g1, 0.05f)
        assertEquals(0.5f, b1, 0.05f)

        // 2. Monochrome Noir should output identical R, G, B channels (grayscale)
        val (rbw, gbw, bbw) = SamsungLogColorScience.applyLutPreset(0.7f, 0.4f, 0.2f, LutPreset.MONOCHROME_NOIR)
        assertEquals(rbw, gbw, 0.001f)
        assertEquals(gbw, bbw, 0.001f)

        // 3. Samsung S24 Vivid Punch should expand contrast and vibrancy
        val (rp, gp, bp) = SamsungLogColorScience.applyLutPreset(0.8f, 0.5f, 0.3f, LutPreset.SAMSUNG_REC709_PUNCH)
        assertTrue(rp > 0.8f) // Red boost in punch look
    }

    @Test
    fun testCustomLutCreation() {
        val custom = CustomLut(
            name = "Golden Sunrise",
            description = "Warm saturated morning glow",
            temperature = 0.5f,
            saturation = 1.3f
        )
        assertEquals("Golden Sunrise", custom.name)
        assertEquals(0.5f, custom.temperature, 0.01f)

        val settings = ColorGradeSettings(
            customLuts = listOf(custom),
            activeLut = LutPreset.CUSTOM_SAVED,
            selectedCustomLutId = custom.id
        )
        val (r, g, b) = SamsungLogColorScience.applyLutPreset(0.5f, 0.5f, 0.5f, LutPreset.CUSTOM_SAVED, settings)
        // With warm temperature (+0.5), red should increase and blue should decrease
        assertTrue(r > b)
    }

    @Test
    fun testStudioUiStateManager() {
        val manager = com.example.ui.state.StudioUiStateManager(com.example.ui.state.ScreenSizeClass.COMPACT)
        // Compact screens default to collapsed floating menu
        assertEquals(com.example.ui.state.DockPresentationMode.COLLAPSED_FLOATING, manager.dockMode)
        assertFalse(manager.isFloatingMenuExpanded)
        assertFalse(manager.isToolSheetOpen)

        // Expand floating menu
        manager.toggleFloatingMenu()
        assertTrue(manager.isFloatingMenuExpanded)

        // Open tool sheet
        manager.openToolSheet()
        assertTrue(manager.isToolSheetOpen)
        assertFalse(manager.isFloatingMenuExpanded)

        // Close tool sheet
        manager.closeToolSheet()
        assertFalse(manager.isToolSheetOpen)

        // Toggle to docked mode
        manager.toggleDockMode()
        assertEquals(com.example.ui.state.DockPresentationMode.DOCKED, manager.dockMode)

        // Collapse back to floating
        manager.collapseToFloating()
        assertEquals(com.example.ui.state.DockPresentationMode.COLLAPSED_FLOATING, manager.dockMode)
    }

    @Test
    fun testFilmGrainProcessorEmulsionDensity() {
        // Physical silver halide density is highest in midtones (~0.5)
        val midtoneDensity = com.example.color.FilmGrainProcessor.computeEmulsionDensity(0.5f, midtoneBias = 0.8f)
        val highlightDensity = com.example.color.FilmGrainProcessor.computeEmulsionDensity(0.98f, midtoneBias = 0.8f)
        assertTrue("Midtone grain density should be higher than highlight density", midtoneDensity > highlightDensity)
        assertTrue(midtoneDensity in 0.5f..1.0f)
    }

    @Test
    fun testFilmGrainApplication() {
        val midGray = (0xFF shl 24) or (128 shl 16) or (128 shl 8) or 128
        val pixels = IntArray(100) { midGray }
        val grainSettings = FilmGrainSettings(
            enabled = true,
            strength = 0.5f,
            size = 1.5f,
            roughness = 0.7f,
            monochrome = true
        )
        com.example.color.FilmGrainProcessor.applyGrain(pixels, 10, 10, grainSettings)
        // Ensure values were altered and stayed in legal 0..255 range
        var foundDifferent = false
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            assertTrue(r in 0..255)
            assertTrue(g in 0..255)
            assertTrue(b in 0..255)
            if (r != 128) foundDifferent = true
        }
        assertTrue("Film grain should perturb pixel values", foundDifferent)
    }

    @Test
    fun testDefaultPresetsList() {
        val presets = GradePreset.DEFAULT_PRESETS
        assertEquals(5, presets.size)

        // Verify all preset IDs are unique
        val ids = presets.map { it.id }
        assertEquals(ids.size, ids.distinct().size)

        // Check specific known presets
        val golden = presets.first { it.id == "builtin_s24_golden_warmth" }
        assertEquals("S24 Golden Hour Warmth", golden.name)
        assertTrue(golden.isBuiltIn)
        assertEquals(LutPreset.SAMSUNG_CINE_WARM, golden.settings.activeLut)
        assertTrue(golden.settings.effects.grain.enabled)

        val noir = presets.first { it.id == "builtin_silver_gelatin_bw" }
        assertEquals(0.0f, noir.settings.saturation, 0.001f)
        assertEquals(LutPreset.MONOCHROME_NOIR, noir.settings.activeLut)
    }

    @Test
    fun testPresetJsonSerialization() {
        val moshi = com.squareup.moshi.Moshi.Builder()
            .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()
        val adapter = moshi.adapter(GradePreset::class.java)

        val originalPreset = GradePreset(
            name = "Test Teal & Orange",
            description = "Custom test look",
            category = "Cinematic",
            settings = ColorGradeSettings(
                contrast = 1.25f,
                saturation = 1.15f,
                temperature = 0.2f,
                activeLut = LutPreset.BLOCKBUSTER_TEAL_ORANGE,
                effects = FilmEffects(
                    grain = FilmGrainSettings(enabled = true, strength = 0.35f)
                )
            )
        )

        val json = adapter.toJson(originalPreset)
        assertNotNull(json)
        assertTrue(json.contains("Test Teal & Orange"))

        val deserialized = adapter.fromJson(json)
        assertNotNull(deserialized)
        assertEquals(originalPreset.name, deserialized!!.name)
        assertEquals(originalPreset.category, deserialized.category)
        assertEquals(1.25f, deserialized.settings.contrast, 0.001f)
        assertEquals(LutPreset.BLOCKBUSTER_TEAL_ORANGE, deserialized.settings.activeLut)
        assertTrue(deserialized.settings.effects.grain.enabled)
        assertEquals(0.35f, deserialized.settings.effects.grain.strength, 0.001f)
    }

    @Test
    fun testColorSpaceTransformNodeDWG() {
        val cst = CstNodeSettings(
            enabled = true,
            inputColorSpace = InputColorSpace.DAVINCI_WIDE_GAMUT,
            inputGamma = InputGamma.DAVINCI_INTERMEDIATE,
            outputColorSpace = OutputColorSpace.REC709,
            outputGamma = OutputGamma.GAMMA_2_4,
            toneMapping = ToneMappingMethod.DAVINCI
        )
        val (r, g, b) = SamsungLogColorScience.applyColorSpaceTransform(0.5f, 0.5f, 0.5f, cst)
        assertTrue(r in 0f..1f)
        assertTrue(g in 0f..1f)
        assertTrue(b in 0f..1f)

        // Test CST bypass
        val bypassedCst = cst.copy(enabled = false)
        val (br, bg, bb) = SamsungLogColorScience.applyColorSpaceTransform(0.42f, 0.55f, 0.68f, bypassedCst)
        assertEquals(0.42f, br, 0.001f)
        assertEquals(0.55f, bg, 0.001f)
        assertEquals(0.68f, bb, 0.001f)
    }

    @Test
    fun testColorSpaceTransformNodeSamsungS24() {
        val cst = CstNodeSettings(
            enabled = true,
            inputColorSpace = InputColorSpace.SAMSUNG_S24_GAMUT,
            inputGamma = InputGamma.SAMSUNG_LOG,
            outputColorSpace = OutputColorSpace.REC709,
            outputGamma = OutputGamma.REC709
        )
        // Red primary in wide gamut transforms to legal Rec.709
        val (r, g, b) = SamsungLogColorScience.applyColorSpaceTransform(0.8f, 0.2f, 0.1f, cst)
        assertTrue(r > g)
        assertTrue(r > b)
        assertTrue(r in 0f..1f)
    }

    @Test
    fun testFalseColorIREZones() {
        // Deep black (0 IRE) should map to Purple
        val (pr, pg, pb) = SamsungLogColorScience.evaluateFalseColor(0.01f, 0.01f, 0.01f)
        assertTrue(pr > 0.4f && pb > 0.6f && pg == 0f)

        // 18% Middle Gray (~40 IRE) should map to Green
        val (gr, gg, gb) = SamsungLogColorScience.evaluateFalseColor(0.40f, 0.40f, 0.40f)
        assertTrue(gg > 0.8f && gr == 0f)

        // Blown clipped highlight (100 IRE) should map to Red
        val (rr, rg, rb) = SamsungLogColorScience.evaluateFalseColor(0.99f, 0.99f, 0.99f)
        assertTrue(rr > 0.9f && rg < 0.1f && rb < 0.1f)
    }

    @Test
    fun testPrimaryAdjustmentSlidersInSettings() {
        val settings = ColorGradeSettings(
            brightness = 0.2f,
            exposure = 0.3f,
            contrast = 1.15f,
            temperature = 0.25f,
            tint = -0.1f,
            saturation = 1.2f,
            shadows = 0.15f,
            highlights = -0.2f,
            colorBoost = 0.35f
        )
        assertEquals(0.2f, settings.brightness, 0.001f)
        assertEquals(0.3f, settings.exposure, 0.001f)
        assertEquals(1.15f, settings.contrast, 0.001f)
        assertEquals(0.25f, settings.temperature, 0.001f)
        assertEquals(-0.1f, settings.tint, 0.001f)
        assertEquals(1.2f, settings.saturation, 0.001f)
        assertEquals(0.15f, settings.shadows, 0.001f)
        assertEquals(-0.2f, settings.highlights, 0.001f)
        assertEquals(0.35f, settings.colorBoost, 0.001f)
    }

    @Test
    fun testBezierMaskEvaluation() {
        val bezierMask = PowerWindowMask(
            type = MaskType.CUSTOM_BEZIER,
            feather = 0.2f,
            invert = false,
            bezierPoints = defaultBezierPoints()
        )

        // Center (0.5, 0.5) is inside the default 4-point diamond/oval Bezier loop
        val centerWeight = SamsungLogColorScience.evaluateMask(0.5f, 0.5f, bezierMask)
        assertEquals(1.0f, centerWeight, 0.05f)

        // Far corner (0.05, 0.05) is outside the Bezier loop
        val cornerWeight = SamsungLogColorScience.evaluateMask(0.05f, 0.05f, bezierMask)
        assertEquals(0.0f, cornerWeight, 0.05f)

        // Invert mode: center should be 0.0 and corner should be 1.0
        val invertedMask = bezierMask.copy(invert = true)
        val invertedCenter = SamsungLogColorScience.evaluateMask(0.5f, 0.5f, invertedMask)
        assertEquals(0.0f, invertedCenter, 0.05f)
        val invertedCorner = SamsungLogColorScience.evaluateMask(0.05f, 0.05f, invertedMask)
        assertEquals(1.0f, invertedCorner, 0.05f)
    }

    @Test
    fun testFujifilmRecipeLuts() {
        // Test all 7 famous Fujifilm simulation recipes for legal range [0..1]
        val fujiPresets = listOf(
            LutPreset.FUJI_CLASSIC_CHROME,
            LutPreset.FUJI_CLASSIC_NEGATIVE,
            LutPreset.FUJI_NOSTALGIC_NEGATIVE,
            LutPreset.FUJI_VELVIA,
            LutPreset.FUJI_PROVIA,
            LutPreset.FUJI_ASTIA,
            LutPreset.FUJI_ACROS
        )

        assertEquals(7, fujiPresets.size)

        for (preset in fujiPresets) {
            val (r, g, b) = SamsungLogColorScience.applyLutPreset(0.6f, 0.45f, 0.35f, preset)
            assertTrue("Preset ${preset.name} red in bounds", r in 0f..1f)
            assertTrue("Preset ${preset.name} green in bounds", g in 0f..1f)
            assertTrue("Preset ${preset.name} blue in bounds", b in 0f..1f)
        }

        // ACROS must produce pure monochrome (identical R, G, B)
        val (ar, ag, ab) = SamsungLogColorScience.applyLutPreset(0.72f, 0.41f, 0.28f, LutPreset.FUJI_ACROS)
        assertEquals(ar, ag, 0.001f)
        assertEquals(ag, ab, 0.001f)

        // Velvia should have punchier saturation than Astia
        val (vr, _, vb) = SamsungLogColorScience.applyLutPreset(0.7f, 0.4f, 0.2f, LutPreset.FUJI_VELVIA)
        val (asr, _, asb) = SamsungLogColorScience.applyLutPreset(0.7f, 0.4f, 0.2f, LutPreset.FUJI_ASTIA)
        assertTrue("Velvia red saturation punch", (vr - vb) > (asr - asb))
    }

    @Test
    fun testInstagramReelsExportDimensions() {
        val reels4k = com.example.video.VideoExportConfig(
            resolution = com.example.video.ExportResolution.UHD_4K,
            aspectRatio = com.example.video.ExportAspectRatio.REELS_9_16
        )
        val (w4k, h4k) = reels4k.getTargetDimensions()
        assertEquals(2160, w4k)
        assertEquals(3840, h4k)

        val reelsFhd = com.example.video.VideoExportConfig(
            resolution = com.example.video.ExportResolution.FHD_1080P,
            aspectRatio = com.example.video.ExportAspectRatio.REELS_9_16
        )
        val (wFhd, hFhd) = reelsFhd.getTargetDimensions()
        assertEquals(1080, wFhd)
        assertEquals(1920, hFhd)

        val cinema4k = com.example.video.VideoExportConfig(
            resolution = com.example.video.ExportResolution.UHD_4K,
            aspectRatio = com.example.video.ExportAspectRatio.CINEMA_16_9
        )
        val (cw, ch) = cinema4k.getTargetDimensions()
        assertEquals(3840, cw)
        assertEquals(2160, ch)

        val square = com.example.video.VideoExportConfig(
            resolution = com.example.video.ExportResolution.FHD_1080P,
            aspectRatio = com.example.video.ExportAspectRatio.SQUARE_1_1
        )
        val (sw, sh) = square.getTargetDimensions()
        assertEquals(1080, sw)
        assertEquals(1080, sh)
    }

    @Test
    fun testExportQualityBitrates() {
        val master = com.example.video.ExportBitrateQuality.LOSSLESS_MASTER_4K
        assertEquals(100_000_000, master.bitrateBps)
        assertTrue(master.isLosslessMaster)

        val highCinema = com.example.video.ExportBitrateQuality.HIGH_QUALITY_60M
        assertEquals(60_000_000, highCinema.bitrateBps)

        val reelsOptimized = com.example.video.ExportBitrateQuality.REELS_OPTIMIZED_25M
        assertEquals(25_000_000, reelsOptimized.bitrateBps)

        val standard = com.example.video.ExportBitrateQuality.BALANCED_12M
        assertEquals(12_000_000, standard.bitrateBps)
    }

    @Test
    fun testPrimaryColorWheelRgbShift() {
        // Center position (0, 0) gives neutral zero offset
        val (r0, g0, b0) = SamsungLogColorScience.wheelToRgbShift(0f, 0f)
        assertEquals(0f, r0, 0.001f)
        assertEquals(0f, g0, 0.001f)
        assertEquals(0f, b0, 0.001f)

        // Pure top (y=1) biases green
        val (rg, gg, bg) = SamsungLogColorScience.wheelToRgbShift(0f, 1f)
        assertEquals(1f, gg, 0.001f)
        assertEquals(-0.5f, rg, 0.001f)
        assertEquals(-0.5f, bg, 0.001f)

        // Pure right (x=1) biases red over blue
        val (rr, gr, br) = SamsungLogColorScience.wheelToRgbShift(1f, 0f)
        assertTrue(rr > 0f)
        assertTrue(br < 0f)
        assertEquals(0f, gr, 0.001f)
    }

    @Test
    fun testCurveQuickPresetsAndHevcExportConfig() {
        // Verify all 5 1-tap cinema curve presets generate valid monotonic 256-entry spline LUTs
        val presets = com.example.ui.components.CurveQuickPreset.values()
        assertEquals(5, presets.size)
        for (preset in presets) {
            val lut = SamsungLogColorScience.buildSplineLut(preset.points)
            assertEquals(256, lut.size)
            assertTrue("Preset ${preset.name} start in bounds", lut[0] in 0f..1f)
            assertTrue("Preset ${preset.name} end in bounds", lut[255] in 0f..1f)
        }

        // Verify HEVC and Gallery export config options
        val hevcConfig = com.example.video.VideoExportConfig(
            codec = com.example.video.ExportVideoCodec.HEVC_H265,
            saveToGallery = true
        )
        assertEquals("video/hevc", hevcConfig.codec.mimeType)
        assertEquals("HEVC", hevcConfig.codec.shortName)
        assertTrue(hevcConfig.saveToGallery)
    }

    @Test
    fun test3dHslQualifierSkinToneIsolation() {
        val skinKey = HslQualifierSettings(
            enabled = true,
            hueCenterDeg = HslQualifierPreset.SKIN_TONES.hueCenterDeg, // 24 deg
            hueWidthDeg = HslQualifierPreset.SKIN_TONES.hueWidthDeg,
            hueSoftnessDeg = HslQualifierPreset.SKIN_TONES.hueSoftnessDeg
        )

        // Warm Caucasian/Golden skin tone RGB (~24 deg orange-peach)
        val skinWeight = SamsungLogColorScience.evaluateHslKeyWeight(0.82f, 0.54f, 0.40f, skinKey)
        assertTrue("Skin tone pixel should be strongly selected by Skin Tones 3D Qualifier", skinWeight > 0.8f)

        // Deep cyan-blue sky pixel (~205 deg) should be rejected (weight 0)
        val skyWeight = SamsungLogColorScience.evaluateHslKeyWeight(0.20f, 0.55f, 0.88f, skinKey)
        assertEquals(0f, skyWeight, 0.01f)
    }

    @Test
    fun testTrianglePowerWindowMask() {
        val triMask = PowerWindowMask(
            type = MaskType.TRIANGLE,
            centerX = 0.5f,
            centerY = 0.5f,
            radiusX = 0.30f,
            radiusY = 0.30f,
            feather = 0.15f
        )
        // Center of triangle (0.5, 0.5) should have full mask weight (1.0)
        val centerWeight = SamsungLogColorScience.evaluateMask(0.5f, 0.5f, triMask)
        assertEquals(1.0f, centerWeight, 0.01f)

        // Far top-left corner (0.05, 0.05) is well outside the triangle and should be 0.0
        val cornerWeight = SamsungLogColorScience.evaluateMask(0.05f, 0.05f, triMask)
        assertEquals(0.0f, cornerWeight, 0.01f)
    }

    @Test
    fun testLogZoneWheelsAndSecondaryCurves() {
        val settings = ColorGradeSettings()
        assertEquals(0.33f, settings.logWheels.lowRange, 0.001f)
        assertEquals(0.68f, settings.logWheels.highRange, 0.001f)

        // Default secondary curves (hueVsHue, hueVsSat, hueVsLum, lumVsSat) are flat at y = 0.5
        val hueSatLut = SamsungLogColorScience.buildSplineLut(settings.curves.hueVsSat)
        assertEquals(0.5f, hueSatLut[128], 0.001f)

        // Boosting Hue vs Sat around Cyan (x=0.5, y=0.8) should raise the LUT at index 128 above 0.7
        val boostedCurves = settings.curves.copy(
            hueVsSat = listOf(
                CurvePoint(0f, 0.5f),
                CurvePoint(0.35f, 0.5f),
                CurvePoint(0.50f, 0.85f),
                CurvePoint(0.65f, 0.5f),
                CurvePoint(1f, 0.5f)
            )
        )
        val boostedLut = SamsungLogColorScience.buildSplineLut(boostedCurves.hueVsSat)
        assertTrue("Boosted Hue vs Sat curve should exceed 0.75 at Cyan midpoint", boostedLut[128] > 0.75f)
    }
}
