package com.nothing.glyphdraw

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

class MainActivity : ComponentActivity() {
    private lateinit var glyphController: GlyphController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        glyphController = GlyphController(this)

        setContent {
            GlyphDrawTheme {
                GlyphDrawScreen(
                    onUpdateHardware = { activeSegments, brightness ->
                        glyphController.updateHardware(activeSegments, brightness)
                    },
                    onBackPress = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        glyphController.release()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlyphDrawScreen(
    onUpdateHardware: (Set<Int>, Float) -> Unit,
    onBackPress: () -> Unit
) {
    // Current painted segments (0..33)
    var activeSegments by remember { mutableStateOf(setOf<Int>()) }

    // History stacks for Undo / Redo
    var undoStack by remember { mutableStateOf(listOf<Set<Int>>()) }
    var redoStack by remember { mutableStateOf(listOf<Set<Int>>()) }

    // Brightness 0f .. 1f
    var brightness by remember { mutableFloatStateOf(1.0f) }

    var showMenu by remember { mutableStateOf(false) }

    // Notify hardware whenever state changes
    LaunchedEffect(activeSegments, brightness) {
        onUpdateHardware(activeSegments, brightness)
    }

    fun pushState(newState: Set<Int>) {
        if (newState != activeSegments) {
            undoStack = undoStack + listOf(activeSegments)
            redoStack = emptyList()
            activeSegments = newState
        }
    }

    fun handleUndo() {
        if (undoStack.isNotEmpty()) {
            val prev = undoStack.last()
            undoStack = undoStack.dropLast(1)
            redoStack = redoStack + listOf(activeSegments)
            activeSegments = prev
        }
    }

    fun handleRedo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.last()
            redoStack = redoStack.dropLast(1)
            undoStack = undoStack + listOf(activeSegments)
            activeSegments = next
        }
    }

    Scaffold(
        containerColor = AmoledBlack,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AmoledBlack),
                navigationIcon = {
                    IconButton(onClick = onBackPress) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                title = {
                    Text(
                        text = "GLYPH DRAW",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = GoogleSans,
                        letterSpacing = 2.sp
                    )
                },
                actions = {
                    // Undo
                    IconButton(
                        onClick = { handleUndo() },
                        enabled = undoStack.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (undoStack.isNotEmpty()) Color.White else Color(0xFF444444)
                        )
                    }
                    // Redo
                    IconButton(
                        onClick = { handleRedo() },
                        enabled = redoStack.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            tint = if (redoStack.isNotEmpty()) Color.White else Color(0xFF444444)
                        )
                    }
                    // Menu
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More",
                            tint = Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(Color(0xFF1E1E1E))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Очистить все", color = Color.White, fontFamily = GoogleSans) },
                            onClick = {
                                pushState(emptySet())
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Включить все", color = Color.White, fontFamily = GoogleSans) },
                            onClick = {
                                pushState((0 until GlyphLayoutData.TOTAL_SEGMENTS).toSet())
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Инвертировать", color = Color.White, fontFamily = GoogleSans) },
                            onClick = {
                                val all = (0 until GlyphLayoutData.TOTAL_SEGMENTS).toSet()
                                pushState(all - activeSegments)
                                showMenu = false
                            }
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Interactive 2D Glyph Drawing Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                GlyphInteractiveCanvas(
                    activeSegments = activeSegments,
                    brightness = brightness,
                    onSegmentToggled = { segId ->
                        val updated = if (segId in activeSegments) {
                            activeSegments - segId
                        } else {
                            activeSegments + segId
                        }
                        pushState(updated)
                    },
                    onSegmentsDrawn = { newActive ->
                        pushState(newActive)
                    }
                )
            }

            // Bottom Controls (Material You style on pure black)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Quick Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { pushState(emptySet()) },
                        colors = ButtonDefaults.buttonColors(containerColor = ButtonDarkGray),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Очистить", color = Color.White, fontSize = 14.sp, fontFamily = GoogleSans)
                    }
                    Button(
                        onClick = { pushState((0 until GlyphLayoutData.TOTAL_SEGMENTS).toSet()) },
                        colors = ButtonDefaults.buttonColors(containerColor = ButtonDarkGray),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Все ВКЛ", color = Color.White, fontSize = 14.sp, fontFamily = GoogleSans)
                    }
                    Button(
                        onClick = {
                            val all = (0 until GlyphLayoutData.TOTAL_SEGMENTS).toSet()
                            pushState(all - activeSegments)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ButtonDarkGray),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Инверсия", color = Color.White, fontSize = 14.sp, fontFamily = GoogleSans)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Brightness Slider Card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262626)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.BrightnessMedium,
                            contentDescription = "Brightness",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Slider(
                            value = brightness,
                            onValueChange = { brightness = it },
                            valueRange = 0.05f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color(0xFF333333)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "${(brightness * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = GoogleSans,
                            modifier = Modifier.width(42.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GlyphInteractiveCanvas(
    activeSegments: Set<Int>,
    brightness: Float,
    onSegmentToggled: (Int) -> Unit,
    onSegmentsDrawn: (Set<Int>) -> Unit
) {
    var canvasCenter by remember { mutableStateOf(Offset.Zero) }
    var trackRadius by remember { mutableFloatStateOf(0f) }
    var trackThickness by remember { mutableFloatStateOf(0f) }

    // Helper to test if touch coordinate falls inside a segment
    fun findSegmentAtPoint(touch: Offset): Int? {
        val dx = touch.x - canvasCenter.x
        val dy = touch.y - canvasCenter.y
        val dist = sqrt(dx * dx + dy * dy)

        // Check if within track radius
        val innerR = trackRadius - trackThickness / 2f - 18f
        val outerR = trackRadius + trackThickness / 2f + 18f
        if (dist !in innerR..outerR) return null

        // Convert angle to degrees 0..360
        var angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (angleDeg < 0) angleDeg += 360f

        // Check each segment
        for (seg in GlyphLayoutData.allSegments) {
            var start = seg.startAngleDeg
            if (start < 0) start += 360f
            val end = (start + seg.sweepAngleDeg) % 360f

            val inAngle = if (start <= end) {
                angleDeg in start..end
            } else {
                angleDeg >= start || angleDeg <= end
            }

            if (inAngle) return seg.id
        }
        return null
    }

    var tempDrawState by remember { mutableStateOf<Set<Int>?>(null) }
    var drawModeIsAdd by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(16.dp)
            .pointerInput(activeSegments) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val hit = findSegmentAtPoint(offset)
                        if (hit != null) {
                            val willAdd = hit !in activeSegments
                            drawModeIsAdd = willAdd
                            tempDrawState = if (willAdd) activeSegments + hit else activeSegments - hit
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val hit = findSegmentAtPoint(change.position)
                        if (hit != null && tempDrawState != null) {
                            tempDrawState = if (drawModeIsAdd) {
                                tempDrawState!! + hit
                            } else {
                                tempDrawState!! - hit
                            }
                        }
                    },
                    onDragEnd = {
                        tempDrawState?.let { onSegmentsDrawn(it) }
                        tempDrawState = null
                    },
                    onDragCancel = {
                        tempDrawState = null
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val currentDisplaySegments = tempDrawState ?: activeSegments

        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            canvasCenter = center

            val baseRadius = size.minDimension * 0.40f
            val cameraRadius = baseRadius * 0.72f
            trackRadius = baseRadius * 0.94f
            trackThickness = 22.dp.toPx()

            // 1. Draw 2D Front-Facing Camera Module (Nothing Phone 3a Pro style)
            // Outer circular island
            drawCircle(
                color = Color(0xFF141414),
                radius = cameraRadius,
                center = center
            )
            // Outer border
            drawCircle(
                color = Color(0xFF242424),
                radius = cameraRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
            // Subtle concentric decorative ring
            drawCircle(
                color = Color(0xFF1A1A1A),
                radius = cameraRadius * 0.88f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            // Left Pill: Twin camera cutouts
            val pillWidth = cameraRadius * 0.65f
            val pillHeight = cameraRadius * 1.15f
            val pillLeft = center.x - cameraRadius * 0.68f
            val pillTop = center.y - pillHeight / 2f
            drawRoundRect(
                color = Color(0xFF0D0D0D),
                topLeft = Offset(pillLeft, pillTop),
                size = Size(pillWidth, pillHeight),
                cornerRadius = CornerRadius(pillWidth / 2f, pillWidth / 2f)
            )

            // Top camera lens inside pill
            val lens1Center = Offset(pillLeft + pillWidth / 2f, pillTop + pillHeight * 0.30f)
            val lensRadius = pillWidth * 0.34f
            drawCircle(color = Color(0xFF050505), radius = lensRadius, center = lens1Center)
            drawCircle(color = Color(0xFF2C2C2C), radius = lensRadius, center = lens1Center, style = Stroke(2.dp.toPx()))
            drawCircle(color = Color(0xFF111111), radius = lensRadius * 0.6f, center = lens1Center)

            // Bottom camera lens inside pill
            val lens2Center = Offset(pillLeft + pillWidth / 2f, pillTop + pillHeight * 0.70f)
            drawCircle(color = Color(0xFF050505), radius = lensRadius, center = lens2Center)
            drawCircle(color = Color(0xFF2C2C2C), radius = lensRadius, center = lens2Center, style = Stroke(2.dp.toPx()))
            drawCircle(color = Color(0xFF111111), radius = lensRadius * 0.6f, center = lens2Center)

            // Right Sensor / Flash Rectangles
            val sensorLeft = center.x + cameraRadius * 0.05f
            val sensorTop = center.y - cameraRadius * 0.35f
            val sensorWidth = cameraRadius * 0.50f
            val sensorHeight = cameraRadius * 0.70f
            drawRoundRect(
                color = Color(0xFF111111),
                topLeft = Offset(sensorLeft, sensorTop),
                size = Size(sensorWidth, sensorHeight),
                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
            )
            // Sensor aperture
            drawCircle(
                color = Color(0xFF080808),
                radius = sensorWidth * 0.28f,
                center = Offset(sensorLeft + sensorWidth / 2f, sensorTop + sensorHeight / 2f)
            )
            drawCircle(
                color = Color(0xFF222222),
                radius = sensorWidth * 0.28f,
                center = Offset(sensorLeft + sensorWidth / 2f, sensorTop + sensorHeight / 2f),
                style = Stroke(1.5.dp.toPx())
            )

            // 2. Draw Glyph Segments along curved tracks in Block Blast grid style
            val arcRect = Rect(
                center.x - trackRadius,
                center.y - trackRadius,
                center.x + trackRadius,
                center.y + trackRadius
            )

            GlyphLayoutData.allSegments.forEach { seg ->
                val isOn = seg.id in currentDisplaySegments

                // Base arc segment (off state: dark grid cell)
                drawArc(
                    color = if (isOn) SegmentOnColor else SegmentOffColor,
                    startAngle = seg.startAngleDeg,
                    sweepAngle = seg.sweepAngleDeg,
                    useCenter = false,
                    topLeft = arcRect.topLeft,
                    size = arcRect.size,
                    style = Stroke(
                        width = trackThickness,
                        cap = StrokeCap.Round
                    )
                )

                // Grid cell border
                drawArc(
                    color = if (isOn) Color.White.copy(alpha = 0.9f) else SegmentOffBorder,
                    startAngle = seg.startAngleDeg,
                    sweepAngle = seg.sweepAngleDeg,
                    useCenter = false,
                    topLeft = arcRect.topLeft,
                    size = arcRect.size,
                    style = Stroke(
                        width = if (isOn) 2.5.dp.toPx() else 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )

                // Neon glow aura when ON
                if (isOn) {
                    drawArc(
                        color = Color.White.copy(alpha = 0.35f * brightness),
                        startAngle = seg.startAngleDeg - 1.5f,
                        sweepAngle = seg.sweepAngleDeg + 3f,
                        useCenter = false,
                        topLeft = arcRect.topLeft,
                        size = arcRect.size,
                        style = Stroke(
                            width = trackThickness + 8.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    )
                }
            }
        }
    }
}
