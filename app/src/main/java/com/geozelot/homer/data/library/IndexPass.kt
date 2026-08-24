package com.geozelot.homer.data.library

/**
 * One maintenance pass over the library.
 *
 * Declaration order is **run order**, cheapest and most user-visible first. A correction is
 * kilobytes and someone is waiting to see it on their other device, so it must never sit behind an
 * hour-long length sweep; a length sweep is the longest thing this app does and goes last.
 */
enum class IndexPass(
    /** Whether the pass has a thorough variant — see [PassRequest.deep]. */
    val hasDeep: Boolean,
) {
    /** Publish the shared half of every metadata correction. */
    CORRECTIONS(hasDeep = false),

    /** Crawl the folder tree. Deep = a full crawl instead of an incremental one. */
    BOOKS(hasDeep = true),

    /** Fetch cover art for books without any. Deep = drop what is cached and fetch it all again. */
    ARTWORK(hasDeep = true),

    /** Measure the books that have no length. Deep = re-arm files whose probe failed before. */
    LENGTHS(hasDeep = true),
    ;

    companion object {
        /**
         * The pass called [name], or null if this build does not have one.
         *
         * Names travel through WorkManager progress data and the persisted queue, so a name written
         * by a different build has to be ignored rather than guessed at.
         */
        fun of(name: String?): IndexPass? = entries.firstOrNull { it.name == name }
    }
}

/**
 * A request for one pass.
 *
 * [deep] asks for the thorough variant, which for every pass means re-doing work already done:
 * a full crawl rather than an incremental one, cover art fetched again from scratch, a duration
 * probe re-armed on the files where it failed. It is ignored on a pass that has no such variant.
 */
data class PassRequest(val pass: IndexPass, val deep: Boolean = false)

/**
 * The queue of requested passes, as a set of tokens that survives the process.
 *
 * The passes used to share one WorkManager unique name enqueued with `REPLACE`, so asking for
 * lengths killed a cover pass half way through and the UI had to disable every action while any
 * one of them ran. Here the request is the durable thing and the worker drains it: asking for a
 * second pass queues it, and a worker the system stops mid-pass leaves the token in place, which
 * is what lets an hours-long sweep resume on the next run.
 *
 * All of it is set arithmetic on strings so it can be tested off-device and stored in DataStore
 * without a schema.
 */
object PassQueue {

    /** The token for [request], with a meaningless `deep` normalised away. */
    fun encode(request: PassRequest): String =
        if (request.deep && request.pass.hasDeep) "${request.pass.name}$DEEP_SUFFIX" else request.pass.name

    /**
     * The request [token] names, or null if it names nothing this build understands.
     *
     * Covers a token from a newer build, and a deep token for a pass that has no deep variant —
     * neither can be honoured, and inventing an interpretation would run the wrong pass.
     */
    fun decode(token: String): PassRequest? {
        val deep = token.endsWith(DEEP_SUFFIX)
        val pass = IndexPass.of(if (deep) token.removeSuffix(DEEP_SUFFIX) else token) ?: return null
        if (deep && !pass.hasDeep) return null
        return PassRequest(pass, deep)
    }

    /**
     * [tokens] with [request] added.
     *
     * A deep request replaces a shallow one for the same pass — it does everything the shallow one
     * would — and a shallow request is absorbed by a deep one already waiting. Asking twice is
     * therefore never two passes, which matters because several places ask for artwork on their own
     * initiative.
     */
    fun request(tokens: Set<String>, request: PassRequest): Set<String> {
        val deep = request.deep && request.pass.hasDeep
        val shallow = PassRequest(request.pass, deep = false)
        return when {
            deep -> tokens - encode(shallow) + encode(request)
            encode(PassRequest(request.pass, deep = true)) in tokens -> tokens
            else -> tokens + encode(shallow)
        }
    }

    /**
     * [tokens] with the pass that just ran removed, and anything undecodable swept up with it.
     *
     * The *exact* token is dropped, not every token for that pass: a shallow pass that was upgraded
     * to deep while it ran leaves the deep request standing, so the thorough pass the user asked
     * for still happens.
     */
    fun done(tokens: Set<String>, request: PassRequest): Set<String> =
        tokens.filterTo(mutableSetOf()) { it != encode(request) && decode(it) != null }

    /** Everything still requested, in run order. */
    fun pending(tokens: Set<String>): List<PassRequest> =
        tokens.mapNotNull(::decode).sortedBy { it.pass.ordinal }

    /** The next pass to run, or null when the queue is empty. */
    fun next(tokens: Set<String>): PassRequest? = pending(tokens).firstOrNull()

    private const val DEEP_SUFFIX = ":deep"
}
