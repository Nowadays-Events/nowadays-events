package com.nowadays.events.presentation.map

import org.junit.Assert.assertEquals
import org.junit.Test

class MapHomePanelPolicyTest {
    @Test fun `list and map buttons have deterministic destinations`() {
        assertEquals(MapHomePanelState.EXPANDED, MapHomePanelPolicy.onListButton())
        assertEquals(MapHomePanelState.CLOSED, MapHomePanelPolicy.onMapButton())
    }

    @Test fun `handle walks through the three simple states`() {
        assertEquals(MapHomePanelState.PREVIEW, MapHomePanelPolicy.onHandleTap(MapHomePanelState.CLOSED))
        assertEquals(MapHomePanelState.EXPANDED, MapHomePanelPolicy.onHandleTap(MapHomePanelState.PREVIEW))
        assertEquals(MapHomePanelState.PREVIEW, MapHomePanelPolicy.onHandleTap(MapHomePanelState.EXPANDED))
    }

    @Test fun `vertical drags move one state and ignore small movement`() {
        assertEquals(MapHomePanelState.PREVIEW, MapHomePanelPolicy.onVerticalDrag(MapHomePanelState.CLOSED, -80f))
        assertEquals(MapHomePanelState.EXPANDED, MapHomePanelPolicy.onVerticalDrag(MapHomePanelState.PREVIEW, -80f))
        assertEquals(MapHomePanelState.PREVIEW, MapHomePanelPolicy.onVerticalDrag(MapHomePanelState.EXPANDED, 80f))
        assertEquals(MapHomePanelState.CLOSED, MapHomePanelPolicy.onVerticalDrag(MapHomePanelState.PREVIEW, 80f))
        assertEquals(MapHomePanelState.PREVIEW, MapHomePanelPolicy.onVerticalDrag(MapHomePanelState.PREVIEW, 20f))
    }
}
