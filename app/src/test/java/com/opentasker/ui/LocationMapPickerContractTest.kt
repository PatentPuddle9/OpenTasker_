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
    fun aNewLocationTriggerCentersNearTheDeviceWhenPermissionIsAlreadyGranted() {
        assertTrue("new location triggers should check existing location permission", source.contains("LaunchedEffect(initialLatitude, initialLongitude)"))
        assertTrue("saved coordinates must remain the initial map center", source.contains("if (initialLatitude == null && initialLongitude == null)"))
        assertTrue("the picker should reuse the current-location flow", source.contains("if (hasLocationPermission) useCurrentLocation()"))
    }

    @Test
    fun currentLocationTriesEnabledProvidersAndFallsBackToLastKnown() {
        assertTrue("GPS and network providers should both be considered", source.contains("LocationManager.GPS_PROVIDER") && source.contains("LocationManager.NETWORK_PROVIDER"))
        assertTrue("all enabled providers should be queried", source.contains("enabledProviders.forEach"))
        assertTrue("provider failures should use the freshest valid known fix", source.contains("bestLastKnownLocation()"))
        assertTrue("returned coordinates should be range checked", source.contains("location.latitude in -90.0..90.0") && source.contains("location.longitude in -180.0..180.0"))
    }

    @Test
    fun theMapKeepsItsManualSelectionAndOpenStreetMapAttributionFallback() {
        assertTrue("the map should still allow selection without a tile response", source.contains("Map tiles are unavailable. Check your internet connection; place search and manual selection still work."))
        assertTrue("the map must retain visible OpenStreetMap attribution", source.contains("© <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap contributors</a>"))
    }
}
