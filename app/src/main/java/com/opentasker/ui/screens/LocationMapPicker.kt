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
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.rememberUpdatedState
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

    fun setSelection(latitude: Double, longitude: Double, moveMap: Boolean = true) {
        if (!latitude.isFinite() || latitude !in -90.0..90.0 ||
            !longitude.isFinite() || longitude !in -180.0..180.0
        ) return
        selectedLatitude = latitude
        selectedLongitude = longitude
        statusMessage = null
        val move = if (moveMap) "true" else "false"
        mapView?.evaluateJavascript(
            "window.setOpenTaskerPoint($latitude,$longitude,$move);",
            null,
        )
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
                modifier = Modifier.fillMaxWidth().padding(16.dp),
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
                            webViewClient = WebViewClient()
                            webChromeClient = WebChromeClient()
                            setBackgroundColor(android.graphics.Color.WHITE)
                            isVerticalScrollBarEnabled = false
                            isHorizontalScrollBarEnabled = false
                            addJavascriptInterface(
                                LocationPickerJavascriptBridge { lat, lon ->
                                    mainHandler.post { setSelection(lat, lon, moveMap = false) }
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
                        webView.evaluateJavascript(
                            "window.updateOpenTaskerRadius($radiusMeters);",
                            null,
                        )
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
    val centerLatitude = latitude ?: 20.0
    val centerLongitude = longitude ?: 0.0
    val zoom = if (hasInitialPoint) 16 else 2
    return """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
          <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
          <style>
            html, body, #map { height:100%; width:100%; margin:0; padding:0; background:#e8e8e8; }
            .ot-pin { width:24px; height:24px; border-radius:50% 50% 50% 0; transform:rotate(-45deg);
              background:#6750a4; border:3px solid #fff; box-shadow:0 2px 8px #0005; }
            .ot-pin:after { content:''; position:absolute; width:7px; height:7px; border-radius:50%;
              top:5px; left:5px; background:#fff; }
            .leaflet-control-attribution { font-size:10px !important; }
            .leaflet-container { font: 12px sans-serif; }
          </style>
          <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
        </head>
        <body>
          <div id="map"></div>
          <script>
            var map = L.map('map', { zoomControl:true, attributionControl:true });
            L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
              maxZoom:19,
              attribution:'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>'
            }).addTo(map);
            var marker = null;
            var rangeCircle = null;
            var pointIcon = L.divIcon({
              className:'ot-marker-host',
              html:'<div class="ot-pin"></div>',
              iconSize:[24,24],
              iconAnchor:[12,24]
            });
            var activeRadius = RADIUS;
            function updatePoint(lat, lon, notify, moveMap) {
              if (!isFinite(lat) || !isFinite(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180) return;
              var point = [lat, lon];
              if (marker) marker.setLatLng(point);
              else marker = L.marker(point, { icon:pointIcon, draggable:true }).addTo(map);
              if (rangeCircle) rangeCircle.setLatLng(point).setRadius(activeRadius);
              else rangeCircle = L.circle(point, {
                radius:activeRadius, color:'#6750a4', weight:2,
                fillColor:'#6750a4', fillOpacity:0.16
              }).addTo(map);
              if (moveMap) map.setView(point, Math.max(map.getZoom(), 16));
              if (notify && window.OpenTaskerPicker) {
                window.OpenTaskerPicker.onPointSelected(lat, lon);
              }
            }
            window.setOpenTaskerPoint = function(lat, lon, moveMap) {
              updatePoint(lat, lon, true, moveMap);
            };
            window.updateOpenTaskerRadius = function(radius) {
              activeRadius = Math.max(1, Number(radius) || 100);
              if (rangeCircle) rangeCircle.setRadius(activeRadius);
            };
            map.on('click', function(event) {
              updatePoint(event.latlng.lat, event.latlng.lng, true, false);
            });
            map.on('tileerror', function() {
              document.body.setAttribute('data-tile-error', 'true');
            });
            map.setView([CENTER_LAT, CENTER_LON], ZOOM);
            if (HAS_POINT) updatePoint(CENTER_LAT, CENTER_LON, false, false);
          </script>
        </body>
        </html>
    """.replace("RADIUS", radiusMeters.toString())
        .replace("CENTER_LAT", centerLatitude.toString())
        .replace("CENTER_LON", centerLongitude.toString())
        .replace("ZOOM", zoom.toString())
        .replace("HAS_POINT", hasInitialPoint.toString())
}
