package com.smartisanos.music.ui.playback

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NeedleSeekMappingTest {

    @Test
    fun `needle playback arc maps to current media position`() {
        val durationMs = 200_000L

        assertEquals(0L, needleSeekPositionFromRotation(rotationDegrees = 12f, durationMs))
        assertEquals(100_000L, needleSeekPositionFromRotation(rotationDegrees = 23.15f, durationMs))
        assertEquals(200_000L, needleSeekPositionFromRotation(rotationDegrees = 34.3f, durationMs))
    }

    @Test
    fun `needle rest arc does not seek`() {
        assertNull(needleSeekPositionFromRotation(rotationDegrees = 0f, durationMs = 200_000L))
        assertNull(needleSeekPositionFromRotation(rotationDegrees = 11.99f, durationMs = 200_000L))
        assertNull(needleSeekPositionFromRotation(rotationDegrees = 23.15f, durationMs = 0L))
    }

    @Test
    fun `needle outside seek needs deliberate movement from playable position`() {
        assertFalse(
            shouldStartNeedleSeekDrag(
                initialPositionMs = 100_000L,
                candidatePositionMs = 0L,
                maxMoveDistance = 24f,
                outsideActivationDistancePx = 36f,
            ),
        )
        assertTrue(
            shouldStartNeedleSeekDrag(
                initialPositionMs = 100_000L,
                candidatePositionMs = 0L,
                maxMoveDistance = 48f,
                outsideActivationDistancePx = 36f,
            ),
        )
        assertTrue(
            shouldStartNeedleSeekDrag(
                initialPositionMs = 100_000L,
                candidatePositionMs = 96_000L,
                maxMoveDistance = 24f,
                outsideActivationDistancePx = 36f,
            ),
        )
    }

    @Test
    fun `needle point mapping clamps to supported arc`() {
        val containerSize = IntSize(width = 360, height = 357)
        val geometry = playbackNeedleGeometry(
            containerSize = containerSize,
            densityPxPerDp = 1f,
            turntableScale = 1f,
            rotationDegrees = 0f,
        )
        val beyondEndPoint = Offset(
            x = geometry.pivot.x - 220f,
            y = geometry.pivot.y + 180f,
        )
        val beforeRestPoint = Offset(
            x = geometry.pivot.x + 120f,
            y = geometry.pivot.y,
        )

        assertEquals(
            34.3f,
            needleSeekRotationFromPoint(
                point = beyondEndPoint,
                containerSize = containerSize,
                densityPxPerDp = 1f,
                turntableScale = 1f,
            ),
            0.001f,
        )
        assertEquals(
            0f,
            needleSeekRotationFromPoint(
                point = beforeRestPoint,
                containerSize = containerSize,
                densityPxPerDp = 1f,
                turntableScale = 1f,
            ),
            0.001f,
        )
    }

    @Test
    fun `angle delta normalizes across opposite angle boundary`() {
        assertEquals(2f, normalizeAngleDelta(-179f - 179f), 0.001f)
        assertEquals(-2f, normalizeAngleDelta(179f - -179f), 0.001f)
    }

    @Test
    fun `needle hit region matches original bottom fifth`() {
        val containerSize = IntSize(width = 360, height = 357)
        val geometry = playbackNeedleGeometry(
            containerSize = containerSize,
            densityPxPerDp = 1f,
            turntableScale = 1f,
            rotationDegrees = 12f,
        )
        val bottomFifth = Offset(
            x = geometry.left + (geometry.width / 2f),
            y = geometry.top + (geometry.height * 0.9f),
        )
        val middleOfNeedleView = Offset(
            x = geometry.left + (geometry.width / 2f),
            y = geometry.top + (geometry.height * 0.5f),
        )

        assertTrue(
            isWithinNeedleSeekRegion(
                point = bottomFifth,
                containerSize = containerSize,
                densityPxPerDp = 1f,
                turntableScale = 1f,
                rotationDegrees = 0f,
            ),
        )
        assertFalse(
            isWithinNeedleSeekRegion(
                point = middleOfNeedleView,
                containerSize = containerSize,
                densityPxPerDp = 1f,
                turntableScale = 1f,
                rotationDegrees = 0f,
            ),
        )
    }

    @Test
    fun `needle hit region follows rotated view coordinates`() {
        val containerSize = IntSize(width = 432, height = 428)
        val geometry = playbackNeedleGeometry(
            containerSize = containerSize,
            densityPxPerDp = 1f,
            turntableScale = 1.2f,
            rotationDegrees = 34.3f,
        )
        val localActivePoint = Offset(
            x = geometry.width / 2f,
            y = geometry.height * 0.9f,
        )
        val rotatedActivePoint = Offset(
            x = geometry.pivot.x,
            y = geometry.pivot.y,
        ) + rotateForTest(
            offset = Offset(
                x = localActivePoint.x - geometry.pivotLocal.x,
                y = localActivePoint.y - geometry.pivotLocal.y,
            ),
            rotationDegrees = 34.3f,
        )
        val localMiddlePoint = Offset(
            x = geometry.width / 2f,
            y = geometry.height * 0.5f,
        )
        val rotatedMiddlePoint = Offset(
            x = geometry.pivot.x,
            y = geometry.pivot.y,
        ) + rotateForTest(
            offset = Offset(
                x = localMiddlePoint.x - geometry.pivotLocal.x,
                y = localMiddlePoint.y - geometry.pivotLocal.y,
            ),
            rotationDegrees = 34.3f,
        )

        assertTrue(
            isWithinNeedleSeekRegion(
                point = rotatedActivePoint,
                containerSize = containerSize,
                densityPxPerDp = 1f,
                turntableScale = 1.2f,
                rotationDegrees = 34.3f,
            ),
        )
        assertFalse(
            isWithinNeedleSeekRegion(
                point = rotatedMiddlePoint,
                containerSize = containerSize,
                densityPxPerDp = 1f,
                turntableScale = 1.2f,
                rotationDegrees = 34.3f,
            ),
        )
    }

    @Test
    fun `large phone turntable uses original full width needle metrics`() {
        val geometry = playbackNeedleGeometry(
            containerSize = IntSize(width = 432, height = 428),
            densityPxPerDp = 1f,
            turntableScale = 1.2f,
            rotationDegrees = 12f,
        )

        assertEquals(73.3f, geometry.width, 0.001f)
        assertEquals(354.6953f, geometry.height, 0.001f)
        assertEquals(29.199982f, geometry.top, 0.001f)
        assertEquals(355.90002f, geometry.left, 0.001f)
        assertEquals(48f, geometry.pivotLocal.x, 0.001f)
        assertEquals(28f, geometry.pivotLocal.y, 0.001f)
    }

    @Test
    fun `compact turntable keeps needle cross axis metrics while shortening vertically`() {
        val turntableScale = 0.75f
        val geometry = playbackNeedleGeometry(
            containerSize = IntSize(width = 270, height = 267),
            densityPxPerDp = 1f,
            turntableScale = turntableScale,
            rotationDegrees = 12f,
        )

        assertEquals(OriginalNeedleWidthBaseDp, geometry.width, 0.001f)
        assertEquals(OriginalNeedleHeightBaseDp * turntableScale, geometry.height, 0.001f)
        assertEquals(OriginalNeedleTopMarginBaseDp * turntableScale, geometry.top, 0.001f)
        assertEquals(
            270f -
                OriginalNeedleRightMarginDp -
                OriginalNeedleWidthBaseDp,
            geometry.left,
            0.001f,
        )
        assertEquals(OriginalNeedlePivotXDp, geometry.pivotLocal.x, 0.001f)
        assertEquals(OriginalNeedlePivotYDp * turntableScale, geometry.pivotLocal.y, 0.001f)
    }

    @Test
    fun `disc tap inside touch slop can toggle lyrics`() {
        val center = Offset(100f, 100f)
        val initialPosition = Offset(140f, 100f)
        val finalPosition = Offset(146f, 104f)

        assertTrue(
            isDiscTapWithinSlop(
                initialPosition = initialPosition,
                finalPosition = finalPosition,
                maxMoveDistance = 8f,
                center = center,
                radius = 80f,
                tapTouchSlop = 12f,
            ),
        )
    }

    @Test
    fun `disc movement past touch slop is scratch gesture`() {
        val center = Offset(100f, 100f)
        val initialPosition = Offset(140f, 100f)
        val finalPosition = Offset(160f, 120f)

        assertFalse(
            isDiscTapWithinSlop(
                initialPosition = initialPosition,
                finalPosition = finalPosition,
                maxMoveDistance = 28f,
                center = center,
                radius = 80f,
                tapTouchSlop = 12f,
            ),
        )
    }

    @Test
    fun `scratch start position uses playback position at drag start`() {
        assertEquals(42_000L, scratchStartPosition(positionMs = 42_000L, durationMs = 180_000L))
        assertEquals(180_000L, scratchStartPosition(positionMs = 181_500L, durationMs = 180_000L))
    }

    private fun rotateForTest(
        offset: Offset,
        rotationDegrees: Float,
    ): Offset {
        val radians = Math.toRadians(rotationDegrees.toDouble())
        val cos = kotlin.math.cos(radians).toFloat()
        val sin = kotlin.math.sin(radians).toFloat()
        return Offset(
            x = (offset.x * cos) - (offset.y * sin),
            y = (offset.x * sin) + (offset.y * cos),
        )
    }
}
