package com.example.manga_readerver2.features.reader.components

import android.content.Context
import android.graphics.PointF
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import java.io.File
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

@Composable
fun SubsamplingImage(
    model: Any?,
    modifier: Modifier = Modifier,
    scaleMode: Int = 0, // 0: Fit Screen, 1: Fit Width, 2: Fit Height
    isWebtoon: Boolean = false,
    onTap: ((x: Float, y: Float, width: Float) -> Unit)? = null
) {
    val currentOnTap = rememberUpdatedState(onTap)
    val contentScale = when(scaleMode) {
        1 -> SubsamplingScaleImageView.SCALE_TYPE_START
        2 -> SubsamplingScaleImageView.SCALE_TYPE_CENTER_CROP
        else -> SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val imageSource by produceState<ImageSource?>(initialValue = null, model) {
        value = withContext(Dispatchers.IO) {
            try {
                when (model) {
                    is String -> if (File(model).exists()) ImageSource.uri(model) else null
                    is ByteArray -> {
                        val contentHash = "${java.util.Arrays.hashCode(model)}_${model.size}"
                        val tempFile = File(context.cacheDir, "reader_page_${contentHash}.tmp")
                        if (!tempFile.exists()) tempFile.writeBytes(model)
                        ImageSource.uri(tempFile.absolutePath)
                    }
                    else -> null
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Error loading image: ${e.message}" }
                null
            }
        }
    }

    val lastSource = remember { mutableStateOf<ImageSource?>(null) }
    
    if (imageSource != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
        ) {
            if (isWebtoon) {
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                currentOnTap.value?.invoke(offset.x, offset.y, size.width.toFloat())
                            }
                        },
                    contentScale = ContentScale.FillWidth
                )
            } else {
                AndroidView(
                    factory = { ctx ->
                        SubsamplingScaleImageView(ctx).apply {
                            setMinimumScaleType(contentScale)
                            setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
                            setQuickScaleEnabled(false)
                            setMinimumDpi(80)
                            setZoomEnabled(true)
                            setPanEnabled(true)
                            
                            val detector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                                    currentOnTap.value?.invoke(e.x, e.y, width.toFloat())
                                    return true
                                }
                                override fun onDoubleTap(e: MotionEvent): Boolean {
                                    if (scale > minScale) {
                                        animateScaleAndCenter(minScale, center)?.withDuration(250)?.start()
                                    } else {
                                        val targetScale = Math.min(maxScale, minScale * 2f)
                                        animateScaleAndCenter(targetScale, PointF(e.x, e.y))?.withDuration(250)?.start()
                                    }
                                    return true
                                }
                            })
                            
                            setOnTouchListener { _, event ->
                                detector.onTouchEvent(event)
                                false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        view.setMinimumScaleType(contentScale)
                        if (lastSource.value != imageSource) {
                            view.setImage(imageSource!!)
                            lastSource.value = imageSource
                        }
                    }
                )
            }
        }
    } else {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = com.example.manga_readerver2.ui.theme.PrimaryOrange, modifier = Modifier.size(48.dp))
        }
    }
}
