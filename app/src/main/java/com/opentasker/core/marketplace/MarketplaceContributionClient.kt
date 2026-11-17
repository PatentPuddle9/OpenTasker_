package com.opentasker.core.marketplace

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Base64
import com.opentasker.app.BuildConfig
import com.opentasker.app.OpenTaskerApp_NoHilt
import com.opentasker.core.model.DEFAULT_PROJECT_ID
import com.opentasker.core.model.Profile
import com.opentasker.core.model.Task
import com.opentasker.core.transfer.MarketplaceShortcutWidget
import com.opentasker.core.transfer.OpenTaskerBundleCodec
import com.opentasker.widget.TaskWidgetProvider
import com.opentasker.widget.WidgetStyle
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal enum class MarketplaceContributionKind(val label: String, val category: String, val wireValue: String) {
    ACTION("Action", "Actions", "action"),
    SHORTCUT_WIDGET("Shortcut Widget", "Shortcut Widgets", "shortcut_widget"),
}

internal data class MarketplaceContributionCandidate(
    val kind: MarketplaceContributionKind,
    val profile: Profile,
    val tasks: List<Task>,
    val widgetId: Int? = null,
    val widgetStyle: WidgetStyle? = null,
) {
    val key: String get() = kind.wireValue + ":" + profile.id + ":" + (widgetId ?: 0)
    val name: String get() = profile.name
    val actionCount: Int get() = tasks.sumOf { it.actions.size }
}

internal data class MarketplaceExternalDestinationDraft(
    val provider: String,
    val domains: String,
    val hostingCountry: String,
    val privacyPolicyUrl: String,
    val dataFields: String,
    val purpose: String,
    val privacyAssessment: String = "not-verified",
    val riskNotes: String = "",
)

internal data class MarketplacePullRequest(val number: Int, val url: String)

internal data class MarketplaceDeletionRequest(val number: Int, val url: String)

internal fun allowedMarketplaceVersions(current: String): List<String> {
    val match = Regex("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$").matchEntire(current)
        ?: throw IllegalArgumentException("The published version must use MAJOR.MINOR.PATCH format.")
    val major = match.groupValues[1].toInt()
    val minor = match.groupValues[2].toInt()
    val patch = match.groupValues[3].toInt()
    return listOf("$major.$minor.${patch + 1}", "$major.${minor + 1}.0", "${major + 1}.0.0")
}


internal object MarketplaceContributionClient {
    private const val REPOSITORY = "PatentPuddle9/OpenTasker_Makrketplace"
    private const val MAX_BUNDLE_BYTES = 8 * 1024 * 1024
    private val externalIds = setOf("http.request", "http.get", "http.post", "integration.home_assistant.webhook", "mqtt.publish", "download")
    private val email = Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b")
    private val phone = Regex("(?<!\\w)(?:\\+?1[ .-]?)?(?:\\(?\\d{3}\\)?[ .-]?)\\d{3}[ .-]?\\d{4}(?!\\w)")
    private val secret = Regex("(?i)\\b(?:bearer\\s+[A-Za-z0-9._~+/=-]{12,}|(?:sk|gh[pousr]?)_[A-Za-z0-9_-]{16,}|(?:api[_-]?key|access[_-]?token|client[_-]?secret|password)\\s*[:=]\\s*['\\\"]?[^\\s,'\\\"]{8,})")
    private val urlHost = Regex("(?i)https?://([^/:?#]+)")

    suspend fun loadEligibleCandidates(context: Context): List<MarketplaceContributionCandidate> =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val db = OpenTaskerApp_NoHilt.awaitDb()
            val profiles = db.profileDao().getAll().mapNotNull { entity ->
                val decoded = entity.toDomainDecodeResult()
                decoded.value.takeIf { decoded.issue == null && it.enabled && !it.requiresRiskAcknowledgement }
            }
            val result = mutableListOf<MarketplaceContributionCandidate>()

            suspend fun append(profile: Profile, kind: MarketplaceContributionKind, widgetId: Int? = null, style: WidgetStyle? = null) {
                if (profile.enterTaskId <= 0L) return
                val ids = buildList {
                    add(profile.enterTaskId)
                    profile.exitTaskId?.takeIf { it > 0L }?.let(::add)
                    profile.fallbackTaskId?.takeIf { it > 0L }?.let(::add)
                }.distinct()
                val tasks = mutableListOf<Task>()
                for (id in ids) {
                    val entity = db.taskDao().getById(id) ?: return
                    val decoded = entity.toDomainDecodeResult()
                    if (decoded.issue != null) return
                    tasks += decoded.value
                }
                if (tasks.isNotEmpty() && tasks.any { it.actions.isNotEmpty() }) {
                    result += MarketplaceContributionCandidate(kind, profile, tasks, widgetId, style)
                }
            }

            profiles.filter { it.widgetId == null }.forEach { append(it, MarketplaceContributionKind.ACTION) }

            val manager = AppWidgetManager.getInstance(app)
            val installed = manager.getAppWidgetIds(ComponentName(app, TaskWidgetProvider::class.java)).toSet()
            val prefs = app.getSharedPreferences(TaskWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
            installed.sorted().forEach { id ->
                val savedProfileId = prefs.getLong(TaskWidgetProvider.keyActionProfileId(id), -1L)
                val profile = profiles.firstOrNull { it.id == savedProfileId && it.widgetId == id }
                    ?: profiles.firstOrNull { it.widgetId == id }
                    ?: return@forEach
                val defaults = WidgetStyle()
                val style = WidgetStyle(
                    icon = prefs.getString(TaskWidgetProvider.keyIcon(id), defaults.icon) ?: defaults.icon,
                    iconColor = prefs.getInt(TaskWidgetProvider.keyIconColor(id), defaults.iconColor),
                    backgroundColor = prefs.getInt(TaskWidgetProvider.keyBackgroundColor(id), defaults.backgroundColor),
                    gradient = prefs.getBoolean(TaskWidgetProvider.keyGradient(id), defaults.gradient),
                    shape = prefs.getString(TaskWidgetProvider.keyShape(id), defaults.shape) ?: defaults.shape,
                )
                append(profile, MarketplaceContributionKind.SHORTCUT_WIDGET, id, style)
            }
            result.sortedWith(compareBy<MarketplaceContributionCandidate> { it.kind.ordinal }.thenBy { it.name.lowercase() })
        }

    suspend fun submit(
        context: Context,
        candidate: MarketplaceContributionCandidate,
        packageId: String,
        packageName: String,
        version: String,
        summary: String,
        disclosure: MarketplaceExternalDestinationDraft?,
        previousVersion: String? = null,
    ): MarketplacePullRequest = withContext(Dispatchers.IO) {
        val id = packageId.trim()
        val name = packageName.trim()
        val description = summary.trim()
        require(id.matches(Regex("^[a-z0-9][a-z0-9._-]{1,79}$"))) { "Package ID must be a lowercase slug." }
        require(name.isNotBlank() && name.length <= 80) { "Package name must be 1–80 characters." }
        val allowedVersions = previousVersion?.let(::allowedMarketplaceVersions)
        if (previousVersion == null) {
            require(version == "1.0.0") { "New Marketplace packages must start at version 1.0.0." }
        } else {
            require(version in allowedVersions.orEmpty()) {
                "Version must increase by exactly one step from $previousVersion. Allowed next versions: ${allowedVersions?.joinToString()}."
            }
        }
        require(description.isNotBlank() && description.length <= 400) { "Summary must be 1–400 characters." }

        val app = context.applicationContext
        val db = OpenTaskerApp_NoHilt.awaitDb()
        val entity = db.profileDao().getById(candidate.profile.id) ?: throw IOException("The selected Action no longer exists.")
        val decodedProfile = entity.toDomainDecodeResult()
        require(decodedProfile.issue == null) { "The selected Action could not be read safely." }
        val profile = decodedProfile.value
        require(profile.enabled && !profile.requiresRiskAcknowledgement) {
            "Turn this Action on in OpenTasker before uploading it. Disabled Actions cannot be contributed."
        }
        when (candidate.kind) {
            MarketplaceContributionKind.ACTION -> require(profile.widgetId == null) { "Select an Action, not a widget-owned Action." }
            MarketplaceContributionKind.SHORTCUT_WIDGET -> {
                require(candidate.widgetId != null && profile.widgetId == candidate.widgetId) { "This Shortcut Widget is no longer linked to the selected Action." }
                val isInstalled = AppWidgetManager.getInstance(app).getAppWidgetIds(
                    ComponentName(app, TaskWidgetProvider::class.java),
                ).contains(candidate.widgetId)
                require(isInstalled) { "This Shortcut Widget is no longer installed on the home screen." }
            }
        }

        val taskIds = buildList {
            add(profile.enterTaskId)
            profile.exitTaskId?.takeIf { it > 0L }?.let(::add)
            profile.fallbackTaskId?.takeIf { it > 0L }?.let(::add)
        }.distinct()
        val tasks = taskIds.map { taskId ->
            val taskEntity = db.taskDao().getById(taskId) ?: throw IOException("Action data is missing.")
            val decodedTask = taskEntity.toDomainDecodeResult()
            require(decodedTask.issue == null) { "Action data could not be read safely." }
            decodedTask.value
        }
        require(tasks.isNotEmpty() && tasks.any { it.actions.isNotEmpty() }) { "Add at least one action block before contributing this item." }

        val base = OpenTaskerBundleCodec.build(
            appVersion = BuildConfig.VERSION_NAME,
            exportedAtEpochMs = System.currentTimeMillis(),
            // Share the reusable action flow, not the contributor's personal triggers/contexts.
            // Imported marketplace profiles start disabled and are configured by the recipient.
            profiles = listOf(
                profile.copy(
                    name = name,
                    enabled = false,
                    contexts = emptyList(),
                    contextExpression = null,
                    projectId = DEFAULT_PROJECT_ID,
                    widgetId = null,
                    marketplacePackageId = null,
                ),
            ),
            tasks = tasks.map { it.copy(projectId = DEFAULT_PROJECT_ID) },
            name = name,
            description = description,
        )
        val widget = if (candidate.kind == MarketplaceContributionKind.SHORTCUT_WIDGET) {
            val style = candidate.widgetStyle ?: readStyle(app, candidate.widgetId!!)
            MarketplaceShortcutWidget(name, style.icon, style.iconColor, style.backgroundColor, style.gradient, style.shape)
        } else null
        val bundle = base.copy(
            metadata = base.metadata.copy(
                name = name,
                description = description,
                contributionType = candidate.kind.wireValue,
                shortcutWidget = widget,
            ),
        )
        val bundleText = OpenTaskerBundleCodec.encode(bundle)
        require(bundleText.toByteArray(Charsets.UTF_8).size <= MAX_BUNDLE_BYTES) { "The exported package is too large." }
        validateDisclosure(bundleText, tasks, disclosure)
        val destinations = makeDestinations(tasks, disclosure)

        val token = GitHubDeviceAuth.accessToken(app)
        val login = GitHubDeviceAuth.apiRequestBlocking(token, "GET", "/user").optString("login").trim()
        require(login.matches(Regex("^[A-Za-z0-9-]{1,39}$"))) { "GitHub did not return a valid username." }
        val manifest = JSONObject()
            .put("schemaVersion", 1)
            .put("id", id)
            .put("name", name)
            .put("publisher", login)
            .put("version", version)
            .put("summary", description)
            .put("category", candidate.kind.category)
            .put("minimumAppVersion", BuildConfig.VERSION_NAME.substringBefore('-').substringBefore('+'))
            .put("bundleFile", "bundle.json")
            .put("requiredInputs", JSONArray())
            .put("externalDestinations", JSONArray().apply {
                destinations.forEach { item ->
                    put(JSONObject()
                        .put("provider", item.provider)
                        .put("domain", item.domain)
                        .put("hostingCountry", item.hostingCountry)
                        .put("privacyPolicyUrl", item.privacyPolicyUrl)
                        .put("dataFields", JSONArray(item.dataFields))
                        .put("purpose", item.purpose)
                        .put("privacyAssessment", item.privacyAssessment)
                        .put("riskNotes", item.riskNotes))
                }
            })

        val upstream = GitHubDeviceAuth.apiRequestBlocking(token, "GET", "/repos/" + REPOSITORY)
        val upstreamBranch = upstream.optString("default_branch").ifBlank { "main" }
        val canWrite = upstream.optJSONObject("permissions")?.optBoolean("push", false) == true
        val target = if (canWrite) upstream else forkForUser(token, login)
        val targetRepo = target.optString("full_name")
        require(targetRepo.isNotBlank()) { "GitHub did not return the contribution repository name." }
        val targetBranch = target.optString("default_branch").ifBlank { upstreamBranch }
        val branchInfo = GitHubDeviceAuth.apiRequestBlocking(
            token, "GET", "/repos/" + targetRepo + "/branches/" + urlPath(targetBranch),
        )
        val baseSha = branchInfo.getJSONObject("commit").getString("sha")
        val branch = "opentasker/" + id + "-" + version + "-" + System.currentTimeMillis()
        GitHubDeviceAuth.apiRequestBlocking(
            token, "POST", "/repos/" + targetRepo + "/git/refs",
            JSONObject().put("ref", "refs/heads/" + branch).put("sha", baseSha),
        )

        val folder = "packages/" + id + "/" + version
        putFile(token, targetRepo, branch, folder + "/manifest.json", manifest.toString(2))
        putFile(token, targetRepo, branch, folder + "/bundle.json", bundleText)
        val pr = GitHubDeviceAuth.apiRequestBlocking(
            token,
            "POST",
            "/repos/" + REPOSITORY + "/pulls",
            JSONObject()
                .put(
                    "title",
                    (if (previousVersion == null) "[Marketplace] " else "[Marketplace update] ") + name + " (" + version + ")",
                )
                .put("body", buildString {
                    appendLine("Submitted from the OpenTasker Android app.")
                    if (previousVersion != null) {
                        appendLine()
                        appendLine("Package update: $id")
                        appendLine("Previous version: $previousVersion")
                        appendLine("This update requires a formal APPROVED review from a trusted Marketplace moderator before it can be merged.")
                    }
                    appendLine()
                    appendLine("Contribution type: " + candidate.kind.label)
                    appendLine("Package: " + id)
                    appendLine("Version: " + version)
                    appendLine("Publisher: @" + login)
                    if (candidate.kind == MarketplaceContributionKind.SHORTCUT_WIDGET) {
                        appendLine("Includes the enabled linked Action and the app-style Shortcut Widget presentation.")
                    }
                    appendLine()
                    appendLine("This is a data-only OpenTasker bundle. Automated GitHub validation and Marketplace moderation remain required.")
                })
                .put("head", if (targetRepo.equals(REPOSITORY, true)) branch else login + ":" + branch)
                .put("base", upstreamBranch)
                .put("maintainer_can_modify", true),
        )
        val number = pr.optInt("number", 0)
        val url = pr.optString("html_url")
        require(number > 0 && url.startsWith("https://github.com/")) { "GitHub did not return a valid pull-request link." }
        MarketplacePullRequest(number, url)
    }

    suspend fun requestDeletion(
        context: Context,
        packageEntry: MarketplacePackage,
    ): MarketplaceDeletionRequest = withContext(Dispatchers.IO) {
        require(packageEntry.id.matches(Regex("^[a-z0-9][a-z0-9._-]{1,79}$"))) {
            "The Marketplace package ID is invalid."
        }
        val token = GitHubDeviceAuth.accessToken(context.applicationContext)
        val user = GitHubDeviceAuth.apiRequestBlocking(token, "GET", "/user")
        val login = user.optString("login").trim()
        require(login.isNotBlank() && login.equals(packageEntry.publisher, ignoreCase = true)) {
            "Only the GitHub account that originally published this package can delete it."
        }

        val requestBody = "<!-- opentasker-marketplace-delete:v1 -->\n" +
            JSONObject().put("packageId", packageEntry.id).toString()
        val issue = GitHubDeviceAuth.apiRequestBlocking(
            token,
            "POST",
            "/repos/" + REPOSITORY + "/issues",
            JSONObject()
                .put("title", "[Marketplace deletion] " + packageEntry.id)
                .put(
                    "body",
                    "Please withdraw package ${packageEntry.id} from the official Marketplace. " +
                        "This publisher-authenticated request does not require review. " +
                        "Automations already imported by users must remain installed and unchanged.\n\n" +
                        requestBody,
                ),
        )
        val number = issue.optInt("number", 0)
        val url = issue.optString("html_url")
        require(number > 0 && url.startsWith("https://github.com/")) {
            "GitHub did not return a valid deletion request link."
        }
        MarketplaceDeletionRequest(number, url)
    }

    private fun readStyle(context: Context, id: Int): WidgetStyle {
        val prefs = context.getSharedPreferences(TaskWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
        val defaults = WidgetStyle()
        return WidgetStyle(
            icon = prefs.getString(TaskWidgetProvider.keyIcon(id), defaults.icon) ?: defaults.icon,
            iconColor = prefs.getInt(TaskWidgetProvider.keyIconColor(id), defaults.iconColor),
            backgroundColor = prefs.getInt(TaskWidgetProvider.keyBackgroundColor(id), defaults.backgroundColor),
            gradient = prefs.getBoolean(TaskWidgetProvider.keyGradient(id), defaults.gradient),
            shape = prefs.getString(TaskWidgetProvider.keyShape(id), defaults.shape) ?: defaults.shape,
        )
    }

    private fun forkForUser(token: String, login: String): JSONObject {
        val path = "/repos/" + login + "/OpenTasker_Makrketplace"
        runCatching { GitHubDeviceAuth.apiRequestBlocking(token, "GET", path) }.getOrNull()?.let { return it }
        runCatching {
            GitHubDeviceAuth.apiRequestBlocking(token, "POST", "/repos/" + REPOSITORY + "/forks", JSONObject())
        }.onFailure { error ->
            if (error !is GitHubApiException || error.statusCode !in setOf(403, 404, 422)) throw error
        }
        repeat(20) {
            try {
                return GitHubDeviceAuth.apiRequestBlocking(token, "GET", path)
            } catch (error: GitHubApiException) {
                if (error.statusCode != 404) throw error
                Thread.sleep(1_000)
            }
        }
        throw IOException("GitHub is still preparing your Marketplace fork. Try again in a moment.")
    }

    private fun putFile(token: String, repo: String, branch: String, path: String, content: String) {
        val encoded = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        GitHubDeviceAuth.apiRequestBlocking(
            token, "PUT", "/repos/" + repo + "/contents/" + path,
            JSONObject().put("message", "Add Marketplace package file " + path).put("content", encoded).put("branch", branch),
        )
    }

    private fun validateDisclosure(
        bundle: String,
        tasks: List<Task>,
        disclosure: MarketplaceExternalDestinationDraft?,
    ) {
        require(!email.containsMatchIn(bundle)) { "The export contains an email address. Replace it with a local setup input." }
        require(!phone.containsMatchIn(bundle)) { "The export contains a phone number. Replace it with a local setup input." }
        require(!secret.containsMatchIn(bundle)) { "The export contains a credential. Remove it and use a local setup input." }
        val requires = tasks.any { task -> task.actions.any { it.type in externalIds } }
        if (requires) requireNotNull(disclosure) {
            "This Action uses a network, webhook, MQTT, or download block. Add its external-service disclosure."
        }
        if (disclosure != null) {
            require(disclosure.provider.isNotBlank()) { "Enter the external-service provider." }
            require(disclosure.hostingCountry.isNotBlank()) { "Enter a hosting country or Unknown." }
            require(disclosure.privacyPolicyUrl.startsWith("https://")) { "The privacy-policy URL must use HTTPS." }
            require(disclosure.dataFields.split(',').any { it.isNotBlank() }) { "List the data fields sent to this service." }
            require(disclosure.purpose.isNotBlank()) { "Describe why the Action contacts this service." }
            require(disclosure.privacyAssessment in setOf("verified", "not-verified", "known-concerns")) { "Choose a valid privacy assessment." }
            val declared = disclosure.domains.split(',', ';').map { it.trim().lowercase().trimEnd('.') }.filter { it.isNotBlank() }
            require(declared.isNotEmpty() && declared.all { it.matches(Regex("^[a-z0-9.-]{3,253}$")) }) {
                "Enter external hostnames separated by commas."
            }
            val hosts = urlHost.findAll(bundle).map { it.groupValues[1].lowercase().trimEnd('.') }
                .filterNot { it == "localhost" || it.endsWith(".local") }.toSet()
            val undeclared = hosts.filter { host -> declared.none { host == it || host.endsWith("." + it) } }
            require(undeclared.isEmpty()) { "Declare every external host used: " + undeclared.sorted().joinToString() + "." }
        }
    }

    private fun makeDestinations(
        tasks: List<Task>,
        disclosure: MarketplaceExternalDestinationDraft?,
    ): List<MarketplaceExternalDestination> {
        val requires = tasks.any { task -> task.actions.any { it.type in externalIds } }
        if (!requires && disclosure == null) return emptyList()
        val value = requireNotNull(disclosure) { "External service disclosure is required." }
        val domains = value.domains.split(',', ';').map { it.trim().lowercase().trimEnd('.') }.filter { it.isNotBlank() }.distinct()
        val fields = value.dataFields.split(',').map { it.trim() }.filter { it.isNotBlank() }
        return domains.map { domain ->
            MarketplaceExternalDestination(
                provider = value.provider.trim(),
                domain = domain,
                hostingCountry = value.hostingCountry.trim(),
                privacyPolicyUrl = value.privacyPolicyUrl.trim(),
                dataFields = fields,
                purpose = value.purpose.trim(),
                privacyAssessment = value.privacyAssessment,
                riskNotes = value.riskNotes.trim(),
            )
        }
    }

    private fun urlPath(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
