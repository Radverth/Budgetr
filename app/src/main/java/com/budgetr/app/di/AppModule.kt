package com.budgetr.app.di

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.budgetr.app.data.repository.SheetsRepository
import com.budgetr.app.data.repository.SheetsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private const val SECURE_PREFS_NAME = "budgetr_secure_prefs"

    @Provides
    @Singleton
    @Named("encrypted")
    fun provideEncryptedSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return try {
            createEncryptedPrefs(context)
        } catch (e: Exception) {
            // The encrypted prefs file exists but its Tink keyset can no longer be decrypted by the
            // AndroidKeyStore master key. This happens when the file is restored onto a device/install
            // whose KeyStore key wasn't (and can't be) restored — e.g. Android Auto Backup restore,
            // a KeyStore reset, or a corrupted write. Left unhandled it throws here during Hilt graph
            // construction for the first screen, so the app black-screens and closes on every launch.
            // Recover by discarding the unreadable prefs and starting fresh; the user simply re-signs in.
            Log.w(TAG, "Encrypted prefs unreadable, resetting them", e)
            context.deleteSharedPreferences(SECURE_PREFS_NAME)
            createEncryptedPrefs(context)
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private const val TAG = "AppModule"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSheetsRepository(impl: SheetsRepositoryImpl): SheetsRepository
}
