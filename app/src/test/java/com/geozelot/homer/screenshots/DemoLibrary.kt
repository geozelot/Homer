package com.geozelot.homer.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.geozelot.homer.data.db.entity.DownloadStatus
import com.geozelot.homer.ui.home.BookListItem
import com.geozelot.homer.ui.player.PlayerChapter

/**
 * The library the store screenshots show: public-domain classics, the kind of thing LibriVox has
 * recorded, so nothing on screen is anybody's copyrighted title or artwork.
 *
 * The covers are drawn here rather than fetched. Real audiobook art is somebody else's work, and
 * a screenshot is a redistribution of it; these are typographic covers in the spirit of a
 * clothbound classics shelf, and they make the same point a real cover would — that Homer shows
 * art, square, at every size.
 */
internal object DemoLibrary {

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    /** "Now" for every relative time on screen, so a re-run draws the same pixels. */
    const val NOW = 1_762_430_400_000L

    const val PLAYING = "Homer/Homeric Epics/02 The Odyssey"
    const val MOBY_DICK = "Herman Melville/Moby-Dick"
    const val VOYAGES = "Extraordinary Voyages"

    /** Background top, background bottom, ink — and the motif drawn behind the title. */
    private data class Look(val top: Int, val bottom: Int, val ink: Int, val motif: Motif)

    private enum class Motif { SUN, WAVES, MOON, STAR, LAMP, ROSE, GEAR, KEY }

    private data class Spec(
        val id: String,
        val title: String,
        val author: String,
        val hours: Double,
        val genres: List<String>,
        val look: Look,
        val series: String? = null,
        val index: Int? = null,
        val collection: String? = null,
        val collectionIndex: Int? = null,
        val files: Int = 1,
        val progress: Float? = null,
        val playedDaysAgo: Double? = null,
        val downloaded: Boolean = false,
        val finished: Boolean = false,
        val documents: List<String> = emptyList(),
    )

    // Lazy: it reads the chapter lists, which are declared further down.
    private val specs by lazy {
        listOf(
            Spec(
                PLAYING, "The Odyssey", "Homer", 12.1, listOf("mythology", "classics"),
                Look(0xFF1F3A4D.toInt(), 0xFF0E1B26.toInt(), 0xFFE9C46A.toInt(), Motif.WAVES),
                series = "Homeric Epics", index = 2, files = ODYSSEY_BOOKS.size,
                progress = odyssey.progress, playedDaysAgo = 0.0, downloaded = true,
                documents = listOf("Homer/Homeric Epics/02 The Odyssey/The voyage of Odysseus.pdf"),
            ),
            Spec(
                "Homer/Homeric Epics/01 The Iliad", "The Iliad", "Homer", 14.4, listOf("mythology", "classics"),
                Look(0xFF7A2E1F.toInt(), 0xFF3A140D.toInt(), 0xFFF2D49B.toInt(), Motif.SUN),
                series = "Homeric Epics", index = 1, files = 24, finished = true, playedDaysAgo = 90.0,
            ),
            Spec(
                "Arthur Conan Doyle/Sherlock Holmes/01 A Study in Scarlet", "A Study in Scarlet",
                "Arthur Conan Doyle", 4.8, listOf("mystery", "classics"),
                Look(0xFF8C1C13.toInt(), 0xFF40100B.toInt(), 0xFFF1E3C8.toInt(), Motif.KEY),
                series = "Sherlock Holmes", index = 1, files = 14, finished = true, playedDaysAgo = 40.0,
            ),
            Spec(
                "Arthur Conan Doyle/Sherlock Holmes/02 The Sign of the Four", "The Sign of the Four",
                "Arthur Conan Doyle", 4.5, listOf("mystery", "classics"),
                Look(0xFF2D3E2F.toInt(), 0xFF141E15.toInt(), 0xFFE8D9A8.toInt(), Motif.STAR),
                series = "Sherlock Holmes", index = 2, files = 12, finished = true, playedDaysAgo = 21.0,
            ),
            Spec(
                "Arthur Conan Doyle/Sherlock Holmes/03 The Hound of the Baskervilles", "The Hound of the Baskervilles",
                "Arthur Conan Doyle", 6.5,
                listOf("mystery", "horror", "classics"),
                Look(0xFF3B3355.toInt(), 0xFF17132A.toInt(), 0xFFD9C5F2.toInt(), Motif.MOON),
                series = "Sherlock Holmes", index = 3, files = 15, progress = 0.64f, playedDaysAgo = 1.2,
                downloaded = true,
            ),
            Spec(
                "Arthur Conan Doyle/Sherlock Holmes/04 The Valley of Fear", "The Valley of Fear",
                "Arthur Conan Doyle", 7.1, listOf("mystery", "classics"),
                Look(0xFF5B4A2E.toInt(), 0xFF261E11.toInt(), 0xFFF0DDB4.toInt(), Motif.LAMP),
                series = "Sherlock Holmes", index = 4, files = 15,
            ),
            Spec(
                "Arthur Conan Doyle/The Lost World", "The Lost World", "Arthur Conan Doyle", 7.6,
                listOf("adventure", "scifi"),
                Look(0xFF3F6B2A.toInt(), 0xFF17290F.toInt(), 0xFFF0E6A8.toInt(), Motif.SUN),
                files = 16,
            ),
            Spec(
                "Arthur Conan Doyle/The White Company", "The White Company", "Arthur Conan Doyle", 14.2,
                listOf("adventure", "classics"),
                Look(0xFFB9B4A6.toInt(), 0xFF5C574B.toInt(), 0xFF1E1B15.toInt(), Motif.KEY),
                files = 38,
            ),
            Spec(
                "L. Frank Baum/Oz/01 The Wonderful Wizard of Oz", "The Wonderful Wizard of Oz",
                "L. Frank Baum", 3.8, listOf("fantasy", "childrens"),
                Look(0xFF1E6B4E.toInt(), 0xFF0B2E21.toInt(), 0xFFF6E27F.toInt(), Motif.STAR),
                series = "Oz", index = 1, files = 24, finished = true, playedDaysAgo = 60.0,
            ),
            Spec(
                "L. Frank Baum/Oz/02 The Marvelous Land of Oz", "The Marvelous Land of Oz",
                "L. Frank Baum", 4.8, listOf("fantasy", "childrens"),
                Look(0xFF6A3C8C.toInt(), 0xFF2B163B.toInt(), 0xFFF3D27A.toInt(), Motif.SUN),
                series = "Oz", index = 2, files = 24,
            ),
            Spec(
                "L. Frank Baum/Oz/03 Ozma of Oz", "Ozma of Oz", "L. Frank Baum", 3.7,
                listOf("fantasy", "childrens"),
                Look(0xFF2C5F8A.toInt(), 0xFF112538.toInt(), 0xFFF7E3A1.toInt(), Motif.ROSE),
                series = "Oz", index = 3, files = 21,
            ),
            Spec(
                "L. Frank Baum/Queen Zixi of Ix", "Queen Zixi of Ix", "L. Frank Baum", 5.2,
                listOf("fantasy", "childrens"),
                Look(0xFF9C3D5E.toInt(), 0xFF431628.toInt(), 0xFFF9DDA4.toInt(), Motif.KEY),
                files = 23,
            ),
            Spec(
                "L. Frank Baum/The Master Key", "The Master Key", "L. Frank Baum", 4.1, listOf("scifi", "childrens"),
                Look(0xFF2F4858.toInt(), 0xFF121D24.toInt(), 0xFF9FE2D0.toInt(), Motif.GEAR),
                files = 22,
            ),
            Spec(
                "Jane Austen/Pride and Prejudice", "Pride and Prejudice", "Jane Austen", 11.6,
                listOf("romance", "classics"),
                Look(0xFFB98A8A.toInt(), 0xFF5E3B3E.toInt(), 0xFFFBF2E6.toInt(), Motif.ROSE),
                files = 61,
            ),
            Spec(
                "Jane Austen/Emma", "Emma", "Jane Austen", 16.4, listOf("romance", "classics"),
                Look(0xFF7D8F6A.toInt(), 0xFF3A4430.toInt(), 0xFFF6F0DC.toInt(), Motif.ROSE),
                files = 55, finished = true, playedDaysAgo = 150.0,
            ),
            Spec(
                "Jane Austen/Sense and Sensibility", "Sense and Sensibility", "Jane Austen", 11.8,
                listOf("romance", "classics"),
                Look(0xFF6E7FA3.toInt(), 0xFF2E3650.toInt(), 0xFFF3EEE2.toInt(), Motif.STAR),
                files = 50,
            ),
            Spec(
                "Jane Austen/Persuasion", "Persuasion", "Jane Austen", 8.4, listOf("romance", "classics"),
                Look(0xFFC4A36A.toInt(), 0xFF6B5126.toInt(), 0xFF2B1D0B.toInt(), Motif.WAVES),
                files = 24,
            ),
            Spec(
                "Mary Shelley/Frankenstein", "Frankenstein", "Mary Shelley", 8.5,
                listOf("horror", "scifi", "classics"),
                Look(0xFF35473A.toInt(), 0xFF111A14.toInt(), 0xFFC9E3B0.toInt(), Motif.GEAR),
                files = 28,
            ),
            Spec(
                "Bram Stoker/Dracula", "Dracula", "Bram Stoker", 15.5, listOf("horror", "classics"),
                Look(0xFF4A0F14.toInt(), 0xFF140306.toInt(), 0xFFE7D3C1.toInt(), Motif.MOON),
                files = 27, progress = 0.18f, playedDaysAgo = 3.4,
            ),
            Spec(
                "H. G. Wells/The Time Machine", "The Time Machine", "H. G. Wells", 3.5, listOf("scifi", "classics"),
                Look(0xFF2E2A24.toInt(), 0xFF0F0D0B.toInt(), 0xFFE0B05C.toInt(), Motif.GEAR),
                files = 12,
            ),
            Spec(
                "H. G. Wells/The War of the Worlds", "The War of the Worlds", "H. G. Wells", 6.4,
                listOf("scifi", "classics"),
                Look(0xFF8A3B12.toInt(), 0xFF2E1205.toInt(), 0xFFF5D7A1.toInt(), Motif.SUN),
                files = 27,
            ),
            Spec(
                MOBY_DICK, "Moby-Dick", "Herman Melville", 21.1, listOf("adventure", "classics"),
                Look(0xFF16404F.toInt(), 0xFF07181F.toInt(), 0xFFEDE6D3.toInt(), Motif.WAVES),
                progress = mobyDick.progress, playedDaysAgo = 8.0,
            ),
            Spec(
                "Lewis Carroll/Alice/01 Alice's Adventures in Wonderland", "Alice's Adventures in Wonderland",
                "Lewis Carroll", 2.8, listOf("fantasy", "childrens"),
                Look(0xFF4F7CAC.toInt(), 0xFF203752.toInt(), 0xFFFFF3C7.toInt(), Motif.KEY),
                series = "Alice", index = 1, files = 12, finished = true, playedDaysAgo = 200.0,
            ),
            Spec(
                "Lewis Carroll/Alice/02 Through the Looking-Glass", "Through the Looking-Glass",
                "Lewis Carroll", 3.4, listOf("fantasy", "childrens"),
                Look(0xFFB0413E.toInt(), 0xFF4D1715.toInt(), 0xFFFFF0D6.toInt(), Motif.STAR),
                series = "Alice", index = 2, files = 12,
            ),
            Spec(
                "Lewis Carroll/Sylvie and Bruno", "Sylvie and Bruno", "Lewis Carroll", 9.3,
                listOf("fantasy", "childrens"),
                Look(0xFF8A6FB0.toInt(), 0xFF392A52.toInt(), 0xFFFFF4DF.toInt(), Motif.ROSE),
                files = 25,
            ),
            Spec(
                "Lewis Carroll/The Hunting of the Snark", "The Hunting of the Snark", "Lewis Carroll", 0.6,
                listOf("poetry", "childrens"),
                Look(0xFF3C6E71.toInt(), 0xFF173032.toInt(), 0xFFF2E5BE.toInt(), Motif.WAVES),
                files = 8,
            ),
            // A collection with two series inside it and novels that belong to neither — the shape
            // the collection shelf exists for. Numbered in the order Hetzel published them.
            verne(
                "01 Five Weeks in a Balloon", "Five Weeks in a Balloon", 7.9, 1, null, null,
                Look(0xFF3E7CA6.toInt(), 0xFF173247.toInt(), 0xFFF7E6B5.toInt(), Motif.SUN),
                files = 44, finished = true, playedDaysAgo = 120.0,
            ),
            verne(
                "03 A Journey to the Centre of the Earth", "A Journey to the Centre of the Earth", 8.6, 3, null, null,
                Look(0xFF7E3B1E.toInt(), 0xFF2B1209.toInt(), 0xFFF4C98A.toInt(), Motif.LAMP),
                files = 45, finished = true, playedDaysAgo = 75.0,
            ),
            verne(
                "Baltimore Gun Club/01 From the Earth to the Moon", "From the Earth to the Moon", 6.3, 4,
                "Baltimore Gun Club", 1,
                Look(0xFF1D2547.toInt(), 0xFF0A0E1F.toInt(), 0xFFE8E1C4.toInt(), Motif.MOON),
                files = 28, finished = true, playedDaysAgo = 30.0,
            ),
            verne(
                "Captain Nemo/01 In Search of the Castaways", "In Search of the Castaways", 21.0, 5,
                "Captain Nemo", 1,
                Look(0xFF5A6B2E.toInt(), 0xFF222A10.toInt(), 0xFFF2E8B8.toInt(), Motif.STAR),
                files = 64,
            ),
            verne(
                "Captain Nemo/02 Twenty Thousand Leagues Under the Seas", "Twenty Thousand Leagues Under the Seas",
                12.7, 6, "Captain Nemo", 2,
                Look(0xFF0F4C5C.toInt(), 0xFF041C22.toInt(), 0xFFB8E1DD.toInt(), Motif.WAVES),
                files = 47,
            ),
            verne(
                "Baltimore Gun Club/02 Around the Moon", "Around the Moon", 6.0, 7, "Baltimore Gun Club", 2,
                Look(0xFF4B3A6B.toInt(), 0xFF1B142B.toInt(), 0xFFEADDF7.toInt(), Motif.MOON),
                files = 23,
            ),
            verne(
                "11 Around the World in Eighty Days", "Around the World in Eighty Days", 6.9, 11, null, null,
                Look(0xFFA8452B.toInt(), 0xFF45190D.toInt(), 0xFFFBE7C6.toInt(), Motif.GEAR),
                files = 37, downloaded = true,
            ),
            verne(
                "Captain Nemo/03 The Mysterious Island", "The Mysterious Island", 19.6, 12, "Captain Nemo", 3,
                Look(0xFF2F5E4C.toInt(), 0xFF0F241C.toInt(), 0xFFD9F0D0.toInt(), Motif.KEY),
                files = 62,
            ),
            Spec(
                "Robert Louis Stevenson/Treasure Island", "Treasure Island", "Robert Louis Stevenson", 7.1,
                listOf("adventure", "childrens"),
                Look(0xFFC08A2E.toInt(), 0xFF5A3A0E.toInt(), 0xFF2A1A07.toInt(), Motif.STAR),
                files = 34,
            ),
            Spec(
                "Mark Twain/The Adventures of Tom Sawyer", "The Adventures of Tom Sawyer", "Mark Twain", 7.5,
                listOf("adventure", "childrens"),
                Look(0xFF5D7A3A.toInt(), 0xFF243315.toInt(), 0xFFF6EBC8.toInt(), Motif.SUN),
                files = 35,
            ),
        )
    }

    /** One of Verne's novels, filed under [VOYAGES] at [number] and in [series] where it has one. */
    private fun verne(
        folder: String,
        title: String,
        hours: Double,
        number: Int,
        series: String?,
        index: Int?,
        look: Look,
        files: Int,
        finished: Boolean = false,
        playedDaysAgo: Double? = null,
        downloaded: Boolean = false,
    ) = Spec(
        "Jules Verne/$VOYAGES/$folder", title, "Jules Verne", hours, listOf("adventure", "scifi"), look,
        series = series, index = index, collection = VOYAGES, collectionIndex = number, files = files,
        finished = finished, playedDaysAgo = playedDaysAgo, downloaded = downloaded,
    )

    val books: List<BookListItem> by lazy { specs.map(::item) }

    fun book(id: String): BookListItem = books.first { it.id == id }

    /** The Currently-listening shelf: started and not finished, most recently played first. */
    val listening: List<BookListItem>
        get() = books.filter { it.started && !it.finished }.sortedByDescending { it.lastPlayedAt }

    private fun item(s: Spec): BookListItem {
        val total = (s.hours * HOUR).toLong()
        val progress = if (s.finished) 1f else s.progress
        val timeLeft = progress?.let { (total * (1 - it)).toLong() }
        return BookListItem(
            id = s.id,
            title = s.title,
            authors = listOf(s.author),
            isMultiFile = s.files > 1,
            fileCount = s.files,
            coverModel = cover(s),
            hasCustomCover = false,
            series = s.series,
            seriesIndex = s.index,
            collection = s.collection,
            collectionIndex = s.collectionIndex,
            genres = s.genres,
            language = "en",
            tags = emptyList(),
            documents = s.documents,
            totalDurationMs = total,
            timeLeftMs = timeLeft,
            progress = progress,
            lastPlayedAt = s.playedDaysAgo?.let { NOW - (it * DAY).toLong() },
            started = progress != null,
            finishedOverride = if (s.finished) true else null,
            downloadOnPlayOverride = null,
            downloadStatus = if (s.downloaded) DownloadStatus.DONE else null,
            downloadedFiles = if (s.downloaded) s.files else 0,
            hidden = false,
        )
    }

    /**
     * A book being listened to: its chapters, which one is current and how far into it.
     *
     * [inFile] is a book that is ONE file with chapter marks inside it, as against one file per
     * chapter. The player and the chapter picker draw the two differently — only marks inside a file
     * are names somebody chose, so only they are listed by name — and the screenshots show both.
     */
    class Listening(
        val bookId: String,
        hours: Double,
        names: List<String>,
        val current: Int,
        into: Double,
        val inFile: Boolean,
        title: (Int, String) -> String,
    ) {
        val totalMs: Long = (hours * HOUR).toLong()

        val chapters: List<PlayerChapter> = run {
            // Uneven, as real chapters are; rescaled so they add up to the book.
            val weights = names.indices.map { 0.75 + ((it * 37) % 11) / 20.0 }
            val sum = weights.sum()
            var start = 0L
            names.mapIndexed { i, name ->
                val length = (totalMs * weights[i] / sum).toLong()
                PlayerChapter(
                    title = title(i, name),
                    mediaItemIndex = if (inFile) null else i,
                    startMs = if (inFile) start else null,
                    isCurrent = i == current,
                    lengthMs = length,
                    startInBookMs = start,
                ).also { start += length }
            }
        }

        val intoChapterMs: Long = ((chapters[current].lengthMs ?: 0L) * into).toLong()

        val elapsedMs: Long = (chapters[current].startInBookMs ?: 0L) + intoChapterMs

        val progress: Float get() = elapsedMs.toFloat() / totalMs
    }

    /** The Odyssey as LibriVox publishes it — one file per book — part way through Book IX. */
    val odyssey: Listening by lazy {
        Listening(PLAYING, 12.1, ODYSSEY_BOOKS, current = 8, into = 0.45, inFile = false) { i, name ->
            "Book ${roman(i + 1)} – $name"
        }
    }

    /** Moby-Dick as one M4B with its chapters marked inside, a few chapters in. */
    val mobyDick: Listening by lazy {
        val names = MOBY_DICK_CHAPTERS + (MOBY_DICK_CHAPTERS.size + 1..135).map { "" }
        Listening(MOBY_DICK, 21.1, names, current = 8, into = 0.3, inFile = true) { _, name -> name }
    }

    private val ODYSSEY_BOOKS = listOf(
        "Telemachus and the suitors", "Telemachus calls an assembly", "At Pylos with Nestor",
        "In Sparta with Menelaus", "Calypso", "Nausicaa", "The palace of Alcinous",
        "The games of the Phaeacians", "The Cyclops", "Aeolus and Circe", "The land of the dead",
        "The Sirens, Scylla and Charybdis", "Home to Ithaca", "Eumaeus the swineherd",
        "Telemachus returns", "Father and son", "The beggar in the hall", "Irus the beggar",
        "Eurycleia's recognition", "Omens", "The bow", "The slaying of the suitors",
        "Penelope", "The last of the feud",
    )

    /** The first forty of Melville's own chapter titles; the picker scrolls to Chapter 9. */
    private val MOBY_DICK_CHAPTERS = listOf(
        "Loomings", "The Carpet-Bag", "The Spouter-Inn", "The Counterpane", "Breakfast", "The Street",
        "The Chapel", "The Pulpit", "The Sermon", "A Bosom Friend", "Nightgown", "Biographical",
        "Wheelbarrow", "Nantucket", "Chowder", "The Ship", "The Ramadan", "His Mark", "The Prophet",
        "All Astir", "Going Aboard", "Merry Christmas", "The Lee Shore", "The Advocate", "Postscript",
        "Knights and Squires", "Knights and Squires", "Ahab", "Enter Ahab; to Him, Stubb", "The Pipe",
        "Queen Mab", "Cetology", "The Specksnyder", "The Cabin-Table", "The Mast-Head",
        "The Quarter-Deck", "Sunset", "Dusk", "First Night-Watch", "Midnight, Forecastle",
    )

    private fun roman(n: Int): String {
        val table = listOf(10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I")
        var left = n
        return buildString {
            for ((value, glyph) in table) while (left >= value) { append(glyph); left -= value }
        }
    }

    // ── covers ───────────────────────────────────────────────────────────────────────────────

    private const val SIZE = 600

    /** Where the short rule over the author's name sits. */
    private const val RULE_Y = SIZE - 118f

    /** How tall the largest motif is drawn at full size — the sun, rays and all. */
    private const val MOTIF_EXTENT = 250f

    private fun cover(s: Spec): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val look = s.look
        val size = SIZE.toFloat()

        canvas.drawRect(
            0f, 0f, size, size,
            Paint().apply { shader = LinearGradient(0f, 0f, 0f, size, look.top, look.bottom, Shader.TileMode.CLAMP) },
        )
        // A ruled frame, the one ornament every cover shares, so they read as one edition.
        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = look.ink
            alpha = 150
            strokeWidth = 3f
        }
        canvas.drawRect(RectF(26f, 26f, size - 26f, size - 26f), rule)
        canvas.drawRect(RectF(36f, 36f, size - 36f, size - 36f), rule.apply { strokeWidth = 1.5f; alpha = 110 })

        val width = (size * 0.74f).toInt()
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = look.ink
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textSize = when {
                s.title.length > 28 -> 50f
                s.title.length > 16 -> 58f
                else -> 70f
            }
            // Never break inside a word: shrink until the longest one fits on its own line.
            val longest = s.title.split(' ').maxBy { measureText(it) }
            while (measureText(longest) > width && textSize > 24f) textSize -= 2f
        }
        val layout = StaticLayout.Builder.obtain(s.title, 0, s.title.length, title, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.05f)
            .build()
        // Hung from a quarter of the way down, whatever its length. The shelf prints a series number
        // over a cover's top corner, and a title set any higher lost its first letters under it.
        val titleTop = size * 0.26f
        val titleBottom = titleTop + layout.height
        // The motif takes whatever is left between the title and the rule above the author, shrunk
        // to fit — so a long title gets a small ornament rather than one drawn through its words.
        val gap = RULE_Y - titleBottom
        motif(canvas, look, cy = titleBottom + gap / 2f, scale = ((gap - 24f) / MOTIF_EXTENT).coerceIn(0.3f, 1f))
        canvas.save()
        canvas.translate((size - width) / 2f, titleTop)
        layout.draw(canvas)
        canvas.restore()

        val byline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = look.ink
            alpha = 220
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = 26f
            letterSpacing = 0.18f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(s.author.uppercase(), size / 2f, size - 76f, byline)
        canvas.drawLine(size / 2f - 40f, RULE_Y, size / 2f + 40f, RULE_Y, rule.apply { alpha = 160; strokeWidth = 2f })
        return bitmap
    }

    private fun motif(canvas: Canvas, look: Look, cy: Float, scale: Float) {
        val size = SIZE.toFloat()
        val cx = size / 2f
        canvas.save()
        canvas.scale(scale, scale, cx, cy)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = look.ink; alpha = 46 }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = look.ink
            alpha = 70
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        when (look.motif) {
            Motif.SUN -> {
                canvas.drawCircle(cx, cy, 70f, paint)
                for (i in 0 until 16) {
                    val a = Math.toRadians(i * 22.5)
                    canvas.drawLine(
                        cx + 90f * Math.cos(a).toFloat(), cy + 90f * Math.sin(a).toFloat(),
                        cx + 125f * Math.cos(a).toFloat(), cy + 125f * Math.sin(a).toFloat(), stroke,
                    )
                }
            }
            Motif.WAVES -> for (row in 0 until 4) {
                val y = cy - 40f + row * 34f
                val path = Path().apply {
                    moveTo(60f, y)
                    var x = 60f
                    while (x < size - 60f) {
                        quadTo(x + 30f, y - 22f, x + 60f, y)
                        x += 60f
                    }
                }
                canvas.drawPath(path, stroke)
            }
            Motif.MOON -> {
                canvas.drawCircle(cx, cy, 82f, paint)
                canvas.drawCircle(cx + 38f, cy - 18f, 76f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = look.bottom; alpha = 200 })
            }
            Motif.STAR -> {
                val path = Path()
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) 105f else 42f
                    val a = Math.toRadians(-90.0 + i * 36)
                    val x = cx + r * Math.cos(a).toFloat()
                    val y = cy + r * Math.sin(a).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                canvas.drawPath(path, paint)
            }
            Motif.LAMP -> {
                canvas.drawCircle(cx, cy - 10f, 58f, paint)
                canvas.drawRect(cx - 34f, cy + 52f, cx + 34f, cy + 66f, paint)
                canvas.drawCircle(cx, cy - 10f, 100f, stroke)
            }
            Motif.ROSE -> for (i in 0 until 6) {
                canvas.save()
                canvas.rotate(i * 30f, cx, cy)
                canvas.drawOval(RectF(cx - 28f, cy - 100f, cx + 28f, cy + 100f), stroke)
                canvas.restore()
            }
            Motif.GEAR -> {
                canvas.drawCircle(cx, cy, 64f, stroke)
                canvas.drawCircle(cx, cy, 22f, paint)
                for (i in 0 until 12) {
                    canvas.save()
                    canvas.rotate(i * 30f, cx, cy)
                    canvas.drawRect(cx - 10f, cy - 96f, cx + 10f, cy - 66f, paint)
                    canvas.restore()
                }
            }
            Motif.KEY -> {
                canvas.drawCircle(cx - 60f, cy, 40f, stroke)
                canvas.drawLine(cx - 20f, cy, cx + 100f, cy, stroke)
                canvas.drawLine(cx + 70f, cy, cx + 70f, cy + 30f, stroke)
                canvas.drawLine(cx + 92f, cy, cx + 92f, cy + 22f, stroke)
            }
        }
        canvas.restore()
    }
}
