package com.geozelot.homer.data

import kotlin.coroutines.cancellation.CancellationException

/**
 * [runCatching], minus the bug.
 *
 * `runCatching` catches `Throwable`, and in a coroutine that includes the `CancellationException`
 * the machinery throws to unwind a cancelled job. Swallowing it does two bad things at once: the
 * coroutine keeps going until something else happens to check, and — far worse — the failure is
 * handed to the caller as an ANSWER.
 *
 * That second one is not theoretical. `WebDavClient.canWrite` read `runCatching { mkcol(…) }
 * .isSuccess`, so a cancelled probe returned false, and false there means "this server is
 * read-only" — which is what decides whether this device maintains the library or only reads it.
 * A user backing out of setup could quietly demote themselves. Elsewhere a cancelled PROPFIND
 * became "there are no libraries in this folder".
 *
 * Returning a [Result] rather than a nullable keeps every call site's existing shape — `getOrNull`,
 * `getOrElse`, `getOrDefault`, `isSuccess` — so adopting it is a one-word change and the handling
 * above it stays exactly as it was.
 *
 * Use this for anything that suspends. Plain `runCatching` is still right around code that cannot
 * be cancelled because it never suspends: a file delete, a `MediaController` call, releasing an
 * audio effect.
 */
suspend fun <T> runCatchingUnlessCancelled(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
