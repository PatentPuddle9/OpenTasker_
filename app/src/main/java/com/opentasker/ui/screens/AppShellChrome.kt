package com.opentasker.ui.screens

import com.opentasker.ui.icons.Icons
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.opentasker.app.R

internal enum class OpenTaskerScreen(@StringRes val labelRes: Int) {
    Home(R.string.nav_home),
    Actions(R.string.nav_actions),
    Shortcuts(R.string.nav_shortcuts),
    Vars(R.string.nav_variables),
    Flow(R.string.nav_flow),
    Scenes(R.string.nav_scenes),
    Inspector(R.string.nav_inspector),
    Setup(R.string.nav_setup),
    RunLog(R.string.nav_run_log),
    Diagnostics(R.string.nav_diagnostics),
    Settings(R.string.nav_settings),
    Marketplace(R.string.nav_marketplace),
}

internal val primaryNavigationScreens = listOf(
    OpenTaskerScreen.Home,
    OpenTaskerScreen.Actions,
    OpenTaskerScreen.Shortcuts,
    OpenTaskerScreen.RunLog,
)

internal val secondaryNavigationScreens =
    OpenTaskerScreen.entries.filterNot { it in primaryNavigationScreens }

internal fun OpenTaskerScreen.icon(): androidx.compose.ui.graphics.vector.ImageVector = when (this) {
    OpenTaskerScreen.Home -> Icons.Outlined.Tune
    OpenTaskerScreen.Actions -> Icons.AutoMirrored.Outlined.PlaylistPlay
    OpenTaskerScreen.Shortcuts -> Icons.Outlined.Widgets
    OpenTaskerScreen.Vars -> Icons.Outlined.Key
    OpenTaskerScreen.Flow -> Icons.Outlined.AccountTree
    OpenTaskerScreen.Scenes -> Icons.Outlined.Widgets
    OpenTaskerScreen.Inspector -> Icons.Outlined.Sensors
    OpenTaskerScreen.Setup -> Icons.Outlined.Settings
    OpenTaskerScreen.RunLog -> Icons.Outlined.History
    OpenTaskerScreen.Diagnostics -> Icons.Outlined.MonitorHeart
    OpenTaskerScreen.Settings -> Icons.Outlined.Settings
    OpenTaskerScreen.Marketplace -> Icons.Outlined.ShoppingBag
}

/** Back from a tool returns to the control room rather than leaving the app. */
internal fun shouldNavigateBackToActions(screen: OpenTaskerScreen): Boolean =
    screen != OpenTaskerScreen.Home

/**
 * Beta's navigation is an app switcher, not a row of tabs.
 * The work surface gets the full width; every destination is one tap away from the
 * same launcher sheet, with no persistent navigation strip competing with the content.
 */
@Composable
internal fun BetaWorkspaceChrome(
    screen: OpenTaskerScreen,
    detail: String?,
    onOpenSearch: () -> Unit,
    showSearch: Boolean,
    showMoreDestinations: Boolean,
    onToggleMoreDestinations: () -> Unit,
    onDismissMoreDestinations: () -> Unit,
    onNavigate: (OpenTaskerScreen) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 10.dp, end = 12.dp, top = 7.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconButton(
                onClick = onToggleMoreDestinations,
                modifier = Modifier.size(48.dp),
            ) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = "Open workspace switcher",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    "OPEN TASKER  /  BETA",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.45.sp,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(screen.labelRes),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 22.sp,
                            lineHeight = 26.sp,
                            letterSpacing = (-0.5).sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!detail.isNullOrBlank()) {
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
            if (showSearch) {
                IconButton(onClick = onOpenSearch, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = stringResource(R.string.global_search_content_description),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f),
            thickness = 1.dp,
        )
    }

    if (showMoreDestinations) {
        ModalBottomSheet(
            onDismissRequest = onDismissMoreDestinations,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "WORKSPACE SWITCHER",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.35.sp,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text("Where do you want to work?", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Open a tool or move to another part of OpenTasker.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OpenTaskerScreen.entries.chunked(2).forEach { destinations ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        destinations.forEach { destination ->
                            WorkspaceDestinationTile(
                                destination = destination,
                                selected = destination == screen,
                                modifier = Modifier.weight(1f),
                                onClick = { onNavigate(destination) },
                            )
                        }
                        if (destinations.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceDestinationTile(
    destination: OpenTaskerScreen,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceContainerLow
    val foreground = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
        color = background,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(destination.icon(), contentDescription = null, tint = if (selected) foreground else MaterialTheme.colorScheme.primary)
            Text(
                stringResource(destination.labelRes),
                style = MaterialTheme.typography.titleSmall,
                color = foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun SummaryMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 2.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun StatusPill(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(color, RoundedCornerShape(50)))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
