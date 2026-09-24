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
import androidx.compose.foundation.shape.CircleShape
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
import kotlinx.coroutines.delay
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

private val CardBackground = Color(0xFF141414)
private val GridBorderColor = Color(0xFF444444)

@Composable
fun GlyphDrawScreen(glyphController: GlyphController) {
    var activeSegments by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var undoStack by remember { mutableStateOf<List<Set<Int>>>(listOf(emptySet())) }
    var redoStack by remember { mutableStateOf<List<Set<Int>>>(emptyList()) }
    var brightness by remember { mutableFloatStateOf(1.0f) }

    // Channel Inspector State
    var isTestMode by remember { mutableStateOf(false) }
    var testChannel by remember { mutableIntStateOf(0) }
    var isAutoRunning by remember { mutableStateOf(false) }

    LaunchedEffect(isAutoRunning, testChannel) {
        if (isAutoRunning) {
            glyphController.sendSingleChannel(testChannel, brightness)
            delay(450)
            testChannel = (testChannel + 1) % 36
        }
    }

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
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "GLYPH DRAW",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mode Toggle Button
                Surface(
                    color = if (isTestMode) Color.White else CardBackground,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable {
                        isTestMode = !isTestMode
                        isAutoRunning = false
                        if (!isTestMode) {
                            glyphController.updateHardware(activeSegments, brightness)
                        }
                    }
                ) {
                    Text(
                        text = if (isTestMode) "ТЕСТ КАНАЛОВ" else "РЕЖИМ РИСОВАНИЯ",
                        color = if (isTestMode) Color.Black else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = "UNDO",
                    color = if (undoStack.size > 1 || activeSegments.isNotEmpty()) Color.White else Color.DarkGray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(enabled = undoStack.size > 1 || activeSegments.isNotEmpty()) { undo() }
                )
                Text(
                    text = "REDO",
                    color = if (redoStack.isNotEmpty()) Color.White else Color.DarkGray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(enabled = redoStack.isNotEmpty()) { redo() }
                )
            }
        }

        // Main Visual Canvas with Clear Segmented Grid
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            GlyphDrawingCanvas(
                activeSegments = activeSegments,
                brightness = brightness,
                highlightChannel = if (isTestMode) testChannel else null,
                onSegmentsDrawn = { newSet ->
                    if (!isTestMode) {
                        pushState(newSet)
                    }
                }
            )
        }

        if (isTestMode) {
            // Channel Inspector UI (Диагностика физических светодиодов)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBackground, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Канал: #$testChannel",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Button(
                        onClick = { isAutoRunning = !isAutoRunning },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAutoRunning) Color(0xFFD32F2F) else Color(0xFF2E7D32)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isAutoRunning) "СТОП" else "АВТО-ТЕСТ ▶",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Stepper Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            isAutoRunning = false
                            testChannel = (testChannel - 1 + 36) % 36
                            glyphController.sendSingleChannel(testChannel, brightness)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF242424)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("◀", color = Color.White, fontSize = 16.sp)
                    }

                    Slider(
                        value = testChannel.toFloat(),
                        onValueChange = {
                            isAutoRunning = false
                            testChannel = it.toInt()
                            glyphController.sendSingleChannel(testChannel, brightness)
                        },
                        valueRange = 0f..35f,
                        steps = 34,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color(0xFF333333)
                        )
                    )

                    Button(
                        onClick = {
                            isAutoRunning = false
                            testChannel = (testChannel + 1) % 36
                            glyphController.sendSingleChannel(testChannel, brightness)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF242424)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("▶", color = Color.White, fontSize = 16.sp)
                    }
                }
            }
        } else {
            // Standard Control Panel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Очистить", color = Color.White, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { pushState((0 until GlyphLayoutData.TOTAL_SEGMENTS).toSet()) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = CardBackground),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Все ВКЛ", color = Color.White, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val all = (0 until GlyphLayoutData.TOTAL_SEGMENTS).toSet()
                            pushState(all - activeSegments)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = CardBackground),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Инверсия", color = Color.White, fontSize = 12.sp)
                    }
                }

                // Brightness Slider
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardBackground, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Яркость",
                        color = Color.White,
                        fontSize = 12.sp,
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
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(38.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun GlyphDrawingCanvas(
    activeSegments: Set<Int>,
    brightness: Float,
    highlightChannel: Int? = null,
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
            .padding(6.dp)
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
            trackRadius = baseRadius * 0.96f
            trackThickness = 22.dp.toPx()

            // 1. Phone (3a) Pro Camera Puck
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

            // Bottom-right periscope/telephoto
            val periWidth = cameraRadius * 0.54f
            val periHeight = cameraRadius * 0.36f
            val periLeft = center.x + cameraRadius * 0.06f
            val periTop = center.y + cameraRadius * 0.08f
            drawRoundRect(
                color = Color(0xFF0E0E0E),
                topLeft = Offset(periLeft, periTop),
                size = Size(periWidth, periHeight),
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
            )
            drawRoundRect(
                color = Color(0xFF2C2C2C),
                topLeft = Offset(periLeft, periTop),
                size = Size(periWidth, periHeight),
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                style = Stroke(1.5.dp.toPx())
            )

            // 2. Draw 3 Physical Glyph Strips with Clear Visible SEGMENTED GRID (Сетка)
            val arcRect = Rect(
                center.x - trackRadius,
                center.y - trackRadius,
                center.x + trackRadius,
                center.y + trackRadius
            )

            GlyphLayoutData.allSegments.forEach { seg ->
                val isHighlighted = highlightChannel != null && seg.id == highlightChannel
                val isOn = seg.id in currentDisplaySegments || isHighlighted

                // Segment base background (cell in grid)
                drawArc(
                    color = if (isOn) SegmentOnColor else Color(0xFF181818),
                    startAngle = seg.startAngleDeg,
                    sweepAngle = seg.sweepAngleDeg,
                    useCenter = false,
                    topLeft = arcRect.topLeft,
                    size = arcRect.size,
                    style = Stroke(
                        width = trackThickness,
                        cap = StrokeCap.Butt // Butt shows clear sharp rectangular grid cells!
                    )
                )

                // Visible Grid Cell Borders (СЕТКА)
                drawArc(
                    color = if (isOn) Color.White else GridBorderColor,
                    startAngle = seg.startAngleDeg,
                    sweepAngle = seg.sweepAngleDeg,
                    useCenter = false,
                    topLeft = arcRect.topLeft,
                    size = arcRect.size,
                    style = Stroke(
                        width = if (isOn) 2.5.dp.toPx() else 1.2.dp.toPx(),
                        cap = StrokeCap.Butt
                    )
                )

                // Glow effect when ON
                if (isOn) {
                    drawArc(
                        color = Color.White.copy(alpha = 0.35f * brightness),
                        startAngle = seg.startAngleDeg,
                        sweepAngle = seg.sweepAngleDeg,
                        useCenter = false,
                        topLeft = arcRect.topLeft,
                        size = arcRect.size,
                        style = Stroke(
                            width = trackThickness + 8.dp.toPx(),
                            cap = StrokeCap.Butt
                        )
                    )
                }
            }
        }
    }
}
