package com.geozelot.homer.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Marks Homer draws itself, because a stock icon that merely sounds right is not a design decision.
 *
 * [Spines] exists because of a specific failure: a mark was designed, reviewed at true size, and
 * approved — and then implemented as `Icons.Filled.CollectionsBookmark` on the strength of the name
 * matching the description. It renders as something else. The mock and the device disagreed, which is
 * the one thing a design review is supposed to rule out, and no amount of care in the review could
 * have caught it because the review never looked at the stock icon.
 *
 * So the shape lives here, in the same coordinates it was drawn and approved in. What ships is what
 * was shown.
 */
object HomerIcons {

    /**
     * A 24-unit vector, the same viewport the Material set uses — so the same `size` reads as the
     * same weight beside a stock glyph.
     */
    private fun icon(name: String, body: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(body).build()

    /**
     * A square-cornered `[` — two arms and the stroke joining them.
     *
     * Written once because four of the marks here are made of these, and a bracket whose arm is a
     * tenth of a unit off its neighbour is a mark that looks hand-set at 3x and wrong at 1x.
     */
    private fun PathBuilder.bracket(left: Float, top: Float, bottom: Float, arm: Float, stroke: Float) {
        moveTo(left, top)
        horizontalLineTo(left + arm)
        verticalLineTo(top + stroke)
        horizontalLineTo(left + stroke)
        verticalLineTo(bottom - stroke)
        horizontalLineTo(left + arm)
        verticalLineTo(bottom)
        horizontalLineTo(left)
        close()
    }

    /** An axis-aligned rectangle, because `rect` reads better than four line commands. */
    private fun PathBuilder.rect(left: Float, top: Float, width: Float, height: Float) {
        moveTo(left, top)
        horizontalLineToRelative(width)
        verticalLineToRelative(height)
        horizontalLineToRelative(-width)
        close()
    }

    /**
     * Three upright book spines, the last carrying a bookmark notch. A shelf, at badge size.
     *
     * ## Why this shape and not a stock one
     *
     * It is the only candidate tested that still reads as *books* at 13dp. The framed alternatives
     * (`LibraryBooks`) lose their contents to the frame; the open book (`MenuBook`) reads as ONE book
     * being read, which is the opposite of what a shelf badge claims; the fanned card shapes rely on
     * opacity steps that are gone well before 15dp; and the layered lozenges are the Arrange chip's
     * own glyph, so re-using them would make one symbol mean two things.
     *
     * Three uprights survive on silhouette alone, and three uprights is the whole message. The
     * bookmark notch is the part that goes soft first, which is fine — it is the grace note, not the
     * meaning.
     *
     * ## The geometry, deliberately blunt
     *
     * A 24-unit viewport to match the Material set it sits beside, so the same `size` reads the same
     * weight. Plain rectangles with square corners: the design carries a 1-unit corner radius, which
     * at 13–17dp is a third of a pixel and costs four arcs per corner to express. The notch is a
     * single V cut into the third spine's foot.
     *
     * Filled black because [androidx.compose.material3.Icon] paints its own tint over the vector;
     * the fill colour here is never seen.
     */
    /**
     * A bracket holding one block: a **series**, at chip size.
     *
     * ## Why a bracket and not a picture of books
     *
     * The question these marks answer is not "are these books" — everything in Homer is books. It
     * is *what contains what*: a series holds volumes, a collection holds series. A bracket says
     * containment in the one way that still reads at 14dp, because it is structure rather than
     * depiction: the silhouette itself carries the meaning, and silhouette is all that survives.
     *
     * The pair before this was a shelf and an open book, and it failed for exactly the opposite
     * reason — two pictures whose difference lived in detail that had already dissolved. See
     * [com.geozelot.homer.ui.home.ShelfBadge].
     *
     * Strokes are 2.4 units, which is 1.4dp at chip size: the thinnest thing here that still holds
     * a full pixel at every density Homer runs on.
     */
    val SeriesBracket: ImageVector by lazy {
        icon("SeriesBracket") {
            path(fill = SolidColor(Color.Black)) {
                bracket(left = 3.5f, top = 5f, bottom = 19f, arm = 6f, stroke = 2.4f)
            }
            path(fill = SolidColor(Color.Black)) {
                rect(left = 12.5f, top = 9.4f, width = 8f, height = 5.2f)
            }
        }
    }

    /**
     * A bracket holding a bracket holding a block: a **collection**.
     *
     * The inner bracket is shorter than the outer one, and that stagger is the whole message — at
     * the size this is drawn the two strokes can blur into each other, and what still reads is one
     * mark nested inside another. Contained, not merely doubled.
     */
    val CollectionBracket: ImageVector by lazy {
        icon("CollectionBracket") {
            path(fill = SolidColor(Color.Black)) {
                bracket(left = 1.5f, top = 3.5f, bottom = 20.5f, arm = 5.5f, stroke = 2.2f)
            }
            path(fill = SolidColor(Color.Black)) {
                bracket(left = 8.5f, top = 6.5f, bottom = 17.5f, arm = 5f, stroke = 2.2f)
            }
            path(fill = SolidColor(Color.Black)) {
                rect(left = 15.5f, top = 10f, width = 6f, height = 4f)
            }
        }
    }

    /**
     * The same bracket, holding a book: a series **on a cover**.
     *
     * A cover corner has a few more dp than a chip does, and it sits on artwork rather than on a
     * flat surface — so it can afford the block becoming a thing, and it needs to, because a bare
     * rectangle on a photograph reads as a smudge.
     *
     * The book is one SOLID shape with a slot near its left edge for the spine. An outlined book
     * was tried first: its frame thins to nothing against a 78%-opaque scrim, and by 13dp on a list
     * row it fills in and is a plain rectangle again. Solid keeps its silhouette, which is the only
     * property that survives down there.
     */
    val SeriesShelf: ImageVector by lazy {
        icon("SeriesShelf") {
            path(fill = SolidColor(Color.Black)) {
                bracket(left = 2.5f, top = 4f, bottom = 20f, arm = 6f, stroke = 2.6f)
            }
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                rect(left = 11f, top = 5.5f, width = 10.5f, height = 13f)
                // The spine, as a slot the scrim shows through. EvenOdd is what makes it a hole
                // rather than a second filled bar.
                rect(left = 13.5f, top = 5.5f, width = 1.5f, height = 13f)
            }
        }
    }

    /** Bracket in bracket, holding a book: a collection **on a cover**. See [SeriesShelf]. */
    val CollectionShelf: ImageVector by lazy {
        icon("CollectionShelf") {
            path(fill = SolidColor(Color.Black)) {
                bracket(left = 0.5f, top = 3f, bottom = 21f, arm = 5f, stroke = 2.2f)
            }
            path(fill = SolidColor(Color.Black)) {
                bracket(left = 6.8f, top = 6f, bottom = 18f, arm = 4.5f, stroke = 2.2f)
            }
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                rect(left = 13f, top = 7.5f, width = 9f, height = 9f)
                rect(left = 15.2f, top = 7.5f, width = 1.4f, height = 9f)
            }
        }
    }

    /**
     * A person: the **author**.
     *
     * Deliberately not a bracket. The other three marks all answer "what contains this", and an
     * author does not contain a book — they wrote it. A different kind of fact should not wear the
     * family's structural mark, or the family stops meaning anything.
     *
     * A head over shoulders is the most legible small glyph there is, because it is the shape
     * people are best at recognising. No neck, no arms: both are detail that closes up by 13dp.
     */
    val Author: ImageVector by lazy {
        icon("Author") {
            path(fill = SolidColor(Color.Black)) {
                // The head, as four arcs — a circle of radius 3.6 centred at (12, 7.4).
                moveTo(12f, 3.8f)
                arcToRelative(3.6f, 3.6f, 0f, true, true, 0f, 7.2f)
                arcToRelative(3.6f, 3.6f, 0f, true, true, 0f, -7.2f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                // The shoulders: a slab with its top corners rounded off by one arc each.
                moveTo(4.6f, 21f)
                verticalLineTo(19.4f)
                arcToRelative(7.4f, 7.4f, 0f, false, true, 14.8f, 0f)
                verticalLineTo(21f)
                close()
            }
        }
    }

    /**
     * A tag with a punched hole: the **genre**.
     *
     * A tag is what the world already uses for "this is filed under", and a genre is the one fact
     * on a card that is a filing decision rather than a property of the book. The hole is what
     * makes it a tag and not a diamond — it is the first thing to close up at 13dp, and it is
     * allowed to, in the same way [Spines]' bookmark notch is: it is the grace note, not the
     * meaning.
     */
    val Genre: ImageVector by lazy {
        icon("Genre") {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(3f, 3f)
                horizontalLineTo(11.5f)
                lineTo(21f, 12.5f)
                lineTo(12.5f, 21f)
                lineTo(3f, 11.5f)
                close()
                // The hole, punched.
                moveTo(6.8f, 5.2f)
                arcToRelative(1.7f, 1.7f, 0f, true, false, 0f, 3.4f)
                arcToRelative(1.7f, 1.7f, 0f, true, false, 0f, -3.4f)
                close()
            }
        }
    }

    val Spines: ImageVector by lazy {
        ImageVector.Builder(
            name = "Spines",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // Left spine.
                moveTo(2f, 3f)
                lineTo(6.4f, 3f)
                lineTo(6.4f, 21f)
                lineTo(2f, 21f)
                close()
                // Middle spine.
                moveTo(8f, 3f)
                lineTo(12.4f, 3f)
                lineTo(12.4f, 21f)
                lineTo(8f, 21f)
                close()
                // Right spine, wider, with the bookmark cut out of its foot.
                moveTo(14.4f, 3f)
                lineTo(21.6f, 3f)
                lineTo(21.6f, 21f)
                lineTo(18f, 18.6f)
                lineTo(14.4f, 21f)
                close()
            }
        }.build()
    }
}
