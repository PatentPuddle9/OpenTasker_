package com.opentasker.ui.icons

import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.opentasker.widget.CustomWidgetIconStore
import com.opentasker.widget.WidgetIconCatalog

/** Renders a bundled Coolicons vector or a user-uploaded icon in the same icon slot. */
@Composable
fun WidgetIconImage(
    iconKey: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val customId = CustomWidgetIconStore.idFromKey(iconKey)
    if (customId == null) {
        Icon(
            painter = painterResource(WidgetIconCatalog.resolve(iconKey).drawableRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier,
        )
    } else {
        val context = LocalContext.current.applicationContext
        val customIcons by CustomWidgetIconStore.observe(context).collectAsState()
        val customIcon = customIcons.firstOrNull { it.id == customId }
        val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
            initialValue = null,
            customIcon?.id,
        ) {
            value = customIcon?.let {
                CustomWidgetIconStore.loadBitmap(context, it, 256)?.asImageBitmap()
            }
        }
        if (image != null) {
            Image(
                bitmap = image!!,
                contentDescription = contentDescription,
                modifier = modifier,
                colorFilter = if (customIcon?.recolorable == true) {
                    ColorFilter.tint(tint, BlendMode.SrcIn)
                } else {
                    null
                },
            )
        } else {
            Icon(
                painter = painterResource(WidgetIconCatalog.default.drawableRes),
                contentDescription = contentDescription,
                tint = tint,
                modifier = modifier,
            )
        }
    }
}
