package com.opentasker.core.engine

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.provider.Settings
import com.opentasker.core.contexts.DeviceStateEvents
import kotlin.math.roundToInt

/**
 * Reads device facts once at the beginning of an automation run. Values live in their own
 * template namespace ({{ system.* }}) so they never overwrite user variables or persist as globals.
 */
internal fun readSystemVariableSnapshot(context: Context): Map<String, String> {
    val appContext = context.applicationContext
    val values = linkedMapOf<String, String>()

    runCatching {
        Settings.System.getInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    }.getOrNull()?.takeIf { it >= 0 }?.let { level ->
        values["brightness"] = level.toString()
        values["brightness_percent"] = ((level * 100f) / 255f).roundToInt().coerceIn(0, 100).toString()
    }
    runCatching {
        Settings.System.getInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
    }.getOrNull()?.let { mode ->
        val automatic = mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        values["brightness_mode"] = if (automatic) "automatic" else "manual"
        values["brightness_automatic"] = automatic.toString()
    }

    val audio = appContext.getSystemService(AudioManager::class.java)
    if (audio != null) {
        addVolumeSnapshot(values, audio, "volume", AudioManager.STREAM_MUSIC, includeMute = true)
        addVolumeSnapshot(values, audio, "volume_ring", AudioManager.STREAM_RING)
        addVolumeSnapshot(values, audio, "volume_alarm", AudioManager.STREAM_ALARM)
        addVolumeSnapshot(values, audio, "volume_notification", AudioManager.STREAM_NOTIFICATION)
    }

    val batteryIntent = runCatching {
        @Suppress("DEPRECATION")
        appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }.getOrNull()
    if (batteryIntent != null) {
        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        values["battery_level"] = level.takeIf { it >= 0 }?.toString().orEmpty()
        values["battery_scale"] = scale.takeIf { it > 0 }?.toString().orEmpty()
        normalizeBatteryPercent(level, scale)?.let { values["battery_percent"] = it.toString() }

        val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val statusName = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
            else -> "unknown"
        }
        values["battery_status"] = statusName
        values["battery_charging"] = (
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            ).toString()

        val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        values["battery_plugged"] = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "ac"
            BatteryManager.BATTERY_PLUGGED_USB -> "usb"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            BatteryManager.BATTERY_PLUGGED_DOCK -> "dock"
            else -> "unplugged"
        }

        val temperatureTenthsC = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        if (temperatureTenthsC != Int.MIN_VALUE) {
            values["battery_temperature_c"] = (temperatureTenthsC / 10.0).toString()
        }
        val voltageMv = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        if (voltageMv >= 0) values["battery_voltage_mv"] = voltageMv.toString()
    }

    // These are the current, merged facts published by the app's Wi-Fi/connectivity monitors.
    DeviceStateEvents.events.value
        .filterKeys { it in DEVICE_STATE_VARIABLES }
        .forEach { (name, value) -> values[name] = value }

    return values.filterValues(String::isNotEmpty)
}

private fun addVolumeSnapshot(
    values: MutableMap<String, String>,
    audio: AudioManager,
    name: String,
    stream: Int,
    includeMute: Boolean = false,
) {
    runCatching {
        val level = audio.getStreamVolume(stream)
        val maximum = audio.getStreamMaxVolume(stream)
        values[name] = level.toString()
        values["${name}_max"] = maximum.toString()
        values["${name}_percent"] = if (maximum > 0) {
            ((level * 100f) / maximum).roundToInt().coerceIn(0, 100).toString()
        } else {
            "0"
        }
        if (includeMute) {
            values["${name}_muted"] = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                audio.isStreamMute(stream)
            } else {
                level == 0
            }.toString()
        }
    }
}

internal fun normalizeBatteryPercent(level: Int, scale: Int): Int? {
    if (level < 0 || scale <= 0) return null
    return ((level * 100f) / scale).roundToInt().coerceIn(0, 100)
}

private val DEVICE_STATE_VARIABLES = setOf(
    "wifi",
    "wifi_ssid",
    "wifi_connected",
    "internet",
    "network_type",
    "vpn",
)
