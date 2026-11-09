package com.geozelot.homer.screenshots

import com.geozelot.homer.data.auth.NextcloudCredentials
import com.geozelot.homer.data.library.LibraryRole
import com.geozelot.homer.data.library.LibraryStanding
import com.geozelot.homer.data.sync.facet.IndexActivity
import com.geozelot.homer.data.sync.facet.PolicyInForce
import com.geozelot.homer.data.update.UpdateState
import com.geozelot.homer.ui.home.HomeViewModel
import com.geozelot.homer.ui.settings.UpdateViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The state the settings screenshots show: one person's own Nextcloud, whose library they keep and
 * share, with progress syncing to the same account.
 *
 * The settings pages are drawn as they are — only their ViewModels are stand-ins, because the real
 * ones open a database, a server connection and a media session the moment they are built. Every
 * value a page reads is stubbed here; one that is not fails the screenshot loudly rather than
 * drawing a default nobody chose.
 */
internal object DemoSettings {

    private val account = NextcloudCredentials(
        serverUrl = "https://cloud.example.org",
        loginName = "penelope",
        appPassword = "",
    )

    /** Keeps the library and publishes its index; the owner, so the rules are theirs to set. */
    private val standing = LibraryStanding(
        role = LibraryRole.MAINTAINER,
        isOwner = true,
        policy = PolicyInForce(
            sharedIndexRequired = false,
            editsAllowed = true,
            owner = "penelope",
            atFolder = "Audiobooks",
        ),
        policyKnown = true,
        backendWritable = true,
        usesSharedIndex = true,
        sharedIndexLocked = false,
        maintains = true,
        mayPublishIndex = true,
        mayPublishEdits = true,
        mayEditRules = true,
        restriction = null,
        editRestriction = null,
    )

    fun homeViewModel(): HomeViewModel = mockk(relaxed = true) {
        // The hub.
        every { account } returns MutableStateFlow(DemoSettings.account)
        every { libraryIsShare } returns MutableStateFlow(false)
        every { bookCount } returns MutableStateFlow(DemoLibrary.books.size)
        every { customStorageUri } returns MutableStateFlow(null)
        every { customStoragePath } returns MutableStateFlow(null)
        every { readsSharedIndex } returns MutableStateFlow(false)
        every { unmeasuredCount } returns MutableStateFlow(0)
        every { artlessCount } returns MutableStateFlow(0)
        every { storageAccessLost } returns MutableStateFlow(false)

        // Library & sync.
        every { libraryRoot } returns MutableStateFlow("Audiobooks")
        every { standing } returns MutableStateFlow(DemoSettings.standing)
        every { syncAccount } returns MutableStateFlow(DemoSettings.account)
        every { progressSyncEnabled } returns MutableStateFlow(true)
        every { indexActivity } returns MutableStateFlow(IndexActivity.IDLE)
        every { ruleWriteFailed } returns MutableStateFlow(false)
        every { pinningBlocked } returns MutableStateFlow(null)

        // Playback.
        every { seekSeconds } returns MutableStateFlow(30)
        every { autoRewindSeconds } returns MutableStateFlow(3)
        every { rewindOnReturnSeconds } returns MutableStateFlow(30)
        every { playOnSleepTimer } returns MutableStateFlow(true)
        every { sleepTimerNotification } returns MutableStateFlow(true)
        every { sleepExtend } returns MutableStateFlow("15")
        every { sleepFadeOutSeconds } returns MutableStateFlow(10)
        every { downloadOnPlay } returns MutableStateFlow(true)
    }

    /** Nothing newer to offer, so the About row wears no pill. */
    fun updateViewModel(): UpdateViewModel = mockk(relaxed = true) {
        every { state } returns MutableStateFlow(UpdateState.Idle)
    }
}
