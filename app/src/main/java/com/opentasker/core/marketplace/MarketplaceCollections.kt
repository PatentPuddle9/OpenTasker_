package com.opentasker.core.marketplace

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class MarketplaceCollection(
    val name: String,
    val packageIds: Set<String>,
)

internal object MarketplaceCollections {
    private const val PREFERENCES = "opentasker_marketplace_collections"
    private const val KEY = "collections"

    fun load(context: Context): List<MarketplaceCollection> {
        val raw = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val ids = item.optJSONArray("packageIds") ?: JSONArray()
                    add(
                        MarketplaceCollection(
                            name = item.optString("name").trim(),
                            packageIds = buildSet {
                                for (idIndex in 0 until ids.length()) {
                                    ids.optString(idIndex).takeIf { it.isNotBlank() }?.let(::add)
                                }
                            },
                        ),
                    )
                }
            }.filter { it.name.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    fun create(context: Context, name: String): List<MarketplaceCollection> {
        val cleanName = name.trim()
        require(cleanName.length in 1..32) { "Collection names must be 1–32 characters." }
        val existing = load(context)
        require(existing.none { it.name.equals(cleanName, ignoreCase = true) }) {
            "A collection with that name already exists."
        }
        return (existing + MarketplaceCollection(cleanName, emptySet())).also {
            persist(context, it)
        }
    }

    fun addPackage(
        context: Context,
        collectionName: String,
        packageId: String,
    ): List<MarketplaceCollection> {
        val current = load(context)
        require(current.any { it.name == collectionName }) { "That collection no longer exists." }
        return current.map { collection ->
            if (collection.name == collectionName) {
                collection.copy(packageIds = collection.packageIds + packageId)
            } else collection
        }.also { persist(context, it) }
    }

    fun remove(context: Context, collectionName: String): List<MarketplaceCollection> =
        load(context).filterNot { it.name == collectionName }.also { persist(context, it) }

    private fun persist(context: Context, collections: List<MarketplaceCollection>) {
        val array = JSONArray()
        collections.forEach { collection ->
            array.put(
                JSONObject()
                    .put("name", collection.name)
                    .put("packageIds", JSONArray(collection.packageIds.sorted())),
            )
        }
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, array.toString())
            .apply()
    }
}
