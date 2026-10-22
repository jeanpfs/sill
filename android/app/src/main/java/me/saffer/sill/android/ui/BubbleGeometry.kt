package me.saffer.sill.android.ui

import kotlin.math.cos
import kotlin.math.sin

enum class BubbleEdge { LEFT, RIGHT, TOP, BOTTOM }

/**
 * Where the command bubble rests: an edge, and `along` ∈ 0…1 down that edge (from the top on a left or
 * right edge, from the left on a top or bottom one). A fraction, not pixels, so the same place holds on
 * a Fold's inner and cover screens and when the keyboard shrinks the area.
 */
data class BubblePlace(val edge: BubbleEdge, val along: Float) {
    fun encode() = "${edge.name}:$along"

    companion object {
        val Default = BubblePlace(BubbleEdge.RIGHT, 0.62f)

        fun decode(s: String?): BubblePlace? {
            val (e, a) = s?.split(':', limit = 2)?.takeIf { it.size == 2 } ?: return null
            val edge = BubbleEdge.entries.firstOrNull { it.name == e } ?: return null
            return a.toFloatOrNull()?.takeIf { it in 0f..1f }?.let { BubblePlace(edge, it) }
        }
    }
}

/** The command bubble's geometry, in dp, as proposal C of the Fold redesign draws it. Pure, so it is checked on the JVM. */
object BubbleGeometry {
    const val BUBBLE = 56f
    const val INSET = 8f
    /** From the bubble's centre to the centre of each arc button. */
    const val RADIUS = 112f
    const val ARC_BUTTON = 52f
    /** The arc's buttons, in order: Apps, Windows, the Mac's menus, Aa, Keyboard, Settings. */
    const val ARC_COUNT = 6

    /**
     * The direction of arc button `index` as an angle in degrees, y pointing down: six buttons 32° apart
     * across 160°, always opening toward the inside of the screen from the bubble's edge.
     */
    fun angleDegrees(index: Int, edge: BubbleEdge): Double {
        val o = -80.0 + 32.0 * index
        return when (edge) {
            BubbleEdge.LEFT -> o
            BubbleEdge.RIGHT -> 180.0 - o
            BubbleEdge.BOTTOM -> -90.0 + o
            BubbleEdge.TOP -> 90.0 - o
        }
    }

    /** Where arc button `index` sits from the bubble's centre, in dp. */
    fun arcOffset(index: Int, edge: BubbleEdge): Pair<Float, Float> {
        val r = Math.toRadians(angleDegrees(index, edge))
        return (cos(r) * RADIUS).toFloat() to (sin(r) * RADIUS).toFloat()
    }

    /** How far the bubble keeps from each end of its edge so the whole arc fits on screen. */
    private fun margin(edge: BubbleEdge) = if (edge == BubbleEdge.LEFT || edge == BubbleEdge.RIGHT) 150f else 200f

    private fun clampAlong(v: Float, length: Float, edge: BubbleEdge): Float {
        val m = margin(edge)
        return v.coerceIn(minOf(m, length / 2f), maxOf(length - m, length / 2f))
    }

    /** The bubble's centre in a `w` × `h` area, in dp. */
    fun center(place: BubblePlace, w: Float, h: Float): Pair<Float, Float> {
        val edgeOffset = INSET + BUBBLE / 2f
        return when (place.edge) {
            BubbleEdge.LEFT -> edgeOffset to clampAlong(place.along * h, h, place.edge)
            BubbleEdge.RIGHT -> (w - edgeOffset) to clampAlong(place.along * h, h, place.edge)
            BubbleEdge.TOP -> clampAlong(place.along * w, w, place.edge) to edgeOffset
            BubbleEdge.BOTTOM -> clampAlong(place.along * w, w, place.edge) to (h - edgeOffset)
        }
    }

    /** The place nearest a released bubble centred at (`x`, `y`) in a `w` × `h` area. */
    fun snap(x: Float, y: Float, w: Float, h: Float): BubblePlace {
        val toLeft = x; val toRight = w - x; val toTop = y; val toBottom = h - y
        val nearest = minOf(toLeft, toRight, toTop, toBottom)
        return when {
            nearest == toLeft -> BubblePlace(BubbleEdge.LEFT, (y / h).coerceIn(0f, 1f))
            nearest == toRight -> BubblePlace(BubbleEdge.RIGHT, (y / h).coerceIn(0f, 1f))
            nearest == toTop -> BubblePlace(BubbleEdge.TOP, (x / w).coerceIn(0f, 1f))
            else -> BubblePlace(BubbleEdge.BOTTOM, (x / w).coerceIn(0f, 1f))
        }
    }

    /** Whether the arc around a bubble at `place` stays inside a `w` × `h` area, buttons whole. */
    fun arcFits(place: BubblePlace, w: Float, h: Float): Boolean {
        val (cx, cy) = center(place, w, h)
        val half = ARC_BUTTON / 2f
        return (0 until ARC_COUNT).all { i ->
            val (dx, dy) = arcOffset(i, place.edge)
            cx + dx - half >= 0f && cx + dx + half <= w && cy + dy - half >= 0f && cy + dy + half <= h
        }
    }
}
