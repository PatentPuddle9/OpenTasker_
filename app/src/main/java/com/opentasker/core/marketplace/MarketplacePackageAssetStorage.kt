package com.opentasker.core.marketplace

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.FileOutputStream

/** Stores optional Marketplace media in app-private storage or the folder selected in Settings. */
internal object MarketplacePackageAssetStorage {
    private const val PREFS = "marketplace_package_asset_storage"
    private const val TREE_URI = "custom_tree_uri"
    private val safeId = Regex("""^[a-z0-9][a-z0-9._-]{1,79}$""")
    private val safeVersion = Regex("""^[0-9]+\.[0-9]+\.[0-9]+$""")
    private val safeFile = Regex("""^(?:image|audio)-[0-9]{2}\.(?:png|mp3|ogg|wav|m4a)$""")

    internal data class AssetContent(
        val key: String,
        val fileName: String,
        val mediaType: String,
        val bytes: ByteArray,
    )

    fun selectedTreeUri(context: Context): Uri? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(TREE_URI, null)
            ?.let(Uri::parse)

    fun locationDescription(context: Context): String =
        if (selectedTreeUri(context) == null) "App-private storage" else "Your selected folder"

    fun setTreeUri(context: Context, uri: Uri) {
        val app = context.applicationContext
        app.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(TREE_URI, uri.toString())
            .apply()
    }

    fun useAppStorage(context: Context) {
        val app = context.applicationContext
        selectedTreeUri(app)?.let { previous ->
            runCatching {
                app.contentResolver.releasePersistableUriPermission(
                    previous,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(TREE_URI).apply()
    }

    fun installAssets(
        context: Context,
        packageId: String,
        version: String,
        assets: List<AssetContent>,
    ): Map<String, String> {
        require(safeId.matches(packageId) && safeVersion.matches(version)) {
            "The Marketplace package identity is invalid."
        }
        require(assets.size <= 12 && assets.map { it.key }.distinct().size == assets.size) {
            "The Marketplace package asset list is invalid."
        }
        assets.forEach { asset ->
            require(asset.key.matches(Regex("""^asset-[0-9]{2}$""")) && safeFile.matches(asset.fileName)) {
                "The Marketplace package contains an unsafe asset filename."
            }
            require(asset.bytes.isNotEmpty() && asset.bytes.size <= 10 * 1024 * 1024) {
                "A Marketplace package asset is outside the permitted size limit."
            }
            require(
                (asset.fileName.endsWith(".png") && asset.mediaType == "image/png") ||
                    (asset.fileName.endsWith(".mp3") && asset.mediaType == "audio/mpeg") ||
                    (asset.fileName.endsWith(".ogg") && asset.mediaType == "audio/ogg") ||
                    (asset.fileName.endsWith(".wav") && asset.mediaType == "audio/wav") ||
                    (asset.fileName.endsWith(".m4a") && asset.mediaType == "audio/mp4"),
            ) { "A Marketplace package asset has an invalid media type." }
        }

        val app = context.applicationContext
        val tree = selectedTreeUri(app)
        return if (tree == null) {
            installIntoAppStorage(app, packageId, version, assets)
        } else {
            installIntoSelectedFolder(app, tree, packageId, version, assets)
        }
    }

    private fun installIntoAppStorage(
        context: Context,
        packageId: String,
        version: String,
        assets: List<AssetContent>,
    ): Map<String, String> {
        val directory = File(context.filesDir, "marketplace-package-assets/$packageId/$version")
        require(directory.mkdirs() || directory.isDirectory) {
            "OpenTasker couldn't create its private Marketplace media folder."
        }
        return assets.associate { asset ->
            val destination = File(directory, asset.fileName)
            val temporary = File(directory, asset.fileName + ".tmp")
            FileOutputStream(temporary).use { it.write(asset.bytes) }
            if (destination.exists() && !destination.delete()) {
                temporary.delete()
                throw IllegalStateException("OpenTasker couldn't replace an existing package asset.")
            }
            if (!temporary.renameTo(destination)) {
                temporary.delete()
                throw IllegalStateException("OpenTasker couldn't save a Marketplace package asset.")
            }
            asset.key to destination.absolutePath
        }
    }

    private fun installIntoSelectedFolder(
        context: Context,
        treeUri: Uri,
        packageId: String,
        version: String,
        assets: List<AssetContent>,
    ): Map<String, String> {
        val resolver = context.contentResolver
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootId)
        return assets.associate { asset ->
            val name = "opentasker-$packageId-$version-${asset.fileName}"
            val existingUri = resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                var found: Uri? = null
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameColumn) == name) {
                        found = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idColumn))
                        break
                    }
                }
                found
            }
            val target = existingUri ?: DocumentsContract.createDocument(
                resolver,
                rootUri,
                asset.mediaType,
                name,
            ) ?: throw IllegalStateException("The selected folder refused to create a package asset.")
            val output = resolver.openOutputStream(target, "wt")
                ?: resolver.openOutputStream(target, "w")
                ?: throw IllegalStateException("The selected folder couldn't open a package asset for writing.")
            output.use { it.write(asset.bytes) }
            asset.key to target.toString()
        }
    }
}
