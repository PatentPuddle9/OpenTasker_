package com.opentasker.ui.screens

import com.opentasker.ui.icons.Icons
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

internal fun OpenTaskerScreen.icon(): ImageVector = when (this) {
    OpenTaskerScreen.Home -> Icons.Outlined.Tune
    OpenTaskerScreen.Actions -> Icons.Outlined.Tune
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

internal fun shouldNavigateBackToActions(screen: OpenTaskerScreen): Boolean =
    screen != OpenTaskerScreen.Home

/**
 * BETA's new workspace chrome: the app identity and page context live in one compact masthead;
 * navigation is a horizontal workspace strip, never a bottom dock or a side rail.
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
                .padding(start = 18.dp, end = 14.dp, top = 9.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(6.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    "OPEN TASKER",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.7.sp,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(screen.labelRes),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 23.sp,
                            lineHeight = 27.sp,
                            letterSpacing = (-0.7).sp,
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
                        modifier = Modifier.size(23.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 8.dp, end = 8.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            primaryNavigationScreens.forEach { destination ->
                WorkspaceTab(
                    label = stringResource(destination.labelRes),
                    selected = screen == destination,
                    onClick = { onNavigate(destination) },
                )
            }
            Box {
                WorkspaceTab(
                    label = "More",
                    selected = screen in secondaryNavigationScreens || showMoreDestinations,
                    onClick = onToggleMoreDestinations,
                )
                DropdownMenu(
                    expanded = showMoreDestinations,
                    onDismissRequest = onDismissMoreDestinations,
                ) {
                    secondaryNavigationScreens.forEach { destination ->
                        DropdownMenuItem(
                            text = { Text(stringResource(destination.labelRes)) },
                            leadingIcon = { Icon(destination.icon(), contentDescription = null) },
                            onClick = { onNavigate(destination) },
                        )
                    }
                }
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
            thickness = 1.dp,
        )
    }
}

@Composable
private fun WorkspaceTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .widthIn(min = 68.dp)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                letterSpacing = 0.7.sp,
            ),
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .width(if (selected) 28.dp else 8.dp)
                .height(3.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(2.dp),
                ),
        )
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
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
