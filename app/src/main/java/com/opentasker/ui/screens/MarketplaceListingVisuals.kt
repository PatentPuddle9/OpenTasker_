package com.opentasker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opentasker.widget.WidgetIconCatalog

internal fun parseMarketplaceColor(value: String?, fallback: Color): Color {
    val normalized = value.orEmpty().trim().removePrefix("#")
    if (!normalized.matches(Regex("^[A-Fa-f0-9]{6}$"))) return fallback
    return runCatching {
        Color(android.graphics.Color.parseColor("#$normalized"))
    }.getOrDefault(fallback)
}

@Composable
internal fun MarketplaceListingIcon(
    icon: String,
    iconColor: String,
    backgroundColor: String,
    gradient: Boolean,
    shapeKey: String,
    modifier: Modifier = Modifier.size(64.dp),
    iconSize: Dp = 30.dp,
) {
    val background = parseMarketplaceColor(backgroundColor, MaterialTheme.colorScheme.primary)
    val foreground = parseMarketplaceColor(iconColor, MaterialTheme.colorScheme.onPrimary)
    val shape = when (shapeKey) {
        "circle" -> CircleShape
        "square" -> RoundedCornerShape(8.dp)
        "rounded_square" -> RoundedCornerShape(18.dp)
        "teardrop" -> RoundedCornerShape(
            topStart = 28.dp,
            topEnd = 28.dp,
            bottomStart = 12.dp,
            bottomEnd = 28.dp,
        )
        else -> RoundedCornerShape(24.dp)
    }
    val brush = if (gradient) {
        Brush.linearGradient(listOf(background, lerp(background, Color.Black, 0.24f)))
    } else {
        SolidColor(background)
    }

    Box(
        modifier = modifier.clip(shape).background(brush),
        contentAlignment = Alignment.Center,
    ) {
        val glyph = WidgetIconCatalog.resolve(icon)
        Icon(
            painter = painterResource(glyph.drawableRes),
            contentDescription = glyph.label,
            tint = foreground,
            modifier = Modifier.size(iconSize),
        )
    }
}
