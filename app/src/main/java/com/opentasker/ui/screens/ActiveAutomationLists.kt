package com.opentasker.ui.screens

import com.opentasker.ui.icons.Icons
import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opentasker.app.R
import com.opentasker.core.actions.ActionMetadataRegistry
import com.opentasker.core.actions.ActionSummaryFormatter
import com.opentasker.core.capabilities.ActionCapabilityRegistry
import com.opentasker.core.capabilities.CapabilityLevel
import com.opentasker.core.contexts.contextConfigSummary
import com.opentasker.core.engine.SingleActionRun
import com.opentasker.core.model.ActionSpec
import com.opentasker.core.model.ContextSpec
import com.opentasker.core.model.Profile
import com.opentasker.core.model.Task
import com.opentasker.core.storage.StorageDecodeIssue
import com.opentasker.ui.theme.DesignSystem
import com.opentasker.ui.utils.expandCollapseToggle
import com.opentasker.widget.WidgetIconCatalog
import com.opentasker.ui.icons.WidgetIconImage
@Composable
internal fun ProfilesScreen(
    profiles: List<Profile>,
    tasks: List<Task>,
    storageDecodeIssues: List<StorageDecodeIssue>,
    onCreateProfile: () -> Unit,
    onBrowseTemplates: () -> Unit,
    onPreviewProfileShare: () -> Unit,
    onPreflightProfile: (Profile) -> Unit,
    onRunProfile: (Profile) -> Unit = {},
    runBusy: Boolean = false,
    onExportOpenTaskerBundle: () -> Unit,
    onImportOpenTaskerBundle: () -> Unit,
    onImportOpenTaskerBundleText: () -> Unit,
    openTaskerBundleBusy: Boolean,
    onImportTaskerXml: () -> Unit,
    onExportTaskerXml: () -> Unit,
    taskerImportBusy: Boolean,
    onEditProfile: (Profile) -> Unit,
    onUndoProfileEdit: (Profile) -> Unit = {},
    onRedoProfileEdit: (Profile) -> Unit = {},
    onDeleteProfile: (Profile) -> Unit,
    onDuplicateProfile: (Profile) -> Unit = {},
    contentPadding: PaddingValues,
    contentLoaded: Boolean = true,
    historyAvailability: EditHistoryAvailabilityState = EditHistoryAvailabilityState(),
    /** Every backing action record, whatever the project scope, so cards can show the correct action count. */
    allTasks: List<Task> = tasks,
) {
    // An unread database and an empty one look identical from here; without this gate a cold
    // start with existing data flashes the first-run screen before Room's first emission.
    if (!contentLoaded) {
        ContentLoadingState(contentPadding)
        return
    }
    // Both lists arrive scoped to the selected project, and a profile can run a task that lives
    // in another project. Gating on tasks alone hid such a profile behind the first-run screen.
    if (tasks.isEmpty() && profiles.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.empty_first_automation_title),
            body = stringResource(R.string.empty_first_automation_body),
            actionLabel = stringResource(R.string.action_browse_templates),
            onAction = onBrowseTemplates,
            secondaryActionLabel = if (openTaskerBundleBusy) stringResource(R.string.import_reading_bundle) else stringResource(R.string.import_import_json),
            onSecondaryAction = onImportOpenTaskerBundle,
            secondaryActionEnabled = !openTaskerBundleBusy,
            quinaryActionLabel = stringResource(R.string.import_paste_json_action),
            onQuinaryAction = onImportOpenTaskerBundleText,
            quinaryActionEnabled = !openTaskerBundleBusy,
            tertiaryActionLabel = if (taskerImportBusy) stringResource(R.string.import_reading_xml) else stringResource(R.string.action_import_tasker_xml),
            onTertiaryAction = onImportTaskerXml,
            tertiaryActionEnabled = !taskerImportBusy,
            quaternaryActionLabel = stringResource(R.string.action_create_blank_profile),
            onQuaternaryAction = onCreateProfile,
            contentPadding = contentPadding,
        )
        return
    }
    if (profiles.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.empty_first_profile_title),
            body = stringResource(R.string.empty_first_profile_body),
            actionLabel = stringResource(R.string.action_browse_templates),
            onAction = onBrowseTemplates,
            secondaryActionLabel = if (openTaskerBundleBusy) stringResource(R.string.import_reading_bundle) else stringResource(R.string.import_import_json),
            onSecondaryAction = onImportOpenTaskerBundle,
            secondaryActionEnabled = !openTaskerBundleBusy,
            quinaryActionLabel = stringResource(R.string.import_paste_json_action),
            onQuinaryAction = onImportOpenTaskerBundleText,
            quinaryActionEnabled = !openTaskerBundleBusy,
            tertiaryActionLabel = if (taskerImportBusy) stringResource(R.string.import_reading_xml) else stringResource(R.string.action_import_tasker_xml),
            onTertiaryAction = onImportTaskerXml,
            tertiaryActionEnabled = !taskerImportBusy,
            quaternaryActionLabel = stringResource(R.string.action_create_blank_profile),
            onQuaternaryAction = onCreateProfile,
            contentPadding = contentPadding,
        )
        return
    }

    var profileSearchQuery by rememberSaveable { mutableStateOf("") }
    var selectedGroup by rememberSaveable { mutableStateOf<String?>(null) }
    val groups = remember(profiles) {
        profiles.mapNotNull { it.group }.distinct().sorted()
    }
    var selectedStatus by rememberSaveable { mutableStateOf("all") }
    val filteredProfiles = remember(profiles, selectedGroup, profileSearchQuery, selectedStatus) {
        profiles
            .filter { selectedGroup == null || it.group == selectedGroup }
            .filter { profileSearchQuery.isBlank() || it.name.contains(profileSearchQuery, ignoreCase = true) }
            .filter {
                when (selectedStatus) {
                    "active" -> it.enabled && !it.requiresRiskAcknowledgement
                    "paused" -> !it.enabled || it.requiresRiskAcknowledgement
                    else -> true
                }
            }
    }
    val tasksById = remember(allTasks) { allTasks.associateBy(Task::id) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(
            start = DesignSystem.Screen.horizontalPadding,
            end = DesignSystem.Screen.horizontalPadding,
            top = 12.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (storageDecodeIssues.isNotEmpty()) {
            item {
                StorageDecodeWarningCard(storageDecodeIssues)
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = profileSearchQuery,
                    onValueChange = { profileSearchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search actions") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.variables_search_label),
                        )
                    },
                    trailingIcon = if (profileSearchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { profileSearchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.variables_search_clear))
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(DesignSystem.Radii.md),
                )
                WorkspaceActionsButton(
                    onBrowseTemplates = onBrowseTemplates,
                    onPreviewProfileShare = onPreviewProfileShare,
                    onExportOpenTaskerBundle = onExportOpenTaskerBundle,
                    onImportOpenTaskerBundle = onImportOpenTaskerBundle,
                    onImportOpenTaskerBundleText = onImportOpenTaskerBundleText,
                    onImportTaskerXml = onImportTaskerXml,
                    onExportTaskerXml = onExportTaskerXml,
                )
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedStatus == "all",
                        onClick = { selectedStatus = "all" },
                        label = { Text("All") },
                    )
                }
                item {
                    FilterChip(
                        selected = selectedStatus == "active",
                        onClick = { selectedStatus = "active" },
                        label = { Text("Enabled") },
                    )
                }
                item {
                    FilterChip(
                        selected = selectedStatus == "paused",
                        onClick = { selectedStatus = "paused" },
                        label = { Text("Paused") },
                    )
                }
                if (groups.isNotEmpty()) {
                    item {
                        Box(
                            Modifier
                                .padding(horizontal = 2.dp)
                                .width(1.dp)
                                .height(24.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                    items(groups, key = { it }) { group ->
                        FilterChip(
                            selected = selectedGroup == group,
                            onClick = { selectedGroup = if (selectedGroup == group) null else group },
                            label = { Text(group) },
                        )
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Actions",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "${filteredProfiles.size} of ${profiles.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (filteredProfiles.isEmpty()) {
            item {
                InlineNotice(
                    title = stringResource(R.string.workspace_no_matching_profiles),
                    body = stringResource(R.string.workspace_no_matching_profiles_body),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        items(filteredProfiles, key = { it.id }) { profile ->
            val actionCount = tasksById[profile.enterTaskId]?.actions?.size ?: 0
            ProfileCard(
                profile = profile,
                actionCount = actionCount,
                onEdit = { onEditProfile(profile) },
                onUndo = { onUndoProfileEdit(profile) },
                onRedo = { onRedoProfileEdit(profile) },
                canUndo = historyAvailability.canUndoProfile(profile.id),
                canRedo = historyAvailability.canRedoProfile(profile.id),
                onDelete = { onDeleteProfile(profile) },
                onDuplicate = { onDuplicateProfile(profile) },
                onPreflight = { onPreflightProfile(profile) },
                onRun = { onRunProfile(profile) },
                runBusy = runBusy,
            )
        }
    }
}

@Composable
private fun WorkspaceActionsButton(
    onBrowseTemplates: () -> Unit,
    onPreviewProfileShare: () -> Unit,
    onExportOpenTaskerBundle: () -> Unit,
    onImportOpenTaskerBundle: () -> Unit,
    onImportOpenTaskerBundleText: () -> Unit,
    onImportTaskerXml: () -> Unit,
    onExportTaskerXml: () -> Unit,
) {
    var actionsExpanded by rememberSaveable { mutableStateOf(false) }

    Box {
        IconButton(onClick = { actionsExpanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.workspace_actions_content_description))
        }
        DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }) {
            WorkspaceActionMenuItem(R.string.workspace_templates) { actionsExpanded = false; onBrowseTemplates() }
            WorkspaceActionMenuItem(R.string.import_tasker) { actionsExpanded = false; onImportTaskerXml() }
            WorkspaceActionMenuItem(R.string.import_export_json) { actionsExpanded = false; onExportOpenTaskerBundle() }
            WorkspaceActionMenuItem(R.string.import_import_json) { actionsExpanded = false; onImportOpenTaskerBundle() }
            WorkspaceActionMenuItem(R.string.import_export_tasker_xml) { actionsExpanded = false; onExportTaskerXml() }
            WorkspaceActionMenuItem(R.string.import_paste_json_action) { actionsExpanded = false; onImportOpenTaskerBundleText() }
            WorkspaceActionMenuItem(R.string.profile_share_preview_action) { actionsExpanded = false; onPreviewProfileShare() }
        }
    }
}

@Composable
private fun WorkspaceActionMenuItem(@androidx.annotation.StringRes labelRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(labelRes)) }, onClick = onClick)
}

@Composable
internal fun StorageDecodeWarningCard(issues: List<StorageDecodeIssue>) {
    val issueSummary = issues.take(3).joinToString(separator = "; ") { issue ->
        "${issue.recordType.label} \"${issue.recordName}\" #${issue.recordId}: ${issue.fieldName}"
    }
    val remaining = issues.size - 3
    val suffix = if (remaining > 0) "; ${stringResource(R.string.label_more_count, remaining)}" else ""
    InlineNotice(
        title = stringResource(R.string.workspace_stored_data_review),
        body = stringResource(R.string.workspace_stored_data_review_body, issueSummary, suffix),
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun TemplatePromptCard(onBrowseTemplates: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)),
        shape = RoundedCornerShape(DesignSystem.Radii.xxl),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignSystem.Spacing.md),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.workspace_templates), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.workspace_templates_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onBrowseTemplates) {
                Text(stringResource(R.string.action_browse))
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: Profile,
    actionCount: Int,
    onEdit: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onPreflight: () -> Unit,
    onRun: () -> Unit,
    runBusy: Boolean,
) {
    val widgetOwned = profile.widgetId != null
    val testEnabled = !runBusy && !profile.requiresRiskAcknowledgement
    val testDescription = stringResource(R.string.a11y_test_action, profile.name)
    val profileState = when {
        widgetOwned -> "Widget action"
        profile.requiresRiskAcknowledgement -> stringResource(R.string.imported_profile_review_required)
        profile.enabled -> stringResource(R.string.label_enabled)
        else -> stringResource(R.string.label_paused)
    }
    val statusColor = when {
        profile.requiresRiskAcknowledgement -> MaterialTheme.colorScheme.error
        profile.enabled -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(48.dp)
                    .background(statusColor, RoundedCornerShape(3.dp)),
            )
            Text(
                "#${profile.id.toString().takeLast(3).padStart(3, '0')}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(11.dp),
                modifier = Modifier.size(44.dp),
            ) {
                IconButton(
                    onClick = onRun,
                    enabled = testEnabled,
                    modifier = Modifier.fillMaxSize().semantics { this.contentDescription = testDescription },
                ) {
                    WidgetIconImage(
                        iconKey = profile.icon,
                        contentDescription = null,
                        tint = if (profile.enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(23.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$actionCount steps  /  ${profile.contexts.size} triggers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusPill(profileState, statusColor)
            }
            IconButton(
                onClick = onRun,
                enabled = testEnabled,
                modifier = Modifier.semantics { this.contentDescription = testDescription },
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = if (testEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!widgetOwned) {
                ProfileActionsMenu(
                    contentDescription = stringResource(R.string.a11y_duplicate_profile, profile.name),
                    editDescription = stringResource(R.string.a11y_edit_profile, profile.name),
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onEdit = onEdit,
                    onPreflight = onPreflight,
                    onTest = onRun,
                    testEnabled = testEnabled,
                    onDuplicate = onDuplicate,
                    onUndo = onUndo,
                    onRedo = onRedo,
                    onDelete = onDelete,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
    }
}
@Composable
internal fun TasksScreen(
    tasks: List<Task>,
    storageDecodeIssues: List<StorageDecodeIssue>,
    onCreateTask: () -> Unit,
    onEditTask: (Task) -> Unit,
    onUndoTaskEdit: (Task) -> Unit = {},
    onRedoTaskEdit: (Task) -> Unit = {},
    onDeleteTask: (Task) -> Unit,
    onDuplicateTask: (Task) -> Unit = {},
    onRunTask: (Task) -> Unit,
    onPreflightTask: (Task) -> Unit,
    onPinTask: (Task) -> Unit,
    onAddAction: (Task) -> Unit,
    onEditAction: (Task, Int, ActionSpec) -> Unit,
    onDeleteAction: (Task, Int) -> Unit,
    onMoveAction: (Task, Int, Int) -> Unit,
    onRunAction: (Task, Int) -> Unit,
    contentPadding: PaddingValues,
    runBusy: Boolean = false,
    contentLoaded: Boolean = true,
    historyAvailability: EditHistoryAvailabilityState = EditHistoryAvailabilityState(),
) {
    if (!contentLoaded) {
        ContentLoadingState(contentPadding)
        return
    }
    if (tasks.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.empty_tasks_title),
            body = stringResource(R.string.empty_tasks_create_body),
            actionLabel = stringResource(R.string.action_create_task),
            onAction = onCreateTask,
            contentPadding = contentPadding,
        )
        return
    }
    var taskSearchQuery by rememberSaveable { mutableStateOf("") }
    val filteredTasks = remember(tasks, taskSearchQuery) {
        if (taskSearchQuery.isBlank()) tasks
        else tasks.filter { it.name.contains(taskSearchQuery, ignoreCase = true) }
    }
    val initiallyExpandedTaskId = filteredTasks.firstOrNull()?.id

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        // Extra bottom inset so the extended FAB never sits on top of the last row; Scaffold's
        // innerPadding does not reserve floating-action-button height.
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(DesignSystem.Screen.cardGap),
    ) {
        if (storageDecodeIssues.isNotEmpty()) {
            item {
                StorageDecodeWarningCard(storageDecodeIssues)
            }
        }
        item {
            OutlinedTextField(
                value = taskSearchQuery,
                onValueChange = { taskSearchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.workspace_search_tasks)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.run_log_search_label)) },
                trailingIcon = if (taskSearchQuery.isNotEmpty()) {
                    { IconButton(onClick = { taskSearchQuery = "" }) { Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.variables_search_clear)) } }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(DesignSystem.Radii.lg),
            )
        }
        if (filteredTasks.isEmpty()) {
            item {
                InlineNotice(
                    title = stringResource(R.string.workspace_no_matching_tasks),
                    body = stringResource(R.string.workspace_no_matching_tasks_body),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        items(filteredTasks, key = { it.id }) { task ->
            TaskCard(
                task = task,
                initiallyExpanded = initiallyExpandedTaskId == task.id,
                onEdit = { onEditTask(task) },
                onUndo = { onUndoTaskEdit(task) },
                onRedo = { onRedoTaskEdit(task) },
                canUndo = historyAvailability.canUndoTask(task.id),
                canRedo = historyAvailability.canRedoTask(task.id),
                onDelete = { onDeleteTask(task) },
                onDuplicate = { onDuplicateTask(task) },
                onRun = { onRunTask(task) },
                onPreflight = { onPreflightTask(task) },
                onPin = { onPinTask(task) },
                onAddAction = { onAddAction(task) },
                onEditAction = { index, action -> onEditAction(task, index, action) },
                onDeleteAction = { index -> onDeleteAction(task, index) },
                onMoveAction = { fromIndex, toIndex -> onMoveAction(task, fromIndex, toIndex) },
                onRunAction = { index -> onRunAction(task, index) },
                runBusy = runBusy,
            )
        }
    }
}

@Composable
private fun TaskCard(
    task: Task,
    initiallyExpanded: Boolean,
    onEdit: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onRun: () -> Unit,
    onPreflight: () -> Unit,
    onPin: () -> Unit,
    onAddAction: () -> Unit,
    onEditAction: (Int, ActionSpec) -> Unit,
    onDeleteAction: (Int) -> Unit,
    onMoveAction: (Int, Int) -> Unit,
    onRunAction: (Int) -> Unit,
    runBusy: Boolean,
) {
    var expanded by rememberSaveable(task.id) { mutableStateOf(initiallyExpanded) }
    val runDescription = stringResource(R.string.a11y_run_task, task.name)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
        shape = RoundedCornerShape(DesignSystem.Radii.lg),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(DesignSystem.Spacing.sm)) {
            Row(
                modifier = Modifier.expandCollapseToggle(expanded) { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignSystem.Spacing.sm),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(DesignSystem.Radii.md),
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = runDescription,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(20.dp)
                            .clearAndSetSemantics { },
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(task.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.label_action_count, task.actions.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = onRun,
                    modifier = Modifier.semantics { contentDescription = runDescription },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(DesignSystem.Radii.sm),
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = runDescription,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(stringResource(R.string.action_run), style = MaterialTheme.typography.labelLarge)
                }
                TaskActionsMenu(
                    contentDescription = stringResource(R.string.a11y_duplicate_task, task.name),
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onEdit = onEdit,
                    onAddAction = onAddAction,
                    onPreflight = onPreflight,
                    onPin = onPin,
                    onDuplicate = onDuplicate,
                    onUndo = onUndo,
                    onRedo = onRedo,
                    onDelete = onDelete,
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = listOf(
                    stringResource(R.string.label_priority_short, task.priority),
                    collisionModeTitle(task.collisionMode),
                ).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (expanded && task.actions.isEmpty()) {
                InlineNotice(
                    title = stringResource(R.string.workspace_task_has_no_actions),
                    body = stringResource(R.string.workspace_task_has_no_actions_body),
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (expanded) {
                task.actions.forEachIndexed { index, action ->
                    ActionRow(
                        index = index,
                        action = action,
                        canMoveUp = index > 0,
                        canMoveDown = index < task.actions.lastIndex,
                        onMoveUp = { onMoveAction(index, index - 1) },
                        onMoveDown = { onMoveAction(index, index + 1) },
                        onEdit = { onEditAction(index, action) },
                        onDelete = { onDeleteAction(index) },
                        onRun = { onRunAction(index) },
                        runBusy = runBusy,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileActionsMenu(
    contentDescription: String,
    editDescription: String,
    canUndo: Boolean,
    canRedo: Boolean,
    onEdit: () -> Unit,
    onPreflight: () -> Unit,
    onTest: () -> Unit,
    testEnabled: Boolean,
    onDuplicate: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDelete: () -> Unit,
) {
    ContextualActionsMenu(
        contentDescription = contentDescription,
        actions = listOf(
            ContextualMenuAction(R.string.action_edit, accessibilityLabel = editDescription, onClick = onEdit),
            ContextualMenuAction(R.string.action_run_alone, enabled = testEnabled, onClick = onTest),
            ContextualMenuAction(R.string.action_preflight, onClick = onPreflight),
            ContextualMenuAction(R.string.action_duplicate, onClick = onDuplicate),
            ContextualMenuAction(R.string.action_undo, enabled = canUndo, onClick = onUndo),
            ContextualMenuAction(R.string.action_redo, enabled = canRedo, onClick = onRedo),
            ContextualMenuAction(R.string.profile_delete, onClick = onDelete),
        ),
    )
}

@Composable
private fun TaskActionsMenu(
    contentDescription: String,
    canUndo: Boolean,
    canRedo: Boolean,
    onEdit: () -> Unit,
    onAddAction: () -> Unit,
    onPreflight: () -> Unit,
    onPin: () -> Unit,
    onDuplicate: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDelete: () -> Unit,
) {
    ContextualActionsMenu(
        contentDescription = contentDescription,
        actions = listOf(
            ContextualMenuAction(R.string.action_edit, onClick = onEdit),
            ContextualMenuAction(R.string.task_add_action, onClick = onAddAction),
            ContextualMenuAction(R.string.action_preflight, onClick = onPreflight),
            ContextualMenuAction(R.string.action_pin, onClick = onPin),
            ContextualMenuAction(R.string.action_duplicate, onClick = onDuplicate),
            ContextualMenuAction(R.string.action_undo, enabled = canUndo, onClick = onUndo),
            ContextualMenuAction(R.string.action_redo, enabled = canRedo, onClick = onRedo),
            ContextualMenuAction(R.string.task_delete, onClick = onDelete),
        ),
    )
}

private data class ContextualMenuAction(
    @StringRes val labelRes: Int,
    val enabled: Boolean = true,
    val accessibilityLabel: String? = null,
    val onClick: () -> Unit,
)

@Composable
private fun ContextualActionsMenu(
    contentDescription: String,
    actions: List<ContextualMenuAction>,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { this.contentDescription = contentDescription },
        ) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = contentDescription,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(stringResource(action.labelRes)) },
                    enabled = action.enabled,
                    modifier = Modifier.semantics {
                        action.accessibilityLabel?.let { this.contentDescription = it }
                    },
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                )
            }
        }
    }
}

@Composable
internal fun DuplicateMenu(
    contentDescription: String,
    onDuplicate: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { this.contentDescription = contentDescription },
        ) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = contentDescription,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_duplicate)) },
                onClick = {
                    expanded = false
                    onDuplicate()
                },
            )
        }
    }
}

@Composable
internal fun ActionRow(
    index: Int,
    action: ActionSpec,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRun: () -> Unit,
    runBusy: Boolean,
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    val resources = LocalContext.current.resources
    val metadata = ActionMetadataRegistry.get(action.type)
    val capability = ActionCapabilityRegistry.get(action.type)
    val metadataName = metadata?.let { stringResource(it.nameRes) }
    val metadataDescription = metadata?.let { stringResource(it.descriptionRes) }
    val actionLabel = action.label ?: metadataName ?: stringResource(R.string.action_unknown_name)
    val editDescription = stringResource(R.string.a11y_edit_action, index + 1, actionLabel)
    val deleteDescription = stringResource(R.string.a11y_delete_action, index + 1, actionLabel)
    val runDescription = stringResource(R.string.a11y_run_action, index + 1, actionLabel)
    val moveUpDescription = stringResource(R.string.a11y_move_action_up, index + 1, actionLabel)
    val moveDownDescription = stringResource(R.string.a11y_move_action_down, index + 1, actionLabel)

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 11.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(9.dp),
                modifier = Modifier.size(34.dp),
            ) {
                if (SingleActionRun.isRunnableAlone(action)) {
                    IconButton(
                        onClick = onRun,
                        enabled = !runBusy,
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics { contentDescription = runDescription },
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = runDescription,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = (index + 1).toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    actionLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    ActionSummaryFormatter.format(resources, action.type, action.args)
                        .ifBlank { metadataDescription ?: stringResource(R.string.workspace_no_arguments) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (capability.level != CapabilityLevel.Supported) {
                    Spacer(Modifier.height(3.dp))
                    StatusPill(
                        if (capability.level == CapabilityLevel.Unsupported) {
                            stringResource(R.string.label_unsupported)
                        } else {
                            stringResource(R.string.status_needs_setup)
                        },
                        if (capability.level == CapabilityLevel.Unsupported) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.secondary
                        },
                    )
                }
                action.condition?.takeIf(String::isNotBlank)?.let { condition ->
                    Text(
                        stringResource(R.string.action_condition_summary, condition),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (action.continueOnError) {
                    Text(
                        stringResource(R.string.action_continue_on_error_summary),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(
                onClick = onEdit,
                modifier = Modifier.semantics { contentDescription = editDescription },
            ) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = editDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.nav_more),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.projects_move_up)) },
                        onClick = { menuExpanded = false; onMoveUp() },
                        enabled = canMoveUp,
                        modifier = Modifier.semantics { contentDescription = moveUpDescription },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.projects_move_down)) },
                        onClick = { menuExpanded = false; onMoveDown() },
                        enabled = canMoveDown,
                        modifier = Modifier.semantics { contentDescription = moveDownDescription },
                    )
                    if (SingleActionRun.isRunnableAlone(action)) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (runBusy) R.string.action_run_alone_busy else R.string.action_run_alone,
                                    ),
                                )
                            },
                            onClick = { menuExpanded = false; onRun() },
                            enabled = !runBusy,
                            leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = runDescription) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        onClick = { menuExpanded = false; onDelete() },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = deleteDescription,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f))
    }
}

@Composable
internal fun ContextRow(
    index: Int,
    context: ContextSpec,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val contextTypeLabel = stringResource(contextTitleRes(context.type))
    val editDescription = stringResource(R.string.a11y_edit_context, index + 1, contextTypeLabel)
    val deleteDescription = stringResource(R.string.a11y_delete_context, index + 1, contextTypeLabel)
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                (index + 1).toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(20.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    contextTypeLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    contextConfigSummary(context),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (context.invert) {
                    StatusPill(stringResource(R.string.label_inverted), MaterialTheme.colorScheme.secondary)
                }
            }
            IconButton(
                onClick = onEdit,
                modifier = Modifier.semantics { contentDescription = editDescription },
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = editDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.semantics { contentDescription = deleteDescription },
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = deleteDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f))
    }
}

