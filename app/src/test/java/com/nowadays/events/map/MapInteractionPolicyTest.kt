package com.nowadays.events.map

import org.junit.Assert.*
import org.junit.Test

class MapInteractionPolicyTest {
    @Test fun `individual event selection never requests zoom`() {
        val action = MapInteractionPolicy.resolve("event-1", null)
        assertEquals(MapTapAction.SelectEvent("event-1"), action)
        assertFalse(MapInteractionPolicy.requestsZoom(action))
    }

    @Test fun `cluster selection requests zoom`() {
        val action = MapInteractionPolicy.resolve(null, setOf("one", "two"))
        assertTrue(action is MapTapAction.ExpandCluster)
        assertTrue(MapInteractionPolicy.requestsZoom(action))
    }

    @Test fun `filter keeps visible selection and invalidates hidden selection`() {
        assertEquals("child", MapSelectionPolicy.retainIfVisible("child", setOf("parent", "child")))
        assertNull(MapSelectionPolicy.retainIfVisible("child", setOf("parent")))
    }

    @Test fun `expanded cluster retains only still visible events`() {
        assertEquals(setOf("parent"), MapSelectionPolicy.expandedClusterIds(setOf("parent", "child"), setOf("parent")))
    }
}
