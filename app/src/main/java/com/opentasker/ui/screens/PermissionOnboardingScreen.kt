package com.opentasker.ui.screens

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.FilterChip
import com.opentasker.app.OpenTaskerApp_NoHilt
import com.opentasker.core.storage.ConfigurationSnapshotPolicy
import com.opentasker.core.storage.ConfigurationSnapshotStatus
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opentasker.app.BuildConfig
import com.opentasker.app.R
import com.opentasker.core.storage.RestoreCandidate
import com.opentasker.core.storage.RestoreRollback
import androidx.compose.runtime.collectAsState
import com.opentasker.core.actions.hasWriteSecureSettings
import com.opentasker.core.actions.secureSettingsGrantCommand
import com.opentasker.core.permissions.OemBatteryGuidance
import com.opentasker.core.diagnostics.AdvancedProtectionReader
import com.opentasker.core.permissions.RuntimePermissionOutcome
import com.opentasker.core.permissions.RuntimePermissionRequestHistory
import com.opentasker.ui.theme.ThemeMode
import com.opentasker.ui.theme.ThemePreference
import com.opentasker.ui.theme.DesignSystem
import java.util.Date
import kotlinx.coroutines.launch
import com.opentasker.core.permissions.UsageAccess
import com.opentasker.core.power.ShizukuPowerBackend
import com.opentasker.core.power.ShizukuPowerState
import com.opentasker.core.scheduling.ExactAlarmSupport
import com.opentasker.core.scripting.TermuxScriptBackend
import com.opentasker.core.support.ProjectLinks
import com.opentasker.core.scripting.TermuxScriptState
import androidx.compose.ui.platform.testTag
import com.opentasker.core.capabilities.SetupRequirement
import com.opentasker.core.capabilities.SetupRequirementResolver
import com.opentasker.core.platform.LockDeviceAdminReceiver
import com.opentasker.core.platform.PromotedOngoingNotificationSupport
import com.opentasker.core.contexts.PushTriggerTokenStore
import com.opentasker.core.contexts.UnifiedPushConnector
import com.opentasker.core.contexts.UnifiedPushEndpointStore
import com.opentasker.core.contexts.UnifiedPushRegistrationState
import com.opentasker.core.contexts.UnifiedPushRegistrationStatus
import com.opentasker.core.engine.DirectBootTriggerStore
import com.opentasker.core.contexts.CompanionAssociation
import com.opentasker.core.contexts.CompanionAssociationResult
import com.opentasker.core.contexts.CompanionDeviceAssociation
import com.opentasker.core.logging.AppLogger
import com.opentasker.core.plugins.locale.LocaleGrant
import com.opentasker.core.plugins.locale.LocaleGrantStore
import com.opentasker.core.model.Profile
import com.opentasker.core.model.Task
import com.opentasker.core.updates.UpdateCheckAvailability
import com.opentasker.core.updates.BranchVersion
import com.opentasker.core.updates.collectEnabledProfileActionIds
import com.opentasker.core.updates.UpdateCheckRunResult
import com.opentasker.core.updates.UpdateCheckRunner
import com.opentasker.core.updates.UpdateCheckSettings
import com.opentasker.core.updates.UpdateCheckProtocol
import com.opentasker.core.updates.UpdateCheckState
import com.opentasker.core.updates.UpdateCheckWorker
import com.opentasker.core.updates.UpdateApkDownloader
import java.io.File
import com.opentasker.ui.utils.expandCollapseToggle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

internal const val SETUP_FOCUS_BANNER_TAG = "setup_focus_banner"

internal enum class SetupSection {
    ENGINE,
    NEEDED,
    OPTIONAL,
    RELIABILITY,
}

internal fun SetupSection.titleRes(): Int = when (this) {
    SetupSection.ENGINE -> R.string.setup_section_engine
    SetupSection.NEEDED -> R.string.setup_section_needed
    SetupSection.OPTIONAL -> R.string.setup_section_optional
    SetupSection.RELIABILITY -> R.string.setup_section_reliability
}

internal data class PermissionSetupItem(
    val title: String,
    val body: String,
    val granted: Boolean,
    val actionLabel: String,
    val action: PermissionAction,
    val requiredFor: String,
    val optional: Boolean = false,
    val allowActionWhenGranted: Boolean = false,
    val section: SetupSection = SetupSection.ENGINE,
    val requirements: Set<SetupRequirement> = emptySet(),
    /** The check behind [granted] threw on this device, so [granted] is a fallback, not an answer. */
    val unavailable: Boolean = false,
)

data class BackupSetupState(
    val busy: Boolean,
    val latestBackupName: String? = null,
    val pendingRestore: Boolean = false,
    /** What the staged restore would install, so the pending banner is specific rather than generic. */
    val pendingRestoreSummary: RestoreCandidate? = null,
    val snapshotPolicy: ConfigurationSnapshotPolicy = ConfigurationSnapshotPolicy(),
    val snapshotStatus: ConfigurationSnapshotStatus = ConfigurationSnapshotStatus(),
    /** The database the last restore replaced, while it is still on disk. */
    val lastRestoreRollback: RestoreRollback? = null,
)

internal sealed interface PermissionAction {
    /**
     * A runtime permission dialog for [permission], which decides the outcome. [requestedWith] is
     * asked for in the same dialog, because Android 12+ ignores a fine-location request that does
     * not also ask for coarse location.
     */
    data class RuntimePermission(
        val permission: String,
        val requestedWith: List<String> = emptyList(),
    ) : PermissionAction
    data class SettingsIntent(val intent: Intent) : PermissionAction
    data object ShizukuPermission : PermissionAction

    /** Turning the lock admin back off, which Android offers no direct settings intent for. */
    data object RemoveDeviceAdmin : PermissionAction

    /**
     * Puts a command on the clipboard.
     *
     * `WRITE_SECURE_SETTINGS` has no settings page and no runtime dialog. The only way to grant it
     * is to run one command from a computer, so the row's job is to hand over that exact command
     * rather than open something.
     */
    data class CopyText(val label: String, val text: String) : PermissionAction
    data class ShizukuKillSwitch(val enabled: Boolean) : PermissionAction
    /** Try each OEM settings component in order, falling back to a web guide URL. */
    data class OemSettings(
        val targets: List<OemBatteryGuidance.SettingsTarget>,
        val fallbackUrl: String,
    ) : PermissionAction
    data object None : PermissionAction
}

private class PermissionOnboardingViewModel(appContext: Context) : ViewModel() {
    private val context = appContext.applicationContext

    val permissionHistory = RuntimePermissionRequestHistory(context)

    private val refreshTick = MutableStateFlow(0L)
    val permissionItems: StateFlow<List<PermissionSetupItem>?> = refreshTick
        .map { buildPermissionItems(context, permissionHistory) }
        .flowOn(Dispatchers.IO)
        // null until the first read: an empty list is a real answer only after one.
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val directBootEnabled: StateFlow<Boolean> = DirectBootTriggerStore.observe(context)
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val themeMode: StateFlow<ThemeMode> = ThemePreference.observe(context)
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.System)

    val accentColorHex: StateFlow<String> = ThemePreference.observeAccentColor(context)
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemePreference.DEFAULT_ACCENT_HEX)

    private val _updateCheckState = MutableStateFlow(
        if (UpdateCheckAvailability.isAvailable()) UpdateCheckSettings(context).load() else UpdateCheckState(),
    )
    val updateCheckState: StateFlow<UpdateCheckState> = _updateCheckState.asStateFlow()

    private val _availableUpdateBranches = MutableStateFlow(listOf(BuildConfig.UPDATE_SOURCE_BRANCH))
    val availableUpdateBranches: StateFlow<List<String>> = _availableUpdateBranches.asStateFlow()

    private val _updateBranchesLoading = MutableStateFlow(false)
    val updateBranchesLoading: StateFlow<Boolean> = _updateBranchesLoading.asStateFlow()

    private val _updateBranchesFailed = MutableStateFlow(false)
    val updateBranchesFailed: StateFlow<Boolean> = _updateBranchesFailed.asStateFlow()

    private val _updateCheckInProgress = MutableStateFlow(false)
    val updateCheckInProgress: StateFlow<Boolean> = _updateCheckInProgress.asStateFlow()

    private val _updateCheckFailed = MutableStateFlow(false)
    val updateCheckFailed: StateFlow<Boolean> = _updateCheckFailed.asStateFlow()

    private val _branchVersions = MutableStateFlow<List<BranchVersion>>(emptyList())
    val branchVersions: StateFlow<List<BranchVersion>> = _branchVersions.asStateFlow()

    private val _branchVersionsLoading = MutableStateFlow(false)
    val branchVersionsLoading: StateFlow<Boolean> = _branchVersionsLoading.asStateFlow()

    private val _branchVersionsFailed = MutableStateFlow(false)
    val branchVersionsFailed: StateFlow<Boolean> = _branchVersionsFailed.asStateFlow()

    private var branchVersionsRequestGeneration = 0

    private val _updateDownloadInProgress = MutableStateFlow(false)
    val updateDownloadInProgress: StateFlow<Boolean> = _updateDownloadInProgress.asStateFlow()

    private val _updateDownloadProgress = MutableStateFlow(0)
    val updateDownloadProgress: StateFlow<Int> = _updateDownloadProgress.asStateFlow()

    private val _updateInstallFailed = MutableStateFlow(false)
    val updateInstallFailed: StateFlow<Boolean> = _updateInstallFailed.asStateFlow()

    private val _associations = MutableStateFlow<List<CompanionAssociation>>(emptyList())
    val associations: StateFlow<List<CompanionAssociation>> = _associations.asStateFlow()

    private val _pushToken = MutableStateFlow("")
    val pushToken: StateFlow<String> = _pushToken.asStateFlow()

    private val _pushRegistration = MutableStateFlow(UnifiedPushRegistrationState())
    val pushRegistration: StateFlow<UnifiedPushRegistrationState> = _pushRegistration.asStateFlow()

    private val _localeGrants = MutableStateFlow<List<LocaleGrant>>(emptyList())
    val localeGrants: StateFlow<List<LocaleGrant>> = _localeGrants.asStateFlow()

    private var externalRefreshJob: Job? = null

    init {
        refreshExternalState()
        refreshUpdateBranches()
    }

    fun refresh() {
        refreshTick.update { it + 1L }
        refreshExternalState()
        refreshUpdateCheckState()
        refreshUpdateBranches()
    }

    fun setDirectBootEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            DirectBootTriggerStore.setEnabled(context, enabled)
            refresh()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch(Dispatchers.IO) { ThemePreference.set(context, mode) }
    }

    fun setAccentColor(hex: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { ThemePreference.setAccentColor(context, hex) }
        }
    }

    fun setUpdateChecksEnabled(enabled: Boolean) {
        if (!UpdateCheckAvailability.isAvailable()) return
        viewModelScope.launch(Dispatchers.IO) {
            val settings = UpdateCheckSettings(context)
            settings.setEnabled(enabled)
            UpdateCheckWorker.sync(context)
            _updateCheckState.value = settings.load()
        }
    }

    fun setUpdateSourceBranch(branch: String) {
        if (!UpdateCheckAvailability.isAvailable()) return
        if (!UpdateCheckProtocol.isSelectableBranch(branch) || branch !in _availableUpdateBranches.value) return
        viewModelScope.launch(Dispatchers.IO) {
            val settings = UpdateCheckSettings(context)
            settings.setSelectedBranch(branch)
            _updateCheckState.value = settings.load()
            _updateCheckFailed.value = false
            _updateInstallFailed.value = false
        }
    }

    fun refreshUpdateBranches() {
        if (!UpdateCheckAvailability.isAvailable() || _updateBranchesLoading.value) return
        _updateBranchesLoading.value = true
        _updateBranchesFailed.value = false
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val branches = UpdateCheckRunner.fetchAvailableBranches(context)
                if (branches.isNullOrEmpty()) {
                    _updateBranchesFailed.value = true
                    return@launch
                }
                _availableUpdateBranches.value = branches
                val settings = UpdateCheckSettings(context)
                val selectedBranch = settings.load().selectedBranch
                if (selectedBranch !in branches) {
                    val fallbackBranch = BuildConfig.UPDATE_SOURCE_BRANCH
                        .takeIf { it in branches }
                        ?: branches.first()
                    settings.setSelectedBranch(fallbackBranch)
                }
                _updateCheckState.value = settings.load()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLogger.warn("UpdateCheck", "Could not refresh update branches: ${error.message ?: "unknown error"}")
                _updateBranchesFailed.value = true
            } finally {
                _updateBranchesLoading.value = false
            }
        }
    }

    fun refreshBranchVersions(branch: String, usedActionIds: Set<String>) {
        if (!UpdateCheckAvailability.isAvailable() || !UpdateCheckProtocol.isSelectableBranch(branch)) return
        val generation = ++branchVersionsRequestGeneration
        _branchVersionsLoading.value = true
        _branchVersionsFailed.value = false
        _branchVersions.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val versions = UpdateCheckRunner.fetchAvailableVersions(context, branch, usedActionIds)
                if (generation != branchVersionsRequestGeneration) return@launch
                if (versions == null) {
                    _branchVersionsFailed.value = true
                } else {
                    _branchVersions.value = versions
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLogger.warn("UpdateCheck", "Could not refresh version history: ${error.message ?: "unknown error"}")
                if (generation == branchVersionsRequestGeneration) _branchVersionsFailed.value = true
            } finally {
                if (generation == branchVersionsRequestGeneration) _branchVersionsLoading.value = false
            }
        }
    }

    fun downloadBranchVersion(version: BranchVersion, onDownloaded: (File) -> Unit) {
        if (!UpdateCheckAvailability.isAvailable() || _updateDownloadInProgress.value) return
        val sourceBranch = _updateCheckState.value.selectedBranch
        val apkUrl = version.apkUrl ?: return
        val size = version.apkSizeBytes ?: return
        val digest = version.apkSha256 ?: return
        val versionCode = version.versionCode ?: return
        if (versionCode <= BuildConfig.VERSION_CODE) return
        _updateDownloadInProgress.value = true
        _updateDownloadProgress.value = 0
        _updateInstallFailed.value = false
        viewModelScope.launch {
            try {
                val file = UpdateApkDownloader.download(
                    context = context,
                    version = version.version,
                    sourceBranch = sourceBranch,
                    apkUrl = apkUrl,
                    expectedSizeBytes = size,
                    expectedSha256 = digest,
                    expectedVersionCode = versionCode,
                    onProgress = { downloaded, total ->
                        _updateDownloadProgress.value =
                            ((downloaded * 100L) / total.coerceAtLeast(1L)).toInt().coerceIn(0, 100)
                    },
                )
                onDownloaded(file)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLogger.warn("UpdateCheck", "Could not download selected version: ${error.message ?: "unknown error"}")
                _updateInstallFailed.value = true
            } finally {
                _updateDownloadInProgress.value = false
            }
        }
    }

    fun downloadAvailableUpdate(onDownloaded: (File) -> Unit) {
        if (!UpdateCheckAvailability.isAvailable() || _updateDownloadInProgress.value) return
        val update = _updateCheckState.value
        val version = update.newerVersion ?: return
        val sourceBranch = update.selectedBranch
        val apkUrl = update.apkUrl ?: return
        val apkSizeBytes = update.apkSizeBytes ?: return
        val apkSha256 = update.apkSha256 ?: return
        _updateDownloadInProgress.value = true
        _updateDownloadProgress.value = 0
        _updateInstallFailed.value = false
        viewModelScope.launch {
            try {
                val file = UpdateApkDownloader.download(
                    context = context,
                    version = version,
                    sourceBranch = sourceBranch,
                    apkUrl = apkUrl,
                    expectedSizeBytes = apkSizeBytes,
                    expectedSha256 = apkSha256,
                    onProgress = { downloaded, total ->
                        _updateDownloadProgress.value =
                            ((downloaded * 100L) / total.coerceAtLeast(1L)).toInt().coerceIn(0, 100)
                    },
                )
                onDownloaded(file)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLogger.warn("UpdateCheck", "Could not download update: " + (error.message ?: "unknown error"))
                _updateInstallFailed.value = true
            } finally {
                _updateDownloadInProgress.value = false
            }
        }
    }

    fun checkForUpdatesNow() {
        if (!UpdateCheckAvailability.isAvailable() || _updateCheckInProgress.value || _updateDownloadInProgress.value) return
        _updateCheckInProgress.value = true
        _updateCheckFailed.value = false
        val sourceBranch = _updateCheckState.value.selectedBranch
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (UpdateCheckRunner.checkAndRecord(context, sourceBranch)) {
                    is UpdateCheckRunResult.Completed -> {
                        _updateCheckState.value = UpdateCheckSettings(context).load()
                    }
                    UpdateCheckRunResult.Failed -> {
                        _updateCheckFailed.value = true
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLogger.warn("UpdateCheck", "Manual release check failed: ${error.message ?: "unknown error"}")
                _updateCheckFailed.value = true
            } finally {
                _updateCheckInProgress.value = false
            }
        }
    }

    fun refreshAssociations() {
        viewModelScope.launch(Dispatchers.IO) {
            _associations.value = SetupProbes(::logFailedCheck).read("paired devices", emptyList()) {
                CompanionDeviceAssociation.list(context)
            }
        }
    }

    fun revokeLocaleGrant(token: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val store = LocaleGrantStore(context)
            store.revoke(token)
            _localeGrants.value = store.grants()
        }
    }

    /**
     * Undo for a revoke. Checked against the database when tapped: a task deleted since took its
     * grants with it, and the Settings card's own task list stops updating once it scrolls away.
     */
    fun restoreLocaleGrant(grant: LocaleGrant) {
        viewModelScope.launch(Dispatchers.IO) {
            if (OpenTaskerApp_NoHilt.awaitDb().taskDao().getById(grant.taskId) == null) return@launch
            val store = LocaleGrantStore(context)
            store.restore(grant)
            _localeGrants.value = store.grants()
        }
    }

    fun chooseUnifiedPushDistributor(activityContext: Context, onResult: (Boolean) -> Unit) {
        UnifiedPushConnector.chooseDistributor(activityContext) { selected ->
            refreshExternalState()
            onResult(selected)
        }
    }

    fun registerUnifiedPush(activityContext: Context, onResult: (Boolean) -> Unit) {
        UnifiedPushConnector.register(activityContext) { requested ->
            refreshExternalState()
            onResult(requested)
        }
    }

    fun unregisterUnifiedPush() {
        UnifiedPushConnector.unregister(context)
        refreshExternalState()
    }

    private fun refreshExternalState() {
        externalRefreshJob?.cancel()
        externalRefreshJob = viewModelScope.launch(Dispatchers.IO) {
            // Each read stands alone: this runs on every open of Setup and Settings, and one
            // uncaught throw here took both screens down with the app (#20).
            val probes = SetupProbes(::logFailedCheck)
            _associations.value = probes.read("paired devices", emptyList()) { CompanionDeviceAssociation.list(context) }
            _pushToken.value = probes.read("the push token", "") { PushTriggerTokenStore(context).token() }
            _pushRegistration.value = probes.read("UnifiedPush registration", UnifiedPushRegistrationState()) {
                UnifiedPushEndpointStore(context).state()
            }
            _localeGrants.value = probes.read("plugin grants", emptyList()) { LocaleGrantStore(context).grants() }
        }
    }

    private fun refreshUpdateCheckState() {
        if (!UpdateCheckAvailability.isAvailable()) {
            _updateCheckState.value = UpdateCheckState()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _updateCheckState.value = UpdateCheckSettings(context).load()
        }
    }
}

private class PermissionOnboardingViewModelFactory(
    private val appContext: Context,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PermissionOnboardingViewModel::class.java)) {
            return PermissionOnboardingViewModel(appContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
fun PermissionOnboardingScreen(
    contentPadding: PaddingValues,
    onMessage: (String) -> Unit,
    onUndoableMessage: UndoableMessage,
    backupState: BackupSetupState,
    onCreateBackup: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onCancelPendingRestore: () -> Unit = {},
    onReviewRestoreRollback: () -> Unit = {},
    onSnapshotPolicyChanged: (ConfigurationSnapshotPolicy) -> Unit = {},
    onSnapshotDestinationSelected: (Uri, CharArray, Boolean) -> Unit = { _, passphrase, _ ->
        passphrase.fill('\u0000')
    },
    profiles: List<Profile> = emptyList(),
    tasks: List<Task> = emptyList(),
    globalFallbackTaskId: Long? = null,
    onGlobalFallbackTaskChange: (Long?) -> Unit = {},
    settingsOnly: Boolean = false,
    onRunOnboardingAgain: (() -> Unit)? = null,
    focusRequirements: Set<SetupRequirement> = emptySet(),
) {
    val context = LocalContext.current
    val viewModelFactory = remember(context) {
        PermissionOnboardingViewModelFactory(context.applicationContext)
    }
    val setupViewModel: PermissionOnboardingViewModel = viewModel(factory = viewModelFactory)
    val advancedProtectionEnabled by AdvancedProtectionReader.enabled.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionHistory = setupViewModel.permissionHistory
    val loadedItems by setupViewModel.permissionItems.collectAsState()
    val items = loadedItems.orEmpty()
    val directBootEnabled by setupViewModel.directBootEnabled.collectAsState()
    val themeMode by setupViewModel.themeMode.collectAsState()
    val accentColorHex by setupViewModel.accentColorHex.collectAsState()
    val updateCheckState by setupViewModel.updateCheckState.collectAsState()
    val availableUpdateBranches by setupViewModel.availableUpdateBranches.collectAsState()
    val updateBranchesLoading by setupViewModel.updateBranchesLoading.collectAsState()
    val updateBranchesFailed by setupViewModel.updateBranchesFailed.collectAsState()
    val updateCheckInProgress by setupViewModel.updateCheckInProgress.collectAsState()
    val updateCheckFailed by setupViewModel.updateCheckFailed.collectAsState()
    val branchVersions by setupViewModel.branchVersions.collectAsState()
    val branchVersionsLoading by setupViewModel.branchVersionsLoading.collectAsState()
    val branchVersionsFailed by setupViewModel.branchVersionsFailed.collectAsState()
    val updateDownloadInProgress by setupViewModel.updateDownloadInProgress.collectAsState()
    val updateDownloadProgress by setupViewModel.updateDownloadProgress.collectAsState()
    val updateInstallFailed by setupViewModel.updateInstallFailed.collectAsState()
    val associations by setupViewModel.associations.collectAsState()
    val pushToken by setupViewModel.pushToken.collectAsState()
    val pushRegistration by setupViewModel.pushRegistration.collectAsState()
    val localeGrants by setupViewModel.localeGrants.collectAsState()
    val permissionGrantedMessage = stringResource(R.string.permission_granted)
    val permissionDeniedRetryMessage = stringResource(R.string.permission_denied_retry)
    val permissionDeniedSettingsMessage = stringResource(R.string.permission_denied_settings)
    val shizukuPermissionRequestedMessage = stringResource(R.string.setup_shizuku_permission_requested)
    val shizukuPermissionFailedMessage = stringResource(R.string.setup_shizuku_permission_failed)
    val shizukuModeDisabledMessage = stringResource(R.string.setup_shizuku_mode_disabled)
    val deviceAdminRemovedMessage = stringResource(R.string.setup_device_admin_removed)
    val grantCommandCopiedMessage = stringResource(R.string.setup_secure_settings_copied)
    val deviceAdminRemoveFailedMessage = stringResource(R.string.setup_device_admin_remove_failed)
    val shizukuModeEnabledMessage = stringResource(R.string.setup_shizuku_mode_enabled)
    val pushDistributorSelectedMessage = stringResource(R.string.setup_push_distributor_selected)
    val pushDistributorUnavailableMessage = stringResource(R.string.setup_push_distributor_unavailable)
    val pushRegistrationRequestedMessage = stringResource(R.string.setup_push_registration_requested)
    val pushRegistrationFailedMessage = stringResource(R.string.setup_push_registration_failed)
    val pushUnregisteredMessage = stringResource(R.string.setup_push_unregistered)
    var pendingPermission by rememberSaveable { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        pendingPermission?.let { permission ->
            val granted = results[permission] == true
            val shouldShowRationale = context.findActivity()
                ?.shouldShowRequestPermissionRationale(permission)
                ?: false
            when (permissionHistory.recordResult(permission, granted, shouldShowRationale).outcome) {
                RuntimePermissionOutcome.Granted -> onMessage(permissionGrantedMessage)
                RuntimePermissionOutcome.DeniedCanRetry -> onMessage(permissionDeniedRetryMessage)
                RuntimePermissionOutcome.SettingsRequired -> onMessage(permissionDeniedSettingsMessage)
            }
        }
        pendingPermission = null
        setupViewModel.refresh()
    }

    val usedActionIds = remember(profiles, tasks, globalFallbackTaskId) {
        collectEnabledProfileActionIds(profiles, tasks, globalFallbackTaskId)
    }
    LaunchedEffect(settingsOnly, updateCheckState.selectedBranch, usedActionIds) {
        if (settingsOnly && UpdateCheckAvailability.isAvailable()) {
            setupViewModel.refreshBranchVersions(updateCheckState.selectedBranch, usedActionIds)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                setupViewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val automationRequirements = remember(profiles, tasks) {
        SetupRequirementResolver.resolve(profiles, tasks)
    }
    // A template installed from onboarding surfaces the grants it needs. This *adds* those rows,
    // it never hides anything: a freshly installed template is disabled by default, so
    // automationRequirements is still empty and the NEEDED rows it depends on would otherwise not
    // be listed at all. Filtering instead of adding meant the row the user was sent here for could
    // be the only one on screen, and the engine's own notification and alarm rows disappeared.
    val visibleItems = remember(items, automationRequirements, focusRequirements) {
        items.map { item ->
            // Overlay access is useful for Scene overlays, but most installations do not need it
            // until a profile uses one. Keep it discoverable under Optional by default, and promote
            // the same row to Needed as soon as an enabled profile or onboarding template depends on it.
            if (SetupRequirement.OVERLAY in item.requirements) {
                val required = SetupRequirement.OVERLAY in automationRequirements ||
                    SetupRequirement.OVERLAY in focusRequirements
                item.copy(
                    section = if (required) SetupSection.NEEDED else SetupSection.OPTIONAL,
                    optional = !required,
                )
            } else {
                item
            }
        }.filter { item ->
            item.section != SetupSection.NEEDED ||
                item.requirements.any(automationRequirements::contains) ||
                item.requirements.any(focusRequirements::contains)
        }
    }
    val sectionItems = remember(visibleItems) {
        SetupSection.entries.associateWith { section ->
            visibleItems
                .filter { it.section == section }
                .sortedWith(compareBy<PermissionSetupItem> { it.granted }.thenBy { it.title })
        }
    }
    // Counted by the same flag the row's own icon reads, not by section. Counting sections meant
    // "1 of 3 ready" sat above four rows marked Required, because Battery optimization is required
    // but lives under Reliability, while "Modify system settings" is optional and lives under
    // Needed. Two definitions of required, disagreeing on screen.
    val requiredItems = remember(visibleItems) { visibleItems.filterNot { it.optional } }
    val grantedCount = requiredItems.count { it.granted }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (settingsOnly) 6.dp else 10.dp),
    ) {
        if (focusRequirements.isNotEmpty()) item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag(SETUP_FOCUS_BANNER_TAG),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(DesignSystem.Radii.md),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.setup_focus_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.setup_focus_body),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (!settingsOnly) item {
            SetupProgressCard(
                loaded = loadedItems != null,
                grantedCount = grantedCount,
                requiredCount = requiredItems.size,
            )
        }

        if (settingsOnly) {
            item { SettingsSectionLabel(stringResource(R.string.settings_section_general)) }
            if (UpdateCheckAvailability.isAvailable()) {
                item {
                    UpdateCheckSetupCard(
                        state = updateCheckState,
                        availableBranches = availableUpdateBranches.filter(UpdateCheckProtocol::isSelectableBranch),
                        branchesLoading = updateBranchesLoading,
                        branchesFailed = updateBranchesFailed,
                        onBranchSelected = setupViewModel::setUpdateSourceBranch,
                        onRefreshBranches = setupViewModel::refreshUpdateBranches,
                        versions = branchVersions,
                        versionsLoading = branchVersionsLoading,
                        versionsFailed = branchVersionsFailed,
                        onRefreshVersions = {
                            setupViewModel.refreshBranchVersions(updateCheckState.selectedBranch, usedActionIds)
                        },
                        onInstallVersion = { version ->
                            if (!context.packageManager.canRequestPackageInstalls()) {
                                onMessage(context.getString(R.string.setup_update_install_permission_prompt))
                                try {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                            Uri.parse("package:" + context.packageName),
                                        ),
                                    )
                                } catch (error: Exception) {
                                    onMessage(context.getString(R.string.setup_update_install_failed))
                                }
                            } else {
                                setupViewModel.downloadBranchVersion(version) { apkFile ->
                                    try {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            BuildConfig.APPLICATION_ID + ".fileprovider",
                                            apkFile,
                                        )
                                        val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                                            setDataAndType(uri, "application/vnd.android.package-archive")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(installIntent)
                                    } catch (error: Exception) {
                                        AppLogger.warn("UpdateCheck", "Could not open Android package installer", error)
                                        onMessage(context.getString(R.string.setup_update_install_failed))
                                    }
                                }
                            }
                        },
                        checking = updateCheckInProgress,
                        checkFailed = updateCheckFailed,
                        downloading = updateDownloadInProgress,
                        downloadProgress = updateDownloadProgress,
                        installFailed = updateInstallFailed,
                        onEnabledChange = setupViewModel::setUpdateChecksEnabled,
                        onCheckNow = setupViewModel::checkForUpdatesNow,
                        onInstallUpdate = {
                            if (!context.packageManager.canRequestPackageInstalls()) {
                                onMessage(context.getString(R.string.setup_update_install_permission_prompt))
                                try {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                            Uri.parse("package:" + context.packageName),
                                        ),
                                    )
                                } catch (error: Exception) {
                                    onMessage(context.getString(R.string.setup_update_install_failed))
                                }
                            } else {
                                setupViewModel.downloadAvailableUpdate { apkFile ->
                                    try {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            BuildConfig.APPLICATION_ID + ".fileprovider",
                                            apkFile,
                                        )
                                        val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                                            setDataAndType(uri, "application/vnd.android.package-archive")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(installIntent)
                                    } catch (error: Exception) {
                                        AppLogger.warn("UpdateCheck", "Could not open Android package installer", error)
                                        onMessage(context.getString(R.string.setup_update_install_failed))
                                    }
                                }
                            }
                        },
                    )
                }
            }
            item {
                ThemeSetupCard(
                    currentMode = themeMode,
                    currentAccentHex = accentColorHex,
                    onSelectMode = setupViewModel::setThemeMode,
                    onSelectAccent = setupViewModel::setAccentColor,
                )
            }
            item {
                DirectBootSetupCard(
                    enabled = directBootEnabled,
                    onEnabledChange = setupViewModel::setDirectBootEnabled,
                )
            }
            onRunOnboardingAgain?.let { runAgain ->
                item { RunOnboardingAgainCard(onRunAgain = runAgain) }
            }
            if (advancedProtectionEnabled) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.42f)),
                        shape = RoundedCornerShape(com.opentasker.ui.theme.DesignSystem.Radii.lg),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.setup_advanced_protection_title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.setup_advanced_protection_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }
            item { SettingsSectionLabel(stringResource(R.string.settings_section_data_recovery)) }
            item {
                BackupSetupCard(
                    state = backupState,
                    onCreateBackup = onCreateBackup,
                    onExportBackup = onExportBackup,
                    onImportBackup = onImportBackup,
                    onCancelPendingRestore = onCancelPendingRestore,
                    onReviewRestoreRollback = onReviewRestoreRollback,
                    onSnapshotPolicyChanged = onSnapshotPolicyChanged,
                    onSnapshotDestinationSelected = onSnapshotDestinationSelected,
                )
            }
            item {
                GlobalFallbackTaskCard(
                    taskId = globalFallbackTaskId,
                    tasks = tasks,
                    onTaskChange = onGlobalFallbackTaskChange,
                )
            }
            item { TermuxScriptAllowlistCard(onMessage) }
            item { SettingsSectionLabel(stringResource(R.string.settings_section_integrations)) }
            item {
                PushTriggerSetupCard(
                    token = pushToken,
                    registration = pushRegistration,
                    onChooseDistributor = {
                        setupViewModel.chooseUnifiedPushDistributor(context) { selected ->
                            onMessage(
                                if (selected) pushDistributorSelectedMessage else pushDistributorUnavailableMessage,
                            )
                        }
                    },
                    onRegister = {
                        setupViewModel.registerUnifiedPush(context) { requested ->
                            onMessage(
                                if (requested) pushRegistrationRequestedMessage else pushRegistrationFailedMessage,
                            )
                        }
                    },
                    onUnregister = {
                        setupViewModel.unregisterUnifiedPush()
                        onMessage(pushUnregisteredMessage)
                    },
                    onMessage = onMessage,
                )
            }
            item {
                CompanionSetupCard(
                    associations = associations,
                    onRefresh = setupViewModel::refreshAssociations,
                    onDisassociate = { association -> CompanionDeviceAssociation.disassociate(context, association) },
                    onMessage = onMessage,
                )
            }
            item {
                LocaleGrantManagementCard(
                    tasks = tasks,
                    grants = localeGrants,
                    onRevoke = { grant -> setupViewModel.revokeLocaleGrant(grant.token) },
                    onRestore = setupViewModel::restoreLocaleGrant,
                    onUndoableMessage = onUndoableMessage,
                )
            }
            item { SettingsSectionLabel(stringResource(R.string.settings_section_about)) }
            item {
                AboutCard(onOpenLink = { url -> openExternalLink(context, url, onMessage) })
            }
        }

        if (!settingsOnly) SetupSection.entries.forEach { section ->
            val itemsForSection = sectionItems.getValue(section)
            if (itemsForSection.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(section.titleRes()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                }
                items(itemsForSection, key = { it.title }) { item ->
                    val alreadyReadyMessage = stringResource(R.string.setup_item_already_ready, item.title)
                    PermissionSetupCard(
                        item = item,
                        onRunAction = {
                            when (val action = item.action) {
                        PermissionAction.None -> onMessage(alreadyReadyMessage)
                        is PermissionAction.RuntimePermission -> {
                            pendingPermission = action.permission
                            permissionHistory.recordRequest(action.permission)
                            permissionLauncher.launch((listOf(action.permission) + action.requestedWith).toTypedArray())
                        }
                        is PermissionAction.SettingsIntent -> openSettingsIntent(context, action.intent, onMessage)
                        is PermissionAction.OemSettings -> openOemSettings(context, action, onMessage)
                        PermissionAction.ShizukuPermission -> {
                            val requested = ShizukuPowerBackend.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                            onMessage(
                                if (requested) shizukuPermissionRequestedMessage
                                else shizukuPermissionFailedMessage,
                            )
                            setupViewModel.refresh()
                        }
                        is PermissionAction.ShizukuKillSwitch -> {
                            ShizukuPowerBackend.setKillSwitchEnabled(context, action.enabled)
                            onMessage(
                                if (action.enabled) shizukuModeDisabledMessage
                                else shizukuModeEnabledMessage,
                            )
                            setupViewModel.refresh()
                        }
                        is PermissionAction.CopyText -> {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText(action.label, action.text))
                            onMessage(grantCommandCopiedMessage)
                        }
                        PermissionAction.RemoveDeviceAdmin -> {
                            // Android has no "open my admin's page" intent, so removal has to
                            // happen here. Uninstalling is blocked while an admin is active, so
                            // leaving the user only a route into system settings would be a trap.
                            val removed = runCatching {
                                context.getSystemService(DevicePolicyManager::class.java)
                                    ?.removeActiveAdmin(LockDeviceAdminReceiver.component(context))
                            }.isSuccess
                            onMessage(if (removed) deviceAdminRemovedMessage else deviceAdminRemoveFailedMessage)
                            setupViewModel.refresh()
                        }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsIntroCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
        shape = RoundedCornerShape(com.opentasker.ui.theme.DesignSystem.Radii.xxl),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.settings_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Version, licence, and the three links the project had nowhere in the app.
 *
 * "Report a problem" opens a new issue with the build and device already in the body. Issue #14
 * came in as screenshots because there was no route from the app to the tracker and nothing said
 * what a useful report contains.
 */
@Composable
private fun AboutCard(onOpenLink: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.46f)),
        shape = RoundedCornerShape(DesignSystem.Radii.lg),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(
                        R.string.about_version,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                        BuildConfig.DISTRIBUTION,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.about_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { onOpenLink(ProjectLinks.REPOSITORY_URL) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Text(stringResource(R.string.about_source), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = { onOpenLink(ProjectLinks.RELEASES_URL) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Text(stringResource(R.string.about_releases), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            TextButton(
                onClick = { onOpenLink(ProjectLinks.LICENSE_URL) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.about_license))
            }
            Text(
                stringResource(R.string.about_report_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    onOpenLink(
                        ProjectLinks.reportProblemUrl(
                            appVersion = BuildConfig.VERSION_NAME,
                            versionCode = BuildConfig.VERSION_CODE,
                            distribution = BuildConfig.DISTRIBUTION,
                            androidRelease = Build.VERSION.RELEASE.orEmpty(),
                            sdkInt = Build.VERSION.SDK_INT,
                            device = "${Build.MANUFACTURER} ${Build.MODEL}",
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.about_report))
            }
        }
    }
}

@Composable
private fun UpdateCheckSetupCard(
    state: UpdateCheckState,
    availableBranches: List<String>,
    branchesLoading: Boolean,
    branchesFailed: Boolean,
    onBranchSelected: (String) -> Unit,
    onRefreshBranches: () -> Unit,
    versions: List<BranchVersion>,
    versionsLoading: Boolean,
    versionsFailed: Boolean,
    onRefreshVersions: () -> Unit,
    onInstallVersion: (BranchVersion) -> Unit,
    checking: Boolean,
    checkFailed: Boolean,
    downloading: Boolean,
    downloadProgress: Int,
    installFailed: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onCheckNow: () -> Unit,
    onInstallUpdate: () -> Unit,
) {
    val context = LocalContext.current
    var branchMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var versionHistoryExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedVersionTag by rememberSaveable(state.selectedBranch) { mutableStateOf<String?>(null) }
    val selectedVersion = versions.firstOrNull { it.tag == selectedVersionTag } ?: versions.firstOrNull()
    val installedVersionCode = BuildConfig.VERSION_CODE.toLong()
    val showNonDefaultBranchWarning =
        UpdateCheckProtocol.requiresNonDefaultBranchWarning(state.selectedBranch)
    val branchWarningColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        DesignSystem.SemanticColor.warningDark
    } else {
        DesignSystem.SemanticColor.warningLight
    }
    val stateText = when {
        checking -> stringResource(R.string.setup_update_status_checking, state.selectedBranch)
        downloading -> stringResource(R.string.setup_update_status_downloading, downloadProgress)
        installFailed -> stringResource(R.string.setup_update_status_install_failed)
        checkFailed -> stringResource(R.string.setup_update_status_failed)
        state.newerVersion != null -> stringResource(
            R.string.setup_update_status_available,
            state.selectedBranch,
            state.newerVersion,
        )
        state.lastCheckedAtMs != null -> stringResource(R.string.setup_update_status_current, state.selectedBranch)
        !state.enabled -> stringResource(R.string.setup_update_status_off)
        else -> stringResource(R.string.setup_update_status_pending)
    }
    val stateColor = when {
        checkFailed || installFailed -> MaterialTheme.colorScheme.error
        state.newerVersion != null -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.tertiary
    }
    val toggleDescription = stringResource(
        if (state.enabled) R.string.setup_update_enabled else R.string.setup_update_disabled,
    )
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = state.enabled, onValueChange = onEnabledChange, role = Role.Switch)
                .semantics(mergeDescendants = true) { stateDescription = toggleDescription }
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DesignSystem.Spacing.sm),
                ) {
                    Text(
                        stringResource(R.string.setup_update_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (BuildConfig.IS_BETA) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = branchWarningColor.copy(alpha = 0.14f),
                            contentColor = branchWarningColor,
                            border = BorderStroke(
                                1.dp,
                                branchWarningColor.copy(alpha = DesignSystem.Opacity.subtleBorder),
                            ),
                        ) {
                            Text(
                                stringResource(R.string.setup_update_beta_badge),
                                modifier = Modifier.padding(
                                    horizontal = DesignSystem.Spacing.sm,
                                    vertical = 2.dp,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
                Text(
                    stateText,
                    style = MaterialTheme.typography.labelMedium,
                    color = stateColor,
                )
            }
            Switch(checked = state.enabled, onCheckedChange = null)
        }
        Text(
            stringResource(R.string.setup_update_body),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = DesignSystem.Screen.cardPadding,
                    vertical = DesignSystem.Spacing.sm,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignSystem.Spacing.md),
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(DesignSystem.Spacing.xs),
            ) {
                Text(
                    stringResource(R.string.setup_update_branch_label),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.setup_update_branch_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                OutlinedButton(
                    onClick = { branchMenuExpanded = true },
                    enabled = availableBranches.isNotEmpty() && !branchesLoading && !checking && !downloading,
                ) {
                    Text(state.selectedBranch, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Filled.ExpandMore, contentDescription = stringResource(R.string.setup_update_branch_label))
                }
                DropdownMenu(
                    expanded = branchMenuExpanded,
                    onDismissRequest = { branchMenuExpanded = false },
                ) {
                    availableBranches
                        .filter(UpdateCheckProtocol::isSelectableBranch)
                        .forEach { branch ->
                            DropdownMenuItem(
                                text = { Text(branch, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                onClick = {
                                    branchMenuExpanded = false
                                    onBranchSelected(branch)
                                },
                            )
                        }
                }
            }
        }
        if (showNonDefaultBranchWarning) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DesignSystem.Screen.cardPadding)
                    .padding(bottom = DesignSystem.Spacing.sm),
                shape = MaterialTheme.shapes.medium,
                color = branchWarningColor.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(
                    1.dp,
                    branchWarningColor.copy(alpha = DesignSystem.Opacity.subtleBorder),
                ),
                tonalElevation = DesignSystem.Elevation.sm,
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = DesignSystem.Screen.cardPadding,
                        vertical = DesignSystem.Spacing.sm,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(DesignSystem.Spacing.sm),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = branchWarningColor,
                        modifier = Modifier.size(DesignSystem.ComponentSize.iconMedium),
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(DesignSystem.Spacing.xs),
                    ) {
                        Text(
                            stringResource(R.string.setup_update_branch_non_main_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = branchWarningColor,
                        )
                        Text(
                            stringResource(
                                R.string.setup_update_branch_non_main_warning,
                                state.selectedBranch,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (branchesLoading) {
            Text(
                stringResource(R.string.setup_update_branch_loading),
                modifier = Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = DesignSystem.Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                enabled = !checking && !downloading,
                onClick = { versionHistoryExpanded = !versionHistoryExpanded },
            ) {
                Text(if (versionHistoryExpanded) "Hide version history" else "Choose a version")
                Icon(
                    if (versionHistoryExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                )
            }
            if (versionsLoading) {
                Text("Loading versions…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("${versions.size} available", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (versionHistoryExpanded) {
            if (versionsFailed) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Couldn't load versions or verify their compatibility.",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRefreshVersions) { Text("Retry") }
                }
            } else if (versions.isEmpty() && !versionsLoading) {
                Text(
                    "No published APK versions were found for this branch yet.",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = DesignSystem.Spacing.sm),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(DesignSystem.Spacing.xs),
                ) {
                    items(versions, key = { it.tag }) { version ->
                        val selected = version.tag == selectedVersion?.tag
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedVersionTag = version.tag },
                            shape = MaterialTheme.shapes.medium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        "v${version.version}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        version.versionCode?.let { "Android versionCode $it" } ?: "Version code not published",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (version.isLatest) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = "Latest version on this branch",
                                        tint = Color(0xFF219653),
                                    )
                                }
                                if (!version.updaterSupportKnown || !version.supportsInAppUpdates || version.missingActionIds.isNotEmpty()) {
                                    Icon(
                                        Icons.Filled.Error,
                                        contentDescription = "Version warning",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                } else if (!version.compatibilityKnown) {
                                    Icon(
                                        Icons.Filled.Info,
                                        contentDescription = "Compatibility not verified",
                                        tint = branchWarningColor,
                                    )
                                }
                            }
                        }
                    }
                }
                selectedVersion?.let { version ->
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = DesignSystem.Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(DesignSystem.Spacing.sm),
                    ) {
                        if (version.missingActionIds.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "This version is missing actions used by enabled profiles.",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    Text(
                                        version.missingActionIds.joinToString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        } else if (!version.compatibilityKnown) {
                            Text(
                                "Action compatibility couldn't be verified for this release. Treat it as unverified before installing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = branchWarningColor,
                            )
                        }
                        if (!version.updaterSupportKnown || !version.supportsInAppUpdates) {
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Text(
                                    if (version.updaterSupportKnown) {
                                        "This version doesn't include in-app updating. It won't be able to check for or install later versions itself."
                                    } else {
                                        "This release doesn't confirm that in-app updating is included. Treat it as a manual-update build; it may not be able to install later versions itself."
                                    },
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        when {
                            version.versionCode == null -> {
                                Text(
                                    "This release didn't publish an Android version code, so OpenTasker can't verify an in-place update.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(version.releaseUrl))) }
                                }) { Text("Open release page") }
                            }
                            version.versionCode < installedVersionCode -> {
                                Text(
                                    "This is older than the installed version. Android blocks downgrades over an installed app. Download the APK first, back up your automations, uninstall OpenTasker, then install the downloaded APK from Downloads. Uninstalling clears local app data.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                OutlinedButton(
                                    enabled = version.apkUrl != null,
                                    onClick = {
                                        val url = version.apkUrl ?: version.releaseUrl
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Download older APK for a fresh install") }
                            }
                            version.versionCode == installedVersionCode -> {
                                Text(
                                    "This version is already installed.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                            version.canInstallInPlace -> {
                                Button(
                                    enabled = !checking && !downloading,
                                    onClick = { onInstallVersion(version) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Download and install selected version") }
                            }
                            else -> {
                                TextButton(
                                    onClick = {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(version.releaseUrl))) }
                                    },
                                ) { Text("Open release page") }
                            }
                        }
                    }
                }
            }
        }
        if (branchesFailed) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    stringResource(R.string.setup_update_branch_failed),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onRefreshBranches) {
                    Text(stringResource(R.string.setup_update_branch_retry))
                }
            }
        }
        if (checking || downloading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                enabled = !checking && !downloading,
                onClick = onCheckNow,
            ) {
                Text(
                    stringResource(
                        if (checking) R.string.setup_update_checking else R.string.setup_update_check_now,
                    ),
                )
            }
            if (state.canInstallUpdate) {
                TextButton(
                    enabled = !checking && !downloading,
                    onClick = onInstallUpdate,
                ) {
                    Text(
                        stringResource(
                            if (downloading) R.string.setup_update_downloading
                            else R.string.setup_update_install,
                        ),
                    )
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = DesignSystem.Spacing.sm),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Original OpenTasker project",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        "This is the upstream project, separate from this fork. Its APK is signed independently. To return to this fork's current version later, you'll need a fresh install. Back up first: uninstalling either app removes its local data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SysAdminDoc/OpenTasker")),
                                )
                            }
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("Open original repository") }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    }
}

@Composable
private fun GlobalFallbackTaskCard(
    taskId: Long?,
    tasks: List<Task>,
    onTaskChange: (Long?) -> Unit,
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    val selectedName = tasks.firstOrNull { it.id == taskId }?.name ?: stringResource(R.string.label_none)
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.setup_global_fallback_title), style = MaterialTheme.typography.titleSmall)
                Text(selectedName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.ExpandMore, contentDescription = stringResource(R.string.setup_global_fallback_label))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.label_none)) },
                        onClick = {
                            menuExpanded = false
                            onTaskChange(null)
                        },
                    )
                    tasks.forEach { task ->
                        DropdownMenuItem(
                            text = { Text(task.name) },
                            onClick = {
                                menuExpanded = false
                                onTaskChange(task.id)
                            },
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    }
}

@Composable
internal fun CompanionSetupCard(
    associations: List<CompanionAssociation>,
    onRefresh: () -> Unit,
    onDisassociate: (CompanionAssociation) -> Boolean,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val resources = LocalResources.current
    val associatedMessage = stringResource(R.string.setup_companion_associated)
    val failureMessage = stringResource(R.string.setup_companion_failed)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) {
        onRefresh()
        onMessage(associatedMessage)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.46f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.setup_companion_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.setup_companion_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (associations.isEmpty()) {
                Text(
                    stringResource(R.string.setup_companion_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                associations.forEach { association ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(association.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        OutlinedButton(
                            onClick = {
                                // Nothing to undo with: pairing again goes through the system picker,
                                // so the message says how to get the device back.
                                val removed = onDisassociate(association)
                                onRefresh()
                                onMessage(
                                    resources.getString(
                                        if (removed) R.string.setup_companion_removed else R.string.setup_companion_remove_failed,
                                        association.label,
                                    ),
                                )
                            },
                        ) {
                            Text(stringResource(R.string.setup_companion_revoke))
                        }
                    }
                }
            }
            Button(
                enabled = activity != null,
                onClick = {
                    val started = activity?.let {
                        CompanionDeviceAssociation.associate(it) { result ->
                            when (result) {
                                is CompanionAssociationResult.Found -> launcher.launch(
                                    IntentSenderRequest.Builder(result.intentSender).build(),
                                )
                                is CompanionAssociationResult.Created -> {
                                    onRefresh()
                                    onMessage(associatedMessage)
                                }
                                is CompanionAssociationResult.Failed -> onMessage(
                                    result.message.ifBlank { failureMessage },
                                )
                            }
                        }
                    } ?: false
                    if (!started) onMessage(failureMessage)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.setup_companion_associate))
            }
        }
    }
}

@Composable
private fun PushTriggerSetupCard(
    token: String,
    registration: UnifiedPushRegistrationState,
    onChooseDistributor: () -> Unit,
    onRegister: () -> Unit,
    onUnregister: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val tokenLabel = stringResource(R.string.setup_push_title)
    val copiedMessage = stringResource(R.string.setup_push_copied)
    val endpointCopiedMessage = stringResource(R.string.setup_push_endpoint_copied)
    val statusText = when (registration.status) {
        UnifiedPushRegistrationStatus.IDLE -> stringResource(R.string.setup_push_status_idle)
        UnifiedPushRegistrationStatus.REGISTERING -> stringResource(R.string.setup_push_status_registering)
        UnifiedPushRegistrationStatus.REGISTERED -> stringResource(R.string.setup_push_status_registered)
        UnifiedPushRegistrationStatus.UNREGISTERED -> stringResource(R.string.setup_push_status_unregistered)
        UnifiedPushRegistrationStatus.TEMPORARILY_UNAVAILABLE -> {
            stringResource(R.string.setup_push_status_temporarily_unavailable)
        }
        UnifiedPushRegistrationStatus.REGISTRATION_FAILED -> stringResource(
            R.string.setup_push_status_failed,
            stringResource(pushFailureReasonRes(registration.failureReason)),
        )
    }
    val distributor = registration.distributor
        ?: stringResource(R.string.setup_push_distributor_none)
    val endpoint = registration.endpoint
    val canUnregister = registration.status != UnifiedPushRegistrationStatus.IDLE &&
        registration.status != UnifiedPushRegistrationStatus.UNREGISTERED
    val canRegister = registration.status != UnifiedPushRegistrationStatus.REGISTERING
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.46f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.setup_push_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.setup_push_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.setup_push_status, statusText),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.setup_push_distributor, distributor),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (endpoint != null) {
                Text(
                    stringResource(
                        R.string.setup_push_endpoint,
                        registration.endpointHost ?: stringResource(R.string.setup_push_endpoint_unknown),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onChooseDistributor,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(stringResource(R.string.setup_push_choose_distributor))
                }
                if (canUnregister) {
                    OutlinedButton(
                        onClick = onUnregister,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(stringResource(R.string.setup_push_unregister))
                    }
                } else {
                    Button(
                        onClick = onRegister,
                        enabled = canRegister,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(stringResource(R.string.setup_push_register))
                    }
                }
            }
            if (endpoint != null) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText(tokenLabel, endpoint))
                        onMessage(endpointCopiedMessage)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(stringResource(R.string.setup_push_copy_endpoint))
                }
            }
            Text(
                stringResource(R.string.setup_push_legacy_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.setup_push_token, token),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText(tokenLabel, token))
                    onMessage(copiedMessage)
                },
                enabled = token.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.setup_push_copy))
            }
        }
    }
}

private fun pushFailureReasonRes(reason: String?): Int = when (reason) {
    "VAPID_REQUIRED" -> R.string.setup_push_failure_vapid
    "NETWORK" -> R.string.setup_push_failure_network
    "ACTION_REQUIRED" -> R.string.setup_push_failure_action_required
    "INTERNAL_ERROR" -> R.string.setup_push_failure_internal
    "NO_DISTRIBUTOR" -> R.string.setup_push_failure_no_distributor
    else -> R.string.setup_push_failure_unknown
}

@Composable
internal fun LocaleGrantManagementCard(
    tasks: List<Task>,
    grants: List<LocaleGrant>,
    onRevoke: (LocaleGrant) -> Unit,
    onRestore: (LocaleGrant) -> Unit,
    onUndoableMessage: UndoableMessage,
) {
    val resources = LocalResources.current
    val taskNames = remember(tasks) { tasks.associateBy(Task::id) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.46f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.setup_locale_grants_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.setup_locale_grants_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (grants.isEmpty()) {
                Text(
                    stringResource(R.string.setup_locale_grants_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                grants.forEach { grant ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                taskNames[grant.taskId]?.name
                                    ?: stringResource(R.string.setup_locale_grant_unknown_task, grant.taskId),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                stringResource(R.string.setup_locale_grant_task_id, grant.taskId),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                val label = taskNames[grant.taskId]?.name
                                    ?: resources.getString(R.string.setup_locale_grant_unknown_task, grant.taskId)
                                onRevoke(grant)
                                onUndoableMessage(resources.getString(R.string.setup_locale_grant_revoked, label)) {
                                    onRestore(grant)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(stringResource(R.string.setup_locale_grant_revoke))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSetupCard(
    currentMode: ThemeMode,
    currentAccentHex: String,
    onSelectMode: (ThemeMode) -> Unit,
    onSelectAccent: (String) -> Unit,
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    var customHex by rememberSaveable(currentAccentHex) { mutableStateOf(currentAccentHex) }
    var showColorPicker by rememberSaveable { mutableStateOf(false) }
    var pickerHue by rememberSaveable { mutableStateOf(0f) }
    var pickerSaturation by rememberSaveable { mutableStateOf(0.72f) }
    var pickerBrightness by rememberSaveable { mutableStateOf(0.92f) }
    val accentPresets = listOf("#B69CFF", "#9B7BFF", "#D0A4FF", "#E18BCE", "#805AD5")
    val offeredModes = ThemeMode.entries.filter {
        it != ThemeMode.Dynamic || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
    val modeLabels = ThemeMode.entries.associateWith { mode ->
        when (mode) {
            ThemeMode.System -> stringResource(R.string.theme_system)
            ThemeMode.Dark -> stringResource(R.string.theme_dark)
            ThemeMode.Light -> stringResource(R.string.theme_light)
            ThemeMode.HighContrast -> stringResource(R.string.theme_high_contrast)
            ThemeMode.Amoled -> stringResource(R.string.theme_amoled)
            ThemeMode.Dynamic -> stringResource(R.string.theme_dynamic)
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(DesignSystem.Radii.md),
            ) {
                Icon(
                    Icons.Outlined.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(12.dp).size(22.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.setup_theme_label), style = MaterialTheme.typography.titleSmall)
                Text(modeLabels.getValue(currentMode), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.ExpandMore, contentDescription = stringResource(R.string.setup_theme_label))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    offeredModes.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(modeLabels.getValue(mode)) },
                            onClick = {
                                menuExpanded = false
                                onSelectMode(mode)
                            },
                            modifier = Modifier.semantics {
                                role = Role.RadioButton
                                selected = mode == currentMode
                            },
                            trailingIcon = if (mode == currentMode) {
                                { Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.label_selected)) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Accent color", style = MaterialTheme.typography.labelLarge)
            Text(
                "Choose one base color. Matching light, dark, and container shades are generated automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                accentPresets.forEach { preset ->
                    val swatch = Color(android.graphics.Color.parseColor(preset))
                    Surface(
                        modifier = Modifier.size(34.dp).clickable {
                            customHex = preset
                            onSelectAccent(preset)
                        },
                        color = swatch,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(
                            2.dp,
                            if (currentAccentHex.equals(preset, true)) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.outlineVariant,
                        ),
                    ) {}
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = customHex,
                    onValueChange = { customHex = it.take(7) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Custom HEX") },
                    singleLine = true,
                    placeholder = { Text("#B69CFF") },
                )
                OutlinedButton(
                    onClick = {
                        val normalized = if (customHex.startsWith("#")) customHex else "#$customHex"
                        onSelectAccent(normalized)
                    },
                    enabled = Regex("#?[0-9A-Fa-f]{6}").matches(customHex),
                ) { Text("Apply") }
            }
            OutlinedButton(
                onClick = {
                    val hsv = FloatArray(3)
                    val baseColor = runCatching {
                        android.graphics.Color.parseColor(currentAccentHex)
                    }.getOrDefault(android.graphics.Color.parseColor(ThemePreference.DEFAULT_ACCENT_HEX))
                    android.graphics.Color.colorToHSV(baseColor, hsv)
                    pickerHue = hsv[0]
                    pickerSaturation = hsv[1]
                    pickerBrightness = hsv[2]
                    showColorPicker = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Palette, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Choose custom color")
            }
            Text("Generated shades", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Primary" to MaterialTheme.colorScheme.primary,
                    "Container" to MaterialTheme.colorScheme.primaryContainer,
                    "Accent" to MaterialTheme.colorScheme.tertiary,
                    "Soft" to MaterialTheme.colorScheme.tertiaryContainer,
                ).forEach { (label, shade) ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().height(28.dp),
                            color = shade,
                            shape = RoundedCornerShape(DesignSystem.Radii.sm),
                        ) {}
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (showColorPicker) {
                val pickerArgb = android.graphics.Color.HSVToColor(
                    floatArrayOf(pickerHue, pickerSaturation, pickerBrightness),
                )
                val pickerHex = String.format(
                    java.util.Locale.ROOT,
                    "#%06X",
                    pickerArgb and 0x00FFFFFF,
                )
                AlertDialog(
                    onDismissRequest = { showColorPicker = false },
                    title = { Text("Choose accent color") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                color = Color(pickerArgb),
                                shape = RoundedCornerShape(DesignSystem.Radii.md),
                            ) {}
                            Text(pickerHex, style = MaterialTheme.typography.labelLarge)
                            Text("Hue · ${pickerHue.toInt()}°", style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = pickerHue,
                                onValueChange = { pickerHue = it },
                                valueRange = 0f..360f,
                            )
                            Text("Saturation · ${(pickerSaturation * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = pickerSaturation,
                                onValueChange = { pickerSaturation = it },
                                valueRange = 0f..1f,
                            )
                            Text("Brightness · ${(pickerBrightness * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = pickerBrightness,
                                onValueChange = { pickerBrightness = it },
                                valueRange = 0f..1f,
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                customHex = pickerHex
                                onSelectAccent(pickerHex)
                                showColorPicker = false
                            },
                        ) { Text("Use color") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showColorPicker = false }) { Text("Cancel") }
                    },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    }
}

@Composable
private fun RunOnboardingAgainCard(onRunAgain: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.settings_run_onboarding_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.settings_run_onboarding_body),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            OutlinedButton(onClick = onRunAgain) {
                Text(stringResource(R.string.settings_run_onboarding_action))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    }
}

@Composable
private fun DirectBootSetupCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val stateDescription = stringResource(
        if (enabled) R.string.setup_direct_boot_enabled else R.string.setup_direct_boot_disabled,
    )

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = enabled, onValueChange = onEnabledChange, role = Role.Switch)
                .semantics(mergeDescendants = true) { this.stateDescription = stateDescription }
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(DesignSystem.Radii.md),
            ) {
                Icon(
                    Icons.Outlined.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(12.dp).size(22.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.setup_direct_boot_title), style = MaterialTheme.typography.titleSmall)
                Text(stateDescription, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            }
            Switch(checked = enabled, onCheckedChange = null)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    }
}

@Composable
private fun ThemeChoice(
    mode: ThemeMode,
    selected: Boolean,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = when (mode) {
        ThemeMode.System -> stringResource(R.string.theme_system)
        ThemeMode.Dark -> stringResource(R.string.theme_dark)
        ThemeMode.Light -> stringResource(R.string.theme_light)
        ThemeMode.HighContrast -> stringResource(R.string.theme_high_contrast)
        ThemeMode.Amoled -> stringResource(R.string.theme_amoled)
        ThemeMode.Dynamic -> stringResource(R.string.theme_dynamic)
    }
    val accent = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val selectionDescription = stringResource(
        if (selected) R.string.a11y_option_selected else R.string.a11y_option_not_selected,
        label,
    )
    Surface(
        modifier = modifier
            .heightIn(min = 52.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = { if (!selected) onSelect(mode) },
            )
            .semantics {
                this.selected = selected
                stateDescription = selectionDescription
            },
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.48f)
        },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = if (selected) 0.58f else 0.72f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (selected) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = selectionDescription,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
internal fun BackupSetupCard(
    state: BackupSetupState,
    onCreateBackup: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onCancelPendingRestore: () -> Unit,
    onReviewRestoreRollback: () -> Unit,
    onSnapshotPolicyChanged: (ConfigurationSnapshotPolicy) -> Unit,
    onSnapshotDestinationSelected: (Uri, CharArray, Boolean) -> Unit,
    initiallyExpanded: Boolean = false,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val summary = when {
        state.pendingRestore -> stringResource(R.string.setup_backup_restore_staged)
        state.latestBackupName != null -> stringResource(R.string.setup_backup_available)
        else -> stringResource(R.string.setup_backup_none)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.46f)),
        shape = RoundedCornerShape(DesignSystem.Radii.lg),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().expandCollapseToggle(expanded) { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.setup_backup_label), style = MaterialTheme.typography.titleSmall)
                    Text(summary, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = summary,
                )
            }
            if (expanded) {
                Text(
                    stringResource(R.string.setup_backup_helper_full),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BackupStateBanner(state)
                Button(
                    onClick = onCreateBackup,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(if (state.busy) stringResource(R.string.setup_backup_working) else stringResource(R.string.setup_backup_create))
                }
                // No Stop here on purpose: see the 2026-09-03 entry in docs/DECISIONS.md.
                if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onExportBackup,
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        Text(stringResource(R.string.action_export), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = onImportBackup,
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        Text(stringResource(R.string.action_import), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (state.pendingRestore) {
                    OutlinedButton(
                        onClick = onCancelPendingRestore,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(stringResource(R.string.setup_backup_restore_cancel))
                    }
                }
                state.lastRestoreRollback?.let { rollback ->
                    val context = LocalContext.current
                    val formatter = remember(context) { displayDateTimeFormat(context, withSeconds = false) }
                    Text(
                        stringResource(R.string.setup_backup_rollback_kept, formatter.format(Date(rollback.restoredAtMs))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = onReviewRestoreRollback,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(stringResource(R.string.setup_backup_rollback_action))
                    }
                }
                SnapshotScheduleControls(
                    policy = state.snapshotPolicy,
                    status = state.snapshotStatus,
                    enabled = !state.busy,
                    onPolicyChanged = onSnapshotPolicyChanged,
                    onDestinationSelected = onSnapshotDestinationSelected,
                )
            }
        }
    }
}

@Composable
private fun BackupStateBanner(state: BackupSetupState) {
    val color = when {
        state.pendingRestore -> MaterialTheme.colorScheme.primary
        state.latestBackupName != null -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }
    val title = when {
        state.pendingRestore -> stringResource(R.string.setup_backup_restore_staged)
        state.latestBackupName != null -> stringResource(R.string.setup_backup_available)
        else -> stringResource(R.string.setup_backup_none)
    }
    val body = when {
        state.pendingRestore -> state.pendingRestoreSummary?.let { summary ->
            summary.error?.let { error -> stringResource(R.string.setup_backup_restore_unreadable, error) }
                ?: stringResource(
                    R.string.setup_backup_restore_summary,
                    summary.sourceLabel,
                    summary.schemaVersion,
                    summary.profileCount,
                    summary.taskCount,
                    summary.sceneCount,
                )
        } ?: stringResource(R.string.setup_backup_restore_body)
        state.latestBackupName != null -> stringResource(R.string.setup_backup_latest, state.latestBackupName)
        else -> stringResource(R.string.setup_backup_none_body)
    }
    Surface(
        // This banner is how a finished backup or a staged restore reports itself. It replaces its
        // own text with no focus change, so without a live region a screen-reader user is told
        // nothing when the work they started completes.
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.26f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                if (state.latestBackupName != null || state.pendingRestore) Icons.Filled.CheckCircle else Icons.Filled.Info,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PermissionSetupCard(
    item: PermissionSetupItem,
    onRunAction: () -> Unit,
) {
    val status = item.status()
    val stateLabel = stringResource(
        when (status) {
            SetupRowStatus.DETECTED -> R.string.status_detected
            SetupRowStatus.OPTIONAL -> R.string.status_optional
            SetupRowStatus.READY -> R.string.status_ready
            SetupRowStatus.NEEDS_SETUP -> R.string.status_needs_setup
            SetupRowStatus.UNAVAILABLE -> R.string.status_unavailable
        },
    )
    val stateColor = when (status) {
        SetupRowStatus.DETECTED, SetupRowStatus.READY -> MaterialTheme.colorScheme.tertiary
        SetupRowStatus.OPTIONAL -> MaterialTheme.colorScheme.secondary
        SetupRowStatus.NEEDS_SETUP -> MaterialTheme.colorScheme.error
        SetupRowStatus.UNAVAILABLE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                color = stateColor.copy(alpha = 0.14f),
                shape = RoundedCornerShape(DesignSystem.Radii.lg),
            ) {
                Box(modifier = Modifier.padding(10.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        when {
                            item.granted -> Icons.Filled.CheckCircle
                            item.optional || item.unavailable -> Icons.Filled.Info
                            else -> Icons.Filled.Error
                        },
                        contentDescription = when {
                            item.granted -> stringResource(R.string.status_granted)
                            item.unavailable -> stateLabel
                            item.optional -> stringResource(R.string.status_optional)
                            else -> stringResource(R.string.status_required)
                        },
                        tint = stateColor,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stateLabel, style = MaterialTheme.typography.labelMedium, color = stateColor)
            }
            when {
                !item.granted && item.action != PermissionAction.None -> {
                    OutlinedButton(
                        onClick = onRunAction,
                        shape = RoundedCornerShape(DesignSystem.Radii.lg),
                    ) {
                        Text(item.actionLabel, maxLines = 1)
                    }
                }
                item.granted && item.allowActionWhenGranted -> {
                    IconButton(onClick = onRunAction) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.setup_review_settings))
                    }
                }
                else -> {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = stateLabel,
                        tint = stateColor,
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    }
}

@Composable
private fun PermissionMetric(value: String, label: String, modifier: Modifier = Modifier) {
    SummaryMetric(value = value, label = label, modifier = modifier)
}

@Composable
private fun PermissionStatusPill(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color, RoundedCornerShape(2.dp)),
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun SettingsSectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
    )
}


/**
 * Opt-in encrypted snapshot schedule.
 *
 * Archives are written only to the user-selected SAF tree and never applied automatically.
 */
@Composable
private fun SnapshotScheduleControls(
    policy: ConfigurationSnapshotPolicy,
    status: ConfigurationSnapshotStatus,
    enabled: Boolean,
    onPolicyChanged: (ConfigurationSnapshotPolicy) -> Unit,
    onDestinationSelected: (Uri, CharArray, Boolean) -> Unit,
) {
    val context = LocalContext.current
    var pendingDestinationUri by rememberSaveable { mutableStateOf<String?>(null) }
    var enableAfterSelection by rememberSaveable { mutableStateOf(false) }
    val destinationPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        pendingDestinationUri = uri?.toString()
    }
    fun chooseDestination(enableSchedule: Boolean) {
        enableAfterSelection = enableSchedule
        destinationPicker.launch(policy.destinationTreeUri?.let(Uri::parse))
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = policy.enabled,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = { checked ->
                        if (checked && policy.destinationTreeUri == null) {
                            chooseDestination(enableSchedule = true)
                        } else {
                            onPolicyChanged(policy.copy(enabled = checked))
                        }
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.setup_snapshots_label), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.setup_snapshots_helper),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = policy.enabled, onCheckedChange = null, enabled = enabled)
        }
        if (policy.enabled || policy.destinationTreeUri != null) {
            Text(
                stringResource(
                    if (policy.destinationTreeUri == null) {
                        R.string.setup_snapshots_destination_missing
                    } else {
                        R.string.setup_snapshots_destination_ready
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (policy.destinationTreeUri == null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            OutlinedButton(
                onClick = { chooseDestination(enableSchedule = policy.enabled) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    stringResource(
                        if (policy.destinationTreeUri == null) {
                            R.string.setup_snapshots_destination_choose
                        } else {
                            R.string.setup_snapshots_destination_change
                        },
                    ),
                )
            }
        }
        if (policy.enabled) {
            Text(
                stringResource(
                    R.string.setup_snapshots_retention,
                    policy.maxSnapshots,
                    policy.maxAgeDays,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SNAPSHOT_RETENTION_CHOICES.forEach { choice ->
                    FilterChip(
                        selected = policy.maxSnapshots == choice.first && policy.maxAgeDays == choice.second,
                        onClick = {
                            onPolicyChanged(policy.copy(maxSnapshots = choice.first, maxAgeDays = choice.second))
                        },
                        enabled = enabled,
                        label = {
                            Text(
                                stringResource(R.string.setup_snapshots_choice, choice.first, choice.second),
                                maxLines = 1,
                            )
                        },
                    )
                }
            }
            Text(
                snapshotStatusLabel(context, status),
                style = MaterialTheme.typography.bodySmall,
                color = if (status.lastFailureAtMs != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }

    pendingDestinationUri?.let { encodedUri ->
        SnapshotRecoveryPassphraseDialog(
            onDismiss = { pendingDestinationUri = null },
            onConfirm = { passphrase ->
                pendingDestinationUri = null
                onDestinationSelected(Uri.parse(encodedUri), passphrase, enableAfterSelection)
            },
        )
    }
}

@Composable
private fun SnapshotRecoveryPassphraseDialog(
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
) {
    var passphrase by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    val longEnough = passphrase.length >= SNAPSHOT_MIN_PASSPHRASE_CHARS
    val matches = passphrase == confirmation
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setup_snapshots_passphrase_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.setup_snapshots_passphrase_body))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.setup_snapshots_passphrase_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    // Masking is not enough on its own: without a password keyboard type the IME
                    // keeps autocorrect and its personal dictionary on, learns the passphrase and
                    // can offer it as a suggestion in another app later.
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false,
                    ),
                    singleLine = true,
                    isError = passphrase.isNotEmpty() && !longEnough,
                )
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it },
                    label = { Text(stringResource(R.string.setup_snapshots_passphrase_confirm)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false,
                    ),
                    singleLine = true,
                    isError = confirmation.isNotEmpty() && !matches,
                )
                if ((passphrase.isNotEmpty() && !longEnough) || (confirmation.isNotEmpty() && !matches)) {
                    Text(
                        stringResource(R.string.setup_snapshots_passphrase_error, SNAPSHOT_MIN_PASSPHRASE_CHARS),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val recoveryPassphrase = passphrase.toCharArray()
                    passphrase = ""
                    confirmation = ""
                    onConfirm(recoveryPassphrase)
                },
                enabled = longEnough && matches,
            ) {
                Text(stringResource(R.string.setup_snapshots_passphrase_save))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private val SNAPSHOT_RETENTION_CHOICES = listOf(3 to 7, 5 to 14, 10 to 30)
private const val SNAPSHOT_MIN_PASSPHRASE_CHARS = 12

private fun snapshotStatusLabel(context: Context, status: ConfigurationSnapshotStatus): String {
    val storage = android.text.format.Formatter.formatShortFileSize(context, status.storageBytes)
    val failure = status.lastFailureAtMs
    if (failure != null) {
        return context.getString(
            R.string.setup_snapshots_status_failed,
            formatSnapshotTimestamp(context, failure),
            status.lastFailureMessage ?: context.getString(R.string.setup_snapshots_status_unknown_error),
        )
    }
    val success = status.lastSuccessAtMs
        ?: return context.getString(R.string.setup_snapshots_status_pending)
    return context.getString(
        R.string.setup_snapshots_status_ok,
        formatSnapshotTimestamp(context, success),
        status.snapshotCount,
        storage,
    )
}

/** In the phone's own date order and clock, like every other visible time (A-363). */
private fun formatSnapshotTimestamp(context: Context, epochMs: Long): String =
    displayDateTimeFormat(context, withSeconds = false).format(Date(epochMs))

/**
 * The ready count at the top of Setup. Before the first permission read there are no rows, and an
 * empty list counted as "0 of 0 ready", a green Ready pill and 0% for a moment on every open.
 */
@Composable
internal fun SetupProgressCard(loaded: Boolean, grantedCount: Int, requiredCount: Int) {
    val pendingCount = requiredCount - grantedCount
    val progress = if (requiredCount == 0) 0f else grantedCount.toFloat() / requiredCount.toFloat()
    val accent = if (pendingCount == 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(DesignSystem.Radii.md),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (loaded) {
                            stringResource(R.string.setup_progress_ready_count, grantedCount, requiredCount)
                        } else {
                            stringResource(R.string.setup_progress_loading)
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.setup_checklist_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        // No line cap: at two lines this sentence cut off mid-word on a
                        // 411dp phone, so the one line explaining what an incomplete
                        // setup actually costs you ended at "until setup is co...".
                    )
                    if (loaded) {
                        Spacer(Modifier.height(6.dp))
                        PermissionStatusPill(
                            if (pendingCount == 0) stringResource(R.string.status_ready) else stringResource(R.string.status_pending, pendingCount),
                            accent,
                        )
                    }
                }
                if (loaded) {
                    Text(
                        stringResource(R.string.setup_progress_percent, (progress * 100).toInt()),
                        style = MaterialTheme.typography.titleLarge,
                        color = accent,
                    )
                }
            }
            if (loaded) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
            Text(
                stringResource(R.string.setup_status_order),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
