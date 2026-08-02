package com.kuyermqi.quotawidget.update

import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository

data class UpdateAvailability(
    val versionName: String,
    val releaseUrl: String,
)

class UpdateCheckInteractor(
    private val settingsRepository: PlatformSettingsRepository,
    private val releaseClient: GitHubReleaseClient = GitHubReleaseClient(),
) {
    suspend fun check(currentVersionName: String): UpdateAvailability? {
        if (!settingsRepository.getAppSettings().checkForUpdatesOnLaunch) {
            return null
        }
        val release = runCatching { releaseClient.fetchLatestRelease() }.getOrNull()
            ?: return null
        val remoteVersion = normalizeVersionName(release.tagName)
        if (remoteVersion.isBlank() || release.htmlUrl.isBlank()) {
            return null
        }
        val current = SemVer.parse(currentVersionName)
        val remote = SemVer.parse(remoteVersion)
        if (remote <= current) {
            return null
        }
        val ignored = settingsRepository.getUpdateIgnoredVersion()
        if (ignored != null && normalizeVersionName(ignored) == remoteVersion) {
            return null
        }
        return UpdateAvailability(
            versionName = remoteVersion,
            releaseUrl = release.htmlUrl,
        )
    }
}
