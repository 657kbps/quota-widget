package com.kuyermqi.quotawidget.update

import com.kuyermqi.quotawidget.deepseek.createHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class GitHubReleaseClient(
    private val httpClient: HttpClient = createHttpClient(),
) {
    suspend fun fetchLatestRelease(): GitHubRelease {
        return httpClient.get(LATEST_RELEASE_URL) {
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header(HttpHeaders.UserAgent, USER_AGENT)
            header("X-GitHub-Api-Version", "2022-11-28")
        }.body()
    }

    fun close() {
        httpClient.close()
    }

    companion object {
        private const val OWNER = "657kbps"
        private const val REPO = "quota-widget"
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
        private const val USER_AGENT = "quota-widget"
    }
}

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
)
