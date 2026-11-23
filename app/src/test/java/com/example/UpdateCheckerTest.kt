package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun latestReleaseEndpoint_pointsToAntiMagerCoachRepo() {
        assertEquals(
            "https://api.github.com/repos/kubukubukuku-max/Anti_mager_coach/releases/latest",
            UpdateChecker.GITHUB_LATEST_RELEASE_URL
        )
    }

    @Test
    fun normalizeVersion_stripsLeadingVPrefix() {
        assertEquals("1.1.0", UpdateChecker.normalizeVersion("v1.1.0"))
        assertEquals("2.0.3", UpdateChecker.normalizeVersion("V2.0.3"))
        assertEquals("1.0", UpdateChecker.normalizeVersion("1.0"))
    }

    @Test
    fun isNewerVersion_comparesNumericSegmentsAccurately() {
        assertTrue(UpdateChecker.isNewerVersion("1.1.0", "1.0"))
        assertTrue(UpdateChecker.isNewerVersion("1.10", "1.2"))
        assertTrue(UpdateChecker.isNewerVersion("2.0", "1.9.9"))
        assertTrue(UpdateChecker.isNewerVersion("1.1.1", "1.1.0"))

        assertFalse(UpdateChecker.isNewerVersion("1.1.0", "1.1.0"))
        assertFalse(UpdateChecker.isNewerVersion("1.0.0", "1.0"))
        assertFalse(UpdateChecker.isNewerVersion("1.2", "1.10"))
    }

    @Test
    fun evaluateRelease_detectsUpdateAndExtractsApkUrl() {
        val assets = listOf(
            UpdateChecker.ReleaseAsset(
                name = "checksums.txt",
                downloadUrl = "https://github.com/kubukubukuku-max/Anti_mager_coach/releases/download/v1.2.0/checksums.txt"
            ),
            UpdateChecker.ReleaseAsset(
                name = "app-debug.apk",
                downloadUrl = "https://github.com/kubukubukuku-max/Anti_mager_coach/releases/download/v1.2.0/app-debug.apk"
            )
        )

        val result = UpdateChecker.evaluateRelease(
            rawTag = "v1.2.0",
            rawBody = "- Tambah Live Workout Timer\n- Perbaikan notifikasi",
            assets = assets,
            htmlUrl = "https://github.com/kubukubukuku-max/Anti_mager_coach/releases/tag/v1.2.0",
            currentVersion = "1.1.0"
        )
        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        val available = result as UpdateCheckResult.UpdateAvailable
        assertEquals("1.2.0", available.latestVersion)
        assertTrue(available.notes.contains("Live Workout Timer"))
        assertEquals(
            "https://github.com/kubukubukuku-max/Anti_mager_coach/releases/download/v1.2.0/app-debug.apk",
            available.apkUrl
        )
    }

    @Test
    fun evaluateRelease_returnsUpToDateWhenVersionMatches() {
        val result = UpdateChecker.evaluateRelease(
            rawTag = "v1.1.0",
            rawBody = "Rilis v1.1.0",
            assets = emptyList(),
            htmlUrl = "https://github.com/kubukubukuku-max/Anti_mager_coach/releases/tag/v1.1.0",
            currentVersion = "1.1.0"
        )
        assertTrue(result is UpdateCheckResult.UpToDate)
        assertEquals("1.1.0", (result as UpdateCheckResult.UpToDate).currentVersion)
    }

    @Test
    fun isTrustedGitHubUrl_allowsOnlyHttpsGitHubHosts() {
        assertTrue(
            UpdateChecker.isTrustedGitHubUrl(
                "https://github.com/kubukubukuku-max/Anti_mager_coach/releases/download/v1.1.0/app-debug.apk"
            )
        )
        assertTrue(
            UpdateChecker.isTrustedGitHubUrl(
                "https://objects.githubusercontent.com/github-production-release-asset/app.apk"
            )
        )
        assertFalse(UpdateChecker.isTrustedGitHubUrl("http://github.com/test.apk"))
        assertFalse(UpdateChecker.isTrustedGitHubUrl("https://evil.example.com/app.apk"))
        assertFalse(UpdateChecker.isTrustedGitHubUrl("javascript:alert(1)"))
    }
}
