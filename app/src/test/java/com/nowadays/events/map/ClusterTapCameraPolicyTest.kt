package com.nowadays.events.map

import org.junit.Assert.assertEquals
import org.junit.Test

class ClusterTapCameraPolicyTest {
    @Test fun `numbered bubble advances by one gentle zoom step`() {
        assertEquals(11.0, ClusterTapCameraPolicy.targetZoom(10.0), 0.0)
        assertEquals(14.4, ClusterTapCameraPolicy.targetZoom(13.4), 0.0)
    }

    @Test fun `numbered bubble never forces a precise zoom beyond marker threshold`() {
        assertEquals(15.0, ClusterTapCameraPolicy.targetZoom(14.6), 0.0)
        assertEquals(15.2, ClusterTapCameraPolicy.targetZoom(15.2), 0.0)
    }
}
