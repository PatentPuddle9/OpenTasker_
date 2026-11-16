package com.opentasker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.opentasker.core.marketplace.MarketplacePackageWatchdog

@Composable
internal fun MarketplaceSafetySettingsCard() {
    val context = LocalContext.current.applicationContext
    val initial = remember { MarketplacePackageWatchdog.settings(context) }
    var enabled by remember { mutableStateOf(initial.enabled) }
    var threshold by remember { mutableStateOf(initial.maxExecutions.toFloat()) }
    var window by remember { mutableStateOf(initial.windowSeconds.toFloat()) }
    var disabledPackages by remember { mutableStateOf(initial.disabledPackageIds) }

    fun save() {
        MarketplacePackageWatchdog.updateSettings(
            context = context,
            enabled = enabled,
            maxExecutions = threshold.toInt(),
            windowSeconds = window.toInt(),
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Marketplace package protection", style = MaterialTheme.typography.titleMedium)
            Text(
                "Automatically disable a package if it triggers too frequently. The lock remains until you clear it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Enable rapid-fire protection")
                Switch(checked = enabled, onCheckedChange = {
                    enabled = it
                    save()
                })
            }
            Text("Disable after ${threshold.toInt()} executions within ${window.toInt()} seconds")
            Slider(
                value = threshold,
                onValueChange = { threshold = it },
                onValueChangeFinished = ::save,
                valueRange = 2f..100f,
                steps = 97,
                enabled = enabled,
            )
            Slider(
                value = window,
                onValueChange = { window = it },
                onValueChangeFinished = ::save,
                valueRange = 10f..300f,
                steps = 28,
                enabled = enabled,
            )
            Text(
                "These settings apply to packages that have not disabled protection individually. Default: 10 executions in 60 seconds.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (disabledPackages.isNotEmpty()) {
                Text("Disabled packages", style = MaterialTheme.typography.titleSmall)
                disabledPackages.forEach { packageId ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("!  $packageId", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = {
                            MarketplacePackageWatchdog.clearDisabled(context, packageId)
                            disabledPackages = MarketplacePackageWatchdog.disabledPackageIds(context)
                        }) { Text("Clear lock") }
                    }
                }
                Text(
                    "Clearing a lock does not re-enable the package's profiles. Check them before turning them back on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
