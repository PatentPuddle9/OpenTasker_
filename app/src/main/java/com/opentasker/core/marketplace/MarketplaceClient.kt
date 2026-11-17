package com.opentasker.core.marketplace

import android.content.Context
import com.opentasker.core.transfer.OpenTaskerBundle
import com.opentasker.core.transfer.OpenTaskerBundleCodec
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
internal data class MarketplaceCatalog(
    val schemaVersion: Int,
    val marketplaceId: String,
    val generatedAt: String = "",
    val packages: List<MarketplacePackage> = emptyList(),
)

@Serializable
internal data class MarketplacePackage(
    val id: String,
    val name: String,
    val summary: String,
    val description: String = "",
    val tags: List<String> = emptyList(),
    val icon: String = "bolt",
    val iconColor: String = "#FFFFFF",
    val backgroundColor: String = "#6750A4",
    val gradient: Boolean = true,
    val shape: String = "squircle",
    val changelog: String = "",
    val documentationUrl: String = "",
    val category: String,
    val publisher: String,
    val version: String,
    val minimumAppVersion: String,
    val riskLevel: String,
    val reviewStatus: String,
    val reviewedAt: String? = null,
    val reviewedBy: List<String> = emptyList(),
    val actionIds: List<String> = emptyList(),
    val requiredInputs: List<MarketplaceInput> = emptyList(),
    val externalDestinations: List<MarketplaceExternalDestination> = emptyList(),
    val bundlePath: String,
    val bundleSha256: String,
    val previewPath: String = "",
    val previewSha256: String = "",
    val iconAssetPath: String = "",
    val iconAssetSha256: String = "",
    val assets: List<MarketplacePackageAsset> = emptyList(),
    val featured: Boolean = false,
    val ratingAverage: Double = 0.0,
    val ratingCount: Int = 0,
    val ratingVotes: Map<String, Int> = emptyMap(),
    val ratingDistribution: Map<String, Int> = emptyMap(),
    val reviews: List<MarketplaceReview> = emptyList(),
    val reviewCount: Int = 0,
    val downloadCount: Int = 0,
    val uniqueInstallerCount: Int = 0,
    val weeklyDownloads: Int = 0,
    val publishedAt: String = "",
    val faqs: List<MarketplaceFaq> = emptyList(),
    val dependencies: List<MarketplaceDependency> = emptyList(),
    val screenshots: List<MarketplaceScreenshot> = emptyList(),
)

@Serializable
internal data class MarketplaceReview(
    val reviewId: String,
    val userId: Long,
    val author: String,
    val title: String,
    val body: String,
    val createdAt: String = "",
    val updatedAt: String = "",
    val helpfulCount: Int = 0,
    val helpfulUserIds: List<Long> = emptyList(),
    val verifiedInstaller: Boolean = false,
    val publisherResponse: MarketplacePublisherResponse? = null,
)

@Serializable
internal data class MarketplacePublisherResponse(
    val author: String,
    val body: String,
    val updatedAt: String = "",
)

@Serializable
internal data class MarketplaceFaq(
    val question: String,
    val answer: String,
)

@Serializable
internal data class MarketplaceDependency(
    val id: String,
    val minimumVersion: String,
    val optional: Boolean = false,
)

@Serializable
internal data class MarketplaceScreenshot(
    val path: String,
    val sha256: String,
)

@Serializable
internal data class MarketplacePackageAsset(
    val key: String,
    val path: String,
    val sha256: String,
    val mediaType: String,
    val sizeBytes: Int,
)

@Serializable
internal data class MarketplaceInput(
    val key: String,
    val label: String,
    val path: String,
    val required: Boolean = true,
    val defaultValue: String = "",
    val hint: String = "",
    val selector: String = "TEXT",
)

@Serializable
internal data class MarketplaceExternalDestination(
    val provider: String,
    val domain: String,
    val hostingCountry: String,
    val privacyPolicyUrl: String,
    val dataFields: List<String>,
    val purpose: String,
    val privacyAssessment: String,
    val riskNotes: String = "",
)

internal data class MarketplaceCatalogResult(
    val catalog: MarketplaceCatalog,
    val fromCache: Boolean,
)

internal object MarketplaceClient {
    private const val PREFS_NAME = "opentasker_marketplace_cache"
    private const val CATALOG_CACHE = "verified_catalog"
    private const val SIGNATURE_CACHE = "verified_signature"
    private const val MAX_CATALOG_BYTES = 2 * 1024 * 1024
    private const val MAX_BUNDLE_BYTES = 8 * 1024 * 1024
    private const val MAX_PACKAGE_ASSET_BYTES = 10 * 1024 * 1024
    private val safePackageAssetPath = Regex("""^packages/[a-z0-9][a-z0-9._-]{1,79}/[0-9]+\.[0-9]+\.[0-9]+/assets/[a-z0-9][a-z0-9._-]{0,70}\.(?:png|mp3|ogg|wav|m4a)$""")
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = false
    }
    private val inputToken = Regex("\\{\\{input:([A-Za-z][A-Za-z0-9_-]{0,47})\\}\\}")
    private val safeBundlePath = Regex("^packages/[a-z0-9][a-z0-9._-]{1,79}/[0-9]+\\.[0-9]+\\.[0-9]+/bundle\\.json$")

    suspend fun loadCatalog(context: Context): MarketplaceCatalogResult = withContext(Dispatchers.IO) {
        val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Cache fallback is only for transport failures. Never hide a bad signature, malformed
        // catalogue, or trust-key mismatch behind an older cached catalogue.
        val remoteContent = try {
            val root = MarketplaceTrust.CATALOG_BASE_URL
            readText("$root/catalog/v1/index.json", MAX_CATALOG_BYTES) to
                readText("$root/catalog/v1/index.sig", 16 * 1024)
        } catch (networkError: Exception) {
            val cachedCatalog = preferences.getString(CATALOG_CACHE, null)
            val cachedSignature = preferences.getString(SIGNATURE_CACHE, null)
            if (cachedCatalog != null && cachedSignature != null) {
                verifyCatalogSignature(cachedCatalog, cachedSignature)
                val decoded = json.decodeFromString<MarketplaceCatalog>(cachedCatalog)
                require(decoded.schemaVersion == 1) { "Unsupported marketplace catalogue version." }
                require(decoded.marketplaceId == MarketplaceTrust.OFFICIAL_MARKETPLACE_ID) {
                    "The cached catalogue belongs to a different marketplace."
                }
                return@withContext MarketplaceCatalogResult(decoded, fromCache = true)
            }
            throw networkError
        }

        val (rawCatalog, signature) = remoteContent
        verifyCatalogSignature(rawCatalog, signature)
        val decoded = json.decodeFromString<MarketplaceCatalog>(rawCatalog)
        require(decoded.schemaVersion == 1) { "Unsupported marketplace catalogue version." }
        require(decoded.marketplaceId == MarketplaceTrust.OFFICIAL_MARKETPLACE_ID) {
            "The catalogue belongs to a different marketplace."
        }

        preferences.edit()
            .putString(CATALOG_CACHE, rawCatalog)
            .putString(SIGNATURE_CACHE, signature)
            .apply()
        MarketplaceCatalogResult(decoded, fromCache = false)
    }

    /**
     * Downloads and validates the exact bundle listed by the signed catalogue for a read-only
     * preview. It never imports or executes the package and never substitutes setup inputs.
     */
    suspend fun previewPackage(packageEntry: MarketplacePackage): OpenTaskerBundle =
        withContext(Dispatchers.IO) {
            require(safeBundlePath.matches(packageEntry.bundlePath)) {
                "The signed catalogue contains an invalid package path."
            }
            val rawBytes = readBytes(
                "${MarketplaceTrust.CATALOG_BASE_URL}/${packageEntry.bundlePath}",
                MAX_BUNDLE_BYTES,
            )
            val expectedHash = packageEntry.bundleSha256.lowercase()
            val actualHash = MessageDigest.getInstance("SHA-256")
                .digest(rawBytes)
                .joinToString("") { "%02x".format(it) }
            require(actualHash == expectedHash) {
                "The package checksum does not match the signed catalogue. Preview was stopped."
            }
            OpenTaskerBundleCodec.decode(rawBytes.toString(Charsets.UTF_8))
        }

    suspend fun downloadVerifiedAsset(
        path: String,
        expectedSha256: String,
        maxBytes: Int = MAX_BUNDLE_BYTES,
    ): ByteArray = withContext(Dispatchers.IO) {
        require(
            Regex("""^packages/[a-z0-9][a-z0-9._-]{1,79}/[0-9]+\.[0-9]+\.[0-9]+/(?:bundle\.json|preview\.png|icon\.png|screenshots/[0-9]{2}\.png|assets/[a-z0-9][a-z0-9._-]{0,70}\.(?:png|mp3|ogg|wav|m4a))$""")
                .matches(path),
        ) {
            "The signed catalogue contains an invalid Marketplace asset path."
        }
        require(expectedSha256.matches(Regex("^[a-fA-F0-9]{64}$"))) {
            "The signed catalogue contains an invalid asset checksum."
        }
        val bytes = readBytes("${MarketplaceTrust.CATALOG_BASE_URL}/$path", maxBytes)
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
        require(actual.equals(expectedSha256, ignoreCase = true)) {
            "The package asset checksum does not match the signed catalogue. The download was stopped."
        }
        bytes
    }

    suspend fun downloadRawPackage(packageEntry: MarketplacePackage): ByteArray =
        downloadVerifiedAsset(packageEntry.bundlePath, packageEntry.bundleSha256, MAX_BUNDLE_BYTES)

    suspend fun downloadPreviewImage(packageEntry: MarketplacePackage): ByteArray? =
        if (packageEntry.previewPath.isBlank() || packageEntry.previewSha256.isBlank()) {
            null
        } else {
            downloadVerifiedAsset(packageEntry.previewPath, packageEntry.previewSha256, 2 * 1024 * 1024)
        }

    suspend fun downloadIconAsset(packageEntry: MarketplacePackage): ByteArray =
        downloadVerifiedAsset(packageEntry.iconAssetPath, packageEntry.iconAssetSha256, 15 * 1024 * 1024)

    suspend fun downloadAndConfigure(
        context: Context,
        packageEntry: MarketplacePackage,
        inputValues: Map<String, String>,
    ): String = withContext(Dispatchers.IO) {
        require(safeBundlePath.matches(packageEntry.bundlePath)) {
            "The signed catalogue contains an invalid package path."
        }
        val rawBytes = readBytes(
            "${MarketplaceTrust.CATALOG_BASE_URL}/${packageEntry.bundlePath}",
            MAX_BUNDLE_BYTES,
        )
        val expectedHash = packageEntry.bundleSha256.lowercase()
        val actualHash = MessageDigest.getInstance("SHA-256")
            .digest(rawBytes)
            .joinToString("") { "%02x".format(it) }
        require(actualHash == expectedHash) {
            "The package checksum does not match the signed catalogue. Installation was stopped."
        }

        val rawBundle = rawBytes.toString(Charsets.UTF_8)
        val configuredBundle = substituteRequiredInputs(rawBundle, packageEntry.requiredInputs, inputValues)
        val rewrittenBundle = resolvePackageAssetReferences(context, packageEntry, configuredBundle)
        val bundleObject = json.parseToJsonElement(rewrittenBundle) as? JsonObject
            ?: error("The downloaded package is not a valid OpenTasker bundle.")
        val existingMetadata = bundleObject["metadata"] as? JsonObject ?: JsonObject(emptyMap())
        val taggedMetadata = JsonObject(
            existingMetadata + ("marketplacePackageId" to JsonPrimitive(packageEntry.id)),
        )
        val taggedBundle = JsonObject(bundleObject + ("metadata" to taggedMetadata))
        json.encodeToString(JsonElement.serializer(), taggedBundle)
    }

    suspend fun resolvePackageAssetReferences(
        context: Context,
        packageEntry: MarketplacePackage,
        payloadText: String,
    ): String = withContext(Dispatchers.IO) {
        require(packageEntry.assets.size <= 12) { "This package declares too many content assets." }
        require(packageEntry.assets.map { it.key }.distinct().size == packageEntry.assets.size) {
            "The signed catalogue contains duplicate package asset keys."
        }
        val assetPayloads = packageEntry.assets.map { asset ->
            require(asset.key.matches(Regex("""^asset-(?:0[1-9]|1[0-2])$"""))) {
                "The signed catalogue contains an invalid package asset key."
            }
            require(safePackageAssetPath.matches(asset.path) && asset.sizeBytes in 1..MAX_PACKAGE_ASSET_BYTES) {
                "The signed catalogue contains an invalid package asset declaration."
            }
            require(asset.mediaType in setOf("image/png", "audio/mpeg", "audio/ogg", "audio/wav", "audio/mp4")) {
                "The signed catalogue contains an unsupported package media type."
            }
            val bytes = downloadVerifiedAsset(asset.path, asset.sha256, MAX_PACKAGE_ASSET_BYTES)
            require(bytes.size == asset.sizeBytes) {
                "The package asset ${asset.key} has an unexpected size."
            }
            MarketplacePackageAssetStorage.AssetContent(
                key = asset.key,
                fileName = asset.path.substringAfterLast('/'),
                mediaType = asset.mediaType,
                bytes = bytes,
            )
        }
        val installedAssets = if (assetPayloads.isEmpty()) {
            emptyMap()
        } else {
            MarketplacePackageAssetStorage.installAssets(
                context = context.applicationContext,
                packageId = packageEntry.id,
                version = packageEntry.version,
                assets = assetPayloads,
            )
        }
        replacePackageAssetReferences(payloadText, installedAssets)
    }

    private fun replacePackageAssetReferences(
        bundleText: String,
        installedAssets: Map<String, String>,
    ): String {
        val parsed = json.parseToJsonElement(bundleText)
        val token = Regex("""\{\{asset:([a-z0-9][a-z0-9-]{0,31})}}""")
        fun rewrite(element: JsonElement): JsonElement = when (element) {
            is JsonObject -> JsonObject(element.mapValues { (_, value) -> rewrite(value) })
            is JsonArray -> JsonArray(element.map(::rewrite))
            is JsonPrimitive -> {
                if (!element.isString) element else {
                    val original = element.content
                    require(!original.contains("{{asset:") || token.containsMatchIn(original)) {
                        "The package contains an invalid asset reference."
                    }
                    val replaced = token.replace(original) { match ->
                        installedAssets[match.groupValues[1]]
                            ?: throw IllegalArgumentException(
                                "The package references ${match.value}, but that file was not included.",
                            )
                    }
                    require(!replaced.contains("{{asset:")) {
                        "The package contains an invalid or unresolved asset reference."
                    }
                    JsonPrimitive(replaced)
                }
            }
        }
        return json.encodeToString(JsonElement.serializer(), rewrite(parsed))
    }

    private fun verifyCatalogSignature(rawCatalog: String, signatureText: String) {
        val publicKeyText = MarketplaceTrust.OFFICIAL_PUBLIC_KEY_X509_BASE64
        require(publicKeyText.isNotBlank()) {
            "The official marketplace verification key has not been configured in this app build. Marketplace downloads are disabled."
        }
        val publicKeyBytes = Base64.decode(publicKeyText, Base64.DEFAULT)
        val publicKey = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(publicKeyBytes))
        val signatureBytes = Base64.decode(signatureText.trim(), Base64.DEFAULT)
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(rawCatalog.toByteArray(Charsets.UTF_8))
        require(verifier.verify(signatureBytes)) {
            "Marketplace signature verification failed. The catalogue may have been modified."
        }
    }

    private fun substituteRequiredInputs(
        rawBundle: String,
        inputs: List<MarketplaceInput>,
        values: Map<String, String>,
    ): String {
        val declaredKeys = inputs.mapTo(linkedSetOf()) { it.key }
        val configured = inputs.associate { input ->
            val value = values[input.key] ?: input.defaultValue
            require(!input.required || value.isNotBlank()) {
                "Complete the required field: ${input.label}."
            }
            input.key to value
        }
        val root = json.parseToJsonElement(rawBundle)
        val referencedKeys = inputToken.findAll(rawBundle).map { it.groupValues[1] }.toSet()
        val undeclared = referencedKeys - declaredKeys
        require(undeclared.isEmpty()) {
            "The package contains undeclared setup fields: ${undeclared.sorted().joinToString()}."
        }

        fun replace(element: JsonElement): JsonElement = when (element) {
            is JsonObject -> JsonObject(element.mapValues { (_, child) -> replace(child) })
            is JsonArray -> JsonArray(element.map(::replace))
            is JsonPrimitive -> if (element.isString) {
                val updated = inputToken.replace(element.content) { match ->
                    val key = match.groupValues[1]
                    configured[key] ?: error("Missing package input: $key")
                }
                JsonPrimitive(updated)
            } else {
                element
            }
            else -> element
        }

        val configuredBundle = replace(root)
        val unresolved = inputToken.find(configuredBundle.toString())
        require(unresolved == null) {
            "The package still contains an unresolved setup field."
        }
        return json.encodeToString(JsonElement.serializer(), configuredBundle)
    }

    private fun readText(url: String, maxBytes: Int): String =
        readBytes(url, maxBytes).toString(Charsets.UTF_8)

    private fun readBytes(url: String, maxBytes: Int): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json, text/plain")
            setRequestProperty("User-Agent", "OpenTasker-Marketplace")
        }
        try {
            val status = connection.responseCode
            require(status in 200..299) { "GitHub returned HTTP $status while loading the marketplace." }
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= maxBytes) { "Marketplace response exceeded the allowed size." }
                    output.write(buffer, 0, count)
                }
            }
            return output.toByteArray()
        } finally {
            connection.disconnect()
        }
    }
}
