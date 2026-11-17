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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opentasker.app.R
import com.opentasker.ui.theme.DesignSystem

internal enum class OpenTaskerScreen(@StringRes val labelRes: Int) {
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
    OpenTaskerScreen.Actions,
    OpenTaskerScreen.Shortcuts,
    OpenTaskerScreen.RunLog,
    OpenTaskerScreen.Setup,
)

internal val secondaryNavigationScreens = OpenTaskerScreen.entries.filterNot { it in primaryNavigationScreens }
internal val adaptiveNavigationScreens = OpenTaskerScreen.entries

internal fun OpenTaskerScreen.icon(): ImageVector = when (this) {
    OpenTaskerScreen.Actions -> Icons.Outlined.Tune
    OpenTaskerScreen.Shortcuts -> Icons.Outlined.Widgets
    OpenTaskerScreen.Vars -> Icons.Outlined.Key
    OpenTaskerScreen.Flow -> Icons.Outlined.AccountTree
    OpenTaskerScreen.Scenes -> Icons.Outlined.Widgets
    OpenTaskerScreen.Inspector -> Icons.Outlined.Sensors
    OpenTaskerScreen.Setup -> Icons.Outlined.Settings
    OpenTaskerScreen.RunLog -> Icons.Outlined.History
    OpenTaskerScreen.Diagnostics -> Icons.Outlined.MonitorHeart
    OpenTaskerScreen.Settings -> Icons.Outlined.Tune
    OpenTaskerScreen.Marketplace -> Icons.Outlined.ShoppingBag
}

internal fun shouldNavigateBackToActions(screen: OpenTaskerScreen): Boolean =
    screen != OpenTaskerScreen.Actions

/**
 * Compact destination header. The old oversized title stack took up nearly a fifth of a phone
 * screen before the user could reach their content; this keeps a clear title without wasting it.
 */
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
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                     .padding(
                        start = DesignSystem.Screen.horizontalPadding,
                        end = DesignSystem.Screen.horizontalPadding,
                        top = 10.dp,
                        bottom = 12.dp,
                    ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(13.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Tune,
                            contentDescription = stringResource(R.string.app_name),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.15.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Text(
                        text = stringResource(screen.labelRes),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 23.sp,
                            lineHeight = 27.sp,
                            letterSpacing = (-0.45).sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!detail.isNullOrBlank()) {
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (showSearch) {
                    Surface(
                        shape = RoundedCornerShape(13.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        IconButton(
                            onClick = onOpenSearch,
                            modifier = Modifier.size(42.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = stringResource(R.string.global_search_content_description),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)),
            )
        }
    }
}

/** A consistent destination control for both the compact bottom bar and expanded rail. */
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
            .heightIn(min = 62.dp)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                stateDescription = if (selected) selectedDescription else notSelectedDescription
            }
            .padding(horizontal = 4.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 32.dp)
                .background(
                    color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent,
                    shape = RoundedCornerShape(11.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun SummaryMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 2.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp, lineHeight = 24.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.45.sp),
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
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(9.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(color, CircleShape),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
