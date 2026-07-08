package com.geozelot.homer.di

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {

    /**
     * Resolves both streamed and downloaded audio: [DefaultDataSource] handles local
     * `file://` URIs (offline playback) and delegates http(s) to the authenticated,
     * Range-capable OkHttp source (Basic auth injected; ExoPlayer seeks within large
     * remote files without a full download). The same factory backs cover/duration
     * probing, which only ever passes http URLs.
     */
    @Provides
    @Singleton
    @UnstableApi
    fun provideDataSourceFactory(
        @Authed client: OkHttpClient,
        @ApplicationContext context: Context,
    ): DataSource.Factory =
        DefaultDataSource.Factory(context, OkHttpDataSource.Factory(client))
}
