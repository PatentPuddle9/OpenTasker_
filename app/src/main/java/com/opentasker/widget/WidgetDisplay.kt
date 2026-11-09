package com.opentasker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import com.opentasker.core.scenes.SceneImageLoader
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
enum class WidgetDisplayElementType {
    TEXT, IMAGE, BUTTON, SHORTCUT, ICON, PROGRESS, DIVIDER, SPACER,
}

@Serializable
data class WidgetDisplayElement(
    val id: String = UUID.randomUUID().toString(),
    val type: WidgetDisplayElementType,
    val text: String = "",
    val actionProfileId: Long? = null,
    val icon: String = "bolt",
    val imageUri: String = "",
    val progress: Int = 50,
    val textSizeSp: Int = 14,
    /** Top-left position and size in the editor's 320 × 200 dp design canvas. */
    val xDp: Int = -1,
    val yDp: Int = -1,
    val widthDp: Int = 136,
    val heightDp: Int = 40,
)

@Serializable
data class WidgetDisplayState(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "State",
    val title: String = "Widget Display",
    val subtitle: String = "",
    val backgroundColor: Int = 0xFF30243F.toInt(),
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val accentColor: Int = 0xFFB58BEF.toInt(),
    val gradient: Boolean = true,
    val horizontal: Boolean = false,
    val elements: List<WidgetDisplayElement> = emptyList(),
)

@Serializable
data class WidgetDisplayConfig(
    val states: List<WidgetDisplayState> = listOf(WidgetDisplayState(name = "State 1")),
)

object WidgetDisplayCanvasMetrics {
    const val WIDTH_DP = 320
    const val HEIGHT_DP = 200
    const val MAX_ELEMENTS = 8
    const val DEFAULT_WIDTH_DP = 136
    const val DEFAULT_HEIGHT_DP = 40
    private const val OUTER_PADDING_DP = 12
    private const val COLUMN_GAP_DP = 24
    private const val ROW_GAP_DP = 8

    fun defaultBounds(index: Int): com.opentasker.core.scenes.CanvasElementBounds {
        val slot = index.coerceIn(0, MAX_ELEMENTS - 1)
        val column = slot % 2
        val row = slot / 2
        return com.opentasker.core.scenes.CanvasElementBounds(
            x = OUTER_PADDING_DP + column * (DEFAULT_WIDTH_DP + COLUMN_GAP_DP),
            y = OUTER_PADDING_DP + row * (DEFAULT_HEIGHT_DP + ROW_GAP_DP),
            width = DEFAULT_WIDTH_DP,
            height = DEFAULT_HEIGHT_DP,
        )
    }

    /** Positions widgets created by older builds in a tidy two-column starter layout. */
    fun normalize(elements: List<WidgetDisplayElement>): List<WidgetDisplayElement> =
        elements.mapIndexed { index, element ->
            if (element.xDp < 0 || element.yDp < 0 || element.widthDp <= 0 || element.heightDp <= 0) {
                val bounds = defaultBounds(index)
                element.copy(
                    xDp = bounds.x,
                    yDp = bounds.y,
                    widthDp = bounds.width,
                    heightDp = bounds.height,
                )
            } else {
                val width = element.widthDp.coerceIn(32, WIDTH_DP)
                val height = element.heightDp.coerceIn(24, HEIGHT_DP)
                element.copy(
                    xDp = element.xDp.coerceIn(0, WIDTH_DP - width),
                    yDp = element.yDp.coerceIn(0, HEIGHT_DP - height),
                    widthDp = width,
                    heightDp = height,
                )
            }
        }
}
object WidgetDisplayStore {
    const val MAX_STATES = 75
    private const val PREFS = "opentasker_widget_display_prefs"
    fun key(widgetId: Int) = "widget_display_${widgetId}_config"
    private fun activeStateKey(widgetId: Int) = "widget_display_${widgetId}_active_state"
    private fun removedKey(widgetId: Int) = "widget_display_${widgetId}_removed"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(context: Context, widgetId: Int): WidgetDisplayConfig {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(widgetId), null)
            ?: return WidgetDisplayConfig()
        return runCatching {
            json.decodeFromString<WidgetDisplayConfig>(raw)
                .let { config ->
                    config.copy(
                        states = config.states.take(MAX_STATES).ifEmpty {
                            listOf(WidgetDisplayState(name = "State 1"))
                        }.map { state ->
                            state.copy(elements = WidgetDisplayCanvasMetrics.normalize(state.elements))
                        },
                    )
                }
        }.getOrElse { WidgetDisplayConfig() }
    }

    fun save(context: Context, widgetId: Int, config: WidgetDisplayConfig) {
        require(config.states.isNotEmpty()) { "A Widget Display needs at least one state." }
        require(config.states.size <= MAX_STATES) { "A Widget Display can contain at most $MAX_STATES states." }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key(widgetId), json.encodeToString(config))
            .putInt(activeStateKey(widgetId), activeIndex(context, widgetId, config.states.size))
            .putBoolean(removedKey(widgetId), false)
            .apply()
    }

    fun activeIndex(context: Context, widgetId: Int, stateCount: Int = load(context, widgetId).states.size): Int {
        val index = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(activeStateKey(widgetId), 0)
        return index.coerceIn(0, (stateCount - 1).coerceAtLeast(0))
    }

    fun setActiveState(context: Context, widgetId: Int, index: Int): Boolean {
        val config = load(context, widgetId)
        if (index !in config.states.indices) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(activeStateKey(widgetId), index)
            .apply()
        return true
    }

    fun moveState(context: Context, widgetId: Int, delta: Int): Boolean {
        val config = load(context, widgetId)
        val current = activeIndex(context, widgetId, config.states.size)
        val next = (current + delta + config.states.size) % config.states.size
        if (next == current) return false
        return setActiveState(context, widgetId, next)
    }

    fun isRemoved(context: Context, widgetId: Int): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(removedKey(widgetId), false)

    /** Remove the configuration from OpenTasker while leaving a clear launcher placeholder. */
    fun markRemoved(context: Context, widgetId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(key(widgetId))
            .remove(activeStateKey(widgetId))
            .putBoolean(removedKey(widgetId), true)
            .apply()
    }

    fun clear(context: Context, widgetId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(key(widgetId))
            .remove(activeStateKey(widgetId))
            .remove(removedKey(widgetId))
            .apply()
    }
}

object WidgetDisplayStateController {
    fun control(context: Context, widgetId: Int, operation: String, stateIndex: Int? = null): Boolean {
        if (!WidgetDisplayProvider.isWidgetId(context, widgetId)) return false
        val changed = when (operation.trim().lowercase()) {
            "next" -> WidgetDisplayStore.moveState(context, widgetId, 1)
            "previous", "prev" -> WidgetDisplayStore.moveState(context, widgetId, -1)
            "show", "select" -> stateIndex?.let { WidgetDisplayStore.setActiveState(context, widgetId, it - 1) } ?: false
            else -> false
        }
        if (changed || operation.equals("refresh", ignoreCase = true)) {
            WidgetDisplayRenderer.updateWidget(context, AppWidgetManager.getInstance(context), widgetId)
            return true
        }
        return false
    }
}

/**
 * Renders one page of a configurable home-screen dashboard using RemoteViews-safe layouts.
 * Each button/shortcut targets a top-level Action (Profile ID), never an Action block.
 */
object WidgetDisplayRenderer {
    /**
     * Every widget uses one fixed XML view tree. Updates only change existing text, visibility,
     * image and progress properties; no child RemoteViews are inserted or resized at runtime.
     */
    private const val MAX_SLOTS = 8
    private const val MAX_REMOTE_BITMAP_BYTES = 480 * 1024
    private const val BACKGROUND_WIDTH_PX = 192
    private const val BACKGROUND_HEIGHT_PX = 144
    private const val IMAGE_WIDTH_PX = 160
    private const val IMAGE_HEIGHT_PX = 90
    private const val ICON_MAX_DIMENSION_PX = 40

    private enum class ContentMode {
        CANVAS,
        COMPACT_ROW,
        SQUARE_GRID,
        WIDE_GRID,
        PORTRAIT_GRID,
    }

    private data class SlotIds(
        val root: Int,
        val text: Int,
        val image: Int,
        val action: Int,
        val actionIcon: Int,
        val actionLabel: Int,
        val progress: Int,
        val divider: Int,
        val spacer: Int,
    )

    private val slots = listOf(
        SlotIds(
            com.opentasker.app.R.id.widget_display_slot_1_root,
            com.opentasker.app.R.id.widget_display_slot_1_text,
            com.opentasker.app.R.id.widget_display_slot_1_image,
            com.opentasker.app.R.id.widget_display_slot_1_action,
            com.opentasker.app.R.id.widget_display_slot_1_action_icon,
            com.opentasker.app.R.id.widget_display_slot_1_action_label,
            com.opentasker.app.R.id.widget_display_slot_1_progress,
            com.opentasker.app.R.id.widget_display_slot_1_divider,
            com.opentasker.app.R.id.widget_display_slot_1_spacer,
        ),
        SlotIds(
            com.opentasker.app.R.id.widget_display_slot_2_root,
            com.opentasker.app.R.id.widget_display_slot_2_text,
            com.opentasker.app.R.id.widget_display_slot_2_image,
            com.opentasker.app.R.id.widget_display_slot_2_action,
            com.opentasker.app.R.id.widget_display_slot_2_action_icon,
            com.opentasker.app.R.id.widget_display_slot_2_action_label,
            com.opentasker.app.R.id.widget_display_slot_2_progress,
            com.opentasker.app.R.id.widget_display_slot_2_divider,
            com.opentasker.app.R.id.widget_display_slot_2_spacer,
        ),
        SlotIds(
            com.opentasker.app.R.id.widget_display_slot_3_root,
            com.opentasker.app.R.id.widget_display_slot_3_text,
            com.opentasker.app.R.id.widget_display_slot_3_image,
            com.opentasker.app.R.id.widget_display_slot_3_action,
            com.opentasker.app.R.id.widget_display_slot_3_action_icon,
            com.opentasker.app.R.id.widget_display_slot_3_action_label,
            com.opentasker.app.R.id.widget_display_slot_3_progress,
            com.opentasker.app.R.id.widget_display_slot_3_divider,
            com.opentasker.app.R.id.widget_display_slot_3_spacer,
        ),
        SlotIds(
            com.opentasker.app.R.id.widget_display_slot_4_root,
            com.opentasker.app.R.id.widget_display_slot_4_text,
            com.opentasker.app.R.id.widget_display_slot_4_image,
            com.opentasker.app.R.id.widget_display_slot_4_action,
            com.opentasker.app.R.id.widget_display_slot_4_action_icon,
            com.opentasker.app.R.id.widget_display_slot_4_action_label,
            com.opentasker.app.R.id.widget_display_slot_4_progress,
            com.opentasker.app.R.id.widget_display_slot_4_divider,
            com.opentasker.app.R.id.widget_display_slot_4_spacer,
        ),
        SlotIds(
            com.opentasker.app.R.id.widget_display_slot_5_root,
            com.opentasker.app.R.id.widget_display_slot_5_text,
            com.opentasker.app.R.id.widget_display_slot_5_image,
            com.opentasker.app.R.id.widget_display_slot_5_action,
            com.opentasker.app.R.id.widget_display_slot_5_action_icon,
            com.opentasker.app.R.id.widget_display_slot_5_action_label,
            com.opentasker.app.R.id.widget_display_slot_5_progress,
            com.opentasker.app.R.id.widget_display_slot_5_divider,
            com.opentasker.app.R.id.widget_display_slot_5_spacer,
        ),
        SlotIds(
            com.opentasker.app.R.id.widget_display_slot_6_root,
            com.opentasker.app.R.id.widget_display_slot_6_text,
            com.opentasker.app.R.id.widget_display_slot_6_image,
            com.opentasker.app.R.id.widget_display_slot_6_action,
            com.opentasker.app.R.id.widget_display_slot_6_action_icon,
            com.opentasker.app.R.id.widget_display_slot_6_action_label,
            com.opentasker.app.R.id.widget_display_slot_6_progress,
            com.opentasker.app.R.id.widget_display_slot_6_divider,
            com.opentasker.app.R.id.widget_display_slot_6_spacer,
        ),
        SlotIds(
            com.opentasker.app.R.id.widget_display_slot_7_root,
            com.opentasker.app.R.id.widget_display_slot_7_text,
            com.opentasker.app.R.id.widget_display_slot_7_image,
            com.opentasker.app.R.id.widget_display_slot_7_action,
            com.opentasker.app.R.id.widget_display_slot_7_action_icon,
            com.opentasker.app.R.id.widget_display_slot_7_action_label,
            com.opentasker.app.R.id.widget_display_slot_7_progress,
            com.opentasker.app.R.id.widget_display_slot_7_divider,
            com.opentasker.app.R.id.widget_display_slot_7_spacer,
        ),
        SlotIds(
            com.opentasker.app.R.id.widget_display_slot_8_root,
            com.opentasker.app.R.id.widget_display_slot_8_text,
            com.opentasker.app.R.id.widget_display_slot_8_image,
            com.opentasker.app.R.id.widget_display_slot_8_action,
            com.opentasker.app.R.id.widget_display_slot_8_action_icon,
            com.opentasker.app.R.id.widget_display_slot_8_action_label,
            com.opentasker.app.R.id.widget_display_slot_8_progress,
            com.opentasker.app.R.id.widget_display_slot_8_divider,
            com.opentasker.app.R.id.widget_display_slot_8_spacer,
        )
    )

    fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        try {
            renderWidget(context, manager, widgetId)
        } catch (error: Exception) {
            android.util.Log.e("WidgetDisplay", "Could not render widget $widgetId", error)
            runCatching {
                manager.updateAppWidget(widgetId, createRecoveryViews(context, widgetId))
            }.onFailure {
                android.util.Log.e("WidgetDisplay", "Could not show recovery widget $widgetId", it)
            }
        }
    }

    private fun renderWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        if (WidgetDisplayStore.isRemoved(context, widgetId)) {
            manager.updateAppWidget(widgetId, createRecoveryViews(context, widgetId, removed = true))
            return
        }

        val config = WidgetDisplayStore.load(context, widgetId)
        val stateIndex = WidgetDisplayStore.activeIndex(context, widgetId, config.states.size)
        val state = config.states[stateIndex]
        val options = manager.getAppWidgetOptions(widgetId)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 220).coerceAtLeast(110)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 140).coerceAtLeast(40)
        val mode = when {
            minHeight <= 69 -> ContentMode.COMPACT_ROW
            minWidth < 190 && minHeight >= 70 && minHeight < 180 -> ContentMode.SQUARE_GRID
            minWidth >= 190 && minHeight < 180 -> ContentMode.WIDE_GRID
            minHeight > minWidth * 1.35f -> ContentMode.PORTRAIT_GRID
            else -> ContentMode.CANVAS
        }
        val compact = mode == ContentMode.COMPACT_ROW
        val hideHeader = mode != ContentMode.CANVAS
        val modernCanvas = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val layoutId = if (modernCanvas) {
            com.opentasker.app.R.layout.widget_display
        } else {
            com.opentasker.app.R.layout.widget_display_legacy
        }
        val views = RemoteViews(context.packageName, layoutId)
        val foreground = state.textColor
        val paddingDp = if (compact) 6f else 12f
        val padding = (paddingDp * context.resources.displayMetrics.density).toInt()
        views.setViewPadding(
            com.opentasker.app.R.id.widget_display_panel,
            padding,
            padding,
            padding,
            padding,
        )

        val background = WidgetBackgroundRenderer.render(
            backgroundColor = state.backgroundColor,
            gradient = state.gradient,
            width = BACKGROUND_WIDTH_PX,
            height = BACKGROUND_HEIGHT_PX,
            shape = "rounded_square",
        )
        var bitmapBytes = background.byteCount
        views.setImageViewBitmap(com.opentasker.app.R.id.widget_display_background, background)

        views.setTextViewText(com.opentasker.app.R.id.widget_display_title, state.title)
        views.setTextViewText(com.opentasker.app.R.id.widget_display_subtitle, state.subtitle)
        views.setTextColor(com.opentasker.app.R.id.widget_display_title, foreground)
        views.setTextColor(com.opentasker.app.R.id.widget_display_subtitle, foreground)
        views.setViewVisibility(
            com.opentasker.app.R.id.widget_display_title,
            if (hideHeader || state.title.isBlank()) View.GONE else View.VISIBLE,
        )
        views.setViewVisibility(
            com.opentasker.app.R.id.widget_display_subtitle,
            if (hideHeader || state.subtitle.isBlank()) View.GONE else View.VISIBLE,
        )
        views.setTextViewTextSize(
            com.opentasker.app.R.id.widget_display_title,
            android.util.TypedValue.COMPLEX_UNIT_SP,
            if (compact) 13f else 17f,
        )

        // Small fixed sizes use responsive grids instead of shrinking the 320 × 200 canvas
        // until every item becomes unreadable. Larger widgets retain the exact authored layout.
        val configured = state.elements
        val visibleElements = when (mode) {
            ContentMode.COMPACT_ROW -> configured.take(2)
            ContentMode.SQUARE_GRID -> configured.take(4)
            ContentMode.WIDE_GRID, ContentMode.PORTRAIT_GRID -> configured.take(MAX_SLOTS)
            ContentMode.CANVAS -> if (configured.size > MAX_SLOTS) {
                configured.take(MAX_SLOTS - 1) + WidgetDisplayElement(
                    id = "overflow-" + configured.size,
                    type = WidgetDisplayElementType.TEXT,
                    text = "+ " + (configured.size - (MAX_SLOTS - 1)) + " more items",
                    textSizeSp = 11,
                    xDp = 12,
                    yDp = 172,
                    widthDp = 296,
                    heightDp = 22,
                )
            } else {
                configured
            }
        }

        val contentWidthDp = (minWidth - paddingDp * 2).coerceAtLeast(72f)
        val headerHeightDp = if (hideHeader) 0f else {
            (if (state.title.isBlank()) 0f else 24f) +
                (if (state.subtitle.isBlank()) 0f else 18f) + 7f
        }
        val contentHeightDp = (minHeight - paddingDp * 2 - headerHeightDp).coerceAtLeast(26f)

        if (visibleElements.isEmpty()) {
            val empty = WidgetDisplayElement(
                id = "empty",
                type = WidgetDisplayElementType.TEXT,
                text = "Tap to edit this Widget Display",
                textSizeSp = 12,
                xDp = 8,
                yDp = 8,
                widthDp = WidgetDisplayCanvasMetrics.WIDTH_DP - 16,
                heightDp = 40,
            )
            bindElement(context, views, slots.first(), empty, state, widgetId, foreground, bitmapBytes, compact)
            views.setOnClickPendingIntent(slots.first().root, configPendingIntent(context, widgetId))
            if (modernCanvas) {
                placeSlot(
                    views = views,
                    rootId = slots.first().root,
                    element = empty,
                    contentWidthDp = contentWidthDp,
                    contentHeightDp = contentHeightDp,
                    mode = mode,
                    compactIndex = 0,
                    compactCount = 1,
                )
            }
        } else {
            visibleElements.forEachIndexed { index, element ->
                val slot = slots.getOrNull(index) ?: return@forEachIndexed
                bitmapBytes = bindElement(
                    context, views, slot, element, state, widgetId, foreground, bitmapBytes, compact,
                )
                if (modernCanvas) {
                    placeSlot(
                        views = views,
                        rootId = slot.root,
                        element = element,
                        contentWidthDp = contentWidthDp,
                        contentHeightDp = contentHeightDp,
                        mode = mode,
                        compactIndex = index,
                        compactCount = visibleElements.size,
                    )
                }
            }
        }

        manager.updateAppWidget(widgetId, views)
    }

    /** Places an existing XML slot without constructing or inserting any child RemoteViews. */
    private fun placeSlot(
        views: RemoteViews,
        rootId: Int,
        element: WidgetDisplayElement,
        contentWidthDp: Float,
        contentHeightDp: Float,
        mode: ContentMode,
        compactIndex: Int,
        compactCount: Int,
    ) {
        val unit = android.util.TypedValue.COMPLEX_UNIT_DIP
        if (mode != ContentMode.CANVAS) {
            val columns = when (mode) {
                ContentMode.COMPACT_ROW -> compactCount.coerceAtLeast(1)
                ContentMode.SQUARE_GRID, ContentMode.PORTRAIT_GRID -> 2
                ContentMode.WIDE_GRID -> 4
                ContentMode.CANVAS -> 1
            }
            val rows = when (mode) {
                ContentMode.COMPACT_ROW, ContentMode.WIDE_GRID -> 1
                ContentMode.SQUARE_GRID -> 2
                ContentMode.PORTRAIT_GRID -> 4
                ContentMode.CANVAS -> 1
            }
            val cellWidth = contentWidthDp / columns
            val cellHeight = contentHeightDp / rows
            val width = (cellWidth - 4f).coerceAtLeast(1f)
            val height = (cellHeight - 4f).coerceAtLeast(18f)
            val left = (compactIndex % columns) * cellWidth + 2f
            val top = (compactIndex / columns) * cellHeight + 2f
            views.setViewLayoutWidth(rootId, width, unit)
            views.setViewLayoutHeight(rootId, height, unit)
            views.setViewLayoutMargin(rootId, Gravity.LEFT, left, unit)
            views.setViewLayoutMargin(rootId, Gravity.TOP, top, unit)
            return
        }

        val scaleX = contentWidthDp / WidgetDisplayCanvasMetrics.WIDTH_DP
        val scaleY = contentHeightDp / WidgetDisplayCanvasMetrics.HEIGHT_DP
        val width = (element.widthDp * scaleX).coerceAtLeast(1f)
        val height = (element.heightDp * scaleY).coerceAtLeast(1f)
        val left = (element.xDp * scaleX).coerceIn(0f, (contentWidthDp - width).coerceAtLeast(0f))
        val top = (element.yDp * scaleY).coerceIn(0f, (contentHeightDp - height).coerceAtLeast(0f))
        views.setViewLayoutWidth(rootId, width, unit)
        views.setViewLayoutHeight(rootId, height, unit)
        views.setViewLayoutMargin(rootId, Gravity.LEFT, left, unit)
        views.setViewLayoutMargin(rootId, Gravity.TOP, top, unit)
    }

    private fun bindElement(
        context: Context,
        views: RemoteViews,
        slot: SlotIds,
        element: WidgetDisplayElement,
        state: WidgetDisplayState,
        widgetId: Int,
        foreground: Int,
        alreadyUsedBitmapBytes: Int,
        compact: Boolean,
    ): Int {
        views.setViewVisibility(slot.root, View.VISIBLE)
        return when (element.type) {
            WidgetDisplayElementType.TEXT -> {
                views.setTextViewText(slot.text, element.text)
                views.setTextColor(slot.text, foreground)
                views.setTextViewTextSize(
                    slot.text,
                    android.util.TypedValue.COMPLEX_UNIT_SP,
                    element.textSizeSp.coerceIn(10, 36).toFloat(),
                )
                views.setViewVisibility(slot.text, View.VISIBLE)
                alreadyUsedBitmapBytes
            }

            WidgetDisplayElementType.IMAGE -> {
                val image = SceneImageLoader.load(
                    context = context,
                    source = element.imageUri,
                    targetWidthPx = IMAGE_WIDTH_PX,
                    targetHeightPx = IMAGE_HEIGHT_PX,
                )
                val canDisplayImage = image != null &&
                    alreadyUsedBitmapBytes + image.byteCount <= MAX_REMOTE_BITMAP_BYTES
                if (canDisplayImage) {
                    views.setImageViewBitmap(slot.image, image!!)
                    views.setViewVisibility(slot.image, View.VISIBLE)
                    if (element.text.isNotBlank()) {
                        views.setTextViewText(slot.text, element.text)
                        views.setTextColor(slot.text, foreground)
                        views.setViewVisibility(slot.text, View.VISIBLE)
                    }
                } else {
                    val message = if (image == null) "Image unavailable" else "Image omitted to keep widget stable"
                    views.setTextViewText(slot.text, listOf(message, element.text).filter { it.isNotBlank() }.joinToString(" · "))
                    views.setTextColor(slot.text, foreground)
                    views.setViewVisibility(slot.text, View.VISIBLE)
                }
                alreadyUsedBitmapBytes + if (canDisplayImage) image!!.byteCount else 0
            }

            WidgetDisplayElementType.BUTTON,
            WidgetDisplayElementType.SHORTCUT,
            WidgetDisplayElementType.ICON -> {
                val customIcon = CustomWidgetIconStore.loadBitmapForWidget(
                    context, element.icon, state.accentColor, maxDimensionPx = ICON_MAX_DIMENSION_PX,
                )
                val canDisplayIcon = customIcon != null &&
                    alreadyUsedBitmapBytes + customIcon.byteCount <= MAX_REMOTE_BITMAP_BYTES
                if (canDisplayIcon) {
                    views.setImageViewBitmap(slot.actionIcon, customIcon!!)
                } else {
                    val icon = WidgetIconCatalog.resolve(element.icon)
                    views.setImageViewResource(slot.actionIcon, icon.drawableRes)
                    views.setInt(slot.actionIcon, "setColorFilter", state.accentColor)
                }
                views.setTextViewText(slot.actionLabel, element.text)
                views.setTextColor(slot.actionLabel, foreground)
                if (compact) {
                    views.setTextViewTextSize(
                        slot.actionLabel,
                        android.util.TypedValue.COMPLEX_UNIT_SP,
                        10f,
                    )
                }
                views.setViewVisibility(slot.action, View.VISIBLE)

                if (element.type != WidgetDisplayElementType.ICON) {
                    element.actionProfileId?.takeIf { it > 0L }?.let { profileId ->
                        val runAction = Intent(context, TaskRunActivity::class.java).apply {
                            putExtra(TaskRunActivity.EXTRA_PROFILE_ID, profileId)
                            putExtra(TaskRunActivity.EXTRA_SOURCE, TaskRunActivity.SOURCE_WIDGET_DISPLAY)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        val requestCode = ((widgetId.toLong() shl 20) xor element.id.hashCode().toLong()).toInt()
                        val pending = PendingIntent.getActivity(
                            context,
                            requestCode,
                            runAction,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                        views.setOnClickPendingIntent(slot.action, pending)
                    }
                }
                alreadyUsedBitmapBytes + if (canDisplayIcon) customIcon!!.byteCount else 0
            }

            WidgetDisplayElementType.PROGRESS -> {
                val progress = element.progress.coerceIn(0, 100)
                views.setTextViewText(slot.text, element.text + " · " + progress + "%")
                views.setTextColor(slot.text, foreground)
                views.setViewVisibility(slot.text, View.VISIBLE)
                views.setProgressBar(slot.progress, 100, progress, false)
                views.setViewVisibility(slot.progress, View.VISIBLE)
                alreadyUsedBitmapBytes
            }

            WidgetDisplayElementType.DIVIDER -> {
                views.setViewVisibility(slot.divider, View.VISIBLE)
                alreadyUsedBitmapBytes
            }

            WidgetDisplayElementType.SPACER -> {
                views.setViewVisibility(slot.spacer, View.VISIBLE)
                alreadyUsedBitmapBytes
            }
        }
    }

    private fun configPendingIntent(context: Context, widgetId: Int): PendingIntent {
        val openEditor = Intent(context, WidgetDisplayConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return PendingIntent.getActivity(
            context,
            700_000 + widgetId,
            openEditor,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createRecoveryViews(
        context: Context,
        widgetId: Int,
        removed: Boolean = false,
    ): RemoteViews {
        val recovery = RemoteViews(context.packageName, com.opentasker.app.R.layout.widget_display_fallback)
        recovery.setTextViewText(
            com.opentasker.app.R.id.widget_display_fallback_title,
            if (removed) "Widget removed" else "Widget Display couldn't load",
        )
        recovery.setTextViewText(
            com.opentasker.app.R.id.widget_display_fallback_message,
            if (removed) "Remove this widget from your home screen"
            else "Tap here to reopen setup and repair it",
        )
        if (!removed) {
            recovery.setOnClickPendingIntent(
                com.opentasker.app.R.id.widget_display_fallback_root,
                configPendingIntent(context, widgetId),
            )
        }
        return recovery
    }
}
