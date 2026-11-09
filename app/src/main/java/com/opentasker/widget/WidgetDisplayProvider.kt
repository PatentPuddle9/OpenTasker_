package com.opentasker.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent

open class WidgetDisplayProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { WidgetDisplayRenderer.updateWidget(context, appWidgetManager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        WidgetDisplayRenderer.updateWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetDisplayStore.clear(context, it) }
    }

    enum class Size(
        val label: String,
        val providerClass: Class<out WidgetDisplayProvider>,
    ) {
        COMPACT("Compact · 2 × 1", WidgetDisplayCompactProvider::class.java),
        SQUARE("Square · 2 × 2", WidgetDisplaySquareProvider::class.java),
        WIDE("Wide · 4 × 2", WidgetDisplayWideProvider::class.java),
        LARGE("Large · 4 × 4", WidgetDisplayLargeProvider::class.java),
        CUSTOM("Custom · 3 × 3 · set size in editor", WidgetDisplayCustomProvider::class.java),
    }

    data class Instance(val widgetId: Int, val size: Size)

    companion object {

        fun requestPinWidget(context: Context, size: Size): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            return manager.requestPinAppWidget(ComponentName(context, size.providerClass), null, null)
        }

        fun instances(context: Context): List<Instance> {
            val manager = AppWidgetManager.getInstance(context)
            return Size.entries.flatMap { size ->
                manager.getAppWidgetIds(ComponentName(context, size.providerClass))
                    .filterNot { WidgetDisplayStore.isRemoved(context, it) }
                    .map { Instance(it, size) }
            }
        }

        fun isWidgetId(context: Context, widgetId: Int): Boolean =
            instances(context).any { it.widgetId == widgetId }

        fun updateWidget(context: Context, widgetId: Int) {
            WidgetDisplayRenderer.updateWidget(context, AppWidgetManager.getInstance(context), widgetId)
        }

        fun deleteWidget(context: Context, widgetId: Int) {
            WidgetDisplayStore.markRemoved(context, widgetId)
            updateWidget(context, widgetId)
        }

        fun requestRefresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            Size.entries.forEach { size ->
                val ids = manager.getAppWidgetIds(ComponentName(context, size.providerClass))
                if (ids.isNotEmpty()) {
                    context.sendBroadcast(
                        Intent(context, size.providerClass).apply {
                            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                        },
                    )
                }
            }
        }
    }
}

class WidgetDisplayCompactProvider : WidgetDisplayProvider()
class WidgetDisplaySquareProvider : WidgetDisplayProvider()
class WidgetDisplayWideProvider : WidgetDisplayProvider()
class WidgetDisplayLargeProvider : WidgetDisplayProvider()
class WidgetDisplayCustomProvider : WidgetDisplayProvider()
