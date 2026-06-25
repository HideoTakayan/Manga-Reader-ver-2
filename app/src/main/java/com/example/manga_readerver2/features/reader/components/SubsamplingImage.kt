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
    onTap: ((x: Float, y: Float, width: Float) -> Unit)? = null,
    onLongPress: (() -> Unit)? = null
) {
    val currentOnTap = rememberUpdatedState(onTap)
    val currentOnLongPress = rememberUpdatedState(onLongPress)
    val contentScale = when(scaleMode) {
        1 -> SubsamplingScaleImageView.SCALE_TYPE_START
        2 -> SubsamplingScaleImageView.SCALE_TYPE_CENTER_CROP
        else -> SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    var imageSource by remember { mutableStateOf<ImageSource?>(null) }
    LaunchedEffect(model) {
        imageSource = withContext(Dispatchers.IO) {
            try {
                when (model) {
                    is String -> if (File(model).exists()) ImageSource.uri(model) else null
                    is android.graphics.Bitmap -> ImageSource.bitmap(model)
                    is ByteArray -> {
                        val contentHash = "${java.util.Arrays.hashCode(model)}_${model.size}"
                        val tempFile = File(context.cacheDir, "reader_page_${contentHash}.tmp")
                        if (!tempFile.exists()) tempFile.writeBytes(model)
                        ImageSource.uri(tempFile.absolutePath)
                    }
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    val lastSource = remember { mutableStateOf<ImageSource?>(null) }
    
    if (imageSource != null || isWebtoon) {
        Box(modifier = modifier) {
            if (isWebtoon) {
                // Webtoon mode uses standard Coil AsyncImage for smooth continuous scrolling
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = { currentOnLongPress.value?.invoke() },
                                onTap = { offset ->
                                    currentOnTap.value?.invoke(offset.x, offset.y, size.width.toFloat())
                                }
                            )
                        },
                    contentScale = ContentScale.FillWidth
                )
            } else {
                // Single-page mode: SubsamplingScaleImageView with proper gesture handling
                AndroidView(
                    factory = { ctx ->
                        createSubsamplingView(ctx, contentScale, currentOnTap, currentOnLongPress)
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

/**
 * Tạo SubsamplingScaleImageView với gesture handling đúng:
 * - Single tap → toggle controls (invoke onTap)
 * - Double tap → zoom in/out
 * - Pinch → zoom
 * - Khi đang zoom (scale > minScale): consume touch event (return true) để LazyColumn không can thiệp
 * - Khi ở minScale: return false cho phép parent scroll (nhưng chế độ single-page không cần scroll)
 */
private class ComposeSubsamplingImageView(context: Context) : SubsamplingScaleImageView(context) {
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val result = super.onTouchEvent(event)
        val isZoomed = scale > minScale * 1.05f
        if (!isZoomed && event.action == MotionEvent.ACTION_MOVE) {
            // Undo any disallow intercept requests made by super.onTouchEvent so Compose Pager can intercept
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return result
    }
}

private fun createSubsamplingView(
    ctx: Context,
    contentScale: Int,
    currentOnTap: State<((Float, Float, Float) -> Unit)?>,
    currentOnLongPress: State<(() -> Unit)?>
): SubsamplingScaleImageView {
    return ComposeSubsamplingImageView(ctx).apply {
        setMinimumScaleType(contentScale)
        setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
        setMinimumDpi(80)
        setZoomEnabled(true)
        setPanEnabled(true)

        val tapDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                currentOnTap.value?.invoke(e.x, e.y, width.toFloat())
                return true
            }
            
            override fun onLongPress(e: MotionEvent) {
                currentOnLongPress.value?.invoke()
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val currentScale = scale
                val mScale = minScale
                if (currentScale > mScale * 1.1f) {
                    // Zoom về minScale
                    animateScaleAndCenter(mScale, center)?.withDuration(250)?.start()
                } else {
                    // Zoom vào 2x
                    val targetScale = minOf(maxScale, mScale * 2.5f)
                    animateScaleAndCenter(targetScale, PointF(e.x, e.y))?.withDuration(250)?.start()
                }
                return true
            }
        })

        var initialX = 0f
        var initialY = 0f
        var isZoomed = false

        setOnTouchListener { v, event ->
            tapDetector.onTouchEvent(event)

            val currentScale = scale
            val mScale = minScale
            isZoomed = currentScale > mScale * 1.05f

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = event.x
                    initialY = event.y
                    // Chỉ block parent nếu đang zoom
                    v.parent?.requestDisallowInterceptTouchEvent(isZoomed)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isZoomed) {
                        // Không zoom: nhường hết gesture cho Pager (trái/phải/dọc)
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                    } else {
                        // Đang zoom: kiểm tra xem có thể pan không
                        val dx = event.x - initialX
                        val dy = event.y - initialY
                        val canPanX = if (dx > 0) v.canScrollHorizontally(-1) else v.canScrollHorizontally(1)
                        val canPanY = if (dy > 0) v.canScrollVertically(-1) else v.canScrollVertically(1)
                        val isHorizontalDrag = kotlin.math.abs(dx) > kotlin.math.abs(dy)
                        val canPan = if (isHorizontalDrag) canPanX else canPanY
                        v.parent?.requestDisallowInterceptTouchEvent(canPan)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            
            // Trả về false để nhường quyền xử lý pan/zoom cho SubsamplingScaleImageView
            false
        }
    }
}
