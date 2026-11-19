package com.opentasker.ui.theme

import androidx.compose.ui.unit.dp

/**
 * OpenTasker design system: a calm command-center interface with quiet surfaces, tight rhythm,
 * and a clear reading order.
 */
object DesignSystem {
    object Spacing {
        val xs = 4.dp
        val sm = 8.dp
        val md = 12.dp
        val lg = 16.dp
        val xl = 20.dp
        val xxl = 32.dp
    }

    object Screen {
        val horizontalPadding = 18.dp
        val verticalPadding = 18.dp
        val cardPadding = 14.dp
        val heroCardPadding = 20.dp
        val sectionGap = 24.dp
        val cardGap = 10.dp
    }

    object Radii {
        val xs = 2.dp
        val sm = 4.dp
        val md = 7.dp
        val lg = 10.dp
        val xl = 12.dp
        val xxl = 16.dp
    }

    object Elevation {
        val none = 0.dp
        val sm = 1.dp
        val md = 2.dp
        val lg = 4.dp
        val xl = 8.dp
    }

    object ComponentSize {
        val buttonSmall = 36.dp
        val buttonMedium = 44.dp
        val buttonLarge = 48.dp
        val buttonXLarge = 52.dp

        val iconSmall = 16.dp
        val iconMedium = 20.dp
        val iconStandard = 24.dp
        val iconLarge = 32.dp
        val iconXLarge = 48.dp

        val touchTargetMin = 48.dp
        val listItemHeight = 60.dp
        val checkboxSize = 24.dp
        val statusIndicator = 8.dp
        val compactControlHeight = 40.dp
        val cardActionHeight = 40.dp
    }

    object SemanticColor {
        val warningDark = androidx.compose.ui.graphics.Color(0xFFFFC66D)
        // Text-grade amber chosen for readable warnings on deep surfaces.
        val warningLight = androidx.compose.ui.graphics.Color(0xFF72520A)
    }

    object Opacity {
        val disabled = 0.38f
        val secondary = 0.72f
        val tertiary = 0.52f
        val hintText = 0.64f
        val elevatedSurface = 0.96f
        val restingSurface = 0.88f
        val selectedSurface = 0.12f
        val subtleBorder = 0.42f
    }
}
