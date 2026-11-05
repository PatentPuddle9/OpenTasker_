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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import com.opentasker.ui.icons.WidgetIconImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opentasker.app.R
import com.opentasker.app.OpenTaskerApp_NoHilt
import com.opentasker.ui.screens.ActionIconPickerDialog
import com.opentasker.widget.CustomWidgetIconStore
import com.opentasker.ui.theme.OpenTaskerTheme
import com.opentasker.ui.theme.ThemeMode
import com.opentasker.ui.theme.ThemePreference
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

        val prefs = getSharedPreferences(TaskWidgetProvider.PREFS_NAME, MODE_PRIVATE)
        val initialProfileId = prefs.getLong(TaskWidgetProvider.keyActionProfileId(widgetId), -1L)
            .takeIf { it > 0L }
        val initialExecutionId = prefs.getLong(TaskWidgetProvider.keyTaskId(widgetId), -1L)
            .takeIf { it > 0L }
        val initialActionName = prefs.getString(
            TaskWidgetProvider.keyActionName(widgetId),
            null,
        )?.takeIf { it.isNotBlank() } ?: "Widget $widgetId"
        val initialStyle = WidgetStyle(
            icon = prefs.getString(TaskWidgetProvider.keyIcon(widgetId), WidgetIconCatalog.default.key)
                ?: WidgetIconCatalog.default.key,
            iconColor = prefs.getInt(TaskWidgetProvider.keyIconColor(widgetId), WidgetStyle().iconColor),
            backgroundColor = prefs.getInt(TaskWidgetProvider.keyBackgroundColor(widgetId), WidgetStyle().backgroundColor),
            gradient = prefs.getBoolean(TaskWidgetProvider.keyGradient(widgetId), WidgetStyle().gradient),
            shape = prefs.getString(TaskWidgetProvider.keyShape(widgetId), WidgetShapeCatalog.default.key)
                ?: WidgetShapeCatalog.default.key,
        )

        setContent {
            val themeMode by ThemePreference.observe(this).collectAsState(initial = ThemeMode.System)
            OpenTaskerTheme(themeMode) {
                ConfigScreen(
                    widgetId = widgetId,
                    initialProfileId = initialProfileId,
                    initialActionName = initialActionName,
                    initialStyle = initialStyle,
                    onWidgetSaved = { name, style ->
                        saveWidgetAction(initialProfileId, initialExecutionId, name, style)
                    },
                )
            }
        }
    }

    private fun saveWidgetAction(
        existingProfileId: Long?,
        existingExecutionId: Long?,
        name: String,
        style: WidgetStyle,
    ) {
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    WidgetProfileLinks.createOrUpdateAction(
                        context = this@TaskWidgetConfigActivity,
                        widgetId = widgetId,
                        name = name.trim(),
                        style = style,
                        existingProfileId = existingProfileId,
                        sourceProfileId = existingProfileId,
                        sourceExecutionId = existingExecutionId,
                    )
                }
            }.onSuccess { profileId ->
                getSharedPreferences(TaskWidgetProvider.PREFS_NAME, MODE_PRIVATE).edit()
                    .putLong(TaskWidgetProvider.keyActionProfileId(widgetId), profileId)
                    .putString(TaskWidgetProvider.keyActionName(widgetId), name.trim())
                    .putString(TaskWidgetProvider.keyIcon(widgetId), style.icon)
                    .putInt(TaskWidgetProvider.keyIconColor(widgetId), style.iconColor)
                    .putInt(TaskWidgetProvider.keyBackgroundColor(widgetId), style.backgroundColor)
                    .putBoolean(TaskWidgetProvider.keyGradient(widgetId), style.gradient)
                    .putString(TaskWidgetProvider.keyShape(widgetId), style.shape)
                    .remove(TaskWidgetProvider.keyTaskId(widgetId))
                    .remove(TaskWidgetProvider.keyProfileId(widgetId))
                    .putBoolean(TaskWidgetProvider.keyRemoved(widgetId), false)
                    .apply()

                TaskWidgetProvider.updateWidget(
                    this@TaskWidgetConfigActivity,
                    AppWidgetManager.getInstance(this@TaskWidgetConfigActivity),
                    widgetId,
                )
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                finish()
            }.onFailure { error ->
                Toast.makeText(
                    this@TaskWidgetConfigActivity,
                    "Couldn't save widget Action: ${error.message ?: "Unknown error"}",
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
    widgetId: Int,
    initialProfileId: Long?,
    initialActionName: String,
    initialStyle: WidgetStyle,
    onWidgetSaved: (String, WidgetStyle) -> Unit,
) {
    var actionName by rememberSaveable { mutableStateOf(initialActionName) }
    val context = LocalContext.current.applicationContext
    var selectedIcon by rememberSaveable { mutableStateOf(WidgetIconCatalog.resolveKey(initialStyle.icon)) }
    val customIcons by CustomWidgetIconStore.observe(context).collectAsState()
    val selectedIconLabel = customIcons.firstOrNull { it.key == selectedIcon }?.name
        ?: WidgetIconCatalog.labelFor(selectedIcon)
    var showIconPicker by rememberSaveable { mutableStateOf(false) }
    var iconColorHex by rememberSaveable { mutableStateOf(initialStyle.iconColor.toHexColor()) }
    var backgroundColorHex by rememberSaveable { mutableStateOf(initialStyle.backgroundColor.toHexColor()) }
    var useGradient by rememberSaveable { mutableStateOf(initialStyle.gradient) }
    var selectedShape by rememberSaveable { mutableStateOf(initialStyle.shape) }

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
                        Text(
                            if (initialProfileId == null) "Create widget" else "Edit widget",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "Every widget gets its own protected Action",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "OpenTasker creates a separate Action for this widget automatically. You don't need to choose an existing Action.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = actionName,
                onValueChange = { actionName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Widget Action name") },
                singleLine = true,
            )

            Text("Action icon", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = { showIconPicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                WidgetIconImage(
                    iconKey = selectedIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Choose icon: $selectedIconLabel")
            }

            Text("Icon color", style = MaterialTheme.typography.titleMedium)
            WidgetColorEditor(
                value = iconColorHex,
                onValueChange = { iconColorHex = it },
                presets = listOf("#FFFFFF", "#111111", "#FF453A", "#FF9F0A", "#FFD60A", "#30D158", "#32ADE6", "#0A84FF", "#BF5AF2", "#FF2D55"),
            )

            Text("App icon shape", style = MaterialTheme.typography.titleMedium)
            Text(
                "Pick the silhouette used by the widget on your home screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WidgetShapeCatalog.all.forEach { option ->
                    val selected = selectedShape == option.key
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { selectedShape = option.key },
                    ) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = widgetPreviewShape(option.key),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                if (selected) 2.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                            ),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                WidgetIconImage(
                                    iconKey = selectedIcon,
                                    contentDescription = null,
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        Text(option.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }

            Text("Background color", style = MaterialTheme.typography.titleMedium)
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
                actionName = actionName.ifBlank { "Widget $widgetId" },
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
                    onWidgetSaved(
                        actionName.trim(),
                        WidgetStyle(
                            icon = selectedIcon,
                            iconColor = selectedIconColor,
                            backgroundColor = selectedBackgroundColor,
                            gradient = useGradient,
                            shape = selectedShape,
                        ),
                    )
                },
                enabled = actionName.isNotBlank() && iconColor != null && backgroundColor != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (initialProfileId == null) "Create widget and Action" else "Save widget")
            }
        }
    }

    if (showIconPicker) {
        ActionIconPickerDialog(
            selectedIcon = selectedIcon,
            onIconSelected = { selectedIcon = it },
            onDismiss = { showIconPicker = false },
        )
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
    actionName: String,
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
            WidgetIconImage(
                iconKey = icon,
                contentDescription = null,
                tint = iconColor ?: MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(52.dp),
            )
        }
        Text(
            actionName,
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

