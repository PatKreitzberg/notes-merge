package com.wyldsoft.notes.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.wyldsoft.notes.DrawingCanvas
import com.wyldsoft.notes.ui.components.UpdatedToolbar
import com.wyldsoft.notes.pen.PenProfile

@Composable
fun EditorView(
    onSurfaceViewCreated: (android.view.SurfaceView) -> Unit = {},
    onPenProfileChanged: (PenProfile) -> Unit = {}
) {
    val editorState = remember { EditorState() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Updated toolbar with 5 profiles
        UpdatedToolbar(
            editorState = editorState,
            onPenProfileChanged = onPenProfileChanged
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Drawing canvas with real Onyx SDK integration
        DrawingCanvas(
            editorState = editorState,
            onSurfaceViewCreated = onSurfaceViewCreated
        )
    }
}