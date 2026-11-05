package com.opentasker.ui.icons

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.opentasker.widget.WidgetIconCatalog

/** Renders a bundled Coolicons vector for Actions, profile cards, and home-screen widgets. */
@Composable
fun WidgetIconImage(
    iconKey: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Icon(
        painter = painterResource(WidgetIconCatalog.resolve(iconKey).drawableRes),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier,
    )
}
