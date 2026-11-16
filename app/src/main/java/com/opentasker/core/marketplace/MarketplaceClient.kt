package com.opentasker.core.marketplace

import android.content.Context
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
        val remote = runCatching {
            val root = MarketplaceTrust.CATALOG_BASE_URL
            val rawCatalog = readText("$root/catalog/v1/index.json", MAX_CATALOG_BYTES)
            val signature = readText("$root/catalog/v1/index.sig", 16 * 1024)
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
        remote.getOrElse { remoteError ->
            val cachedCatalog = preferences.getString(CATALOG_CACHE, null)
            val cachedSignature = preferences.getString(SIGNATURE_CACHE, null)
            if (cachedCatalog != null && cachedSignature != null) {
                runCatching {
                    verifyCatalogSignature(cachedCatalog, cachedSignature)
                    val decoded = json.decodeFromString<MarketplaceCatalog>(cachedCatalog)
                    require(decoded.marketplaceId == MarketplaceTrust.OFFICIAL_MARKETPLACE_ID)
                    MarketplaceCatalogResult(decoded, fromCache = true)
                }.getOrElse { throw remoteError }
            } else {
                throw remoteError
            }
        }
    }

    suspend fun downloadAndConfigure(
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
        val bundleObject = json.parseToJsonElement(configuredBundle) as? JsonObject
            ?: error("The downloaded package is not a valid OpenTasker bundle.")
        val existingMetadata = bundleObject["metadata"] as? JsonObject ?: JsonObject(emptyMap())
        val taggedMetadata = JsonObject(
            existingMetadata + ("marketplacePackageId" to JsonPrimitive(packageEntry.id)),
        )
        val taggedBundle = JsonObject(bundleObject + ("metadata" to taggedMetadata))
        json.encodeToString(JsonElement.serializer(), taggedBundle)
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
