package com.nothing.glyphdraw

enum class StripType {
    RIGHT_ARC,   // Правая дуга
    LEFT_ARC,    // Левая дуга
    SLASH        // Нижний слэш (снизу-слева)
}

data class GlyphSegmentDef(
    val id: Int,
    val strip: StripType,
    val startAngleDeg: Float,
    val sweepAngleDeg: Float,
    val label: String
)

object GlyphLayoutData {
    // Делаем чёткую видимую СЕТКУ сегментов:
    // 1. Правая дуга: разбита на 10 отчётливых сегментов (Сетка)
    val rightArcSegments: List<GlyphSegmentDef> = (0 until 10).map { i ->
        val totalSweep = 90f
        val step = totalSweep / 10f
        val gap = 1.6f
        val start = -45f + i * step
        GlyphSegmentDef(
            id = i, // 0..9
            strip = StripType.RIGHT_ARC,
            startAngleDeg = start,
            sweepAngleDeg = step - gap,
            label = "R$i"
        )
    }

    // 2. Левая дуга: разбита на 6 сегментов (Сетка)
    val leftArcSegments: List<GlyphSegmentDef> = (0 until 6).map { i ->
        val totalSweep = 70f
        val step = totalSweep / 6f
        val gap = 1.8f
        val start = 145f + i * step
        GlyphSegmentDef(
            id = 10 + i, // 10..15
            strip = StripType.LEFT_ARC,
            startAngleDeg = start,
            sweepAngleDeg = step - gap,
            label = "L$i"
        )
    }

    // 3. Нижний слэш (снизу-слева, 7-8 часов): разбит на 4 сегмента (Сетка)
    val slashSegments: List<GlyphSegmentDef> = (0 until 4).map { i ->
        val totalSweep = 36f
        val step = totalSweep / 4f
        val gap = 1.8f
        val start = 105f + i * step
        GlyphSegmentDef(
            id = 16 + i, // 16..19
            strip = StripType.SLASH,
            startAngleDeg = start,
            sweepAngleDeg = step - gap,
            label = "S$i"
        )
    }

    val allSegments: List<GlyphSegmentDef> = rightArcSegments + leftArcSegments + slashSegments
    const val TOTAL_SEGMENTS = 20
}
