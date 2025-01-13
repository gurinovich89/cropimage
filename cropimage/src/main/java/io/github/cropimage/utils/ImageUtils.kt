/*
 * Copyright © by JSC Belgazprombank. All rights reserved.
 */

package io.github.cropimage.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapFactory.Options
import android.graphics.Canvas
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.compose.ui.geometry.Rect
import androidx.core.content.ContextCompat
import io.github.cropimage.OUTPUT_BITMAP_RESOLUTION
import io.github.cropimage.TAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


object ImageUtils {
    // on below line we are creating a function to get bitmap
    // from image and passing params as context and an int for drawable.
    fun getBitmapFromImage(context: Context, drawable: Int): Bitmap {

        // on below line we are getting drawable
        val db = ContextCompat.getDrawable(context, drawable)

        // in below line we are creating our bitmap and initializing it.
        val bit = Bitmap.createBitmap(
            db!!.intrinsicWidth, db.intrinsicHeight, Bitmap.Config.ARGB_8888
        )

        // on below line we are
        // creating a variable for canvas.
        val canvas = Canvas(bit)

        // on below line we are setting bounds for our bitmap.
        db.setBounds(0, 0, canvas.width, canvas.height)

        // on below line we are simply
        // calling draw to draw our canvas.
        db.draw(canvas)

        // on below line we are
        // returning our bitmap.
        return bit
    }

    fun cropAndLoadThumbnail(imagePath: String?, viewPortSize: Int): Bitmap? {
        var thumbnail: Bitmap? = null
        val opts = Options()
        opts.inJustDecodeBounds = true
        BitmapFactory.decodeFile(imagePath, opts)
        val width = opts.outWidth
        val height = opts.outHeight
        val lesserDimension = Math.min(width, height)
        val sampleSize = lesserDimension / viewPortSize
        opts.inJustDecodeBounds = false
        opts.inSampleSize = sampleSize
        try {
            thumbnail = BitmapFactory.decodeFile(imagePath, opts)
            /*
                        LogUtil.d(
                            TAG,
                            "thumbnail.w = " + thumbnail.width + ", thumbnail.h = "
                                    + thumbnail.height

                        )
            */
        } catch (e: Exception) {
//            LogUtil.e(TAG, "failed to load: " + e.message)
        }
        return thumbnail
    }

    fun getBitmapFromString(image: String?): Bitmap? {
        if (image == null) return null
        val index = image.indexOf(",")
        if (index < 0) return null
        val imageStr = image.substring(index + 1)
        val base64Image = Base64.decode(imageStr, Base64.NO_WRAP)
        return BitmapFactory.decodeByteArray(base64Image, 0, base64Image.size)
    }

    /**
     * Helper function used to convert an EXIF orientation enum into a transformation matrix
     * that can be applied to a bitmap.
     * @param orientation - One of the constants from [ExifInterface]
     */
    private fun decodeExifOrientation(orientation: Int): Matrix {
        val matrix = Matrix()

        // Apply transformation corresponding to declared EXIF orientation
        when (orientation) {
            ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_UNDEFINED -> Unit
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90F)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180F)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270F)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1F, 1F)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1F, -1F)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postScale(-1F, 1F)
                matrix.postRotate(270F)
            }

            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postScale(-1F, 1F)
                matrix.postRotate(90F)
            }

            // Error out if the EXIF orientation is invalid
            else -> throw IllegalArgumentException("Invalid orientation: $orientation")
        }

        // Return the resulting matrix
        return matrix
    }

    suspend fun loadBitmapFromUri(context: Context, imageUri: Uri): Bitmap? {
        return withContext(Dispatchers.IO) {
            val matrix = context.contentResolver.openInputStream(imageUri)?.use { input ->
                val exifInterface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    ExifInterface(input)
                } else {
                    null
                }
                val orientation = exifInterface?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
                    ?: ExifInterface.ORIENTATION_UNDEFINED
                return@use decodeExifOrientation(orientation)
            }
            return@withContext context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                val source = BitmapFactory.decodeStream(inputStream)
                val rotatedBitmap = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
                return@use rotatedBitmap
            }
        }
    }


    suspend fun cropBitmapImage(
        bitmap: Bitmap,
        cropArea: Rect,
        virtualImageCoordinates: Rect,
        outputBitmapResolution: Int
    ): Bitmap {
        val output = withContext(Dispatchers.IO) {
            val w = virtualImageCoordinates.width
            val h = virtualImageCoordinates.height
            val cropStart = cropArea.topLeft.minus(virtualImageCoordinates.topLeft)
            val startX = bitmap.width * cropStart.x / w
            val startY = bitmap.height * cropStart.y / h
            val cropWidth = bitmap.width * cropArea.width / w
            val cropHeight = bitmap.height * cropArea.height / h
            val croppedBitmap = Bitmap.createBitmap(bitmap, startX.toInt(), startY.toInt(), cropWidth.toInt(), cropHeight.toInt())
            Log.i(TAG, "croppedBitmap h=${croppedBitmap.height} w=${croppedBitmap.width}")
            getCroppedBitmap(croppedBitmap, outputBitmapResolution)
        }
        Log.i(TAG, "croppedBitmap output h=${output.height} w=${output.width}")
        return output
    }

    private fun getCroppedBitmap(bitmap: Bitmap, maxOutputDesiredPx: Int = OUTPUT_BITMAP_RESOLUTION): Bitmap {
        val originBitmap: Bitmap = bitmap.copy(Bitmap.Config.RGB_565, false)
        val sourceWidth = originBitmap.width
        val sourceHeight = originBitmap.height
        val aspectRatio = sourceWidth.toFloat() / sourceHeight
        val outputWidth: Int
        val outputHeight: Int
        if (sourceHeight > maxOutputDesiredPx || sourceWidth > maxOutputDesiredPx) {
            outputHeight = maxOutputDesiredPx
            outputWidth = (aspectRatio * maxOutputDesiredPx).toInt()
        } else {
            outputWidth = sourceWidth
            outputHeight = sourceHeight
        }
        if (sourceWidth > maxOutputDesiredPx || sourceHeight > maxOutputDesiredPx) {
            val outputScaledBitmap = Bitmap.createScaledBitmap(originBitmap, outputWidth, outputHeight, false)
            Log.i(
                TAG, "cropped image origin w=${originBitmap.width} h=${originBitmap.height} " +
                        "cropped w=${outputScaledBitmap.width} h=${outputScaledBitmap.height}"
            )
            return outputScaledBitmap
        } else {
            Log.i(
                TAG, "cropped image origin w=${originBitmap.width} h=${originBitmap.height} without scale"
            )
            return originBitmap
        }
    }

}