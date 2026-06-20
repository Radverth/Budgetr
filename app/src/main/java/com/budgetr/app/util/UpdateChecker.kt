package com.budgetr.app.util

import com.budgetr.app.data.api.GitHubApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateChecker @Inject constructor(
    private val gitHubApi: GitHubApi
) {
    data class UpdateResult(
        val available: Boolean,
        val version: String = "",
        val url: String = ""
    )

    suspend fun checkForUpdate(currentVersionName: String): UpdateResult {
        return try {
            val release = gitHubApi.getLatestRelease()
            val latestVersion = release.tagName.removePrefix("v")
            if (isNewer(latestVersion, currentVersionName)) {
                UpdateResult(available = true, version = latestVersion, url = release.htmlUrl)
            } else {
                UpdateResult(available = false)
            }
        } catch (_: Exception) {
            UpdateResult(available = false)
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val l = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(l.size, c.size)
        for (i in 0 until maxLen) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return false
    }
}
