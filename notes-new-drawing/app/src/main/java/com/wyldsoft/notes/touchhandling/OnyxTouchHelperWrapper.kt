package com.wyldsoft.notes.touchhandling

import android.graphics.Rect
import android.util.Log
import com.onyx.android.sdk.pen.TouchHelper
import com.wyldsoft.notes.touchhandling.BaseTouchHelper

class OnyxTouchHelperWrapper(private val onyxTouchHelper: TouchHelper) : BaseTouchHelper() {

    override fun setRawDrawingEnabled(enabled: Boolean) {
        onyxTouchHelper.setRawDrawingEnabled(enabled)
    }

    override fun setRawDrawingRenderEnabled(enabled: Boolean) {
        onyxTouchHelper.setRawDrawingRenderEnabled(enabled)
    }

    override fun setStrokeWidth(width: Float) {
        onyxTouchHelper.setStrokeWidth(width)
    }

    override fun setStrokeStyle(style: Int) {
        onyxTouchHelper.setStrokeStyle(style)
    }

    override fun setLimitRect(limit: Rect, excludeRects: List<Rect>): BaseTouchHelper {
        Log.d("ExclusionRects", "Current exclusion rects ${excludeRects.size}")
        onyxTouchHelper.setLimitRect(limit, ArrayList(excludeRects))
        return this
    }

    override fun openRawDrawing(): BaseTouchHelper {
        onyxTouchHelper.openRawDrawing()
        return this
    }

    override fun closeRawDrawing() {
        onyxTouchHelper.closeRawDrawing()
    }

    override fun cleanup() {
        onyxTouchHelper.closeRawDrawing()
    }
}