package com.opentasker.ui.screens

import com.opentasker.BuildConfig
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.opentasker.core.marketplace.MarketplaceClient
import com.opentasker.core.marketplace.MarketplaceCatalog
import com.opentasker.core.marketplace.MarketplaceExternalDestination
import com.opentasker.core.marketplace.MarketplaceInput
import com.opentasker.core.marketplace.MarketplacePackage
import com.opentasker.core.marketplace.MarketplacePackageWatchdog
import kotlinx.coroutines.launch

private const val MARKETPLACE_CONTRIBUTING_URL =
    "https://github.com/PatentPuddle9/OpenTasker_Makrketplace/blob/main/CONTRIBUTING.md"

@Composable
internal fun MarketplaceScreen(
    contentPadding: PaddingValues,
    onInstallBundle: (String) -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    var catalog by remember { mutableStateOf<MarketplaceCatalog?>(null) }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var fromCache by remember { mutableStateOf(false) }
    var installingPackageId by remember { mutableStateOf<String?>(null) }
    var inputPackage by remember { mutableStateOf<MarketplacePackage?>(null) }
    var watchdogStatus by remember { mutableStateOf<Map<String, Pair<Boolean, Boolean>>>(emptyMap()) }

    fun refresh() {
        scope.launch {
            loading = true
            errorMessage = null
            runCatching { MarketplaceClient.loadCatalog(appContext) }
                .onSuccess { result ->
                    catalog = result.catalog
                    fromCache = result.fromCache
                    watchdogStatus = result.catalog.packages.associate { item ->
                        item.id to (
                            MarketplacePackageWatchdog.packageProtectionEnabled(appContext, item.id) to
                                MarketplacePackageWatchdog.isDisabled(appContext, item.id)
                            )
                    }
                }
                .onFailure { error ->
                    errorMessage = error.message ?: "Couldn't verify the marketplace catalogue."
                }
            loading = false
        }
    }

    fun install(packageEntry: MarketplacePackage, values: Map<String, String>) {
        scope.launch {
            installingPackageId = packageEntry.id
            errorMessage = null
            runCatching {
                MarketplaceClient.downloadAndConfigure(packageEntry, values)
            }.onSuccess { bundle ->
                inputPackage = null
                onInstallBundle(bundle)
            }.onFailure { error ->
                errorMessage = error.message ?: "Package download or validation failed."
            }
            installingPackageId = null
        }
    }

    LaunchedEffect(Unit) { refresh() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Community marketplace",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "Browse reusable actions, automations, and Widget Display scenes. Every package still goes through OpenTasker's normal import and permission review.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { refresh() }, enabled = !loading) {
                        Text(if (loading) "Refreshing…" else "Refresh catalogue")
                    }
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MARKETPLACE_CONTRIBUTING_URL)))
                            }
                        },
                    ) {
                        Text("Contribute")
                    }
                }
                if (fromCache) {
                    Text(
                        "Showing the last verified catalogue saved on this device. Refresh when GitHub is available.",
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (loading && catalog == null) {
                    CircularProgressIndicator()
                }
                errorMessage?.let { message ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            message,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        val packages = catalog?.packages.orEmpty()
        if (catalog != null && packages.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("No packages published yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "The official marketplace is ready for submissions. Once the signing key is configured and packages are approved, they will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        items(packages, key = { "${it.id}:${it.version}" }) { packageEntry ->
            MarketplacePackageCard(
                packageEntry = packageEntry,
                installing = installingPackageId == packageEntry.id,
                compatible = isVersionAtLeast(BuildConfig.VERSION_NAME, packageEntry.minimumAppVersion),
                protectionEnabled = watchdogStatus[packageEntry.id]?.first ?: true,
                locallyDisabled = watchdogStatus[packageEntry.id]?.second ?: false,
                onProtectionChanged = { enabled ->
                    MarketplacePackageWatchdog.setPackageProtectionEnabled(appContext, packageEntry.id, enabled)
                    val wasDisabled = watchdogStatus[packageEntry.id]?.second ?: false
                    watchdogStatus = watchdogStatus + (packageEntry.id to (enabled to wasDisabled))
                },
                onClearDisabled = {
                    MarketplacePackageWatchdog.clearDisabled(appContext, packageEntry.id)
                    val isProtected = watchdogStatus[packageEntry.id]?.first ?: true
                    watchdogStatus = watchdogStatus + (packageEntry.id to (isProtected to false))
                },
                onInstall = {
                    if (packageEntry.requiredInputs.isEmpty()) {
                        install(packageEntry, emptyMap())
                    } else {
                        inputPackage = packageEntry
                    }
                },
                onReport = {
                    val title = Uri.encode("[Package report] ${packageEntry.id} ${packageEntry.version}")
                    val reportUrl =
                        "https://github.com/PatentPuddle9/OpenTasker_Makrketplace/issues/new?template=package-report.yml&title=$title"
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(reportUrl))) }
                },
            )
        }
    }

    inputPackage?.let { packageEntry ->
        MarketplaceInputsDialog(
            packageEntry = packageEntry,
            onDismiss = { inputPackage = null },
            onInstall = { values -> install(packageEntry, values) },
        )
    }
}

@Composable
private fun MarketplacePackageCard(
    packageEntry: MarketplacePackage,
    installing: Boolean,
    compatible: Boolean,
    protectionEnabled: Boolean,
    locallyDisabled: Boolean,
    onProtectionChanged: (Boolean) -> Unit,
    onClearDisabled: () -> Unit,
    onInstall: () -> Unit,
    onReport: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(packageEntry.name, style = MaterialTheme.typography.titleMedium)
            Text(packageEntry.summary, style = MaterialTheme.typography.bodyMedium)
            if (!compatible) {
                Text(
                    "Requires OpenTasker ${packageEntry.minimumAppVersion} or newer.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (locallyDisabled) {
                Text(
                    "! Disabled on this device after repeated rapid executions.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(
                "${packageEntry.category} · v${packageEntry.version} · by @${packageEntry.publisher}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            val highRisk = packageEntry.riskLevel.equals("high", ignoreCase = true)
            Text(
                text = "Risk: ${packageEntry.riskLevel.uppercase()}",
                color = if (highRisk) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.labelLarge,
            )
            if (highRisk && packageEntry.reviewStatus.equals("approved", ignoreCase = true)) {
                val reviewers = packageEntry.reviewedBy.joinToString().ifBlank { "marketplace moderators" }
                Text(
                    "High-risk action. Reviewed and approved by $reviewers on ${packageEntry.reviewedAt ?: "a recorded review date"}. Moderator approval does not remove the high-risk warning.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (packageEntry.reviewStatus.equals("automated", ignoreCase = true)) {
                Text(
                    "Automated validation passed. This is not a manual security audit.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Rapid-fire protection", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = protectionEnabled, onCheckedChange = onProtectionChanged)
            }
            if (locallyDisabled) {
                TextButton(onClick = onClearDisabled) { Text("Clear local safety lock") }
            }

            if (packageEntry.actionIds.isNotEmpty()) {
                Text(
                    "Actions: ${packageEntry.actionIds.joinToString()}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            packageEntry.externalDestinations.forEach { destination ->
                ExternalDestinationDisclosure(destination)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onInstall, enabled = !installing && !locallyDisabled && compatible) {
                    Text(if (installing) "Checking…" else "Install")
                }
                TextButton(onClick = onReport) {
                    Text("Report")
                }
            }
        }
    }
}

@Composable
private fun ExternalDestinationDisclosure(destination: MarketplaceExternalDestination) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text("External service: ${destination.provider}", style = MaterialTheme.typography.titleSmall)
            Text("Destination: ${destination.domain}", style = MaterialTheme.typography.bodySmall)
            Text("Publisher-declared hosting country: ${destination.hostingCountry}", style = MaterialTheme.typography.bodySmall)
            Text("Data sent: ${destination.dataFields.joinToString()}", style = MaterialTheme.typography.bodySmall)
            Text("Purpose: ${destination.purpose}", style = MaterialTheme.typography.bodySmall)
            Text("Publisher-declared privacy assessment: ${destination.privacyAssessment}", style = MaterialTheme.typography.bodySmall)
            if (destination.riskNotes.isNotBlank()) {
                Text(destination.riskNotes, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Text("Privacy policy: ${destination.privacyPolicyUrl}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MarketplaceInputsDialog(
    packageEntry: MarketplacePackage,
    onDismiss: () -> Unit,
    onInstall: (Map<String, String>) -> Unit,
) {
    var values by remember(packageEntry.id, packageEntry.version) {
        mutableStateOf(packageEntry.requiredInputs.associate { it.key to it.defaultValue })
    }
    val missingRequired = packageEntry.requiredInputs.any { input ->
        input.required && values[input.key].isNullOrBlank()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure ${packageEntry.name}") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        "Fill in the values this package needs. These values are applied locally after the signed package has been downloaded; they are not uploaded to the marketplace.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(packageEntry.requiredInputs, key = { it.key }) { input: MarketplaceInput ->
                    OutlinedTextField(
                        value = values[input.key].orEmpty(),
                        onValueChange = { next -> values = values + (input.key to next) },
                        label = { Text(input.label) },
                        supportingText = input.hint.takeIf { it.isNotBlank() }?.let { hint -> { Text(hint) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !missingRequired,
                onClick = { onInstall(values) },
            ) {
                Text("Continue to import review")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun isVersionAtLeast(current: String, required: String): Boolean {
    fun parts(value: String): List<Int>? {
        val pieces = value.substringBefore("-").substringBefore("+").split(".")
        if (pieces.size < 3) return null
        return pieces.take(3).map { it.toIntOrNull() ?: return null }
    }
    val currentParts = parts(current) ?: return false
    val requiredParts = parts(required) ?: return false
    for (index in 0..2) {
        if (currentParts[index] != requiredParts[index]) {
            return currentParts[index] > requiredParts[index]
        }
    }
    return true
}
