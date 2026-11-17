package com.opentasker.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.opentasker.core.actions.ActionMetadataRegistry
import com.opentasker.core.marketplace.GitHubDeviceAuth
import com.opentasker.core.marketplace.GitHubDeviceChallenge
import com.opentasker.core.marketplace.MarketplaceContributionCandidate
import com.opentasker.core.marketplace.MarketplaceContributionClient
import com.opentasker.core.marketplace.MarketplaceContributionKind
import com.opentasker.core.marketplace.MarketplacePackage
import com.opentasker.core.marketplace.MarketplaceExternalDestinationDraft
import com.opentasker.core.marketplace.allowedMarketplaceVersions
import kotlinx.coroutines.launch

@Composable
internal fun MarketplaceContributionDialog(
    onDismiss: () -> Unit,
    existingPackage: MarketplacePackage? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var candidates by remember { mutableStateOf<List<MarketplaceContributionCandidate>>(emptyList()) }
    var loadingCandidates by remember { mutableStateOf(true) }
    var loadingError by remember { mutableStateOf<String?>(null) }
    var account by remember { mutableStateOf<String?>(null) }
    var signingIn by remember { mutableStateOf(false) }
    var challenge by remember { mutableStateOf<GitHubDeviceChallenge?>(null) }
    var step by remember(existingPackage?.id) { mutableStateOf(0) }
    var selectedKind by remember(existingPackage?.id) {
        mutableStateOf(
            if (existingPackage?.category == "Shortcut Widgets") {
                MarketplaceContributionKind.SHORTCUT_WIDGET
            } else {
                MarketplaceContributionKind.ACTION
            },
        )
    }
    var selectedKey by remember { mutableStateOf<String?>(null) }
    var packageName by remember(existingPackage?.id) { mutableStateOf(existingPackage?.name.orEmpty()) }
    var packageId by remember(existingPackage?.id) { mutableStateOf(existingPackage?.id.orEmpty()) }
    val allowedVersions = remember(existingPackage?.version) {
        existingPackage?.let { allowedMarketplaceVersions(it.version) } ?: listOf("1.0.0")
    }
    var version by remember(existingPackage?.version) { mutableStateOf(allowedVersions.first()) }
    var summary by remember(existingPackage?.id) { mutableStateOf(existingPackage?.summary.orEmpty()) }
    var provider by remember { mutableStateOf("") }
    var domains by remember { mutableStateOf("") }
    var hostingCountry by remember { mutableStateOf("Unknown") }
    var privacyPolicyUrl by remember { mutableStateOf("") }
    var dataFields by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("") }
    var privacyAssessment by remember { mutableStateOf("not-verified") }
    var riskNotes by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pullRequestUrl by remember { mutableStateOf<String?>(null) }
    var pullRequestNumber by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        loadingCandidates = true
        runCatching { MarketplaceContributionClient.loadEligibleCandidates(context) }
            .onSuccess { candidates = it }
            .onFailure { loadingError = it.message ?: "Couldn't load your eligible Actions and Shortcut Widgets." }
        account = GitHubDeviceAuth.verifySavedLogin(context)
        loadingCandidates = false
    }

    val visibleCandidates = candidates.filter { it.kind == selectedKind }
    val selected = visibleCandidates.firstOrNull { it.key == selectedKey }
    val requiresDisclosure = selected?.tasks?.any { task ->
        task.actions.any {
            it.type in setOf(
                "http.request", "http.get", "http.post",
                "integration.home_assistant.webhook", "mqtt.publish", "download",
            )
        }
    } == true
    val validPackageId = packageId.matches(Regex("^[a-z0-9][a-z0-9._-]{1,79}$"))
    val validDetails = packageName.isNotBlank() &&
        packageName.length <= 80 &&
        validPackageId &&
        summary.isNotBlank() &&
        summary.length <= 400 &&
        version in allowedVersions
    val validDisclosure = !requiresDisclosure || (
        provider.isNotBlank() &&
            domains.split(',', ';').any { it.isNotBlank() } &&
            hostingCountry.isNotBlank() &&
            privacyPolicyUrl.startsWith("https://") &&
            dataFields.split(',').any { it.isNotBlank() } &&
            purpose.isNotBlank() &&
            privacyAssessment in setOf("verified", "not-verified", "known-concerns")
        )
    val canReview = validDetails && validDisclosure

    fun choose(candidate: MarketplaceContributionCandidate) {
        selectedKey = candidate.key
        if (packageName.isBlank()) packageName = candidate.name
        if (summary.isBlank()) summary = candidate.name
        if (packageId.isBlank()) {
            val slug = candidate.name.lowercase()
                .replace(Regex("[^a-z0-9._-]+"), "-")
                .trim('-', '.', '_')
                .take(70)
                .ifBlank { "my-opentasker-package" }
            packageId = if (slug.length == 1) slug + "-package" else slug
        }
    }

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { errorMessage = it.message ?: "Couldn't open the browser." }
    }

    fun startSignIn() {
        if (!GitHubDeviceAuth.isConfigured) {
            errorMessage = "GitHub sign-in isn't configured in this build. The app needs its GitHub OAuth Client ID."
            return
        }
        scope.launch {
            signingIn = true
            errorMessage = null
            challenge = null
            runCatching {
                val device = GitHubDeviceAuth.beginDeviceFlow()
                challenge = device
                openUrl(device.verificationUri)
                val token = GitHubDeviceAuth.waitForAuthorization(device)
                GitHubDeviceAuth.finishSignIn(context, token)
            }.onSuccess { login ->
                account = login
                challenge = null
            }.onFailure { error ->
                errorMessage = error.message ?: "GitHub sign-in failed."
                challenge = null
            }
            signingIn = false
        }
    }

    fun submit() {
        val candidate = selected ?: return
        scope.launch {
            submitting = true
            errorMessage = null
            runCatching {
                val external = if (requiresDisclosure) {
                    MarketplaceExternalDestinationDraft(
                        provider = provider,
                        domains = domains,
                        hostingCountry = hostingCountry,
                        privacyPolicyUrl = privacyPolicyUrl,
                        dataFields = dataFields,
                        purpose = purpose,
                        privacyAssessment = privacyAssessment,
                        riskNotes = riskNotes,
                    )
                } else null
                MarketplaceContributionClient.submit(
                    context = context,
                    candidate = candidate,
                    packageId = packageId,
                    packageName = packageName,
                    version = version,
                    summary = summary,
                    disclosure = external,
                    previousVersion = existingPackage?.version,
                )
            }.onSuccess { pull ->
                pullRequestNumber = pull.number
                pullRequestUrl = pull.url
            }.onFailure { error ->
                errorMessage = error.message ?: "Couldn't submit the Marketplace package."
            }
            submitting = false
        }
    }

    val title = when {
        pullRequestUrl != null -> "Submission created"
        existingPackage != null -> "Update published package"
        else -> "Publish to Marketplace"
    }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (pullRequestUrl != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Your package was sent to GitHub successfully.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text("Pull request #${pullRequestNumber}")
                        Text(
                            if (existingPackage == null) {
                                "The package will appear in the Marketplace after the required checks pass and a maintainer merges it."
                            } else {
                                "This update is not live yet. It needs an independent approval and must be merged before the new version appears."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedButton(onClick = { openUrl(pullRequestUrl!!) }) {
                            Text("Open pull request")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf("Choose", "Details", "Review").forEachIndexed { index, label ->
                            Text(
                                text = if (index == step) "● $label" else "${index + 1}. $label",
                                modifier = Modifier.weight(1f),
                                style = if (index == step) {
                                    MaterialTheme.typography.labelLarge
                                } else {
                                    MaterialTheme.typography.labelMedium
                                },
                                color = if (index == step) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }

                    Card {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (account != null) {
                                Text("Publishing as @${account}", style = MaterialTheme.typography.labelLarge)
                                TextButton(
                                    onClick = {
                                        GitHubDeviceAuth.signOut(context)
                                        account = null
                                        challenge = null
                                    },
                                ) { Text("Switch account") }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("GitHub account", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "Sign in to submit your package as a pull request.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Button(
                                        onClick = { startSignIn() },
                                        enabled = !signingIn && GitHubDeviceAuth.isConfigured,
                                    ) {
                                        Text(if (signingIn) "Waiting…" else "Sign in")
                                    }
                                }
                                if (!GitHubDeviceAuth.isConfigured) {
                                    Text(
                                        "Publishing isn't available in this build because GitHub sign-in isn't configured.",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                challenge?.let { device ->
                                    Text("Enter this code on GitHub", style = MaterialTheme.typography.bodySmall)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            device.userCode,
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        OutlinedButton(onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("GitHub device code", device.userCode))
                                        }) {
                                            Text("Copy")
                                        }
                                    }
                                    TextButton(onClick = { openUrl(device.verificationUri) }) {
                                        Text("Open GitHub sign-in")
                                    }
                                }
                            }
                        }
                    }

                    when (step) {
                        0 -> {
                            Text(
                                if (existingPackage == null) {
                                    "Choose the reusable Action or home-screen Shortcut Widget you want to publish."
                                } else {
                                    "Choose the updated local Action or Shortcut Widget for this package."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (existingPackage == null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            selectedKind = MarketplaceContributionKind.ACTION
                                            selectedKey = null
                                        },
                                    ) { Text("Action") }
                                    OutlinedButton(
                                        onClick = {
                                            selectedKind = MarketplaceContributionKind.SHORTCUT_WIDGET
                                            selectedKey = null
                                        },
                                    ) { Text("Shortcut Widget") }
                                }
                            }
                            when {
                                loadingCandidates -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator()
                                    Text("Loading eligible items…")
                                }
                                loadingError != null -> Text(
                                    loadingError.orEmpty(),
                                    color = MaterialTheme.colorScheme.error,
                                )
                                visibleCandidates.isEmpty() -> Text(
                                    if (selectedKind == MarketplaceContributionKind.ACTION) {
                                        "No eligible Actions found. Turn on an Action and make sure it contains at least one block."
                                    } else {
                                        "No eligible Shortcut Widgets found. Add the widget to your home screen and turn on its linked Action."
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                else -> {
                                    visibleCandidates.forEach { candidate ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { choose(candidate) },
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                RadioButton(
                                                    selected = selectedKey == candidate.key,
                                                    onClick = { choose(candidate) },
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(candidate.name, style = MaterialTheme.typography.titleSmall)
                                                    Text(
                                                        "${candidate.tasks.size} flow(s) · ${candidate.actionCount} blocks",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            Text(
                                "Give the Marketplace listing a clear name and description. The package ID stays fixed after first publication.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            OutlinedTextField(
                                value = packageName,
                                onValueChange = { packageName = it },
                                label = { Text("Package name") },
                                supportingText = { Text("${packageName.length}/80 characters") },
                                isError = packageName.isBlank() || packageName.length > 80,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = packageId,
                                onValueChange = {
                                    packageId = it.lowercase().replace(Regex("[^a-z0-9._-]"), "-")
                                },
                                label = { Text("Package ID") },
                                supportingText = {
                                    Text(if (existingPackage == null) "Lowercase ID; cannot be changed later." else "Fixed after publication.")
                                },
                                enabled = existingPackage == null,
                                isError = !validPackageId,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = summary,
                                onValueChange = { summary = it.take(400) },
                                label = { Text("Short description") },
                                supportingText = { Text("${summary.length}/400") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 4,
                            )
                            Text(
                                if (existingPackage == null) "Starting version" else "Choose the next version",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (existingPackage != null) {
                                Text(
                                    "Current version: ${existingPackage.version}. Only one version component can increase by one step.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                allowedVersions.forEach { choice ->
                                    OutlinedButton(
                                        onClick = { version = choice },
                                        enabled = !submitting,
                                    ) {
                                        Text(if (version == choice) "✓ $choice" else choice)
                                    }
                                }
                            }
                            if (version !in allowedVersions) {
                                Text("Choose one of the allowed next versions.", color = MaterialTheme.colorScheme.error)
                            }

                            if (requiresDisclosure) {
                                Text("External-service disclosure", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "This Action sends data to an external service. These details are shown to users before installation.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedTextField(provider, { provider = it }, label = { Text("Service or provider") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(domains, { domains = it }, label = { Text("Hostnames (comma-separated)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(hostingCountry, { hostingCountry = it }, label = { Text("Hosting country (or Unknown)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(privacyPolicyUrl, { privacyPolicyUrl = it }, label = { Text("Privacy policy URL (HTTPS)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(dataFields, { dataFields = it }, label = { Text("Data fields sent (comma-separated)") }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(purpose, { purpose = it }, label = { Text("Purpose") }, modifier = Modifier.fillMaxWidth())
                                Text("Privacy assessment", style = MaterialTheme.typography.labelLarge)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(
                                        "not-verified" to "Not verified",
                                        "verified" to "Verified",
                                    ).forEach { (value, label) ->
                                        FilterChip(
                                            selected = privacyAssessment == value,
                                            onClick = { privacyAssessment = value },
                                            label = { Text(label) },
                                        )
                                    }
                                }
                                FilterChip(
                                    selected = privacyAssessment == "known-concerns",
                                    onClick = { privacyAssessment = "known-concerns" },
                                    label = { Text("Known concerns") },
                                )
                                OutlinedTextField(riskNotes, { riskNotes = it }, label = { Text("Additional risk notes (optional)") }, modifier = Modifier.fillMaxWidth())
                            }
                        }

                        else -> {
                            Text("Review your submission", style = MaterialTheme.typography.titleMedium)
                            Card {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(packageName, style = MaterialTheme.typography.titleSmall)
                                    Text(packageId, style = MaterialTheme.typography.labelSmall)
                                    Text("Version $version · ${selectedKind.label}")
                                    Text(summary)
                                    Text("${selected?.tasks?.size ?: 0} flow(s) · ${selected?.actionCount ?: 0} blocks")
                                    Text("Publisher: @${account ?: "not signed in"}")
                                }
                            }
                            Text(
                                "Personal triggers and context rules are not shared. Imported profiles start disabled and recipients review them before enabling.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text("Included blocks", style = MaterialTheme.typography.titleSmall)
                            selected?.tasks?.forEach { task ->
                                Card {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(task.name, style = MaterialTheme.typography.labelLarge)
                                        task.actions.forEachIndexed { index, action ->
                                            val blockName = ActionMetadataRegistry.get(action.type)
                                                ?.let { metadata -> runCatching { context.getString(metadata.nameRes) }.getOrNull() }
                                                ?: action.type
                                                    .split('.', '_', '-')
                                                    .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
                                            Text(
                                                "${index + 1}. $blockName",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                            if (requiresDisclosure) {
                                Text(
                                    "External service: $provider · ${domains.split(',', ';').filter { it.isNotBlank() }.joinToString()}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text("Disclosure: $purpose", style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                if (existingPackage == null) {
                                    "Publishing creates a GitHub pull request. The package goes live only after required checks and maintainer merge. High-risk packages need moderator approval."
                                } else {
                                    "This is a new immutable version. It needs an independent approval and maintainer merge before it replaces the current listing."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            when {
                pullRequestUrl != null -> TextButton(onClick = onDismiss) { Text("Done") }
                submitting -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text("Submitting…")
                }
                step == 0 -> Button(
                    onClick = { step = 1 },
                    enabled = selected != null && !loadingCandidates && account != null,
                ) { Text("Continue") }
                step == 1 -> Button(
                    onClick = {
                        errorMessage = null
                        step = 2
                    },
                    enabled = canReview && account != null,
                ) { Text("Review submission") }
                else -> Button(
                    onClick = { submit() },
                    enabled = canReview && selected != null && account != null,
                ) { Text("Submit pull request") }
            }
        },
        dismissButton = {
            if (pullRequestUrl == null && !submitting) {
                TextButton(
                    onClick = { if (step > 0) step-- else onDismiss() },
                ) {
                    Text(if (step > 0) "Back" else "Cancel")
                }
            }
        },
    )
}
