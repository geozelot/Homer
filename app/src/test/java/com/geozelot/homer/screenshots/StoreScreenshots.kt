package com.geozelot.homer.screenshots

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.drawToBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.unit.dp
import coil.Coil
import coil.ImageLoader
import com.geozelot.homer.R
import com.geozelot.homer.ui.home.BookDetailsCard
import com.geozelot.homer.ui.home.LibraryShelving
import com.geozelot.homer.ui.player.ChapterPickerDialog
import com.geozelot.homer.ui.theme.HomerTheme
import kotlinx.coroutines.Dispatchers
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The phone screenshots for the README and the F-Droid listing, drawn on the JVM.
 *
 * Skipped in an ordinary test run. Asked for, it writes F-Droid's own layout under the directory
 * given — `fastlane/metadata/android` is where F-Droid looks:
 *
 * ```
 * ./gradlew :app:testDebugUnitTest --tests '*StoreScreenshots*' -Pscreenshots=$PWD/fastlane/metadata/android
 * ```
 *
 * The window is a 411×914dp phone at 420dpi — 1080×2400 pixels, the shape of most current phones —
 * with a status bar and a gesture bar handed to it as real insets, so every component pads itself
 * exactly as it does on a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h914dp-420dpi")
class StoreScreenshots {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val root: String? = System.getProperty("homer.screenshots.dir")

    @Before
    fun setUp() {
        assumeTrue("screenshots only run with -Pscreenshots=<dir>", root != null)
        // Covers are Bitmaps, loaded by Coil like any other cover. Unconfined, so a load finishes
        // inside the frame that asked for it and the capture never races an image still decoding;
        // no hardware bitmaps, which a JVM has nothing to back.
        Coil.setImageLoader(
            ImageLoader.Builder(rule.activity)
                .dispatcher(Dispatchers.Unconfined)
                .allowHardware(false)
                .crossfade(false)
                .build(),
        )
        rule.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(rule.activity.window, false) }
    }

    @Test
    fun library() = shoot("1_library") { Scenes.Library(gridView = true) }

    @Test
    fun player() = shoot("2_player") { Scenes.Player() }

    @Test
    fun libraryList() = shoot("3_library_list") {
        Scenes.Library(gridView = false, shelving = LibraryShelving.AUTHOR)
    }

    @Test
    fun chapters() = shoot("4_chapters") {
        // Moby-Dick, not the Odyssey: one file with its chapters marked inside, which is the kind of
        // book the picker lists by name.
        val moby = DemoLibrary.mobyDick
        Scenes.Player(moby, sleepRemainingMs = null)
        ChapterPickerDialog(
            chapters = moby.chapters,
            bookTotalMs = moby.totalMs,
            onJump = {},
            onDismiss = {},
        )
    }

    @Test
    fun details() = shoot("5_details") {
        Scenes.Library(gridView = true)
        BookDetailsCard(
            book = DemoLibrary.book(DemoLibrary.DETAILED),
            onEdit = {},
            onFilter = {},
            onOpenDocument = {},
            onDismiss = {},
        )
    }

    // ── store graphics ───────────────────────────────────────────────────────────────────────

    /** The launcher icon at 512px: its own background and foreground, in a rounded square. */
    @Test
    fun icon() {
        val size = 512
        val image = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        val corner = size * 0.22f
        val shape = Path().apply { addRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), corner, corner, Path.Direction.CW) }
        canvas.clipPath(shape)
        canvas.drawColor(ContextCompat.getColor(rule.activity, R.color.ic_launcher_background))
        // An adaptive icon's layers are 108 units with the visible 72 in the middle; scale so those
        // 72 fill the square, exactly as a launcher's mask frames them.
        val layer = (size * 108f / 72f).toInt()
        val offset = (size - layer) / 2
        ContextCompat.getDrawable(rule.activity, R.drawable.ic_launcher_foreground)!!.apply {
            setBounds(offset, offset, offset + layer, offset + layer)
            draw(canvas)
        }
        save(image, "icon.png")
    }

    /** F-Droid's 1024×500 banner: the wordmark and what Homer is, beside a fan of covers. */
    @Test
    @Config(qualifiers = "w1024dp-h500dp-land-mdpi")
    fun featureGraphic() {
        rule.setContent { HomerTheme { Scenes.Banner() } }
        settle()
        save(rule.onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap(), "featureGraphic.png")
    }

    // ── capture ──────────────────────────────────────────────────────────────────────────────

    private fun shoot(name: String, content: @Composable () -> Unit) {
        rule.setContent {
            HomerTheme {
                Scenes.Phone(statusBar = STATUS_BAR, navigationBar = NAVIGATION_BAR) { content() }
            }
        }
        applyInsets()
        settle()

        val windows = rule.onAllNodes(isRoot()).fetchSemanticsNodes().size
        val screen = rule.onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap()
        val image = if (windows == 1) {
            screen
        } else {
            // A dialog is a window of its own, so it is drawn on its own and laid over the screen
            // the way the system shows it: centred, over a dimmed backdrop. Drawn from its view
            // rather than captured — the capture reads pixels back from the ACTIVITY's window here,
            // which gave the screen a second time in the dialog's frame.
            val dialogView = (rule.onAllNodes(isRoot())[1].fetchSemanticsNode().root as ViewRootForTest).view.rootView
            val dialog = dialogView.drawToBitmap()
            screen.copy(Bitmap.Config.ARGB_8888, true).also { out ->
                val canvas = Canvas(out)
                canvas.drawColor(Color.argb(SCRIM_ALPHA, 0, 0, 0))
                canvas.drawBitmap(
                    dialog,
                    (out.width - dialog.width) / 2f,
                    (out.height - dialog.height) / 2f,
                    null,
                )
            }
        }
        save(image, "phoneScreenshots/$name.png")
    }

    /** Writes [image] under the listing's image folder, at [path]. */
    private fun save(image: Bitmap, path: String) {
        val file = File(root, "en-US/images/$path")
        file.parentFile?.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("screenshot: ${file.absolutePath} (${image.width}x${image.height})")
    }

    /** Hands the window a status bar and a gesture bar, as the system would. */
    private fun applyInsets() {
        val density = rule.activity.resources.displayMetrics.density
        val status = (STATUS_BAR.value * density).toInt()
        val navigation = (NAVIGATION_BAR.value * density).toInt()
        rule.runOnUiThread {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, status, 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, navigation))
                .build()
            ViewCompat.dispatchApplyWindowInsets(rule.activity.window.decorView, insets)
        }
    }

    /** Lets every animation, inset pass and image load finish before anything is captured. */
    private fun settle() {
        repeat(SETTLE_ROUNDS) {
            rule.mainClock.advanceTimeBy(250)
            rule.waitForIdle()
        }
    }

    private companion object {
        val STATUS_BAR = 32.dp
        val NAVIGATION_BAR = 24.dp
        const val SCRIM_ALPHA = 153 // the platform's default dialog dim, 0.6
        const val SETTLE_ROUNDS = 8
    }
}
