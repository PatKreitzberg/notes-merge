package com.wyldsoft.notes.bitmapmanagement

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.graphics.createBitmap

class BitmapManager {
    private var bitmap: Bitmap? = null
    private var bitmapCanvas: Canvas? = null

    fun getBitmap(): Bitmap? = bitmap

    fun getBitmapCanvas(): Canvas? = bitmapCanvas

    fun createBitmap(width: Int, height: Int): Bitmap? {
        if (bitmap == null && width > 0 && height > 0) {
            bitmap = createBitmap(width, height)
            bitmapCanvas = Canvas(bitmap!!)
            bitmapCanvas?.drawColor(Color.WHITE)
        }
        return bitmap
    }

    fun hasBitmap(): Boolean = bitmap != null

    fun recycleBitmap() {
        bitmap?.recycle()
        bitmap = null
        bitmapCanvas = null
    }

    fun clearBitmap() {
        bitmapCanvas?.drawColor(Color.WHITE)
    }
}