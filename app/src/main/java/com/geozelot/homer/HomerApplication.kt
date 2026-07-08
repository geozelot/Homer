package com.geozelot.homer

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. [HiltAndroidApp] triggers Hilt's code generation and
 * establishes the application-level dependency container that the rest of Homer
 * builds on.
 */
@HiltAndroidApp
class HomerApplication : Application()
