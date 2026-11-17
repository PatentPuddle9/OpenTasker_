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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import com.opentasker.core.marketplace.GitHubDeviceAuth
import com.opentasker.core.marketplace.GitHubDeviceChallenge
import com.opentasker.core.marketplace.MarketplaceContributionCandidate
import com.opentasker.core.marketplace.MarketplaceContributionClient
import com.opentasker.core.marketplace.MarketplaceContributionKind
import com.opentasker.core.marketplace.MarketplacePackage
import com.opentasker.core.marketplace.allowedMarketplaceVersions
import com.opentasker.core.marketplace.MarketplaceExternalDestinationDraft
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
    var version by remember(existingPackage?.version) {
        mutableStateOf(existingPackage?.let { allowedMarketplaceVersions(it.version).first() } ?: "1.0.0")
    }
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
            .onFailure { loadingError = it.message ?: "Couldn't load the Actions and Shortcut Widgets on this device." }
        account = GitHubDeviceAuth.verifySavedLogin(context)
        loadingCandidates = false
    }

    val visibleCandidates = candidates.filter { it.kind == selectedKind }
    val allowedVersions = existingPackage?.let { allowedMarketplaceVersions(it.version) } ?: listOf("1.0.0")
    val validVersion = version in allowedVersions
    val selected = visibleCandidates.firstOrNull { it.key == selectedKey }
    val requiresDisclosure = selected?.tasks?.any { task ->
        task.actions.any { it.type in setOf("http.request", "http.get", "http.post", "integration.home_assistant.webhook", "mqtt.publish", "download") }
    } == true

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
            errorMessage = "GitHub sign-in hasn't been configured for this build. The repository owner needs to add the OPENTASKER_GITHUB_OAUTH_CLIENT_ID repository Actions secret and rebuild."
            return
        }
        scope.launch {
            signingIn = true
            errorMessage = null
            challenge = null
            runCatching {
                val device = GitHubDeviceAuth.beginDeviceFlow()
                challenge = device
                // Open the GitHub device-authorization page automatically; the one-time code remains
                // visible here so the contributor can enter it in the browser.
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
                val external = if (requiresDisclosure || domains.isNotBlank() || provider.isNotBlank()) {
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
                errorMessage = error.message ?: "Marketplace submission failed."
            }
            submitting = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingPackage == null) "Contribute to Marketplace" else "Update published package") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    if (existingPackage == null) {
                        "Submit an enabled Action or an app-style Shortcut Widget. Widget Displays and Scene layouts aren't contribution types."
                    } else {
                        "Create the next version of ${existingPackage.name}. Choose the updated local Action or Shortcut Widget below. Published versions cannot be overwritten."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "A Shortcut Widget includes its linked Action and its icon, colors, gradient, and shape. The linked Action must be turned on before it can be uploaded.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Personal triggers and context rules are not shared. Imported copies start turned off so the recipient can review and configure them first.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("GitHub account", style = MaterialTheme.typography.titleSmall)
                if (account != null) {
                    Text("Signed in as @" + account, color = MaterialTheme.colorScheme.primary)
                    TextButton(
                        onClick = {
                            GitHubDeviceAuth.signOut(context)
                            account = null
                            challenge = null
                        },
                    ) { Text("Sign out") }
                } else {
                    Button(onClick = { startSignIn() }, enabled = !signingIn && GitHubDeviceAuth.isConfigured) {
                        Text(if (signingIn) "Waiting for GitHub approval…" else "Sign in with GitHub")
                    }
                    if (!GitHubDeviceAuth.isConfigured) {
                        Text(
                            "GitHub OAuth is not configured in this APK yet. Add the OPENTASKER_GITHUB_OAUTH_CLIENT_ID repository Actions secret and rebuild to enable sign-in.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    challenge?.let { device ->
                        Text("Enter this one-time code on GitHub:", style = MaterialTheme.typography.bodySmall)
                        Text(device.userCode, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("GitHub device code", device.userCode))
                            }) { Text("Copy code") }
                            OutlinedButton(onClick = { openUrl(device.verificationUri) }) { Text("Open GitHub") }
                        }
                        Text("Approve this request in your browser. OpenTasker will finish signing in automatically.", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Text("What are you uploading?", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            selectedKind = MarketplaceContributionKind.ACTION
                            selectedKey = null
                        },
                        enabled = existingPackage == null,
                    ) { Text("Action") }
                    OutlinedButton(
                        onClick = {
                            selectedKind = MarketplaceContributionKind.SHORTCUT_WIDGET
                            selectedKey = null
                        },
                        enabled = existingPackage == null,
                    ) { Text("Shortcut Widget") }
                }

                when {
                    loadingCandidates -> CircularProgressIndicator()
                    loadingError != null -> Text(loadingError.orEmpty(), color = MaterialTheme.colorScheme.error)
                    visibleCandidates.isEmpty() -> Text(
                        if (selectedKind == MarketplaceContributionKind.ACTION) {
                            "No eligible Actions found. Turn on an Action and make sure it contains at least one action block."
                        } else {
                            "No eligible Shortcut Widgets found. Add the app-style widget to your home screen, then turn on its linked Action in the Actions tab."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> {
                        Text("Choose an enabled " + selectedKind.label, style = MaterialTheme.typography.titleSmall)
                        visibleCandidates.forEach { candidate ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { choose(candidate) }.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selectedKey == candidate.key,
                                    onClick = { choose(candidate) },
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(candidate.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        candidate.actionCount.toString() + " action block(s)",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }

                Text("Package details", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("Package name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = packageId,
                    onValueChange = { packageId = it.lowercase().replace(Regex("[^a-z0-9._-]"), "-") },
                    label = { Text("Package ID (lowercase)") },
                    supportingText = if (existingPackage != null) { { Text("Package IDs cannot be changed after publication.") } } else null,
                    enabled = existingPackage == null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = version,
                    onValueChange = { version = it },
                    label = { Text(if (existingPackage == null) "Initial version" else "Next version") },
                    supportingText = {
                        Text(
                            if (existingPackage == null) {
                                "New packages start at 1.0.0."
                            } else {
                                "From ${existingPackage.version}, the next valid versions are ${allowedVersions.joinToString()}."
                            },
                        )
                    },
                    isError = !validVersion,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    label = { Text("Short description") },
                    supportingText = { Text("Up to 400 characters.") },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (requiresDisclosure || domains.isNotBlank()) {
                    Text("External-service disclosure", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "The Marketplace validator requires a disclosure for network, webhook, MQTT, and download Actions. Describe every external destination.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(provider, { provider = it }, label = { Text("Service or provider") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(domains, { domains = it }, label = { Text("Hostnames (comma-separated)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(hostingCountry, { hostingCountry = it }, label = { Text("Hosting country (or Unknown)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(privacyPolicyUrl, { privacyPolicyUrl = it }, label = { Text("Privacy policy URL (HTTPS)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(dataFields, { dataFields = it }, label = { Text("Data fields sent (comma-separated)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(purpose, { purpose = it }, label = { Text("Purpose") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(privacyAssessment, { privacyAssessment = it }, label = { Text("Assessment: not-verified / verified / known-concerns") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(riskNotes, { riskNotes = it }, label = { Text("Additional risk notes (optional)") }, modifier = Modifier.fillMaxWidth())
                }
                if (errorMessage != null) {
                    Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (pullRequestUrl != null) {
                    Text(
                        if (existingPackage == null) {
                            "Pull request #" + pullRequestNumber + " created. The Marketplace's automated checks and any required review still need to pass."
                        } else {
                            "Update pull request #" + pullRequestNumber + " created. A trusted Marketplace moderator must approve this update before it can be published."
                        },
                        color = MaterialTheme.colorScheme.primary,
                    )
                    TextButton(onClick = { openUrl(pullRequestUrl!!) }) { Text("View pull request") }
                } else {
                    Text(
                        if (existingPackage == null) {
                            "Your package will be exported as data-only JSON, uploaded on a separate branch, and submitted as a pull request."
                        } else {
                            "The updated package will be submitted as a new immutable version. It will not replace older versions, and it will remain unpublished until another authorized repository collaborator approves the pull request. High-risk updates also need trusted-moderator approval."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            if (pullRequestUrl != null) {
                TextButton(onClick = onDismiss) { Text("Done") }
            } else {
                Button(
                    onClick = { submit() },
                    enabled = account != null && selected != null && !submitting && packageName.isNotBlank() && packageId.isNotBlank() && validVersion && summary.isNotBlank(),
                ) {
                    if (submitting) CircularProgressIndicator()
                    else Text(if (existingPackage == null) "Create pull request" else "Submit update for review")
                }
            }
        },
        dismissButton = {
            if (pullRequestUrl == null) TextButton(onClick = onDismiss, enabled = !submitting) { Text("Cancel") }
        },
    )
}
