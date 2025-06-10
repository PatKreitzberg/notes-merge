package com.wyldsoft.notes.sdkintegration.onyx

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import android.view.SurfaceView
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.pen.data.TouchPointList
import com.onyx.android.sdk.rx.RxManager
import com.wyldsoft.notes.editor.EditorState
import com.wyldsoft.notes.sdkintegration.GlobalDeviceReceiver
import com.wyldsoft.notes.rendering.RendererToScreenRequest
import com.wyldsoft.notes.rendering.RendererHelper
import com.wyldsoft.notes.touchhandling.TouchUtils
import com.wyldsoft.notes.sdkintegration.BaseDeviceReceiver
import com.wyldsoft.notes.sdkintegration.BaseDrawingActivity
import com.wyldsoft.notes.touchhandling.BaseTouchHelper
import com.wyldsoft.notes.shapemanagement.ShapeFactory
import com.wyldsoft.notes.shapemanagement.shapes.Shape
import com.wyldsoft.notes.pen.PenType
import androidx.core.graphics.createBitmap
import com.wyldsoft.notes.touchhandling.OnyxTouchHelperWrapper
import com.wyldsoft.notes.shapemanagement.EraseManager
import com.wyldsoft.notes.refreshingscreen.PartialEraseRefresh
import com.wyldsoft.notes.sdkintegration.onyx.OnyxDeviceReceiverWrapper

open class OnyxDrawingActivity : BaseDrawingActivity() {
    private var rxManager: RxManager? = null
    private var onyxTouchHelper: TouchHelper? = null
    private var onyxDeviceReceiver: GlobalDeviceReceiver? = null

    // Store all drawn shapes for re-rendering
    private val drawnShapes = mutableListOf<Shape>()

    // Renderer helper for shape rendering
    private var rendererHelper: RendererHelper? = null
    
    // Erase management
    private val eraseManager = EraseManager()
    private val partialEraseRefresh = PartialEraseRefresh()

    override fun initializeSDK() {
        // Onyx-specific initialization
        // Initialize renderer helper
        rendererHelper = RendererHelper()
    }

    override fun createTouchHelper(surfaceView: SurfaceView): BaseTouchHelper {
        val callback = createOnyxCallback()
        onyxTouchHelper = TouchHelper.create(surfaceView, callback)
        return OnyxTouchHelperWrapper(onyxTouchHelper!!)
    }

    override fun createDeviceReceiver(): BaseDeviceReceiver {
        onyxDeviceReceiver = GlobalDeviceReceiver()
        return OnyxDeviceReceiverWrapper(onyxDeviceReceiver!!)
    }

    override fun enableFingerTouch() {
        TouchUtils.enableFingerTouch(applicationContext)
    }

    override fun disableFingerTouch() {
        TouchUtils.disableFingerTouch(applicationContext)
    }

    override fun cleanSurfaceView(surfaceView: SurfaceView): Boolean {
        val holder = surfaceView.holder ?: return false
        val canvas = holder.lockCanvas() ?: return false
        canvas.drawColor(Color.WHITE)
        holder.unlockCanvasAndPost(canvas)
        return true
    }

    override fun renderToScreen(surfaceView: SurfaceView, bitmap: Bitmap?) {
        if (bitmap != null) {
            getRxManager().enqueue(
                RendererToScreenRequest(
                    surfaceView,
                    bitmap
                ), null)
        }
    }

    override fun onResumeDrawing() {
        onyxTouchHelper?.setRawDrawingEnabled(true)
    }

    override fun onPauseDrawing() {
        onyxTouchHelper?.setRawDrawingEnabled(false)
    }

    override fun onCleanupSDK() {
        onyxTouchHelper?.closeRawDrawing()
        drawnShapes.clear()
    }

    override fun updateActiveSurface() {
        updateTouchHelperWithProfile()
    }

    override fun updateTouchHelperWithProfile() {
        onyxTouchHelper?.let { helper ->
            helper.setRawDrawingEnabled(false)
            helper.closeRawDrawing()

            val limit = Rect()
            surfaceView?.getLocalVisibleRect(limit)

            val excludeRects = EditorState.getCurrentExclusionRects()
            Log.d("ExclusionRects", "Current exclusion rects ${excludeRects.size}")
            helper.setStrokeWidth(currentPenProfile.strokeWidth)
                .setStrokeColor(currentPenProfile.getColorAsInt())
                .setLimitRect(limit, ArrayList(excludeRects))
                .openRawDrawing()

            helper.setStrokeStyle(currentPenProfile.getOnyxStrokeStyle())
            helper.setRawDrawingEnabled(true)
            helper.setRawDrawingRenderEnabled(true)
        }
    }

    override fun updateTouchHelperExclusionZones(excludeRects: List<Rect>) {
        onyxTouchHelper?.let { helper ->
            helper.setRawDrawingEnabled(false)
            helper.closeRawDrawing()

            val limit = Rect()
            surfaceView?.getLocalVisibleRect(limit)

            Log.d("ExclusionRects", "Current exclusion rects ${excludeRects.size}")
            helper.setStrokeWidth(currentPenProfile.strokeWidth)
                .setLimitRect(limit, ArrayList(excludeRects))
                .openRawDrawing()
            helper.setStrokeStyle(currentPenProfile.getOnyxStrokeStyle())

            helper.setRawDrawingEnabled(true)
            helper.setRawDrawingRenderEnabled(true)
        }
    }

    override fun initializeDeviceReceiver() {
        val deviceReceiver = createDeviceReceiver() as OnyxDeviceReceiverWrapper
        deviceReceiver.enable(this, true)
        deviceReceiver.setSystemNotificationPanelChangeListener { open ->
            onyxTouchHelper?.setRawDrawingEnabled(!open)
            surfaceView?.let { sv ->
                renderToScreen(sv, bitmap)
            }
        }.setSystemScreenOnListener {
            surfaceView?.let { sv ->
                renderToScreen(sv, bitmap)
            }
        }
    }

    override fun onCleanupDeviceReceiver() {
        onyxDeviceReceiver?.enable(this, false)
    }

    override fun forceScreenRefresh() {
        Log.d("Onyx", "forceScreenRefresh() called")
        surfaceView?.let { sv ->
            cleanSurfaceView(sv)
            // Recreate bitmap from all stored shapes
            recreateBitmapFromShapes()
            bitmap?.let { renderToScreen(sv, it) }
        }
    }

    private fun getRxManager(): RxManager {
        if (rxManager == null) {
            rxManager = RxManager.Builder.sharedSingleThreadManager()
        }
        return rxManager!!
    }

    private fun createOnyxCallback() = object : com.onyx.android.sdk.pen.RawInputCallback() {
        override fun onBeginRawDrawing(b: Boolean, touchPoint: TouchPoint?) {
            isDrawingInProgress = true
            disableFingerTouch()
            EditorState.notifyDrawingStarted()
        }

        override fun onEndRawDrawing(b: Boolean, touchPoint: TouchPoint?) {
            isDrawingInProgress = false
            enableFingerTouch()
            forceScreenRefresh()
            EditorState.notifyDrawingEnded()
        }

        override fun onRawDrawingTouchPointMoveReceived(touchPoint: TouchPoint?) {
            // Handle move events if needed
        }

        override fun onRawDrawingTouchPointListReceived(touchPointList: TouchPointList?) {
            touchPointList?.points?.let { points ->
                if (!isDrawingInProgress) {
                    isDrawingInProgress = true
                }
                drawScribbleToBitmap(points, touchPointList)
            }
        }

        override fun onBeginRawErasing(b: Boolean, touchPoint: TouchPoint?) {
            // Handle erasing start
        }

        override fun onEndRawErasing(b: Boolean, touchPoint: TouchPoint?) {
            // Handle erasing end
        }

        override fun onRawErasingTouchPointMoveReceived(touchPoint: TouchPoint?) {
            // Handle erase move
        }

        override fun onRawErasingTouchPointListReceived(touchPointList: TouchPointList?) {
            touchPointList?.let { erasePointList ->
                handleErasing(erasePointList)
            }

        }
    }

    private fun handleErasing(erasePointList: TouchPointList) {
        Log.d(TAG, "handleErasing called with ${erasePointList.size()} points")
        
        // Find shapes that intersect with the erase touch points
        val intersectingShapes = eraseManager.findIntersectingShapes(
            erasePointList, 
            drawnShapes
        )
        
        if (intersectingShapes.isNotEmpty()) {
            Log.d(TAG, "Found ${intersectingShapes.size} shapes to erase")
            
            // Calculate refresh area before removing shapes
            val refreshRect = eraseManager.calculateRefreshRect(intersectingShapes)
            
            // Remove intersecting shapes from our shape list
            drawnShapes.removeAll(intersectingShapes.toSet())
            
            // Perform partial refresh of the erased area
            refreshRect?.let { rect: RectF ->

                surfaceView?.let { sv ->
                    partialEraseRefresh.performPartialRefresh(
                        sv,
                        rect,
                        drawnShapes, // Pass remaining shapes
                        rendererHelper!!,
                        getRxManager()
                    )
                }
            }
            
            // Also update the main bitmap by recreating it from remaining shapes
            recreateBitmapFromShapes()
        }
    }

    private fun drawScribbleToBitmap(points: List<TouchPoint>, touchPointList: TouchPointList) {
        Log.d(TAG, "drawScribbleToBitmap called list size " + touchPointList.size())
        surfaceView?.let { sv ->
            createDrawingBitmap()

            // Create and store the shape based on current pen type
            val shape = createShapeFromPenType(touchPointList)
            drawnShapes.add(shape)

            // Render the new shape to the bitmap
            // fixme i dont think either of next to lines do anything necessary
            renderShapeToBitmap(shape)
            renderToScreen(sv, bitmap)
        }
    }

    private fun createShapeFromPenType(touchPointList: TouchPointList): Shape {
        // Map pen type to shape type
        val shapeType = when (currentPenProfile.penType) {
            PenType.BALLPEN, PenType.PENCIL -> ShapeFactory.SHAPE_PENCIL_SCRIBBLE
            PenType.FOUNTAIN -> ShapeFactory.SHAPE_BRUSH_SCRIBBLE
            PenType.MARKER -> ShapeFactory.SHAPE_MARKER_SCRIBBLE
            PenType.CHARCOAL, PenType.CHARCOAL_V2 -> ShapeFactory.SHAPE_CHARCOAL_SCRIBBLE
            PenType.NEO_BRUSH -> ShapeFactory.SHAPE_NEO_BRUSH_SCRIBBLE
            PenType.DASH -> ShapeFactory.SHAPE_PENCIL_SCRIBBLE // Default to pencil for dash
        }

        // Create the shape
        val shape = ShapeFactory.createShape(shapeType)
        shape.setTouchPointList(touchPointList)
            .setStrokeColor(currentPenProfile.getColorAsInt())
            .setStrokeWidth(currentPenProfile.strokeWidth)
            .setShapeType(shapeType)
            
        // Update bounding rect for hit testing
        shape.updateShapeRect()

        // Set texture for charcoal if needed
        if (currentPenProfile.penType == PenType.CHARCOAL_V2) {
            shape.setTexture(com.onyx.android.sdk.data.note.PenTexture.CHARCOAL_SHAPE_V2)
        } else if (currentPenProfile.penType == PenType.CHARCOAL) {
            shape.setTexture(com.onyx.android.sdk.data.note.PenTexture.CHARCOAL_SHAPE_V1)
        }

        return shape
    }

    private fun renderShapeToBitmap(shape: Shape) {
        bitmap?.let { bmp ->
            val renderContext = rendererHelper?.getRenderContext() ?: return
            renderContext.bitmap = bmp
            renderContext.canvas = Canvas(bmp)
            renderContext.paint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            // Initialize viewPoint for shapes that need it (like CharcoalScribbleShape)
            renderContext.viewPoint = android.graphics.Point(0, 0)

            shape.render(renderContext)
        }
    }

    private fun recreateBitmapFromShapes() {
        surfaceView?.let { sv ->
            // Create a fresh bitmap
            bitmap?.recycle()
            bitmap = createBitmap(sv.width, sv.height)
            bitmapCanvas = Canvas(bitmap!!)
            bitmapCanvas?.drawColor(Color.WHITE)

            // Get render context
            val renderContext = rendererHelper?.getRenderContext() ?: return
            renderContext.bitmap = bitmap
            renderContext.canvas = bitmapCanvas!!
            renderContext.paint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            // Initialize viewPoint for shapes that need it (like CharcoalScribbleShape)
            renderContext.viewPoint = android.graphics.Point(0, 0)

            // Render all shapes
            for (shape in drawnShapes) {
                shape.render(renderContext)
            }
        }
    }

    // Add method to clear all drawings
    fun clearDrawing() {
        drawnShapes.clear()
        surfaceView?.let { sv ->
            bitmap?.recycle()
            bitmap = null
            bitmapCanvas = null
            cleanSurfaceView(sv)
        }
    }
}