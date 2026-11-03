package com.opentasker.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.opentasker.widget.TaskWidgetConfigActivity
import com.opentasker.widget.TaskWidgetProvider
import com.opentasker.widget.WidgetIconCatalog

private data class ShortcutWidgetRow(
    val widgetId: Int,
    val taskName: String,
    val icon: String,
)

/** A list of pinned home-screen task widgets, with direct access to their configuration editor. */
@Composable
internal fun ShortcutsScreen(
    contentPadding: PaddingValues,
    onCreateNew: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshKey by remember { mutableIntStateOf(0) }
    var shortcuts by remember { mutableStateOf(loadShortcutWidgets(context)) }

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
                "Create a home-screen widget that looks like an app icon and runs one of your tasks when tapped.",
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
                        Text(shortcut.icon, style = MaterialTheme.typography.headlineMedium)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(shortcut.taskName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                }
            }
        }
    }
}

internal fun requestNewTaskWidget(context: Context): Boolean {
    val manager = AppWidgetManager.getInstance(context)
    val provider = ComponentName(context, TaskWidgetProvider::class.java)
    return manager.requestPinAppWidget(provider, null, null)
}

internal fun taskWidgetCount(context: Context): Int = AppWidgetManager.getInstance(context)
    .getAppWidgetIds(ComponentName(context, TaskWidgetProvider::class.java)).size

private fun loadShortcutWidgets(context: Context): List<ShortcutWidgetRow> {
    val manager = AppWidgetManager.getInstance(context)
    val ids = manager.getAppWidgetIds(ComponentName(context, TaskWidgetProvider::class.java))
    val prefs = context.getSharedPreferences(TaskWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
    return ids.map { widgetId ->
        ShortcutWidgetRow(
            widgetId = widgetId,
            taskName = prefs.getString(
                TaskWidgetProvider.keyTaskName(widgetId),
                "Choose a task",
            ) ?: "Choose a task",
            icon = prefs.getString(
                TaskWidgetProvider.keyIcon(widgetId),
                WidgetIconCatalog.default.symbol,
            ) ?: WidgetIconCatalog.default.symbol,
        )
    }.sortedBy { it.taskName.lowercase() }
}
