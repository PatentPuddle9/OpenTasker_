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

/** Renders a saved custom image or a built-in vector in the same icon slot. */
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

    if (customIcon != null) {
        val bitmap by produceState<Bitmap?>(null, customIcon.id) {
            value = CustomWidgetIconStore.loadBitmap(context, customIcon, 512)
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = ContentScale.Fit,
                colorFilter = if (customIcon.recolorable) ColorFilter.tint(tint, BlendMode.SrcIn) else null,
            )
        } else {
            Icon(
                painter = painterResource(WidgetIconCatalog.default.drawableRes),
                contentDescription = contentDescription,
                tint = tint,
                modifier = modifier,
            )
        }
    } else {
        Icon(
            painter = painterResource(WidgetIconCatalog.resolve(iconKey).drawableRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier,
        )
    }
}
