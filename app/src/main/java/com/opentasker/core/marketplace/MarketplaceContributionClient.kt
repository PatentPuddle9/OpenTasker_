package com.opentasker.core.marketplace

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.opentasker.app.BuildConfig
import com.opentasker.app.OpenTaskerApp_NoHilt
import com.opentasker.core.model.DEFAULT_PROJECT_ID
import com.opentasker.core.model.Profile
import com.opentasker.core.model.Task
import com.opentasker.core.transfer.MarketplaceShortcutWidget
import com.opentasker.core.transfer.OpenTaskerBundleCodec
import com.opentasker.ui.theme.ThemeMode
import com.opentasker.ui.theme.ThemePreference
import com.opentasker.widget.CustomWidgetIcon
import com.opentasker.widget.CustomWidgetIconStore
import com.opentasker.widget.TaskWidgetProvider
import com.opentasker.widget.WidgetDisplayConfig
import com.opentasker.widget.WidgetDisplayProvider
import com.opentasker.widget.WidgetDisplayStore
import com.opentasker.widget.WidgetDisplayElementType
import com.opentasker.widget.WidgetStyle
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject

internal enum class MarketplaceContributionKind(val label: String, val category: String, val wireValue: String) {
    ACTION("Action", "Actions", "action"),
    SHORTCUT_WIDGET("Shortcut Widget", "Shortcut Widgets", "shortcut_widget"),
    WIDGET_DISPLAY("Widget Display", "Widget Displays", "widget_display"),
    THEME("Theme", "Themes", "theme"),
    CUSTOM_WIDGET_ICON("Custom Widget Icon", "Custom Widget Icons", "custom_widget_icon"),
}

internal data class MarketplaceContributionCandidate(
    val kind: MarketplaceContributionKind,
    val profile: Profile? = null,
    val tasks: List<Task> = emptyList(),
    val widgetId: Int? = null,
    val widgetStyle: WidgetStyle? = null,
    val widgetDisplayId: Int? = null,
    val widgetDisplayConfig: WidgetDisplayConfig? = null,
    val customIcon: CustomWidgetIcon? = null,
    val customIconBitmap: Bitmap? = null,
    val themeMode: ThemeMode? = null,
    val themeAccentColor: String? = null,
) {
    val key: String get() = kind.wireValue + ":" + (profile?.id ?: 0L) + ":" +
        (widgetId ?: widgetDisplayId ?: customIcon?.id ?: 0)
    val name: String get() = profile?.name
        ?: customIcon?.name
        ?: widgetDisplayConfig?.states?.firstOrNull()?.title?.takeIf { it.isNotBlank() }
        ?: if (kind == MarketplaceContributionKind.THEME) "Current appearance" else kind.label
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
                    result += MarketplaceContributionCandidate(
                        kind = kind,
                        profile = profile,
                        tasks = tasks,
                        widgetId = widgetId,
                        widgetStyle = style,
                    )
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

            WidgetDisplayProvider.instances(app).forEach { instance ->
                val config = WidgetDisplayStore.load(app, instance.widgetId)
                result += MarketplaceContributionCandidate(
                    kind = MarketplaceContributionKind.WIDGET_DISPLAY,
                    widgetDisplayId = instance.widgetId,
                    widgetDisplayConfig = config,
                )
            }

            val currentMode = ThemePreference.observe(app).first()
            val currentAccent = ThemePreference.observeAccentColor(app).first()
            result += MarketplaceContributionCandidate(
                kind = MarketplaceContributionKind.THEME,
                themeMode = currentMode,
                themeAccentColor = currentAccent,
            )

            CustomWidgetIconStore.observe(app).value.forEach { icon ->
                val bitmap = CustomWidgetIconStore.loadBitmap(app, icon, 1024) ?: return@forEach
                result += MarketplaceContributionCandidate(
                    kind = MarketplaceContributionKind.CUSTOM_WIDGET_ICON,
                    customIcon = icon,
                    customIconBitmap = bitmap,
                )
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
        description: String,
        tags: List<String>,
        icon: String,
        iconColor: String,
        backgroundColor: String,
        gradient: Boolean,
        shape: String,
        changelog: String,
        documentationUrl: String,
        disclosure: MarketplaceExternalDestinationDraft?,
        themeMode: String = "amoled",
        themeAccentColor: String = ThemePreference.DEFAULT_ACCENT_HEX,
        previousVersion: String? = null,
    ): MarketplacePullRequest = withContext(Dispatchers.IO) {
        val id = packageId.trim()
        val name = packageName.trim()
        val shortDescription = summary.trim()
        val longDescription = description.trim()
        val cleanTags = tags.map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }
        val cleanChangelog = changelog.trim()
        val cleanDocumentationUrl = documentationUrl.trim()
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
        require(shortDescription.isNotBlank() && shortDescription.length <= 400) { "Summary must be 1–400 characters." }
        require(longDescription.length <= 4000) { "The full description must be 4000 characters or fewer." }
        require(cleanTags.size <= 10 && cleanTags.all { it.matches(Regex("^[A-Za-z0-9][A-Za-z0-9 _+-]{0,23}$")) }) {
            "Use up to 10 short tags with letters, numbers, spaces, plus signs, underscores, or hyphens."
        }
        require(icon.matches(Regex("^[a-z0-9][a-z0-9-]{0,63}$"))) { "Choose a built-in Marketplace icon." }
        require(iconColor.matches(Regex("^#?[A-Fa-f0-9]{6}$"))) { "Icon color must be a six-digit hex color." }
        require(backgroundColor.matches(Regex("^#?[A-Fa-f0-9]{6}$"))) { "Background color must be a six-digit hex color." }
        require(shape in setOf("squircle", "rounded_square", "circle", "square", "teardrop")) {
            "Choose a supported listing icon shape."
        }
        require(cleanChangelog.length <= 2000) { "Release notes must be 2000 characters or fewer." }
        require(cleanDocumentationUrl.isBlank() || (
            cleanDocumentationUrl.startsWith("https://") &&
                android.net.Uri.parse(cleanDocumentationUrl).host != null
            )
        ) { "Documentation links must use a valid HTTPS URL." }

        val app = context.applicationContext
        var tasks: List<Task> = emptyList()
        var destinations: List<MarketplaceExternalDestination> = emptyList()
        var iconAssetBytes: ByteArray? = null
        val activeThemeMode = themeMode.ifBlank { candidate.themeMode?.toStorageString() ?: "amoled" }
        val activeThemeAccent = themeAccentColor.trim().let { if (it.startsWith("#")) it else "#$it" }
        val payloadText: String = when (candidate.kind) {
            MarketplaceContributionKind.ACTION,
            MarketplaceContributionKind.SHORTCUT_WIDGET -> {
                val profileId = candidate.profile?.id
                    ?: throw IOException("The selected Action no longer exists.")
                val db = OpenTaskerApp_NoHilt.awaitDb()
                val entity = db.profileDao().getById(profileId)
                    ?: throw IOException("The selected Action no longer exists.")
                val decodedProfile = entity.toDomainDecodeResult()
                require(decodedProfile.issue == null) { "The selected Action could not be read safely." }
                val profile = decodedProfile.value
                require(profile.enabled && !profile.requiresRiskAcknowledgement) {
                    "Turn this Action on in OpenTasker before uploading it. Disabled Actions cannot be contributed."
                }
                when (candidate.kind) {
                    MarketplaceContributionKind.ACTION ->
                        require(profile.widgetId == null) { "Select an Action, not a widget-owned Action." }
                    MarketplaceContributionKind.SHORTCUT_WIDGET -> {
                        require(candidate.widgetId != null && profile.widgetId == candidate.widgetId) {
                            "This Shortcut Widget is no longer linked to the selected Action."
                        }
                        val isInstalled = AppWidgetManager.getInstance(app).getAppWidgetIds(
                            ComponentName(app, TaskWidgetProvider::class.java),
                        ).contains(candidate.widgetId)
                        require(isInstalled) { "This Shortcut Widget is no longer installed on the home screen." }
                    }
                    else -> Unit
                }

                val taskIds = buildList {
                    add(profile.enterTaskId)
                    profile.exitTaskId?.takeIf { it > 0L }?.let(::add)
                    profile.fallbackTaskId?.takeIf { it > 0L }?.let(::add)
                }.distinct()
                tasks = taskIds.map { taskId ->
                    val taskEntity = db.taskDao().getById(taskId) ?: throw IOException("Action data is missing.")
                    val decodedTask = taskEntity.toDomainDecodeResult()
                    require(decodedTask.issue == null) { "Action data could not be read safely." }
                    decodedTask.value
                }
                require(tasks.isNotEmpty() && tasks.any { it.actions.isNotEmpty() }) {
                    "Add at least one action block before contributing this item."
                }

                val base = OpenTaskerBundleCodec.build(
                    appVersion = BuildConfig.VERSION_NAME,
                    exportedAtEpochMs = System.currentTimeMillis(),
                    // Share the reusable Action flow, not personal triggers or contexts.
                    // Imported profiles start disabled and are configured by the recipient.
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
                    description = longDescription.ifBlank { shortDescription },
                )
                val widget = if (candidate.kind == MarketplaceContributionKind.SHORTCUT_WIDGET) {
                    val style = candidate.widgetStyle ?: readStyle(app, candidate.widgetId!!)
                    MarketplaceShortcutWidget(name, style.icon, style.iconColor, style.backgroundColor, style.gradient, style.shape)
                } else null
                val exported = base.copy(
                    metadata = base.metadata.copy(
                        name = name,
                        description = longDescription.ifBlank { shortDescription },
                        contributionType = candidate.kind.wireValue,
                        shortcutWidget = widget,
                    ),
                )
                val encoded = OpenTaskerBundleCodec.encode(exported)
                require(encoded.toByteArray(Charsets.UTF_8).size <= MAX_BUNDLE_BYTES) {
                    "The exported package is too large."
                }
                validateDisclosure(encoded, tasks, disclosure)
                destinations = makeDestinations(tasks, disclosure)
                encoded
            }

            MarketplaceContributionKind.WIDGET_DISPLAY -> {
                val config = candidate.widgetDisplayConfig
                    ?: throw IOException("The selected Widget Display is no longer available.")
                require(candidate.widgetDisplayId != null && WidgetDisplayProvider.isWidgetId(app, candidate.widgetDisplayId)) {
                    "The selected Widget Display is no longer installed."
                }
                require(config.states.isNotEmpty() && config.states.size <= WidgetDisplayStore.MAX_STATES) {
                    "The Widget Display has an invalid number of states."
                }
                require(config.states.none { state -> state.elements.any { it.type == WidgetDisplayElementType.IMAGE && it.imageUri.isNotBlank() } }) {
                    "This Widget Display uses images stored privately on this device. Remove those image elements before publishing so the layout can be shared safely."
                }
                val sharedConfig = config.copy(
                    states = config.states.map { state ->
                        state.copy(
                            elements = state.elements.map { element ->
                                element.copy(actionProfileId = null, imageUri = "")
                            },
                        )
                    },
                )
                JSONObject()
                    .put("schemaVersion", 1)
                    .put("type", "widget_display")
                    .put("config", JSONObject(Json { encodeDefaults = true }.encodeToString(sharedConfig)))
                    .toString(2)
            }

            MarketplaceContributionKind.THEME -> {
                require(activeThemeMode in setOf("system", "dark", "amoled", "light", "high_contrast", "dynamic")) {
                    "Choose a supported OpenTasker theme mode."
                }
                require(activeThemeAccent.matches(Regex("^#[A-Fa-f0-9]{6}$"))) {
                    "Theme accent must be a six-digit hex color."
                }
                JSONObject()
                    .put("schemaVersion", 1)
                    .put("type", "theme")
                    .put("themeMode", activeThemeMode)
                    .put("accentColor", activeThemeAccent.uppercase())
                    .toString(2)
            }

            MarketplaceContributionKind.CUSTOM_WIDGET_ICON -> {
                val customIcon = candidate.customIcon
                    ?: throw IOException("The selected custom widget icon is no longer available.")
                val bitmap = candidate.customIconBitmap
                    ?: CustomWidgetIconStore.loadBitmap(app, customIcon, 1024)
                    ?: throw IOException("The custom widget icon image could not be loaded.")
                val output = ByteArrayOutputStream()
                require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Could not encode the custom widget icon."
                }
                iconAssetBytes = output.toByteArray()
                require(iconAssetBytes!!.size in 1..15 * 1024 * 1024) {
                    "The custom widget icon exceeds the 15 MB package limit."
                }
                JSONObject()
                    .put("schemaVersion", 1)
                    .put("type", "custom_widget_icon")
                    .put("name", customIcon.name)
                    .put("recolorable", customIcon.recolorable)
                    .toString(2)
            }
        }

        require(payloadText.toByteArray(Charsets.UTF_8).size <= MAX_BUNDLE_BYTES) {
            "The Marketplace package data is too large."
        }

        val previewBitmap = MarketplacePreviewRenderer.render(
            kind = candidate.kind,
            name = name,
            summary = shortDescription,
            display = candidate.widgetDisplayConfig,
            iconBitmap = candidate.customIconBitmap,
            themeMode = activeThemeMode,
            themeAccent = activeThemeAccent,
            listingBackground = if (candidate.kind == MarketplaceContributionKind.CUSTOM_WIDGET_ICON) "#6750A4" else backgroundColor,
            listingForeground = iconColor,
            gradient = gradient,
        )
        val previewOutput = ByteArrayOutputStream()
        require(previewBitmap.compress(Bitmap.CompressFormat.PNG, 100, previewOutput)) {
            "Could not generate the Marketplace preview PNG."
        }
        val previewBytes = previewOutput.toByteArray()
        require(previewBytes.size in 1..2 * 1024 * 1024) {
            "The generated preview image exceeds the 2 MB limit."
        }
        val token = GitHubDeviceAuth.accessToken(app)
        val login = GitHubDeviceAuth.apiRequestBlocking(token, "GET", "/user").optString("login").trim()
        require(login.matches(Regex("^[A-Za-z0-9-]{1,39}$"))) { "GitHub did not return a valid username." }
        val manifest = JSONObject()
            .put("schemaVersion", 1)
            .put("id", id)
            .put("name", name)
            .put("publisher", login)
            .put("version", version)
            .put("summary", shortDescription)
            .put("description", longDescription)
            .put("tags", JSONArray(cleanTags))
            .put("icon", icon)
            .put("iconColor", if (iconColor.startsWith("#")) iconColor else "#$iconColor")
            .put("backgroundColor", if (backgroundColor.startsWith("#")) backgroundColor else "#$backgroundColor")
            .put("gradient", gradient)
            .put("shape", shape)
            .put("changelog", cleanChangelog)
            .put("documentationUrl", cleanDocumentationUrl)
            .put("category", candidate.kind.category)
        if (candidate.kind == MarketplaceContributionKind.THEME) {
            manifest.put("themeMode", activeThemeMode)
                .put("themeAccentColor", activeThemeAccent.uppercase())
        }
            .put("minimumAppVersion", BuildConfig.VERSION_NAME.substringBefore('-').substringBefore('+'))
            .put("bundleFile", "bundle.json")
            .put("previewFile", "preview.png")
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
        if (iconAssetBytes != null) manifest.put("assetFile", "icon.png")

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
        putFile(token, targetRepo, branch, folder + "/bundle.json", payloadText)
        putFileBytes(token, targetRepo, branch, folder + "/preview.png", previewBytes)
        iconAssetBytes?.let { bytes ->
            putFileBytes(token, targetRepo, branch, folder + "/icon.png", bytes)
        }
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
                    } else if (candidate.kind == MarketplaceContributionKind.WIDGET_DISPLAY) {
                        appendLine("Includes a reusable Widget Display layout. Publisher-specific Action links are cleared; recipients can connect their own Actions.")
                    } else if (candidate.kind == MarketplaceContributionKind.THEME) {
                        appendLine("Includes an OpenTasker appearance preset (theme mode and accent color).")
                    } else if (candidate.kind == MarketplaceContributionKind.CUSTOM_WIDGET_ICON) {
                        appendLine("Includes the original custom icon PNG asset.")
                    }
                    appendLine()
                    appendLine("## Package preview")
                    appendLine()
                    appendLine("![Marketplace package preview](https://raw.githubusercontent.com/" + targetRepo + "/" + branch + "/" + folder + "/preview.png)")
                    appendLine()
                    appendLine("The preview PNG is included in this pull request so reviewers can inspect the submitted appearance.")
                    appendLine()
                    appendLine("Package data is limited to the supported OpenTasker Marketplace format. Automated validation and Marketplace moderation remain required.")
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

    private fun putFileBytes(token: String, repo: String, branch: String, path: String, content: ByteArray) {
        require(content.isNotEmpty() && content.size <= 15 * 1024 * 1024) {
            "Marketplace image assets must be between 1 byte and 15 MB."
        }
        val encoded = Base64.encodeToString(content, Base64.NO_WRAP)
        GitHubDeviceAuth.apiRequestBlocking(
            token, "PUT", "/repos/" + repo + "/contents/" + path,
            JSONObject().put("message", "Add Marketplace package image " + path).put("content", encoded).put("branch", branch),
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
