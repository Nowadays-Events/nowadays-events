package com.nowadays.events.map

import org.junit.Assert.assertEquals
import org.junit.Test

class ClusterTapCameraPolicyTest {
    @Test fun `numbered bubble uses the zoom that frames all its members`() {
        assertEquals(12.8, ClusterTapCameraPolicy.targetZoom(10.0, 12.8), 0.0)
    }

    @Test fun `member framing never exceeds the clickable event threshold`() {
        assertEquals(15.0, ClusterTapCameraPolicy.targetZoom(10.0, 18.0), 0.0)
    }

    @Test fun `member framing never zooms backwards`() {
        assertEquals(14.0, ClusterTapCameraPolicy.targetZoom(14.0, 12.0), 0.0)
    }

    @Test fun `distant zoom uses a smoother animation without becoming excessively long`() {
        assertEquals(640, ClusterTapCameraPolicy.durationMs(14.6, 15.0))
        assertEquals(1_100, ClusterTapCameraPolicy.durationMs(10.0, 15.0))
        assertEquals(1_200, ClusterTapCameraPolicy.durationMs(5.0, 15.0))
    }
}
