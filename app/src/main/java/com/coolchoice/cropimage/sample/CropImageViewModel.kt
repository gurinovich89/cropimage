package com.coolchoice.cropimage.sample

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Log
import androidx.compose.ui.geometry.Rect
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coolchoice.cropimage.TAG
import com.coolchoice.cropimage.utils.ImageUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch


class CropImageViewModel constructor() : ViewModel() {

    private val _currentImage = MutableStateFlow<Bitmap?>(null)
    val currentImage: StateFlow<Bitmap?> = _currentImage

    private val _croppedImageEvent = MutableLiveData<Event<Bitmap>>(null)
    val croppedImageEvent: LiveData<Event<Bitmap>> = _croppedImageEvent

    private val _testCroppedImage = MutableStateFlow<Bitmap?>(null)
    val testCroppedImage: StateFlow<Bitmap?> = _testCroppedImage

    fun loadTestImage(context: Context) {
        viewModelScope.launch {
            val drawable = ContextCompat.getDrawable(context, R.drawable.test)
            (drawable as? BitmapDrawable)?.bitmap?.let { bmp ->
                _currentImage.value = bmp
            }
        }
    }

    fun setCroppedImage(croppedImage: Bitmap) {
        _croppedImageEvent.value = Event(croppedImage)
        if (BuildConfig.DEBUG) {
            _testCroppedImage.value = croppedImage
        }
    }

    fun loadImage(uriString: String, context: Context) {
        viewModelScope.launch {
            try {
                val imageUri = Uri.parse(uriString)
                _currentImage.value = ImageUtils.loadBitmapFromUri(context, imageUri)
            } catch (ex: Exception) {
                Log.e(TAG, "error in getBitmapFromUrl imageUri=${uriString}", ex)
            }
        }
    }

    fun cropBitmapImage(bitmap: Bitmap, cropArea: Rect, virtualImageCoordinates: Rect) {
        viewModelScope.launch {
            try {
                val output = ImageUtils.cropBitmapImage(bitmap, cropArea, virtualImageCoordinates)
                _croppedImageEvent.value = Event(output)
                if (BuildConfig.DEBUG) {
                    _testCroppedImage.value = output
                }
            } catch (ex: Exception) {
                Log.e(TAG, "error in cropBitmapImage cropArea=$cropArea virtualImageCoordinates=$virtualImageCoordinates", ex)
            }

        }
    }
}