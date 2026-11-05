package com.opentasker.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.material3.OutlinedButton
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
import com.opentasker.widget.CustomWidgetIcon
import com.opentasker.widget.CustomWidgetIconStore
import com.opentasker.ui.theme.DesignSystem
import kotlinx.coroutines.launch

@Composable
internal fun CustomIconsSettingsCard() {
    val context = LocalContext.current.applicationContext
    val icons by CustomWidgetIconStore.observe(context).collectAsState()
    val scope = rememberCoroutineScope()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var selectedImageUri by rememberSaveable { mutableStateOf<String?>(null) }
    var croppedImage by remember { mutableStateOf<Bitmap?>(null) }
    var showCropDialog by rememberSaveable { mutableStateOf(false) }
    var addName by rememberSaveable { mutableStateOf("") }
    var addRecolorable by rememberSaveable { mutableStateOf(true) }
    var adding by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }
    var editingIcon by remember { mutableStateOf<CustomWidgetIcon?>(null) }
    var deletingIcon by remember { mutableStateOf<CustomWidgetIcon?>(null) }
    var editName by remember { mutableStateOf("") }
    var actionError by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedImageUri = uri.toString()
            croppedImage = null
            addName = ""
            addRecolorable = true
            addError = null
            showCropDialog = true
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)),
        shape = RoundedCornerShape(DesignSystem.Radii.lg),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Custom icons", style = MaterialTheme.typography.titleMedium)
            Text(
                "Upload your own images for use throughout OpenTasker. Custom and built-in icons appear together in the Icons picker.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { imagePicker.launch(arrayOf("image/*")) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !adding,
            ) {
                Text("Upload an icon")
            }
            if (icons.isEmpty()) {
                Text(
                    "Your custom icons will appear beside the built-in icons in the Icons picker.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                icons.forEach { icon ->
                    PopupIconSettingsRow(
                        icon = icon,
                        onRecolorableChange = { enabled ->
                            scope.launch {
                                runCatching {
                                    CustomWidgetIconStore.update(context, icon.copy(recolorable = enabled))
                                    
                                }.onFailure { actionError = it.message ?: "Could not update the icon." }
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
            actionError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (showAddDialog) {
        val uri = selectedImageUri?.let(Uri::parse)
        val originalPreview = croppedImage
        AlertDialog(
            onDismissRequest = { if (!adding) showAddDialog = false },
            title = { Text("Add custom icon") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = addName,
                        onValueChange = { addName = it.take(48) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Icon name") },
                        singleLine = true,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Recolorable", style = MaterialTheme.typography.labelLarge)
                            Text(
                                "When enabled, this image can use the app's selected icon color.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = addRecolorable, onCheckedChange = { addRecolorable = it })
                    }
                    if (originalPreview != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(
                                    bitmap = originalPreview!!.asImageBitmap(),
                                    contentDescription = "Square crop preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(64.dp),
                                )
                                Text("Square crop", style = MaterialTheme.typography.labelSmall)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(
                                    bitmap = originalPreview!!.asImageBitmap(),
                                    contentDescription = "Recolored square icon preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(64.dp),
                                    colorFilter = if (addRecolorable) {
                                        ColorFilter.tint(MaterialTheme.colorScheme.primary, BlendMode.SrcIn)
                                    } else {
                                        null
                                    },
                                )
                                Text(
                                    if (addRecolorable) "Recolored preview" else "Unchanged preview",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                    addError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sourceUri = uri ?: return@Button
                        adding = true
                        scope.launch {
                            runCatching {
                                val image = croppedImage ?: error("Crop the image before saving.")
                                CustomWidgetIconStore.add(context, image, addName, addRecolorable)
                            }.onSuccess {
                                adding = false
                                showAddDialog = false
                                selectedImageUri = null
                                croppedImage = null
                                
                            }.onFailure {
                                adding = false
                                addError = it.message ?: "Could not save this icon."
                            }
                        }
                    },
                    enabled = uri != null && croppedImage != null && addName.isNotBlank() && !adding,
                ) {
                    Text(if (adding) "Saving…" else "Save icon")
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!adding) showAddDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showCropDialog && selectedImageUri != null) {
        SquareImageCropDialog(
            source = Uri.parse(selectedImageUri),
            onDismiss = { showCropDialog = false },
            onCropped = { bitmap ->
                croppedImage = bitmap
                showCropDialog = false
                showAddDialog = true
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
                        runCatching { CustomWidgetIconStore.update(context, icon.copy(name = editName)) }
                            .onSuccess { editingIcon = null }
                            .onFailure { actionError = it.message ?: "Could not rename this icon." }
                    },
                    enabled = editName.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingIcon = null }) { Text("Cancel") } },
        )
    }

    deletingIcon?.let { icon ->
        AlertDialog(
            onDismissRequest = { deletingIcon = null },
            title = { Text("Delete custom icon?") },
            text = {
                Text("“${icon.name}” will be removed from the custom icon library. Actions or widgets using it will fall back to the default icon.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        CustomWidgetIconStore.delete(context, icon.id)
                        
                        deletingIcon = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deletingIcon = null }) { Text("Cancel") } },
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap!!.asImageBitmap(),
                        contentDescription = icon.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(42.dp),
                        colorFilter = if (icon.recolorable) ColorFilter.tint(MaterialTheme.colorScheme.primary, BlendMode.SrcIn) else null,
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
                        if (icon.recolorable) "Tint preview · recolorable" else "Original colors · not recolorable",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = icon.recolorable, onCheckedChange = onRecolorableChange)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRename) { Text("Rename") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}
