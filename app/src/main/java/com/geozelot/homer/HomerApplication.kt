package com.geozelot.homer

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. [HiltAndroidApp] triggers Hilt's code generation and
 * establishes the application-level dependency container that the rest of Homer
 * builds on. Also supplies Coil's default (authenticated) [ImageLoader] so cover
 * images load from WebDAV, and WorkManager's Hilt-aware worker factory for the
 * download workers.
 */
@HiltAndroidApp
class HomerApplication : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun newImageLoader(): ImageLoader = imageLoader
}
