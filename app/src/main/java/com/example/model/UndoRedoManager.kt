package com.example.model

data class HistoryEntry(
    val settings: ColorGradeSettings,
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Professional Undo/Redo stack manager designed for non-destructive color grading.
 * Maintains history checkpoints across color wheels, curves, LUTs, and masking overlays.
 */
class UndoRedoManager(
    private val maxHistory: Int = 50
) {
    private val undoStack = mutableListOf<HistoryEntry>()
    private val redoStack = mutableListOf<HistoryEntry>()

    init {
        undoStack.add(HistoryEntry(ColorGradeSettings(), "Initial Grade"))
    }

    val canUndo: Boolean
        get() = undoStack.size > 1

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    val historyEntries: List<HistoryEntry>
        get() = undoStack.toList()

    val currentDescription: String
        get() = undoStack.lastOrNull()?.description ?: "Initial Grade"

    /**
     * Push a new state snapshot to the undo stack, clearing any redos.
     */
    fun pushSnapshot(settings: ColorGradeSettings, description: String) {
        if (undoStack.isNotEmpty() && undoStack.last().settings == settings) {
            return // Skip duplicate states
        }
        undoStack.add(HistoryEntry(settings, description))
        if (undoStack.size > maxHistory) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
    }

    /**
     * Undo the last change and return the previous settings state.
     */
    fun undo(): HistoryEntry? {
        if (!canUndo) return null
        val current = undoStack.removeAt(undoStack.lastIndex)
        redoStack.add(current)
        return undoStack.lastOrNull()
    }

    /**
     * Redo the undone change and return the restored settings state.
     */
    fun redo(): HistoryEntry? {
        if (!canRedo) return null
        val target = redoStack.removeAt(redoStack.lastIndex)
        undoStack.add(target)
        return target
    }

    /**
     * Jump directly to a specific historical checkpoint.
     */
    fun jumpTo(entry: HistoryEntry): ColorGradeSettings? {
        val index = undoStack.indexOf(entry)
        if (index == -1) return null

        while (undoStack.size > index + 1) {
            val popped = undoStack.removeAt(undoStack.lastIndex)
            redoStack.add(popped)
        }
        return undoStack.lastOrNull()?.settings
    }

    /**
     * Reset the history stack with a fresh initial baseline.
     */
    fun reset(initialSettings: ColorGradeSettings = ColorGradeSettings()) {
        undoStack.clear()
        redoStack.clear()
        undoStack.add(HistoryEntry(initialSettings, "Reset Grade"))
    }
}
