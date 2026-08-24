package com.geozelot.homer.di

import com.geozelot.homer.data.sync.facet.FacetTransport
import com.geozelot.homer.data.sync.facet.LibraryRootSource
import com.geozelot.homer.data.sync.facet.SettingsLibraryRoot
import com.geozelot.homer.data.sync.facet.WebDavFacetTransport
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The two seams the faceted index is built on. Both exist so the store's decisions — ETag caching,
 * damaged files, lost writes — can be tested without a server or DataStore behind them.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    @Binds
    @Singleton
    abstract fun bindFacetTransport(impl: WebDavFacetTransport): FacetTransport

    @Binds
    @Singleton
    abstract fun bindLibraryRoot(impl: SettingsLibraryRoot): LibraryRootSource
}
