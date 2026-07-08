package com.geozelot.homer.di

import com.geozelot.homer.data.auth.AuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // Enforce TLS at the transport layer: MODERN_TLS is TLS 1.2/1.3 with strong ciphers,
    // and dropping the default CLEARTEXT spec means an http:// URL fails here too — defence
    // in depth beyond the manifest's usesCleartextTraffic=false.
    private val TLS_ONLY = listOf(ConnectionSpec.MODERN_TLS)

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Provides
    @Singleton
    @Bootstrap
    fun provideBootstrapClient(): OkHttpClient = OkHttpClient.Builder()
        .connectionSpecs(TLS_ONLY)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    @Authed
    fun provideAuthedClient(authInterceptor: AuthInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .connectionSpecs(TLS_ONLY)
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
}
