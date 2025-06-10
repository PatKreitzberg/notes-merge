package com.wyldsoft.notes.touchhandling

import android.graphics.Rect
import android.view.SurfaceView
import com.wyldsoft.notes.pen.PenProfile

class TouchProcessor(
    private val createTouchHelper: (SurfaceView) -> BaseTouchHelper,
    private val updateTouchHelperWithProfile: () -> Unit,
    private val updateTouchHelperExclusionZones: (List<Rect>) -> Unit,
    private val updateActiveSurface: () -> Unit
) {
    private var currentTouchHelper: BaseTouchHelper? = null

    fun initializeTouchHelper(surfaceView: SurfaceView) {
        currentTouchHelper = createTouchHelper(surfaceView)
        
        surfaceView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateActiveSurface()
        }
    }

    fun updatePenProfile(penProfile: PenProfile) {
        updateTouchHelperWithProfile()
    }

    fun updateExclusionZones(excludeRects: List<Rect>) {
        updateTouchHelperExclusionZones(excludeRects)
    }

    fun getCurrentTouchHelper(): BaseTouchHelper? = currentTouchHelper
}