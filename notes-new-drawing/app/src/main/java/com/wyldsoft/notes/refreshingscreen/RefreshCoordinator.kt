package com.wyldsoft.notes.refreshingscreen

import android.graphics.Bitmap
import android.view.SurfaceHolder
import android.view.SurfaceView

class RefreshCoordinator(
    private val screenRefreshManager: ScreenRefreshManager
) {

    fun createRefreshingSurfaceCallback(
        surfaceView: SurfaceView,
        getBitmap: () -> Bitmap?,
        onSurfaceChanged: (Int, Int, Int) -> Unit
    ): SurfaceHolder.Callback {
        return object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                screenRefreshManager.forceScreenRefresh(surfaceView, getBitmap())
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                onSurfaceChanged(format, width, height)
                screenRefreshManager.forceScreenRefresh(surfaceView, getBitmap())
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                holder.removeCallback(this)
            }
        }
    }
}