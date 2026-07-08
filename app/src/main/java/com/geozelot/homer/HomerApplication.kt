package com.geozelot.homer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. [HiltAndroidApp] triggers Hilt's code generation and
 * establishes the application-level dependency container that the rest of Homer
 * builds on. Also supplies Coil's default (authenticated) [ImageLoader] so cover
 * images load from WebDAV.
 */
@HiltAndroidApp
class HomerApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var imageLoader: ImageLoader

    override fun newImageLoader(): ImageLoader = imageLoader
}
