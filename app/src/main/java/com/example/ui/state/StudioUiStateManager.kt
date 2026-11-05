package com.example.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Screen size classifications for adaptive studio layouts.
 */
enum class ScreenSizeClass {
    COMPACT,    // Mobile phone portrait (< 600dp width)
    MEDIUM,     // Small tablet / phone landscape (600dp..840dp)
    EXPANDED    // Tablet landscape / desktop (>= 840dp)
}

/**
 * Presentation mode for the color grading Studio Dock.
 */
enum class DockPresentationMode {
    /** Dock is placed statically in layout (stacked or side-by-side) */
    DOCKED,

    /** Dock is collapsed into a floating action menu, giving video 100% full-width view */
    COLLAPSED_FLOATING,

    /** Quick grading modal sheet popped up from the floating action menu */
    FLOATING_SHEET
}

/**
 * Responsive UI State Manager for ResolveStudio.
 * Intelligently manages dock presentation between docked cards and floating action menus
 * based on screen geometry and user preferences.
 */
class StudioUiStateManager(
    initialSizeClass: ScreenSizeClass = ScreenSizeClass.COMPACT
) {
    var screenSizeClass by mutableStateOf(initialSizeClass)
        private set

    var dockMode by mutableStateOf(
        if (initialSizeClass == ScreenSizeClass.COMPACT) DockPresentationMode.COLLAPSED_FLOATING
        else DockPresentationMode.DOCKED
    )
        private set

    var isFloatingMenuExpanded by mutableStateOf(false)
        private set

    var isToolSheetOpen by mutableStateOf(false)
        private set

    private var userExplicitPreference: DockPresentationMode? = null

    /**
     * Updates screen dimensions and auto-adapts dock mode if user has not set an explicit override.
     */
    fun updateScreenDimensions(widthDp: Dp, heightDp: Dp) {
        val newClass = when {
            widthDp < 600.dp -> ScreenSizeClass.COMPACT
            widthDp < 840.dp -> ScreenSizeClass.MEDIUM
            else -> ScreenSizeClass.EXPANDED
        }

        if (newClass != screenSizeClass) {
            screenSizeClass = newClass
            if (userExplicitPreference == null) {
                dockMode = if (newClass == ScreenSizeClass.COMPACT) {
                    DockPresentationMode.COLLAPSED_FLOATING
                } else {
                    DockPresentationMode.DOCKED
                }
            }
        }
    }

    /**
     * Toggles between docked container mode and collapsed floating action menu.
     */
    fun toggleDockMode() {
        if (dockMode == DockPresentationMode.COLLAPSED_FLOATING) {
            userExplicitPreference = DockPresentationMode.DOCKED
            dockMode = DockPresentationMode.DOCKED
            isToolSheetOpen = false
            isFloatingMenuExpanded = false
        } else {
            userExplicitPreference = DockPresentationMode.COLLAPSED_FLOATING
            dockMode = DockPresentationMode.COLLAPSED_FLOATING
            isToolSheetOpen = false
            isFloatingMenuExpanded = false
        }
    }

    /**
     * Explicitly collapse dock into floating menu to maximize video preview area.
     */
    fun collapseToFloating() {
        userExplicitPreference = DockPresentationMode.COLLAPSED_FLOATING
        dockMode = DockPresentationMode.COLLAPSED_FLOATING
        isToolSheetOpen = false
        isFloatingMenuExpanded = false
    }

    /**
     * Explicitly expand dock into layout.
     */
    fun expandToDocked() {
        userExplicitPreference = DockPresentationMode.DOCKED
        dockMode = DockPresentationMode.DOCKED
        isToolSheetOpen = false
        isFloatingMenuExpanded = false
    }

    fun openToolSheet() {
        isToolSheetOpen = true
        isFloatingMenuExpanded = false
    }

    fun closeToolSheet() {
        isToolSheetOpen = false
    }

    fun toggleFloatingMenu() {
        isFloatingMenuExpanded = !isFloatingMenuExpanded
    }

    fun closeFloatingMenu() {
        isFloatingMenuExpanded = false
    }
}

@Composable
fun rememberStudioUiStateManager(
    initialSizeClass: ScreenSizeClass = ScreenSizeClass.COMPACT,
    startDockedByDefault: Boolean = true
): StudioUiStateManager {
    return remember {
        StudioUiStateManager(initialSizeClass).apply {
            if (startDockedByDefault) {
                expandToDocked()
            }
        }
    }
}
