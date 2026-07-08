package com.geozelot.homer.di

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {

    /**
     * Streams over the authenticated OkHttp client, so Basic auth is injected and
     * HTTP Range is honored (ExoPlayer seeks within large files without full download).
     */
    @Provides
    @Singleton
    @UnstableApi
    fun provideDataSourceFactory(@Authed client: OkHttpClient): DataSource.Factory =
        OkHttpDataSource.Factory(client)
}
