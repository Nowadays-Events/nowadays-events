package com.nowadays.events.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClusterZoomPolicyTest {
    @Test fun `small camera movements keep expanded markers stable`() {
        assertFalse(ClusterZoomPolicy.shouldCollapse(15.2, 15.0))
        assertFalse(ClusterZoomPolicy.shouldCollapse(15.2, 14.76))
    }

    @Test fun `meaningful zoom out reforms the cluster`() {
        assertTrue(ClusterZoomPolicy.shouldCollapse(15.2, 14.75))
        assertTrue(ClusterZoomPolicy.shouldCollapse(15.2, 13.0))
    }

    @Test fun `markers cannot collapse before expansion zoom is known`() {
        assertFalse(ClusterZoomPolicy.shouldCollapse(null, 10.0))
    }
}
