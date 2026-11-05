package com.opentasker.widget

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * An image uploaded in Settings for use anywhere the app supports a selectable icon, including popup titles.
 *
 * [key] is what popup actions save, so renaming or recoloring an icon never changes
 * assignments that already use it.
 */
data class CustomWidgetIcon(
    val id: String,
    val name: String,
    val recolorable: Boolean,
) {
    val key: String get() = CustomWidgetIconStore.keyForId(id)
    val fileName: String get() = "$id.img"
}

object CustomWidgetIconStore {
    const val KEY_PREFIX = "custom:"
    private const val PREFERENCES_NAME = "custom_widget_icons"
    private const val PREFERENCES_KEY = "icons_v1"
    private const val MAX_ICONS = 100
    private const val MAX_IMAGE_BYTES = 15L * 1024L * 1024L
    private const val THUMBNAIL_CACHE_BYTES = 12 * 1024 * 1024

    private val state = MutableStateFlow<List<CustomWidgetIcon>>(emptyList())
    private val lock = Any()
    private var observedPreferences: SharedPreferences? = null
    private val bitmapCache = object : LruCache<String, Bitmap>(THUMBNAIL_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.width * value.height * 4
    }

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == PREFERENCES_KEY) {
            state.value = decode(prefs.getString(PREFERENCES_KEY, null))
        }
    }

    fun keyForId(id: String): String = KEY_PREFIX + id

    fun idFromKey(key: String?): String? {
        val raw = key?.takeIf { it.startsWith(KEY_PREFIX) }?.removePrefix(KEY_PREFIX) ?: return null
        return runCatching { UUID.fromString(raw).toString() }
            .getOrNull()
            ?.takeIf { it == raw }
    }

    /** Shared state keeps Settings and every icon picker in sync. */
    fun observe(context: Context): StateFlow<List<CustomWidgetIcon>> {
        val preferences = preferences(context)
        synchronized(lock) {
            if (observedPreferences !== preferences) {
                observedPreferences?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
                observedPreferences = preferences
                state.value = decode(preferences.getString(PREFERENCES_KEY, null))
                preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
            }
        }
        return state.asStateFlow()
    }

    fun find(context: Context, key: String?): CustomWidgetIcon? {
        val id = idFromKey(key) ?: return null
        return observe(context).value.firstOrNull { it.id == id }
    }

    /**
     * Stores only a user-approved square crop. Keeping the crop requirement here as well as in
     * the Settings UI prevents another caller from accidentally saving an uncropped source image.
     */
    suspend fun add(
        context: Context,
        croppedImage: Bitmap,
        name: String,
        recolorable: Boolean,
    ): CustomWidgetIcon = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val cleanedName = name.trim().take(48)
        require(cleanedName.isNotBlank()) { "Give the icon a name first." }
        require(croppedImage.width == croppedImage.height) {
            "Crop the image to a square before saving it as an icon."
        }
        require(croppedImage.width in 1..2048) {
            "The cropped icon dimensions are too large."
        }

        val icons = observe(app).value
        require(icons.size < MAX_ICONS) { "You can save up to $MAX_ICONS popup icons." }

        val id = UUID.randomUUID().toString()
        val target = iconFile(app, id)
        try {
            target.outputStream().buffered().use { output ->
                check(croppedImage.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Could not encode the cropped icon."
                }
            }
            require(target.length() in 1..MAX_IMAGE_BYTES) {
                "The cropped icon could not be saved within the 15 MB limit."
            }
            val icon = CustomWidgetIcon(id = id, name = cleanedName, recolorable = recolorable)
            save(app, icons + icon)
            icon
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    fun update(
        context: Context,
        icon: CustomWidgetIcon,
    ) {
        val cleanedName = icon.name.trim().take(48)
        require(cleanedName.isNotBlank()) { "Give the icon a name first." }
        val icons = observe(context).value
        require(icons.any { it.id == icon.id }) { "That custom icon no longer exists." }
        save(context, icons.map { if (it.id == icon.id) icon.copy(name = cleanedName) else it })
    }

    fun delete(context: Context, id: String) {
        val app = context.applicationContext
        val icons = observe(app).value
        if (icons.none { it.id == id }) return
        save(app, icons.filterNot { it.id == id })
        iconFile(app, id).delete()
        synchronized(bitmapCache) {
            bitmapCache.snapshot().keys.filter { it.startsWith("$id:") }.forEach(bitmapCache::remove)
        }
    }

    suspend fun loadBitmap(
        context: Context,
        icon: CustomWidgetIcon,
        maxDimensionPx: Int = 192,
    ): Bitmap? = withContext(Dispatchers.IO) {
        decodeIconCached(context.applicationContext, icon, maxDimensionPx)
    }

    /** Loads a compact bitmap and applies the supplied tint only when recoloring is enabled. */
    fun loadBitmapForDisplay(
        context: Context,
        key: String?,
        tintColor: Int,
        maxDimensionPx: Int = 192,
    ): Bitmap? {
        val icon = find(context, key) ?: return null
        val bitmap = decodeIconCached(context.applicationContext, icon, maxDimensionPx) ?: return null
        if (!icon.recolorable) return bitmap
        return runCatching {
            val tinted = bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return@runCatching bitmap
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
            }
            Canvas(tinted).drawBitmap(bitmap, 0f, 0f, paint)
            tinted
        }.getOrDefault(bitmap)
    }

    /** Small bitmap used by popup dialogs. */
    fun loadBitmapForPopup(
        context: Context,
        key: String?,
        tintColor: Int,
        maxDimensionPx: Int = 96,
    ): Bitmap? = loadBitmapForDisplay(context, key, tintColor, maxDimensionPx)

    /** RemoteViews cannot render Compose images, so widgets use this bitmap-backed variant. */
    fun loadBitmapForWidget(
        context: Context,
        key: String?,
        tintColor: Int,
        maxDimensionPx: Int = 192,
    ): Bitmap? = loadBitmapForDisplay(context, key, tintColor, maxDimensionPx)

    suspend fun loadPreviewBitmap(
        context: Context,
        source: Uri,
        maxDimensionPx: Int = 128,
    ): Bitmap? = withContext(Dispatchers.IO) {
        decodeUriSampled(context.applicationContext, source, maxDimensionPx)
    }

    /**
     * RemoteViews cannot render Compose images. Return a small bitmap for the home-screen widget,
     * tinting only icons whose owner enabled recolorability.
     */
    private fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private fun save(context: Context, icons: List<CustomWidgetIcon>) {
        val normalized = icons.sortedBy { it.name.lowercase() }
        preferences(context).edit().putString(PREFERENCES_KEY, encode(normalized)).apply()
        state.value = normalized
    }

    private fun decode(raw: String?): List<CustomWidgetIcon> = runCatching {
        val array = JSONArray(raw ?: "[]")
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val validId = idFromKey(keyForId(id)) ?: continue
                val name = item.optString("name").trim().take(48)
                if (name.isBlank()) continue
                add(
                    CustomWidgetIcon(
                        id = validId,
                        name = name,
                        recolorable = item.optBoolean("recolorable", true),
                    ),
                )
            }
        }.distinctBy(CustomWidgetIcon::id)
    }.getOrDefault(emptyList())

    private fun encode(icons: List<CustomWidgetIcon>): String =
        JSONArray().apply {
            icons.forEach { icon ->
                put(
                    JSONObject()
                        .put("id", icon.id)
                        .put("name", icon.name)
                        .put("recolorable", icon.recolorable),
                )
            }
        }.toString()

    private fun iconDirectory(context: Context): File =
        File(context.applicationContext.filesDir, "custom_icons").apply {
            if (!exists() && !mkdirs()) throw IOException("Could not create the popup icon folder.")
        }

    private fun iconFile(context: Context, id: String): File =
        File(iconDirectory(context), "$id.img")

    private fun decodeIconCached(
        context: Context,
        icon: CustomWidgetIcon,
        maxDimensionPx: Int,
    ): Bitmap? {
        val file = iconFile(context, icon.id)
        if (!file.isFile) return null
        val targetSize = maxDimensionPx.coerceIn(32, 512)
        val cacheKey = "${icon.id}:${file.lastModified()}:$targetSize"
        // LruCache synchronizes get/put internally. Decode outside its lock so opening a
        // Custom category with several icons does not serialize file reads and bitmap decoding.
        bitmapCache.get(cacheKey)?.let { return it }
        val decoded = decodeFileSampled(file, targetSize) ?: return null
        return bitmapCache.get(cacheKey) ?: decoded.also { bitmapCache.put(cacheKey, it) }
    }

    private fun decodeFileSampled(file: File, maxDimensionPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDimensionPx)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun decodeUriSampled(context: Context, uri: Uri, maxDimensionPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDimensionPx)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    }

    private fun sampleSize(width: Int, height: Int, maxDimensionPx: Int): Int {
        var sample = 1
        while (maxOf(width / sample, height / sample) > maxDimensionPx && sample < (1 shl 15)) {
            sample *= 2
        }
        return sample
    }
}
