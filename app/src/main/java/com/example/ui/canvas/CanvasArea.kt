package com.example.ui.canvas
import com.example.*
import com.example.model.*
import com.example.modifier.*
import com.example.viewmodel.*
import com.example.ui.theme.*
import com.example.model.AppliedEffect
import com.example.model.createDefaultEffect
import com.example.modifier.motionStudioEffects
import com.example.viewmodel.BASE_LAYER_IDS
import com.example.viewmodel.TIMELINE_ZOOM_RANGE
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.ui.effects.EffectBrowserScreen
import com.example.ui.effects.EffectsMenu
import com.example.ui.effects.EffectParameterSlider
import com.example.ui.effects.AppliedEffectCard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardTab
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.vector.ImageVector
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.Dp

val LocalDeletedLayers = compositionLocalOf<List<String>> { emptyList() }
@Composable
fun CanvasArea(viewModel: com.example.viewmodel.EditorViewModel, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val videoAwareImageLoader = androidx.compose.runtime.remember(context) {
        coil.ImageLoader.Builder(context)
            .components { add(coil.decode.VideoFrameDecoder.Factory()) }
            .build()
    }

        // Canvas Container
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .then(modifier)
                .background(Color(0xFF181A20))
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            val canvasDensity = LocalDensity.current
            val parentWidth = maxWidth
            val parentHeight = maxHeight
            val parentRatio = if (parentHeight.value > 0) parentWidth.value / parentHeight.value else 1f
            val ratioVal = when (viewModel.selectedAspectRatio) {
                "16:9" -> 16f / 9f
                "9:16" -> 9f / 16f
                "4:5" -> 4f / 5f
                "1:1" -> 1f
                "4:3" -> 4f / 3f
                else -> 4f / 5f
            }
            
            val childWidth: androidx.compose.ui.unit.Dp
            val childHeight: androidx.compose.ui.unit.Dp
            if (parentRatio > ratioVal) {
                // Parent is wider than the target ratio; height is the limiting factor
                childHeight = parentHeight * 0.98f
                childWidth = childHeight * ratioVal
            } else {
                // Parent is taller than the target ratio; width is the limiting factor
                childWidth = parentWidth * 0.98f
                childHeight = childWidth / ratioVal
            }

            val canvasBgModifier = when (viewModel.selectedBackground) {
                "Black" -> Modifier.background(Color.Black)
                "White" -> Modifier.background(Color.White)
                "Light Grey" -> Modifier.background(Color(0xFFE2E2E2))
                "Green" -> Modifier.background(Color(0xFF107C41))
                "Blue" -> Modifier.background(Color(0xFF1F4E79))
                "Transparent" -> Modifier.checkerboard(gridSize = 8.dp)
                else -> Modifier.background(Color(0xFFE2E2E2))
            }

            // Whiteish canvas
            Box(
                modifier = Modifier
                    .size(width = childWidth, height = childHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .then(canvasBgModifier)
                    .onSizeChanged { size ->
                        viewModel.previewWidthPx = size.width.toFloat()
                        viewModel.previewHeightPx = size.height.toFloat()
                    }
                    // Smart hit-testing: graphicsLayer { scaleX/scaleY } only affects
                    // visual rendering, NOT the clickable area.  When a layer is
                    // scaled up beyond its original layout size, taps on the visual
                    // overflow used to fall through to this handler and deselect the
                    // layer.  We now do proper hit-testing against each layer's
                    // visual bounds (accounting for scale, rotation & translation) so
                    // that tapping on a large layer selects it instead of deselecting.
                    .pointerInput(
                        viewModel.addedShapes.toList(),
                        viewModel.addedMedia.toList(),
                        viewModel.addedTexts.toList(),
                        viewModel.hiddenLayers.toSet(),
                        viewModel.layerTexts.toMap(),
                        viewModel.layerTransforms,
                        viewModel.layerKeyframes,
                        viewModel.playheadProgress,
                        viewModel.deletedLayers.toSet(),
                        viewModel.layerStartTimes,
                        viewModel.layerEndTimes,
                        viewModel.selectedLayer,
                        viewModel.previewWidthPx,
                        viewModel.previewHeightPx
                    ) {
                        detectTapGestures { tapOffset ->
                            val hitLayer = findHitLayer(
                                tapOffset = tapOffset,
                                canvasWidthPx = viewModel.previewWidthPx,
                                canvasHeightPx = viewModel.previewHeightPx,
                                addedShapes = viewModel.addedShapes,
                                addedMedia = viewModel.addedMedia,
                                addedTexts = viewModel.addedTexts,
                                layerTexts = viewModel.layerTexts,
                                hiddenLayers = viewModel.hiddenLayers.toSet(),
                                layerTransforms = viewModel.layerTransforms,
                                layerKeyframes = viewModel.layerKeyframes,
                                playheadProgress = viewModel.playheadProgress,
                                deletedLayers = viewModel.deletedLayers.toSet(),
                                layerStartTimes = viewModel.layerStartTimes,
                                layerEndTimes = viewModel.layerEndTimes,
                                density = canvasDensity
                            )
                            if (hitLayer != null) {
                                if (viewModel.selectedLayer != hitLayer) {
                                    viewModel.selectedLayer = hitLayer
                                }
                                // If tap is on the already-selected layer, keep it selected
                            } else {
                                viewModel.selectedLayer = null
                                viewModel.inMoveTransform = false
                                viewModel.inColorFill = false
                            }
                        }
                    }
            ) {
               // (Removed hardcoded demo layers: Triangle 1, image, Circle 1, Callout 1 preview rendering.
               // Real layers now render below via addedShapes/addedMedia.forEachIndexed.)
                viewModel.addedShapes.forEachIndexed { index, shapeIcon ->
                    val layerId = "Shape ${index + 1}"
                    val shapeStartTime = viewModel.layerStartTimes[layerId] ?: 0f
                    val shapeEndTime = viewModel.layerEndTimes[layerId] ?: Float.MAX_VALUE
                    if (!viewModel.deletedLayers.contains(layerId) && !viewModel.hiddenLayers.contains(layerId) && viewModel.playheadProgress >= shapeStartTime && viewModel.playheadProgress <= shapeEndTime) {
                        val shapeColor = viewModel.layerColors[layerId] ?: Color(0xFF16B996)
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .graphicsLayer {
                                    val transform = getActiveTransform(layerId, viewModel.playheadProgress, viewModel.layerTransforms, viewModel.layerKeyframes)
                                    val baseOffsetX = with(density) { (index * 20 - 40).dp.toPx() }
                                    val baseOffsetY = with(density) { (index * 20 - 40).dp.toPx() }
                                    translationX = baseOffsetX + transform.offsetX
                                    translationY = baseOffsetY + transform.offsetY
                                    rotationZ = transform.rotation
                                    scaleX = transform.scaleX
                                    scaleY = transform.scaleY
                                    clip = true
                                    shape = RoundedCornerShape(1.dp)
                                }
                                .motionStudioLayerBlend(layerId, viewModel.layerOpacities + (layerId to getActiveOpacity(layerId, viewModel.playheadProgress, viewModel.layerOpacities, viewModel.opacityKeyframes)), viewModel.layerBlendModes)
                                .motionStudioEffects(layerId, viewModel.layerEffects)
                                .layerTransformGestures(
                                    layerId = layerId,
                                    isSelected = viewModel.selectedLayer == layerId,
                                    onSelect = { viewModel.selectedLayer = layerId },
                                    transform = getActiveTransform(layerId, viewModel.playheadProgress, viewModel.layerTransforms, viewModel.layerKeyframes),
                                    onTransformChange = { updateLayerTransform(layerId, it, viewModel.playheadProgress, viewModel.layerTransforms, viewModel.layerKeyframes) },
                                    onGestureStart = { viewModel.pushUndo() }
                                )
                        ) {
                            if (shapeIcon != null) {
                                Icon(shapeIcon, contentDescription = null, modifier = Modifier.size(80.dp), tint = shapeColor)
                            } else {
                                if (layerId == "Shape 2") {
                                    Canvas(modifier = Modifier.size(120.dp)) {
                                        val w = size.width
                                        val h = size.height
                                        val polyPath = Path().apply {
                                            if (viewModel.vectorPoints.isNotEmpty()) {
                                                val first = viewModel.vectorPoints[0]
                                                moveTo(first.x * w, first.y * h)
                                                for (i in 1 until viewModel.vectorPoints.size) {
                                                    val current = viewModel.vectorPoints[i]
                                                    if (viewModel.pointModes.getOrElse(i) { false }) {
                                                        val prev = viewModel.vectorPoints[i - 1]
                                                        val ctrl1X = prev.x * w + (current.x * w - prev.x * w) * 0.5f
                                                        val ctrl1Y = prev.y * h
                                                        val ctrl2X = prev.x * w + (current.x * w - prev.x * w) * 0.5f
                                                        val ctrl2Y = current.y * h
                                                        cubicTo(ctrl1X, ctrl1Y, ctrl2X, ctrl2Y, current.x * w, current.y * h)
                                                    } else {
                                                        lineTo(current.x * w, current.y * h)
                                                    }
                                                }
                                                close()
                                            }
                                        }
                                        drawPath(polyPath, Color(0xFF4EF293))
                                        drawPath(polyPath, Color.Black, style = Stroke(width = 2.dp.toPx()))
                                    }
                                } else {
                                    Canvas(modifier = Modifier.size(80.dp)) {
                                        drawArc(color = shapeColor, startAngle = 45f, sweepAngle = 270f, useCenter = false, style = Stroke(width = 16f))
                                    }
                                }
                            }
                            if (viewModel.selectedLayer == layerId) {
                                Box(modifier = Modifier.matchParentSize()) { SelectionHandles() }
                            }
                        }
                    }
                }
                 viewModel.addedMedia.forEachIndexed { index, uri ->
                     val layerId = "Media ${index + 1}"
                     val mediaStartTime = viewModel.layerStartTimes[layerId] ?: 0f
                     val mediaEndTime = viewModel.layerEndTimes[layerId] ?: Float.MAX_VALUE
                     if (!viewModel.deletedLayers.contains(layerId) && !viewModel.hiddenLayers.contains(layerId) && viewModel.playheadProgress >= mediaStartTime && viewModel.playheadProgress <= mediaEndTime) {
                         Box(
                             modifier = Modifier
                                 .align(Alignment.Center)
                                 .graphicsLayer {
                                     val transform = getActiveTransform(layerId, viewModel.playheadProgress, viewModel.layerTransforms, viewModel.layerKeyframes)
                                     val baseOffsetX = with(density) { (index * 20 - 40).dp.toPx() }
                                     val baseOffsetY = with(density) { (index * 20 - 40).dp.toPx() }
                                     translationX = baseOffsetX + transform.offsetX
                                     translationY = baseOffsetY + transform.offsetY
                                     rotationZ = transform.rotation
                                     scaleX = transform.scaleX
                                     scaleY = transform.scaleY
                                 }
                                 .motionStudioLayerBlend(layerId, viewModel.layerOpacities + (layerId to getActiveOpacity(layerId, viewModel.playheadProgress, viewModel.layerOpacities, viewModel.opacityKeyframes)), viewModel.layerBlendModes)
                                 .motionStudioEffects(layerId, viewModel.layerEffects)
                                 .layerTransformGestures(
                                     layerId = layerId,
                                     isSelected = viewModel.selectedLayer == layerId,
                                     onSelect = { viewModel.selectedLayer = layerId },
                                     transform = getActiveTransform(layerId, viewModel.playheadProgress, viewModel.layerTransforms, viewModel.layerKeyframes),
                                     onTransformChange = { updateLayerTransform(layerId, it, viewModel.playheadProgress, viewModel.layerTransforms, viewModel.layerKeyframes) },
                                     onGestureStart = { viewModel.pushUndo() }
                                 )
                         ) {
                             val mimeType = remember(uri) { context.contentResolver.getType(uri) }
                             val isVideo = mimeType?.startsWith("video/") == true
                             if (isVideo) {
                                 VideoLayerPreview(
                                     uri = uri,
                                     playheadProgressSeconds = viewModel.playheadProgress,
                                     layerStartSeconds = mediaStartTime,
                                     isPlaying = viewModel.isPlaying,
                                     modifier = Modifier,
                                     onDurationKnownSeconds = { realDurationSeconds ->
                                         // Only auto-apply the real duration the first time it's
                                         // discovered for this layer. Otherwise, every time the
                                         // player fires this event (which can happen more than
                                         // once) it would clobber any manual trim the user made
                                         // afterward.
                                         if (!viewModel.autoResizedMediaLayers.contains(layerId)) {
                                             viewModel.autoResizedMediaLayers.add(layerId)
                                             val updatedEndTimes = viewModel.layerEndTimes.toMutableMap()
                                             updatedEndTimes[layerId] = mediaStartTime + realDurationSeconds
                                             viewModel.layerEndTimes = updatedEndTimes
                                         }
                                     }
                                 )
                             } else {
                                 AsyncImage(
                                     model = uri,
                                     contentDescription = null,
                                     imageLoader = videoAwareImageLoader,
                                     contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                     modifier = Modifier.size(160.dp)
                                 )
                             }
                             if (viewModel.selectedLayer == layerId) {
                                 Box(modifier = Modifier.matchParentSize()) { SelectionHandles() }
                             }
                         }
                     }
                 }
                viewModel.addedTexts.forEachIndexed { index, textId ->
                    val layerId = textId.ifBlank { "Text ${index + 1}" }
                    val textContent = viewModel.layerTexts[layerId] ?: "New Text"
                    val textStartTime = viewModel.layerStartTimes[layerId] ?: 0f
                    val textEndTime = viewModel.layerEndTimes[layerId] ?: Float.MAX_VALUE
                    if (!viewModel.deletedLayers.contains(layerId) && !viewModel.hiddenLayers.contains(layerId) && viewModel.playheadProgress >= textStartTime && viewModel.playheadProgress <= textEndTime) {
                        val textColor = viewModel.layerColors[layerId] ?: Color(0xFF16B996)
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .graphicsLayer {
                                    val transform = getActiveTransform(layerId, viewModel.playheadProgress, viewModel.layerTransforms, viewModel.layerKeyframes)
                                    val baseOffsetX = with(density) { (index * 20 - 40).dp.toPx() }
                                    val baseOffsetY = with(density) { (index * 20 - 40).dp.toPx() }
                                    translationX = baseOffsetX + transform.offsetX
                                    translationY = baseOffsetY + transform.offsetY
                                    rotationZ = transform.rotation
                                    scaleX = transform.scaleX
                                    scaleY = transform.scaleY
                                    clip = true
                                    shape = RoundedCornerShape(1.dp)
                                }
                                .motionStudioLayerBlend(layerId, viewModel.layerOpacities + (layerId to getActiveOpacity(layerId, viewModel.playheadProgress, viewModel.layerOpacities, viewModel.opacityKeyframes)), viewModel.layerBlendModes)
                                .motionStudioEffects(layerId, viewModel.layerEffects)
                                .layerTransformGestures(
                                    layerId = layerId,
                                    isSelected = viewModel.selectedLayer == layerId,
                                    onSelect = { viewModel.selectedLayer = layerId },
                                    transform = getActiveTransform(layerId, viewModel.playheadProgress, viewModel.layerTransforms, viewModel.layerKeyframes),
                                    onTransformChange = { updateLayerTransform(layerId, it, viewModel.playheadProgress, viewModel.layerTransforms, viewModel.layerKeyframes) },
                                    onGestureStart = { viewModel.pushUndo() }
                                )
                        ) {
                            var isEditing by androidx.compose.runtime.remember(layerId) { androidx.compose.runtime.mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 120.dp)
                                    .heightIn(min = 56.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isEditing && viewModel.selectedLayer == layerId) {
                                    androidx.compose.material3.TextField(
                                        value = textContent,
                                        onValueChange = { viewModel.layerTexts[layerId] = it },
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            color = textColor,
                                            fontSize = 24.sp,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                        ),
                                        colors = androidx.compose.material3.TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent
                                        ),
                                        modifier = Modifier
                                            .widthIn(min = 120.dp)
                                            .padding(8.dp)
                                    )
                                } else {
                                    Text(
                                        text = textContent,
                                        color = textColor,
                                        fontSize = 24.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        modifier = Modifier.padding(16.dp).pointerInput(layerId) {
                                            detectTapGestures(
                                                onDoubleTap = {
                                                    if (!isEditing) viewModel.pushUndo()
                                                    isEditing = true
                                                    viewModel.selectedLayer = layerId
                                                },
                                                onTap = {
                                                    isEditing = false
                                                    viewModel.selectedLayer = layerId
                                                }
                                            )
                                        }
                                    )
                                }
                                if (viewModel.selectedLayer == layerId && !isEditing) {
                                    SelectionHandles()
                                }
                            }
                        }
                    }
                }
            }
        }
        
}
