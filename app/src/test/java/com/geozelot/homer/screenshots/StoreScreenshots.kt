package com.geozelot.homer.screenshots

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.unit.dp
import coil.Coil
import coil.ImageLoader
import com.geozelot.homer.R
import com.geozelot.homer.ui.settings.LibrarySyncScreen
import com.geozelot.homer.ui.settings.PlaybackSettingsScreen
import com.geozelot.homer.ui.settings.SettingsHubScreen
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
 * The window is 1080×2400 pixels, the shape of most current phones, at 360dpi — Android's "Small"
 * display size on such a phone, a 480×1067dp window. That is one step below the default, and the
 * step where the grid fits a fourth column. A status bar and a gesture bar are handed to it as real
 * insets, so every component pads itself exactly as it does on a device.
 *
 * The settings pages are the real screens over stand-in ViewModels — see [DemoSettings].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w480dp-h1067dp-360dpi")
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

    // Shelved by author and stacked by collection: the arrangement that shows the most of what the
    // shelf can do in one screen.
    @Test
    fun libraryGrid() = shoot("1_library_grid") { Scenes.Library(gridView = true) }

    @Test
    fun libraryList() = shoot("2_library_list") { Scenes.Library(gridView = false) }

    /** Verne's collection opened: its two series under their own headings, then the novels in neither. */
    @Test
    fun collection() = shoot("3_collection") {
        Scenes.Library(gridView = true, open = listOf(DemoLibrary.VOYAGES))
    }

    @Test
    fun arrange() = shoot("4_arrange") { Scenes.Library(gridView = true, arrangeOpen = true) }

    /** Two letters typed: the shelf already filtered, and a genre, an author and two series on offer. */
    @Test
    fun search() = shoot("5_search") { Scenes.Library(gridView = true, searchOpen = true, query = "ho") }

    @Test
    fun player() = shoot("6_player") { Scenes.Player() }

    @Test
    fun settings() {
        val viewModel = DemoSettings.homeViewModel()
        val updates = DemoSettings.updateViewModel()
        shoot("7_settings") {
            SettingsHubScreen(
                viewModel = viewModel,
                onBack = {},
                onOpenLibrary = {},
                onOpenUpkeep = {},
                onOpenStorage = {},
                onOpenBrowsing = {},
                onOpenPlayback = {},
                onOpenPrivacy = {},
                onOpenAbout = {},
                updateViewModel = updates,
            )
        }
    }

    @Test
    fun settingsSync() {
        val viewModel = DemoSettings.homeViewModel()
        shoot("8_settings_sync") { LibrarySyncScreen(viewModel = viewModel, onChange = {}, onBack = {}) }
    }

    @Test
    fun settingsPlayback() {
        val viewModel = DemoSettings.homeViewModel()
        shoot("9_settings_playback") { PlaybackSettingsScreen(viewModel = viewModel, onBack = {}) }
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
        save(rule.onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap(), "phoneScreenshots/$name.png")
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
        const val SETTLE_ROUNDS = 8
    }
}
