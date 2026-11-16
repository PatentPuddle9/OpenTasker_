package com.opentasker.ui.screens

import com.opentasker.app.BuildConfig
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
import com.opentasker.core.actions.ActionArgumentSensitivity
import com.opentasker.core.actions.ActionMetadataRegistry
import com.opentasker.core.model.ActionSpec
import com.opentasker.core.model.Profile
import com.opentasker.core.transfer.OpenTaskerBundle
import kotlinx.coroutines.launch

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
    var showContributionDialog by remember { mutableStateOf(false) }
    var selectedPreviewPackage by remember { mutableStateOf<MarketplacePackage?>(null) }
    var packagePreview by remember { mutableStateOf<OpenTaskerBundle?>(null) }
    var previewingPackageId by remember { mutableStateOf<String?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }
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

    fun preview(packageEntry: MarketplacePackage) {
        scope.launch {
            previewingPackageId = packageEntry.id
            selectedPreviewPackage = packageEntry
            packagePreview = null
            previewError = null
            runCatching { MarketplaceClient.previewPackage(packageEntry) }
                .onSuccess { bundle -> packagePreview = bundle }
                .onFailure { error ->
                    previewError = error.message ?: "Couldn't verify and preview this package."
                }
            previewingPackageId = null
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
                    "Browse reusable Actions and app-style Shortcut Widgets. Widget Displays and Scene layouts aren't Marketplace contribution types. Every package still goes through OpenTasker's normal import and permission review.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { refresh() }, enabled = !loading) {
                        Text(if (loading) "Refreshing…" else "Refresh catalogue")
                    }
                    OutlinedButton(
                        onClick = { showContributionDialog = true },
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
                previewing = previewingPackageId == packageEntry.id,
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
                onPreview = { preview(packageEntry) },
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

    selectedPreviewPackage?.let { packageEntry ->
        MarketplacePackagePreviewDialog(
            packageEntry = packageEntry,
            bundle = packagePreview,
            loading = previewingPackageId == packageEntry.id,
            errorMessage = previewError,
            onDismiss = {
                selectedPreviewPackage = null
                packagePreview = null
                previewError = null
            },
            onInstall = {
                selectedPreviewPackage = null
                packagePreview = null
                if (packageEntry.requiredInputs.isEmpty()) {
                    install(packageEntry, emptyMap())
                } else {
                    inputPackage = packageEntry
                }
            },
        )
    }

    inputPackage?.let { packageEntry ->
        MarketplaceInputsDialog(
            packageEntry = packageEntry,
            onDismiss = { inputPackage = null },
            onInstall = { values -> install(packageEntry, values) },
        )
    }

    if (showContributionDialog) {
        MarketplaceContributionDialog(onDismiss = { showContributionDialog = false })
    }
}

@Composable
private fun MarketplacePackageCard(
    packageEntry: MarketplacePackage,
    installing: Boolean,
    previewing: Boolean,
    compatible: Boolean,
    protectionEnabled: Boolean,
    locallyDisabled: Boolean,
    onProtectionChanged: (Boolean) -> Unit,
    onClearDisabled: () -> Unit,
    onPreview: () -> Unit,
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
                OutlinedButton(onClick = onPreview, enabled = !previewing && !installing) {
                    Text(if (previewing) "Loading…" else "Preview")
                }
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

@Composable
private fun MarketplacePackagePreviewDialog(
    packageEntry: MarketplacePackage,
    bundle: OpenTaskerBundle?,
    loading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
) {
    val context = LocalContext.current
    val taskNames = remember(bundle) { bundle?.tasks?.associate { it.id to it.name }.orEmpty() }
    val blockCount = remember(bundle) { bundle?.tasks?.sumOf { it.actions.size } ?: 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(packageEntry.name)
                Text(
                    "Package preview · v${packageEntry.version}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            when {
                loading -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator()
                    Text("Downloading and verifying package contents…")
                }
                errorMessage != null -> Text(
                    errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                bundle != null -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            "${bundle.tasks.size} flows · ${bundle.profiles.size} triggers · $blockCount blocks",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            "This is a read-only preview. Nothing runs during preview.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (bundle.profiles.isNotEmpty()) {
                        item {
                            Text("Automations and triggers", style = MaterialTheme.typography.titleMedium)
                        }
                        items(bundle.profiles, key = { "profile:${it.id}" }) { profile ->
                            ProfilePreviewCard(profile = profile, taskNames = taskNames)
                        }
                    }

                    item {
                        Text("Action flows and blocks", style = MaterialTheme.typography.titleMedium)
                    }
                    if (bundle.tasks.isEmpty()) {
                        item { Text("This package contains no action blocks.") }
                    }
                    items(bundle.tasks, key = { "task:${it.id}" }) { task ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(task.name, style = MaterialTheme.typography.titleSmall)
                                if (task.actions.isEmpty()) {
                                    Text(
                                        "No blocks in this flow.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                task.actions.forEachIndexed { index, action ->
                                    ActionPreviewBlock(index = index + 1, action = action)
                                }
                            }
                        }
                    }

                    if (bundle.variables.isNotEmpty() || bundle.scenes.isNotEmpty()) {
                        item {
                            Text(
                                "Also includes ${bundle.variables.size} variables and ${bundle.scenes.size} scenes.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    bundle.metadata.shortcutWidget?.let { widget ->
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text("Shortcut widget", style = MaterialTheme.typography.titleSmall)
                                    Text(widget.name)
                                    Text(
                                        "Icon: ${widget.icon} · Shape: ${widget.shape} · Gradient: ${if (widget.gradient) "On" else "Off"}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Text(
                            "Imported profiles start disabled. Review the normal import and permission screens before enabling anything.",
                            color = MaterialTheme.colorScheme.tertiary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onInstall, enabled = !loading && bundle != null) {
                Text("Continue to install")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun ProfilePreviewCard(
    profile: Profile,
    taskNames: Map<Long, String>,
) {
    val triggerNames = profile.contexts.map { trigger ->
        trigger.type.name
            .lowercase()
            .replace('_', ' ')
            .replaceFirstChar { it.uppercase() } +
            if (trigger.invert) " (inverted)" else ""
    }
    val starts = taskNames[profile.enterTaskId] ?: "No linked flow"
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(profile.name, style = MaterialTheme.typography.titleSmall)
            Text("Runs: $starts", style = MaterialTheme.typography.bodySmall)
            Text(
                "When: ${triggerNames.takeIf { it.isNotEmpty() }?.joinToString() ?: "No automatic trigger configured"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionPreviewBlock(
    index: Int,
    action: ActionSpec,
) {
    val context = LocalContext.current
    val metadata = remember(action.type) { ActionMetadataRegistry.get(action.type) }
    val title = remember(metadata, action.type) {
        metadata?.let { runCatching { context.getString(it.nameRes) }.getOrNull() }
            ?: action.type
                .split('.', '_', '-')
                .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
    }
    val description = remember(metadata) {
        metadata?.let { runCatching { context.getString(it.descriptionRes) }.getOrNull() }.orEmpty()
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    index.toString(),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        action.type,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (description.isNotBlank()) {
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
            if (action.args.isNotEmpty()) {
                action.args.entries.sortedBy { it.key }.forEach { (key, value) ->
                    val fieldLabel = metadata?.fields
                        ?.firstOrNull { it.key.equals(key, ignoreCase = true) }
                        ?.let { field -> runCatching { context.getString(field.labelRes) }.getOrNull() }
                        ?: key.split('_', '-').joinToString(" ") { it.replaceFirstChar { ch -> ch.uppercase() } }
                    val shownValue = safePreviewArgumentValue(action, key, value)
                    Text(
                        "$fieldLabel: $shownValue",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!action.enabled) {
                Text("Disabled block", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall)
            }
            if (!action.condition.isNullOrBlank()) {
                Text("Has a run condition", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun safePreviewArgumentValue(
    action: ActionSpec,
    key: String,
    value: String,
): String {
    val normalized = value.trim()
    if (normalized.contains("{{input:", ignoreCase = true)) {
        return "Provided during installation"
    }
    if (
        normalized.startsWith("content://", ignoreCase = true) ||
        normalized.startsWith("file://", ignoreCase = true) ||
        normalized.startsWith("/") ||
        Regex("https?://", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
    ) {
        return "Configured (device-specific value hidden)"
    }
    return ActionArgumentSensitivity.maskValue(
        actionType = action.type,
        argName = key,
        value = value,
        args = action.args,
        maxLength = 64,
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
