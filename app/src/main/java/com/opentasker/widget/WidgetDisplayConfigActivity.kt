package com.opentasker.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.opentasker.ui.theme.OpenTaskerTheme
import com.opentasker.ui.theme.ThemeMode
import com.opentasker.ui.theme.ThemePreference

class WidgetDisplayConfigActivity : ComponentActivity() {
    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(RESULT_CANCELED)
        widgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID ||
            !WidgetDisplayProvider.isWidgetId(this, widgetId)
        ) {
            finish()
            return
        }

        val manager = AppWidgetManager.getInstance(this)
        val provider = manager.getAppWidgetInfo(widgetId)?.provider?.className
        val variant = WidgetDisplayProvider.Size.entries.firstOrNull {
            it.providerClass.name == provider
        } ?: WidgetDisplayProvider.Size.CUSTOM

        setContent {
            val themeMode by ThemePreference.observe(this).collectAsState(initial = ThemeMode.System)
            OpenTaskerTheme(themeMode) {
                WidgetDisplayConfigEditor(
                    widgetId = widgetId,
                    initialConfig = WidgetDisplayStore.load(this, widgetId),
                    variantLabel = variant.label,
                    onSave = { config ->
                        runCatching {
                            WidgetDisplayStore.save(this, widgetId, config)
                            WidgetDisplayRenderer.updateWidget(
                                this,
                                AppWidgetManager.getInstance(this),
                                widgetId,
                            )
                        }.onSuccess {
                            setResult(
                                RESULT_OK,
                                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                            )
                            finish()
                        }
                    },
                    onCancel = { finish() },
                )
            }
        }
    }
}
