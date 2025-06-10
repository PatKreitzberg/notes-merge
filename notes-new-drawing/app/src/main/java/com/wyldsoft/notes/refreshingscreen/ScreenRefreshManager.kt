package com.wyldsoft.notes.refreshingscreen

import android.graphics.Bitmap
import android.util.Log
import android.view.SurfaceView

class ScreenRefreshManager(
    private val cleanSurfaceView: (SurfaceView) -> Boolean,
    private val renderToScreen: (SurfaceView, Bitmap?) -> Unit
) {

    fun forceScreenRefresh(surfaceView: SurfaceView?, bitmap: Bitmap?) {
        Log.d("ScreenRefreshManager", "forceScreenRefresh()")
        surfaceView?.let { sv ->
            cleanSurfaceView(sv)
            bitmap?.let { renderToScreen(sv, it) }
        }
    }

    fun refreshWithBitmap(surfaceView: SurfaceView, bitmap: Bitmap) {
        renderToScreen(surfaceView, bitmap)
    }
}