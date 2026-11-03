package com.opentasker.core.updates

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Downloads the exact APK asset described by the GitHub release API and verifies its published
 * SHA-256 and package/version metadata, then returns it for Android's user-confirmed installer.
 */
object UpdateApkDownloader {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.MINUTES)
        .followRedirects(true)
        .followSslRedirects(false)
        .build()

    suspend fun download(
        context: Context,
        version: String,
        sourceBranch: String,
        apkUrl: String,
        expectedSizeBytes: Long,
        expectedSha256: String,
        expectedVersionCode: Long? = null,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        require(version.matches(Regex("^\\d+\\.\\d+\\.\\d+$"))) { "Update version is invalid." }
        require(UpdateCheckProtocol.isSelectableBranch(sourceBranch)) {
            "The master branch cannot be used as an update source."
        }
        val releaseAssetUrl = apkUrl.toHttpUrlOrNull()
        require(
            releaseAssetUrl != null &&
                releaseAssetUrl.isHttps &&
                releaseAssetUrl.host == "github.com" &&
                releaseAssetUrl.encodedPath.startsWith("/PatentPuddle9/OpenTasker_/releases/download/") &&
                releaseAssetUrl.encodedPath.count { it == '/' } == 6 &&
                releaseAssetUrl.query == null &&
                releaseAssetUrl.fragment == null
        ) {
            "APK URL is not a release asset from the OpenTasker fork."
        }
        require(expectedSizeBytes > 0L && expectedSizeBytes <= UpdateCheckProtocol.MAX_APK_BYTES) {
            "APK size is outside the allowed limit."
        }
        require(expectedSha256.matches(Regex("^[0-9a-f]{64}$"))) { "APK digest is invalid." }

        val request = Request.Builder()
            .url(apkUrl)
            .header("Accept", "application/octet-stream")
            .get()
            .build()
        val response = client.newCall(request).await()
        try {
            if (!response.isSuccessful) throw IOException("GitHub returned HTTP " + response.code + ".")
            val finalUrl = response.request.url
            val finalHostTrusted = finalUrl.isHttps &&
                (finalUrl.host == "github.com" || finalUrl.host.endsWith(".githubusercontent.com"))
            if (!finalHostTrusted) throw IOException("APK download redirected outside GitHub.")
            val body = response.body ?: throw IOException("GitHub returned an empty APK response.")
            val contentLength = body.contentLength()
            if (contentLength > 0L && contentLength != expectedSizeBytes) {
                throw IOException("Downloaded APK size does not match the release metadata.")
            }

            val updateDirectory = File(context.cacheDir, "update-apks")
            if (!updateDirectory.exists() && !updateDirectory.mkdirs()) {
                throw IOException("Could not create the update download directory.")
            }
            updateDirectory.listFiles()
                ?.filter { it.name.endsWith(".part") }
                ?.forEach { it.delete() }

            val fileName = releaseAssetUrl!!.encodedPath.substringAfterLast('/')
            require(fileName.matches(Regex("^[A-Za-z0-9._-]{1,160}\\.apk$"))) {
                "Release APK filename is invalid."
            }
            val temporaryFile = File(updateDirectory, "$fileName.part")
            val apkFile = File(updateDirectory, fileName)
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L

            try {
                body.byteStream().buffered().use { input ->
                    FileOutputStream(temporaryFile).buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            downloaded += read
                            if (downloaded > expectedSizeBytes || downloaded > UpdateCheckProtocol.MAX_APK_BYTES) {
                                throw IOException("Downloaded APK exceeded its expected size.")
                            }
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            onProgress(downloaded, expectedSizeBytes)
                        }
                    }
                }

                if (downloaded != expectedSizeBytes) {
                    throw IOException("Downloaded APK is incomplete.")
                }
                val actualSha256 = digest.digest().toHexString()
                if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                    throw IOException("Downloaded APK failed its SHA-256 check.")
                }

                val archive = context.packageManager.getPackageArchiveInfo(temporaryFile.absolutePath, 0)
                    ?: throw IOException("Downloaded file is not a readable Android APK.")
                if (archive.packageName != context.packageName) {
                    throw IOException("Downloaded APK is for a different application.")
                }
                if (archive.versionName != version) {
                    throw IOException("Downloaded APK version does not match the release.")
                }
                val archiveVersionCode = archive.versionCodeLongCompat()
                if (expectedVersionCode != null && archiveVersionCode != expectedVersionCode) {
                    throw IOException("Downloaded APK version code does not match the selected release.")
                }
                val installed = context.packageManager.getPackageInfo(context.packageName, 0)
                if (archiveVersionCode <= installed.versionCodeLongCompat()) {
                    throw IOException("The downloaded APK is not newer than this installation.")
                }

                apkFile.delete()
                if (!temporaryFile.renameTo(apkFile)) {
                    throw IOException("Could not finalize the downloaded APK.")
                }
                apkFile
            } finally {
                temporaryFile.delete()
            }
        } finally {
            response.close()
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) {
                    continuation.resume(response)
                } else {
                    response.close()
                }
            }
        })
        continuation.invokeOnCancellation { this@await.cancel() }
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.versionCodeLongCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()

    private fun ByteArray.toHexString(): String = joinToString("") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }
}
