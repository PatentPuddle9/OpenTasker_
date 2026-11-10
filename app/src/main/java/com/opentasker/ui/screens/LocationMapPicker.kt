package com.opentasker.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.opentasker.app.BuildConfig
import com.opentasker.ui.theme.DesignSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executor

private data class LocationSearchResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

@Composable
internal fun LocationMapPicker(
    latitude: String,
    longitude: String,
    radiusMeters: String,
    onLocationSelected: (String, String) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val parsedLatitude = latitude.toDoubleOrNull()
    val parsedLongitude = longitude.toDoubleOrNull()
    val hasLocation = parsedLatitude != null && parsedLongitude != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(DesignSystem.Radii.lg),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Trigger location", style = MaterialTheme.typography.labelLarge)
            Text(
                if (hasLocation) {
                    "Selected point: " + String.format(Locale.US, "%.6f, %.6f", parsedLatitude, parsedLongitude)
                } else {
                    "Choose the exact point where this location trigger should activate."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "The circle on the map uses the radius configured below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { showPicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (hasLocation) "Change location on map" else "Choose location on map")
            }
        }
    }

    if (showPicker) {
        LocationMapPickerDialog(
            initialLatitude = parsedLatitude,
            initialLongitude = parsedLongitude,
            radiusMeters = radiusMeters.toDoubleOrNull()?.coerceAtLeast(1.0) ?: 100.0,
            onDismiss = { showPicker = false },
            onConfirm = { selectedLatitude, selectedLongitude ->
                onLocationSelected(
                    String.format(Locale.US, "%.7f", selectedLatitude),
                    String.format(Locale.US, "%.7f", selectedLongitude),
                )
                showPicker = false
            },
        )
    }
}

@Composable
private fun LocationMapPickerDialog(
    initialLatitude: Double?,
    initialLongitude: Double?,
    radiusMeters: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var selectedLatitude by remember { mutableStateOf(initialLatitude) }
    var selectedLongitude by remember { mutableStateOf(initialLongitude) }
    var searchText by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<LocationSearchResult>>(emptyList()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var lastSearchAt by remember { mutableLongStateOf(0L) }
    var mapView by remember { mutableStateOf<WebView?>(null) }
    var mapLoaded by remember { mutableStateOf(false) }

    fun setSelection(latitude: Double, longitude: Double, moveMap: Boolean = true, updateMap: Boolean = true) {
        if (!latitude.isFinite() || latitude !in -90.0..90.0 ||
            !longitude.isFinite() || longitude !in -180.0..180.0
        ) return
        selectedLatitude = latitude
        selectedLongitude = longitude
        statusMessage = null
        val move = if (moveMap) "true" else "false"
        if (updateMap && mapLoaded) {
            mapView?.evaluateJavascript(
                "window.setOpenTaskerPoint($latitude,$longitude,$move);",
                null,
            )
        }
    }

    fun useCurrentLocation() {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) {
            statusMessage = "Allow location access to center the map on your device."
            return
        }
        requestPickerLocation(context) { location ->
            if (location == null) {
                statusMessage = "Couldn't get a current location. Check that Location is turned on."
            } else {
                setSelection(location.latitude, location.longitude)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it }) {
            useCurrentLocation()
        } else {
            statusMessage = "Location permission wasn't granted."
        }
    }

    val onSearch: () -> Unit = {
        val query = searchText.trim()
        if (query.isBlank()) {
            statusMessage = "Enter an address or place to search."
        } else if (searching) {
            Unit
        } else {
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastSearchAt < 1_000L) {
                statusMessage = "Please wait a moment before searching again."
            } else {
                lastSearchAt = now
                searching = true
                statusMessage = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) { searchOpenStreetMap(query) }
                    searching = false
                    if (result.isFailure) {
                        statusMessage = "Place search failed. Check your internet connection and try again."
                        searchResults = emptyList()
                    } else {
                        searchResults = result.getOrNull().orEmpty()
                        statusMessage = if (searchResults.isEmpty()) {
                            "No matching places found."
                        } else {
                            null
                        }
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.94f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Choose location", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Tap the map to place the trigger pin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = {
                            searchText = it
                            searchResults = emptyList()
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Search address or place") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    )
                    OutlinedButton(
                        onClick = onSearch,
                        enabled = !searching && searchText.isNotBlank(),
                    ) {
                        if (searching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Search")
                        }
                    }
                }

                if (searchResults.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 112.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        searchResults.forEach { result ->
                            TextButton(
                                onClick = {
                                    setSelection(result.latitude, result.longitude)
                                    searchResults = emptyList()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    result.name,
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 2,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }

                AndroidView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                    factory = { viewContext ->
                        WebView(viewContext).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.javaScriptCanOpenWindowsAutomatically = false
                            settings.userAgentString =
                                settings.userAgentString + " OpenTasker/" + BuildConfig.VERSION_NAME
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String?) {
                                    super.onPageFinished(view, url)
                                    // The first HTML render can happen before AndroidView has its
                                    // final measured size. Re-render once the document is ready.
                                    mapLoaded = true
                                    view.post {
                                        // Selection and radius changes can arrive while the document
                                        // is still loading. Replay the latest Compose state only after
                                        // the JavaScript map API is available.
                                        val selectedPointUpdate =
                                            if (selectedLatitude != null && selectedLongitude != null) {
                                                "window.setOpenTaskerPoint && window.setOpenTaskerPoint(${selectedLatitude},${selectedLongitude},true);"
                                            } else {
                                                ""
                                            }
                                        view.evaluateJavascript(
                                            """
                                                window.invalidateOpenTaskerMap && window.invalidateOpenTaskerMap();
                                                window.updateOpenTaskerRadius && window.updateOpenTaskerRadius($radiusMeters);
                                                $selectedPointUpdate
                                            """.trimIndent(),
                                            null,
                                        )
                                    }
                                }

                                override fun onReceivedError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    error: WebResourceError,
                                ) {
                                    super.onReceivedError(view, request, error)
                                    if (request.isForMainFrame) {
                                        mapLoaded = false
                                        statusMessage = "The map view could not load. You can still search for an address or place."
                                    }
                                }
                            }
                            webChromeClient = WebChromeClient()
                            setBackgroundColor(android.graphics.Color.rgb(232, 232, 232))
                            isVerticalScrollBarEnabled = false
                            isHorizontalScrollBarEnabled = false
                            addJavascriptInterface(
                                LocationPickerJavascriptBridge { lat, lon ->
                                    mainHandler.post { setSelection(lat, lon, moveMap = false, updateMap = false) }
                                },
                                "OpenTaskerPicker",
                            )
                            mapView = this
                            loadDataWithBaseURL(
                                "https://github.com/PatentPuddle9/OpenTasker_/",
                                locationPickerHtml(
                                    selectedLatitude,
                                    selectedLongitude,
                                    radiusMeters,
                                ),
                                "text/html",
                                "UTF-8",
                                null,
                            )
                        }
                    },
                    update = { webView ->
                        mapView = webView
                        if (mapLoaded) {
                            webView.evaluateJavascript(
                                "window.updateOpenTaskerRadius($radiusMeters);",
                                null,
                            )
                        }
                    },
                )

                Text(
                    text = if (selectedLatitude != null && selectedLongitude != null) {
                        "Selected: " + String.format(
                            Locale.US,
                            "%.6f, %.6f",
                            selectedLatitude,
                            selectedLongitude,
                        ) + "  •  Radius: " + radiusMeters.toInt() + " m"
                    } else {
                        "No point selected yet. Tap the map or search for a place."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (statusMessage != null) {
                    Text(
                        statusMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = {
                            val fine = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            ) == PackageManager.PERMISSION_GRANTED
                            val coarse = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (fine || coarse) {
                                useCurrentLocation()
                            } else {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ),
                                )
                            }
                        },
                    ) {
                        Text("Use current location")
                    }
                    Button(
                        onClick = {
                            val lat = selectedLatitude
                            val lon = selectedLongitude
                            if (lat != null && lon != null) onConfirm(lat, lon)
                            else statusMessage = "Choose a point on the map first."
                        },
                        enabled = selectedLatitude != null && selectedLongitude != null,
                    ) {
                        Text("Use this location")
                    }
                }

                Text(
                    "Map data © OpenStreetMap contributors. Place search by Nominatim.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mapView?.apply {
                stopLoading()
                removeJavascriptInterface("OpenTaskerPicker")
                loadUrl("about:blank")
                destroy()
            }
            mapView = null
            mapLoaded = false
        }
    }
}

private class LocationPickerJavascriptBridge(
    private val onPointSelected: (Double, Double) -> Unit,
) {
    @JavascriptInterface
    fun onPointSelected(latitude: Double, longitude: Double) {
        onPointSelected.invoke(latitude, longitude)
    }
}

@SuppressLint("MissingPermission")
private fun requestPickerLocation(
    context: android.content.Context,
    onResult: (Location?) -> Unit,
) {
    val manager = context.getSystemService(LocationManager::class.java)
    val provider = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
    ).firstOrNull { name ->
        try {
            manager.isProviderEnabled(name)
        } catch (_: Exception) {
            false
        }
    }
    if (provider == null) {
        onResult(null)
        return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val executor = Executor { command -> Handler(Looper.getMainLooper()).post(command) }
        try {
            manager.getCurrentLocation(provider, CancellationSignal(), executor) { location ->
                onResult(location)
            }
        } catch (_: SecurityException) {
            onResult(null)
        }
    } else {
        val location = try {
            manager.getLastKnownLocation(provider)
                ?: manager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        } catch (_: SecurityException) {
            null
        }
        onResult(location)
    }
}

private fun searchOpenStreetMap(query: String): Result<List<LocationSearchResult>> = runCatching {
    val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
    val connection = URL(
        "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5&q=" + encodedQuery,
    ).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.setRequestProperty(
            "User-Agent",
            "OpenTasker/" + BuildConfig.VERSION_NAME + " (https://github.com/PatentPuddle9/OpenTasker_)",
        )
        connection.setRequestProperty("Accept", "application/json")
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val array = JSONArray(body)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val lat = item.optString("lat").toDoubleOrNull() ?: continue
                val lon = item.optString("lon").toDoubleOrNull() ?: continue
                val name = item.optString("display_name").ifBlank { "Unnamed place" }
                if (lat.isFinite() && lon.isFinite()) {
                    add(LocationSearchResult(name, lat, lon))
                }
            }
        }
    } finally {
        connection.disconnect()
    }
}

private fun locationPickerHtml(
    latitude: Double?,
    longitude: Double?,
    radiusMeters: Double,
): String {
    val hasInitialPoint = latitude != null && longitude != null
    val centerLatitude = latitude ?: 39.5
    val centerLongitude = longitude ?: -98.35
    val zoom = if (hasInitialPoint) 16 else 4
    return """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
          <meta name="color-scheme" content="light">
          <style>
            * { box-sizing:border-box; }
            html, body, #map { height:100%; width:100%; margin:0; padding:0; overflow:hidden; }
            body { font:14px sans-serif; background:#e8e8e8; color:#29252f; }
            #map { position:relative; background-color:#e8e8e8;
              background-image:linear-gradient(45deg,#dedede 25%,transparent 25%),
                linear-gradient(-45deg,#dedede 25%,transparent 25%),
                linear-gradient(45deg,transparent 75%,#dedede 75%),
                linear-gradient(-45deg,transparent 75%,#dedede 75%);
              background-size:24px 24px; background-position:0 0,0 12px,12px -12px,-12px 0;
              touch-action:none; user-select:none; -webkit-user-select:none; }
            #tiles, #overlay { position:absolute; inset:0; overflow:hidden; pointer-events:none; }
            .tile { position:absolute; width:256px; height:256px; max-width:none; border:0; }
            #circle { position:absolute; border:2px solid #6750a4; border-radius:50%;
              background:rgba(103,80,164,.16); transform:translate(-50%,-50%); pointer-events:none; }
            #pin { position:absolute; width:26px; height:26px; margin:-25px 0 0 -13px;
              border-radius:50% 50% 50% 0; transform:rotate(-45deg);
              background:#6750a4; border:3px solid #fff; box-shadow:0 2px 8px #0006;
              pointer-events:auto; touch-action:none; }
            #pin:after { content:''; position:absolute; width:8px; height:8px; border-radius:50%;
              top:6px; left:6px; background:#fff; }
            #zoom { position:absolute; z-index:5; top:12px; left:12px; display:flex; flex-direction:column;
              gap:5px; pointer-events:auto; }
            .zoom-button { width:40px; height:40px; border:1px solid #bbb; border-radius:8px;
              background:#fff; color:#222; font-size:25px; line-height:1; box-shadow:0 1px 4px #0003; }
            #tile-status { display:none; position:absolute; z-index:4; top:12px; left:62px; right:12px;
              padding:8px 10px; border-radius:8px; background:#fff; color:#524b5b;
              box-shadow:0 1px 4px #0003; font-size:12px; }
            #attribution { position:absolute; z-index:5; right:0; bottom:0; padding:3px 6px;
              background:rgba(255,255,255,.86); color:#333; font-size:10px; pointer-events:auto; }
            #attribution a { color:#315f91; }
          </style>
        </head>
        <body>
          <div id="map" aria-label="Map. Tap to choose a location, drag to pan.">
            <div id="tiles"></div>
            <div id="overlay">
              <div id="circle"></div>
              <div id="pin" title="Drag to move the selected location"></div>
            </div>
            <div id="zoom">
              <button class="zoom-button" id="zoom-in" aria-label="Zoom in">+</button>
              <button class="zoom-button" id="zoom-out" aria-label="Zoom out">−</button>
            </div>
            <div id="tile-status"></div>
            <div id="attribution">© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a></div>
          </div>
          <script>
            (function() {
              'use strict';
              var TILE_SIZE = 256;
              var EARTH_RADIUS = 6378137;
              var MAX_LATITUDE = 85.05112878;
              var map = document.getElementById('map');
              var tilesLayer = document.getElementById('tiles');
              var pin = document.getElementById('pin');
              var circle = document.getElementById('circle');
              var tileStatus = document.getElementById('tile-status');
              var centerLat = CENTER_LAT;
              var centerLon = CENTER_LON;
              var zoom = ZOOM;
              var selected = HAS_POINT ? { lat:CENTER_LAT, lon:CENTER_LON } : null;
              var activeRadius = MAP_RADIUS;
              var pointer = null;
              var tileCache = Object.create(null);
              var visibleTileKeys = [];
              var renderQueued = false;

              function worldSize() { return TILE_SIZE * Math.pow(2, zoom); }
              function clamp(value, min, max) { return Math.max(min, Math.min(max, value)); }
              function lonToX(lon) { return (lon + 180) / 360 * worldSize(); }
              function latToY(lat) {
                var safeLat = clamp(lat, -MAX_LATITUDE, MAX_LATITUDE) * Math.PI / 180;
                return (1 - Math.log(Math.tan(safeLat) + 1 / Math.cos(safeLat)) / Math.PI) / 2 * worldSize();
              }
              function xToLon(x) { return x / worldSize() * 360 - 180; }
              function yToLat(y) {
                var n = Math.PI - 2 * Math.PI * y / worldSize();
                return 180 / Math.PI * Math.atan(0.5 * (Math.exp(n) - Math.exp(-n)));
              }
              function screenToPoint(x, y) {
                var rect = map.getBoundingClientRect();
                var worldX = lonToX(centerLon) + x - rect.width / 2;
                var worldY = latToY(centerLat) + y - rect.height / 2;
                var size = worldSize();
                worldX = ((worldX % size) + size) % size;
                worldY = clamp(worldY, 0, size);
                return { lat:yToLat(worldY), lon:xToLon(worldX) };
              }
              function scheduleRender() {
                if (renderQueued) return;
                renderQueued = true;
                window.requestAnimationFrame(function() {
                  renderQueued = false;
                  render();
                });
              }
              function updateTileStatus() {
                var failures = 0;
                for (var i = 0; i < visibleTileKeys.length; i++) {
                  var tile = tileCache[visibleTileKeys[i]];
                  if (tile && tile.dataset.state === 'error') failures++;
                }
                if (failures > 0 && failures >= visibleTileKeys.length) {
                  tileStatus.textContent = 'Map tiles are unavailable. Check your internet connection; place search and manual selection still work.';
                  tileStatus.style.display = 'block';
                } else if (failures > 0) {
                  tileStatus.textContent = 'Some map tiles could not be loaded.';
                  tileStatus.style.display = 'block';
                } else {
                  tileStatus.style.display = 'none';
                }
              }
              function renderTiles(width, height, centerX, centerY) {
                var left = centerX - width / 2;
                var top = centerY - height / 2;
                var firstX = Math.floor(left / TILE_SIZE);
                var lastX = Math.floor((left + width) / TILE_SIZE);
                var firstY = Math.floor(top / TILE_SIZE);
                var lastY = Math.floor((top + height) / TILE_SIZE);
                var count = Math.pow(2, zoom);
                var nextVisible = [];
                for (var ty = firstY; ty <= lastY; ty++) {
                  if (ty < 0 || ty >= count) continue;
                  for (var tx = firstX; tx <= lastX; tx++) {
                    var wrappedX = ((tx % count) + count) % count;
                    var key = zoom + '/' + wrappedX + '/' + ty;
                    var image = tileCache[key];
                    if (!image) {
                      image = document.createElement('img');
                      image.className = 'tile';
                      image.alt = '';
                      image.draggable = false;
                      image.dataset.state = 'loading';
                      image.dataset.key = key;
                      image.onload = function() {
                        this.dataset.state = 'loaded';
                        updateTileStatus();
                      };
                      image.onerror = function() {
                        this.dataset.state = 'error';
                        updateTileStatus();
                      };
                      tileCache[key] = image;
                      image.src = 'https://tile.openstreetmap.org/' + key + '.png';
                    }
                    image.style.left = (tx * TILE_SIZE - left) + 'px';
                    image.style.top = (ty * TILE_SIZE - top) + 'px';
                    if (image.parentNode !== tilesLayer) tilesLayer.appendChild(image);
                    nextVisible.push(key);
                  }
                }
                var children = Array.prototype.slice.call(tilesLayer.children);
                children.forEach(function(child) {
                  if (nextVisible.indexOf(child.dataset.key) === -1) tilesLayer.removeChild(child);
                });
                visibleTileKeys = nextVisible;
                updateTileStatus();
                var cacheKeys = Object.keys(tileCache);
                if (cacheKeys.length > 120) {
                  cacheKeys.slice(0, cacheKeys.length - 100).forEach(function(key) {
                    if (visibleTileKeys.indexOf(key) !== -1) return;
                    var oldTile = tileCache[key];
                    if (oldTile.parentNode) oldTile.parentNode.removeChild(oldTile);
                    delete tileCache[key];
                  });
                }
              }
              function renderSelection(width, height, centerX, centerY) {
                if (!selected) {
                  pin.style.display = 'none';
                  circle.style.display = 'none';
                  return;
                }
                var rectX = lonToX(selected.lon) - centerX + width / 2;
                var rectY = latToY(selected.lat) - centerY + height / 2;
                var metersPerPixel = Math.cos(selected.lat * Math.PI / 180) * 2 * Math.PI * EARTH_RADIUS / worldSize();
                var radiusPixels = activeRadius / Math.max(0.0001, metersPerPixel);
                radiusPixels = Math.min(radiusPixels, Math.max(width, height) * 2);
                circle.style.display = 'block';
                circle.style.width = (radiusPixels * 2) + 'px';
                circle.style.height = (radiusPixels * 2) + 'px';
                circle.style.left = rectX + 'px';
                circle.style.top = rectY + 'px';
                pin.style.display = 'block';
                pin.style.left = rectX + 'px';
                pin.style.top = rectY + 'px';
              }
              function render() {
                var width = map.clientWidth;
                var height = map.clientHeight;
                if (!width || !height) return;
                var centerX = lonToX(centerLon);
                var centerY = latToY(centerLat);
                renderTiles(width, height, centerX, centerY);
                renderSelection(width, height, centerX, centerY);
              }
              function setPoint(lat, lon, moveMap) {
                if (!isFinite(lat) || !isFinite(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180) return;
                selected = { lat:lat, lon:lon };
                if (moveMap) {
                  centerLat = lat;
                  centerLon = lon;
                  zoom = Math.max(zoom, 15);
                }
                scheduleRender();
              }
              window.setOpenTaskerPoint = setPoint;
              window.updateOpenTaskerRadius = function(radius) {
                activeRadius = Math.max(1, Number(radius) || 100);
                scheduleRender();
              };
              function notifyPoint() {
                if (selected && window.OpenTaskerPicker) {
                  window.OpenTaskerPicker.onPointSelected(selected.lat, selected.lon);
                }
              }
              function pointerPosition(event) {
                var rect = map.getBoundingClientRect();
                return { x:event.clientX - rect.left, y:event.clientY - rect.top };
              }
              map.addEventListener('pointerdown', function(event) {
                if (event.target.closest && event.target.closest('.zoom-button')) return;
                var pos = pointerPosition(event);
                pointer = {
                  id:event.pointerId, x:pos.x, y:pos.y,
                  startX:pos.x, startY:pos.y,
                  centerX:lonToX(centerLon), centerY:latToY(centerLat),
                  draggingPin:event.target === pin, moved:false
                };
                map.setPointerCapture(event.pointerId);
                event.preventDefault();
              });
              map.addEventListener('pointermove', function(event) {
                if (!pointer || pointer.id !== event.pointerId) return;
                var pos = pointerPosition(event);
                var dx = pos.x - pointer.startX;
                var dy = pos.y - pointer.startY;
                if (Math.abs(dx) + Math.abs(dy) > 4) pointer.moved = true;
                if (!pointer.moved) return;
                if (pointer.draggingPin) {
                  selected = screenToPoint(pos.x, pos.y);
                } else {
                  var size = worldSize();
                  var x = ((pointer.centerX - dx) % size + size) % size;
                  var y = clamp(pointer.centerY - dy, 0, size);
                  centerLon = xToLon(x);
                  centerLat = yToLat(y);
                }
                scheduleRender();
                event.preventDefault();
              });
              function finishPointer(event) {
                if (!pointer || pointer.id !== event.pointerId) return;
                var pos = pointerPosition(event);
                if (!pointer.moved) {
                  selected = screenToPoint(pos.x, pos.y);
                  notifyPoint();
                } else if (pointer.draggingPin) {
                  notifyPoint();
                }
                pointer = null;
                scheduleRender();
              }
              map.addEventListener('pointerup', finishPointer);
              map.addEventListener('pointercancel', function() { pointer = null; });
              document.getElementById('zoom-in').addEventListener('click', function(event) {
                event.stopPropagation();
                zoom = Math.min(19, zoom + 1);
                scheduleRender();
              });
              document.getElementById('zoom-out').addEventListener('click', function(event) {
                event.stopPropagation();
                zoom = Math.max(2, zoom - 1);
                scheduleRender();
              });
              map.addEventListener('wheel', function(event) {
                zoom = clamp(zoom + (event.deltaY < 0 ? 1 : -1), 2, 19);
                scheduleRender();
                event.preventDefault();
              }, { passive:false });
              // AndroidView can initially measure the WebView at 0x0 while Compose is
              // laying out the dialog. Observe the actual map element so tiles are drawn
              // as soon as it receives a non-zero size, not only on a later window resize.
              if (window.ResizeObserver) {
                var mapResizeObserver = new ResizeObserver(scheduleRender);
                mapResizeObserver.observe(map);
              }
              window.addEventListener('resize', scheduleRender);
              window.addEventListener('orientationchange', scheduleRender);
              document.addEventListener('visibilitychange', function() {
                if (!document.hidden) scheduleRender();
              });
              window.invalidateOpenTaskerMap = function() {
                render();
                scheduleRender();
              };
              render();
              if (selected) notifyInitialSelection();
              function notifyInitialSelection() { /* Initial coordinates already live in Compose state. */ }
            })();
          </script>
        </body>
        </html>
    """.replace("MAP_RADIUS", radiusMeters.toString())
        .replace("CENTER_LAT", centerLatitude.toString())
        .replace("CENTER_LON", centerLongitude.toString())
        .replace("ZOOM", zoom.toString())
        .replace("HAS_POINT", hasInitialPoint.toString())
}
