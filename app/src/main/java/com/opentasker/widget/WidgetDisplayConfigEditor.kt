package com.opentasker.widget

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opentasker.app.OpenTaskerApp_NoHilt
import com.opentasker.app.R
import com.opentasker.core.model.Profile
import com.opentasker.core.storage.toDomainDecodeResult
import com.opentasker.ui.icons.WidgetIconImage
import com.opentasker.ui.screens.ActionIconPickerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
internal fun WidgetDisplayConfigEditor(
    widgetId: Int,
    initialConfig: WidgetDisplayConfig,
    onBackToShortcut: () -> Unit,
    onSave: (WidgetDisplayConfig) -> Unit,
) {
    val context = LocalContext.current
    val availableActions by produceState(initialValue = emptyList<Profile>()) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                OpenTaskerApp_NoHilt.awaitDb().profileDao().getAll()
                    .mapNotNull { entity ->
                        val decoded = entity.toDomainDecodeResult()
                        decoded.value.takeIf {
                            decoded.issue == null && it.widgetId == null && it.enterTaskId > 0L
                        }
                    }
                    .sortedBy { it.name.lowercase() }
            }.getOrDefault(emptyList())
        }
    }

    var title by rememberSaveable(widgetId) { mutableStateOf(initialConfig.title) }
    var subtitle by rememberSaveable(widgetId) { mutableStateOf(initialConfig.subtitle) }
    var backgroundHex by rememberSaveable(widgetId) { mutableStateOf(initialConfig.backgroundColor.toHexColor()) }
    var textHex by rememberSaveable(widgetId) { mutableStateOf(initialConfig.textColor.toHexColor()) }
    var accentHex by rememberSaveable(widgetId) { mutableStateOf(initialConfig.accentColor.toHexColor()) }
    var gradient by rememberSaveable(widgetId) { mutableStateOf(initialConfig.gradient) }
    var horizontal by rememberSaveable(widgetId) { mutableStateOf(initialConfig.horizontal) }
    var elements by remember(widgetId) { mutableStateOf(initialConfig.elements) }
    var selectingActionFor by rememberSaveable(widgetId) { mutableStateOf<String?>(null) }
    var selectingIconFor by rememberSaveable(widgetId) { mutableStateOf<String?>(null) }

    val backgroundColor = parseDisplayColor(backgroundHex)
    val textColor = parseDisplayColor(textHex)
    val accentColor = parseDisplayColor(accentHex)
    val canSave = title.isNotBlank() && backgroundColor != null && textColor != null && accentColor != null &&
        elements.size <= 18 && elements.all {
            it.type !in setOf(WidgetDisplayElementType.BUTTON, WidgetDisplayElementType.SHORTCUT) ||
                (it.actionProfileId ?: 0L) > 0L
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Widget Display")
                        Text(
                            "Build a home-screen dashboard",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBackToShortcut) { Text("Widget shortcut") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Compose a compact dashboard from text, icons, progress, dividers, and interactive Action shortcuts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Display title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = subtitle,
                onValueChange = { subtitle = it },
                label = { Text("Subtitle") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Horizontal layout", fontWeight = FontWeight.Medium)
                    Text(
                        "Arrange items side by side instead of in a column.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = horizontal, onCheckedChange = { horizontal = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Gradient background", fontWeight = FontWeight.Medium)
                    Text(
                        "Blend the background into a darker shade.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = gradient, onCheckedChange = { gradient = it })
            }
            OutlinedTextField(
                value = backgroundHex,
                onValueChange = { backgroundHex = it },
                label = { Text("Background color (HEX)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = backgroundColor == null,
            )
            OutlinedTextField(
                value = textHex,
                onValueChange = { textHex = it },
                label = { Text("Text color (HEX)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = textColor == null,
            )
            OutlinedTextField(
                value = accentHex,
                onValueChange = { accentHex = it },
                label = { Text("Accent color (HEX)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = accentColor == null,
            )

            Text("Add content", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                addTypes.forEach { (label, type) ->
                    OutlinedButton(
                        onClick = {
                            val item = WidgetDisplayElement(
                                id = UUID.randomUUID().toString(),
                                type = type,
                                text = when (type) {
                                    WidgetDisplayElementType.TEXT -> "Your text"
                                    WidgetDisplayElementType.BUTTON -> "Run Action"
                                    WidgetDisplayElementType.SHORTCUT -> "Action shortcut"
                                    WidgetDisplayElementType.ICON -> "Icon label"
                                    WidgetDisplayElementType.PROGRESS -> "Progress"
                                    else -> ""
                                },
                            )
                            elements = elements + item
                        },
                        enabled = elements.size < 18,
                    ) { Text(label) }
                }
            }

            Text("Display items (${elements.size}/18)", style = MaterialTheme.typography.titleMedium)
            elements.forEachIndexed { index, element ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                element.type.displayLabel,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            TextButton(
                                onClick = { elements = elements.toMutableList().also { it.removeAt(index) } },
                            ) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                        }
                        when (element.type) {
                            WidgetDisplayElementType.TEXT,
                            WidgetDisplayElementType.BUTTON,
                            WidgetDisplayElementType.SHORTCUT,
                            WidgetDisplayElementType.ICON,
                            WidgetDisplayElementType.PROGRESS,
                            -> {
                                OutlinedTextField(
                                    value = element.text,
                                    onValueChange = { text ->
                                        elements = elements.replaceElement(element.id) { it.copy(text = text) }
                                    },
                                    label = {
                                        Text(
                                            when (element.type) {
                                                WidgetDisplayElementType.TEXT -> "Text"
                                                WidgetDisplayElementType.ICON -> "Caption"
                                                WidgetDisplayElementType.PROGRESS -> "Progress label"
                                                else -> "Button label"
                                            },
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            else -> Text(
                                if (element.type == WidgetDisplayElementType.DIVIDER) "A thin separator line."
                                else "Adds vertical breathing room.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        if (element.type in setOf(WidgetDisplayElementType.BUTTON, WidgetDisplayElementType.SHORTCUT)) {
                            val selectedAction = availableActions.firstOrNull { it.id == element.actionProfileId }
                            OutlinedButton(
                                onClick = { selectingActionFor = element.id },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    selectedAction?.name?.let { "Action: $it" } ?: "Choose an Action",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            OutlinedButton(
                                onClick = { selectingIconFor = element.id },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                WidgetIconImage(
                                    iconKey = element.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.size(6.dp))
                                Text("Choose icon")
                            }
                        }
                        if (element.type == WidgetDisplayElementType.ICON) {
                            OutlinedButton(
                                onClick = { selectingIconFor = element.id },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                WidgetIconImage(
                                    iconKey = element.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.size(6.dp))
                                Text("Choose icon")
                            }
                        }
                        if (element.type == WidgetDisplayElementType.TEXT) {
                            OutlinedTextField(
                                value = element.textSizeSp.toString(),
                                onValueChange = { raw ->
                                    val parsed = raw.toIntOrNull()?.coerceIn(10, 32) ?: return@OutlinedTextField
                                    elements = elements.replaceElement(element.id) { it.copy(textSizeSp = parsed) }
                                },
                                label = { Text("Text size (10–32 sp)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (element.type == WidgetDisplayElementType.PROGRESS) {
                            OutlinedTextField(
                                value = element.progress.toString(),
                                onValueChange = { raw ->
                                    val parsed = raw.toIntOrNull()?.coerceIn(0, 100) ?: return@OutlinedTextField
                                    elements = elements.replaceElement(element.id) { it.copy(progress = parsed) }
                                },
                                label = { Text("Progress (0–100)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (index > 0) {
                            TextButton(
                                onClick = {
                                    val copy = elements.toMutableList()
                                    val moved = copy.removeAt(index)
                                    copy.add(index - 1, moved)
                                    elements = copy
                                },
                            ) { Text("Move up") }
                        }
                        if (index < elements.lastIndex) {
                            TextButton(
                                onClick = {
                                    val copy = elements.toMutableList()
                                    val moved = copy.removeAt(index)
                                    copy.add(index + 1, moved)
                                    elements = copy
                                },
                            ) { Text("Move down") }
                        }
                    }
                }
            }

            Text("Preview", style = MaterialTheme.typography.titleMedium)
            WidgetDisplayPreview(
                title = title,
                subtitle = subtitle,
                elements = elements,
                background = backgroundColor?.let(::Color) ?: MaterialTheme.colorScheme.surfaceVariant,
                foreground = textColor?.let(::Color) ?: MaterialTheme.colorScheme.onSurface,
                accent = accentColor?.let(::Color) ?: MaterialTheme.colorScheme.primary,
                horizontal = horizontal,
            )

            Button(
                onClick = {
                    val bg = backgroundColor ?: return@Button
                    val fg = textColor ?: return@Button
                    val accent = accentColor ?: return@Button
                    onSave(
                        WidgetDisplayConfig(
                            title = title.trim(),
                            subtitle = subtitle.trim(),
                            backgroundColor = bg,
                            textColor = fg,
                            accentColor = accent,
                            gradient = gradient,
                            horizontal = horizontal,
                            elements = elements,
                        ),
                    )
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save Widget Display") }
        }
    }

    selectingActionFor?.let { elementId ->
        val current = elements.firstOrNull { it.id == elementId }
        AlertDialog(
            onDismissRequest = { selectingActionFor = null },
            title = { Text("Choose an Action") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().height(320.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    availableActions.forEach { action ->
                        TextButton(
                            onClick = {
                                elements = elements.replaceElement(elementId) {
                                    it.copy(actionProfileId = action.id, icon = WidgetIconCatalog.resolveKey(action.icon))
                                }
                                selectingActionFor = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(action.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Action", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (availableActions.isEmpty()) {
                        Text("Create an Action first. It will appear here as a widget shortcut.")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectingActionFor = null }) { Text("Close") } },
        )
    }

    selectingIconFor?.let { elementId ->
        val current = elements.firstOrNull { it.id == elementId }
        if (current != null) {
            ActionIconPickerDialog(
                selectedIcon = current.icon,
                onIconSelected = { icon ->
                    elements = elements.replaceElement(elementId) { it.copy(icon = icon) }
                    selectingIconFor = null
                },
                onDismiss = { selectingIconFor = null },
            )
        }
    }
}

@Composable
private fun WidgetDisplayPreview(
    title: String,
    subtitle: String,
    elements: List<WidgetDisplayElement>,
    background: Color,
    foreground: Color,
    accent: Color,
    horizontal: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = background,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title.ifBlank { "Widget Display" }, color = foreground, fontWeight = FontWeight.Bold)
            if (subtitle.isNotBlank()) Text(subtitle, color = foreground.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
            val arrangement = if (horizontal) Arrangement.spacedBy(6.dp) else Arrangement.spacedBy(6.dp)
            if (horizontal) {
                Row(horizontalArrangement = arrangement, verticalAlignment = Alignment.CenterVertically) {
                    elements.take(5).forEach { WidgetDisplayPreviewItem(it, foreground, accent) }
                }
            } else {
                Column(verticalArrangement = arrangement) {
                    elements.take(5).forEach { WidgetDisplayPreviewItem(it, foreground, accent) }
                }
            }
        }
    }
}

@Composable
private fun WidgetDisplayPreviewItem(item: WidgetDisplayElement, foreground: Color, accent: Color) {
    when (item.type) {
        WidgetDisplayElementType.TEXT -> Text(item.text, color = foreground, fontSize = item.textSizeSp.sp)
        WidgetDisplayElementType.BUTTON,
        WidgetDisplayElementType.SHORTCUT -> Surface(color = accent, shape = RoundedCornerShape(10.dp)) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                WidgetIconImage(
                    iconKey = item.icon,
                    contentDescription = null,
                    tint = foreground,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(7.dp))
                Text(item.text, color = foreground, maxLines = 1)
            }
        }
        WidgetDisplayElementType.ICON -> Row(verticalAlignment = Alignment.CenterVertically) {
            WidgetIconImage(item.icon, null, tint = accent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.size(7.dp))
            Text(item.text, color = foreground)
        }
        WidgetDisplayElementType.PROGRESS -> Column {
            Text("${item.text}  ${item.progress.coerceIn(0, 100)}%", color = foreground)
            Surface(color = foreground.copy(alpha = 0.2f), shape = RoundedCornerShape(20.dp)) {
                Spacer(
                    Modifier.fillMaxWidth(item.progress.coerceIn(0, 100) / 100f)
                        .height(5.dp).background(accent, RoundedCornerShape(20.dp)),
                )
            }
        }
        WidgetDisplayElementType.DIVIDER -> Spacer(Modifier.fillMaxWidth().height(1.dp).background(foreground.copy(alpha = 0.3f)))
        WidgetDisplayElementType.SPACER -> Spacer(Modifier.height(8.dp))
    }
}

private val addTypes = listOf(
    "Text" to WidgetDisplayElementType.TEXT,
    "Button" to WidgetDisplayElementType.BUTTON,
    "Widget shortcut" to WidgetDisplayElementType.SHORTCUT,
    "Icon" to WidgetDisplayElementType.ICON,
    "Progress" to WidgetDisplayElementType.PROGRESS,
    "Divider" to WidgetDisplayElementType.DIVIDER,
    "Spacer" to WidgetDisplayElementType.SPACER,
)

private val WidgetDisplayElementType.displayLabel: String
    get() = when (this) {
        WidgetDisplayElementType.TEXT -> "Text"
        WidgetDisplayElementType.BUTTON -> "Action button"
        WidgetDisplayElementType.SHORTCUT -> "Widget shortcut"
        WidgetDisplayElementType.ICON -> "Icon / image"
        WidgetDisplayElementType.PROGRESS -> "Progress"
        WidgetDisplayElementType.DIVIDER -> "Divider"
        WidgetDisplayElementType.SPACER -> "Spacer"
    }

private fun List<WidgetDisplayElement>.replaceElement(
    id: String,
    update: (WidgetDisplayElement) -> WidgetDisplayElement,
): List<WidgetDisplayElement> = map { if (it.id == id) update(it) else it }

private fun Int.toHexColor(): String = String.format(java.util.Locale.ROOT, "#%06X", 0xFFFFFF and this)

private fun parseDisplayColor(value: String): Int? =
    runCatching { AndroidColor.parseColor(value.trim()) }.getOrNull()
