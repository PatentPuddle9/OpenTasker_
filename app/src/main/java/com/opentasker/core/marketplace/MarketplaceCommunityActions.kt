package com.opentasker.core.marketplace

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class MarketplaceCommunityUser(
    val login: String,
    val id: Long,
)

internal object MarketplaceCommunityActions {
    private const val REPOSITORY = "PatentPuddle9/OpenTasker_Makrketplace"
    private const val RATING_MARKER = "<!-- opentasker-marketplace-rate:v1 -->"
    private const val FEATURE_MARKER = "<!-- opentasker-marketplace-feature:v1 -->"
    private const val REVIEW_MARKER = "<!-- opentasker-marketplace-review:v1 -->"
    private const val HELPFUL_MARKER = "<!-- opentasker-marketplace-helpful:v1 -->"
    private const val DOWNLOAD_MARKER = "<!-- opentasker-marketplace-download:v1 -->"
    private val curatorLogins = setOf("PatentPuddle9")

    fun canFeaturePackages(login: String?): Boolean =
        login?.let { candidate -> curatorLogins.any { it.equals(candidate, ignoreCase = true) } } == true

    suspend fun currentUser(context: Context): MarketplaceCommunityUser = withContext(Dispatchers.IO) {
        val token = GitHubDeviceAuth.accessToken(context.applicationContext)
        val response = GitHubDeviceAuth.apiRequestBlocking(token, "GET", "/user")
        val login = response.optString("login").trim()
        val id = response.optLong("id", 0L)
        require(login.matches(Regex("^[A-Za-z0-9-]{1,39}$")) && id > 0L) {
            "GitHub did not return a valid account identity."
        }
        MarketplaceCommunityUser(login, id)
    }

    suspend fun submitRating(
        context: Context,
        packageId: String,
        publisher: String,
        stars: Int,
    ): String = withContext(Dispatchers.IO) {
        require(packageId.matches(Regex("^[a-z0-9][a-z0-9._-]{1,79}$"))) {
            "The package ID is invalid."
        }
        require(stars in 1..5) { "Choose a rating from 1 to 5 stars." }
        val token = GitHubDeviceAuth.accessToken(context.applicationContext)
        val user = GitHubDeviceAuth.apiRequestBlocking(token, "GET", "/user")
        val login = user.optString("login").trim()
        require(login.isNotBlank()) { "GitHub did not return your account name." }
        require(!login.equals(publisher, ignoreCase = true)) {
            "You can't rate your own package."
        }
        val body = RATING_MARKER + "\n" + JSONObject()
            .put("packageId", packageId)
            .put("stars", stars)
            .toString(2)
        val issue = GitHubDeviceAuth.apiRequestBlocking(
            token,
            "POST",
            "/repos/$REPOSITORY/issues",
            JSONObject()
                .put("title", "[Marketplace rating] $packageId")
                .put("body", body),
        )
        issue.optString("html_url").takeIf { it.startsWith("https://github.com/") }
            ?: throw IllegalStateException("GitHub did not return the rating request link.")
    }

    suspend fun requestFeaturedChange(
        context: Context,
        packageId: String,
        featured: Boolean,
    ): String = withContext(Dispatchers.IO) {
        require(packageId.matches(Regex("^[a-z0-9][a-z0-9._-]{1,79}$"))) {
            "The package ID is invalid."
        }
        val token = GitHubDeviceAuth.accessToken(context.applicationContext)
        val user = GitHubDeviceAuth.apiRequestBlocking(token, "GET", "/user")
        val login = user.optString("login").trim()
        require(canFeaturePackages(login)) {
            "Only trusted Marketplace maintainers can feature packages."
        }
        val actionLabel = if (featured) "Feature" else "Unfeature"
        val body = FEATURE_MARKER + "\n" + JSONObject()
            .put("packageId", packageId)
            .put("featured", featured)
            .toString(2)
        val issue = GitHubDeviceAuth.apiRequestBlocking(
            token,
            "POST",
            "/repos/$REPOSITORY/issues",
            JSONObject()
                .put("title", "[Marketplace] " + actionLabel + " " + packageId)
                .put("body", body),
        )
        issue.optString("html_url").takeIf { it.startsWith("https://github.com/") }
            ?: throw IllegalStateException("GitHub did not return the featured-package request link.")
    }

    suspend fun submitReview(
        context: Context,
        packageId: String,
        publisher: String,
        title: String,
        review: String,
    ): String = withContext(Dispatchers.IO) {
        require(title.trim().isNotEmpty() && title.length <= 100) {
            "Add a review title of up to 100 characters."
        }
        require(review.trim().isNotEmpty() && review.length <= 1200) {
            "Write between 1 and 1,200 characters."
        }
        val token = GitHubDeviceAuth.accessToken(context.applicationContext)
        val user = GitHubDeviceAuth.apiRequestBlocking(token, "GET", "/user")
        val login = user.optString("login").trim()
        require(login.isNotBlank() && !login.equals(publisher, ignoreCase = true)) {
            "Publishers cannot review their own packages."
        }
        createCommunityIssue(
            token,
            "[Marketplace review] $packageId",
            REVIEW_MARKER,
            JSONObject().put("packageId", packageId).put("title", title.trim()).put("body", review.trim()),
        )
    }

    suspend fun markReviewHelpful(
        context: Context,
        packageId: String,
        reviewUserId: Long,
    ): String = withContext(Dispatchers.IO) {
        require(reviewUserId > 0L) { "The review reference is invalid." }
        val token = GitHubDeviceAuth.accessToken(context.applicationContext)
        createCommunityIssue(
            token,
            "[Marketplace helpful] $packageId",
            HELPFUL_MARKER,
            JSONObject().put("packageId", packageId).put("reviewUserId", reviewUserId),
        )
    }

    suspend fun recordDownload(
        context: Context,
        packageId: String,
        version: String,
    ): String = withContext(Dispatchers.IO) {
        val token = GitHubDeviceAuth.accessToken(context.applicationContext)
        createCommunityIssue(
            token,
            "[Marketplace install] $packageId",
            DOWNLOAD_MARKER,
            JSONObject().put("packageId", packageId).put("version", version),
        )
    }

    private fun createCommunityIssue(
        token: String,
        title: String,
        marker: String,
        payload: JSONObject,
    ): String {
        val issue = GitHubDeviceAuth.apiRequestBlocking(
            token,
            "POST",
            "/repos/$REPOSITORY/issues",
            JSONObject().put("title", title).put("body", marker + "\n" + payload.toString(2)),
        )
        return issue.optString("html_url").takeIf { it.startsWith("https://github.com/") }
            ?: throw IllegalStateException("GitHub did not return the Marketplace request link.")
    }

}
