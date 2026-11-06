package com.opentasker.widget

import android.content.Intent
import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.Icon
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
import com.opentasker.ui.icons.Icons
import com.opentasker.core.model.Profile
import com.opentasker.core.scenes.SceneImageLoader
import com.opentasker.core.storage.toDomainDecodeResult
import com.opentasker.ui.icons.WidgetIconImage
import com.opentasker.ui.screens.ActionIconPickerDialog
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WidgetDisplayConfigEditor(
    widgetId: Int,
    initialConfig: WidgetDisplayConfig,
    variantLabel: String,
    onSave: (WidgetDisplayConfig) -> Unit,
    onCancel: () -> Unit,
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

    var states by remember(widgetId) {
        mutableStateOf(initialConfig.states.take(WidgetDisplayStore.MAX_STATES).ifEmpty {
            listOf(WidgetDisplayState(name = "State 1"))
        })
    }
    var editingStateId by rememberSaveable(widgetId) { mutableStateOf<String?>(null) }
    var stateMenuFor by rememberSaveable(widgetId) { mutableStateOf<String?>(null) }
    var deleteStateId by rememberSaveable(widgetId) { mutableStateOf<String?>(null) }
    var showStateLimitDialog by rememberSaveable(widgetId) { mutableStateOf(false) }

    val activeState = states.firstOrNull { it.id == editingStateId }
    var draft by remember(editingStateId, activeState) {
        mutableStateOf(activeState ?: WidgetDisplayState())
    }

    fun createNewState() {
        if (states.size >= WidgetDisplayStore.MAX_STATES) {
            showStateLimitDialog = true
            return
        }
        val state = WidgetDisplayState(
            name = "State ${states.size + 1}",
            title = "",
            subtitle = "",
        )
        states = states + state
        editingStateId = state.id
    }

    fun duplicateState(stateId: String) {
        if (states.size >= WidgetDisplayStore.MAX_STATES) {
            showStateLimitDialog = true
            return
        }
        val index = states.indexOfFirst { it.id == stateId }
        if (index < 0) return
        val source = states[index]
        val duplicate = source.copy(id = UUID.randomUUID().toString(), name = "${source.name} copy")
        states = states.toMutableList().apply { add(index + 1, duplicate) }
        stateMenuFor = null
        editingStateId = duplicate.id
    }

    val title = if (editingStateId == null) "Widget Display" else "Edit state"
    val subtitle = if (editingStateId == null) {
        "$variantLabel · ${states.size} saved states"
    } else {
        "Design what appears in this state"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title)
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    TextButton(
                        onClick = {
                            if (editingStateId != null) {
                                editingStateId = null
                            } else {
                                onCancel()
                            }
                        },
                    ) {
                        Text(if (editingStateId != null) "All states" else "Cancel")
                    }
                },
            )
        },
    ) { padding ->
        if (editingStateId == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Create a slideshow of different Widget Display states. Select a state to edit its layout and content.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = ::createNewState, modifier = Modifier.fillMaxWidth()) {
                    Text("New state")
                }
                states.forEachIndexed { index, state ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            editingStateId = state.id
                            draft = state
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                modifier = Modifier.size(38.dp),
                                color = Color(state.accentColor).copy(alpha = 0.18f),
                                shape = RoundedCornerShape(11.dp),
                            ) {
                                BoxCenterIcon(state)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    state.name.ifBlank { "Untitled state" },
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    listOf(state.title, "${state.elements.size} items").filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Box {
                                IconButton(onClick = { stateMenuFor = state.id }) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = "State options",
                                    )
                                }
                                DropdownMenu(
                                    expanded = stateMenuFor == state.id,
                                    onDismissRequest = { stateMenuFor = null },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Duplicate") },
                                        onClick = { duplicateState(state.id) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        onClick = {
                                            stateMenuFor = null
                                            deleteStateId = state.id
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = {
                        onSave(WidgetDisplayConfig(states = states))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save Widget Display") }
            }
        } else {
            WidgetDisplayStateEditor(
                state = draft,
                actions = availableActions,
                contentPadding = padding,
                onStateChange = { draft = it },
                onSaveState = {
                    states = states.map { if (it.id == draft.id) draft else it }
                    editingStateId = null
                },
            )
        }
    }

    deleteStateId?.let { stateId ->
        val selected = states.firstOrNull { it.id == stateId }
        AlertDialog(
            onDismissRequest = { deleteStateId = null },
            title = { Text("Delete state?") },
            text = { Text("Delete ${selected?.name ?: "this state"} from this Widget Display?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val remaining = states.filterNot { it.id == stateId }
                        states = remaining.ifEmpty { listOf(WidgetDisplayState(name = "State 1")) }
                        deleteStateId = null
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteStateId = null }) { Text("Cancel") }
            },
        )
    }

    if (showStateLimitDialog) {
        AlertDialog(
            onDismissRequest = { showStateLimitDialog = false },
            title = { Text("Maximum states reached") },
            text = { Text("A Widget Display can contain up to 75 states.") },
            confirmButton = {
                TextButton(onClick = { showStateLimitDialog = false }) { Text("OK") }
            },
        )
    }
}

@Composable
private fun BoxCenterIcon(state: WidgetDisplayState) {
    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
        WidgetIconImage(
            iconKey = state.elements.firstOrNull { it.type == WidgetDisplayElementType.ICON }?.icon ?: "bolt",
            contentDescription = null,
            tint = Color(state.accentColor),
            modifier = Modifier.size(22.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetDisplayStateEditor(
    state: WidgetDisplayState,
    actions: List<Profile>,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    onStateChange: (WidgetDisplayState) -> Unit,
    onSaveState: () -> Unit,
) {
    val context = LocalContext.current
    var selectingActionFor by rememberSaveable(state.id) { mutableStateOf<String?>(null) }
    var selectingIconFor by rememberSaveable(state.id) { mutableStateOf<String?>(null) }

    var selectingImageFor by rememberSaveable(state.id) { mutableStateOf<String?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            selectingImageFor?.let { elementId ->
                onStateChange(
                    state.copy(elements = state.elements.replaceElement(elementId) {
                        it.copy(imageUri = uri.toString())
                    }),
                )
                selectingImageFor = null
            }
        }
    }

    fun addElement(type: WidgetDisplayElementType) {
        val item = WidgetDisplayElement(
            type = type,
            text = when (type) {
                WidgetDisplayElementType.TEXT -> "Your text"
                WidgetDisplayElementType.IMAGE -> "Image caption"
                WidgetDisplayElementType.BUTTON -> "Run Action"
                WidgetDisplayElementType.SHORTCUT -> "Widget shortcut"
                WidgetDisplayElementType.ICON -> "Icon label"
                WidgetDisplayElementType.PROGRESS -> "Progress"
                else -> "",
            },
        )
        onStateChange(state.copy(elements = state.elements + item))
    }

    val canSave = state.name.isNotBlank() && state.elements.all { element ->
        when (element.type) {
            WidgetDisplayElementType.BUTTON, WidgetDisplayElementType.SHORTCUT -> (element.actionProfileId ?: 0L) > 0L
            WidgetDisplayElementType.IMAGE -> SceneImageLoader.isSupportedSource(element.imageUri)
            else -> true
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = state.name,
            onValueChange = { onStateChange(state.copy(name = it)) },
            label = { Text("State name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.title,
            onValueChange = { onStateChange(state.copy(title = it)) },
            label = { Text("Display title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.subtitle,
            onValueChange = { onStateChange(state.copy(subtitle = it)) },
            label = { Text("Subtitle") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Horizontal layout", fontWeight = FontWeight.Medium)
                Text(
                    "Arrange content side by side instead of in a column.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = state.horizontal, onCheckedChange = { onStateChange(state.copy(horizontal = it)) })
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
            Switch(checked = state.gradient, onCheckedChange = { onStateChange(state.copy(gradient = it)) })
        }
        OutlinedTextField(
            value = state.backgroundColor.toHexColor(),
            onValueChange = { raw -> parseDisplayColor(raw)?.let { onStateChange(state.copy(backgroundColor = it)) } },
            label = { Text("Background color (HEX)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.textColor.toHexColor(),
            onValueChange = { raw -> parseDisplayColor(raw)?.let { onStateChange(state.copy(textColor = it)) } },
            label = { Text("Text color (HEX)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.accentColor.toHexColor(),
            onValueChange = { raw -> parseDisplayColor(raw)?.let { onStateChange(state.copy(accentColor = it)) } },
            label = { Text("Accent color (HEX)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Add content", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            addTypes.forEach { (label, type) ->
                OutlinedButton(onClick = { addElement(type) }) { Text(label) }
            }
        }

        state.elements.forEachIndexed { index, element ->
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
                        Text(element.type.displayLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        TextButton(
                            onClick = {
                                onStateChange(state.copy(elements = state.elements.toMutableList().also { it.removeAt(index) }))
                            },
                        ) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                    }
                    when (element.type) {
                        WidgetDisplayElementType.TEXT,
                        WidgetDisplayElementType.IMAGE,
                        WidgetDisplayElementType.BUTTON,
                        WidgetDisplayElementType.SHORTCUT,
                        WidgetDisplayElementType.ICON,
                        WidgetDisplayElementType.PROGRESS -> {
                            OutlinedTextField(
                                value = element.text,
                                onValueChange = { text ->
                                    onStateChange(state.copy(elements = state.elements.replaceElement(element.id) { it.copy(text = text) }))
                                },
                                label = {
                                    Text(
                                        when (element.type) {
                                            WidgetDisplayElementType.TEXT -> "Text"
                                            WidgetDisplayElementType.IMAGE -> "Image caption"
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

                    if (element.type == WidgetDisplayElementType.IMAGE) {
                        OutlinedButton(
                            onClick = {
                                selectingImageFor = element.id
                                imagePicker.launch(arrayOf("image/*"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (element.imageUri.isBlank()) "Choose image" else "Change image") }
                        if (element.imageUri.isNotBlank()) {
                            Text("Image selected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    if (element.type in setOf(WidgetDisplayElementType.BUTTON, WidgetDisplayElementType.SHORTCUT)) {
                        val selectedAction = actions.firstOrNull { it.id == element.actionProfileId }
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
                    }

                    if (element.type in setOf(WidgetDisplayElementType.BUTTON, WidgetDisplayElementType.SHORTCUT, WidgetDisplayElementType.ICON)) {
                        OutlinedButton(
                            onClick = { selectingIconFor = element.id },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            WidgetIconImage(element.icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Choose icon")
                        }
                    }

                    if (element.type == WidgetDisplayElementType.TEXT) {
                        OutlinedTextField(
                            value = element.textSizeSp.toString(),
                            onValueChange = { raw ->
                                val parsed = raw.toIntOrNull()?.coerceIn(10, 36) ?: return@OutlinedTextField
                                onStateChange(state.copy(elements = state.elements.replaceElement(element.id) { it.copy(textSizeSp = parsed) }))
                            },
                            label = { Text("Text size (10–36 sp)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (element.type == WidgetDisplayElementType.PROGRESS) {
                        OutlinedTextField(
                            value = element.progress.toString(),
                            onValueChange = { raw ->
                                val parsed = raw.toIntOrNull()?.coerceIn(0, 100) ?: return@OutlinedTextField
                                onStateChange(state.copy(elements = state.elements.replaceElement(element.id) { it.copy(progress = parsed) }))
                            },
                            label = { Text("Progress (0–100)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (index > 0) {
                            TextButton(
                                onClick = {
                                    val copy = state.elements.toMutableList()
                                    val moved = copy.removeAt(index)
                                    copy.add(index - 1, moved)
                                    onStateChange(state.copy(elements = copy))
                                },
                            ) { Text("Move up") }
                        }
                        if (index < state.elements.lastIndex) {
                            TextButton(
                                onClick = {
                                    val copy = state.elements.toMutableList()
                                    val moved = copy.removeAt(index)
                                    copy.add(index + 1, moved)
                                    onStateChange(state.copy(elements = copy))
                                },
                            ) { Text("Move down") }
                        }
                    }
                }
            }
        }

        Text("Preview", style = MaterialTheme.typography.titleMedium)
        WidgetDisplayPreview(state)

        Button(onClick = onSaveState, enabled = canSave, modifier = Modifier.fillMaxWidth()) {
            Text("Save state")
        }
    }

    selectingActionFor?.let { elementId ->
        AlertDialog(
            onDismissRequest = { selectingActionFor = null },
            title = { Text("Choose an Action") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().height(320.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    actions.forEach { action ->
                        TextButton(
                            onClick = {
                                onStateChange(
                                    state.copy(elements = state.elements.replaceElement(elementId) {
                                        it.copy(actionProfileId = action.id, icon = WidgetIconCatalog.resolveKey(action.icon))
                                    }),
                                )
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
                    if (actions.isEmpty()) Text("Create an Action first. It will appear here as a widget shortcut.")
                }
            },
            confirmButton = { TextButton(onClick = { selectingActionFor = null }) { Text("Close") } },
        )
    }

    selectingIconFor?.let { elementId ->
        val selected = state.elements.firstOrNull { it.id == elementId }
        if (selected != null) {
            ActionIconPickerDialog(
                selectedIcon = selected.icon,
                onIconSelected = { icon ->
                    onStateChange(state.copy(elements = state.elements.replaceElement(elementId) { it.copy(icon = icon) }))
                    selectingIconFor = null
                },
                onDismiss = { selectingIconFor = null },
            )
        }
    }
}

@Composable
private fun WidgetDisplayPreview(state: WidgetDisplayState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(state.backgroundColor),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.title.isNotBlank()) Text(state.title, color = Color(state.textColor), fontWeight = FontWeight.Bold)
            if (state.subtitle.isNotBlank()) Text(state.subtitle, color = Color(state.textColor).copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
            if (state.horizontal) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    state.elements.take(6).forEach { WidgetDisplayPreviewItem(it, state) }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.elements.take(6).forEach { WidgetDisplayPreviewItem(it, state) }
                }
            }
        }
    }
}

@Composable
private fun WidgetDisplayPreviewItem(item: WidgetDisplayElement, state: WidgetDisplayState) {
    val foreground = Color(state.textColor)
    val accent = Color(state.accentColor)
    when (item.type) {
        WidgetDisplayElementType.TEXT -> Text(item.text, color = foreground, fontSize = item.textSizeSp.sp)
        WidgetDisplayElementType.IMAGE -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("IMAGE", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(7.dp))
            Text(item.text.ifBlank { "Image" }, color = foreground)
        }
        WidgetDisplayElementType.BUTTON,
        WidgetDisplayElementType.SHORTCUT -> Surface(color = accent.copy(alpha = 0.25f), shape = RoundedCornerShape(10.dp)) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                WidgetIconImage(item.icon, null, tint = accent, modifier = Modifier.size(16.dp))
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
    "Image" to WidgetDisplayElementType.IMAGE,
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
        WidgetDisplayElementType.IMAGE -> "Image"
        WidgetDisplayElementType.BUTTON -> "Action button"
        WidgetDisplayElementType.SHORTCUT -> "Widget shortcut"
        WidgetDisplayElementType.ICON -> "Icon"
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
