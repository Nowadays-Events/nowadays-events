package com.nowadays.events.map

import org.junit.Assert.assertEquals
import org.junit.Test

class ClusterTapCameraPolicyTest {
    @Test fun `numbered bubble stops exactly where individual events become clickable`() {
        assertEquals(15.0, ClusterTapCameraPolicy.targetZoom(10.0), 0.0)
        assertEquals(15.0, ClusterTapCameraPolicy.targetZoom(14.6), 0.0)
    }

    @Test fun `numbered bubble never zooms back when already beyond clickable threshold`() {
        assertEquals(15.2, ClusterTapCameraPolicy.targetZoom(15.2), 0.0)
    }

    @Test fun `distant zoom uses a smoother animation without becoming excessively long`() {
        assertEquals(640, ClusterTapCameraPolicy.durationMs(14.6))
        assertEquals(1_100, ClusterTapCameraPolicy.durationMs(10.0))
        assertEquals(1_200, ClusterTapCameraPolicy.durationMs(5.0))
    }
}
