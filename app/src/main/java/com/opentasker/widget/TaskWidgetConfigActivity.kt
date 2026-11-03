package com.opentasker.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opentasker.app.R
import com.opentasker.app.OpenTaskerApp_NoHilt
import com.opentasker.core.model.Task
import com.opentasker.core.storage.StorageDecodeIssue
import com.opentasker.ui.screens.StorageDecodeWarningCard
import com.opentasker.ui.theme.DesignSystem
import com.opentasker.ui.theme.OpenTaskerTheme
import com.opentasker.ui.theme.ThemeMode
import com.opentasker.ui.theme.ThemePreference
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TaskWidgetConfigActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(RESULT_CANCELED)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val tasksFlow = flow { emitAll(OpenTaskerApp_NoHilt.awaitDb().taskDao().getAllAsFlow()) }
            .map { entities ->
                val results = entities.map { it.toDomainDecodeResult() }
                WidgetTaskState(
                    tasks = results.mapNotNull { result -> result.value.takeIf { result.issue == null } },
                    storageDecodeIssues = results.mapNotNull { it.issue },
                )
            }

        val prefs = getSharedPreferences(TaskWidgetProvider.PREFS_NAME, MODE_PRIVATE)
        val initialTaskId = prefs.getLong(TaskWidgetProvider.keyTaskId(widgetId), -1L).takeIf { it > 0L }
        val initialStyle = WidgetStyle(
            icon = prefs.getString(TaskWidgetProvider.keyIcon(widgetId), WidgetIconCatalog.default.key)
                ?: WidgetIconCatalog.default.key,
            iconColor = prefs.getInt(TaskWidgetProvider.keyIconColor(widgetId), WidgetStyle().iconColor),
            backgroundColor = prefs.getInt(TaskWidgetProvider.keyBackgroundColor(widgetId), WidgetStyle().backgroundColor),
            gradient = prefs.getBoolean(TaskWidgetProvider.keyGradient(widgetId), WidgetStyle().gradient),
            shape = prefs.getString(TaskWidgetProvider.keyShape(widgetId), WidgetShapeCatalog.default.key) ?: WidgetShapeCatalog.default.key,
        )

        setContent {
            val themeMode by ThemePreference.observe(this).collectAsState(initial = ThemeMode.System)
            OpenTaskerTheme(themeMode) {
                val taskState by tasksFlow.collectAsState(initial = WidgetTaskState())
                ConfigScreen(
                    tasks = taskState.tasks,
                    storageDecodeIssues = taskState.storageDecodeIssues,
                    initialTaskId = initialTaskId,
                    initialStyle = initialStyle,
                    onWidgetCreated = ::onWidgetCreated,
                )
            }
        }
    }

    private fun onWidgetCreated(task: Task, style: WidgetStyle) {
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    WidgetProfileLinks.syncProfile(this@TaskWidgetConfigActivity, widgetId, task, style)
                }
            }.onSuccess {
                val prefs = getSharedPreferences(TaskWidgetProvider.PREFS_NAME, MODE_PRIVATE)
                prefs.edit()
                    .putLong(TaskWidgetProvider.keyTaskId(widgetId), task.id)
                    .putString(TaskWidgetProvider.keyTaskName(widgetId), task.name)
                    .putString(TaskWidgetProvider.keyIcon(widgetId), style.icon)
                    .putInt(TaskWidgetProvider.keyIconColor(widgetId), style.iconColor)
                    .putInt(TaskWidgetProvider.keyBackgroundColor(widgetId), style.backgroundColor)
                    .putBoolean(TaskWidgetProvider.keyGradient(widgetId), style.gradient)
                    .putString(TaskWidgetProvider.keyShape(widgetId), style.shape)
                    .apply()

                TaskWidgetProvider.updateWidget(this@TaskWidgetConfigActivity, AppWidgetManager.getInstance(this@TaskWidgetConfigActivity), widgetId)
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                finish()
            }.onFailure { error ->
                Toast.makeText(
                    this@TaskWidgetConfigActivity,
                    "Couldn't save widget profile: ${error.message ?: "Unknown error"}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
}

private fun Int.toHexColor(): String = String.format(java.util.Locale.ROOT, "#%06X", 0xFFFFFF and this)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ConfigScreen(
    tasks: List<Task>,
    storageDecodeIssues: List<StorageDecodeIssue>,
    initialTaskId: Long?,
    initialStyle: WidgetStyle,
    onWidgetCreated: (Task, WidgetStyle) -> Unit,
) {
    var selectedTaskId by rememberSaveable { mutableStateOf(initialTaskId) }
    var selectedIcon by rememberSaveable { mutableStateOf(WidgetIconCatalog.resolve(initialStyle.icon).key) }
    var iconColorHex by rememberSaveable { mutableStateOf(initialStyle.iconColor.toHexColor()) }
    var backgroundColorHex by rememberSaveable { mutableStateOf(initialStyle.backgroundColor.toHexColor()) }
    var useGradient by rememberSaveable { mutableStateOf(initialStyle.gradient) }
    var selectedShape by rememberSaveable { mutableStateOf(initialStyle.shape) }

    val selectedTask = tasks.firstOrNull { it.id == selectedTaskId }
    val iconColor = parseWidgetColor(iconColorHex)
    val backgroundColor = parseWidgetColor(backgroundColorHex)
    val previewTextColor = backgroundColor?.let { color ->
        if (Color(color).luminance() > 0.48f) Color(0xFF211826) else Color.White
    } ?: Color.White

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (initialTaskId == null) "Create a task widget" else "Edit widget", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "Choose a task, then make it look like your own app icon",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        if (tasks.isEmpty() && storageDecodeIssues.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxSize().padding(padding),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(DesignSystem.Radii.xxl),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
                    ) {
                        Box(Modifier.padding(14.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = stringResource(R.string.widget_setup_required),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    }
                    Spacer(Modifier.size(20.dp))
                    Text(stringResource(R.string.widget_empty_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Text(
                        stringResource(R.string.widget_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = DesignSystem.Screen.horizontalPadding)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(DesignSystem.Screen.cardGap),
            ) {
                if (storageDecodeIssues.isNotEmpty()) StorageDecodeWarningCard(storageDecodeIssues)
                WidgetConfigHeader(taskCount = tasks.size)

                Text("1. Choose what this widget runs", style = MaterialTheme.typography.titleMedium)
                tasks.forEach { task ->
                    val selected = task.id == selectedTaskId
                    Card(
                        onClick = { selectedTaskId = task.id },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
                        ),
                        border = BorderStroke(
                            if (selected) 2.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.46f),
                        ),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(task.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    pluralStringResource(R.plurals.widget_action_count, task.actions.size, task.actions.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (selected) {
                                Text(
                                    stringResource(R.string.widget_assign),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }

                if (selectedTask != null) {
                    Spacer(Modifier.height(2.dp))
                    Text("2. Pick an icon", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Choose from a collection of vector icons. The icon color is fully customizable.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        WidgetIconCatalog.all.forEach { option ->
                            val isSelected = selectedIcon == option.key
                            Surface(
                                modifier = Modifier.size(46.dp).clickable { selectedIcon = option.key },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
                                border = BorderStroke(
                                    if (isSelected) 2.dp else 1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                ),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(option.drawableRes),
                                        contentDescription = option.label,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }
                    }
                    Text("Selected: ${WidgetIconCatalog.resolve(selectedIcon).label}")

                    Text("3. Choose icon color", style = MaterialTheme.typography.titleMedium)
                    WidgetColorEditor(
                        value = iconColorHex,
                        onValueChange = { iconColorHex = it },
                        presets = listOf("#FFFFFF", "#111111", "#FF453A", "#FF9F0A", "#FFD60A", "#30D158", "#32ADE6", "#0A84FF", "#BF5AF2", "#FF2D55"),
                    )

                    Text("4. Choose app icon shape", style = MaterialTheme.typography.titleMedium)
                    Text("Pick the silhouette used by the widget on your home screen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        WidgetShapeCatalog.all.forEach { option ->
                            val selected = selectedShape == option.key
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { selectedShape = option.key }) {
                                Surface(
                                    modifier = Modifier.size(48.dp),
                                    shape = widgetPreviewShape(option.key),
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                ) { Box(contentAlignment = Alignment.Center) { Icon(painter = painterResource(WidgetIconCatalog.resolve(selectedIcon).drawableRes), contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp)) } }
                                Text(option.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }

                    Text("5. Choose background color", style = MaterialTheme.typography.titleMedium)
                    WidgetColorEditor(
                        value = backgroundColorHex,
                        onValueChange = { backgroundColorHex = it },
                        presets = listOf("#6542A0", "#1D4ED8", "#0F766E", "#B45309", "#BE123C", "#334155", "#111827", "#F3F4F6", "#FDE68A", "#D1FAE5"),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Gradient background", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Blend your background color into a darker shade.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = useGradient, onCheckedChange = { useGradient = it })
                    }

                    Text("Live preview", style = MaterialTheme.typography.titleMedium)
                    WidgetLivePreview(
                        taskName = selectedTask.name,
                        icon = selectedIcon,
                        iconColor = iconColor?.let { Color(it) },
                        backgroundColor = backgroundColor?.let { Color(it) },
                        gradient = useGradient,
                        textColor = previewTextColor,
                        shape = selectedShape,
                    )

                    Button(
                        onClick = {
                            val selectedIconColor = iconColor ?: return@Button
                            val selectedBackgroundColor = backgroundColor ?: return@Button
                            onWidgetCreated(
                                selectedTask,
                                WidgetStyle(
                                    icon = selectedIcon,
                                    iconColor = selectedIconColor,
                                    backgroundColor = selectedBackgroundColor,
                                    gradient = useGradient,
                                    shape = selectedShape,
                                ),
                            )
                        },
                        enabled = iconColor != null && backgroundColor != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (initialTaskId == null) "Add widget to home screen" else "Save shortcut changes")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetColorEditor(
    value: String,
    onValueChange: (String) -> Unit,
    presets: List<String>,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        presets.forEach { preset ->
            val presetColor = parseWidgetColor(preset)?.let { Color(it) } ?: Color.Transparent
            Surface(
                modifier = Modifier.size(30.dp).clickable { onValueChange(preset) },
                color = presetColor,
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
            ) {}
        }
    }
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(7)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("HEX color") },
        placeholder = { Text("#6542A0") },
        singleLine = true,
        isError = parseWidgetColor(value) == null,
        supportingText = {
            Text(if (parseWidgetColor(value) == null) "Enter a valid 6-digit HEX color." else "Example: #6542A0")
        },
    )
}

@Composable
private fun widgetPreviewShape(key: String): androidx.compose.ui.graphics.Shape = when (key) {
    "circle" -> CircleShape
    "square" -> RoundedCornerShape(0.dp)
    "rounded_square" -> RoundedCornerShape(18.dp)
    "teardrop" -> androidx.compose.foundation.shape.GenericShape { size, _ ->
        moveTo(size.width * 0.5f, 0f)
        cubicTo(size.width * 0.92f, 0f, size.width, size.height * 0.3f, size.width, size.height * 0.5f)
        cubicTo(size.width, size.height * 0.8f, size.width * 0.78f, size.height, size.width * 0.5f, size.height)
        cubicTo(size.width * 0.22f, size.height, 0f, size.height * 0.8f, 0f, size.height * 0.5f)
        cubicTo(0f, size.height * 0.3f, size.width * 0.08f, 0f, size.width * 0.5f, 0f)
        close()
    }
    else -> RoundedCornerShape(28.dp)
}

@Composable
private fun WidgetLivePreview(
    taskName: String,
    icon: String,
    iconColor: Color?,
    backgroundColor: Color?,
    gradient: Boolean,
    textColor: Color,
    shape: String,
) {
    val base = backgroundColor ?: MaterialTheme.colorScheme.surfaceVariant
    val end = if (gradient) Color(
        (base.red * 0.76f).coerceIn(0f, 1f),
        (base.green * 0.76f).coerceIn(0f, 1f),
        (base.blue * 0.76f).coerceIn(0f, 1f),
        base.alpha,
    ) else base
    Column(
        modifier = Modifier.fillMaxWidth().height(188.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(104.dp).background(Brush.linearGradient(listOf(base, end)), widgetPreviewShape(shape)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(WidgetIconCatalog.resolve(icon).drawableRes),
                contentDescription = null,
                tint = iconColor ?: MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(52.dp),
            )
        }
        Text(
            taskName,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp),
        )
    }
}

private fun parseWidgetColor(value: String): Int? {
    val normalized = value.trim().removePrefix("#")
    if (!Regex("^[0-9a-fA-F]{6}$").matches(normalized)) return null
    return runCatching { AndroidColor.parseColor("#$normalized") }.getOrNull()
}

private data class WidgetTaskState(
    val tasks: List<Task> = emptyList(),
    val storageDecodeIssues: List<StorageDecodeIssue> = emptyList(),
)

@Composable
private fun WidgetConfigHeader(taskCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.66f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f)),
        shape = RoundedCornerShape(DesignSystem.Radii.xxl),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Make OpenTasker look like an app on your home screen", style = MaterialTheme.typography.headlineSmall)
            Text(
                pluralStringResource(R.plurals.widget_saved_task_count, taskCount, taskCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
