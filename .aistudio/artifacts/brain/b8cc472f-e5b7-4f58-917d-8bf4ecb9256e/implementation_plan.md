# ResolveStudio: DaVinci Resolve Color Grading Studio for Samsung S24 Ultra Log 4K

ResolveStudio is a mobile video color grading application inspired by DaVinci Resolve's Color Page, specifically tuned for processing Samsung Galaxy S24 Ultra Pro Video 10-bit Log footage. It delivers professional-grade color science, real-time video playback with hardware-accelerated color grading, interactive primary wheels, RGB and Hue curves, power window masking, halation and grain effects, broadcast scopes (Waveform, Vectorscope, Histogram), and full MP4 video and .cube 3D LUT export.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural decisions incorporate the answers provided in Phase 1 and technical specifications of Samsung Galaxy S24 Ultra Pro Video Log mode:

- **Primary Export Workflow**: Full MP4 video export with color grade rendered, plus high-res still frame export and standard `.cube` 3D LUT export (compatible with desktop DaVinci Resolve, Premiere Pro, and Final Cut Pro).
- **Workspace Layout**: DaVinci Resolve dark studio dock with dedicated scopes monitor, primary color wheels (Lift/Gamma/Gain/Offset), RGB/Hue curve editor, power window masking, and effects rack.
- **Color Space & Input Profile**: Native **Samsung Log** (logarithmic gamma curve optimized for 10-bit HEVC Pro Video) with mathematical Rec.709 Color Space Transform (CST), dynamic range expansion, highlight knee roll-off, and bundled official Samsung conversion profiles.
- **Privacy & Storage Model**: Zero-permission Android Photo Picker for importing camera roll videos; standard scoped storage for exporting rendered MP4s, graded frames, and `.cube` LUT files with native Android system share sheet.

---

## 1. Overview & Core Concept

### What It Does
ResolveStudio brings the precision and workflow of DaVinci Resolve's color grading suite to Android smartphones and tablets. It allows videographers and content creators who shoot 4K 10-bit Log footage on the Samsung Galaxy S24 Ultra (and other modern cameras) to preview, correct, grade, and export their footage directly on their device with zero quality degradation.

### Target Audience & Persona
Mobile filmmakers, content creators, cinematographers, and colorists using Samsung Galaxy S24 Ultra Pro Video mode who need an on-device color grading suite that matches desktop NLE precision rather than relying on basic social media filters.

### Key Value
- **True Samsung Log Color Pipeline**: Accurately decompresses Samsung Log gamma and converts to Rec.709 with highlight retention, eliminating muddy midtones and clipped highlights.
- **DaVinci Resolve Tactile Feel**: Familiar dark studio interface with real-time responsive color wheels, spline curves, and live broadcast scopes.
- **Multi-Format Output**: Export high-bitrate graded MP4 videos, frame stills, or `.cube` 3D LUTs to bring into desktop editing workflows.

---

## 2. User Experience & Visual Design

### Key User Flows

```
[Import Clip] ────────► [Color Space Setup] ───► [Primary Correction]
 (Photo Picker /         (Samsung Log -> Rec.709  (Lift / Gamma / Gain
  Sample S24 Log)         Highlight Roll-Off)      Color Wheels & Scopes)
                                                          │
                                                          ▼
[Export Result] ◄────── [Effects & Masking] ◄─── [Curves & Qualifiers]
 (Render MP4, LUT        (Power Windows, Grain,   (RGB Curves, Hue vs Hue,
  or PNG Still)           Halation, Vignette)      Hue vs Sat, Hue vs Lum)
```

1. **Clip Loading**: Open video via system Photo Picker or test instantly with bundled realistic Samsung S24 Ultra Log 4K footage.
2. **Setup & CST**: Toggle input profile (Samsung Log, Flat Pro Video, Standard Rec.709) with automated tone mapping and highlight knee recovery.
3. **Primary Grading**: Touch and drag Lift, Gamma, Gain, and Offset color wheels with master wheel luminance sliders; monitor changes in real time on the Waveform Parade and Vectorscope.
4. **Curves & Secondary Grading**: Fine-tune custom spline curves (Master, Red, Green, Blue) and Hue curves (Hue vs Sat, Hue vs Hue, Hue vs Lum).
5. **Power Windows & Masking**: Add circular or linear gradient masks with feathering to selectively illuminate subjects or apply vignettes.
6. **Film Look**: Dial in analog film grain, halation warm bloom, and highlight glow.
7. **Bypass / Split-Screen Comparison**: Press and hold to instantly compare the raw Samsung Log flat profile against the graded look.
8. **Export**: Render out full-resolution MP4 video, save a pristine 4K PNG still, or export a 33x33x33 `.cube` LUT to transfer to desktop.

### Visual Identity & Theme
- **Studio Dark Aesthetic**: Professional dark palette (`#0E1116` charcoal background, `#1A1E26` dock surface, `#252B36` tool panels) modeled after color grading suites to maintain neutral eye adaptation.
- **Resolve Accents**: Precision color rings (Cyan/Red, Magenta/Green, Yellow/Blue axes), golden amber playheads (`#F59E0B`), and crisp cyan indicators (`#06B6D4`).
- **Typography**: Clean, monospace numeric readouts (`sp` scaled) for RGB balance, kelvin temperature, tint, lift, gamma, gain, and timecode values.
- **Tactile Touch Wheels**: Custom vector-drawn touch controls with central reticle, coordinate readouts, reset double-tap, and fine-tune sensitivity.

---

## 3. Key Product Decisions & Trade-Offs

### Decision 1: High-Performance Color Pipeline
- **Chosen Approach**: Hardware-accelerated OpenGL ES / RenderScript color math engine utilizing multi-pass fragment shaders and LUT texture sampling for real-time 60fps playback, paired with Android MediaCodec and MediaMuxer for full MP4 video encoding.
- **Why**: Color grading 4K video requires sub-16ms latency during playback and scrubber movement. Software-only pixel processing is too slow for 4K.
- **Alternatives Considered**: Web-view based grading (far too slow for 4K video) or static frame-only processing (doesn't meet user's explicit request for full video export).

### Decision 2: Samsung Log Mathematical Transform
- **Chosen Approach**: Built-in Samsung Log EOTF/OETF logarithmic equations combined with bundled official Samsung 3D LUTs and custom matrix conversions for Rec.709 / BT.2020.
- **Why**: S24 Ultra Pro Video records in 10-bit HEVC with Samsung's proprietary logarithmic curve; mathematically unwrapping it recovers up to 13 stops of dynamic range without clipping highlights.

### Decision 3: Precision Scopes Engine
- **Chosen Approach**: Real-time GPU/CPU histogram and RGB waveform calculation that samples the graded video surface at 30fps and renders vectorscope color phase circles with target skin-tone line.
- **Why**: True colorists rely on scopes rather than uncalibrated phone displays to ensure legal broadcast limits and neutral skin tones.

---

## 4. Technical Architecture & Component Hierarchy

```
┌────────────────────────────────────────────────────────────────────────┐
│                          ResolveStudio UI                              │
├────────────────────────────────────────────────────────────────────────┤
│  Top Bar: Title, Project Name, Bypass Toggle, Scopes View, Export CTA  │
├────────────────────────────────────────────────────────────────────────┤
│  Preview & Scopes Monitor:                                             │
│  ┌───────────────────────────────────┬───────────────────────────────┐ │
│  │ Video Viewport / Scrubber /       │ Scopes Dock:                  │ │
│  │ A/B Split Comparison / Mask Guide │ Waveform / Vectorscope / Hist │ │
│  └───────────────────────────────────┴───────────────────────────────┘ │
├────────────────────────────────────────────────────────────────────────┤
│  Studio Dock Navigation:                                               │
│  [ Color Wheels ]  [ Curves ]  [ LUTs ]  [ Masking ]  [ FX & Film ]    │
├────────────────────────────────────────────────────────────────────────┤
│  Active Dock Panel:                                                    │
│  - Wheels: Lift (Shadows) | Gamma (Mid) | Gain (High) | Offset         │
│  - Curves: Spline Canvas (Master, R, G, B) & Hue vs Hue / Sat / Lum   │
│  - LUTs: Samsung Log to Rec709, Cine Looks, .cube File Importer        │
│  - Mask: Linear / Radial Power Window with Feather, Invert, Center    │
│  - FX: Film Grain, Halation, Glow, Vignette, White Balance / Tint      │
└────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                     Color Grading Pipeline Engine                      │
├────────────────────────────────────────────────────────────────────────┤
│  Input Video / Texture -> Color Space Transform (Samsung Log)          │
│    -> Primary Wheels (Lift/Gamma/Gain) -> Curves Interpolation         │
│    -> Secondary / Mask Composite -> 3D LUT -> Halation / Grain         │
│    -> Final Frame Surface                                              │
├────────────────────────────────────────────────────────────────────────┤
│  Exporters: MP4 Video Encoder | Still Frame JPEG/PNG | .cube 3D LUT    │
└────────────────────────────────────────────────────────────────────────┘
```

### Data Model & Grade State
- `ColorGradeState`: Lift (x, y, lum), Gamma (x, y, lum), Gain (x, y, lum), Offset (x, y, lum), Temperature, Tint, Saturation, Contrast, Highlight Roll-Off, Shadow Lift.
- `CurvePoints`: Knot arrays for Master, Red, Green, Blue splines (Catmull-Rom or cubic Bezier interpolation).
- `MaskState`: Type (None, Radial, Linear), Center (x, y), Size (width, height), Feather, Invert, Opacity.
- `EffectsState`: Film Grain (intensity, size), Halation (spread, threshold, red bias), Vignette (radius, softness).
- `LutProfile`: Built-in Samsung Log Rec.709, Samsung Cine, Cinematic Teal & Orange, Golden Hour, Black & White Noir, plus custom imported `.cube` files.

---

## 5. Implementation Steps

1. **Color Engine Core**: Implement `ColorScience` math module with Samsung Log decoding, Lift/Gamma/Gain matrix, RGB cubic spline curves, 3D LUT cube parser/generator, and halation/grain algorithms.
2. **DaVinci Resolve Studio UI**:
   - `PreviewSurface`: Real-time video player with scrubber, timecode, play/pause, and press-to-bypass comparison.
   - `ScopesDock`: Live Waveform RGB parade, Vectorscope with skin-tone indicator, and RGB Histogram.
   - `ColorWheelsPanel`: 4 interactive trackball wheels with color rings, fine-tuning knobs, and reset buttons.
   - `CurvesPanel`: Interactive touch canvas with draggable spline knots for Master, R, G, B channels and Hue vs Saturation.
   - `LutPanel`: Preset browser with Samsung S24 Ultra Log transforms, thumbnail previews, and `.cube` file import.
   - `MaskingPanel`: Power window controls (circle, oval, gradient bar) with visual canvas overlays.
   - `EffectsPanel`: Halation, film grain, exposure, and white balance sliders.
3. **Export Engine**:
   - MP4 Video Renderer with progress dialog and playback verification.
   - High-Res Frame Stills export to gallery.
   - Standard `.cube` 3D LUT exporter for desktop DaVinci Resolve compatibility.
4. **Sample Media & Presets**: Pre-populate high-dynamic-range test scenes showcasing Samsung Log before/after grading.
5. **Verification**: Run `compile_applet` to confirm clean compilation and rock-solid performance.
