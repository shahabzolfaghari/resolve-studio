package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import android.graphics.Bitmap
import com.example.color.SamsungLogColorScience
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("ResolveStudio", appName)
  }

  @Test
  fun `test realtime histogram calculation on bitmap`() {
    val bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
    for (y in 0 until 100) {
      for (x in 0 until 100) {
        val color = if (x < 50) {
          (0xFF shl 24) or (0 shl 16) or (0 shl 8) or 0
        } else {
          (0xFF shl 24) or (118 shl 16) or (118 shl 8) or 118
        }
        bmp.setPixel(x, y, color)
      }
    }

    val histo = SamsungLogColorScience.calculateHistogram(bmp)
    assertEquals(256, histo.lumBins.size)
    assertEquals(256, histo.redBins.size)
    assertEquals(256, histo.greenBins.size)
    assertEquals(256, histo.blueBins.size)
    assertTrue(histo.totalSamples > 0)
    assertTrue("Shadow crush should be detected (> 30%)", histo.shadowClipPct > 30f)
    assertEquals(0f, histo.highlightClipPct, 0.01f)
    assertTrue(histo.avgLum in 40f..70f)
  }

  @Test
  fun `test power window mask preserves base camera transform outside window`() {
    val bmp = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
    val logMidGray = (0xFF shl 24) or (128 shl 16) or (128 shl 8) or 128 // ~0.50 Log mid-gray
    for (y in 0 until 40) {
      for (x in 0 until 40) {
        bmp.setPixel(x, y, logMidGray)
      }
    }

    val settings = com.example.model.ColorGradeSettings(
      samsungLogTransform = true,
      exposure = 1.5f, // Brighten inside the mask
      mask = com.example.model.PowerWindowMask(
        type = com.example.model.MaskType.ELLIPTICAL,
        centerX = 0.5f,
        centerY = 0.5f,
        radiusX = 0.25f,
        radiusY = 0.25f,
        feather = 0.05f
      )
    )

    val graded = SamsungLogColorScience.gradeBitmap(bmp, settings)
    val centerRed = android.graphics.Color.red(graded.getPixel(20, 20))
    val cornerRed = android.graphics.Color.red(graded.getPixel(0, 0))

    // Center (inside mask) has +1.5 EV exposure boost, so it must be brighter than corner (outside mask)
    assertTrue("Inside Power Window ($centerRed) should be brighter than outside ($cornerRed)", centerRed > cornerRed)
    // Outside Power Window should have base Log->Linear recovery applied (< raw 128)
    assertTrue("Outside Power Window ($cornerRed) should preserve base camera transform (< 128)", cornerRed < 128)
  }

  @Test
  fun `test multi clip timeline independent grades and copy paste`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.ui.GradingViewModel(app)

    // Startup starts clean with zero synthetic placeholder clips and 100% neutral grade
    assertEquals(0, vm.timelineClips.value.size)
    assertEquals(com.example.model.LutPreset.NONE, vm.gradeSettings.value.activeLut)
    assertEquals(false, vm.gradeSettings.value.samsungLogTransform)
    assertEquals(false, vm.gradeSettings.value.cstNode.enabled)

    // Populate 4 clips to test multi-clip timeline independent grades and copy/paste
    vm.addClipToTimeline(com.example.video.SampleScene.S24_LOG_SUNSET_PORTRAIT)
    vm.addClipToTimeline(com.example.video.SampleScene.S24_LOG_NEON_CYBERPUNK)
    vm.addClipToTimeline(com.example.video.SampleScene.S24_LOG_FOREST_STREAM)
    vm.addClipToTimeline(com.example.video.SampleScene.S24_LOG_STUDIO_CHART)
    vm.selectTimelineClip(0)

    assertEquals(4, vm.timelineClips.value.size)
    assertEquals(0, vm.activeClipIndex.value)

    // Grade Clip 01 with Kodak 2383 and +0.8 EV
    vm.updateSettings("Grade Clip 01", immediateCommit = true) {
      it.copy(exposure = 0.8f, activeLut = com.example.model.LutPreset.FILM_EMULATION_KODAK)
    }
    assertTrue(vm.timelineClips.value[0].settings.hasCustomGrade)

    // Switch to Clip 02 — should still have default ungraded settings
    vm.selectTimelineClip(1)
    assertEquals(1, vm.activeClipIndex.value)
    assertEquals(0.0f, vm.gradeSettings.value.exposure, 0.001f)

    // Copy grade from Clip 01 and paste onto Clip 02
    vm.selectTimelineClip(0)
    vm.copyActiveClipGrade()
    vm.selectTimelineClip(1)
    vm.pasteGradeToActiveClip()
    assertEquals(0.8f, vm.gradeSettings.value.exposure, 0.001f)
    assertEquals(com.example.model.LutPreset.FILM_EMULATION_KODAK, vm.gradeSettings.value.activeLut)

    // Add a 5th clip to the timeline
    vm.addClipToTimeline(com.example.video.SampleScene.S24_LOG_ALPINE_LAKE)
    assertEquals(5, vm.timelineClips.value.size)
    assertEquals(4, vm.activeClipIndex.value)
  }

  @Test
  fun `test normalized cross correlation motion tracker tracks target displacement`() {
    val w = 80
    val h = 80
    val bmp1 = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val bmp2 = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

    // In frame 1, put a bright 12x12 feature block at (40, 40)
    for (y in 0 until h) {
      for (x in 0 until w) {
        val c1 = if (x in 34..46 && y in 34..46) 0xFFEEEEEE.toInt() else 0xFF222222.toInt()
        bmp1.setPixel(x, y, c1)

        // In frame 2, shift the feature block +6px right and +4px down -> (46, 44)
        val c2 = if (x in 40..52 && y in 38..50) 0xFFEEEEEE.toInt() else 0xFF222222.toInt()
        bmp2.setPixel(x, y, c2)
      }
    }

    val initCx = 40f / w
    val initCy = 40f / h
    val res = SamsungLogColorScience.trackMaskPattern(
      previousBitmap = bmp1,
      currentBitmap = bmp2,
      centerX = initCx,
      centerY = initCy,
      radiusX = 0.20f,
      radiusY = 0.20f
    )

    // The tracker should detect displacement and achieve strong positive confidence
    assertTrue("Tracker confidence (${res.confidence}) should be high (> 0.60)", res.confidence > 0.60f)
    assertTrue("Tracker X (${res.centerX}) should move toward 0.57", res.centerX > initCx)
    assertTrue("Tracker Y (${res.centerY}) should move toward 0.55", res.centerY > initCy)
  }

  @Test
  fun `test periodic hue spline lut wrap around boundary`() {
    val points = listOf(
      com.example.model.CurvePoint(0.05f, 0.65f), // red boost near 0 deg
      com.example.model.CurvePoint(0.50f, 0.50f),
      com.example.model.CurvePoint(0.95f, 0.65f)  // matching red boost near 360 deg
    )
    val lut = SamsungLogColorScience.buildPeriodicSplineLut(points)
    assertEquals(256, lut.size)
    // Red seam: values at 0 and 255 should be smooth and close to each other
    assertTrue("Boundary 0 (${lut[0]}) and 255 (${lut[255]}) should match closely", kotlin.math.abs(lut[0] - lut[255]) < 0.05f)
  }
}
