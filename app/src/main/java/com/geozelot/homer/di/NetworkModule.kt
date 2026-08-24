package com.geozelot.homer.di

import com.geozelot.homer.data.auth.AuthInterceptor
import com.geozelot.homer.data.net.CertPinningInterceptor
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
    fun provideAuthedClient(
        authInterceptor: AuthInterceptor,
        certPinningInterceptor: CertPinningInterceptor,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .connectionSpecs(TLS_ONLY)
            .addInterceptor(authInterceptor)
            // Network interceptor: needs the TLS handshake to read/verify the server certificate.
            .addNetworkInterceptor(certPinningInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            // Explicit, because OkHttp's default write timeout is TEN SECONDS and this client
            // uploads the shared library index — several megabytes for a large library. The
            // structure facet silently failed to publish on exactly that: the smaller derived
            // facet fit inside ten seconds and the larger one did not.
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    /** Generous on purpose: a first publish of a few thousand files is a real upload. */
    private const val WRITE_TIMEOUT_SECONDS = 120L
}
