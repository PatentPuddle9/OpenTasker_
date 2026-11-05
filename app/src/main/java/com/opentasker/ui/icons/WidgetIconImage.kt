package com.opentasker.ui.icons

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.opentasker.widget.CustomWidgetIconStore
import com.opentasker.widget.WidgetIconCatalog

/**
 * Renders either a bundled Coolicons vector or a user-uploaded image using the same icon key
 * stored by profiles and home-screen widgets.
 */
@Composable
fun WidgetIconImage(
    iconKey: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val context = LocalContext.current.applicationContext
    val customIcons by CustomWidgetIconStore.observe(context).collectAsState()
    val customIcon = customIcons.firstOrNull { it.key == iconKey }

    if (customIcon == null) {
        Icon(
            painter = painterResource(WidgetIconCatalog.resolve(iconKey).drawableRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier,
        )
    } else {
        val bitmap by produceState<Bitmap?>(
            initialValue = null,
            customIcon.id,
            customIcon.fileName,
        ) {
            value = CustomWidgetIconStore.loadBitmap(context, customIcon, maxDimensionPx = 256)
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = ContentScale.Fit,
                colorFilter = if (customIcon.recolorable) {
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
