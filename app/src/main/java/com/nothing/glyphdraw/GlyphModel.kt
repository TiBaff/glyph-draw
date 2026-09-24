package com.nothing.glyphdraw

enum class StripType {
    ZONE_C_TOP_RIGHT,  // Правая верхняя дуга (20 светодиодов, 0..19)
    ZONE_A_BOTTOM_LEFT,// Левая нижняя дуга (11 светодиодов, 20..30)
    ZONE_B_TOP_LEFT    // Верхний левый штрих/акцент (5 светодиодов, 31..35)
}

data class GlyphSegmentDef(
    val id: Int,
    val strip: StripType,
    val startAngleDeg: Float,
    val sweepAngleDeg: Float,
    val label: String
)

object GlyphLayoutData {
    const val TOTAL_SEGMENTS = 36

    // 1. Верхняя правая дуга (Zone C): 20 сегментов (Сетка)
    // Огибает блок камер сверху-справа: от -75° (12:30) до +25° (3:30), размах 100°
    val zoneCSegments: List<GlyphSegmentDef> = (0 until 20).map { i ->
        val totalSweep = 100f
        val step = totalSweep / 20f
        val gap = 1.4f
        val start = -75f + i * step
        GlyphSegmentDef(
            id = i, // 0..19
            strip = StripType.ZONE_C_TOP_RIGHT,
            startAngleDeg = start,
            sweepAngleDeg = step - gap,
            label = "C$i"
        )
    }

    // 2. Нижняя левая дуга (Zone A): 11 сегментов (Сетка)
    // Огибает блок камер снизу-слева: от 105° (6:30) до 195° (9:30), размах 90°
    val zoneASegments: List<GlyphSegmentDef> = (0 until 11).map { i ->
        val totalSweep = 90f
        val step = totalSweep / 11f
        val gap = 1.6f
        val start = 105f + i * step
        GlyphSegmentDef(
            id = 20 + i, // 20..30
            strip = StripType.ZONE_A_BOTTOM_LEFT,
            startAngleDeg = start,
            sweepAngleDeg = step - gap,
            label = "A$i"
        )
    }

    // 3. Верхний левый акцент (Zone B): 5 сегментов (Сетка)
    // Находится сверху-слева: от 220° до 255° (около 10:30), размах 35°
    val zoneBSegments: List<GlyphSegmentDef> = (0 until 5).map { i ->
        val totalSweep = 35f
        val step = totalSweep / 5f
        val gap = 1.6f
        val start = 220f + i * step
        GlyphSegmentDef(
            id = 31 + i, // 31..35
            strip = StripType.ZONE_B_TOP_LEFT,
            startAngleDeg = start,
            sweepAngleDeg = step - gap,
            label = "B$i"
        )
    }

    val allSegments: List<GlyphSegmentDef> = zoneCSegments + zoneASegments + zoneBSegments
}
