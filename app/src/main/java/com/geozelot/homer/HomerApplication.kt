package com.geozelot.homer

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.geozelot.homer.data.update.UpdateCheckWorker
import com.geozelot.homer.ui.settings.AppLanguage
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

    /**
     * Wrapped here as well as in the activity, so the chosen language reaches the strings that
     * workers and notifications build off the application context — not just the ones Compose
     * resolves. Below Android 13 only; above it the framework has already done it.
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        // Enqueued unconditionally: the worker reads the opt-in preference itself and returns
        // immediately when it is off, so there is no schedule to keep in step with the setting.
        UpdateCheckWorker.schedule(this)
    }

    override fun newImageLoader(): ImageLoader = imageLoader
}
