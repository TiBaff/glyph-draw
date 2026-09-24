package com.nothing.glyphdraw

enum class StripType {
    ZONE_C_RIGHT_ARC,   // C1 - C20: Right Arc (20 segments, ArrayIndex 0..19)
    ZONE_A_LEFT_ARC,    // A1 - A11: Left Arc (11 segments, ArrayIndex 20..30)
    ZONE_B_BOTTOM_SLASH // B1 - B5:  Bottom-Left Slash (5 segments, ArrayIndex 31..35)
}

data class GlyphSegmentDef(
    val id: Int,
    val strip: StripType,
    val startAngleDeg: Float,
    val sweepAngleDeg: Float
)

object GlyphLayoutData {
    const val TOTAL_SEGMENTS = 36

    // Official Nothing Phone (3a) Pro table:
    // C1..C20: C_1 is bottom-right/lower end, C_20 is top-right/upper end (Indices 0..19)
    val zoneCSegments: List<GlyphSegmentDef> = (0 until 20).map { i ->
        val totalSweep = 100f
        val step = totalSweep / 20f
        val start = 50f - (i + 1) * step
        GlyphSegmentDef(
            id = i,
            strip = StripType.ZONE_C_RIGHT_ARC,
            startAngleDeg = start,
            sweepAngleDeg = step * 0.80f
        )
    }

    // A1..A11: A_1 is top, A_11 is bottom (Indices 20..30)
    val zoneASegments: List<GlyphSegmentDef> = (0 until 11).map { i ->
        val totalSweep = 90f
        val step = totalSweep / 11f
        val start = 135f + i * step
        GlyphSegmentDef(
            id = 20 + i,
            strip = StripType.ZONE_A_LEFT_ARC,
            startAngleDeg = start,
            sweepAngleDeg = step * 0.80f
        )
    }

    // B1..B5: B_1 is bottom-right, B_5 is top-left (Indices 31..35)
    val zoneBSegments: List<GlyphSegmentDef> = (0 until 5).map { i ->
        val totalSweep = 45f
        val step = totalSweep / 5f
        val start = 275f - (i + 1) * step
        GlyphSegmentDef(
            id = 31 + i,
            strip = StripType.ZONE_B_BOTTOM_SLASH,
            startAngleDeg = start,
            sweepAngleDeg = step * 0.80f
        )
    }

    val allSegments: List<GlyphSegmentDef> = zoneCSegments + zoneASegments + zoneBSegments
}
