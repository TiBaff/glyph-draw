package com.nothing.glyphdraw

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    GlyphDrawScreen(glyphController = glyphController)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        glyphController.release()
    }
}

// Using colors from Theme.kt
private val CardBackground = Color(0xFF101010)

@Composable
fun GlyphDrawScreen(glyphController: GlyphController) {
    var activeSegments by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var undoStack by remember { mutableStateOf<List<Set<Int>>>(listOf(emptySet())) }
    var redoStack by remember { mutableStateOf<List<Set<Int>>>(emptyList()) }
    var brightness by remember { mutableFloatStateOf(1.0f) }

    fun pushState(newState: Set<Int>) {
        if (newState == activeSegments) return
        undoStack = undoStack + listOf(activeSegments)
        redoStack = emptyList()
        activeSegments = newState
        glyphController.updateHardware(activeSegments, brightness)
    }

    fun undo() {
        if (undoStack.size > 1) {
            val previous = undoStack.last()
            undoStack = undoStack.dropLast(1)
            redoStack = redoStack + listOf(activeSegments)
            activeSegments = previous
            glyphController.updateHardware(activeSegments, brightness)
        } else if (undoStack.size == 1 && activeSegments.isNotEmpty()) {
            redoStack = redoStack + listOf(activeSegments)
            activeSegments = emptySet()
            glyphController.updateHardware(activeSegments, brightness)
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.last()
            redoStack = redoStack.dropLast(1)
            undoStack = undoStack + listOf(activeSegments)
            activeSegments = next
            glyphController.updateHardware(activeSegments, brightness)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "GLYPH DRAW",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "UNDO",
                    color = if (undoStack.size > 1 || activeSegments.isNotEmpty()) Color.White else Color.DarkGray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(enabled = undoStack.size > 1 || activeSegments.isNotEmpty()) { undo() }
                )
                Text(
                    text = "REDO",
                    color = if (redoStack.isNotEmpty()) Color.White else Color.DarkGray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(enabled = redoStack.isNotEmpty()) { redo() }
                )
            }
        }

        // Main Drawing Canvas for Phone (3a) Pro
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            GlyphDrawingCanvas(
                activeSegments = activeSegments,
                brightness = brightness,
                onSegmentsDrawn = { newSet ->
                    pushState(newSet)
                }
            )
        }

        // Control Panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { pushState(emptySet()) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Очистить", color = Color.White, fontSize = 13.sp)
                }

                Button(
                    onClick = { pushState((0 until GlyphLayoutData.TOTAL_SEGMENTS).toSet()) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Все ВКЛ", color = Color.White, fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        val all = (0 until GlyphLayoutData.TOTAL_SEGMENTS).toSet()
                        pushState(all - activeSegments)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Инверсия", color = Color.White, fontSize = 13.sp)
                }
            }

            // Brightness Slider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBackground, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Яркость",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Slider(
                    value = brightness,
                    onValueChange = {
                        brightness = it
                        glyphController.updateHardware(activeSegments, brightness)
                    },
                    valueRange = 0.05f..1.0f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color(0xFF333333)
                    )
                )
                Text(
                    text = "${(brightness * 100).roundToInt()}%",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(42.dp)
                )
            }
        }
    }
}

@Composable
fun GlyphDrawingCanvas(
    activeSegments: Set<Int>,
    brightness: Float,
    onSegmentsDrawn: (Set<Int>) -> Unit
) {
    var canvasCenter by remember { mutableStateOf(Offset.Zero) }
    var trackRadius by remember { mutableFloatStateOf(0f) }
    var trackThickness by remember { mutableFloatStateOf(0f) }

    fun findSegmentAtPoint(touch: Offset): Int? {
        val dx = touch.x - canvasCenter.x
        val dy = touch.y - canvasCenter.y
        val dist = sqrt(dx * dx + dy * dy)

        val innerR = trackRadius - trackThickness / 2f - 24f
        val outerR = trackRadius + trackThickness / 2f + 24f
        if (dist !in innerR..outerR) return null

        var angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (angleDeg < 0) angleDeg += 360f

        for (seg in GlyphLayoutData.allSegments) {
            var start = seg.startAngleDeg
            while (start < 0) start += 360f
            start %= 360f
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
            .padding(8.dp)
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

            // 1. Draw Phone (3a) Pro Camera Module
            drawCircle(
                color = Color(0xFF141414),
                radius = cameraRadius,
                center = center
            )
            drawCircle(
                color = Color(0xFF242424),
                radius = cameraRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // Top camera lens (50MP)
            val lensRadius = cameraRadius * 0.28f
            val topLensCenter = Offset(center.x, center.y - cameraRadius * 0.38f)
            drawCircle(color = Color(0xFF080808), radius = lensRadius, center = topLensCenter)
            drawCircle(color = Color(0xFF2C2C2C), radius = lensRadius, center = topLensCenter, style = Stroke(2.dp.toPx()))

            // Bottom-left camera lens (8MP)
            val blLensCenter = Offset(center.x - cameraRadius * 0.35f, center.y + cameraRadius * 0.25f)
            drawCircle(color = Color(0xFF080808), radius = lensRadius * 0.85f, center = blLensCenter)
            drawCircle(color = Color(0xFF2C2C2C), radius = lensRadius * 0.85f, center = blLensCenter, style = Stroke(2.dp.toPx()))

            // Bottom-right telephoto / periscope
            val periWidth = cameraRadius * 0.54f
            val periHeight = cameraRadius * 0.36f
            val periLeft = center.x + cameraRadius * 0.06f
            val periTop = center.y + cameraRadius * 0.08f
            drawRoundRect(
                color = Color(0xFF0E0E0E),
                topLeft = Offset(periLeft, periTop),
                size = Size(periWidth, periHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx())
            )
            drawRoundRect(
                color = Color(0xFF2C2C2C),
                topLeft = Offset(periLeft, periTop),
                size = Size(periWidth, periHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                style = Stroke(1.5.dp.toPx())
            )

            // 2. Draw Glyph Segments
            val arcRect = Rect(
                center.x - trackRadius,
                center.y - trackRadius,
                center.x + trackRadius,
                center.y + trackRadius
            )

            GlyphLayoutData.allSegments.forEach { seg ->
                val isOn = seg.id in currentDisplaySegments

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
