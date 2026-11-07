package com.example

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.color.SamsungLogColorScience
import com.example.model.*
import com.example.ui.GradingStudioScreen
import com.example.ui.GradingViewModel
import com.example.ui.components.StudioToolTab
import com.example.ui.state.StudioUiStateManager
import com.example.ui.theme.ResolveStudioTheme
import com.example.video.SampleMediaGenerator
import com.example.video.SampleScene
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotCaptureTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun saveActivityScreenshot(fileName: String) {
        composeRule.waitForIdle()
        val decorView = composeRule.activity.window.decorView
        val width = decorView.width.coerceAtLeast(1)
        val height = decorView.height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        decorView.draw(canvas)

        val targetDirs = listOf(
            File("/Users/metteore/antigravity/ResolveStudio-2026-11-05-a63fb/screenshots"),
            File("/Users/metteore/antigravity/ResolveStudio-2026-11-05-a63fb/public/screenshots"),
            File("/Users/metteore/antigravity/ResolveStudio-2026-11-05-a63fb/landing/screenshots"),
            File("/Users/metteore/antigravity/ResolveStudio-2026-11-05-a63fb/landing/public/screenshots"),
            File("/Users/metteore/antigravity/ResolveStudio-2026-11-05-a63fb/dist/screenshots"),
            File("/Users/metteore/antigravity/ResolveStudio-2026-11-05-a63fb/dist/public/screenshots"),
            File("/Users/metteore/antigravity/ResolveStudio-2026-11-05-a63fb/landing/dist/screenshots"),
            File("/Users/metteore/antigravity/ResolveStudio-2026-11-05-a63fb/landing/dist/public/screenshots")
        )

        for (dir in targetDirs) {
            dir.mkdirs()
            val outFile = File(dir, fileName)
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
        }
        println("Saved screenshot to all targets: $fileName (${width}x${height})")
    }

    private fun pumpBitmaps(vm: GradingViewModel) {
        var spins = 0
        while (vm.gradedFrameBitmap.value == null && spins < 50) {
            Thread.sleep(40)
            spins++
        }
        composeRule.waitForIdle()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
    fun captureWheelsScopes() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = GradingViewModel(app)

        vm.loadScene(SampleScene.S24_LOG_SUNSET_PORTRAIT)
        vm.updateSettings("Warm Golden Hour Grade", immediateCommit = true) {
            it.copy(
                lift = WheelValue(-0.10f, -0.05f, -0.03f),
                gamma = WheelValue(0.08f, 0.03f, 0.02f),
                gain = WheelValue(0.18f, 0.08f, 0.05f),
                contrast = 1.18f,
                saturation = 1.22f,
                colorBoost = 0.28f,
                temperature = 0.16f,
                activeLut = LutPreset.FILM_EMULATION_KODAK,
                lutIntensity = 0.85f,
                logWheels = LogWheelSettings(
                    shadow = WheelValue(-0.08f, -0.04f, 0.02f),
                    midtone = WheelValue(0.04f, 0.02f, -0.01f),
                    highlight = WheelValue(0.12f, 0.06f, -0.04f),
                    lowRange = 0.30f,
                    highRange = 0.70f
                )
            )
        }
        Thread.sleep(250)

        val dockManager = StudioUiStateManager().apply { expandToDocked() }
        composeRule.setContent {
            ResolveStudioTheme {
                GradingStudioScreen(
                    viewModel = vm,
                    uiStateManager = dockManager,
                    initialTab = StudioToolTab.WHEELS,
                    initialShowScopes = true,
                    initialHistogramOverlay = true,
                    initialUseLogWheels = true
                )
            }
        }
        pumpBitmaps(vm)
        saveActivityScreenshot("app-wheels-scopes.png")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
    fun captureCurvesParade() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = GradingViewModel(app)

        vm.loadScene(SampleScene.S24_LOG_NEON_CITY)
        vm.updateSettings("Cyberpunk Spline Grade", immediateCommit = true) {
            it.copy(
                activeLut = LutPreset.CYBER_NEON_NIGHT,
                lutIntensity = 0.90f,
                contrast = 1.24f,
                saturation = 1.30f,
                curves = RgbCurves(
                    master = listOf(
                        CurvePoint(0f, 0.02f),
                        CurvePoint(0.25f, 0.18f),
                        CurvePoint(0.75f, 0.84f),
                        CurvePoint(1f, 0.98f)
                    ),
                    red = listOf(CurvePoint(0f, 0f), CurvePoint(0.5f, 0.54f), CurvePoint(1f, 1f)),
                    blue = listOf(CurvePoint(0f, 0.06f), CurvePoint(0.5f, 0.56f), CurvePoint(1f, 0.96f))
                )
            )
        }
        Thread.sleep(250)

        val dockManager = StudioUiStateManager().apply { expandToDocked() }
        composeRule.setContent {
            ResolveStudioTheme {
                GradingStudioScreen(
                    viewModel = vm,
                    uiStateManager = dockManager,
                    initialTab = StudioToolTab.CURVES,
                    initialShowScopes = true
                )
            }
        }
        pumpBitmaps(vm)
        try {
            composeRule.onNodeWithTag("dock_scope_selector_waveform_parade").performClick()
            composeRule.waitForIdle()
        } catch (_: Throwable) {}
        saveActivityScreenshot("app-curves-parade.png")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
    fun captureLutsBrowser() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = GradingViewModel(app)

        vm.loadScene(SampleScene.S24_LOG_ALPINE_LAKE)
        vm.updateSettings("Fujifilm Classic Negative", immediateCommit = true) {
            it.copy(
                activeLut = LutPreset.FUJI_CLASSIC_NEGATIVE,
                lutIntensity = 0.92f,
                splitScreenWipe = 0.58f
            )
        }
        Thread.sleep(250)

        val dockManager = StudioUiStateManager().apply { expandToDocked() }
        composeRule.setContent {
            ResolveStudioTheme {
                GradingStudioScreen(
                    viewModel = vm,
                    uiStateManager = dockManager,
                    initialTab = StudioToolTab.LUTS,
                    initialShowScopes = true
                )
            }
        }
        pumpBitmaps(vm)
        saveActivityScreenshot("app-luts-browser.png")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
    fun capturePowerWindow() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = GradingViewModel(app)

        vm.loadScene(SampleScene.S24_LOG_CINEMA_INTERIOR)
        vm.updateSettings("Power Window Spotlight", immediateCommit = true) {
            it.copy(
                activeLut = LutPreset.FILM_EMULATION_KODAK,
                splitScreenWipe = 1.0f,
                mask = PowerWindowMask(
                    type = MaskType.TRIANGLE,
                    centerX = 0.50f,
                    centerY = 0.45f,
                    radiusX = 0.32f,
                    radiusY = 0.30f,
                    feather = 0.40f,
                    rotationDeg = -15f,
                    trackingEnabled = true,
                    trackingKeyframes = listOf(
                        MaskTrackerKeyframe(0L, 0.50f, 0.45f, 0.99f),
                        MaskTrackerKeyframe(500L, 0.51f, 0.44f, 0.98f),
                        MaskTrackerKeyframe(1000L, 0.52f, 0.46f, 0.97f)
                    )
                )
            )
        }
        Thread.sleep(250)

        val dockManager = StudioUiStateManager().apply { expandToDocked() }
        composeRule.setContent {
            ResolveStudioTheme {
                GradingStudioScreen(
                    viewModel = vm,
                    uiStateManager = dockManager,
                    initialTab = StudioToolTab.MASK,
                    initialShowScopes = true
                )
            }
        }
        pumpBitmaps(vm)
        saveActivityScreenshot("app-power-window.png")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w880dp-h520dp-xxhdpi")
    fun captureLandscapeStudioScreenshot() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = GradingViewModel(app)

        vm.loadScene(SampleScene.S24_LOG_SUNSET_PORTRAIT)
        vm.updateSettings("Studio Master Grade", immediateCommit = true) {
            it.copy(
                lift = WheelValue(-0.14f, -0.05f, -0.02f),
                gamma = WheelValue(0.06f, 0.02f, 0.03f),
                gain = WheelValue(0.16f, 0.09f, 0.05f),
                contrast = 1.16f,
                saturation = 1.18f,
                colorBoost = 0.25f,
                activeLut = LutPreset.SAMSUNG_CINE_WARM,
                splitScreenWipe = 0.55f
            )
        }
        Thread.sleep(250)

        composeRule.setContent {
            ResolveStudioTheme {
                GradingStudioScreen(viewModel = vm)
            }
        }
        pumpBitmaps(vm)
        composeRule.waitForIdle()
        saveActivityScreenshot("app-landscape-studio.png")
    }
}
