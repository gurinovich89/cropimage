package com.coolchoice.cropimage.sample

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.coolchoice.cropimage.CropImageButtonPanelListener
import com.coolchoice.cropimage.CropImageView
import com.coolchoice.cropimage.R
import com.coolchoice.cropimage.TAG
import com.coolchoice.cropimage.debouncedClickable
import com.coolchoice.cropimage.sample.ui.theme.CropImageSampleTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    val viewModel: CropImageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CropImageSampleTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }

                val bitmap = remember {
                    mutableStateOf<Bitmap?>(null)
                }

                val testCroppedBitmap = remember {
                    mutableStateOf<Bitmap?>(null)
                }

                LaunchedEffect(true) {
                    lifecycleScope.launch {
                        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                            launch {
                                viewModel.testCroppedImage.collectLatest { cropImage ->
                                    Log.i(TAG, "bitmap size w=${cropImage?.width} h=${cropImage?.height}")
                                    testCroppedBitmap.value = cropImage
                                }
                            }
                            viewModel.currentImage.collectLatest { curImage ->
                                Log.i(TAG, "bitmap size w=${curImage?.width} h=${curImage?.height}")
                                bitmap.value = curImage
                            }
                        }
                    }
                }

                bitmap.value?.let {
                    CropImageView(bitmap = it,
                        isUseDefaultButtons = true,
                        onCancelCrop = { onCancelCrop() },
                        onCroppedImage = { croppedBmp ->
                            Log.i(TAG, "onCroppedImage croppedBmp size = ${croppedBmp.width} x ${croppedBmp.height}")
                            viewModel.setCroppedImage(croppedBmp)
                        }
                    )
                }

                if (BuildConfig.DEBUG) {
                    testCroppedBitmap.value?.let { bm ->
                        Log.i(TAG, "testCroppedBitmap bitmap size w=${bm.width} h=${bm.height}")
                        Image(
                            bitmap = bm.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .width(80.dp)
                                .height(80.dp)
                        )
                    }
                }
            }
        }

        if (savedInstanceState == null) {
            viewModel.loadTestImage(this)
            viewModel.croppedImageEvent.observe(this) { event ->
                //TODO
                /*event?.getContentIfNotHandled()?.let { bitmap ->
                    setFragmentResult(REQUEST_KEY_CROP_IMAGE, bundleOf(KEY_OUTPUT_CROPPED_BITMAP to bitmap))
                    findNavController().popBackStack()
                }*/
            }
        }
    }

    private fun onCancelCrop() {
        //TODO
    }


}

@Composable
fun CustomCropButtonPanel(listener: CropImageButtonPanelListener) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 100.dp), contentAlignment = Alignment.BottomCenter
    ) {
        Image(
            modifier = Modifier
                .clip(shape = RoundedCornerShape(percent = 50))
                .clickable(interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { })
                .debouncedClickable {
                    listener.onCropClick()
                }
                .padding(16.dp),
            painter = painterResource(id = R.drawable.ic_verified_check_32dp),
            contentDescription = "Close",
            colorFilter = ColorFilter.tint(color = Color.Red)
        )
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    CropImageSampleTheme {
        Greeting("Android")
    }
}