package com.opentasker.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Widgets
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opentasker.app.R
import com.opentasker.ui.theme.DesignSystem

/**
 * App-shell chrome: the destination list, its icons, the top header and the small status
 * helpers every screen reuses. Split out of ActiveAutomationUi.kt so the shell composable is
 * not sharing a file with the navigation vocabulary it renders.
 */

internal enum class OpenTaskerScreen(@StringRes val labelRes: Int) {
    Profiles(R.string.nav_profiles),
    Tasks(R.string.nav_tasks),
    Vars(R.string.nav_variables),
    Flow(R.string.nav_flow),
    Scenes(R.string.nav_scenes),
    Inspector(R.string.nav_inspector),
    Setup(R.string.nav_setup),
    RunLog(R.string.nav_run_log),
    Diagnostics(R.string.nav_diagnostics),
    Settings(R.string.nav_settings),
}
internal val primaryNavigationScreens = listOf(
    OpenTaskerScreen.Profiles,
    OpenTaskerScreen.Tasks,
    OpenTaskerScreen.RunLog,
    OpenTaskerScreen.Setup,
)

internal val secondaryNavigationScreens = OpenTaskerScreen.entries.filterNot { it in primaryNavigationScreens }
internal val adaptiveNavigationScreens = OpenTaskerScreen.entries

internal fun OpenTaskerScreen.icon(): ImageVector = when (this) {
    OpenTaskerScreen.Profiles -> Icons.Outlined.Tune
    OpenTaskerScreen.Tasks -> Icons.AutoMirrored.Outlined.PlaylistPlay
    OpenTaskerScreen.Vars -> Icons.Outlined.Key
    OpenTaskerScreen.Flow -> Icons.Outlined.AccountTree
    OpenTaskerScreen.Scenes -> Icons.Outlined.Widgets
    OpenTaskerScreen.Inspector -> Icons.Outlined.Sensors
    OpenTaskerScreen.Setup -> Icons.Outlined.Settings
    OpenTaskerScreen.RunLog -> Icons.Outlined.History
    OpenTaskerScreen.Diagnostics -> Icons.Outlined.MonitorHeart
    OpenTaskerScreen.Settings -> Icons.Outlined.Tune
}

internal fun shouldNavigateBackToProfiles(screen: OpenTaskerScreen): Boolean =
    screen != OpenTaskerScreen.Profiles

@Composable
internal fun OpenTaskerHeader(
    screen: OpenTaskerScreen,
    detail: String?,
    onOpenSearch: () -> Unit,
    showSearch: Boolean = true,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = DesignSystem.Screen.horizontalPadding,
                        end = DesignSystem.Screen.horizontalPadding,
                        top = 10.dp,
                        bottom = 10.dp,
                    ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(screen.labelRes),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 27.sp,
                            lineHeight = 32.sp,
                            letterSpacing = (-0.45).sp,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!detail.isNullOrBlank()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .background(
                                        color = when (screen) {
                                            OpenTaskerScreen.Diagnostics -> MaterialTheme.colorScheme.tertiary
                                            OpenTaskerScreen.RunLog, OpenTaskerScreen.Inspector -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.primary
                                        },
                                        shape = CircleShape,
                                    ),
                            )
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (showSearch) {
                    IconButton(
                        onClick = onOpenSearch,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = stringResource(R.string.global_search_content_description),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)),
            )
        }
    }
}

@Composable
internal fun OpenTaskerNavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val selectedDescription = stringResource(R.string.a11y_selected)
    val notSelectedDescription = stringResource(R.string.a11y_not_selected)
    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                stateDescription = if (selected) selectedDescription else notSelectedDescription
            }
            .padding(horizontal = 3.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun SummaryMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun StatusPill(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(color, RoundedCornerShape(percent = 50)),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
