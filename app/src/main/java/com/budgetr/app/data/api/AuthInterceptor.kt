package com.budgetr.app.data.api

import com.budgetr.app.util.AuthManager
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val authManager: AuthManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = authManager.getAccessToken()
        val response = chain.proceed(chain.request().withBearerToken(token))
        if (response.code == 401) {
            response.close()
            // Refresh quietly (interceptor runs on a background thread) and try once more
            val fresh = authManager.refreshToken(staleToken = token)
            return chain.proceed(chain.request().withBearerToken(fresh ?: token))
        }
        return response
    }

    private fun Request.withBearerToken(token: String?): Request {
        if (token == null) return this
        return newBuilder().header("Authorization", "Bearer $token").build()
    }
}
