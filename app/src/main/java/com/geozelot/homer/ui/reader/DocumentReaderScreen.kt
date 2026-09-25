package com.geozelot.homer.ui.reader

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.R
import com.geozelot.homer.data.library.documentLabel
import com.geozelot.homer.ui.components.LeadingIconInset
import com.geozelot.homer.ui.components.ScreenInset
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Ground
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.SectionLabel
import com.geozelot.homer.ui.theme.SerifTitle
import com.geozelot.homer.ui.theme.Studio
import com.geozelot.homer.ui.theme.TabularSmall
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

// ── Reading the booklet ──────────────────────────────────────────────────────
//
// A page at a time, swiped sideways, pinch to zoom.
//
// The alternative — one long scroll of every page — is what a PDF reader on a desktop does, and it
// is wrong here for a reason that has nothing to do with taste: it keeps several full-resolution
// bitmaps alive at once, and a zoomed A4 page is twenty-odd megabytes. A pager holds ONE, which is
// what makes it affordable to render that one page at the zoom the reader has actually asked for
// rather than blowing up a blurry thumbnail.

/** Past this the page is drawn from a re-rendered bitmap; below it, zoom is plain magnification. */
private const val MaxRenderScale = 3f

/** How far a pinch goes. Beyond 4× a booklet page is a few words and no context. */
private const val MaxZoom = 4f

/** Where double-tap lands: enough to read small print, not so far that the page is lost. */
private const val DoubleTapZoom = 2.5f

/** Zoom at (or near) rest — the point at which the pager may take horizontal drags again. */
private const val Unzoomed = 1.01f

/** A pinch fires continuously; re-rendering follows only once it stops moving. */
private const val SettleMs = 180L

/**
 * A supplementary document, full screen.
 *
 * Reached from the details card and from the player's top bar. Its own destination rather than a
 * sheet: it is a thing you read for minutes, and every dp of the window belongs to the page.
 */
@Composable
fun DocumentReaderScreen(
    onBack: () -> Unit,
    viewModel: DocumentReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var page by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, start = LeadingIconInset, end = ScreenInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = Muted,
                )
            }
            Text(
                documentLabel(viewModel.path),
                style = SerifTitle.copy(fontSize = 19.sp),
                color = Parchment,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp).weight(1f),
            )
            // Set in tabular figures so the counter does not shuffle sideways every time the page
            // number changes width — the same reason every other number in Homer is.
            // Only while a document is actually open: on a retry the count from the last attempt
            // would otherwise sit over a spinner, counting pages of nothing.
            if (pageCount > 0 && state is DocumentState.Ready) {
                Text(
                    stringResource(R.string.reader_page_of, page + 1, pageCount),
                    style = TabularSmall,
                    color = Muted,
                )
            }
        }

        // `weight`, not `fillMaxSize`: in a Column the latter asks for the WHOLE height rather
        // than what is left under the header, and the page would be pushed off the bottom by
        // exactly the height of the bar above it.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 6.dp)
                .background(Studio),
            contentAlignment = Alignment.Center,
        ) {
            when (val current = state) {
                is DocumentState.Loading -> CircularProgressIndicator(
                    color = Amber,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(28.dp),
                )
                is DocumentState.Ready -> PdfReader(
                    uri = current.uri,
                    onUnreadable = viewModel::reportUnreadable,
                    onPosition = { at, total -> page = at; pageCount = total },
                )
                is DocumentState.Missing -> Failure(
                    message = stringResource(R.string.reader_not_here),
                    onRetry = viewModel::fetch,
                )
                is DocumentState.Unreadable -> Failure(
                    message = stringResource(R.string.reader_unreadable),
                    onRetry = null,
                )
            }
        }
    }
}

/**
 * What went wrong, and whether trying again could help.
 *
 * No Retry on an unreadable file, deliberately: the bytes are on the device and Homer cannot make
 * sense of them, so a second attempt fails identically. A button that can only ever fail is worse
 * than no button.
 */
@Composable
private fun Failure(message: String, onRetry: (() -> Unit)?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = 40.dp),
    ) {
        Text(
            message,
            color = Muted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
        )
        onRetry?.let { retry ->
            Text(
                stringResource(R.string.action_retry).uppercase(),
                style = SectionLabel,
                color = Amber,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = retry)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * The pager, and the one zoom state it shares.
 *
 * Zoom is held HERE rather than inside each page, because the pager has to know about it: a
 * horizontal drag on a zoomed page means "move the page", and on an unzoomed one it means "next
 * page". `userScrollEnabled` is how that is settled — cleanly, and without two gesture detectors
 * fighting over the same drag. Changing page resets the zoom, which is also what a reader expects:
 * arriving at a new page magnified into its top-left corner is disorienting.
 */
@Composable
private fun PdfReader(uri: Uri, onUnreadable: () -> Unit, onPosition: (Int, Int) -> Unit) {
    val context = LocalContext.current
    val reportUnreadable by rememberUpdatedState(onUnreadable)
    val document by produceState<PdfPages?>(null, uri) {
        val opened = PdfPages.open(context, uri)
        value = opened
        if (opened == null) reportUnreadable()
        // Holds the effect open for the composable's lifetime, and closes the file when it leaves.
        // The descriptor is a real OS handle; leaking one per opened booklet is not survivable.
        awaitDispose { opened?.close() }
    }

    val pages = document
    if (pages == null) {
        CircularProgressIndicator(color = Amber, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        return
    }

    val pagerState = rememberPagerState(pageCount = { pages.pageCount })
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(pagerState.currentPage) {
        zoom = 1f
        offset = Offset.Zero
        onPosition(pagerState.currentPage, pages.pageCount)
    }

    HorizontalPager(
        state = pagerState,
        userScrollEnabled = zoom <= Unzoomed,
        modifier = Modifier.fillMaxSize(),
    ) { index ->
        val active = index == pagerState.currentPage
        PdfPageView(
            pages = pages,
            index = index,
            zoom = if (active) zoom else 1f,
            offset = if (active) offset else Offset.Zero,
            onTransform = { nextZoom, nextOffset -> zoom = nextZoom; offset = nextOffset },
        )
    }
}

/**
 * One page: a bitmap, a pinch, and a re-render once the pinch stops.
 *
 * The re-render is the point. Scaling a bitmap rendered at screen width up to 3× gives soft,
 * unreadable small print — exactly the print somebody zooms in to read. So the zoom drives the
 * RENDER, not just the draw: when the gesture settles, the page is drawn again at that many times
 * screen width, and the same pixels are sharp. Going back to 1× re-renders too, which is what
 * releases the large bitmap.
 */
@Composable
private fun PdfPageView(
    pages: PdfPages,
    index: Int,
    zoom: Float,
    offset: Offset,
    onTransform: (Float, Offset) -> Unit,
) {
    var container by remember { mutableStateOf(IntSize.Zero) }
    var renderScale by remember(index) { mutableFloatStateOf(1f) }
    var bitmap by remember(index) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    // Read inside the gesture handler, which outlives the composition that set up `pointerInput`.
    val currentZoom by rememberUpdatedState(zoom)
    val currentOffset by rememberUpdatedState(offset)

    LaunchedEffect(zoom) {
        delay(SettleMs)
        renderScale = zoom.coerceIn(1f, MaxRenderScale)
    }
    LaunchedEffect(index, container.width, renderScale) {
        if (container.width > 0) {
            bitmap = pages
                .render(index, (container.width * renderScale).roundToInt())
                ?.asImageBitmap()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { container = it }
            // Written out rather than `detectTransformGestures`, and that IS the fix for turning
            // the page.
            //
            // `detectTransformGestures` consumes every change once the gesture passes touch slop,
            // whether or not anything was pinched. On an unzoomed page that swallowed the swipe:
            // the pan was discarded here (there is nowhere to pan an unzoomed page) and the pager
            // above never saw it, so the reader was stuck on page one with a gesture that did
            // nothing at all.
            //
            // So this only takes a gesture it has a use for — **two fingers down, or a page already
            // zoomed in** — and leaves everything else untouched for the pager to read. The child
            // sees the Main pass before its parents, so not consuming here is exactly what hands
            // the swipe on.
            .pointerInput(index) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var mine = currentZoom > Unzoomed
                    do {
                        val event = awaitPointerEvent()
                        if (!mine && event.changes.count { it.pressed } >= 2) mine = true
                        if (mine) {
                            val gestureZoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val next = (currentZoom * gestureZoom).coerceIn(1f, MaxZoom)
                            val moved = if (next <= 1f) Offset.Zero else currentOffset + pan
                            onTransform(next, clampPan(moved, next, container))
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(index) {
                detectTapGestures(
                    onDoubleTap = {
                        onTransform(if (currentZoom > Unzoomed) 1f else DoubleTapZoom, Offset.Zero)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image == null) {
            CircularProgressIndicator(
                color = Amber,
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp),
            )
        } else {
            Image(
                bitmap = image,
                // Decorative to a screen reader: the words are in the picture and nothing here can
                // read them out. Naming it "page 4" would announce a label instead of content.
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
        }
    }
}

/**
 * Keeps a zoomed page from being dragged off its own edges.
 *
 * Measured against the container rather than the drawn page, which over-allows a little where a
 * page is letterboxed. The alternative needs the bitmap's laid-out size, and being able to push a
 * page a few dp into its own margin is not a bug anybody reports; being able to fling it off screen
 * entirely is.
 */
private fun clampPan(offset: Offset, zoom: Float, container: IntSize): Offset {
    if (zoom <= 1f || container == IntSize.Zero) return Offset.Zero
    val maxX = container.width * (zoom - 1f) / 2f
    val maxY = container.height * (zoom - 1f) / 2f
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}
