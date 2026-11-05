package com.opentasker.ui.screens

import com.opentasker.ui.icons.Icons
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.opentasker.ui.icons.WidgetIconImage
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import com.opentasker.widget.TaskWidgetConfigActivity
import com.opentasker.widget.TaskWidgetProvider
import com.opentasker.widget.WidgetIconCatalog

private data class ShortcutWidgetRow(
    val widgetId: Int,
    val actionName: String,
    val icon: String,
)

/** A list of pinned home-screen Action widgets, with direct access to their configuration editor. */
@Composable
internal fun ShortcutsScreen(
    contentPadding: PaddingValues,
    onCreateNew: () -> Unit,
    onDeleteWidget: (Int, (Boolean) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshKey by remember { mutableIntStateOf(0) }
    var shortcuts by remember { mutableStateOf(loadShortcutWidgets(context)) }
    var pendingDelete by remember { mutableStateOf<ShortcutWidgetRow?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshKey += 1
                shortcuts = loadShortcutWidgets(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Android can deliver the widget-pinned callback just after the app resumes. Recheck
    // briefly so a new widget appears on this visit instead of requiring the user to reopen
    // Shortcuts. The ID is enough to show a pending row; its name/icon fill in when saved.
    LaunchedEffect(refreshKey) {
        repeat(12) {
            delay(250L)
            val refreshed = loadShortcutWidgets(context)
            if (refreshed != shortcuts) shortcuts = refreshed
        }
    }

    // Keep the state read explicit so a return from the widget editor refreshes this list.
    @Suppress("UNUSED_VARIABLE")
    val observedRefresh = refreshKey

    if (shortcuts.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(contentPadding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Outlined.Widgets,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(16.dp))
            Text("No shortcuts yet", style = MaterialTheme.typography.titleLarge)
            Text(
                "Create a home-screen widget that looks like an app icon and runs one of your Actions when tapped.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(18.dp))
            Button(onClick = onCreateNew) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Create new")
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Home-screen shortcuts", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Each shortcut has its own icon and appearance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(shortcuts, key = { it.widgetId }) { shortcut ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        WidgetIconImage(
                            iconKey = shortcut.icon,
                            contentDescription = shortcut.actionName,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(shortcut.actionName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Home screen widget", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(context, TaskWidgetConfigActivity::class.java)
                                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, shortcut.widgetId)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Edit")
                    }
                    TextButton(onClick = { pendingDelete = shortcut }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    pendingDelete?.let { shortcut ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete widget?") },
            text = {
                Text(
                    "This deletes the widget's protected Action and removes it from OpenTasker's Shortcuts list. Android's launcher still owns the home-screen widget itself, so you'll also need to remove that leftover icon from your home screen.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteWidget(shortcut.widgetId) { deleted ->
                            if (deleted) {
                                shortcuts = shortcuts.filterNot { it.widgetId == shortcut.widgetId }
                            }
                            pendingDelete = null
                        }
                    },
                ) {
                    Text("Delete widget", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

internal fun requestNewTaskWidget(context: Context): Boolean {
    val manager = AppWidgetManager.getInstance(context)
    val provider = ComponentName(context, TaskWidgetProvider::class.java)
    return manager.requestPinAppWidget(provider, null, null)
}

internal fun taskWidgetCount(context: Context): Int {
    val prefs = context.getSharedPreferences(TaskWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
    // The installed AppWidget IDs are the source of truth. A new widget is listed
    // immediately, even while its provider callback is still creating the backing Action.
    return AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, TaskWidgetProvider::class.java))
        .count { widgetId ->
            !prefs.getBoolean(TaskWidgetProvider.keyRemoved(widgetId), false)
        }
}

private fun loadShortcutWidgets(context: Context): List<ShortcutWidgetRow> {
    val manager = AppWidgetManager.getInstance(context)
    val ids = manager.getAppWidgetIds(ComponentName(context, TaskWidgetProvider::class.java))
    val prefs = context.getSharedPreferences(TaskWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
    return ids.toList().mapNotNull { widgetId ->
        // Do not hide freshly pinned widgets while onUpdate is still creating their Action.
        if (prefs.getBoolean(TaskWidgetProvider.keyRemoved(widgetId), false)) {
            return@mapNotNull null
        }
        ShortcutWidgetRow(
            widgetId = widgetId,
            actionName = prefs.getString(
                TaskWidgetProvider.keyActionName(widgetId),
                "Widget $widgetId",
            ) ?: "Widget $widgetId",
            icon = WidgetIconCatalog.resolveKey(
                prefs.getString(
                    TaskWidgetProvider.keyIcon(widgetId),
                    WidgetIconCatalog.default.key,
                ),
            ),
        )
    }.sortedBy { it.actionName.lowercase() }
}
