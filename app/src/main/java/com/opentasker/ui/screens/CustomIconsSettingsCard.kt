package com.opentasker.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.opentasker.ui.theme.DesignSystem
import com.opentasker.widget.CustomWidgetIcon
import com.opentasker.widget.CustomWidgetIconStore
import kotlinx.coroutines.launch

@Composable
internal fun PopupIconsSettingsCard() {
    val context = LocalContext.current.applicationContext
    val icons by CustomWidgetIconStore.observe(context).collectAsState()
    val scope = rememberCoroutineScope()
    var showBuilder by rememberSaveable { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }
    var editingIcon by remember { mutableStateOf<CustomWidgetIcon?>(null) }
    var deletingIcon by remember { mutableStateOf<CustomWidgetIcon?>(null) }
    var editName by remember { mutableStateOf("") }
    var actionError by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        ),
        shape = RoundedCornerShape(DesignSystem.Radii.lg),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Icon builder", style = MaterialTheme.typography.titleMedium)
            Text(
                "Create icons without uploading images. Choose a symbol, shape, colors, gradient, and outline, then save them for Actions, widgets, and popup titles.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    addError = null
                    showBuilder = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !adding,
            ) {
                Text("Create custom icon")
            }

            if (icons.isEmpty()) {
                Text(
                    "Icons you build will appear in the Custom category of the shared icon picker.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("Saved icons", style = MaterialTheme.typography.labelLarge)
                icons.forEach { icon ->
                    PopupIconSettingsRow(
                        icon = icon,
                        onRecolorableChange = { enabled ->
                            scope.launch {
                                runCatching {
                                    CustomWidgetIconStore.update(context, icon.copy(recolorable = enabled))
                                }.onFailure {
                                    actionError = it.message ?: "Could not update the icon."
                                }
                            }
                        },
                        onRename = {
                            editName = icon.name
                            editingIcon = icon
                        },
                        onDelete = { deletingIcon = icon },
                    )
                }
            }
            (actionError ?: addError)?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (showBuilder) {
        IconBuilderDialog(
            onDismiss = { if (!adding) showBuilder = false },
            saving = adding,
            error = addError,
            onSave = { bitmap, name ->
                adding = true
                addError = null
                scope.launch {
                    runCatching {
                        CustomWidgetIconStore.add(
                            context = context,
                            croppedImage = bitmap,
                            name = name,
                            recolorable = false,
                        )
                    }.onSuccess {
                        adding = false
                        showBuilder = false
                    }.onFailure {
                        adding = false
                        addError = it.message ?: "Could not save this icon."
                    }
                }
            },
        )
    }

    editingIcon?.let { icon ->
        AlertDialog(
            onDismissRequest = { editingIcon = null },
            title = { Text("Rename custom icon") },
            text = {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it.take(48) },
                    label = { Text("Icon name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        runCatching {
                            CustomWidgetIconStore.update(context, icon.copy(name = editName))
                        }.onSuccess {
                            editingIcon = null
                        }.onFailure {
                            actionError = it.message ?: "Could not rename this icon."
                        }
                    },
                    enabled = editName.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingIcon = null }) { Text("Cancel") }
            },
        )
    }

    deletingIcon?.let { icon ->
        AlertDialog(
            onDismissRequest = { deletingIcon = null },
            title = { Text("Delete custom icon?") },
            text = {
                Text("“" + icon.name + "” will be removed from the Custom category. Places using it will fall back to the default icon.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        CustomWidgetIconStore.delete(context, icon.id)
                        deletingIcon = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingIcon = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PopupIconSettingsRow(
    icon: CustomWidgetIcon,
    onRecolorableChange: (Boolean) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val previewBitmap by produceState<Bitmap?>(null, icon.id) {
        value = CustomWidgetIconStore.loadBitmap(context, icon, 96)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DesignSystem.Radii.md),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap!!.asImageBitmap(),
                        contentDescription = icon.name,
                        modifier = Modifier.size(42.dp),
                        colorFilter = if (icon.recolorable) {
                            ColorFilter.tint(MaterialTheme.colorScheme.primary, BlendMode.SrcIn)
                        } else {
                            null
                        },
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                    ) {}
                }
                Column(Modifier.weight(1f)) {
                    Text(icon.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (icon.recolorable) "Tint preview · recolorable" else "Original colors · fixed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = icon.recolorable,
                    onCheckedChange = onRecolorableChange,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRename) { Text("Rename") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}
