package com.example.manga_readerver2.features.reader.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import java.io.File
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size

@Composable
fun SubsamplingImage(
    model: Any?,
    modifier: Modifier = Modifier,
    scaleMode: Int = 0, // 0: Fit Screen, 1: Fit Width, 2: Fit Height
    isVertical: Boolean = false,
    onTap: ((x: Float, y: Float, width: Float) -> Unit)? = null
) {
    val contentScale = when(scaleMode) {
        1 -> SubsamplingScaleImageView.SCALE_TYPE_START
        2 -> SubsamplingScaleImageView.SCALE_TYPE_CENTER_CROP
        else -> SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Quản lý file tạm để tránh rò rỉ bộ nhớ
    val imageSource by produceState<ImageSource?>(initialValue = null, model) {
        value = withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                when (model) {
                    is String -> {
                        if (File(model).exists()) ImageSource.uri(model) else null
                    }
                    is ByteArray -> {
                        // Fix BUG-04: Kết hợp contentHash + size để giảm collision.
                        // Arrays.hashCode() chỉ 32-bit, dễ collision với nhiều ảnh khác nhau.
                        val contentHash = "${java.util.Arrays.hashCode(model)}_${model.size}"
                        val tempFile = File(context.cacheDir, "reader_page_${contentHash}.tmp")
                        if (!tempFile.exists()) {
                            tempFile.writeBytes(model)
                        }
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
        AndroidView(
            factory = { ctx ->
                SubsamplingScaleImageView(ctx).apply {
                    setMinimumScaleType(contentScale)
                    setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
                    // Cấu hình mượt mà như Mihon
                    setDoubleTapZoomDuration(250)
                    setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER)
                    setMinimumDpi(80)
                    
                    setZoomEnabled(!isVertical)
                    setPanEnabled(!isVertical)
                    
                    var startX = 0f
                    var startY = 0f
                    var lastX = 0f
                    var lastY = 0f
                    
                    setOnTouchListener { view, event ->
                        if (isVertical) return@setOnTouchListener false
                        
                        val scaleImageView = view as SubsamplingScaleImageView
                        val isZoomed = scaleImageView.scale > scaleImageView.minScale
                        
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                startX = event.x
                                startY = event.y
                                lastX = event.x
                                lastY = event.y
                                // Parent might want to intercept later if we don't disallow now
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val dx = event.x - startX
                                val dy = event.y - startY
                                val absDx = Math.abs(dx)
                                val absDy = Math.abs(dy)
                                
                                // Nếu đang zoom hoặc chạm đa điểm, và đã di chuyển đủ xa
                                if ((isZoomed || event.pointerCount > 1) && (absDx > 10 || absDy > 10)) {
                                    val directionX = if (dx < 0) 1 else -1
                                    val directionY = if (dy < 0) 1 else -1
                                    
                                    val canPanX = scaleImageView.canScrollHorizontally(directionX)
                                    val canPanY = scaleImageView.canScrollVertically(directionY)
                                    
                                    // Chỉ chặn Pager/List cha nếu ảnh có thể pan theo hướng vuốt
                                    if (canPanX || canPanY || event.pointerCount > 1) {
                                        view.parent?.requestDisallowInterceptTouchEvent(true)
                                    }
                                }
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                lastX = event.x
                                lastY = event.y
                                view.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                        }
                        false 
                    }
                    setOnClickListener {
                        onTap?.invoke(lastX, lastY, width.toFloat())
                    }
                }
            },
            modifier = modifier.fillMaxSize(),
            update = { view ->
                view.setMinimumScaleType(contentScale)
                // Fix: Chỉ set image nếu source thực sự thay đổi để tránh reset zoom/pan khi UI recompose
                if (lastSource.value != imageSource) {
                    view.setImage(imageSource!!)
                    lastSource.value = imageSource
                }
            }
        )
    } else {
        // Fallback loading indicator
        androidx.compose.material3.CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = com.example.manga_readerver2.ui.theme.PrimaryOrange
        )
    }
}
