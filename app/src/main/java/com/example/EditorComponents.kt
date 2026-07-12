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
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clipToBounds
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

@Composable
fun AddElementsMenu(
    onClose: () -> Unit,
    onAddShape: (androidx.compose.ui.graphics.vector.ImageVector?) -> Unit = {},
    onAddMedia: (android.net.Uri) -> Unit = {},
    onAddText: () -> Unit = {},
    onVectorDrawingClick: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf("Shape") }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            onAddMedia(uri)
        }
    }

    Row(modifier = Modifier.fillMaxSize().background(Color(0xFF222634))) {
        // Left side: Tabs & Shapes Grid
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            // Tabs 
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.Top
            ) {
                 MenuTabItem("Text", Icons.Default.TextFields, selected = selectedTab == "Text", onClick = { selectedTab = "Text" })
                 MenuTabItem("Shape", Icons.Default.Category, selected = selectedTab == "Shape", onClick = { selectedTab = "Shape" })
                 MenuTabItem("Media", Icons.Default.Image, selected = selectedTab == "Media", onClick = { selectedTab = "Media" })
                 MenuTabItem("Audio", Icons.Default.Audiotrack, selected = selectedTab == "Audio", onClick = { selectedTab = "Audio" })
                 MenuTabItem("Object / Element", Icons.Default.Dashboard, selected = selectedTab == "Object", onClick = { selectedTab = "Object" }) 
                 MenuTabItem("Template", Icons.Default.WebStories, selected = selectedTab == "Template", onClick = { selectedTab = "Template" })
            }
            // Divider
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(0.1f)))
            
            if (selectedTab == "Text") {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Button(onClick = { onAddText() }) {
                        Text("Add Text Layer")
                    }
                }
            }
            if (selectedTab == "Shape") {
                // Shapes Grid
                Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ShapeIcon(Icons.Default.Circle) { onAddShape(Icons.Default.Circle) }
                        ShapeIcon(Icons.Default.Square) { onAddShape(Icons.Default.Square) }
                        ShapeIcon(Icons.Default.Add) { onAddShape(Icons.Default.Add) }
                        ShapeIcon(null) { onAddShape(null) } // Arc/Crescent placeholder
                        ShapeIcon(Icons.Default.ChangeHistory) { onAddShape(Icons.Default.ChangeHistory) } // Triangle
                        ShapeIcon(Icons.Default.ChatBubble) { onAddShape(Icons.Default.ChatBubble) }
                        ShapeIcon(Icons.Default.WaterDrop) { onAddShape(Icons.Default.WaterDrop) }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ShapeIcon(Icons.Default.PieChart) { onAddShape(Icons.Default.PieChart) }
                        ShapeIcon(Icons.Default.Hexagon) { onAddShape(Icons.Default.Hexagon) }
                        ShapeIcon(Icons.Default.Star) { onAddShape(Icons.Default.Star) }
                        ShapeIcon(Icons.Default.NorthEast) { onAddShape(Icons.Default.NorthEast) }
                        ShapeIcon(Icons.Default.Rectangle) { onAddShape(Icons.Default.Rectangle) } // Trapezoid-ish
                        ShapeIcon(Icons.Default.NightsStay) { onAddShape(Icons.Default.NightsStay) } // Crescent
                        ShapeIcon(Icons.Default.Cloud) { onAddShape(Icons.Default.Cloud) }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ShapeIcon(Icons.Default.Stop) { onAddShape(Icons.Default.Stop) }
                        ShapeIcon(Icons.Default.StarBorder) { onAddShape(Icons.Default.StarBorder) }
                        ShapeIcon(Icons.Default.HorizontalRule) { onAddShape(Icons.Default.HorizontalRule) }
                        ShapeIcon(Icons.Default.ArrowOutward) { onAddShape(Icons.Default.ArrowOutward) }
                        ShapeIcon(Icons.Default.Details) { onAddShape(Icons.Default.Details) }
                        ShapeIcon(Icons.Default.Apps) { onAddShape(Icons.Default.Apps) }
                        ShapeIcon(Icons.Default.Favorite) { onAddShape(Icons.Default.Favorite) }
                    }
                }
            } else if (selectedTab == "Media") {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = { launcher.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16B996))
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Select Image")
                    }
                }
            }
        }
        
        // Right side: Vertical column 
        // Background slightly darker
        Column(
            modifier = Modifier.width(72.dp).fillMaxHeight().background(Color(0xFF1B1D25)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))
            ActionItem("Freehand\nDrawing", Icons.Default.Edit)
            Spacer(Modifier.height(16.dp))
            ActionItem("Vector\nDrawing", Icons.Default.InvertColors, onClick = onVectorDrawingClick)
            Spacer(Modifier.height(16.dp))
            ActionItem("Text", Icons.Default.TextFields, onClick = onAddText)
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Default.Close, 
                contentDescription = "Close", 
                tint = Color.White, 
                modifier = Modifier
                    .padding(16.dp)
                    .size(24.dp)
                    .clickable { onClose() }
            )
        }
    }
}

@Composable
fun MenuTabItem(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean = false, onClick: () -> Unit = {}) {
    val color = if (selected) Color(0xFF16B996) else Color.White.copy(alpha=0.8f)
    Column(
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(title, color = color, fontSize = 10.sp, fontWeight = if(selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun ShapeIcon(icon: androidx.compose.ui.graphics.vector.ImageVector?, onClick: () -> Unit = {}) {
    Box(modifier = Modifier.size(32.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (icon != null) {
            Icon(icon, null, tint = Color.LightGray, modifier = Modifier.size(28.dp))
        } else {
            // Fallback generic shape
            Canvas(modifier = Modifier.size(28.dp)) {
                drawArc(color = Color.LightGray, startAngle = 45f, sweepAngle = 270f, useCenter = false, style = Stroke(width = 8f))
            }
        }
    }
}

@Composable
fun ActionItem(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit = {}) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(title, color = Color.White, fontSize = 9.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 11.sp)
    }
}

@Composable
fun LayerPropertiesMenu(
    onMoveTransformClick: () -> Unit = {},
    onColorFillClick: () -> Unit = {},
    onEffectsClick: () -> Unit = {},
    onBlendingOpacityClick: () -> Unit = {},
    onSplitClick: () -> Unit = {},
    onEditShapeClick: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF1B1D25))) {
        // Top slim row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniToolIcon(Icons.Default.Speed)
            MiniToolIcon(Icons.AutoMirrored.Filled.FormatAlignLeft)
            MiniToolIcon(Icons.Default.ContentCut, onClick = onSplitClick)
            MiniToolIcon(Icons.AutoMirrored.Filled.FormatAlignRight)
            MiniToolIcon(Icons.AutoMirrored.Filled.VolumeOff)
        }
        
        // Grid
        val buttonBg = Color(0xFF262C3A)
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                PropertyBlock(Icons.Default.FormatColorFill, "Color & Fill", buttonBg, Modifier.weight(1f), onClick = onColorFillClick)
                PropertyBlock(Icons.Default.CropSquare, "Border & Shadow", buttonBg, Modifier.weight(1f))
                PropertyBlock(Icons.Default.Layers, "Blending & Opacity", buttonBg, Modifier.weight(1f), onClick = onBlendingOpacityClick)
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                PropertyBlock(Icons.Default.OpenWith, "Move & Transform", buttonBg, Modifier.weight(1f), onClick = onMoveTransformClick)
                PropertyBlock(Icons.Default.Category, "Edit Shape", buttonBg, Modifier.weight(1f), onClick = onEditShapeClick)
                PropertyBlock(Icons.Default.AutoAwesome, "Effects", buttonBg, Modifier.weight(1f), onClick = onEffectsClick)
            }
        }
    }
}

@Composable
fun MiniToolIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit = {}) {
    Box(
        modifier = Modifier.size(48.dp, 36.dp).background(Color(0xFF262C3A), RoundedCornerShape(4.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha=0.6f), modifier = Modifier.size(18.dp))
    }
}

@Composable
fun PropertyBlock(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, bgColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Column(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .height(72.dp)
            .background(bgColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, color = Color.White.copy(alpha=0.6f), fontSize = 10.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable
fun TrackRow(
    icon: @Composable () -> Unit, 
    stripColor: Color, 
    title: String, 
    textColor: Color = Color.Black,
    selected: Boolean = false,
    isHidden: Boolean = false,
    onVisibilityToggle: () -> Unit = {},
    scrollOffset: androidx.compose.ui.unit.Dp = 0.dp,
    startTime: Float = 0f,
    endTime: Float = Float.MAX_VALUE,
    keyframes: List<Float> = emptyList(),
    pxPerSecond: Float = TIMELINE_PIXELS_PER_SECOND,
    onClick: () -> Unit = {},
    onTrimStart: ((deltaSeconds: Float) -> Unit)? = null,
    onTrimEnd: ((deltaSeconds: Float) -> Unit)? = null,
    onTrimGestureStart: (() -> Unit)? = null,
    // Fires while dragging either trim handle, with that handle's current
    // absolute X position on screen (px). Lets the caller auto-scroll the
    // timeline (by nudging the playhead) when the handle nears/passes the
    // edge of the visible viewport, since the timeline itself only scrolls
    // in response to playhead changes — trimming alone never moved it,
    // so a handle dragged past ~a few seconds' worth of pixels would walk
    // off the physical screen with no way to bring it back into view.
    onTrimHandleMoved: ((absoluteXPx: Float) -> Unit)? = null
) {
    if (LocalDeletedLayers.current.contains(title)) return
    val bgColor = if (selected) Color(0xFF2E3246) else Color.Transparent
    // While either trim handle is actively being dragged, show a small
    // floating time readout above that edge — the same "what time am I at"
    // feedback a professional NLE gives instead of making you guess from
    // pixel position alone.
    var trimTooltipSeconds by remember { mutableStateOf<Float?>(null) }
    var trimTooltipIsStart by remember { mutableStateOf(true) }

    Row(modifier = Modifier.fillMaxWidth().height(36.dp).padding(bottom = 2.dp).background(bgColor).clickable(onClick = onClick)) {
        Box(modifier = Modifier.width(64.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                modifier = Modifier
                    .background(Color(0xFF262934), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility, 
                    null, 
                    tint = if(selected) Color(0xFFE25B5B) else Color.White.copy(alpha=0.7f), 
                    modifier = Modifier.size(20.dp).clickable { onVisibilityToggle() }
                )
                Spacer(Modifier.width(6.dp))
                icon()
            }
        }
        
        Box(modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            val stripStart = scrollOffset + (startTime * pxPerSecond).dp
            val actualDuration = if (endTime != Float.MAX_VALUE) (endTime - startTime) else 1000f
            val stripWidth = (actualDuration * pxPerSecond).dp
            val stripEnd = stripStart + stripWidth

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 2.dp)
                    .drawBehind {
                        val startPx = stripStart.toPx()
                        val widthPx = stripWidth.toPx()
                        // draw background and border manually to avoid RenderNode max texture size limits!
                        drawRoundRect(
                            color = stripColor,
                            topLeft = Offset(startPx, 0f),
                            size = Size(widthPx, size.height),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                        if (selected) {
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.9f),
                                topLeft = Offset(startPx, 0f),
                                size = Size(widthPx, size.height),
                                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }
                    }
            ) {
                // Keyframe Diamonds drawing block
                keyframes.forEach { kfTime ->
                    val kfOffset = stripStart + ((kfTime - startTime) * pxPerSecond).dp
                    Box(
                        modifier = Modifier
                            .offset(x = kfOffset - 5.dp, y = 11.dp)
                            .size(10.dp)
                            .rotate(45f)
                            .background(Color.White, RoundedCornerShape(1.dp))
                            .border(1.dp, Color.Black.copy(alpha = 0.3f), RoundedCornerShape(1.dp))
                    )
                }

                // Title and Menu Icon
                Row(
                   modifier = Modifier
                       .offset(x = stripStart)
                       .widthIn(max = stripWidth)
                       .fillMaxHeight()
                       .padding(start=4.dp, end=4.dp),
                   verticalAlignment = Alignment.CenterVertically
                ) {
                   if (selected) {
                       Box(Modifier.width(16.dp).fillMaxHeight().background(Color.Black.copy(0.2f)), contentAlignment=Alignment.Center) {
                          Icon(Icons.Default.ChevronLeft, null, tint=Color.White, modifier=Modifier.size(12.dp))
                       }
                   }
                   Text(title, color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }

                if (!selected) {
                    Box(
                        modifier = Modifier
                            .offset(x = maxOf(stripStart, stripEnd - 20.dp))
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Menu, null, tint = textColor.copy(alpha=0.5f), modifier = Modifier.size(16.dp))
                    }
                }

                // Trim handles are only shown (and draggable) for the selected clip,
                // so casual taps on other tracks can't accidentally resize them.
                if (selected && onTrimStart != null) {
                    // Left edge handle: drag to change the clip's start time.
                    Box(
                        modifier = Modifier
                            .offset(x = stripStart)
                            .fillMaxHeight()
                            .width(16.dp)
                            .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                            .background(Color.Black.copy(alpha = 0.3f))
                            .onGloballyPositioned { coords ->
                                if (trimTooltipSeconds != null && trimTooltipIsStart) {
                                    onTrimHandleMoved?.invoke(coords.positionInRoot().x)
                                }
                            }
                            .pointerInput(title) {
                                detectDragGestures(
                                    onDragStart = {
                                        trimTooltipIsStart = true
                                        trimTooltipSeconds = startTime
                                        onTrimGestureStart?.invoke()
                                    },
                                    onDragEnd = { trimTooltipSeconds = null },
                                    onDragCancel = { trimTooltipSeconds = null }
                                ) { change, dragAmount ->
                                    change.consume()
                                    val deltaSeconds = dragAmount.x / pxPerSecond.dp.toPx()
                                    onTrimStart(deltaSeconds)
                                    trimTooltipSeconds = (trimTooltipSeconds ?: startTime) + deltaSeconds
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Grip dots: the small vertical dot pair is the universal
                        // "drag me" affordance used by every mainstream editor's
                        // trim handles, instead of a single plain bar.
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            repeat(3) {
                                Box(Modifier.size(3.dp).background(Color.White, CircleShape))
                            }
                        }
                    }
                }
                
                if (selected && onTrimEnd != null) {
                    // Right edge handle: drag to change the clip's end time.
                    Box(
                        modifier = Modifier
                            .offset(x = stripEnd - 16.dp)
                            .fillMaxHeight()
                            .width(16.dp)
                            .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                            .background(Color.Black.copy(alpha = 0.3f))
                            .onGloballyPositioned { coords ->
                                if (trimTooltipSeconds != null && !trimTooltipIsStart) {
                                    onTrimHandleMoved?.invoke(coords.positionInRoot().x)
                                }
                            }
                            .pointerInput(title) {
                                detectDragGestures(
                                    onDragStart = {
                                        trimTooltipIsStart = false
                                        trimTooltipSeconds = endTime.takeIf { it != Float.MAX_VALUE } ?: startTime
                                        onTrimGestureStart?.invoke()
                                    },
                                    onDragEnd = { trimTooltipSeconds = null },
                                    onDragCancel = { trimTooltipSeconds = null }
                                ) { change, dragAmount ->
                                    change.consume()
                                    val deltaSeconds = dragAmount.x / pxPerSecond.dp.toPx()
                                    onTrimEnd(deltaSeconds)
                                    trimTooltipSeconds = (trimTooltipSeconds ?: endTime) + deltaSeconds
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            repeat(3) {
                                Box(Modifier.size(3.dp).background(Color.White, CircleShape))
                            }
                        }
                    }
                }

                trimTooltipSeconds?.let { seconds ->
                    val tooltipX = if (trimTooltipIsStart) stripStart else stripEnd
                    Box(
                        modifier = Modifier
                            .offset(x = tooltipX, y = (-28).dp)
                            .background(Color(0xFF1C1E26), RoundedCornerShape(4.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(formatTimecode(seconds), color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

// Formats seconds as mm:ss.f for compact trim tooltips.
fun formatTimecode(totalSeconds: Float): String {
    val clamped = totalSeconds.coerceAtLeast(0f)
    val minutes = (clamped / 60).toInt()
    val seconds = clamped % 60
    return String.format("%d:%04.1f", minutes, seconds)
}

// Formats a ruler major-tick label. Sub-second intervals show one decimal
// (e.g. "0.5s"); whole-second-and-above intervals show mm:ss so long
// projects stay readable once zoomed out.
fun formatRulerLabel(totalSeconds: Float): String {
    val rounded = Math.round(totalSeconds * 10) / 10f
    return if (rounded < 60f && rounded % 1f != 0f) {
        String.format("%.1fs", rounded)
    } else {
        val minutes = (rounded / 60).toInt()
        val seconds = (rounded % 60).toInt()
        if (minutes > 0) String.format("%d:%02d", minutes, seconds) else "${seconds}s"
    }
}

/**
 * Checks which layer's visual bounds contain the given tap offset.
 * Iterates layers in reverse order (top-most rendered first) so overlapping
 * layers are handled correctly — the visually topmost layer wins.
 *
 * This is needed because Compose's `graphicsLayer { scaleX / scaleY }` only
 * affects rendering, not hit-testing.  A scaled-up layer's clickable area
 * remains at its original (small) layout size, so taps on the visual
 * overflow would otherwise miss the layer entirely and trigger the canvas
 * background's deselect handler instead.
 *
 * @return the layerId of the hit layer, or null if the tap is on empty canvas.
 */
fun findHitLayer(
    tapOffset: Offset,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    addedShapes: List<ImageVector?>,
    addedMedia: List<Uri>,
    addedTexts: List<String> = emptyList(),
    layerTexts: Map<String, String> = emptyMap(),
    hiddenLayers: Set<String> = emptySet(),
    layerTransforms: Map<String, LayerTransform>,
    layerKeyframes: Map<String, List<LayerKeyframe>>,
    playheadProgress: Float,
    deletedLayers: Set<String>,
    layerStartTimes: Map<String, Float>,
    layerEndTimes: Map<String, Float>,
    density: Density
): String? {
    val centerX = canvasWidthPx / 2f
    val centerY = canvasHeightPx / 2f

    // Text layers are rendered last in the preview, so check them first for top-most hit selection.
    for (i in addedTexts.indices.reversed()) {
        val fallbackLayerId = "Text ${i + 1}"
        val layerId = addedTexts[i].ifBlank { fallbackLayerId }
        if (layerId in deletedLayers || layerId in hiddenLayers) continue
        val start = layerStartTimes[layerId] ?: 0f
        val end = layerEndTimes[layerId] ?: Float.MAX_VALUE
        if (playheadProgress < start || playheadProgress > end) continue

        val transform = getActiveTransform(layerId, playheadProgress, layerTransforms, layerKeyframes)
        val baseOffsetX = with(density) { (i * 20f - 40f).dp.toPx() }
        val baseOffsetY = with(density) { (i * 20f - 40f).dp.toPx() }
        
        // Approximate the laid-out Compose Text/TextField bounds: 24sp bold text
        // plus 16dp horizontal padding and a minimum selectable box. This keeps
        // tap-selection reliable for short, long, and multi-line text layers.
        val text = layerTexts[layerId].orEmpty().ifEmpty { "New Text" }
        val longestLineLength = text.lineSequence().maxOfOrNull { it.length } ?: 0
        val lineCount = text.lineSequence().count().coerceAtLeast(1)
        val minWidthPx = with(density) { 120.dp.toPx() }
        val minHeightPx = with(density) { 56.dp.toPx() }
        val estimatedWidthPx = with(density) { (longestLineLength * 14).dp.toPx() + 32.dp.toPx() }
        val estimatedHeightPx = with(density) { (lineCount * 32).dp.toPx() + 24.dp.toPx() }
        val baseWidthPx = maxOf(minWidthPx, estimatedWidthPx)
        val baseHeightPx = maxOf(minHeightPx, estimatedHeightPx)

        if (isPointInVisualBounds(tapOffset, centerX, centerY, baseOffsetX, baseOffsetY, transform, baseWidthPx, baseHeightPx)) {
            return layerId
        }
    }

    // Media layers are rendered under text but above shapes (in reverse so the last-added/topmost wins).
    for (i in addedMedia.indices.reversed()) {
        val layerId = "Media ${i + 1}"
        if (layerId in deletedLayers || layerId in hiddenLayers) continue
        val start = layerStartTimes[layerId] ?: 0f
        val end = layerEndTimes[layerId] ?: Float.MAX_VALUE
        if (playheadProgress < start || playheadProgress > end) continue

        val transform = getActiveTransform(layerId, playheadProgress, layerTransforms, layerKeyframes)
        val baseOffsetX = with(density) { (i * 20f - 40f).dp.toPx() }
        val baseOffsetY = with(density) { (i * 20f - 40f).dp.toPx() }
        val baseSizePx = with(density) { 160.dp.toPx() }

        if (isPointInVisualBounds(tapOffset, centerX, centerY, baseOffsetX, baseOffsetY, transform, baseSizePx, baseSizePx)) {
            return layerId
        }
    }

    // Shape layers
    for (i in addedShapes.indices.reversed()) {
        val layerId = "Shape ${i + 1}"
        if (layerId in deletedLayers || layerId in hiddenLayers) continue
        val start = layerStartTimes[layerId] ?: 0f
        val end = layerEndTimes[layerId] ?: Float.MAX_VALUE
        if (playheadProgress < start || playheadProgress > end) continue

        val transform = getActiveTransform(layerId, playheadProgress, layerTransforms, layerKeyframes)
        val baseOffsetX = with(density) { (i * 20f - 40f).dp.toPx() }
        val baseOffsetY = with(density) { (i * 20f - 40f).dp.toPx() }
        val baseSizePx = if (layerId == "Shape 2") with(density) { 120.dp.toPx() } else with(density) { 80.dp.toPx() }

        if (isPointInVisualBounds(tapOffset, centerX, centerY, baseOffsetX, baseOffsetY, transform, baseSizePx, baseSizePx)) {
            return layerId
        }
    }

    return null
}

/**
 * Checks whether a point (in canvas coordinates) falls within a layer's
 * visual bounds, accounting for the full `graphicsLayer` transform:
 *  - translation (base offset + user offset)
 *  - scale
 *  - rotation
 *
 * Algorithm:
 *  1. Compute the layer's visual center in canvas coordinates.
 *  2. Translate the test point relative to that center.
 *  3. Rotate the relative point by **-rotation** to undo the layer's rotation.
 *  4. Check if the unrotated point is inside the scaled rectangle.
 */
fun isPointInVisualBounds(
    point: Offset,
    canvasCenterX: Float,
    canvasCenterY: Float,
    baseOffsetX: Float,
    baseOffsetY: Float,
    transform: LayerTransform,
    baseWidthPx: Float,
    baseHeightPx: Float
): Boolean {
    // Layer center in canvas coordinates
    val layerCenterX = canvasCenterX + baseOffsetX + transform.offsetX
    val layerCenterY = canvasCenterY + baseOffsetY + transform.offsetY

    // Scaled half-dimensions (abs handles negative scale = flip)
    val halfW = baseWidthPx * kotlin.math.abs(transform.scaleX) / 2f
    val halfH = baseHeightPx * kotlin.math.abs(transform.scaleY) / 2f

    // Translate point relative to layer center
    val dx = point.x - layerCenterX
    val dy = point.y - layerCenterY

    // Rotate by -rotation to get layer-local coordinates
    val angleRad = -Math.toRadians(transform.rotation.toDouble())
    val cosA = kotlin.math.cos(angleRad).toFloat()
    val sinA = kotlin.math.sin(angleRad).toFloat()
    val localX = dx * cosA - dy * sinA
    val localY = dx * sinA + dy * cosA

    // Check if local point is within the scaled rectangle
    return localX >= -halfW && localX <= halfW && localY >= -halfH && localY <= halfH
}