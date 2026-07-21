package com.example.manga_readerver2.features.reader.components

import android.content.Context
import android.graphics.PointF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
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
import java.io.File
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import kotlin.math.abs

@Composable
fun SubsamplingImage(
    model: Any?,
    modifier: Modifier = Modifier,
    chapterId: Long = 0L, // Định danh ID của chương hiện tại nhằm ngăn chặn xung đột (race condition) khi xử lý bộ nhớ đệm ảnh
    scaleMode: Int = 0, // 0: Fit Screen, 1: Fit Width, 2: Fit Height
    isWebtoon: Boolean = false,
    onTap: ((x: Float, y: Float, width: Float, height: Float) -> Unit)? = null,
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
                        val tempFile = File(context.cacheDir, "reader_page_${chapterId}_${contentHash}.tmp")
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
    
    if (imageSource != null) {
        Box(modifier = modifier) {
            // Use SubsamplingScaleImageView for ALL modes, including Webtoon
            AndroidView(
                factory = { ctx ->
                    createSubsamplingView(ctx, contentScale, currentOnTap, currentOnLongPress).apply {
                        if (isWebtoon) {
                            // In Webtoon mode, disable panning and zooming on the image itself.
                            // The parent LazyColumn and transformableState will handle scrolling and zooming.
                            setPanEnabled(false)
                            setZoomEnabled(false)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    if (isWebtoon) {
                        view.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CENTER_CROP)
                    } else {
                        view.setMinimumScaleType(contentScale)
                    }
                    if (lastSource.value != imageSource) {
                        view.setImage(imageSource!!)
                        lastSource.value = imageSource
                    }
                },
                onRelease = { view ->
                    // Giải phóng tài nguyên native bitmap khi View bị huỷ để tối ưu bộ nhớ
                    view.recycle()
                }
            )
        }
    } else {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = com.example.manga_readerver2.ui.theme.PrimaryOrange, modifier = Modifier.size(48.dp))
        }
    }
}

private class ComposeSubsamplingImageView(context: Context) : SubsamplingScaleImageView(context) {

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = super.onTouchEvent(event)
        
        val isZoomed = scale > minScale * 1.05f
        if (!isZoomed) {
            parent?.requestDisallowInterceptTouchEvent(false)
        } else if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            // Khi ảnh đã zoom và kéo đến rìa, ta cần cho phép Pager intercept thao tác
            // Nếu không thể scroll thêm theo hướng ngang hoặc dọc, trả lại touch event cho Compose
            if (!canScrollHorizontally(-1) || !canScrollHorizontally(1) || !canScrollVertically(-1) || !canScrollVertically(1)) {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        
        return handled
    }

    override fun canScrollHorizontally(direction: Int): Boolean {
        if (!isReady) return false
        val isZoomed = scale > minScale * 1.05f
        if (!isZoomed) return false
        
        val currentCenter = this.center ?: return false
        val sourceLeft = currentCenter.x - (width / 2f) / scale
        val sourceRight = currentCenter.x + (width / 2f) / scale
        val margin = 2f // Bù trừ sai số trong quá trình làm tròn hệ số thu phóng (pixel)
        
        return if (direction < 0) { // Cuộn sang trái (vuốt sang phải)
            sourceLeft > margin
        } else { // Cuộn sang phải (vuốt sang trái)
            sourceRight < sWidth.toFloat() - margin
        }
    }

    override fun canScrollVertically(direction: Int): Boolean {
        if (!isReady) return false
        val isZoomed = scale > minScale * 1.05f
        if (!isZoomed) return false
        
        val currentCenter = this.center ?: return false
        val sourceTop = currentCenter.y - (height / 2f) / scale
        val sourceBottom = currentCenter.y + (height / 2f) / scale
        val margin = 2f
        
        return if (direction < 0) { // Cuộn lên trên (vuốt xuống)
            sourceTop > margin
        } else { // Cuộn xuống dưới (vuốt lên)
            sourceBottom < sHeight.toFloat() - margin
        }
    }
}

private fun createSubsamplingView(
    ctx: Context,
    contentScale: Int,
    currentOnTap: State<((Float, Float, Float, Float) -> Unit)?>,
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
                currentOnTap.value?.invoke(e.x, e.y, width.toFloat(), height.toFloat())
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                currentOnLongPress.value?.invoke()
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isReady) {
                    val targetScale = if (scale > minScale * 1.1f) minScale else maxScale
                    animateScaleAndCenter(targetScale, PointF(e.x, e.y))?.withDuration(300)?.start()
                    return true
                }
                return false
            }
        })

        setOnTouchListener { _, event ->
            tapDetector.onTouchEvent(event)
            // Trả về false để nhường quyền xử lý cử chỉ (pan/zoom) cho lớp SubsamplingScaleImageView gốc
            false
        }
    }
}
