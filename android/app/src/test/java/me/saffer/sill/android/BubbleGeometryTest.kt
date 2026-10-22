package me.saffer.sill.android

import me.saffer.sill.android.ui.BubbleEdge
import me.saffer.sill.android.ui.BubbleGeometry
import me.saffer.sill.android.ui.BubblePlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleGeometryTest {
    private val places = BubbleEdge.entries.flatMap { e -> (0..20).map { BubblePlace(e, it / 20f) } }

    @Test fun arcOpensTowardTheInsideOfTheScreen() {
        // Every button of a left-edge bubble points right, of a bottom-edge one up, and so on.
        for (i in 0 until BubbleGeometry.ARC_COUNT) {
            assertTrue(BubbleGeometry.arcOffset(i, BubbleEdge.LEFT).first > 0f)
            assertTrue(BubbleGeometry.arcOffset(i, BubbleEdge.RIGHT).first < 0f)
            assertTrue(BubbleGeometry.arcOffset(i, BubbleEdge.TOP).second > 0f)
            assertTrue(BubbleGeometry.arcOffset(i, BubbleEdge.BOTTOM).second < 0f)
        }
    }

    @Test fun theSixButtonsSitOnTheArcSpacedEvenly() {
        for (e in BubbleEdge.entries) {
            val pts = (0 until BubbleGeometry.ARC_COUNT).map { BubbleGeometry.arcOffset(it, e) }
            pts.forEach { (x, y) -> assertEquals(BubbleGeometry.RADIUS, Math.hypot(x.toDouble(), y.toDouble()).toFloat(), 0.01f) }
            val gaps = pts.zipWithNext { a, b -> Math.hypot((a.first - b.first).toDouble(), (a.second - b.second).toDouble()) }
            assertTrue("adjacent buttons must not overlap", gaps.all { it > BubbleGeometry.ARC_BUTTON })
            assertEquals(gaps.first(), gaps.last(), 0.01)
        }
    }

    @Test fun theArcFitsOnTheOpenFoldAtEveryRestingPlace() {
        for (p in places) assertTrue("$p", BubbleGeometry.arcFits(p, 932f, 704f))
    }

    @Test fun theArcFitsOnAPhoneOnItsSideToo() {
        for (p in places) assertTrue("$p", BubbleGeometry.arcFits(p, 780f, 360f))
    }

    @Test fun aReleasedBubbleRestsOnTheNearestEdge() {
        assertEquals(BubbleEdge.LEFT, BubbleGeometry.snap(40f, 300f, 932f, 704f).edge)
        assertEquals(BubbleEdge.RIGHT, BubbleGeometry.snap(900f, 300f, 932f, 704f).edge)
        assertEquals(BubbleEdge.TOP, BubbleGeometry.snap(400f, 30f, 932f, 704f).edge)
        assertEquals(BubbleEdge.BOTTOM, BubbleGeometry.snap(400f, 690f, 932f, 704f).edge)
        // Along the edge it keeps the fraction it was released at.
        assertEquals(0.25f, BubbleGeometry.snap(20f, 176f, 932f, 704f).along, 0.001f)
    }

    @Test fun theBubbleKeepsItsInsetFromTheEdgeItRestsOn() {
        val (x, _) = BubbleGeometry.center(BubblePlace(BubbleEdge.RIGHT, 0.5f), 932f, 704f)
        assertEquals(932f - BubbleGeometry.INSET - BubbleGeometry.BUBBLE / 2f, x, 0.001f)
        val (_, y) = BubbleGeometry.center(BubblePlace(BubbleEdge.TOP, 0.5f), 932f, 704f)
        assertEquals(BubbleGeometry.INSET + BubbleGeometry.BUBBLE / 2f, y, 0.001f)
    }

    @Test fun anAreaShorterThanTheArcKeepsTheBubbleInTheMiddle() {
        // The keyboard leaves 300 dp: the clamp must not invert (min above max) and crash.
        val (_, y) = BubbleGeometry.center(BubblePlace(BubbleEdge.LEFT, 0.95f), 932f, 300f)
        assertEquals(150f, y, 0.001f)
    }

    @Test fun aPlaceSurvivesBeingSavedAndAJunkOneIsRefused() {
        val p = BubblePlace(BubbleEdge.BOTTOM, 0.375f)
        assertEquals(p, BubblePlace.decode(p.encode()))
        assertNull(BubblePlace.decode(null))
        assertNull(BubblePlace.decode("SIDE:0.5"))
        assertNull(BubblePlace.decode("LEFT:1.5"))
        assertNull(BubblePlace.decode("LEFT"))
        assertNotNull(BubblePlace.Default)
    }
}
