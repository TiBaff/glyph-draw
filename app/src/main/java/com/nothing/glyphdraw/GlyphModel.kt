package com.nothing.glyphdraw

enum class StripType {
    UPPER_ARC,     // Long upper-right curved strip (24 segments)
    LEFT_ARC,      // Left vertical curved strip (6 segments)
    BOTTOM_STRIP   // Lower slanted/horizontal strip (4 segments)
}

data class GlyphSegmentDef(
    val id: Int,
    val strip: StripType,
    val startAngleDeg: Float,
    val sweepAngleDeg: Float
)

object GlyphLayoutData {
    // 24 addressable segments along upper-right arc (-30° to 120°)
    val upperArcSegments: List<GlyphSegmentDef> = (0 until 24).map { i ->
        val totalSweep = 145f
        val step = totalSweep / 24f
        val start = -25f + i * step
        GlyphSegmentDef(
            id = i,
            strip = StripType.UPPER_ARC,
            startAngleDeg = start,
            sweepAngleDeg = step * 0.82f // small gap between segments like Block Blast grid
        )
    }

    // 6 segments along left arc (145° to 215°)
    val leftArcSegments: List<GlyphSegmentDef> = (0 until 6).map { i ->
        val totalSweep = 68f
        val step = totalSweep / 6f
        val start = 146f + i * step
        GlyphSegmentDef(
            id = 24 + i,
            strip = StripType.LEFT_ARC,
            startAngleDeg = start,
            sweepAngleDeg = step * 0.80f
        )
    }

    // 4 segments along bottom strip (240° to 295°)
    val bottomStripSegments: List<GlyphSegmentDef> = (0 until 4).map { i ->
        val totalSweep = 52f
        val step = totalSweep / 4f
        val start = 242f + i * step
        GlyphSegmentDef(
            id = 30 + i,
            strip = StripType.BOTTOM_STRIP,
            startAngleDeg = start,
            sweepAngleDeg = step * 0.80f
        )
    }

    val allSegments: List<GlyphSegmentDef> = upperArcSegments + leftArcSegments + bottomStripSegments
    const val TOTAL_SEGMENTS = 34
}
