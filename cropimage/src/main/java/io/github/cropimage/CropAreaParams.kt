package io.github.cropimage

import androidx.compose.ui.geometry.Offset

data class OffsetScale(val offset: Offset, val scale: Float)

data class CropAreaParams(
    val imageStart: Offset,
    val imageEnd: Offset,
    val imageOffsetScale: OffsetScale,
    val cropAreaStart: Offset,
    val cropAreaEnd: Offset
)