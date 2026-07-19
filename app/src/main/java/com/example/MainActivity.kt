package com.example
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

data class EditorSnapshot(
    val defaultLayerCount: Map<String, Int>,
    val deletedLayers: List<String>,
    val layerColors: Map<String, Color>,
    val layerOpacities: Map<String, Float>,
    val layerBlendModes: Map<String, String>,
    val layerStartTimes: Map<String, Float>,
    val layerEndTimes: Map<String, Float>,
    val layerTransforms: Map<String, LayerTransform>,
    val layerKeyframes: Map<String, List<LayerKeyframe>>,
    val opacityKeyframes: Map<String, List<OpacityKeyframe>>,
    val layerEffects: Map<String, List<AppliedEffect>>,
    val addedShapes: List<ImageVector?>,
    val addedMedia: List<Uri>,
    val addedTexts: List<String> = emptyList(),
    val layerTexts: Map<String, String> = emptyMap(),
    val vectorPoints: List<Offset>,
    val pointModes: List<Boolean>
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Render engine removed — clean state.
        android.util.Log.i("App", "Running clean on main branch without native engine")

        setContent {
            MotionStudioTheme {
                MotionStudioTimelineEditor()
            }
        }
    }
}

@Composable
fun SelectionHandles() {
    val handleSize = 12.dp
    val handleOffset = 6.dp
    val stroke = 1.dp
    Box(modifier = Modifier.fillMaxSize().border(1.5.dp, Color.White)) {
        Box(Modifier.align(Alignment.TopStart).offset(x = -handleOffset, y = -handleOffset).size(handleSize).background(Color.White, CircleShape).border(stroke, Color.Black, CircleShape))
        Box(Modifier.align(Alignment.TopCenter).offset(y = -handleOffset).size(handleSize).background(Color.White, CircleShape).border(stroke, Color.Black, CircleShape))
        Box(Modifier.align(Alignment.TopEnd).offset(x = handleOffset, y = -handleOffset).size(handleSize).background(Color.White, CircleShape).border(stroke, Color.Black, CircleShape))
        Box(Modifier.align(Alignment.CenterStart).offset(x = -handleOffset).size(handleSize).background(Color.White, CircleShape).border(stroke, Color.Black, CircleShape))
        Box(Modifier.align(Alignment.CenterEnd).offset(x = handleOffset).size(handleSize).background(Color.White, CircleShape).border(stroke, Color.Black, CircleShape))
        Box(Modifier.align(Alignment.BottomStart).offset(x = -handleOffset, y = handleOffset).size(handleSize).background(Color.White, CircleShape).border(stroke, Color.Black, CircleShape))
        Box(Modifier.align(Alignment.BottomCenter).offset(y = handleOffset).size(handleSize).background(Color.White, CircleShape).border(stroke, Color.Black, CircleShape))
        Box(Modifier.align(Alignment.BottomEnd).offset(x = handleOffset, y = handleOffset).size(handleSize).background(Color.White, CircleShape).border(stroke, Color.Black, CircleShape))
    }
}

data class LayerTransform(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val rotation: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f
)

// Timeline scale: how many pixels represent one second on the timeline
// strip at 1x zoom. The effective scale is this times
// EditorViewModel.timelineZoom (see rememberTimelinePxPerSecond below),
// since the timeline now supports pinch-to-zoom.
const val TIMELINE_PIXELS_PER_SECOND = 50f
// Total duration of the timeline is now computed dynamically from the
// actual layers' end times — see EditorViewModel.timelineDurationSeconds.
// (Previously this was a fixed `const val TIMELINE_DURATION_SECONDS = 8f`.)

// Default length (in seconds) given to a newly added layer that has no
// inherent duration of its own (shapes, vector drawings, still images).
// This mirrors Motion Studio's default 5s starting clip length.
const val DEFAULT_LAYER_DURATION_SECONDS = 5f

// Reads a media file's real duration in seconds via MediaMetadataRetriever.
// Returns null if the URI has no duration metadata (e.g. it's a still image)
// or if reading fails for any reason, so callers can fall back to
// DEFAULT_LAYER_DURATION_SECONDS instead of crashing.
fun getMediaDurationSecondsOrNull(context: android.content.Context, uri: android.net.Uri): Float? {
    // First attempt: setDataSource(context, uri) directly. This is what
    // most content:// URIs need, but it can throw for some picker/provider
    // URIs (e.g. transient permission issues right after picking).
    try {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val ms = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (ms != null && ms > 0) return ms / 1000f
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }
    } catch (e: Exception) {
        android.util.Log.w("MediaDuration", "setDataSource(context, uri) failed for $uri, retrying via fd", e)
    }

    // Fallback: open a raw file descriptor and read metadata from that
    // instead. This works even when the direct context+uri overload fails.
    return try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(pfd.fileDescriptor)
                val ms = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                if (ms != null && ms > 0) ms / 1000f else null
            } finally {
                try { retriever.release() } catch (e: Exception) {}
            }
        }
    } catch (e: Exception) {
        android.util.Log.w("MediaDuration", "fd fallback also failed for $uri", e)
        null
    }
}

// Parses a frame-rate label like "30 fps" into its numeric value.
// Clean state: no external video renderer dependency.
// (playhead frame counter) matches the frame rate the user picked in
// Project Settings, instead of always assuming 30fps.
fun parseFrameRate(frameRateStr: String): Int = when {
    frameRateStr.contains("60") -> 60
    frameRateStr.contains("30") -> 30
    frameRateStr.contains("24") -> 24
    frameRateStr.contains("15") -> 15
    else -> 30
}

@Composable
fun MotionStudioTimelineEditor() {
    val viewModel: com.example.viewmodel.EditorViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // Plain coil-compose has no video decoder, so AsyncImage silently fails
    // to render anything for video URIs (images work, video thumbnails
    // don't). This ImageLoader adds VideoFrameDecoder so video media shows
    // a real frame instead of a blank/broken thumbnail.
    val videoAwareImageLoader = remember(context) {
        coil.ImageLoader.Builder(context)
            .components { add(coil.decode.VideoFrameDecoder.Factory()) }
            .build()
    }
    // Dragging tiny trim handles to reach large durations is awkward on a
    // small screen (the handle can scroll out of reach). This dialog lets
    // the user type an exact duration in seconds instead, as a reliable
    // alternative to dragging.
    var showDurationDialog by remember { mutableStateOf(false) }
    var durationInputText by remember { mutableStateOf("") }
    val hasActiveShapes = viewModel.addedShapes.indices.any { "Shape ${it + 1}" !in viewModel.deletedLayers }
    val hasActiveMedia = viewModel.addedMedia.indices.any { "Media ${it + 1}" !in viewModel.deletedLayers }
    val hasActiveTexts = viewModel.addedTexts.indices.any { "Text ${it + 1}" !in viewModel.deletedLayers }
    val hasLayers = hasActiveShapes || hasActiveMedia || hasActiveTexts

    LaunchedEffect(viewModel.isPlaying, hasLayers) {
        if (!hasLayers && viewModel.isPlaying) {
            viewModel.isPlaying = false
        } else if (viewModel.isPlaying) {
            var lastFrame = withFrameMillis { it }
            while (viewModel.isPlaying) {
                val currentFrame = withFrameMillis { it }
                val dt = (currentFrame - lastFrame) / 1000f // seconds
                lastFrame = currentFrame
                
                viewModel.playheadProgress += dt
                if (viewModel.playheadProgress > viewModel.timelineDurationSeconds) { // Loop after reaching the end
                    viewModel.playheadProgress = 0f
                }
            }
        }
    }

    CompositionLocalProvider(LocalDeletedLayers provides viewModel.deletedLayers) {
    if (viewModel.inEffectBrowser) {
        EffectBrowserScreen(
            onClose = { viewModel.inEffectBrowser = false },
            onSelectEffect = { effectName, category ->
                viewModel.inEffectBrowser = false
                if (!viewModel.selectedLayer.isNullOrEmpty()) {
                    viewModel.pushUndo()
                    val currentEffects = viewModel.layerEffects[viewModel.selectedLayer] ?: emptyList()
                    viewModel.layerEffects = viewModel.layerEffects + (viewModel.selectedLayer!! to (currentEffects + createDefaultEffect(effectName, category)))
                }
            }
        )
    } else if (viewModel.inColorPalette) {
        val layerId = viewModel.selectedLayer ?: ""
        val currentColor = viewModel.layerColors[layerId] ?: Color(0xFF16B996)
        ColorPaletteScreen(
            currentColor = currentColor,
            onColorChange = { newColor ->
                viewModel.pushUndo()
                val newColors = viewModel.layerColors.toMutableMap()
                newColors[layerId] = newColor
                viewModel.layerColors = newColors
            },
            onClose = { viewModel.inColorPalette = false }
        )
    } else if (viewModel.inVectorDrawing) {
        VectorDrawingEditor(
            points = viewModel.vectorPoints,
            onPointsChange = { viewModel.pushUndo(); viewModel.vectorPoints = it },
            selectedPointIndex = viewModel.selectedPointIndex,
            onSelectedPointChange = { viewModel.selectedPointIndex = it },
            pointModes = viewModel.pointModes,
            onPointModesChange = { viewModel.pushUndo(); viewModel.pointModes = it },
            playheadProgress = viewModel.playheadProgress,
            isPlaying = viewModel.isPlaying,
            onPlayPauseToggle = { viewModel.isPlaying = !viewModel.isPlaying },
            onClose = { viewModel.inVectorDrawing = false }
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().background(Color(0xFF181A20)).systemBarsPadding()) {
        // Top Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (viewModel.inMoveTransform) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.clickable { viewModel.inMoveTransform = false }.padding(end=16.dp))
                Text("Move & Transform", color = Color.White, fontSize = 16.sp)
            } else if (viewModel.inColorFill) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.clickable { viewModel.inColorFill = false }.padding(end=16.dp))
                Text("Color & Fill", color = Color.White, fontSize = 16.sp)
            } else if (viewModel.inEffects) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.clickable { viewModel.inEffects = false }.padding(end=16.dp))
                Text("Effects", color = Color.White, fontSize = 16.sp)
            } else if (viewModel.inBlendingOpacity) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.clickable { viewModel.inBlendingOpacity = false }.padding(end=16.dp))
                Text("Blending & Opacity", color = Color.White, fontSize = 16.sp)
            } else if (viewModel.selectedLayer == null) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("New Project 3", color = Color.White, fontSize = 16.sp, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha=0.3f)))
                }
                Spacer(Modifier.width(16.dp))
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = if (viewModel.undoStack.isNotEmpty()) Color.White else Color.White.copy(alpha=0.3f), modifier = Modifier.size(20.dp).clickable(enabled = viewModel.undoStack.isNotEmpty()) { viewModel.performUndo(context) })
                Spacer(Modifier.width(16.dp))
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = if (viewModel.redoStack.isNotEmpty()) Color.White else Color.White.copy(alpha=0.3f), modifier = Modifier.size(20.dp).clickable(enabled = viewModel.redoStack.isNotEmpty()) { viewModel.performRedo(context) })
                Spacer(Modifier.width(16.dp))
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(20.dp).clickable { viewModel.showSettings = true })
                Spacer(Modifier.width(16.dp))
                
                val hasActiveShapesTop = viewModel.addedShapes.indices.any { "Shape ${it + 1}" !in viewModel.deletedLayers }
                val hasActiveMediaTop = viewModel.addedMedia.indices.any { "Media ${it + 1}" !in viewModel.deletedLayers }
                val hasActiveTextsTop = viewModel.addedTexts.indices.any { "Text ${it + 1}" !in viewModel.deletedLayers }
                val hasLayersTop = hasActiveShapesTop || hasActiveMediaTop || hasActiveTextsTop
                
                Icon(Icons.Default.IosShare, contentDescription = "Export", tint = if (hasLayersTop) Color.White else Color.White.copy(alpha=0.3f), modifier = Modifier.size(20.dp).clickable(enabled = hasLayersTop) { viewModel.showExport = true })
            } else {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.clickable { viewModel.selectedLayer = null; viewModel.inMoveTransform = false; viewModel.inColorFill = false; viewModel.inBlendingOpacity = false }.padding(end=16.dp))
                Column(modifier = Modifier.weight(1f)) {
                     Text(viewModel.selectedLayer ?: "", color = Color.White, fontSize = 14.sp, maxLines = 1)
                     Spacer(Modifier.height(4.dp))
                     Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha=0.3f)))
                }
                Spacer(Modifier.width(16.dp))
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = if (viewModel.undoStack.isNotEmpty()) Color.White else Color.White.copy(alpha=0.3f), modifier = Modifier.size(20.dp).clickable(enabled = viewModel.undoStack.isNotEmpty()) { viewModel.performUndo(context) })
                Spacer(Modifier.width(16.dp))
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = if (viewModel.redoStack.isNotEmpty()) Color.White else Color.White.copy(alpha=0.3f), modifier = Modifier.size(20.dp).clickable(enabled = viewModel.redoStack.isNotEmpty()) { viewModel.performRedo(context) })
                Spacer(Modifier.width(16.dp))
                Box(contentAlignment = Alignment.TopEnd, modifier = Modifier.clickable {
                    viewModel.selectedLayer?.let { layerId ->
                        viewModel.pushUndo()
                        // Duplicate a "Shape N" or "Media N" layer by appending a
                        // copy to the matching backing list, then copying over
                        // its color/transform/timing onto the new layer id.
                        val newLayerId: String? = when {
                            layerId.startsWith("Shape ") -> {
                                val idx = layerId.removePrefix("Shape ").toIntOrNull()?.minus(1)
                                if (idx != null && idx in viewModel.addedShapes.indices) {
                                    val icon = viewModel.addedShapes[idx]
                                    viewModel.addedShapes = viewModel.addedShapes + listOf(icon)
                                    "Shape ${viewModel.addedShapes.size}"
                                } else null
                            }
                            layerId.startsWith("Media ") -> {
                                val idx = layerId.removePrefix("Media ").toIntOrNull()?.minus(1)
                                if (idx != null && idx in viewModel.addedMedia.indices) {
                                    val uri = viewModel.addedMedia[idx]
                                    viewModel.addedMedia = viewModel.addedMedia + listOf(uri)
                                    "Media ${viewModel.addedMedia.size}"
                                } else null
                            }
                            layerId.startsWith("Text ") -> {
                                val idx = layerId.removePrefix("Text ").toIntOrNull()?.minus(1)
                                if (idx != null && idx in viewModel.addedTexts.indices) {
                                    val newId = "Text ${viewModel.addedTexts.size + 1}"
                                    viewModel.addedTexts = viewModel.addedTexts + listOf(newId)
                                    viewModel.layerTexts[newId] = viewModel.layerTexts[layerId] ?: "New Text"
                                    newId
                                } else null
                            }
                            else -> null
                        }
                        if (newLayerId != null) {
                            val start = viewModel.layerStartTimes[layerId] ?: viewModel.playheadProgress
                            val end = viewModel.layerEndTimes[layerId]?.takeIf { it != Float.MAX_VALUE } ?: (start + DEFAULT_LAYER_DURATION_SECONDS)
                            viewModel.layerStartTimes = viewModel.layerStartTimes.toMutableMap().apply { put(newLayerId, viewModel.playheadProgress) }
                            viewModel.layerEndTimes = viewModel.layerEndTimes.toMutableMap().apply { put(newLayerId, viewModel.playheadProgress + (end - start)) }
                            viewModel.layerColors[layerId]?.let { c -> viewModel.layerColors = viewModel.layerColors.toMutableMap().apply { put(newLayerId, c) } }
                            viewModel.layerTransforms[layerId]?.let { t -> viewModel.layerTransforms[newLayerId] = t.copy() }
                            viewModel.selectedLayer = newLayerId
                            Toast.makeText(context, "Layer duplicated", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                   Icon(Icons.Default.ContentCopy, "Duplicate", tint = Color.White, modifier = Modifier.size(20.dp).padding(top=2.dp, end=2.dp))
                   Text("TRY", color=Color(0xFF16171D), fontSize=6.sp, fontWeight=FontWeight.Black, modifier = Modifier.background(Color(0xFF16B996), RoundedCornerShape(2.dp)).padding(horizontal=2.dp, vertical=1.dp).align(Alignment.TopEnd).offset(x=8.dp, y=(-6).dp))
                }
                Spacer(Modifier.width(16.dp))
                Icon(Icons.Default.Delete, "Delete", tint = Color.White, modifier = Modifier.size(20.dp).clickable { viewModel.performDeleteLayer(context) })
                Spacer(Modifier.width(16.dp))
                Icon(Icons.Default.Timer, "Set duration", tint = Color.White, modifier = Modifier.size(20.dp).clickable {
                    viewModel.selectedLayer?.let { layerId ->
                        val start = viewModel.layerStartTimes[layerId] ?: 0f
                        val end = viewModel.layerEndTimes[layerId]?.takeIf { it != Float.MAX_VALUE } ?: (start + DEFAULT_LAYER_DURATION_SECONDS)
                        durationInputText = String.format("%.2f", end - start)
                        showDurationDialog = true
                    }
                })
                Spacer(Modifier.width(16.dp))
                Icon(Icons.Default.MoreHoriz, "More", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        
        com.example.ui.canvas.CanvasArea(viewModel, modifier = Modifier.weight(1f))
        // Toolbar controls below canvas
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).background(Color(0xFF1B1D25)),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(24.dp).clickable { viewModel.playheadProgress = 0f; viewModel.isPlaying = false })
            Icon(
                if (viewModel.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, 
                null, 
                tint = if (hasLayers) Color.White else Color.White.copy(alpha = 0.3f), 
                modifier = Modifier.size(28.dp).clickable(enabled = hasLayers) { viewModel.isPlaying = !viewModel.isPlaying }
            )
            Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(24.dp).clickable { viewModel.playheadProgress = viewModel.timelineDurationSeconds; viewModel.isPlaying = false })
            Icon(Icons.Default.LibraryAdd, null, tint = Color.White, modifier = Modifier.size(20.dp).clickable { viewModel.showAddMenu = true })
            Icon(Icons.Default.CropFree, null, tint = Color.White, modifier = Modifier.size(20.dp).clickable { viewModel.selectedAspectRatio = if (viewModel.selectedAspectRatio == "9:16") "16:9" else if (viewModel.selectedAspectRatio == "16:9") "1:1" else "9:16" })
        }
        
        // Timeline zoom controls — pinch-to-zoom works directly on the
        // timeline below, but a visible +/- and reset-to-fit affordance is
        // what every professional NLE also offers, since not everyone
        // discovers pinch gestures on a touch timeline.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(Color(0xFF181A20)),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.ZoomOut, null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp).clickable {
                    viewModel.timelineZoom = (viewModel.timelineZoom / 1.4f).coerceIn(TIMELINE_ZOOM_RANGE.start, TIMELINE_ZOOM_RANGE.endInclusive)
                }
            )
            Text(
                "${(viewModel.timelineZoom * 100).toInt()}%",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .clickable { viewModel.timelineZoom = 1f } // tap to reset to 100%
            )
            Icon(
                Icons.Default.ZoomIn, null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp).clickable {
                    viewModel.timelineZoom = (viewModel.timelineZoom * 1.4f).coerceIn(TIMELINE_ZOOM_RANGE.start, TIMELINE_ZOOM_RANGE.endInclusive)
                }
            )
            Spacer(Modifier.width(12.dp))
        }

        // Timeline Work Area

        com.example.ui.timeline.TimelineArea(viewModel)
        }
        
    if (viewModel.showSettings) {
        ProjectSettingsMenu(
            selectedRatio = viewModel.selectedAspectRatio,
            onRatioSelected = { viewModel.selectedAspectRatio = it },
            selectedResolution = viewModel.selectedResolution,
            onResolutionSelected = { viewModel.selectedResolution = it },
            selectedFrameRate = viewModel.selectedFrameRate,
            onFrameRateSelected = { viewModel.selectedFrameRate = it },
            selectedBackground = viewModel.selectedBackground,
            onBackgroundSelected = { viewModel.selectedBackground = it },
            onClose = { viewModel.showSettings = false }
        )
    }
    
    if (viewModel.showExport) {
        ExportShareMenu(
            selectedResolution = viewModel.selectedResolution,
            onResolutionSelected = { viewModel.selectedResolution = it },
            selectedFrameRate = viewModel.selectedFrameRate,
            onFrameRateSelected = { viewModel.selectedFrameRate = it },
            selectedAspectRatio = viewModel.selectedAspectRatio,
            selectedBackground = viewModel.selectedBackground,
            vectorPoints = viewModel.vectorPoints,
            pointModes = viewModel.pointModes,
            layerColors = viewModel.layerColors,
            defaultLayerCount = viewModel.defaultLayerCount,
            addedMedia = viewModel.addedMedia,
            addedShapes = viewModel.addedShapes,
            addedTexts = viewModel.addedTexts,
            layerTexts = viewModel.layerTexts.toMap(),
            deletedLayers = viewModel.deletedLayers.toList(),
            hiddenLayers = viewModel.hiddenLayers.toList(),
            layerStartTimes = viewModel.layerStartTimes,
            layerEndTimes = viewModel.layerEndTimes,
            layerTransforms = viewModel.layerTransforms.toMap(),
            layerKeyframes = viewModel.layerKeyframes.toMap(),
            opacityKeyframes = viewModel.opacityKeyframes.toMap(),
            layerOpacities = viewModel.layerOpacities,
            layerBlendModes = viewModel.layerBlendModes,
            layerEffects = viewModel.layerEffects,
            previewWidthPx = viewModel.previewWidthPx,
            previewHeightPx = viewModel.previewHeightPx,
            timelineDurationSeconds = viewModel.timelineDurationSeconds,
            onClose = { viewModel.showExport = false }
        )
    }

    if (showDurationDialog) {
        AlertDialog(
            onDismissRequest = { showDurationDialog = false },
            title = { Text("Set clip duration") },
            text = {
                Column {
                    Text("Duration in seconds", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = durationInputText,
                        onValueChange = { durationInputText = it },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val layerId = viewModel.selectedLayer
                    val seconds = durationInputText.toFloatOrNull()
                    if (layerId != null && seconds != null && seconds > 0f) {
                        viewModel.pushUndo()
                        val start = viewModel.layerStartTimes[layerId] ?: 0f
                        val updatedEndTimes = viewModel.layerEndTimes.toMutableMap()
                        updatedEndTimes[layerId] = start + seconds
                        viewModel.layerEndTimes = updatedEndTimes
                        // A manually-entered duration is an explicit, intentional
                        // choice — don't let a later media-duration callback
                        // silently override it.
                        if (!viewModel.autoResizedMediaLayers.contains(layerId)) {
                            viewModel.autoResizedMediaLayers.add(layerId)
                        }
                        showDurationDialog = false
                    } else {
                        Toast.makeText(context, "Enter a valid number of seconds", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showDurationDialog = false }) { Text("Cancel") }
            }
        )
    }

    }
}
}
}

