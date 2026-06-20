package com.budgetr.app.di

import com.budgetr.app.data.api.AuthInterceptor
import com.budgetr.app.data.api.GitHubApi
import com.budgetr.app.data.api.GoogleDriveApi
import com.budgetr.app.data.api.GoogleSheetsApi
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val SHEETS_BASE_URL = "https://sheets.googleapis.com/v4/"
    private const val DRIVE_BASE_URL = "https://www.googleapis.com/drive/v3/"

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .build()

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            )
            .build()

    @Provides
    @Singleton
    @Named("sheets")
    fun provideSheetsRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl(SHEETS_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    @Named("drive")
    fun provideDriveRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl(DRIVE_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideGoogleSheetsApi(@Named("sheets") retrofit: Retrofit): GoogleSheetsApi =
        retrofit.create(GoogleSheetsApi::class.java)

    @Provides
    @Singleton
    fun provideGoogleDriveApi(@Named("drive") retrofit: Retrofit): GoogleDriveApi =
        retrofit.create(GoogleDriveApi::class.java)

    @Provides
    @Singleton
    @Named("github")
    fun provideGitHubRetrofit(moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideGitHubApi(@Named("github") retrofit: Retrofit): GitHubApi =
        retrofit.create(GitHubApi::class.java)
}
