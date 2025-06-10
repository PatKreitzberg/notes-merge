package com.wyldsoft.notes.touchhandling

import android.graphics.Rect
import android.view.SurfaceView

abstract class BaseTouchHelper {
    abstract fun setRawDrawingEnabled(enabled: Boolean)
    abstract fun setRawDrawingRenderEnabled(enabled: Boolean)
    abstract fun setStrokeWidth(width: Float)
    abstract fun setStrokeStyle(style: Int)
    abstract fun setLimitRect(limit: Rect, excludeRects: List<Rect>): BaseTouchHelper
    abstract fun openRawDrawing(): BaseTouchHelper
    abstract fun closeRawDrawing()
    abstract fun cleanup()
}