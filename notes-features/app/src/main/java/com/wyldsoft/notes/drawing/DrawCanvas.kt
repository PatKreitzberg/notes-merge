package com.wyldsoft.notes.drawing

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.snapshotFlow
import android.util.Log
import com.wyldsoft.notes.rendering.CanvasRenderer
import com.wyldsoft.notes.strokeManagement.DrawingManager
import com.wyldsoft.notes.strokeManagement.DrawingManager.Companion as DrawingManagerCompanion
import com.wyldsoft.notes.drawing.TouchEventHandlerBridge
import com.wyldsoft.notes.utils.EditorState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
//import com.wyldsoft.notes.gesture.GestureEvent
//import com.wyldsoft.notes.gesture.GestureNotifier
//import com.wyldsoft.notes.gesture.GestureType
import com.wyldsoft.notes.settings.SettingsRepository
import com.wyldsoft.notes.templates.TemplateRenderer
import com.wyldsoft.notes.views.PageView
import com.wyldsoft.notes.selection.SelectionHandler
import com.wyldsoft.notes.history.HistoryManager
import com.wyldsoft.notes.search.SearchManager

/*
 * The main canvas component that coordinates all drawing operations.
 * Acts as a façade for the underlying drawing system components.
 */
class DrawCanvas(
    context: Context,
    val coroutineScope: CoroutineScope,
    val state: EditorState,
    val page: PageView,
    val settingsRepository: SettingsRepository,
    val templateRenderer: TemplateRenderer,
    val searchManager: SearchManager? = null
) : SurfaceView(context) {
    private val TAG="DrawCanvas:"
    private lateinit var selectionHandler: SelectionHandler
    private lateinit var canvasRenderer: CanvasRenderer
    private lateinit var touchEventHandler: TouchEventHandlerBridge
    //private var gestureNotifier = GestureNotifier()
    private var historyManager: HistoryManager? = null

    private val viewportTransformer get() = page.viewportTransformer

    fun init() {
        println("Initializing Canvas")

        // Initialize history manager
        historyManager = page.getHistoryManager()
        println("undo: historyManager $historyManager")

        // Update undo/redo state
        updateUndoRedoState()

        // Initialize selection handler
        selectionHandler = SelectionHandler(
            context,
            state,
            page,
            coroutineScope
        )

        // Initialize canvas renderer with selection handler
        canvasRenderer = CanvasRenderer(
            this,
            page,
            settingsRepository,
            templateRenderer,
            state,
            selectionHandler,
            searchManager
        )

        // Initialize touch event handler
        touchEventHandler = TouchEventHandlerBridge(
            context,
            this,
            coroutineScope,
            state,
            DrawingManager(page, historyManager),
            canvasRenderer,
            page.viewportTransformer
        )

        val surfaceCallback: SurfaceHolder.Callback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                println("Surface created $holder")
                touchEventHandler.updateActiveSurface()

                // Initialize the canvas renderer to draw initial content
                canvasRenderer.initialize()

                // Force a full update
                coroutineScope.launch {
                    DrawingManager.forceUpdate.emit(null)
                    // Also refresh the UI to ensure changes are visible
                    DrawingManager.refreshUi.emit(Unit)
                }
            }

            override fun surfaceChanged(
                holder: SurfaceHolder, format: Int, width: Int, height: Int
            ) {
                println("Surface changed $holder")
                drawCanvasToView()
                touchEventHandler.updatePenAndStroke()
                refreshUi()

                // Extra refresh to ensure content is visible after surface change
                coroutineScope.launch {
                    delay(100)
                    drawCanvasToView()
                }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                println("Surface destroyed ${this@DrawCanvas.hashCode()}")
                holder.removeCallback(this)
                touchEventHandler.closeRawDrawing()
            }
        }

        // Setup touch event interception
        touchEventHandler.setupTouchInterception()

        this.holder.addCallback(surfaceCallback)
    }

    private fun updateUndoRedoState() {
        historyManager?.let { manager ->

            coroutineScope.launch {
                manager.canUndo.collect { canUndo ->
                    state.canUndo = canUndo
                    println("undo: update state.canUndo ${state.canUndo}")
                }
            }

            coroutineScope.launch {
                manager.canRedo.collect { canRedo ->
                    state.canRedo = canRedo
                    println("undo: update state.canRedo ${state.canRedo}")
                }
            }
        }
    }

    fun registerObservers() {
        coroutineScope.launch {
            DrawingManager.isDrawing.collect {
                println("Drawing state changed!")
                state.isDrawing = it

                // Close stroke options panel when drawing starts
                if (it) {
                    // Emit event to close stroke options
                    DrawingManager.isStrokeOptionsOpen.emit(false)
                }
            }
        }

        // observe undoRedoPerformed
        coroutineScope.launch {
            DrawingManager.undoRedoPerformed.collect {
                println("Undo/Redo performed, refreshing UI")
                refreshUi()
            }
        }

        // observe forceUpdate
        coroutineScope.launch {
            DrawingManager.forceUpdate.collect { zoneAffected ->
                println("Force update zone $zoneAffected")

                if (zoneAffected != null) page.drawArea(
                    area = android.graphics.Rect(
                        zoneAffected.left,
                        zoneAffected.top,
                        zoneAffected.right,
                        zoneAffected.bottom
                    ),
                )
                refreshUiSuspend()
            }
        }

        // observe refreshUi
        coroutineScope.launch {
            DrawingManager.refreshUi.collect {
                println("Refreshing UI!")
                refreshUiSuspend()
            }
        }

        // observe restartcount
        coroutineScope.launch {
            DrawingManager.restartAfterConfChange.collect {
                println("Configuration changed!")
                init()
                drawCanvasToView()
            }
        }

        // observe pen and stroke size
        coroutineScope.launch {
            snapshotFlow { state.pen }.drop(1).collect {
                println("Pen change: ${state.pen}")
                touchEventHandler.updatePenAndStroke()
                refreshUiSuspend()
            }
        }

        coroutineScope.launch {
            snapshotFlow { state.penSettings.toMap() }.drop(1).collect {
                println("Pen settings change: ${state.penSettings}")
                touchEventHandler.updatePenAndStroke()
                refreshUiSuspend()
            }
        }

        coroutineScope.launch {
            snapshotFlow { state.eraser }.drop(1).collect {
                println("Eraser change: ${state.eraser}")
                touchEventHandler.updatePenAndStroke()
                refreshUiSuspend()
            }
        }

        // observe is drawing
        coroutineScope.launch {
            snapshotFlow { state.isDrawing }.drop(1).collect {
                println("isDrawing change: ${state.isDrawing}")
                updateIsDrawing()
            }
        }

        // observe toolbar open
        coroutineScope.launch {
            snapshotFlow { state.isToolbarOpen }.drop(1).collect {
                println("isToolbarOpen change: ${state.isToolbarOpen}")
                touchEventHandler.updateActiveSurface()
            }
        }

        // observe allowDrawingOnCanvas. change when settigns may open and close
        coroutineScope.launch {
            snapshotFlow { state.allowDrawingOnCanvas }.drop(1).collect {
                println("allowDrawingOnCanvas change: ${state.allowDrawingOnCanvas}")
                touchEventHandler.updateActiveSurface()
            }
        }

        // update active surface when exclude rect is added or removed
        coroutineScope.launch {
            snapshotFlow { state.stateExcludeRectsModified }.collect{ //drop(1).collect {
                println("stateExcludeRects change: ${state.stateExcludeRects}")
                if (state.stateExcludeRectsModified) {
                    touchEventHandler.updateActiveSurface()
                }
            }
        }


        // observe mode
        coroutineScope.launch {
            snapshotFlow { state.mode }.drop(1).collect {
                println("Mode change: ${state.mode}")
                touchEventHandler.updatePenAndStroke()
                refreshUiSuspend()
            }
        }
    }

    private fun refreshUi() {
        if (!state.isDrawing) {
            println("Not in drawing mode, skipping refreshUI")
            return
        }

        // Check if we're actively drawing before refreshing UI
        if (DrawingManager.drawingInProgress.isLocked) {
            Log.w(TAG, "drawing is in progress, isLocked true")
            println("Drawing is in progress, deferring UI refresh")
            return
        }

        drawCanvasToView()

        // Reset screen freeze
        touchEventHandler.setRawDrawingEnabled(false)
        touchEventHandler.setRawDrawingEnabled(true)
    }


    suspend fun refreshUiSuspend() {
        if (!state.isDrawing) {
            waitForDrawing()
            drawCanvasToView()
            println("Not in drawing mode -- refreshUi ")
            return
        }

        if (android.os.Looper.getMainLooper().isCurrentThread) {
            println("refreshUiSuspend() is called from the main thread, it might not be a good idea.")
        }

        waitForDrawing()
        drawCanvasToView()
        touchEventHandler.setRawDrawingEnabled(false)

        if (DrawingManager.drawingInProgress.isLocked)
            println("Lock was acquired during refreshing UI. It might cause errors.")

        touchEventHandler.setRawDrawingEnabled(true)
    }

    private suspend fun waitForDrawing() {
        withTimeoutOrNull(3000) {
            // Just to make sure wait 1ms before checking lock.
            delay(1)
            // Wait until drawingInProgress is unlocked before proceeding
            while (DrawingManager.drawingInProgress.isLocked) {
                delay(5)
            }
        } ?: println("Timeout while waiting for drawing lock. Potential deadlock.")
    }

    fun drawCanvasToView() {
        canvasRenderer.drawCanvasToView()
    }

    private suspend fun updateIsDrawing() {
        println("Update is drawing: ${state.isDrawing}")
        if (state.isDrawing) {
            touchEventHandler.setRawDrawingEnabled(true)
        } else {
            // Check if drawing is completed
            waitForDrawing()

            // Draw to view, before showing drawing, avoid stutter
            drawCanvasToView()
            touchEventHandler.setRawDrawingEnabled(false)
        }
    }
}