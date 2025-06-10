package com.wyldsoft.notes.bitmapmanagement

import android.view.SurfaceHolder
import android.view.SurfaceView

class CanvasLifecycleManager(
    private val bitmapManager: BitmapManager,
    private val onSurfaceCreated: (SurfaceView) -> Unit,
    private val onSurfaceChanged: (SurfaceView, Int, Int, Int) -> Unit,
    private val onSurfaceDestroyed: (SurfaceView) -> Unit
) {

    fun createSurfaceCallback(surfaceView: SurfaceView): SurfaceHolder.Callback {
        return object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                onSurfaceCreated(surfaceView)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                bitmapManager.createBitmap(width, height)
                onSurfaceChanged(surfaceView, format, width, height)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                onSurfaceDestroyed(surfaceView)
                holder.removeCallback(this)
            }
        }
    }
}