package com.example.ui.timeline
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
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
fun TimelineArea(viewModel: com.example.viewmodel.EditorViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(Color(0xFF1C1E26))
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoomChange, rotation ->
                        val hasActiveShapes = viewModel.addedShapes.indices.any { "Shape ${it + 1}" !in viewModel.deletedLayers }
                        val hasActiveMedia = viewModel.addedMedia.indices.any { "Media ${it + 1}" !in viewModel.deletedLayers }
                        val hasActiveTexts = viewModel.addedTexts.indices.any { "Text ${it + 1}" !in viewModel.deletedLayers }
                        val hasLayers = hasActiveShapes || hasActiveMedia || hasActiveTexts

                        if (hasLayers) {
                            if (zoomChange != 1f) {
                                viewModel.timelineZoom = (viewModel.timelineZoom * zoomChange)
                                    .coerceIn(TIMELINE_ZOOM_RANGE.start, TIMELINE_ZOOM_RANGE.endInclusive)
                            }
                            if (pan != Offset.Zero) {
                                viewModel.isPlaying = false
                                val pxPerSecond = TIMELINE_PIXELS_PER_SECOND * viewModel.timelineZoom
                                val deltaSeconds = pan.x / pxPerSecond.dp.toPx()
                                val newProgress = (viewModel.playheadProgress - deltaSeconds)
                                    .coerceIn(0f, viewModel.timelineDurationSeconds)
                                viewModel.playheadProgress = newProgress
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        val hasActiveShapes = viewModel.addedShapes.indices.any { "Shape ${it + 1}" !in viewModel.deletedLayers }
                        val hasActiveMedia = viewModel.addedMedia.indices.any { "Media ${it + 1}" !in viewModel.deletedLayers }
                        val hasActiveTexts = viewModel.addedTexts.indices.any { "Text ${it + 1}" !in viewModel.deletedLayers }
                        val hasLayers = hasActiveShapes || hasActiveMedia || hasActiveTexts
                        
                        if (hasLayers) {
                            viewModel.isPlaying = false
                            val pxPerSecond = TIMELINE_PIXELS_PER_SECOND * viewModel.timelineZoom
                            val playheadXPx = (size.width + 64.dp.toPx()) / 2f
                            val deltaSeconds = (tapOffset.x - playheadXPx) / pxPerSecond.dp.toPx()
                            val newProgress = (viewModel.playheadProgress + deltaSeconds)
                                .coerceIn(0f, viewModel.timelineDurationSeconds)
                            viewModel.playheadProgress = newProgress
                        }
                    }
                }
        ) {
            
            val screenWidth = LocalConfiguration.current.screenWidthDp.dp
            val playheadX = (screenWidth + 64.dp) / 2
            val pxPerSecond = TIMELINE_PIXELS_PER_SECOND * viewModel.timelineZoom
            val scrollOffset = playheadX - 64.dp - (viewModel.playheadProgress * pxPerSecond).dp
            val density = LocalDensity.current

            // While dragging a trim handle, keep it reachable: the timeline only
            // scrolls in response to the playhead (see `scrollOffset` above), and
            // trimming alone never moved the playhead, so a handle dragged more
            // than a couple of seconds' worth of pixels from center would walk
            // straight off the physical screen with no way to bring it back into
            // view. Once the handle gets within a small margin of either screen
            // edge, nudge the playhead forward/back so the whole timeline scrolls
            // and the handle stays reachable — the same auto-scroll behavior
            // used while dragging near the edge of any scrollable list.
            val edgeMarginPx = with(density) { 24.dp.toPx() }
            val screenWidthPx = with(density) { screenWidth.toPx() }
            val pxPerSecondPx = with(density) { pxPerSecond.dp.toPx() }
            val onTrimHandleMoved: (Float) -> Unit = { xPx ->
                val overflowRight = xPx - (screenWidthPx - edgeMarginPx)
                val overflowLeft = edgeMarginPx - xPx
                val overflowSeconds = when {
                    overflowRight > 0f -> overflowRight / pxPerSecondPx
                    overflowLeft > 0f -> -overflowLeft / pxPerSecondPx
                    else -> 0f
                }
                if (overflowSeconds != 0f) {
                    viewModel.playheadProgress = (viewModel.playheadProgress + overflowSeconds)
                        .coerceIn(0f, viewModel.timelineDurationSeconds)
                }
            }

            // Snapping: collects every "interesting" timeline instant (0, every
            // other layer's start/end, and the playhead) so drag operations can
            // snap to them, the same as clip edges/playhead snapping in a real
            // NLE. Only OTHER layers' times are included for a given layer's own
            // drag so a clip never snaps to itself.
            fun snapPointsExcluding(excludeLayerId: String?): List<Float> {
                val points = mutableListOf(0f)
                viewModel.layerStartTimes.forEach { (id, t) ->
                    if (id != excludeLayerId && id !in viewModel.deletedLayers) points.add(t)
                }
                viewModel.layerEndTimes.forEach { (id, t) ->
                    if (id != excludeLayerId && id !in viewModel.deletedLayers && t.isFinite() && t != Float.MAX_VALUE) points.add(t)
                }
                points.add(viewModel.playheadProgress)
                return points
            }
            // Snap catch radius in seconds — converts a fixed pixel radius so
            // snapping feels equally "sticky" regardless of zoom level.
            val snapRadiusSeconds = with(density) { 10.dp.toPx() } / pxPerSecondPx
            fun applySnap(value: Float, excludeLayerId: String?): Float {
                val candidates = snapPointsExcluding(excludeLayerId)
                val nearest = candidates.minByOrNull { kotlin.math.abs(it - value) }
                return if (nearest != null && kotlin.math.abs(nearest - value) <= snapRadiusSeconds) nearest else value
            }

            // Content
            if (!viewModel.showAddMenu) {
                if (viewModel.selectedLayer == null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Ruler
                        Box(modifier = Modifier.height(32.dp).fillMaxWidth()) // Ruler space
                        
                        // (Removed hardcoded demo track rows: Triangle 1, image, Callout 1, YTDown video, Circle 1.
                        // Real tracks now render below via addedShapes/addedMedia.forEachIndexed.)
                        viewModel.addedShapes.forEachIndexed { index, shapeIcon ->
                            val layerId = "Shape ${index + 1}"
                            val shapeColor = viewModel.layerColors[layerId] ?: Color.LightGray
                            TrackRow(
                                icon = {
                                    if (shapeIcon != null) {
                                        Icon(shapeIcon, contentDescription = null, tint = shapeColor, modifier = Modifier.size(12.dp))
                                    } else {
                                        Canvas(modifier = Modifier.size(12.dp)) {
                                            drawArc(color = shapeColor, startAngle = 45f, sweepAngle = 270f, useCenter = false, style = Stroke(width = 2f))
                                        }
                                    }
                                },
                                stripColor = Color(0xFF75B69E),
                                title = layerId,
                                scrollOffset = scrollOffset,
                                startTime = viewModel.layerStartTimes[layerId] ?: 0f,
                                endTime = viewModel.layerEndTimes[layerId] ?: Float.MAX_VALUE,
                                keyframes = combinedKeyframeTimes(layerId, viewModel.layerKeyframes, viewModel.opacityKeyframes),
                                pxPerSecond = pxPerSecond,
                                isHidden = viewModel.hiddenLayers.contains(layerId),
                                onVisibilityToggle = { if (viewModel.hiddenLayers.contains(layerId)) viewModel.hiddenLayers.remove(layerId) else viewModel.hiddenLayers.add(layerId) },
                                onClick = { viewModel.selectedLayer = layerId }
                            )
                        }
                        viewModel.addedTexts.forEachIndexed { index, textId ->
                            val layerId = textId.ifBlank { "Text ${index + 1}" }
                            TrackRow(
                                icon = { Icon(Icons.Default.TextFields, null, tint = Color.White, modifier = Modifier.size(12.dp)) },
                                stripColor = Color(0xFF6B8AFF),
                                title = layerId,
                                scrollOffset = scrollOffset,
                                startTime = viewModel.layerStartTimes[layerId] ?: 0f,
                                endTime = viewModel.layerEndTimes[layerId] ?: Float.MAX_VALUE,
                                keyframes = combinedKeyframeTimes(layerId, viewModel.layerKeyframes, viewModel.opacityKeyframes),
                                pxPerSecond = pxPerSecond,
                                isHidden = viewModel.hiddenLayers.contains(layerId),
                                onVisibilityToggle = { if (viewModel.hiddenLayers.contains(layerId)) viewModel.hiddenLayers.remove(layerId) else viewModel.hiddenLayers.add(layerId) },
                                onClick = { viewModel.selectedLayer = layerId }
                            )
                        }
                        viewModel.addedMedia.forEachIndexed { index, uri ->
                            val layerId = "Media ${index + 1}"
                            TrackRow(
                                icon = { Icon(Icons.Default.Image, null, tint = Color.White, modifier = Modifier.size(12.dp)) },
                                stripColor = Color(0xFFE2B06F),
                                title = layerId,
                                scrollOffset = scrollOffset,
                                startTime = viewModel.layerStartTimes[layerId] ?: 0f,
                                endTime = viewModel.layerEndTimes[layerId] ?: Float.MAX_VALUE,
                                keyframes = combinedKeyframeTimes(layerId, viewModel.layerKeyframes, viewModel.opacityKeyframes),
                                pxPerSecond = pxPerSecond,
                                isHidden = viewModel.hiddenLayers.contains(layerId),
                                onVisibilityToggle = { if (viewModel.hiddenLayers.contains(layerId)) viewModel.hiddenLayers.remove(layerId) else viewModel.hiddenLayers.add(layerId) },
                                onClick = { viewModel.selectedLayer = layerId }
                            )
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.height(32.dp).fillMaxWidth()) // Ruler space
                        // NOTE: this used to be an if/else-if chain matching 5 hardcoded
                        // demo layer names (Triangle 1, Callout 1, Circle 1, a literal
                        // jpg filename, YTDown_YouTube_LIFE_FORCE...). Those demo layers
                        // were removed (BASE_LAYER_IDS is now empty — see EditorViewModel),
                        // so those branches were permanently unreachable dead code. Real
                        // layers are only ever named "Shape N" or "Media N".
                        if (viewModel.selectedLayer?.startsWith("Text ") == true) {
                            TrackRow(
                                icon = { Icon(Icons.Default.TextFields, null, tint = Color.White, modifier = Modifier.size(12.dp)) },
                                stripColor = Color(0xFF6B8AFF),
                                title = viewModel.selectedLayer!!,
                                scrollOffset = scrollOffset,
                                startTime = viewModel.layerStartTimes[viewModel.selectedLayer] ?: 0f,
                                endTime = viewModel.layerEndTimes[viewModel.selectedLayer] ?: Float.MAX_VALUE,
                                keyframes = combinedKeyframeTimes(viewModel.selectedLayer!!, viewModel.layerKeyframes, viewModel.opacityKeyframes),
                                pxPerSecond = pxPerSecond,
                                isHidden = viewModel.hiddenLayers.contains(viewModel.selectedLayer!!),
                                onVisibilityToggle = { if (viewModel.hiddenLayers.contains(viewModel.selectedLayer!!)) viewModel.hiddenLayers.remove(viewModel.selectedLayer!!) else viewModel.hiddenLayers.add(viewModel.selectedLayer!!) },
                                onClick = { viewModel.selectedLayer = null; viewModel.inMoveTransform = false; viewModel.inColorFill = false; viewModel.inBlendingOpacity = false },
                                onTrimGestureStart = { viewModel.pushUndo() },
                                onTrimStart = { delta ->
                                    val id = viewModel.selectedLayer!!
                                    val raw = (viewModel.layerStartTimes[id] ?: 0f) + delta
                                    val snapped = applySnap(raw, id)
                                    viewModel.updateLayerStartTime(id, snapped, viewModel.timelineDurationSeconds)
                                },
                                onTrimEnd = { delta ->
                                    val id = viewModel.selectedLayer!!
                                    val current = viewModel.layerEndTimes[id]?.takeIf { it != Float.MAX_VALUE } ?: (viewModel.layerStartTimes[id] ?: 0f) + DEFAULT_LAYER_DURATION_SECONDS
                                    val snapped = applySnap(current + delta, id)
                                    viewModel.updateLayerEndTime(id, snapped, viewModel.timelineDurationSeconds)
                                }
                            )
                        } else if (viewModel.selectedLayer?.startsWith("Shape ") == true) {
                            val shapeIndex = viewModel.selectedLayer?.removePrefix("Shape ")?.toIntOrNull()?.minus(1)
                            if (shapeIndex != null && shapeIndex in viewModel.addedShapes.indices) {
                                val shapeIcon = viewModel.addedShapes[shapeIndex]
                                val shapeColor = viewModel.layerColors[viewModel.selectedLayer] ?: Color.LightGray
                                TrackRow(
                                    icon = {
                                        if (shapeIcon != null) Icon(shapeIcon, null, tint = shapeColor, modifier = Modifier.size(12.dp))
                                        else Canvas(modifier = Modifier.size(12.dp)) { drawArc(color = shapeColor, startAngle = 45f, sweepAngle = 270f, useCenter = false, style = Stroke(width = 2f)) }
                                    },
                                    stripColor = Color(0xFF75B69E),
                                    title = viewModel.selectedLayer!!,
                                    selected = true,
                                    scrollOffset = scrollOffset,
                                    startTime = viewModel.layerStartTimes[viewModel.selectedLayer] ?: 0f,
                                    endTime = viewModel.layerEndTimes[viewModel.selectedLayer] ?: Float.MAX_VALUE,
                                    keyframes = combinedKeyframeTimes(viewModel.selectedLayer!!, viewModel.layerKeyframes, viewModel.opacityKeyframes),
                                    pxPerSecond = pxPerSecond,
                                    isHidden = viewModel.hiddenLayers.contains(viewModel.selectedLayer!!),
                                    onVisibilityToggle = { if (viewModel.hiddenLayers.contains(viewModel.selectedLayer!!)) viewModel.hiddenLayers.remove(viewModel.selectedLayer!!) else viewModel.hiddenLayers.add(viewModel.selectedLayer!!) },
                                    onClick = { viewModel.selectedLayer = null; viewModel.inMoveTransform = false; viewModel.inColorFill = false; viewModel.inBlendingOpacity = false },
                                    onTrimGestureStart = { viewModel.pushUndo() },
                                    onTrimStart = { delta ->
                                        val id = viewModel.selectedLayer!!
                                        val raw = (viewModel.layerStartTimes[id] ?: 0f) + delta
                                        val snapped = applySnap(raw, id)
                                        viewModel.updateLayerStartTime(id, snapped, viewModel.timelineDurationSeconds)
                                    },
                                    onTrimEnd = { delta ->
                                        val id = viewModel.selectedLayer!!
                                        val current = viewModel.layerEndTimes[id]?.takeIf { it != Float.MAX_VALUE } ?: (viewModel.layerStartTimes[id] ?: 0f) + DEFAULT_LAYER_DURATION_SECONDS
                                        val snapped = applySnap(current + delta, id)
                                        viewModel.updateLayerEndTime(id, snapped, viewModel.timelineDurationSeconds)
                                    },
                                    onTrimHandleMoved = onTrimHandleMoved
                                )
                            }
                        } else if (viewModel.selectedLayer?.startsWith("Media ") == true) {
                            TrackRow(
                                icon = { Icon(Icons.Default.Image, null, tint = Color.White, modifier = Modifier.size(12.dp)) },
                                stripColor = Color(0xFFE2B06F),
                                title = viewModel.selectedLayer!!,
                                selected = true,
                                scrollOffset = scrollOffset,
                                startTime = viewModel.layerStartTimes[viewModel.selectedLayer] ?: 0f,
                                endTime = viewModel.layerEndTimes[viewModel.selectedLayer] ?: Float.MAX_VALUE,
                                keyframes = combinedKeyframeTimes(viewModel.selectedLayer!!, viewModel.layerKeyframes, viewModel.opacityKeyframes),
                                pxPerSecond = pxPerSecond,
                                isHidden = viewModel.hiddenLayers.contains(viewModel.selectedLayer!!),
                                onVisibilityToggle = { if (viewModel.hiddenLayers.contains(viewModel.selectedLayer!!)) viewModel.hiddenLayers.remove(viewModel.selectedLayer!!) else viewModel.hiddenLayers.add(viewModel.selectedLayer!!) },
                                onClick = { viewModel.selectedLayer = null; viewModel.inMoveTransform = false; viewModel.inColorFill = false; viewModel.inBlendingOpacity = false },
                                onTrimGestureStart = { viewModel.pushUndo() },
                                onTrimStart = { delta ->
                                    val id = viewModel.selectedLayer!!
                                    // A manual drag-trim is an explicit choice — mark this
                                    // layer as already "resized" so a still-pending async
                                    // duration fetch (MediaMetadataRetriever in onAddMedia,
                                    // or the ExoPlayer callback in VideoLayerPreview) can't
                                    // silently clobber it once that fetch resolves.
                                    if (!viewModel.autoResizedMediaLayers.contains(id)) {
                                        viewModel.autoResizedMediaLayers.add(id)
                                    }
                                    val raw = (viewModel.layerStartTimes[id] ?: 0f) + delta
                                    val snapped = applySnap(raw, id)
                                    viewModel.updateLayerStartTime(id, snapped, viewModel.timelineDurationSeconds)
                                },
                                onTrimEnd = { delta ->
                                    val id = viewModel.selectedLayer!!
                                    if (!viewModel.autoResizedMediaLayers.contains(id)) {
                                        viewModel.autoResizedMediaLayers.add(id)
                                    }
                                    val current = viewModel.layerEndTimes[id]?.takeIf { it != Float.MAX_VALUE } ?: (viewModel.layerStartTimes[id] ?: 0f) + DEFAULT_LAYER_DURATION_SECONDS
                                    val snapped = applySnap(current + delta, id)
                                    viewModel.updateLayerEndTime(id, snapped, viewModel.timelineDurationSeconds)
                                },
                                onTrimHandleMoved = onTrimHandleMoved
                            )
                        }
                        
                        if (viewModel.inMoveTransform) {
                            val layerId = viewModel.selectedLayer ?: ""
                            SelectedLayerMoveTransformMenu(
                                layerId = layerId,
                                playheadProgress = viewModel.playheadProgress,
                                layerTransforms = viewModel.layerTransforms,
                                layerKeyframes = viewModel.layerKeyframes,
                                onBeforeTransformChange = { viewModel.pushUndo() },
                                onBack = { viewModel.inMoveTransform = false }
                            )
                        } else if (viewModel.inColorFill) {
                            val layerId = viewModel.selectedLayer ?: ""
                            val currentColor = viewModel.layerColors[layerId] ?: Color(0xFF16B996)
                            ColorFillMenu(
                                currentColor = currentColor,
                                onColorChange = { newColor ->
                                    viewModel.pushUndo()
                                    val newColors = viewModel.layerColors.toMutableMap()
                                    newColors[layerId] = newColor
                                    viewModel.layerColors = newColors
                                },
                                onBack = { viewModel.inColorFill = false }, 
                                onPaletteClick = { viewModel.inColorPalette = true }
                            )
                        } else if (viewModel.inEffects) {
                            val layerId = viewModel.selectedLayer ?: ""
                            EffectsMenu(
                                layerId = layerId,
                                appliedEffects = viewModel.layerEffects[layerId] ?: emptyList(),
                                onAddEffectClick = { viewModel.inEffectBrowser = true },
                                onUpdateEffect = { updatedEffect ->
                                    viewModel.pushUndo()
                                    val currentEffects = viewModel.layerEffects[layerId] ?: emptyList()
                                    viewModel.layerEffects = viewModel.layerEffects + (layerId to currentEffects.map { if (it.id == updatedEffect.id) updatedEffect else it })
                                },
                                onRemoveEffect = { removedId ->
                                    viewModel.pushUndo()
                                    val currentEffects = viewModel.layerEffects[layerId] ?: emptyList()
                                    viewModel.layerEffects = viewModel.layerEffects + (layerId to currentEffects.filter { it.id != removedId })
                                },
                                onBack = { viewModel.inEffects = false }
                            )
                        } else if (viewModel.inBlendingOpacity) {
                            val layerId = viewModel.selectedLayer ?: ""
                            val currentOpacity = getActiveOpacity(layerId, viewModel.playheadProgress, viewModel.layerOpacities, viewModel.opacityKeyframes)
                            val currentBlendMode = viewModel.layerBlendModes[layerId] ?: "Normal"
                            BlendingOpacityMenu(
                                currentOpacity = currentOpacity,
                                currentBlendMode = currentBlendMode,
                                layerId = layerId,
                                playheadProgress = viewModel.playheadProgress,
                                opacityKeyframes = viewModel.opacityKeyframes[layerId] ?: emptyList(),
                                onOpacityChange = { newOpacity ->
                                    viewModel.pushUndo()
                                    val appliedToKeyframe = updateLayerOpacityKeyframeIfPresent(layerId, newOpacity, viewModel.playheadProgress, viewModel.opacityKeyframes)
                                    if (!appliedToKeyframe) {
                                        val map = viewModel.layerOpacities.toMutableMap()
                                        map[layerId] = newOpacity
                                        viewModel.layerOpacities = map
                                    }
                                },
                                onToggleOpacityKeyframe = {
                                    viewModel.pushUndo()
                                    val existing = viewModel.opacityKeyframes[layerId] ?: emptyList()
                                    val index = existing.indexOfFirst { kotlin.math.abs(it.time - viewModel.playheadProgress) < 0.05f }
                                    val updated = existing.toMutableList()
                                    if (index != -1) {
                                        updated.removeAt(index)
                                    } else {
                                        updated.add(OpacityKeyframe(time = viewModel.playheadProgress, opacity = currentOpacity))
                                        updated.sortBy { it.time }
                                    }
                                    viewModel.opacityKeyframes[layerId] = updated
                                },
                                onBlendModeChange = { newMode ->
                                    viewModel.pushUndo()
                                    val map = viewModel.layerBlendModes.toMutableMap()
                                    map[layerId] = newMode
                                    viewModel.layerBlendModes = map
                                },
                                onBack = { viewModel.inBlendingOpacity = false }
                            )
                        } else {
                            LayerPropertiesMenu(
                                onMoveTransformClick = { viewModel.inMoveTransform = true },
                                onColorFillClick = { viewModel.inColorFill = true },
                                onEffectsClick = { viewModel.inEffects = true },
                                onBlendingOpacityClick = { viewModel.inBlendingOpacity = true },
                                onEditShapeClick = { viewModel.inVectorDrawing = true },
                                onSplitClick = {
                                    viewModel.selectedLayer?.let { layerId ->
                                        viewModel.pushUndo()
                                        // End current layer
                                        val newEndTimes = viewModel.layerEndTimes.toMutableMap()
                                        val originalEndTime = newEndTimes[layerId] ?: Float.MAX_VALUE
                                        newEndTimes[layerId] = viewModel.playheadProgress
                                        viewModel.layerEndTimes = newEndTimes
                                        
                                        var newLayerId: String? = null
                                        
                                        // Duplicate layer
                                        if (layerId.startsWith("Shape ")) {
                                            val shapeIndex = layerId.removePrefix("Shape ").toIntOrNull()?.minus(1)
                                            if (shapeIndex != null && shapeIndex in viewModel.addedShapes.indices) {
                                                val shapeIcon = viewModel.addedShapes[shapeIndex]
                                                val newSize = viewModel.addedShapes.size + 1
                                                newLayerId = "Shape $newSize"
                                                viewModel.addedShapes = viewModel.addedShapes + listOf(shapeIcon)
                                            }
                                        } else if (layerId.startsWith("Media ")) {
                                            val mediaIndex = layerId.removePrefix("Media ").toIntOrNull()?.minus(1)
                                            if (mediaIndex != null && mediaIndex in viewModel.addedMedia.indices) {
                                                val uri = viewModel.addedMedia[mediaIndex]
                                                val newSize = viewModel.addedMedia.size + 1
                                                newLayerId = "Media $newSize"
                                                viewModel.addedMedia = viewModel.addedMedia + listOf(uri)
                                            }
                                        } else {
                                            val baseLayer = BASE_LAYER_IDS.find { layerId.startsWith(it) }
                                            if (baseLayer != null) {
                                                val count = (viewModel.defaultLayerCount[baseLayer] ?: 1) + 1
                                                val newCounts = viewModel.defaultLayerCount.toMutableMap()
                                                newCounts[baseLayer] = count
                                                viewModel.defaultLayerCount = newCounts
                                                newLayerId = "${baseLayer}_split_$count"
                                            }
                                        }

                                        newLayerId?.let { newId ->
                                            val newStartTimes = viewModel.layerStartTimes.toMutableMap()
                                            newStartTimes[newId] = viewModel.playheadProgress
                                            viewModel.layerStartTimes = newStartTimes
                                            
                                            val newEndTimesForNew = viewModel.layerEndTimes.toMutableMap()
                                            newEndTimesForNew[newId] = originalEndTime
                                            viewModel.layerEndTimes = newEndTimesForNew
                                            
                                            viewModel.layerColors[layerId]?.let { color ->
                                                val newColors = viewModel.layerColors.toMutableMap()
                                                newColors[newId] = color
                                                viewModel.layerColors = newColors
                                            }
                                            viewModel.layerTransforms[layerId]?.let { transform ->
                                                viewModel.layerTransforms[newId] = transform.copy()
                                            }
                                            viewModel.selectedLayer = newId
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                 // The top ruler area still needs to exist to match padding, 
                 // but the screenshot shows the menu starting right below the ruler.
                 Column(modifier = Modifier.fillMaxSize()) {
                     Box(modifier = Modifier.height(32.dp).fillMaxWidth())
                     AddElementsMenu(
                         onClose = { viewModel.showAddMenu = false },
                         onAddText = {
                             viewModel.pushUndo()
                             val newSize = viewModel.addedTexts.size + 1
                             val newLayerId = "Text $newSize"
                             viewModel.addedTexts = viewModel.addedTexts + listOf(newLayerId)
                             viewModel.layerTexts[newLayerId] = "New Text"
                             
                             val newStartTimes = viewModel.layerStartTimes.toMutableMap()
                             newStartTimes[newLayerId] = viewModel.playheadProgress
                             viewModel.layerStartTimes = newStartTimes
                             val newEndTimes = viewModel.layerEndTimes.toMutableMap()
                             newEndTimes[newLayerId] = viewModel.playheadProgress + DEFAULT_LAYER_DURATION_SECONDS
                             viewModel.layerEndTimes = newEndTimes
                             
                             viewModel.selectedLayer = newLayerId
                             viewModel.showAddMenu = false
                         },
                         onAddShape = { icon -> 
                             viewModel.pushUndo()
                             val newSize = viewModel.addedShapes.size + 1
                             val newLayerId = "Shape $newSize"
                             viewModel.addedShapes = viewModel.addedShapes + listOf(icon)
                             
                             val newStartTimes = viewModel.layerStartTimes.toMutableMap()
                             newStartTimes[newLayerId] = viewModel.playheadProgress
                             viewModel.layerStartTimes = newStartTimes

                             val newEndTimes = viewModel.layerEndTimes.toMutableMap()
                             newEndTimes[newLayerId] = viewModel.playheadProgress + DEFAULT_LAYER_DURATION_SECONDS
                             viewModel.layerEndTimes = newEndTimes
                             
                             viewModel.selectedLayer = newLayerId
                             viewModel.showAddMenu = false
                         },
                         onVectorDrawingClick = {
                             viewModel.pushUndo()
                             while (viewModel.addedShapes.size < 2) {
                                 viewModel.addedShapes = viewModel.addedShapes + listOf<androidx.compose.ui.graphics.vector.ImageVector?>(null)
                             }
                             val layerId = "Shape 2"
                             val newStartTimes = viewModel.layerStartTimes.toMutableMap()
                             newStartTimes[layerId] = viewModel.playheadProgress
                             viewModel.layerStartTimes = newStartTimes

                             val newEndTimes = viewModel.layerEndTimes.toMutableMap()
                             newEndTimes[layerId] = viewModel.playheadProgress + DEFAULT_LAYER_DURATION_SECONDS
                             viewModel.layerEndTimes = newEndTimes

                             viewModel.selectedLayer = layerId
                             viewModel.showAddMenu = false
                             viewModel.inVectorDrawing = true
                         },
                         onAddMedia = { uri ->
                             viewModel.pushUndo()
                             val newSize = viewModel.addedMedia.size + 1
                             val newLayerId = "Media $newSize"
                             viewModel.addedMedia = viewModel.addedMedia + listOf(uri)
                             
                             val newStartTimes = viewModel.layerStartTimes.toMutableMap()
                             newStartTimes[newLayerId] = viewModel.playheadProgress
                             viewModel.layerStartTimes = newStartTimes

                             // Give the clip a default length immediately so it's
                             // usable right away, then refine it with the real
                             // media duration once that's known. MediaMetadataRetriever
                             // can take noticeable time on large video files, so it
                             // runs off the main thread to avoid UI jank/stutter.
                             val newEndTimes = viewModel.layerEndTimes.toMutableMap()
                             newEndTimes[newLayerId] = viewModel.playheadProgress + DEFAULT_LAYER_DURATION_SECONDS
                             viewModel.layerEndTimes = newEndTimes

                             coroutineScope.launch {
                                 var mediaDuration = withContext(Dispatchers.IO) {
                                     getMediaDurationSecondsOrNull(context, uri)
                                 }
                                 if (mediaDuration == null) {
                                     // Some content:// URIs aren't immediately readable right
                                     // after being returned from the picker. One short retry
                                     // avoids permanently leaving the clip stuck at the 5s
                                     // fallback duration.
                                     kotlinx.coroutines.delay(300)
                                     mediaDuration = withContext(Dispatchers.IO) {
                                         getMediaDurationSecondsOrNull(context, uri)
                                     }
                                 }
                                 // Only apply this if nothing has already claimed the layer's
                                 // duration in the meantime — a manual trim, the "Set duration"
                                 // dialog, or the ExoPlayer callback in VideoLayerPreview (which
                                 // is usually more reliable for content:// URIs than
                                 // MediaMetadataRetriever anyway). Without this check, this
                                 // slower fetch could resolve after the user already edited the
                                 // clip and silently overwrite that edit.
                                 if (mediaDuration != null && !viewModel.autoResizedMediaLayers.contains(newLayerId)) {
                                     viewModel.autoResizedMediaLayers.add(newLayerId)
                                     val updatedEndTimes = viewModel.layerEndTimes.toMutableMap()
                                     updatedEndTimes[newLayerId] = (viewModel.layerStartTimes[newLayerId] ?: 0f) + mediaDuration
                                     viewModel.layerEndTimes = updatedEndTimes
                                 } else if (mediaDuration == null) {
                                     android.widget.Toast.makeText(
                                         context,
                                         "Couldn't read video length — you can still trim the clip manually.",
                                         android.widget.Toast.LENGTH_SHORT
                                     ).show()
                                 }
                             }
                             
                             viewModel.selectedLayer = newLayerId
                             viewModel.showAddMenu = false
                         }
                     )
                 }
            }
            
            // Playhead Visuals Overlay (Ruler & Line)
            val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
            Canvas(Modifier.fillMaxSize()) {
                // Draw ruler ticks + second labels. Tick spacing adapts to zoom:
                // at 1x, one second = pxPerSecond px, with a minor tick every
                // 0.2s and a labeled major tick every 1s — as the timeline
                // zooms out, major ticks widen to every 5s/10s/30s so labels
                // never overlap; zoomed in, they narrow for frame-level work.
                val startX = 64.dp.toPx() 
                val scrollPx = scrollOffset.toPx()

                // Choose a "nice" major-tick interval (in seconds) so there's
                // always comfortable label spacing regardless of zoom.
                val minMajorPxGap = 56.dp.toPx()
                val niceIntervals = listOf(0.2f, 0.5f, 1f, 2f, 5f, 10f, 15f, 30f, 60f, 120f, 300f)
                val majorIntervalSeconds = niceIntervals.firstOrNull { (it * pxPerSecond).dp.toPx() >= minMajorPxGap } ?: niceIntervals.last()
                val minorIntervalSeconds = majorIntervalSeconds / 5f
                val tickSpx = (minorIntervalSeconds * pxPerSecond).dp.toPx()
                val zeroOffset = startX + scrollPx
                
                val firstTickIndex = if (startX > zeroOffset) {
                    ((startX - zeroOffset) / tickSpx).toInt()
                } else {
                     -((zeroOffset - startX) / tickSpx).toInt()
                }
                
                var tickIndex = maxOf(0, firstTickIndex) // no negative time
                var drawX = zeroOffset + tickIndex * tickSpx
                
                while(drawX < size.width) {
                    if (drawX >= startX) {
                        val tickSeconds = tickIndex * minorIntervalSeconds
                        val isMajor = kotlin.math.abs(tickSeconds % majorIntervalSeconds) < (minorIntervalSeconds / 2f)
                        val tickHeight = if (isMajor) 12.dp.toPx() else 6.dp.toPx()
                        drawLine(
                            Color.White.copy(alpha=0.2f),
                            Offset(drawX, 32.dp.toPx() - tickHeight),
                            Offset(drawX, 32.dp.toPx()),
                            strokeWidth = 1.dp.toPx()
                        )
                        if (isMajor) {
                            val label = formatRulerLabel(tickSeconds)
                            val measured = textMeasurer.measure(
                                label,
                                style = androidx.compose.ui.text.TextStyle(
                                    color = Color.White.copy(alpha = 0.55f),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            drawText(measured, topLeft = Offset(drawX + 3.dp.toPx(), 2.dp.toPx()))
                        }
                    }
                    tickIndex++
                    drawX += tickSpx
                }
                
                // Playhead line
                drawLine(
                    Color(0xFF16B996),
                    Offset(playheadX.toPx(), 12.dp.toPx()), 
                    Offset(playheadX.toPx(), size.height),
                    strokeWidth = 2.dp.toPx()
                )
                // Draw a nice round playhead cap/head at the top of the line
                drawCircle(
                    color = Color(0xFF16B996),
                    radius = 5.dp.toPx(),
                    center = Offset(playheadX.toPx(), 12.dp.toPx())
                )
            }
            
            // Playhead Time Box
            val totalSec = viewModel.playheadProgress.toInt()
            val min = totalSec / 60
            val sec = totalSec % 60
            val fraction = ((viewModel.playheadProgress - totalSec) * 100).toInt()
            val timeStr = String.format("%02d:%02d.%02d", min, sec, fraction)

            Box(
                modifier = Modifier
                    .offset(x = playheadX - 35.dp, y = 8.dp)
                    .background(Color(0xFF16B996), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(timeStr, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            
            // Floating Button Add (+)
            if (!viewModel.showAddMenu && viewModel.selectedLayer == null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .size(56.dp)
                        .background(Color(0xFF181A20), CircleShape)
                        .border(2.dp, Color(0xFF16B996), CircleShape)
                        .clickable { viewModel.showAddMenu = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Color(0xFF16B996), modifier = Modifier.size(32.dp))
                }
            }
        }
    }
