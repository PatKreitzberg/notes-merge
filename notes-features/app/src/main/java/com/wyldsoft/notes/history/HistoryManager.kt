package com.wyldsoft.notes.history

import android.content.Context
import android.util.Log
import com.wyldsoft.notes.database.dao.HistoryActionDao
import com.wyldsoft.notes.database.entity.HistoryActionEntity
import com.wyldsoft.notes.utils.Pen
import com.wyldsoft.notes.utils.Stroke
import com.wyldsoft.notes.utils.StrokePoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.Date

/**
 * Manages the undo/redo history for a specific note.
 */
class HistoryManager(
    private val context: Context,
    private val noteId: String,
    private val coroutineScope: CoroutineScope,
    private val historyActionDao: HistoryActionDao,
    private val maxStoredActions: Int = 30
) {
    private val TAG = "HistoryManager"

    // In-memory history stacks (unlimited)
    private val undoStack = mutableListOf<HistoryAction>()
    private val redoStack = mutableListOf<HistoryAction>()

    // Current position in the history sequence
    private var currentSequence = 0

    // State flows for UI updates
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    init {
        // Load history from database
        coroutineScope.launch {
            loadHistoryFromDatabase()
        }
    }

    /**
     * Loads history from the database
     */
    private suspend fun loadHistoryFromDatabase() {
        println("history: loadHistoryFromDatabase")
        try {
            val actions = historyActionDao.getActionsForNote(noteId)
            println("history: actions are $actions")

            if (actions.isNotEmpty()) {
                println("history: Actions not empty")
                // Convert database entities to history actions
                val historyActions = actions.map { entity ->
                    deserializeAction(entity)
                }
                println("history: historyActions ${historyActions}")

                // Add to undo stack in order
                undoStack.clear()
                undoStack.addAll(historyActions)

                // Update current sequence
                currentSequence = actions.maxOfOrNull { it.sequenceNumber } ?: 0

                // Update state
                updateState()
                Log.d(TAG, "Loaded ${actions.size} actions from database for note $noteId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "history: Error loading history from database: ${e.message}", e)
        }
    }

    /**
     * Adds an action to the history
     */
    fun addAction(action: HistoryAction) {
        // Clear redo stack when a new action is performed
        redoStack.clear()

        // Increment sequence number
        currentSequence++
        action.sequenceNumber = currentSequence

        // Add to undo stack
        undoStack.add(action)

        // Save to database (limiting to maxStoredActions)
        coroutineScope.launch {
            println("history: try and save to database")
            try {
                // Save this action
                val entity = serializeAction(action)
                historyActionDao.insertAction(entity)
                println("history: inserted activity $entity")

                // Clear redoable actions from database
                historyActionDao.deleteActionsAboveSequence(noteId, currentSequence)

                // Prune database if needed
                if (undoStack.size > maxStoredActions) {
                    historyActionDao.pruneHistoryToLimit(noteId, maxStoredActions)
                }

                // Update state
                updateState()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving action to database: ${e.message}", e)
            }
        }

        updateState()
    }

    /**
     * Performs an undo operation
     */
    fun undo(): HistoryAction? {
        if (undoStack.isEmpty()) {
            return null
        }

        // Remove the last action from undo stack
        val action = undoStack.removeAt(undoStack.size - 1)

        // Add to redo stack
        redoStack.add(action)

        // Update state
        updateState()

        // Return the action to be undone
        return action
    }

    /**
     * Performs a redo operation
     */
    fun redo(): HistoryAction? {
        if (redoStack.isEmpty()) {
            return null
        }

        // Remove the last action from redo stack
        val action = redoStack.removeAt(redoStack.size - 1)

        // Add to undo stack
        undoStack.add(action)

        // Update state
        updateState()

        // Return the action to be redone
        return action
    }

    /**
     * Updates the state of the history manager
     */
    private fun updateState() {
        println("Update state undoStack.isNotEmpty() ${undoStack.isNotEmpty()}")
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    /**
     * Serializes an action to a database entity
     */
    private fun serializeAction(action: HistoryAction): HistoryActionEntity {
        val jsonConfig = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        val json = jsonConfig.encodeToString(action.data)

        return HistoryActionEntity(
            id = action.id,
            noteId = noteId,
            actionType = action.type.key,
            actionData = json,
            sequenceNumber = action.sequenceNumber,
            createdAt = action.timestamp
        )
    }

    /**
     * Deserializes a database entity to an action
     */
    private fun deserializeAction(entity: HistoryActionEntity): HistoryAction {
        return HistoryAction(
            id = entity.id,
            type = ActionType.fromString(entity.actionType),
            data = deserializeActionData(entity.actionType, entity.actionData),
            sequenceNumber = entity.sequenceNumber,
            timestamp = entity.createdAt
        )
    }

    /**
     * Deserializes action data based on action type
     */
    private fun deserializeActionData(actionType: String, actionData: String): HistoryActionData {
        // Create a JSON configuration that ignores unknown keys
        val jsonConfig = Json {
            ignoreUnknownKeys = true
            isLenient = true  // Add some extra tolerance for parsing
        }

        return when (ActionType.fromString(actionType)) {
            ActionType.ADD_STROKES -> {
                jsonConfig.decodeFromString<StrokeActionData>(actionData)
            }
            ActionType.DELETE_STROKES -> {
                jsonConfig.decodeFromString<StrokeActionData>(actionData)
            }
            ActionType.MOVE_STROKES -> {
                jsonConfig.decodeFromString<MoveActionData>(actionData)
            }
            ActionType.INSERT_PAGE -> {
                jsonConfig.decodeFromString<InsertPageActionData>(actionData)
            }
        }
    }

    /**
     * Clears all history
     */
    fun clearHistory() {
        undoStack.clear()
        redoStack.clear()
        updateState()

        coroutineScope.launch {
            historyActionDao.clearHistoryForNote(noteId)
        }
    }
}

/**
 * Base interface for all action data
 */
@kotlinx.serialization.Serializable
sealed interface HistoryActionData

/**
 * Data for stroke creation or deletion actions
 */
@kotlinx.serialization.Serializable
data class StrokeActionData(
    val strokeIds: List<String>,
    val strokes: List<SerializableStroke>
) : HistoryActionData

/**
 * Data for stroke movement actions
 */
@kotlinx.serialization.Serializable
data class MoveActionData(
    val strokeIds: List<String>,
    val originalStrokes: List<SerializableStroke>,
    val modifiedStrokes: List<SerializableStroke>,
    val offsetX: Float,
    val offsetY: Float
) : HistoryActionData

/**
 * Serializable version of a Stroke for history storage
 */
@kotlinx.serialization.Serializable
data class SerializableStroke(
    val id: String,
    val size: Float,
    val penName: String,
    val color: Int,
    val top: Float,
    val bottom: Float,
    val left: Float,
    val right: Float,
    val points: List<SerializableStrokePoint>,
    val pageId: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val createdScrollY: Float
) {
    companion object {
        fun fromStroke(stroke: Stroke): SerializableStroke {
            return SerializableStroke(
                id = stroke.id,
                size = stroke.size,
                penName = stroke.pen.penName,
                color = stroke.color,
                top = stroke.top,
                bottom = stroke.bottom,
                left = stroke.left,
                right = stroke.right,
                points = stroke.points.map { SerializableStrokePoint.fromStrokePoint(it) },
                pageId = stroke.pageId,
                createdAtMillis = stroke.createdAt.time,
                updatedAtMillis = stroke.updatedAt.time,
                createdScrollY = stroke.createdScrollY
            )
        }

        private val TAG = "history:"
        // Add this JSON configuration
        private val jsonConfig = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    fun toStroke(): Stroke {
        return Stroke(
            id = id,
            size = size,
            pen = Pen.fromString(penName),
            color = color,
            top = top,
            bottom = bottom,
            left = left,
            right = right,
            points = points.map { it.toStrokePoint() },
            pageId = pageId,
            createdAt = Date(createdAtMillis),
            updatedAt = Date(updatedAtMillis),
            createdScrollY = createdScrollY
        )
    }
}

/**
 * Serializable version of a StrokePoint for history storage
 */
@kotlinx.serialization.Serializable
data class SerializableStrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float,
    val size: Float,
    val tiltX: Int,
    val tiltY: Int,
    val timestamp: Long
) {
    companion object {
        fun fromStrokePoint(point: StrokePoint): SerializableStrokePoint {
            return SerializableStrokePoint(
                x = point.x,
                y = point.y,
                pressure = point.pressure,
                size = point.size,
                tiltX = point.tiltX,
                tiltY = point.tiltY,
                timestamp = point.timestamp
            )
        }
    }

    fun toStrokePoint(): StrokePoint {
        return StrokePoint(
            x = x,
            y = y,
            pressure = pressure,
            size = size,
            tiltX = tiltX,
            tiltY = tiltY,
            timestamp = timestamp
        )
    }
}

/**
 * Data for page insertion actions
 */
@kotlinx.serialization.Serializable
data class InsertPageActionData(
    val pageNumber: Int,
    val affectedStrokeIds: List<String>,
    val pageOffset: Float
) : HistoryActionData

/**
 * Represents a single action in the history
 */
data class HistoryAction(
    val id: String = UUID.randomUUID().toString(),
    val type: ActionType,
    val data: HistoryActionData,
    var sequenceNumber: Int = 0,
    val timestamp: Date = Date()
)