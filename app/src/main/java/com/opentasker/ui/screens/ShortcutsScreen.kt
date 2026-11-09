package com.opentasker.ui.screens

import com.opentasker.ui.icons.Icons
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.opentasker.widget.WidgetDisplayConfigActivity
import com.opentasker.widget.WidgetDisplayProvider
import com.opentasker.widget.WidgetDisplayStore
import com.opentasker.widget.WidgetIconCatalog
import com.opentasker.ui.icons.WidgetIconImage
import kotlinx.coroutines.delay

internal enum class OpenTaskerWidgetType { ACTION_SHORTCUT, DISPLAY }

internal data class OpenTaskerWidgetEntry(
    val widgetId: Int,
    val name: String,
    val icon: String,
    val type: OpenTaskerWidgetType,
    val variantLabel: String,
)

/** All OpenTasker home-screen widgets, with direct access to their editors. */
@Composable
internal fun ShortcutsScreen(
    contentPadding: PaddingValues,
    onDeleteWidget: (OpenTaskerWidgetEntry, (Boolean) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshKey by remember { mutableIntStateOf(0) }
    var widgets by remember { mutableStateOf(loadOpenTaskerWidgets(context)) }
    var pendingDelete by remember { mutableStateOf<OpenTaskerWidgetEntry?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshKey += 1
                widgets = loadOpenTaskerWidgets(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Android can deliver the widget-pinned callback just after the app resumes. Recheck briefly
    // so newly pinned widgets appear without requiring the user to reopen this screen.
    LaunchedEffect(refreshKey) {
        repeat(12) {
            delay(250L)
            val refreshed = loadOpenTaskerWidgets(context)
            if (refreshed != widgets) widgets = refreshed
        }
    }

    @Suppress("UNUSED_VARIABLE")
    val observedRefresh = refreshKey

    if (widgets.isEmpty()) {
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
            Text("No widgets yet", style = MaterialTheme.typography.titleLarge)
            Text(
                "Create a one-tap Action widget or build a multi-state Widget Display dashboard.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(10.dp))
            Text(
                "Tap Create to add a Widget Shortcut or a Widget Display.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
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
                        Text("Widgets on your home screen", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Action shortcuts and customizable multi-state displays.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(widgets, key = { "${it.type.name}_${it.widgetId}" }) { widget ->
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
                        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            WidgetIconImage(
                                iconKey = widget.icon,
                                contentDescription = widget.name,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(widget.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${widget.variantLabel} · ID ${widget.widgetId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                val target = if (widget.type == OpenTaskerWidgetType.DISPLAY) {
                                    WidgetDisplayConfigActivity::class.java
                                } else {
                                    TaskWidgetConfigActivity::class.java
                                }
                                context.startActivity(
                                    Intent(context, target)
                                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widget.widgetId)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            },
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Edit")
                        }
                        TextButton(onClick = { pendingDelete = widget }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { widget ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete widget?") },
            text = {
                Text(
                    if (widget.type == OpenTaskerWidgetType.DISPLAY) {
                        "This removes the Widget Display and its saved states from OpenTasker. Android’s launcher still owns the home-screen widget, so remove the leftover placeholder from your home screen too."
                    } else {
                        "This deletes the widget's protected Action and removes it from OpenTasker's Widgets list. Android's launcher still owns the home-screen widget itself, so you'll also need to remove that leftover icon from your home screen."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteWidget(widget) { deleted ->
                            if (deleted) widgets = widgets.filterNot {
                                it.widgetId == widget.widgetId && it.type == widget.type
                            }
                            pendingDelete = null
                        }
                    },
                ) { Text("Delete widget", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

/**
 * One creation button with a softly expanding, Google Keep-style choice panel.
 * Choosing Widget Display opens the size picker as the second step.
 */
@Composable
internal fun WidgetsCreateFab(
    onCreateShortcut: () -> Unit,
    onCreateDisplay: (WidgetDisplayProvider.Size) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showDisplaySizes by rememberSaveable { mutableStateOf(false) }
    val addRotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = tween(190),
        label = "widgets-create-icon-rotation",
    )

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(150)) +
                slideInVertically(tween(210)) { it / 4 } +
                scaleIn(tween(210), initialScale = 0.94f),
            exit = fadeOut(tween(110)) +
                slideOutVertically(tween(150)) { it / 5 } +
                scaleOut(tween(150), targetScale = 0.96f),
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 5.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            ) {
                Column(
                    modifier = Modifier.width(254.dp).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Create a widget",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(15.dp))
                            .clickable {
                                expanded = false
                                onCreateShortcut()
                            }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(13.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Widget Shortcut", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "One tap to run an Action",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(15.dp))
                            .clickable {
                                expanded = false
                                showDisplaySizes = true
                            }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(13.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.Widgets,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Widget Display", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "A custom multi-state dashboard",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { expanded = !expanded },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = if (expanded) "Close create menu" else "Create widget",
                    modifier = Modifier.rotate(addRotation),
                )
            },
            text = { Text(if (expanded) "Close" else "Create") },
        )
    }

    if (showDisplaySizes) {
        AlertDialog(
            onDismissRequest = { showDisplaySizes = false },
            title = { Text("Create Widget Display") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Choose a fixed launcher layout. Set the design canvas width and height in the editor.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    WidgetDisplayProvider.Size.entries.forEach { size ->
                        TextButton(
                            onClick = {
                                showDisplaySizes = false
                                onCreateDisplay(size)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(size.label, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDisplaySizes = false }) { Text("Cancel") }
            },
        )
    }
}

internal fun requestNewTaskWidget(context: Context): Boolean {
    val manager = AppWidgetManager.getInstance(context)
    val provider = ComponentName(context, TaskWidgetProvider::class.java)
    return manager.requestPinAppWidget(provider, null, null)
}

internal fun taskWidgetCount(context: Context): Int = loadOpenTaskerWidgets(context).size

private fun loadOpenTaskerWidgets(context: Context): List<OpenTaskerWidgetEntry> {
    val manager = AppWidgetManager.getInstance(context)
    val prefs = context.getSharedPreferences(TaskWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
    val shortcuts = manager.getAppWidgetIds(ComponentName(context, TaskWidgetProvider::class.java))
        .toList()
        .filterNot { prefs.getBoolean(TaskWidgetProvider.keyRemoved(it), false) }
        .map { widgetId ->
            OpenTaskerWidgetEntry(
                widgetId = widgetId,
                name = prefs.getString(TaskWidgetProvider.keyActionName(widgetId), "Widget $widgetId")
                    ?: "Widget $widgetId",
                icon = WidgetIconCatalog.resolveKey(
                    prefs.getString(TaskWidgetProvider.keyIcon(widgetId), WidgetIconCatalog.default.key),
                ),
                type = OpenTaskerWidgetType.ACTION_SHORTCUT,
                variantLabel = "Widget Shortcut",
            )
        }
    val displays = WidgetDisplayProvider.instances(context).map { instance ->
        val config = WidgetDisplayStore.load(context, instance.widgetId)
        val state = config.states.firstOrNull()
        OpenTaskerWidgetEntry(
            widgetId = instance.widgetId,
            name = state?.title?.takeIf { it.isNotBlank() }
                ?: state?.name?.takeIf { it.isNotBlank() }
                ?: "Widget Display ${instance.widgetId}",
            icon = state?.elements?.firstOrNull { it.type == com.opentasker.widget.WidgetDisplayElementType.ICON }?.icon
                ?: WidgetIconCatalog.default.key,
            type = OpenTaskerWidgetType.DISPLAY,
            variantLabel = instance.size.label,
        )
    }
    return (shortcuts + displays).sortedBy { it.name.lowercase() }
}
