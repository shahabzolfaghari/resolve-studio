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
}
