package com.opentasker.ui.screens

import com.opentasker.ui.icons.Icons
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opentasker.core.model.Profile
import com.opentasker.core.model.Task
import com.opentasker.ui.theme.DesignSystem
import com.opentasker.ui.icons.WidgetIconImage

@Composable
internal fun BetaOverviewScreen(
    profiles: List<Profile>,
    tasks: List<Task>,
    runCount: Int,
    runBusy: Boolean,
    contentLoaded: Boolean,
    contentPadding: PaddingValues,
    onCreateAction: () -> Unit,
    onRunAction: (Profile) -> Unit,
    onEditAction: (Profile) -> Unit,
    onNavigate: (OpenTaskerScreen) -> Unit,
) {
    if (!contentLoaded) {
        ContentLoadingState(contentPadding)
        return
    }

    val tasksById = remember(tasks) { tasks.associateBy(Task::id) }
    val enabledProfiles = remember(profiles) {
        profiles.filter { it.enabled && !it.requiresRiskAcknowledgement }
    }
    val totalSteps = remember(profiles, tasksById) {
        profiles.sumOf { profile -> tasksById[profile.enterTaskId]?.actions?.size ?: 0 }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(start = DesignSystem.Screen.horizontalPadding, top = 22.dp, end = DesignSystem.Screen.horizontalPadding, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(2.dp)),
                    )
                    Text(
                        "PERSONAL AUTOMATION SYSTEM",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "Make the little things\nautomatic.",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 36.sp,
                        lineHeight = 39.sp,
                        letterSpacing = (-1.3).sp,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "A live view of what your phone can do for you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onCreateAction,
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("New automation")
                    }
                    TextButton(onClick = { onNavigate(OpenTaskerScreen.Actions) }) {
                        Text("Explore actions")
                    }
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(6.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 19.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "ENABLED AUTOMATIONS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                        )
                        Text(
                            enabledProfiles.size.toString().padStart(2, '0'),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = 48.sp,
                                lineHeight = 52.sp,
                                letterSpacing = (-1.5).sp,
                                fontWeight = FontWeight.ExtraBold,
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            "of ${profiles.size} saved actions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                        )
                    }
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(62.dp)
                            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.22f)),
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "ACTION STEPS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                        )
                        Text(
                            totalSteps.toString().padStart(2, '0'),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = 48.sp,
                                lineHeight = 52.sp,
                                letterSpacing = (-1.5).sp,
                                fontWeight = FontWeight.ExtraBold,
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            "$runCount recorded runs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "THE ACTION BOARD",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text("Your automations", style = MaterialTheme.typography.titleLarge)
                    }
                    TextButton(onClick = { onNavigate(OpenTaskerScreen.Actions) }) {
                        Text("VIEW ALL")
                    }
                }
                if (profiles.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Nothing automated yet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Create your first action and give it a trigger.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    profiles.take(5).forEachIndexed { index, profile ->
                        OverviewActionRow(
                            index = index + 1,
                            profile = profile,
                            task = tasksById[profile.enterTaskId],
                            runBusy = runBusy,
                            onEdit = { onEditAction(profile) },
                            onRun = { onRunAction(profile) },
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "WORKSPACE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text("Tools & building blocks", style = MaterialTheme.typography.titleLarge)
                    }
                    TextButton(onClick = { onNavigate(OpenTaskerScreen.Marketplace) }) {
                        Text("MARKETPLACE")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OverviewToolLink(
                        modifier = Modifier.weight(1f),
                        title = "Flow",
                        detail = "Map automations",
                        icon = Icons.Outlined.AccountTree,
                        onClick = { onNavigate(OpenTaskerScreen.Flow) },
                    )
                    OverviewToolLink(
                        modifier = Modifier.weight(1f),
                        title = "Variables",
                        detail = "Store values",
                        icon = Icons.Outlined.Key,
                        onClick = { onNavigate(OpenTaskerScreen.Vars) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OverviewToolLink(
                        modifier = Modifier.weight(1f),
                        title = "Scenes",
                        detail = "Build interfaces",
                        icon = Icons.Outlined.Widgets,
                        onClick = { onNavigate(OpenTaskerScreen.Scenes) },
                    )
                    OverviewToolLink(
                        modifier = Modifier.weight(1f),
                        title = "Widgets",
                        detail = "Home screen",
                        icon = Icons.Outlined.Widgets,
                        onClick = { onNavigate(OpenTaskerScreen.Shortcuts) },
                    )
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onNavigate(OpenTaskerScreen.RunLog) },
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(6.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Outlined.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Run history", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "$runCount executions recorded",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewActionRow(
    index: Int,
    profile: Profile,
    task: Task?,
    runBusy: Boolean,
    onEdit: () -> Unit,
    onRun: () -> Unit,
) {
    val statusColor = when {
        profile.requiresRiskAcknowledgement -> MaterialTheme.colorScheme.error
        profile.enabled -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(5.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Text(
                index.toString().padStart(2, '0'),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
            Surface(
                modifier = Modifier.size(40.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(4.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    WidgetIconImage(
                        iconKey = profile.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(23.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    profile.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${task?.actions?.size ?: 0} steps  /  ${profile.contexts.size} triggers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                StatusPill(
                    label = when {
                        profile.requiresRiskAcknowledgement -> "Review required"
                        profile.enabled -> "Enabled"
                        else -> "Paused"
                    },
                    color = statusColor,
                )
            }
            IconButton(
                onClick = onRun,
                enabled = !runBusy && !profile.requiresRiskAcknowledgement,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Test ${profile.name}",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun OverviewToolLink(
    modifier: Modifier,
    title: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(5.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
