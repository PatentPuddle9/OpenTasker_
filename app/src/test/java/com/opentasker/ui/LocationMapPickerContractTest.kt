package com.opentasker.ui

import com.opentasker.ProductionSources
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationMapPickerContractTest {
    private val source = ProductionSources.read("com/opentasker/ui/screens/LocationMapPicker.kt")

    @Test
    fun mapRedrawsWhenComposeFinishesMeasuringTheWebView() {
        assertTrue("the picker dialog must occupy its available height", source.contains("modifier = Modifier.fillMaxSize().padding(16.dp)"))
        assertTrue("the WebView must invalidate its map after the HTML page finishes loading", source.contains("override fun onPageFinished(view: WebView, url: String?)"))
        assertTrue("the map must expose an explicit redraw hook", source.contains("window.invalidateOpenTaskerMap = function()"))
        assertTrue("the map must react when Compose gives its container a non-zero size", source.contains("new ResizeObserver(scheduleRender)"))
    }

    @Test
    fun mainFrameLoadErrorsAreShownInsteadOfLeavingAnUnexplainedBlankMap() {
        assertTrue("only main-frame failures should replace the map status", source.contains("if (request.isForMainFrame)"))
        assertTrue("the picker should explain when its map document cannot load", source.contains("The map view could not load. You can still search for an address or place."))
    }

    @Test
    fun selectionAndRadiusAreReplayedWhenTheMapFinishesLoading() {
        assertTrue("the picker must track whether the WebView map script is ready", source.contains("var mapLoaded by remember"))
        assertTrue("early selection changes must wait until the map script is ready", source.contains("if (updateMap && mapLoaded)"))
        assertTrue("the latest selected point must be replayed after the page loads", source.contains("window.setOpenTaskerPoint && window.setOpenTaskerPoint"))
        assertTrue("the configured radius must be replayed after the page loads", source.contains("window.updateOpenTaskerRadius && window.updateOpenTaskerRadius"))
    }

    @Test
    fun theMapKeepsItsManualSelectionAndOpenStreetMapAttributionFallback() {
        assertTrue("the map should still allow selection without a tile response", source.contains("Map tiles are unavailable. Check your internet connection; place search and manual selection still work."))
        assertTrue("the map must retain visible OpenStreetMap attribution", source.contains("© <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap contributors</a>"))
    }
}
