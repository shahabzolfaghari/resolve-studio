package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.color.SamsungLogColorScience
import com.example.model.*
import com.example.video.SampleMediaGenerator
import com.example.video.SampleScene
import com.example.video.VideoExporter
import kotlinx.coroutines.*
import com.example.data.PresetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class GradingViewModel(application: Application) : AndroidViewModel(application) {

    private val presetRepository = PresetRepository(application)
    val savedPresets: StateFlow<List<GradePreset>> = presetRepository.presets

    private val _gradeSettings = MutableStateFlow(ColorGradeSettings())
    val gradeSettings = _gradeSettings.asStateFlow()

    private val _currentScene = MutableStateFlow(SampleScene.S24_LOG_SUNSET_PORTRAIT)
    val currentScene = _currentScene.asStateFlow()

    private val _rawFrameBitmap = MutableStateFlow<Bitmap?>(null)
    val rawFrameBitmap = _rawFrameBitmap.asStateFlow()

    private val _gradedFrameBitmap = MutableStateFlow<Bitmap?>(null)
    val gradedFrameBitmap = _gradedFrameBitmap.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentTimeMs = MutableStateFlow(0L)
    val currentTimeMs = _currentTimeMs.asStateFlow()

    private val _totalDurationMs = MutableStateFlow(12_000L) // 12s multi-clip sequence default
    val totalDurationMs = _totalDurationMs.asStateFlow()

    // Multi-Clip Timeline State (DaVinci Resolve Color Page Clip Strip)
    private val _timelineClips = MutableStateFlow<List<TimelineClip>>(
        listOf(
            TimelineClip(
                clipNumber = 1,
                title = "Sunset Portrait",
                subtitle = "Golden hour skin",
                sceneName = SampleScene.S24_LOG_SUNSET_PORTRAIT.name,
                durationMs = 3_000L
            ),
            TimelineClip(
                clipNumber = 2,
                title = "Tokyo Cyber Neon",
                subtitle = "Neon speculars",
                sceneName = SampleScene.S24_LOG_NEON_CITY.name,
                durationMs = 3_000L
            ),
            TimelineClip(
                clipNumber = 3,
                title = "Mountain Vista",
                subtitle = "Foliage & sky",
                sceneName = SampleScene.S24_LOG_ALPINE_LAKE.name,
                durationMs = 3_000L
            ),
            TimelineClip(
                clipNumber = 4,
                title = "Film Noir Studio",
                subtitle = "Tungsten key",
                sceneName = SampleScene.S24_LOG_CINEMA_INTERIOR.name,
                durationMs = 3_000L
            )
        )
    )
    val timelineClips = _timelineClips.asStateFlow()

    private val _activeClipIndex = MutableStateFlow(0)
    val activeClipIndex = _activeClipIndex.asStateFlow()

    private val _copiedClipGrade = MutableStateFlow<ColorGradeSettings?>(null)
    val copiedClipGrade = _copiedClipGrade.asStateFlow()

    // Export State
    private val _isExporting = MutableStateFlow(false)
    val isExporting = _isExporting.asStateFlow()

    private val _exportProgress = MutableStateFlow(0f)
    val exportProgress = _exportProgress.asStateFlow()

    private val _exportStatusMessage = MutableStateFlow("")
    val exportStatusMessage = _exportStatusMessage.asStateFlow()

    // Undo / Redo Stack State
    private val undoRedoManager = UndoRedoManager()
    private val _canUndo = MutableStateFlow(false)
    val canUndo = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo = _canRedo.asStateFlow()

    private val _historyEntries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val historyEntries = _historyEntries.asStateFlow()

    private val _historyActionToast = MutableStateFlow<String?>(null)
    val historyActionToast = _historyActionToast.asStateFlow()

    private val _isMaskTracking = MutableStateFlow(false)
    val isMaskTracking = _isMaskTracking.asStateFlow()

    private val _maskTrackingDirection = MutableStateFlow(1)
    val maskTrackingDirection = _maskTrackingDirection.asStateFlow()

    private val _histogramData = MutableStateFlow<com.example.color.HistogramData?>(null)
    val histogramData = _histogramData.asStateFlow()

    private val _waveformData = MutableStateFlow<com.example.color.WaveformData?>(null)
    val waveformData = _waveformData.asStateFlow()

    private var trackerJob: Job? = null
    private var previousTrackFrame: Bitmap? = null
    private var proxyBitmap: Bitmap? = null

    private var historyDebounceJob: Job? = null
    private var pendingActionDescription: String = "Color Grade Change"

    private var playbackJob: Job? = null
    private var gradeJob: Job? = null
    private var frameExtractJob: Job? = null
    private var importedVideoUri: Uri? = null
    private var cachedRetriever: MediaMetadataRetriever? = null
    private val retrieverLock = Any()

    init {
        loadScene(SampleScene.S24_LOG_SUNSET_PORTRAIT)
        syncUndoRedoState()
    }

    private fun syncActiveClipSettings(newSettings: ColorGradeSettings) {
        val clips = _timelineClips.value.toMutableList()
        val idx = _activeClipIndex.value
        if (idx in clips.indices) {
            clips[idx] = clips[idx].copy(settings = newSettings)
            _timelineClips.value = clips
        }
    }

    private fun recalculateTimelineDuration() {
        val sum = _timelineClips.value.sumOf { it.durationMs }.coerceAtLeast(1000L)
        _totalDurationMs.value = sum
    }

    fun selectTimelineClip(index: Int) {
        val clips = _timelineClips.value
        if (index !in clips.indices) return
        // Save current clip settings before switching
        syncActiveClipSettings(_gradeSettings.value)

        _activeClipIndex.value = index
        val targetClip = clips[index]
        _gradeSettings.value = targetClip.settings

        // Compute start time offset of this clip in the master timeline
        val startOffsetMs = clips.take(index).sumOf { it.durationMs }
        _currentTimeMs.value = startOffsetMs

        if (targetClip.importedUriString != null) {
            importedVideoUri = Uri.parse(targetClip.importedUriString)
            releaseCachedRetriever()
            updateFrameForTime(startOffsetMs)
        } else {
            val scene = try {
                SampleScene.valueOf(targetClip.sceneName)
            } catch (_: Exception) {
                SampleScene.S24_LOG_SUNSET_PORTRAIT
            }
            _currentScene.value = scene
            importedVideoUri = null
            releaseCachedRetriever()
            val raw = SampleMediaGenerator.generateLogFrame(scene, frameIndex = 0)
            _rawFrameBitmap.value = raw
            triggerGrade()
        }
    }

    fun copyActiveClipGrade() {
        val clipNum = _activeClipIndex.value + 1
        _copiedClipGrade.value = _gradeSettings.value
        _historyActionToast.value = String.format("Copied Grade from Clip %02d", clipNum)
    }

    fun pasteGradeToActiveClip() {
        val copied = _copiedClipGrade.value ?: return
        val clipNum = _activeClipIndex.value + 1
        updateSettings(
            description = String.format("Paste Grade to Clip %02d", clipNum),
            immediateCommit = true
        ) {
            copied
        }
        _historyActionToast.value = String.format("Pasted Grade to Clip %02d", clipNum)
    }

    fun syncGradeToAllClips() {
        val master = _gradeSettings.value
        val updated = _timelineClips.value.map { it.copy(settings = master) }
        _timelineClips.value = updated
        _historyActionToast.value = "Synced Grade across all ${updated.size} timeline clips"
    }

    fun addClipToTimeline(scene: SampleScene = SampleScene.S24_LOG_SUNSET_PORTRAIT) {
        syncActiveClipSettings(_gradeSettings.value)
        val currentClips = _timelineClips.value
        val newClip = TimelineClip(
            clipNumber = currentClips.size + 1,
            title = scene.sceneName.removePrefix("S24 Ultra Log: "),
            subtitle = scene.subtitle.take(22),
            sceneName = scene.name,
            durationMs = 3_000L,
            settings = ColorGradeSettings()
        )
        val updated = currentClips + newClip
        _timelineClips.value = updated
        recalculateTimelineDuration()
        selectTimelineClip(updated.size - 1)
        _historyActionToast.value = "Added Clip ${String.format("%02d", newClip.clipNumber)} to timeline"
    }

    fun removeClipAt(index: Int) {
        val currentClips = _timelineClips.value
        if (currentClips.size <= 1 || index !in currentClips.indices) return
        val updated = currentClips.toMutableList().apply { removeAt(index) }
            .mapIndexed { i, clip -> clip.copy(clipNumber = i + 1) }
        _timelineClips.value = updated
        recalculateTimelineDuration()
        val newActive = _activeClipIndex.value.coerceAtMost(updated.size - 1)
        selectTimelineClip(newActive)
        _historyActionToast.value = "Removed clip from timeline"
    }

    fun updateSettings(
        description: String = "Color Grade Change",
        immediateCommit: Boolean = false,
        transform: (ColorGradeSettings) -> ColorGradeSettings
    ) {
        val updated = transform(_gradeSettings.value)
        _gradeSettings.value = updated
        syncActiveClipSettings(updated)
        triggerGrade()

        if (immediateCommit) {
            historyDebounceJob?.cancel()
            commitSnapshot(description)
        } else {
            pendingActionDescription = description
            historyDebounceJob?.cancel()
            historyDebounceJob = viewModelScope.launch {
                delay(350L) // Quiet time debounce for continuous wheel / curve / mask gestures
                commitSnapshot(pendingActionDescription)
            }
        }
    }

    private fun commitSnapshot(description: String) {
        undoRedoManager.pushSnapshot(_gradeSettings.value, description)
        syncUndoRedoState()
    }

    private fun syncUndoRedoState() {
        _canUndo.value = undoRedoManager.canUndo
        _canRedo.value = undoRedoManager.canRedo
        _historyEntries.value = undoRedoManager.historyEntries
    }

    fun undo() {
        val entry = undoRedoManager.undo() ?: return
        _gradeSettings.value = entry.settings
        syncActiveClipSettings(entry.settings)
        triggerGrade()
        syncUndoRedoState()
        _historyActionToast.value = "Undo: ${entry.description}"
    }

    fun redo() {
        val entry = undoRedoManager.redo() ?: return
        _gradeSettings.value = entry.settings
        syncActiveClipSettings(entry.settings)
        triggerGrade()
        syncUndoRedoState()
        _historyActionToast.value = "Redo: ${entry.description}"
    }

    fun jumpToHistory(entry: HistoryEntry) {
        val settings = undoRedoManager.jumpTo(entry) ?: return
        _gradeSettings.value = settings
        syncActiveClipSettings(settings)
        triggerGrade()
        syncUndoRedoState()
        _historyActionToast.value = "Jumped to: ${entry.description}"
    }

    fun resetGrade() {
        val defaultSettings = ColorGradeSettings()
        _gradeSettings.value = defaultSettings
        syncActiveClipSettings(defaultSettings)
        undoRedoManager.reset(defaultSettings)
        triggerGrade()
        syncUndoRedoState()
        _historyActionToast.value = "Reset clip grade"
    }

    fun loadScene(scene: SampleScene) {
        val matchIdx = _timelineClips.value.indexOfFirst { it.importedUriString == null && it.sceneName == scene.name }
        if (matchIdx != -1) {
            _activeClipIndex.value = matchIdx
            val clip = _timelineClips.value[matchIdx]
            _gradeSettings.value = clip.settings
        }
        _currentScene.value = scene
        importedVideoUri = null
        releaseCachedRetriever()
        val raw = SampleMediaGenerator.generateLogFrame(scene, frameIndex = 0)
        _rawFrameBitmap.value = raw
        triggerGrade()
    }

    private fun releaseCachedRetriever() {
        synchronized(retrieverLock) {
            try {
                cachedRetriever?.release()
            } catch (_: Exception) {}
            cachedRetriever = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        releaseCachedRetriever()
    }

    fun onImportVideoUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                importedVideoUri = uri
                releaseCachedRetriever()
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(getApplication(), uri)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val durMs = (durationStr?.toLongOrNull() ?: 5000L).coerceIn(1000L, 60_000L)

                val newClip = TimelineClip(
                    clipNumber = _timelineClips.value.size + 1,
                    title = "Imported S24 Clip",
                    subtitle = "10-bit Log Video",
                    importedUriString = uri.toString(),
                    durationMs = durMs,
                    settings = _gradeSettings.value
                )
                val updated = _timelineClips.value + newClip
                _timelineClips.value = updated
                _activeClipIndex.value = updated.size - 1
                recalculateTimelineDuration()

                val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    _rawFrameBitmap.value = frame
                    triggerGrade()
                }
                synchronized(retrieverLock) {
                    cachedRetriever = retriever
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onImportCubeLut(cubeContent: String) {
        val parsed = SamsungLogColorScience.parseCubeLut(cubeContent)
        if (parsed != null) {
            val (size, data) = parsed
            updateSettings(description = "Import .cube LUT", immediateCommit = true) {
                it.copy(
                    activeLut = LutPreset.CUSTOM_CUBE,
                    customLutSize = size,
                    customLutData = data
                )
            }
        }
    }

    fun saveCurrentGradeAsLut(name: String, description: String) {
        val current = _gradeSettings.value
        val newCustomLut = CustomLut(
            name = name,
            description = description,
            lift = current.lift,
            gamma = current.gamma,
            gain = current.gain,
            offset = current.offset,
            contrast = current.contrast,
            saturation = current.saturation,
            temperature = current.temperature,
            tint = current.tint,
            exposure = current.exposure
        )
        val updatedList = current.customLuts + newCustomLut
        updateSettings(description = "Save LUT '$name'", immediateCommit = true) {
            it.copy(
                customLuts = updatedList,
                activeLut = LutPreset.CUSTOM_SAVED,
                selectedCustomLutId = newCustomLut.id
            )
        }
        _historyActionToast.value = "Saved LUT: $name"
    }

    fun deleteCustomLut(lutId: String) {
        val current = _gradeSettings.value
        val updatedList = current.customLuts.filterNot { it.id == lutId }
        val newActive = if (current.selectedCustomLutId == lutId) LutPreset.SAMSUNG_LOG_TO_REC709 else current.activeLut
        val newSelectedId = if (current.selectedCustomLutId == lutId) null else current.selectedCustomLutId
        updateSettings(description = "Delete Custom LUT", immediateCommit = true) {
            it.copy(
                customLuts = updatedList,
                activeLut = newActive,
                selectedCustomLutId = newSelectedId
            )
        }
    }

    // ==========================================
    // USER PRESET & LOOK MANAGEMENT
    // ==========================================

    fun applyPreset(preset: GradePreset) {
        updateSettings(
            description = "Apply Preset: ${preset.name}",
            immediateCommit = true
        ) {
            preset.settings
        }
        _historyActionToast.value = "Applied Look: ${preset.name}"
    }

    fun saveCurrentAsPreset(name: String, description: String, category: String) {
        viewModelScope.launch {
            val saved = presetRepository.savePreset(
                name = name.trim(),
                description = description.trim(),
                category = category.trim().ifBlank { "Custom" },
                settings = _gradeSettings.value
            )
            _historyActionToast.value = "Saved Preset: ${saved.name}"
        }
    }

    fun deletePreset(presetId: String) {
        viewModelScope.launch {
            presetRepository.deletePreset(presetId)
            _historyActionToast.value = "Preset deleted"
        }
    }

    fun clearActionToast() {
        _historyActionToast.value = null
    }

    fun setBypass(bypass: Boolean) {
        _gradeSettings.value = _gradeSettings.value.copy(bypass = bypass)
        triggerGrade()
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            _isPlaying.value = false
            playbackJob?.cancel()
        } else {
            _isPlaying.value = true
            startPlaybackLoop()
        }
    }

    fun seekTo(progressFraction: Float) {
        val targetMs = (progressFraction * _totalDurationMs.value).toLong()
        _currentTimeMs.value = targetMs
        updateFrameForTime(targetMs)
    }

    fun stepFrame(delta: Int) {
        val frameMs = 33L // ~30 fps
        val newTime = (_currentTimeMs.value + delta * frameMs).coerceIn(0L, _totalDurationMs.value)
        _currentTimeMs.value = newTime
        updateFrameForTime(newTime)
    }

    // =========================================================================
    // DAVINCI RESOLVE POWER WINDOW CLOUD MOTION TRACKER
    // =========================================================================

    fun stepTrackMask(direction: Int = 1) {
        val frameMs = 33L
        val newTime = (_currentTimeMs.value + direction * frameMs).coerceIn(0L, _totalDurationMs.value)
        _currentTimeMs.value = newTime

        // B-2: Ensure the frame is extracted before tracking (synchronous extraction for imported video)
        val raw = if (importedVideoUri != null) {
            val (_, localTime) = resolveClipAndTime(newTime)
            val extracted = synchronized(retrieverLock) {
                val r = cachedRetriever ?: MediaMetadataRetriever().also {
                    it.setDataSource(getApplication(), importedVideoUri)
                    cachedRetriever = it
                }
                r.getFrameAtTime(localTime * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
            }
            if (extracted != null) {
                _rawFrameBitmap.value = extracted
                extracted
            } else {
                _rawFrameBitmap.value
            }
        } else {
            updateFrameForTime(newTime)
            _rawFrameBitmap.value
        } ?: return

        val currentMask = _gradeSettings.value.mask
        if (currentMask.type == MaskType.NONE) return

        // B-1: Real Normalized Cross-Correlation (NCC) template matching
        val trackResult = SamsungLogColorScience.trackMaskPattern(
            previousBitmap = previousTrackFrame,
            currentBitmap = raw,
            centerX = currentMask.centerX,
            centerY = currentMask.centerY,
            radiusX = currentMask.radiusX,
            radiusY = currentMask.radiusY
        )
        previousTrackFrame = raw
        val trackedCx = trackResult.centerX
        val trackedCy = trackResult.centerY
        val realConfidence = trackResult.confidence

        val dx = trackedCx - currentMask.centerX
        val dy = trackedCy - currentMask.centerY
        val updatedBezier = if (currentMask.type == MaskType.CUSTOM_BEZIER) {
            currentMask.bezierPoints.map { pt ->
                pt.copy(
                    x = (pt.x + dx).coerceIn(0.02f, 0.98f),
                    y = (pt.y + dy).coerceIn(0.02f, 0.98f),
                    handleInX = (pt.handleInX + dx).coerceIn(0.02f, 0.98f),
                    handleInY = (pt.handleInY + dy).coerceIn(0.02f, 0.98f),
                    handleOutX = (pt.handleOutX + dx).coerceIn(0.02f, 0.98f),
                    handleOutY = (pt.handleOutY + dy).coerceIn(0.02f, 0.98f)
                )
            }
        } else {
            currentMask.bezierPoints
        }

        val updatedKeyframes = (currentMask.trackingKeyframes.filterNot { kotlin.math.abs(it.timeMs - newTime) < 16L } +
            MaskTrackerKeyframe(timeMs = newTime, centerX = trackedCx, centerY = trackedCy, confidence = realConfidence))
            .sortedBy { it.timeMs }
            .takeLast(90)

        val updatedMask = currentMask.copy(
            centerX = trackedCx,
            centerY = trackedCy,
            bezierPoints = updatedBezier,
            trackingEnabled = true,
            trackPointLocked = true,
            trackingKeyframes = updatedKeyframes
        )
        _gradeSettings.value = _gradeSettings.value.copy(mask = updatedMask)
        syncActiveClipSettings(_gradeSettings.value)
        triggerGrade()
    }

    fun toggleTrackMaskForward(direction: Int = 1) {
        if (_isMaskTracking.value) {
            _isMaskTracking.value = false
            trackerJob?.cancel()
            previousTrackFrame = null
            commitSnapshot("Track Power Window (${_gradeSettings.value.mask.trackingKeyframes.size} Keyframes)")
            _historyActionToast.value = "Tracker locked ${_gradeSettings.value.mask.trackingKeyframes.size} keyframes"
            return
        }

        _maskTrackingDirection.value = direction
        _isMaskTracking.value = true
        trackerJob?.cancel()
        trackerJob = viewModelScope.launch(Dispatchers.Default) {
            var steps = 0
            while (isActive && _isMaskTracking.value && steps < 36) {
                stepTrackMask(direction)
                steps++
                delay(42L)
            }
            _isMaskTracking.value = false
            previousTrackFrame = null
            commitSnapshot("Track Power Window (${_gradeSettings.value.mask.trackingKeyframes.size} Keyframes)")
            _historyActionToast.value = "Tracked ${_gradeSettings.value.mask.trackingKeyframes.size} frames"
        }
    }

    fun clearMaskTrack() {
        _isMaskTracking.value = false
        previousTrackFrame = null
        trackerJob?.cancel()
        updateSettings(description = "Clear Window Track", immediateCommit = true) {
            it.copy(
                mask = it.mask.copy(
                    trackingEnabled = false,
                    trackPointLocked = false,
                    trackingKeyframes = emptyList()
                )
            )
        }
        _historyActionToast.value = "Cleared Power Window track keyframes"
    }

    private fun startPlaybackLoop() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch(Dispatchers.Default) {
            val frameIntervalMs = 33L
            while (isActive && _isPlaying.value) {
                var newTime = _currentTimeMs.value + frameIntervalMs
                if (newTime >= _totalDurationMs.value) {
                    newTime = 0L // Loop
                }
                _currentTimeMs.value = newTime
                updateFrameForTime(newTime)
                delay(frameIntervalMs)
            }
        }
    }

    private fun resolveClipAndTime(timeMs: Long): Pair<Int, Long> {
        val clips = _timelineClips.value
        var accumulatedMs = 0L
        var resolvedIndex = _activeClipIndex.value.coerceIn(0, (clips.size - 1).coerceAtLeast(0))
        var localTimeMs = timeMs

        if (clips.isNotEmpty()) {
            for (i in clips.indices) {
                val clipDur = clips[i].durationMs.coerceAtLeast(500L)
                if (timeMs < accumulatedMs + clipDur || i == clips.size - 1) {
                    resolvedIndex = i
                    localTimeMs = (timeMs - accumulatedMs).coerceIn(0L, clipDur)
                    break
                }
                accumulatedMs += clipDur
            }
        }
        return Pair(resolvedIndex, localTimeMs)
    }

    private fun updateFrameForTime(timeMs: Long) {
        val (resolvedIndex, localTimeMs) = resolveClipAndTime(timeMs)
        val clips = _timelineClips.value

        if (clips.isNotEmpty() && resolvedIndex != _activeClipIndex.value) {
            syncActiveClipSettings(_gradeSettings.value)
            _activeClipIndex.value = resolvedIndex
            val nextClip = clips[resolvedIndex]
            _gradeSettings.value = nextClip.settings
            if (nextClip.importedUriString != null) {
                val nextUri = Uri.parse(nextClip.importedUriString)
                if (importedVideoUri != nextUri) {
                    importedVideoUri = nextUri
                    releaseCachedRetriever()
                }
            } else {
                importedVideoUri = null
                releaseCachedRetriever()
                _currentScene.value = try {
                    SampleScene.valueOf(nextClip.sceneName)
                } catch (_: Exception) {
                    SampleScene.S24_LOG_SUNSET_PORTRAIT
                }
            }
        }

        if (importedVideoUri != null) {
            frameExtractJob?.cancel()
            frameExtractJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    val frame = synchronized(retrieverLock) {
                        val activeRetriever = cachedRetriever ?: MediaMetadataRetriever().also {
                            it.setDataSource(getApplication(), importedVideoUri)
                            cachedRetriever = it
                        }
                        activeRetriever.getFrameAtTime(localTimeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
                    }
                    if (frame != null) {
                        _rawFrameBitmap.value = frame
                        // B-3: Apply tracked keyframes for imported video as well as sample scenes
                        applyTrackedKeyframeIfPresent(timeMs)
                        triggerGrade()
                    }
                } catch (_: Exception) {}
            }
        } else {
            // Generate animated frame for sample scene
            val frameIndex = (localTimeMs / 33L).toInt()
            val raw = SampleMediaGenerator.generateLogFrame(_currentScene.value, frameIndex = frameIndex)
            _rawFrameBitmap.value = raw
            applyTrackedKeyframeIfPresent(timeMs)
            triggerGrade()
        }
    }

    private fun applyTrackedKeyframeIfPresent(timeMs: Long) {
        if (_isMaskTracking.value) return
        val currentMask = _gradeSettings.value.mask
        val kfs = currentMask.trackingKeyframes
        if (kfs.isEmpty()) return

        val before = kfs.lastOrNull { it.timeMs <= timeMs }
        val after = kfs.firstOrNull { it.timeMs >= timeMs }
        val (interpX, interpY) = when {
            before != null && after != null && before.timeMs != after.timeMs -> {
                val t = ((timeMs - before.timeMs).toFloat() / (after.timeMs - before.timeMs).toFloat()).coerceIn(0f, 1f)
                Pair(
                    before.centerX + (after.centerX - before.centerX) * t,
                    before.centerY + (after.centerY - before.centerY) * t
                )
            }
            before != null -> Pair(before.centerX, before.centerY)
            after != null -> Pair(after.centerX, after.centerY)
            else -> return
        }

        if (kotlin.math.abs(interpX - currentMask.centerX) > 0.001f || kotlin.math.abs(interpY - currentMask.centerY) > 0.001f) {
            _gradeSettings.value = _gradeSettings.value.copy(
                mask = currentMask.copy(centerX = interpX, centerY = interpY)
            )
        }
    }

    private fun getOrCreateProxy(raw: Bitmap): Bitmap {
        val targetWidth = 1280
        if (raw.width <= targetWidth) return raw
        val targetHeight = (raw.height.toFloat() * targetWidth / raw.width).toInt()
        val cur = proxyBitmap
        if (cur != null && cur.width == targetWidth && cur.height == targetHeight) {
            val canvas = android.graphics.Canvas(cur)
            val rect = android.graphics.Rect(0, 0, raw.width, raw.height)
            val destRect = android.graphics.Rect(0, 0, targetWidth, targetHeight)
            val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(raw, rect, destRect, paint)
            return cur
        }
        val newProxy = Bitmap.createScaledBitmap(raw, targetWidth, targetHeight, true)
        proxyBitmap = newProxy
        return newProxy
    }

    private fun triggerGrade() {
        gradeJob?.cancel()
        gradeJob = viewModelScope.launch(Dispatchers.Default) {
            val raw = _rawFrameBitmap.value ?: return@launch
            // P-1: Use proxy bitmap for interactive preview when dimensions exceed 1280px
            val previewSource = if (raw.width > 1280) getOrCreateProxy(raw) else raw
            val graded = SamsungLogColorScience.gradeBitmap(
                source = previewSource,
                settings = _gradeSettings.value,
                isCancelled = { !isActive }
            )
            if (!isActive) return@launch
            _gradedFrameBitmap.value = graded

            // P-6: Compute broadcast scopes & histogram off UI thread on background coroutine
            val histo = SamsungLogColorScience.calculateHistogram(graded)
            val wave = SamsungLogColorScience.calculateWaveform(graded)
            if (isActive) {
                _histogramData.value = histo
                _waveformData.value = wave
            }
        }
    }

    fun exportVideo(
        config: com.example.video.VideoExportConfig = com.example.video.VideoExportConfig(),
        onCompleted: (File) -> Unit
    ) {
        viewModelScope.launch {
            _isExporting.value = true
            _exportProgress.value = 0f
            _exportStatusMessage.value = "Encoding ${config.aspectRatio.shortName} (${config.codec.shortName}) at ${config.quality.bitrateBps / 1_000_000} Mbps..."

            val base = _rawFrameBitmap.value ?: return@launch
            val file = VideoExporter.renderGradedVideo(
                context = getApplication(),
                baseFrame = base,
                settings = _gradeSettings.value,
                config = config,
                importedVideoUri = importedVideoUri,
                totalDurationMs = _totalDurationMs.value,
                onProgress = { p ->
                    _exportProgress.value = p
                    val totalFrames = config.durationSeconds * config.fps
                    val frameNum = (p * totalFrames).toInt()
                    _exportStatusMessage.value = "Rendering frame $frameNum of $totalFrames (${(p * 100).toInt()}%)..."
                }
            )

            _exportProgress.value = 1.0f
            _exportStatusMessage.value = if (config.saveToGallery) {
                "Saved to Gallery (Movies/ResolveStudio)!"
            } else {
                "Video export complete!"
            }
            delay(400)
            _isExporting.value = false
            onCompleted(file)
        }
    }

    fun exportStillFrame(onCompleted: (File) -> Unit) {
        viewModelScope.launch {
            val graded = _gradedFrameBitmap.value ?: return@launch
            val file = VideoExporter.exportStillFrame(getApplication(), graded)
            VideoExporter.saveStillToGallery(getApplication(), file)
            onCompleted(file)
        }
    }

    fun exportCubeLut(onCompleted: (File) -> Unit) {
        viewModelScope.launch {
            val file = VideoExporter.exportCubeLut(getApplication(), _gradeSettings.value)
            onCompleted(file)
        }
    }
}
