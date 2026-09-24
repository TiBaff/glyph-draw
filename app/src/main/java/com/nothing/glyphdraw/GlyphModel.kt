package com.nothing.glyphdraw

enum class StripType {
    RIGHT_ARC,   // Правая дуга (около 3 часов)
    LEFT_ARC,    // Левая дуга (около 9 часов)
    SLASH        // Нижний слэш (около 7-8 часов, снизу-слева)
}

data class GlyphSegmentDef(
    val id: Int,
    val strip: StripType,
    val startAngleDeg: Float,
    val sweepAngleDeg: Float
)

object GlyphLayoutData {
    const val TOTAL_SEGMENTS = 3

    // 3 зоны Nothing Phone (3a) Pro в точном соответствии с физической крышкой:
    // 1. Правая дуга (Right Arc): от -45° до +45° (315° .. 45°) вокруг 3 часов
    val rightArc = GlyphSegmentDef(
        id = 2, // Channel 2
        strip = StripType.RIGHT_ARC,
        startAngleDeg = -45f,
        sweepAngleDeg = 90f
    )

    // 2. Левая дуга (Left Arc): от 150° до 210° вокруг 9 часов
    val leftArc = GlyphSegmentDef(
        id = 0, // Channel 0
        strip = StripType.LEFT_ARC,
        startAngleDeg = 150f,
        sweepAngleDeg = 60f
    )

    // 3. Нижний слэш (Slash): от 105° до 138° снизу-слева (на 7-8 часов)
    // Сверху НИЧЕГО НЕТ!
    val slash = GlyphSegmentDef(
        id = 1, // Channel 1
        strip = StripType.SLASH,
        startAngleDeg = 105f,
        sweepAngleDeg = 33f
    )

    val allSegments = listOf(leftArc, slash, rightArc)
}
