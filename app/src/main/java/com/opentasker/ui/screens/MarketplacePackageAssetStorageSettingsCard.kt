package com.opentasker.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.opentasker.core.marketplace.MarketplacePackageAssetStorage

@Composable
internal fun MarketplacePackageAssetStorageSettingsCard() {
    val context = LocalContext.current
    var selectedTree by remember { mutableStateOf(MarketplacePackageAssetStorage.selectedTreeUri(context)) }
    var message by remember { mutableStateOf<String?>(null) }
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching { MarketplacePackageAssetStorage.setTreeUri(context, uri) }
                .onSuccess {
                    selectedTree = uri
                    message = "Marketplace package files will be stored in your selected folder."
                }
                .onFailure { error ->
                    message = error.message ?: "Couldn't save access to that folder."
                }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Package content storage", style = MaterialTheme.typography.titleMedium)
            Text(
                "Images and audio included with Marketplace packages are copied here when you install them. Your choice affects future installs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Current location: " + MarketplacePackageAssetStorage.locationDescription(context),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { folderPicker.launch(selectedTree) }) {
                    Text(if (selectedTree == null) "Choose folder" else "Change folder")
                }
                if (selectedTree != null) {
                    TextButton(onClick = {
                        MarketplacePackageAssetStorage.useAppStorage(context)
                        selectedTree = null
                        message = "New Marketplace files will use app-private storage."
                    }) { Text("Use app storage") }
                }
            }
            message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
