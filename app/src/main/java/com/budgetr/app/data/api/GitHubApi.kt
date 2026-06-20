package com.budgetr.app.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET

interface GitHubApi {
    @GET("repos/radverth/budgetr/releases/latest")
    suspend fun getLatestRelease(): GitHubRelease
}

@JsonClass(generateAdapter = true)
data class GitHubRelease(
    @Json(name = "tag_name") val tagName: String,
    @Json(name = "name") val name: String,
    @Json(name = "html_url") val htmlUrl: String,
    @Json(name = "body") val body: String? = null
)
