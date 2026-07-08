package com.geozelot.homer.di

import javax.inject.Qualifier

/** Unauthenticated OkHttp client — used for the Login Flow v2 handshake. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Bootstrap

/** Authenticated OkHttp client — injects Basic auth for all WebDAV traffic. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Authed
