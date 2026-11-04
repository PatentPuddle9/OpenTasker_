package com.opentasker.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * OpenTasker's Compose icon catalog, sourced from the Coolicons Line Oval collection.
 * All paths are local vector data; tint and sizing remain controlled by Material 3 Icon.
 */
internal object CooliconVectors {
    private data class IconPath(val data: String, val strokeWidth: Float)
    private val cache = mutableMapOf<String, ImageVector>()

    private val paths = mapOf(
    "add-plus" to listOf(
        IconPath("M6 12h6m0 0h6m-6 0v6m0-6V6", 2f),
    ),
    "arrow-down-lg" to listOf(
        IconPath("m12 21l5-5m-5 5l-5-5m5 5V3", 2f),
    ),
    "arrow-up-lg" to listOf(
        IconPath("M12 3L7 8m5-5l5 5m-5-5v18", 2f),
    ),
    "close-md" to listOf(
        IconPath("m18 18l-6-6m0 0L6 6m6 6l6-6m-6 6l-6 6", 2f),
    ),
    "circle-check" to listOf(
        IconPath("m15 10l-4 4l-2-2m3 9a9 9 0 1 1 0-18a9 9 0 0 1 0 18", 2f),
    ),
    "chevron-right" to listOf(
        IconPath("m9 5l7 7l-7 7", 2f),
    ),
    "trash-full" to listOf(
        IconPath("M14 10v7m-4-7v7M6 6v11.8c0 1.12 0 1.68.218 2.108a2 2 0 0 0 .874.874c.427.218.987.218 2.105.218h5.606c1.118 0 1.677 0 2.104-.218c.377-.192.683-.498.875-.874c.218-.428.218-.987.218-2.105V6M6 6h2M6 6H4m4 0h8M8 6c0-.932 0-1.398.152-1.765a2 2 0 0 1 1.082-1.083C9.602 3 10.068 3 11 3h2c.932 0 1.398 0 1.765.152a2 2 0 0 1 1.083 1.083C16 4.602 16 5.068 16 6m0 0h2m0 0h2", 2f),
    ),
    "edit-pencil-01" to listOf(
        IconPath("m12 8l-8 8v4h4l8-8m-4-4l2.869-2.869l.001-.001c.395-.395.593-.593.821-.667a1 1 0 0 1 .618 0c.228.074.425.272.82.666l1.74 1.74c.396.396.594.594.668.822a1 1 0 0 1 0 .618c-.074.228-.272.426-.668.822L16 12.001m-4-4l4 4", 2f),
    ),
    "circle-warning" to listOf(
        IconPath("M12 8.45v4M12 21a9 9 0 1 1 0-18a9 9 0 0 1 0 18m.05-5.55v.1h-.1v-.1z", 2f),
    ),
    "caret-up-md" to listOf(
        IconPath("m8 14l4-4l4 4", 2f),
    ),
    "caret-down-md" to listOf(
        IconPath("m16 10l-4 4l-4-4", 2f),
    ),
    "info" to listOf(
        IconPath("M12 11v5m0 5a9 9 0 1 1 0-18a9 9 0 0 1 0 18m.05-13v.1h-.1V8z", 2f),
    ),
    "more-vertical" to listOf(
        IconPath("M11 18a1 1 0 1 0 2 0a1 1 0 0 0-2 0m0-6a1 1 0 1 0 2 0a1 1 0 0 0-2 0m0-6a1 1 0 1 0 2 0a1 1 0 0 0-2 0", 2f),
    ),
    "play" to listOf(
        IconPath("M5 17.334V6.667c0-.88 0-1.32.185-1.58a1 1 0 0 1 .687-.412c.317-.04.705.166 1.48.58l10 5.333l.004.002c.857.457 1.286.686 1.427.99c.122.266.122.573 0 .839c-.141.305-.571.535-1.43.993l-10 5.333c-.777.414-1.164.62-1.48.58a1 1 0 0 1-.688-.412C5 18.653 5 18.213 5 17.333", 2f),
    ),
    "search-magnifying-glass" to listOf(
        IconPath("m15 15l6 6m-11-4a7 7 0 1 1 0-14a7 7 0 0 1 0 14", 2f),
    ),
    "arrow-sub-down-right" to listOf(
        IconPath("m13 11l5 5m0 0l-5 5m5-5h-7.803c-1.118 0-1.678 0-2.105-.218a2 2 0 0 1-.874-.874C7 14.48 7 13.92 7 12.8V3", 2f),
    ),
    "map-pin" to listOf(
        IconPath("M5 9.923c0 4.852 4.244 8.864 6.123 10.402c.27.22.405.332.606.388c.156.044.386.044.542 0c.201-.056.336-.167.606-.388C14.756 18.787 19 14.775 19 9.923a6.9 6.9 0 0 0-2.05-4.895A7.04 7.04 0 0 0 12 3a7.04 7.04 0 0 0-4.95 2.028A6.88 6.88 0 0 0 5 9.923", 2f),
        IconPath("M10 9a2 2 0 1 0 4 0a2 2 0 0 0-4 0", 2f),
    ),
    "more-horizontal" to listOf(
        IconPath("M17 12a1 1 0 1 0 2 0a1 1 0 0 0-2 0m-6 0a1 1 0 1 0 2 0a1 1 0 0 0-2 0m-6 0a1 1 0 1 0 2 0a1 1 0 0 0-2 0", 2f),
    ),
    "close-circle" to listOf(
        IconPath("m9 9l3 3m0 0l3 3m-3-3l-3 3m3-3l3-3m-3 12a9 9 0 1 1 0-18a9 9 0 0 1 0 18", 2f),
    ),
    "filter" to listOf(
        IconPath("M20 5.6c0-.56 0-.84-.11-1.054a1 1 0 0 0-.436-.437C19.24 4 18.96 4 18.4 4H5.6c-.56 0-.84 0-1.054.109a1 1 0 0 0-.437.437C4 4.76 4 5.04 4 5.6v.737c0 .245 0 .367.028.482a1 1 0 0 0 .12.29c.061.1.148.187.32.36l5.063 5.062c.173.173.26.26.321.36q.083.136.12.29c.028.114.028.235.028.474v4.756c0 .857 0 1.286.18 1.544a1 1 0 0 0 .674.416c.311.046.695-.145 1.461-.529l.8-.4c.322-.16.482-.24.599-.36a1 1 0 0 0 .231-.374c.055-.158.055-.338.055-.697v-4.348c0-.245 0-.367.028-.482a1 1 0 0 1 .12-.29c.06-.1.147-.186.317-.356l.004-.004l5.063-5.062c.172-.173.258-.26.32-.36q.083-.136.12-.29C20 6.706 20 6.584 20 6.345z", 2f),
    ),
    "clock" to listOf(
        IconPath("M12 7v5h5m-5 9a9 9 0 1 1 0-18a9 9 0 0 1 0 18", 2f),
    ),
    "arrows-reload-01" to listOf(
        IconPath("M10 16H5v5m9-13h5V3M4.583 9.003a8 8 0 0 1 14.331-1.027m.504 7.021a8 8 0 0 1-14.332 1.027", 2f),
    ),
    "copy" to listOf(
        IconPath("M9 9V6.2c0-1.12 0-1.68.218-2.108c.192-.377.497-.682.874-.874C10.52 3 11.08 3 12.2 3h5.6c1.12 0 1.68 0 2.108.218a2 2 0 0 1 .874.874C21 4.52 21 5.08 21 6.2v5.6c0 1.12 0 1.68-.218 2.108a2 2 0 0 1-.874.874C19.48 15 18.92 15 17.803 15H15M9 9H6.2c-1.12 0-1.68 0-2.108.218a2 2 0 0 0-.874.874C3 10.52 3 11.08 3 12.2v5.6c0 1.12 0 1.68.218 2.108a2 2 0 0 0 .874.874c.427.218.987.218 2.105.218h5.607c1.117 0 1.676 0 2.104-.218a2 2 0 0 0 .874-.874c.218-.428.218-.987.218-2.105V15M9 9h2.8c1.12 0 1.68 0 2.108.218a2 2 0 0 1 .874.874c.218.427.218.987.218 2.105V15", 2f),
    ),
    "share-android" to listOf(
        IconPath("m9 13.5l6 3m0-9l-6 3M18 21a3 3 0 1 1 0-6a3 3 0 0 1 0 6M6 15a3 3 0 1 1 0-6a3 3 0 0 1 0 6m12-6a3 3 0 1 1 0-6a3 3 0 0 1 0 6", 2f),
    ),
    "folder" to listOf(
        IconPath("M3 6v10.8c0 1.12 0 1.68.218 2.108a2 2 0 0 0 .874.874c.427.218.987.218 2.105.218h11.606c1.118 0 1.677 0 2.104-.218c.377-.192.683-.498.875-.874C21 18.48 21 17.92 21 16.8V9.2c0-1.12 0-1.68-.218-2.108a2 2 0 0 0-.874-.874C19.48 6 18.92 6 17.8 6H12M3 6h9M3 6a2 2 0 0 1 2-2h3.675c.489 0 .734 0 .964.055q.308.075.578.24c.202.124.375.297.72.643L12 6", 2f),
    ),
    "chevron-left" to listOf(
        IconPath("m15 19l-7-7l7-7", 2f),
    ),
    "show" to listOf(
        IconPath("M3.587 13.779c1.78 1.769 4.883 4.22 8.413 4.22s6.634-2.451 8.413-4.22c.47-.467.705-.7.854-1.159c.107-.327.107-.913 0-1.24c-.15-.458-.385-.692-.854-1.159C18.633 8.452 15.531 6 12 6c-3.53 0-6.634 2.452-8.413 4.221c-.47.467-.705.7-.854 1.159c-.107.327-.107.913 0 1.24c.15.458.384.692.854 1.159", 2f),
        IconPath("M10 12a2 2 0 1 0 4 0a2 2 0 0 0-4 0", 2f),
    ),
    "hide" to listOf(
        IconPath("m4 4l16 16m-3.5-3.244C15.147 17.485 13.618 18 12 18c-3.53 0-6.634-2.452-8.413-4.221c-.47-.467-.705-.7-.854-1.159c-.107-.327-.107-.913 0-1.24c.15-.459.385-.693.855-1.16c.897-.892 2.13-1.956 3.584-2.793M19.5 14.634c.333-.293.638-.582.912-.854l.003-.003c.468-.466.703-.7.852-1.156c.107-.327.107-.914 0-1.241c-.15-.458-.384-.692-.854-1.159C18.633 8.452 15.531 6 12 6q-.507 0-1 .064m2.323 7.436a2 2 0 0 1-2.762-2.889", 2f),
    ),
    "layers" to listOf(
        IconPath("m21 12l-9 6l-9-6m18 4l-9 6l-9-6m18-8l-9 6l-9-6l9-6z", 2f),
    ),
    "lock" to listOf(
        IconPath("M9.23 9H7.2c-1.12 0-1.68 0-2.108.218a2 2 0 0 0-.874.874C4 10.52 4 11.08 4 12.2v5.6c0 1.12 0 1.68.218 2.108a2 2 0 0 0 .874.874c.427.218.987.218 2.105.218h9.606c1.118 0 1.677 0 2.104-.218c.377-.192.683-.498.875-.874c.218-.428.218-.986.218-2.104v-5.607c0-1.118 0-1.678-.218-2.105a2 2 0 0 0-.875-.874C18.48 9 17.92 9 16.8 9h-2.03M9.23 9h5.539M9.23 9A.23.23 0 0 1 9 8.77V6a3 3 0 1 1 6 0v2.77a.23.23 0 0 1-.231.23", 2f),
    ),
    "monitor" to listOf(
        IconPath("M15 20H9m-5-6.2V8.2c0-1.12 0-1.68.218-2.108c.192-.377.497-.682.874-.874C5.52 5 6.08 5 7.2 5h9.6c1.12 0 1.68 0 2.107.218c.377.192.683.497.875.874c.218.427.218.987.218 2.105v5.606c0 1.118 0 1.677-.218 2.104a2 2 0 0 1-.875.875c-.427.218-.986.218-2.104.218H7.197c-1.118 0-1.678 0-2.105-.218a2 2 0 0 1-.874-.875C4 15.48 4 14.92 4 13.8", 2f),
    ),
    "wifi-high" to listOf(
        IconPath("M8.343 14.59a5 5 0 0 1 7.29-.025m-9.484-3.021a8 8 0 0 1 11.664-.04M3.223 8.816a12 12 0 0 1 17.497-.06M12 19a1 1 0 1 1 0-2a1 1 0 0 1 0 2", 2f),
    ),
    "settings-future" to listOf(
        IconPath("m13.6 21.076l5.46-3.152c.584-.337.875-.505 1.087-.74a2 2 0 0 0 .416-.72c.097-.301.097-.637.097-1.307V8.843c0-.67 0-1.006-.098-1.307a2 2 0 0 0-.416-.72c-.21-.234-.5-.402-1.079-.736L13.6 2.924c-.583-.337-.874-.505-1.184-.57a2 2 0 0 0-.832 0c-.31.065-.601.233-1.184.57L4.938 6.077c-.582.336-.873.504-1.084.739a2 2 0 0 0-.416.72c-.098.302-.098.638-.098 1.311v6.305c0 .673 0 1.01.098 1.311a2 2 0 0 0 .416.72c.211.236.503.404 1.085.74l5.46 3.153c.584.337.875.505 1.185.57c.274.059.558.059.832 0c.31-.065.602-.233 1.185-.57", 2f),
        IconPath("M9 12a3 3 0 1 0 6 0a3 3 0 0 0-6 0", 2f),
    ),
    "slider-01" to listOf(
        IconPath("M14 15h7M3 15h2m0 0a2.5 2.5 0 1 0 5 0a2.5 2.5 0 0 0-5 0m15-6h1M3 9h7m6.5 2.5a2.5 2.5 0 1 1 0-5a2.5 2.5 0 0 1 0 5", 2f),
    ),
    "swatches-palette" to listOf(
        IconPath("M6.5 21h13.97a.53.53 0 0 0 .53-.53v-5.94M7.98 20.67l12.662-5.904a.53.53 0 0 0 .256-.703l-2.51-5.385a.53.53 0 0 0-.704-.256l-5.654 2.636m-2.148 7.346a3.5 3.5 0 0 1-6.762-1.812L6.736 3.1a.53.53 0 0 1 .648-.375l5.74 1.538a.53.53 0 0 1 .373.648zM6.5 17.6h.002v.002H6.5z", 2f),
    ),
    "shield-check" to listOf(
        IconPath("m15 9l-4 4l-2-2m11-.835c0 6.568-4.968 9.513-7.074 10.466l-.003.002c-.221.1-.332.15-.584.193c-.16.028-.518.028-.677 0a2 2 0 0 1-.588-.195C8.968 19.678 4 16.733 4 10.165V6.2c0-1.12 0-1.68.218-2.108c.192-.377.497-.682.874-.874C5.52 3 6.08 3 7.2 3h9.6c1.12 0 1.68 0 2.107.218c.377.192.683.497.875.874c.218.427.218.987.218 2.105z", 2f),
    ),
    "play-circle" to listOf(
        IconPath("M3 12a9 9 0 1 0 18 0a9 9 0 0 0-18 0", 2f),
        IconPath("M10 15V9l5 3z", 2f),
    ),
    "pause" to listOf(
        IconPath("M15 5.5v13c0 .465 0 .697.038.89a2 2 0 0 0 1.571 1.572c.194.038.426.038.89.038c.465 0 .698 0 .892-.038a2 2 0 0 0 1.57-1.572c.039-.19.039-.42.039-.878V5.488c0-.457 0-.687-.038-.879a2 2 0 0 0-1.572-1.57C18.197 3 17.965 3 17.5 3s-.697 0-.89.038a2 2 0 0 0-1.572 1.571C15 4.803 15 5.035 15 5.5m-11 0v13c0 .465 0 .697.038.89a2 2 0 0 0 1.571 1.572c.194.038.426.038.89.038c.465 0 .698 0 .892-.038a2 2 0 0 0 1.57-1.572C9 19.2 9 18.97 9 18.512V5.488c0-.457 0-.687-.038-.879A2 2 0 0 0 7.39 3.04C7.197 3 6.965 3 6.5 3s-.697 0-.89.038A2 2 0 0 0 4.037 4.61C4 4.803 4 5.035 4 5.5", 2f),
    ),
    "stop" to listOf(
        IconPath("M5 8.2v7.6c0 1.12 0 1.68.218 2.107c.192.377.497.683.874.875c.427.218.987.218 2.105.218h7.607c1.118 0 1.676 0 2.104-.218c.376-.192.682-.498.874-.875c.218-.427.218-.986.218-2.104V8.197c0-1.118 0-1.678-.218-2.105a2 2 0 0 0-.874-.874C17.48 5 16.92 5 15.8 5H8.2c-1.12 0-1.68 0-2.108.218a2 2 0 0 0-.874.874C5 6.52 5 7.08 5 8.2", 2f),
    ),
    "timer" to listOf(
        IconPath("M12 13V9m9-3l-2-2m-9-2h4m-2 19a8 8 0 1 1 0-16a8 8 0 0 1 0 16", 2f),
    ),
    "mail" to listOf(
        IconPath("m4 6l6.108 4.612l.002.002c.678.497 1.017.746 1.389.842a2 2 0 0 0 1.002 0c.372-.096.712-.345 1.392-.844c0 0 3.917-3.006 6.107-4.612M3 15.8V8.2c0-1.12 0-1.68.218-2.108c.192-.377.497-.682.874-.874C4.52 5 5.08 5 6.2 5h11.6c1.12 0 1.68 0 2.107.218c.377.192.683.497.875.874c.218.427.218.987.218 2.105v7.607c0 1.118 0 1.676-.218 2.104a2 2 0 0 1-.875.874c-.427.218-.986.218-2.104.218H6.197c-1.118 0-1.678 0-2.105-.218a2 2 0 0 1-.874-.874C3 17.48 3 16.92 3 15.8", 2f),
    ),
    "airplay" to listOf(
        IconPath("M6.25 17h-.053c-1.118 0-1.678 0-2.105-.218a2 2 0 0 1-.874-.875C3 15.48 3 14.92 3 13.8V8.2c0-1.12 0-1.68.218-2.108c.192-.377.497-.682.874-.874C4.52 5 5.08 5 6.2 5h11.6c1.12 0 1.68 0 2.107.218c.377.192.683.497.875.874c.218.427.218.987.218 2.105v5.606c0 1.118 0 1.677-.218 2.104a2 2 0 0 1-.874.875C19.48 17 18.92 17 17.803 17h-.05M16 20H8l4-5z", 2f),
    ),
    "cloud" to listOf(
        IconPath("M19 11a4 4 0 0 1 0 8H6a5 5 0 0 1-.331-9.99A7.002 7.002 0 0 1 18.929 11z", 2f),
    ),
    "calendar" to listOf(
        IconPath("M4 8h16M4 8v8.8c0 1.12 0 1.68.218 2.108a2 2 0 0 0 .874.874c.427.218.987.218 2.105.218h9.606c1.118 0 1.677 0 2.104-.218c.377-.192.683-.498.875-.874c.218-.428.218-.986.218-2.104V8M4 8v-.8c0-1.12 0-1.68.218-2.108c.192-.377.497-.682.874-.874C5.52 4 6.08 4 7.2 4H8m12 4v-.803c0-1.118 0-1.678-.218-2.105a2 2 0 0 0-.875-.874C18.48 4 17.92 4 16.8 4H16m0-2v2m0 0H8m0-2v2", 2f),
    ),
    "phone" to listOf(
        IconPath("M9.502 4.257A2 2 0 0 0 7.646 3H4.895A1.895 1.895 0 0 0 3 4.895C3 13.789 10.21 21 19.106 21A1.895 1.895 0 0 0 21 19.105v-2.751a2 2 0 0 0-1.257-1.857l-2.636-1.054a2 2 0 0 0-2.023.32l-.68.568a2 2 0 0 1-2.696-.122L9.792 12.29a2 2 0 0 1-.123-2.694l.567-.68a2 2 0 0 0 .322-2.024z", 2f),
    ),
    "compass" to listOf(
        IconPath("M3 12a9 9 0 1 0 18 0a9 9 0 0 0-18 0", 2f),
        IconPath("M10.5 10.5L16 8l-2.5 5.5L8 16z", 2f),
    ),
    "star" to listOf(
        IconPath("M2.335 10.337a.5.5 0 0 1 .28-.864l6.004-.712a.5.5 0 0 0 .396-.287l2.532-5.49a.5.5 0 0 1 .908 0l2.532 5.49a.5.5 0 0 0 .395.287l6.004.712a.5.5 0 0 1 .28.864l-4.438 4.105a.5.5 0 0 0-.15.464l1.177 5.93a.5.5 0 0 1-.735.534l-5.275-2.953a.5.5 0 0 0-.488 0l-5.276 2.952a.5.5 0 0 1-.735-.533l1.178-5.93a.5.5 0 0 0-.15-.464z", 2f),
    ),
    "bookmark" to listOf(
        IconPath("M6 7.2v9.485c0 1.361 0 2.042.204 2.458a2 2 0 0 0 2.06 1.102c.46-.06 1.026-.438 2.158-1.193l.003-.002c.449-.3.673-.449.908-.532a2 2 0 0 1 1.333 0c.235.083.46.233.911.534c1.133.755 1.7 1.132 2.16 1.193a2 2 0 0 0 2.059-1.102c.204-.416.204-1.097.204-2.458V7.197c0-1.118 0-1.678-.218-2.105a2 2 0 0 0-.875-.874C16.48 4 15.92 4 14.8 4H9.2c-1.12 0-1.68 0-2.108.218a2 2 0 0 0-.874.874C6 5.52 6 6.08 6 7.2", 2f),
    ),
    "navigation" to listOf(
        IconPath("m4.805 3.555l15.81 4.865c.862.265.96 1.446.153 1.85l-6.7 3.35a1 1 0 0 0-.448.447l-3.35 6.7c-.403.807-1.584.71-1.85-.153L3.555 4.804a1 1 0 0 1 1.25-1.249", 2f),
    ),
    "main-component" to listOf(
        IconPath("M11.235 2.374c-.368.152-.697.482-1.356 1.14c-.659.66-.989.989-1.14 1.356a2 2 0 0 0 0 1.531c.151.368.48.697 1.14 1.356c.658.659.988.989 1.356 1.14a2 2 0 0 0 1.53 0c.368-.151.697-.48 1.356-1.14s.988-.988 1.14-1.356a2 2 0 0 0 0-1.53c-.152-.368-.48-.697-1.14-1.356s-.988-.989-1.356-1.141a2 2 0 0 0-1.53 0M4.87 8.738c-.367.152-.697.481-1.355 1.14c-.66.66-.989.989-1.141 1.356a2 2 0 0 0 0 1.531c.152.368.482.697 1.14 1.356c.66.659.989.988 1.356 1.14a2 2 0 0 0 1.531 0c.368-.152.697-.481 1.356-1.14s.988-.988 1.14-1.356a2 2 0 0 0 0-1.53c-.152-.368-.48-.698-1.14-1.357s-.988-.988-1.356-1.14a2 2 0 0 0-1.53 0m11.372 1.14c-.659.66-.988.989-1.14 1.356a2 2 0 0 0 0 1.531c.152.368.481.697 1.14 1.356s.989.988 1.356 1.14a2 2 0 0 0 1.53 0c.368-.152.698-.481 1.357-1.14s.987-.988 1.14-1.356a2 2 0 0 0 0-1.53c-.153-.368-.481-.698-1.14-1.357c-.66-.659-.989-.988-1.356-1.14a2 2 0 0 0-1.531 0c-.367.152-.697.481-1.356 1.14m-5.008 5.224c-.368.152-.697.482-1.356 1.14c-.659.66-.989.989-1.14 1.357a2 2 0 0 0 0 1.53c.151.368.48.697 1.14 1.356c.658.659.988.989 1.356 1.14a2 2 0 0 0 1.53 0c.368-.151.697-.48 1.356-1.14s.988-.988 1.14-1.356c.203-.49.203-1.04 0-1.53c-.152-.368-.48-.698-1.14-1.356c-.659-.66-.988-.989-1.356-1.141a2 2 0 0 0-1.53 0", 2f),
    ),
    )

    fun get(name: String): ImageVector = synchronized(cache) {
        cache.getOrPut(name) {
            val builder = ImageVector.Builder(
                name = "coolicons-$name",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            )
            paths.getValue(name).forEach { path ->
                builder.addPath(
                    pathData = PathParser().parsePathString(path.data).toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = path.strokeWidth,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            builder.build()
        }
    }
}

/** Drop-in replacement for the previous Material Icons names, backed by Coolicons vectors. */
internal object Icons {
    object Filled {
        val Add get() = CooliconVectors.get("add-plus")
        val ArrowDownward get() = CooliconVectors.get("arrow-down-lg")
        val ArrowUpward get() = CooliconVectors.get("arrow-up-lg")
        val Clear get() = CooliconVectors.get("close-md")
        val CheckCircle get() = CooliconVectors.get("circle-check")
        val ChevronRight get() = CooliconVectors.get("chevron-right")
        val Delete get() = CooliconVectors.get("trash-full")
        val Edit get() = CooliconVectors.get("edit-pencil-01")
        val Error get() = CooliconVectors.get("circle-warning")
        val ExpandLess get() = CooliconVectors.get("caret-up-md")
        val ExpandMore get() = CooliconVectors.get("caret-down-md")
        val Info get() = CooliconVectors.get("info")
        val MoreVert get() = CooliconVectors.get("more-vertical")
        val PlayArrow get() = CooliconVectors.get("play")
        val Search get() = CooliconVectors.get("search-magnifying-glass")
        val SubdirectoryArrowRight get() = CooliconVectors.get("arrow-sub-down-right")
        val PushPin get() = CooliconVectors.get("map-pin")
        val Cancel get() = CooliconVectors.get("close-circle")
        val FilterList get() = CooliconVectors.get("filter")
        val HourglassEmpty get() = CooliconVectors.get("clock")
        val Refresh get() = CooliconVectors.get("arrows-reload-01")
        val ContentCopy get() = CooliconVectors.get("copy")
        val Share get() = CooliconVectors.get("share-android")
        val Folder get() = CooliconVectors.get("folder")
        val KeyboardArrowDown get() = CooliconVectors.get("caret-down-md")
        val KeyboardArrowUp get() = CooliconVectors.get("caret-up-md")
        val KeyboardArrowLeft get() = CooliconVectors.get("chevron-left")
        val KeyboardArrowRight get() = CooliconVectors.get("chevron-right")
        val Visibility get() = CooliconVectors.get("show")
        val VisibilityOff get() = CooliconVectors.get("hide")
    }

    object Default {
        val Add get() = Filled.Add
        val ArrowDownward get() = Filled.ArrowDownward
        val ArrowUpward get() = Filled.ArrowUpward
        val Clear get() = Filled.Clear
        val CheckCircle get() = Filled.CheckCircle
        val ChevronRight get() = Filled.ChevronRight
        val Delete get() = Filled.Delete
        val Edit get() = Filled.Edit
        val Error get() = Filled.Error
        val ExpandLess get() = Filled.ExpandLess
        val ExpandMore get() = Filled.ExpandMore
        val Info get() = Filled.Info
        val MoreVert get() = Filled.MoreVert
        val PlayArrow get() = Filled.PlayArrow
        val Search get() = Filled.Search
        val Refresh get() = Filled.Refresh
    }

    object Outlined {
        val AccountTree get() = CooliconVectors.get("layers")
        val History get() = CooliconVectors.get("clock")
        val Key get() = CooliconVectors.get("lock")
        val MonitorHeart get() = CooliconVectors.get("monitor")
        val Sensors get() = CooliconVectors.get("wifi-high")
        val Settings get() = CooliconVectors.get("settings-future")
        val Tune get() = CooliconVectors.get("slider-01")
        val Widgets get() = CooliconVectors.get("main-component")
        val MoreHoriz get() = CooliconVectors.get("more-horizontal")
        val Palette get() = CooliconVectors.get("swatches-palette")
        val Security get() = CooliconVectors.get("shield-check")
    }

    object AutoMirrored {
        object Filled {
            val CallSplit get() = CooliconVectors.get("arrow-sub-down-right")
            val KeyboardArrowLeft get() = CooliconVectors.get("chevron-left")
            val KeyboardArrowRight get() = CooliconVectors.get("chevron-right")
        }
        object Outlined {
            val PlaylistPlay get() = CooliconVectors.get("play-circle")
        }
    }
}
