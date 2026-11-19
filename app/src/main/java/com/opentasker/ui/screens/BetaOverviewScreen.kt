package com.opentasker.ui.screens

import com.opentasker.ui.icons.Icons
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
import androidx.compose.material3.HorizontalDivider
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
    val readiness = if (profiles.isEmpty()) 0f else enabledProfiles.size.toFloat() / profiles.size

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(25.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier.size(8.dp).background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(2.dp)),
                    )
                    Text(
                        "CONTROL ROOM  /  LIVE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        ),
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Text(
                    "Everything,\nin motion.",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 42.sp,
                        lineHeight = 43.sp,
                        letterSpacing = (-1.7).sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Your automations, tools and runtime activity in one place.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(15.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                "AUTOMATION STATUS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    enabledProfiles.size.toString().padStart(2, '0'),
                                    style = MaterialTheme.typography.displayMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-2).sp,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    "running / enabled",
                                    modifier = Modifier.padding(bottom = 8.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                profiles.size.toString().padStart(2, '0'),
                                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text("TOTAL ACTIONS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Box(
                        Modifier.fillMaxWidth().height(5.dp).background(
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(8.dp),
                        ),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(readiness.coerceIn(0f, 1f))
                                .height(5.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "${profiles.size - enabledProfiles.size} paused  /  $totalSteps action steps",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "$runCount runs",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    Button(
                        onClick = onCreateAction,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Build an automation", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                OverviewSectionHeading(
                    eyebrow = "OPEN A WORKSPACE",
                    title = "Tools",
                    trailing = "06 MODULES",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OverviewToolLink(
                        modifier = Modifier.weight(1f),
                        title = "Flow",
                        detail = "Visual map",
                        icon = Icons.Outlined.AccountTree,
                        onClick = { onNavigate(OpenTaskerScreen.Flow) },
                    )
                    OverviewToolLink(
                        modifier = Modifier.weight(1f),
                        title = "Variables",
                        detail = "Shared values",
                        icon = Icons.Outlined.Key,
                        onClick = { onNavigate(OpenTaskerScreen.Vars) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OverviewToolLink(
                        modifier = Modifier.weight(1f),
                        title = "Scenes",
                        detail = "Interface canvas",
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OverviewToolLink(
                        modifier = Modifier.weight(1f),
                        title = "Marketplace",
                        detail = "Packages",
                        icon = Icons.Outlined.ShoppingBag,
                        onClick = { onNavigate(OpenTaskerScreen.Marketplace) },
                    )
                    OverviewToolLink(
                        modifier = Modifier.weight(1f),
                        title = "Inspector",
                        detail = "Live context",
                        icon = Icons.Outlined.Sensors,
                        onClick = { onNavigate(OpenTaskerScreen.Inspector) },
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OverviewSectionHeading(
                    eyebrow = "AUTOMATION REGISTER",
                    title = "Your actions",
                    trailing = "${profiles.size.toString().padStart(2, '0')} SAVED",
                    onTrailingClick = { onNavigate(OpenTaskerScreen.Actions) },
                )
                if (profiles.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text("No automations yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Build one above, then give it a trigger.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onNavigate(OpenTaskerScreen.RunLog) },
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Outlined.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(25.dp),
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Execution history", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            "$runCount recorded runs  /  inspect the runtime",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewSectionHeading(
    eyebrow: String,
    title: String,
    trailing: String,
    onTrailingClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                eyebrow,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold))
        }
        if (onTrailingClick != null) {
            TextButton(onClick = onTrailingClick, contentPadding = PaddingValues(0.dp)) {
                Text(trailing, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace))
            }
        } else {
            Text(
                trailing,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Text(
                index.toString().padStart(2, '0'),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                WidgetIconImage(
                    iconKey = profile.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(23.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(profile.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                Icon(Icons.Filled.PlayArrow, contentDescription = "Test ${profile.name}", tint = MaterialTheme.colorScheme.primary)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
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
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
