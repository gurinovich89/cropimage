package io.github.cropimage

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.cropimage.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min


const val OUTPUT_BITMAP_RESOLUTION = 420
const val MAX_ZOOM_SCALE = 4f
const val MIN_ZOOM_SCALE = 0.5f
const val TAG = "CropImageFragment"
const val REQUEST_KEY_CROP_IMAGE = "REQUEST_KEY_CROP_IMAGE"
const val KEY_OUTPUT_CROPPED_BITMAP = "KEY_OUTPUT_CROPPED_BITMAP"

fun interface ICropAreaChangeListener {
    fun onCropAreaChanged(
        bitmap: Bitmap,
        cropArea: Rect,
        virtualImageCoordinates: Rect,
        outputBitmapResolution: Int
    )
}

@Composable
fun CropImageView(
    bitmap: Bitmap,
    isUseDefaultButtons: Boolean,
    @DrawableRes btnCancelIconResId: Int = R.drawable.ic_close_32dp,
    @DrawableRes btnCropIconResId: Int = R.drawable.ic_verified_check_32dp,
    outputBitmapResolution: Int = OUTPUT_BITMAP_RESOLUTION,
    onCancelCrop: () -> Unit,
    onCroppedImage: (croppedBmp: Bitmap) -> Unit,
    onCropAreaChanged: ICropAreaChangeListener? = null
) {
    val screenSize = remember { mutableStateOf(IntSize.Zero) }
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {
                screenSize.value = it
            },
        color = colorResource(id = R.color.background_base_dark)
    ) {
        /*
        for images with wrong aspect ratio(for example 1000x50) we need enlarge max zoom
        because smallest side of image must be scaled according full width or height of display.
         */
        val coefficientMaxZoom = remember { mutableStateOf(1f) }

        val ovalStart = remember { mutableStateOf(Offset.Zero) }
        val ovalEnd = remember { mutableStateOf(Offset.Zero) }
        val ovalCenter = remember { mutableStateOf(Offset.Zero) }
        val imageStart = remember { mutableStateOf(Offset.Zero) }
        val imageEnd = remember { mutableStateOf(Offset.Zero) }

        // Define mutable state variables to keep track of the scale and offset.
        val scale = remember { mutableStateOf(1f) }
        val offset = remember { mutableStateOf(Offset(0f, 0f)) }
        val scaleAnimate: Float by animateFloatAsState(scale.value, label = "ScaleAnimation")
        val translationXAnimate: Float by animateFloatAsState(offset.value.x, label = "TranslationXAnimation")
        val translationYAnimate: Float by animateFloatAsState(offset.value.y, label = "TranslationYAnimation")


        val cropHorizontalPadding = dimensionResource(R.dimen.crop_area_horizontal_padding).value
        val cropBottomPadding = dimensionResource(R.dimen.crop_area_bottom_padding).value
        val minScreenSize = screenSize.value.let { min(it.width, it.height) }
        val circleRadius: Float = (minScreenSize - 2 * cropHorizontalPadding) / 2

        val centerX = screenSize.value.width / 2
        val centerY = (screenSize.value.height - cropBottomPadding) / 2
        ovalCenter.value = Offset(centerX.toFloat(), centerY)
        val radiusOffset = Offset(circleRadius, circleRadius)
        ovalStart.value = ovalCenter.value.minus(radiusOffset)
        ovalEnd.value = ovalCenter.value.plus(radiusOffset)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGesturesAndEnd(onGesture = { _, pan, zoom, _ ->
                        var newScale = scale.value
                        var newOffset = offset.value.copy()
                        newScale *= zoom
                        newScale = newScale.coerceIn(MIN_ZOOM_SCALE, MAX_ZOOM_SCALE * coefficientMaxZoom.value)
                        newOffset += pan

                        scale.value = newScale
                        offset.value = newOffset
                        Log.i(TAG, "newScale=${newScale} newOffset=${newOffset} offset=${offset.value} scale=$scale")
                    }, onGestureEnd = {
                        Log.i(TAG, "scale=${scale.value}; offsetImage=${offset.value}")
                        Log.i(TAG, "ovalStart=${ovalStart.value}; ovalEnd=${ovalEnd.value}")
                        Log.i(TAG, "imageStart=${imageStart.value}; imageEnd=${imageEnd.value}")
                        val params = CropAreaParams(
                            imageStart = imageStart.value,
                            imageEnd = imageEnd.value,
                            imageOffsetScale = OffsetScale(offset = offset.value, scale = scale.value),
                            cropAreaStart = ovalStart.value,
                            cropAreaEnd = ovalEnd.value
                        )
                        val calcOffsetScale = wrapCropAreaByImage(params)
                        offset.value = calcOffsetScale.offset
                        scale.value = calcOffsetScale.scale

                        onCropAreaChanged?.let {
                            val cropAreaParams = CropAreaParams(
                                imageStart = imageStart.value,
                                imageEnd = imageEnd.value,
                                imageOffsetScale = OffsetScale(offset = offset.value, scale = scale.value),
                                cropAreaStart = ovalStart.value,
                                cropAreaEnd = ovalEnd.value
                            )
                            val virtualImageCoordinates = getVirtualImageCoordinates(cropAreaParams)
                            val cropArea = Rect(cropAreaParams.cropAreaStart, cropAreaParams.cropAreaEnd)
                            it.onCropAreaChanged(bitmap, cropArea = cropArea, virtualImageCoordinates = virtualImageCoordinates, outputBitmapResolution = outputBitmapResolution)
                        }
                    })
                }
        ) {
            Canvas(modifier = Modifier
                .zIndex(1f)
                .fillMaxWidth(1f)
                .fillMaxHeight(1f),
                onDraw = {
                    val circlePath = Path().apply {
                        addOval(Rect(center = ovalCenter.value, radius = circleRadius))
                    }
                    clipPath(circlePath, clipOp = ClipOp.Difference) {
                        drawRect(SolidColor(Color.Black.copy(alpha = 0.5f)))
                    }
                })

            bitmap.let { bm ->
                Log.i(TAG, "bitmap size w=${bm.width} h=${bm.height}")
                val aspectRatio = bm.width.toFloat() / bm.height
                coefficientMaxZoom.value = if (aspectRatio > 1) aspectRatio else 1 / aspectRatio
                val isAlignImageByWidth = screenSize.value.let { it.width < it.height }
                val cropAreaPadding = dimensionResource(id = R.dimen.crop_area_horizontal_padding)
                Image(bitmap = bm.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .conditional(isAlignImageByWidth) {
                            padding(horizontal = cropAreaPadding)
                                .fillMaxWidth()
                                .wrapContentHeight(align = Alignment.CenterVertically)
                        }
                        .conditional(!isAlignImageByWidth) {
                            padding(vertical = cropAreaPadding)
                                .fillMaxHeight()
                                .wrapContentWidth(align = Alignment.CenterHorizontally)
                        }
                        .aspectRatio(ratio = aspectRatio)
                        .align(Alignment.Center)
                        .onGloballyPositioned { coordinates ->
                            imageStart.value = coordinates.positionInWindow()
                            val newX = imageStart.value.x + coordinates.size.width
                            val newY = imageStart.value.y + coordinates.size.height
                            imageEnd.value = Offset(newX, newY)
                            val params = CropAreaParams(
                                imageStart = imageStart.value,
                                imageEnd = imageEnd.value,
                                imageOffsetScale = OffsetScale(offset = offset.value, scale = scale.value),
                                cropAreaStart = ovalStart.value,
                                cropAreaEnd = ovalEnd.value
                            )
                            val calcOffsetScale = wrapCropAreaByImage(params)
                            offset.value = calcOffsetScale.offset
                            scale.value = calcOffsetScale.scale
                            Log.i(TAG, "onGloballyPositioned invoked params=$params")

                            onCropAreaChanged?.let {
                                val cropAreaParams = CropAreaParams(
                                    imageStart = imageStart.value,
                                    imageEnd = imageEnd.value,
                                    imageOffsetScale = OffsetScale(offset = offset.value, scale = scale.value),
                                    cropAreaStart = ovalStart.value,
                                    cropAreaEnd = ovalEnd.value
                                )
                                val virtualImageCoordinates = getVirtualImageCoordinates(cropAreaParams)
                                val cropArea = Rect(cropAreaParams.cropAreaStart, cropAreaParams.cropAreaEnd)
                                it.onCropAreaChanged(bitmap, cropArea = cropArea, virtualImageCoordinates = virtualImageCoordinates, outputBitmapResolution = outputBitmapResolution)
                            }

                        }
                        .graphicsLayer(
                            scaleX = scaleAnimate,
                            scaleY = scaleAnimate,
                            translationX = translationXAnimate,
                            translationY = translationYAnimate
                        ))
            }

            if (isUseDefaultButtons) {
                Row(
                    modifier = Modifier
                        .zIndex(1f)
                        .wrapContentHeight()
                        .padding(bottom = 60.dp)
                        .align(Alignment.BottomCenter)
                        .clip(shape = RoundedCornerShape(8.dp))
                        .wrapContentWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {

                    Image(
                        modifier = Modifier
                            .clip(shape = RoundedCornerShape(percent = 50))
                            .clickable(interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { })
                            .debouncedClickable {
                                onCancelCrop()
                            }
                            .padding(16.dp),
                        painter = painterResource(id = btnCancelIconResId),
                        contentDescription = "Close",
                        colorFilter = ColorFilter.tint(color = Color.White)
                    )

                    Spacer(modifier = Modifier.width(80.dp))

                    val coroutineScope = rememberCoroutineScope()
                    Image(
                        modifier = Modifier
                            .clip(shape = RoundedCornerShape(percent = 50))
                            .clickable(interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { })
                            .debouncedClickable {
                                val cropAreaParams = CropAreaParams(
                                    imageStart = imageStart.value,
                                    imageEnd = imageEnd.value,
                                    imageOffsetScale = OffsetScale(offset = offset.value, scale = scale.value),
                                    cropAreaStart = ovalStart.value,
                                    cropAreaEnd = ovalEnd.value
                                )
                                val virtualImageCoordinates = getVirtualImageCoordinates(cropAreaParams)
                                val cropArea = Rect(cropAreaParams.cropAreaStart, cropAreaParams.cropAreaEnd)
                                coroutineScope.launch {
                                    val croppedBitmap = withContext(Dispatchers.IO) {
                                        ImageUtils.cropBitmapImage(bitmap, cropArea = cropArea, virtualImageCoordinates, outputBitmapResolution)
                                    }
                                    onCroppedImage(croppedBitmap)
                                }
                            }
                            .padding(16.dp),
                        painter = painterResource(id = btnCropIconResId),
                        contentDescription = "Close",
                        colorFilter = ColorFilter.tint(color = Color.White)
                    )
                }
            }
        }
    }
}

private fun getVirtualImageCoordinates(params: CropAreaParams): Rect {
    val offset = params.imageOffsetScale.offset
    val imageWidth = params.imageEnd.x - params.imageStart.x
    val imageHeight = params.imageEnd.y - params.imageStart.y
    val aspectRatio = imageWidth / imageHeight
    val deltaScale = params.imageOffsetScale.scale - 1f
    val ovalDiameter = params.cropAreaEnd.x - params.cropAreaStart.x

    val newStartX = params.imageStart.x - imageWidth * deltaScale / 2 + offset.x
    val newStartY = params.imageStart.y - imageHeight * deltaScale / 2 + offset.y
    val virtualImageStart = Offset(newStartX, newStartY)

    val newEndX = params.imageEnd.x + imageWidth * deltaScale / 2 + offset.x
    val newEndY = params.imageEnd.y + imageHeight * deltaScale / 2 + offset.y
    val virtualImageEnd = Offset(newEndX, newEndY)

    return Rect(virtualImageStart, virtualImageEnd)
}

private fun wrapCropAreaByImage(params: CropAreaParams): OffsetScale {
    val imageStart = params.imageStart
    val imageEnd = params.imageEnd
    val ovalStart = params.cropAreaStart
    val ovalEnd = params.cropAreaEnd
    val scale = params.imageOffsetScale.scale
    val offset = params.imageOffsetScale.offset

    val imageWidth = params.imageEnd.x - params.imageStart.x
    val imageHeight = params.imageEnd.y - params.imageStart.y
    val ovalDiameter = params.cropAreaEnd.x - params.cropAreaStart.x

    val virtualImageCoordinates = getVirtualImageCoordinates(params)
    Log.i(TAG, "virtualImageCoordinates=${virtualImageCoordinates}  offset=${offset} scale=$scale")

    var x1 = virtualImageCoordinates.topLeft.x
    var y1 = virtualImageCoordinates.topLeft.y
    var x2 = virtualImageCoordinates.bottomRight.x
    var y2 = virtualImageCoordinates.bottomRight.y
    if (x1 <= ovalStart.x && y1 <= ovalStart.y && x2 >= ovalEnd.x && y2 >= ovalEnd.y) {
        return OffsetScale(offset, scale)
    }

    //setup image size according X-axis
    val deltaX = x2 - x1
    if (deltaX >= ovalDiameter) {
        //only shift by X-axis
        if (x2 < ovalEnd.x) {
            //shift to right along X-axis
            val shift = ovalEnd.x - x2
            x1 += shift
            x2 += shift
        } else if (x1 > ovalStart.x) {
            //shift to left along X-axis
            val shift = x1 - ovalStart.x
            x1 -= shift
            x2 -= shift
        }
    } else {
        //need to scale and shift by X-axis
        val scaleX = ovalDiameter / deltaX
        x1 = ovalStart.x
        x2 = ovalEnd.x
        val addY = (y2 - y1) * (scaleX - 1)
        y1 -= addY / 2
        y2 += addY / 2
    }

    //setup image size according Y-axis
    val deltaY = y2 - y1
    if (deltaY >= ovalDiameter) {
        //only shift by Y-axis
        if (y2 < ovalEnd.y) {
            //shift to bottom along Y-axis
            val shift = ovalEnd.y - y2
            y1 += shift
            y2 += shift
        } else if (y1 > ovalStart.y) {
            //shift to top along Y-axis
            val shift = y1 - ovalStart.y
            y1 -= shift
            y2 -= shift
        }
    } else {
        //need to scale and shift by Y-axis
        val scaleY = ovalDiameter / deltaY
        y1 = ovalStart.y
        y2 = ovalEnd.y
        val addX = (x2 - x1) * (scaleY - 1)
        x1 -= addX / 2
        x2 += addX / 2
    }

    val newScaleX = (x2 - x1) / imageWidth
    val newScaleY = (y2 - y1) / imageHeight
    Log.i(TAG, "scale=$scale; newScaleX =$newScaleX newScaleY=$newScaleY")
    val newDeltaScale = newScaleX - 1

    val translationX = x1 + newDeltaScale * imageWidth / 2 - imageStart.x
    val translationY = y1 + newDeltaScale * imageHeight / 2 - imageStart.y
    val translationX1 = x2 - newDeltaScale * imageWidth / 2 - imageEnd.x
    val translationY1 = y2 - newDeltaScale * imageHeight / 2 - imageEnd.y
    Log.i(
        TAG,
        "scale=$scale; newScaleX =$newScaleX translationX=$translationX translationY=$translationY translationX1=$translationX1 translationY1=$translationY1"
    )
    return OffsetScale(Offset(translationX, translationY), newScaleX)
}


@Composable
inline fun debounced(crossinline onClick: () -> Unit, debounceTime: Long = 1000L): () -> Unit {
    var lastTimeClicked by remember { mutableStateOf(0L) }
    val onClickLambda: () -> Unit = {
        val now = SystemClock.uptimeMillis()
        if (now - lastTimeClicked > debounceTime) {
            lastTimeClicked = now
            onClick()
        }
    }
    return onClickLambda
}


fun Modifier.debouncedClickable(
    debounceTime: Long = 1000L,
    onClick: () -> Unit
): Modifier {
    return this.composed {
        val clickable = debounced(debounceTime = debounceTime, onClick = { onClick() })
        this.clickable { clickable() }
    }
}