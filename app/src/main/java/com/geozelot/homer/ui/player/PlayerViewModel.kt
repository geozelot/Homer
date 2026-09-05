package com.geozelot.homer.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geozelot.homer.data.db.dao.AudioFileDao
import com.geozelot.homer.data.db.dao.BookDao
import com.geozelot.homer.data.db.dao.BookmarkDao
import com.geozelot.homer.data.db.dao.BookOverrideDao
import com.geozelot.homer.data.db.dao.ChapterDao
import com.geozelot.homer.data.db.dao.DownloadDao
import com.geozelot.homer.data.db.entity.AudioFileEntity
import com.geozelot.homer.data.db.entity.BookmarkEntity
import com.geozelot.homer.data.db.entity.ChapterEntity
import com.geozelot.homer.data.db.entity.DownloadEntity
import com.geozelot.homer.data.auth.CredentialStore
import com.geozelot.homer.data.download.DownloadManager
import com.geozelot.homer.data.library.BookCover
import com.geozelot.homer.data.library.BookEditor
import com.geozelot.homer.data.library.applyOverride
import com.geozelot.homer.data.library.decodeGenres
import com.geozelot.homer.data.settings.LibrarySettings
import com.geozelot.homer.data.webdav.WebDavClient
import com.geozelot.homer.data.settings.PlaybackSettings
import com.geozelot.homer.playback.PlaybackConnection
import com.geozelot.homer.playback.PlaybackUiState
import com.geozelot.homer.ui.components.EditableBook
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One entry in the player's chapter picker. Exactly one of [startMs] (embedded mark — seek within
 * the current file) or [mediaItemIndex] (multi-file — jump to that file) is set.
 *
 * ## Why [lengthMs] and [startInBookMs] are nullable, and what that means on screen
 *
 * A single-file book's marks answer both for free: a chapter runs until the next one begins, and
 * where it begins in the book is the mark itself. A multi-file book cannot — its chapters are
 * files, and a file's length is only known once that file has been probed. So both are null until
 * every file of the book is measured, and the player says less rather than guessing: "chapter 7 of
 * 21" with no numbers beside it is true, and a number derived from a partial sum is not.
 */
data class PlayerChapter(
    val title: String,
    val mediaItemIndex: Int?,
    val startMs: Long?,
    val isCurrent: Boolean,
    /** How long this chapter runs, when that is known. */
    val lengthMs: Long? = null,
    /** Where this chapter begins measured from the start of the BOOK, when that is known. */
    val startInBookMs: Long? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val connection: PlaybackConnection,
    bookDao: BookDao,
    audioFileDao: AudioFileDao,
    private val playbackSettings: PlaybackSettings,
    private val bookmarkDao: BookmarkDao,
    private val bookOverrideDao: BookOverrideDao,
    private val downloadManager: DownloadManager,
    private val bookEditor: BookEditor,
    private val webDavClient: WebDavClient,
    credentialStore: CredentialStore,
    librarySettings: LibrarySettings,
    chapterDao: ChapterDao,
    downloadDao: DownloadDao,
) : ViewModel() {
    val state: StateFlow<PlaybackUiState> = connection.state

    private val bookId = MutableStateFlow<String?>(null)

    /**
     * The current book's cover, observed live from the DB — so a refresh/extraction updates the
     * player without reopening the book (unlike [PlaybackUiState.coverModel], a play-time snapshot).
     * Null until known; the screen falls back to embedded artwork meanwhile.
     */
    val cover: StateFlow<Any?> = combine(
        bookId.flatMapLatest { id -> if (id == null) flowOf(null) else bookDao.observeById(id) },
        credentialStore.credentials,
        librarySettings.libraryRoot,
    ) { book, creds, root ->
        book?.let { BookCover.model(it, creds, webDavClient, root) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val skipSilence: StateFlow<Boolean> = playbackSettings.skipSilence
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Seconds the skip-back/forward buttons jump; user-configurable (default 15). */
    val seekSeconds: StateFlow<Int> = playbackSettings.seekSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 15)

    /** Volume override: "reduced" | "normal" | "increased". */
    val volumeMode: StateFlow<String> = playbackSettings.volumeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "normal")

    val bookmarks: StateFlow<List<BookmarkEntity>> = bookId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else bookmarkDao.observeForBook(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloadState: StateFlow<DownloadEntity?> = bookId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else downloadDao.observeByBookId(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Emit an empty list the instant the book changes, then the new book's data. Without this,
    // combine() would keep the previous book's files until the DB query returns and briefly pair
    // them with the new book's playback state — flashing the wrong chapter names on a switch.
    private val embeddedChapters = bookId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else flow { emit(emptyList<ChapterEntity>()); emitAll(chapterDao.observeForBook(id)) }
    }
    private val bookFiles = bookId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else flow { emit(emptyList<AudioFileEntity>()); emitAll(audioFileDao.observeForBook(id)) }
    }

    /**
     * Unified chapter list for the player's picker. A multi-file book's chapters are its ordered
     * files; a single-file book's are its embedded ID3 marks (if any). Empty ⇒ no picker.
     */
    val chapters: StateFlow<List<PlayerChapter>> =
        combine(embeddedChapters, bookFiles, state) { embedded, files, s ->
            // Only compute when the playback state and the loaded files refer to the same book,
            // so a mid-switch mismatch never marks the wrong chapter current.
            if (s.bookId != null && s.bookId != bookId.value) return@combine emptyList()
            when {
                embedded.isNotEmpty() -> {
                    // Single file: the current mark is the last one starting at/before the position.
                    val current = embedded.indexOfLast { it.startMs <= s.positionMs }.coerceAtLeast(0)
                    // The file's own length ends the last chapter. Taken from the playback state
                    // rather than from the book row: the player knows the loaded file's duration
                    // immediately, where the book's total waits on the measuring pass.
                    val fileEnd = s.durationMs.takeIf { it > 0 }
                        ?: files.firstOrNull()?.durationMs
                    embedded.mapIndexed { i, c ->
                        // A mark runs until the next one starts. Nothing has to be measured for
                        // this — the marks themselves carry it — which is why a single-file book
                        // shows its chapter lengths while streaming and a multi-file one does not.
                        val end = embedded.getOrNull(i + 1)?.startMs ?: fileEnd
                        PlayerChapter(
                            title = c.title?.ifBlank { null } ?: "Chapter ${i + 1}",
                            mediaItemIndex = null,
                            startMs = c.startMs,
                            isCurrent = i == current,
                            lengthMs = end?.minus(c.startMs)?.takeIf { it > 0 },
                            // The same number twice, and not by accident: embedded marks only
                            // exist for a book that is ONE file (see DurationEnricher), so a
                            // mark's offset into the file is its offset into the book.
                            startInBookMs = c.startMs,
                        )
                    }
                }
                files.size > 1 -> {
                    // All or nothing: a running sum over a list with a hole in it is wrong for
                    // every chapter after the hole, and wrong quietly. Until the whole book is
                    // measured the picker says which chapter, not where it sits.
                    val measured = files.all { it.durationMs != null }
                    var elapsed = 0L
                    files.map { f ->
                        val startInBook = if (measured) elapsed else null
                        if (measured) elapsed += f.durationMs ?: 0L
                        PlayerChapter(
                            title = f.fileName.substringBeforeLast('.'),
                            mediaItemIndex = f.sortIndex,
                            startMs = null,
                            isCurrent = f.sortIndex == s.chapterIndex,
                            lengthMs = f.durationMs,
                            startInBookMs = startInBook,
                        )
                    }
                }
                else -> emptyList()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Whole-book length; fills in reactively as durations are measured on first open. */
    val bookDurationMs: StateFlow<Long?> = bookId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else bookDao.observeById(id).map { it?.totalDurationMs }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val chapterDurations: StateFlow<List<Long>?> = bookId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            // Empty-first (see [bookFiles]) so time-left doesn't briefly use the old book's durations.
            else audioFileDao.observeForBook(id).onStart { emit(emptyList()) }.map { files ->
                // Null until every chapter is measured, so "time left" is never misleading.
                if (files.isNotEmpty() && files.all { it.durationMs != null }) {
                    files.map { it.durationMs!! }
                } else {
                    null
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Time remaining in the whole book from the current chapter + position; live. */
    val timeLeftMs: StateFlow<Long?> = combine(chapterDurations, state) { durations, s ->
        if (s.bookId != null && s.bookId != bookId.value) return@combine null
        if (durations == null) return@combine null
        val index = s.chapterIndex.coerceIn(0, durations.size)
        val elapsed = durations.take(index).sum() + s.positionMs.coerceAtLeast(0)
        (durations.sum() - elapsed).coerceAtLeast(0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val bookEntity = bookId.flatMapLatest { id ->
        if (id == null) flowOf(null) else bookDao.observeById(id)
    }
    private val override = bookId.flatMapLatest { id ->
        if (id == null) flowOf(null) else bookOverrideDao.observeById(id)
    }

    /** The current book as an editable model (effective values applied) for the shared edit dialog. */
    val editableBook: StateFlow<EditableBook?> =
        combine(bookEntity, override) { book, ov ->
            if (book == null) return@combine null
            val eff = book.applyOverride(ov)
            EditableBook(
                id = book.id,
                title = eff.title,
                author = eff.author,
                series = eff.series,
                seriesIndex = eff.seriesIndex,
                collection = eff.collection,
                collectionIndex = eff.collectionIndex,
                genres = decodeGenres(eff.genre),
                language = eff.language,
                tags = ov?.tags?.split('\n')?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList(),
                hidden = ov?.hidden ?: false,
                hasCustomCover = book.customCoverPath != null,
                downloadOnPlay = ov?.downloadOnPlay,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun play(bookId: String) {
        this.bookId.value = bookId
        connection.playBook(bookId)
    }

    fun playPause() = connection.playPause()
    fun retry() = connection.retry()
    fun seekTo(positionMs: Long) = connection.seekTo(positionMs)
    fun seekBy(deltaSeconds: Int) = connection.seekBy(deltaSeconds * 1000L)
    fun setSpeed(speed: Float) = connection.setSpeed(speed)
    fun setSkipSilence(enabled: Boolean) = connection.setSkipSilence(enabled)
    fun setVolumeMode(mode: String) = connection.setVolumeMode(mode)
    fun startSleepTimer(durationMs: Long) = connection.startSleepTimer(durationMs)
    fun startSleepTimerEndOfChapter() = connection.startSleepTimerEndOfChapter()
    fun cancelSleepTimer() = connection.cancelSleepTimer()
    /** Jumps to a chapter: a within-file seek (embedded marks) or a media-item jump (multi-file). */
    fun jumpToChapter(chapter: PlayerChapter) {
        when {
            chapter.startMs != null -> connection.seekTo(chapter.startMs)
            chapter.mediaItemIndex != null -> connection.jumpToChapterItem(chapter.mediaItemIndex)
        }
    }

    fun addBookmark(kind: String) = connection.addBookmark(kind)
    fun jumpToBookmark(bookmark: BookmarkEntity) =
        connection.jumpToBookmark(bookmark.mediaId, bookmark.positionMs)
    fun deleteBookmark(bookmark: BookmarkEntity) =
        connection.deleteBookmark(bookmark.id, bookmark.bookId)
    /** "Mark as completed": resets the current book's progress so it drops off the listening shelf. */
    fun markCompleted() {
        val id = bookId.value ?: return
        connection.resetProgress(id)
    }

    /** Saves metadata corrections for the current book from the shared edit dialog. */
    fun saveOverride(
        title: String,
        author: String,
        series: String,
        seriesIndex: String,
        collection: String,
        collectionIndex: String,
        genres: List<String>,
        language: String,
        tags: String,
        hidden: Boolean,
        downloadOnPlay: Boolean?,
    ) {
        val id = bookId.value ?: return
        viewModelScope.launch {
            bookEditor.saveOverride(
                id, title, author, series, seriesIndex, collection, collectionIndex,
                genres, language, tags, hidden, downloadOnPlay,
            )
        }
    }

    fun clearOverride() {
        val id = bookId.value ?: return
        viewModelScope.launch { bookEditor.clearOverride(id) }
    }

    fun setCustomCover(uri: android.net.Uri) {
        val id = bookId.value ?: return
        viewModelScope.launch { bookEditor.setCustomCover(id, uri) }
    }

    fun clearCustomCover() {
        val id = bookId.value ?: return
        viewModelScope.launch { bookEditor.clearCustomCover(id) }
    }

    fun download() = bookId.value?.let(downloadManager::download)
    fun deleteDownload() = bookId.value?.let(downloadManager::delete)
    fun nextChapter() = connection.nextChapter()
    fun previousChapter() = connection.previousChapter()
}
