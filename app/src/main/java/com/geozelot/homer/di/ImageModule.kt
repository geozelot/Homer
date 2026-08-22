package com.geozelot.homer.di

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ImageModule {

    /**
     * Coil image loader backed by the authenticated OkHttp client, so cover images can
     * be fetched directly from WebDAV (Basic auth injected). Coil handles downsampling
     * and caching, avoiding the OOM risk from full-resolution cover art.
     *
     * **[respectCacheHeaders] is off deliberately, and it matters a lot.** A folder cover is
     * addressed by its WebDAV URL, but Nextcloud serves `remote.php/dav` with PHP's default
     * `Cache-Control: no-store, no-cache`. With cache headers respected, Coil refuses to write
     * those responses to its disk cache, so every time a cover scrolled back into view it was
     * re-downloaded at full resolution — hundreds of MB per library browse. Covers are immutable
     * per URL here (a re-scan re-keys them), so ignoring the headers is safe and turns repeat
     * views into disk reads. The disk cache is sized generously because a large library's art can
     * run to a few hundred MB; it lives in the cache dir, so the OS can reclaim it under pressure.
     *
     * Note: request size is deliberately NOT pinned here. Coil decodes to each display's size, so
     * the grid keeps small bitmaps while the player gets a sharp one; the disk cache is keyed by
     * URL, so all sizes share a single download.
     */
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        @Authed client: OkHttpClient,
    ): ImageLoader =
        ImageLoader.Builder(context)
            .okHttpClient(client)
            .crossfade(true)
            .respectCacheHeaders(false)
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("cover_cache"))
                    .maxSizeBytes(MAX_COVER_CACHE_BYTES)
                    .build()
            }
            .build()

    /** 384 MB — enough for a few hundred full-resolution covers. */
    private const val MAX_COVER_CACHE_BYTES = 384L * 1024 * 1024
}
