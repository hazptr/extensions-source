package eu.kanade.tachiyomi.multisrc.pam

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import keiyoushi.utils.WebViewScope
import keiyoushi.utils.applicationContext
import keiyoushi.utils.parseAs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Collections
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * One chapter read through the site's own reader: scrolls it from top to bottom with a finger, copies the reader's box
 * from the screen as it goes and cuts the copy into pages. Only the reader's box is read, never what is inside it.
 */
internal class ChapterCapture(
    private val url: String,
    private val view: SiteView,
    private val dir: File,
    private val scope: CoroutineScope,
) {
    /** The chapter's pages, once the reader has been read to its end. */
    val pages = CompletableDeferred<List<File>>()

    // Some WebViews only paint what would fit on the phone's own screen, so the frame is no taller
    private val frameHeight = minOf(MAX_FRAME_HEIGHT, applicationContext.resources.displayMetrics.heightPixels)

    private val cutting = Dispatchers.IO.limitedParallelism(1)
    private val strip = Strip(dir)

    fun close() {
        job.cancel()
        cuts.forEach { it.cancel() }
        dir.deleteRecursively()
    }

    /** Rows [from] until [until] of the reader, copied while its top was at frame row [readerTop]. */
    private class Segment(val bitmap: Bitmap, val readerTop: Int, val readerHeight: Int, val from: Int, val until: Int)

    private var readerId: Int? = null
    private var readerWidth = 0

    /** Reader rows copied so far; the newest are kept in [recent] until nothing can change them any more. */
    private var filled = 0
    private val recent = ArrayDeque<Segment>()
    private var handedOver = 0
    private var pending: Segment? = null

    // Cutting's failures (a cleared cache, a full disk) end the capture through these, not the app
    private val cuts = Collections.synchronizedList(mutableListOf<Deferred<Unit>>())

    private var readerHeight = -1
    private var heightSince = TimeSource.Monotonic.markNow()

    /** Set while the reader's height changed after rows were copied: [recent] is checked again from the newest back. */
    private var checking = -1
    private var checked: Boolean? = null

    /** When each flat stretch (by its first reader row) was first seen flat: the reader's placeholders look like that. */
    private val flatSince = mutableMapOf<Int, FlatSeen>()

    private class FlatSeen(val since: TimeMark, val colour: Int, val readerHeight: Int)

    /**
     * The placeholders' colour: the reader starts out as one plain box, so a flat top row later drawn over gives it away.
     * Only the top row is used, as nothing above it can move it.
     */
    private var placeholder: Int? = null

    private var waitingSince: TimeMark? = null
    private var furthest = 0
    private var furthestAt = TimeSource.Monotonic.markNow()
    private var lastSpot: Pair<Int, Float>? = null
    private var stuck = 0
    private var dismissals = 0
    private var reloads = 0

    /** Whether any kept rows show more than one flat colour: the reader starts out as a plain box. */
    private var sawArt = false

    private val job = scope.launch(Dispatchers.IO) {
        try {
            dir.mkdirs()
            view.drive(url, "(${view.asset("reader.js")})()", FRAME_WIDTH, frameHeight, CAPTURE_TIMEOUT) { surface, snapshot ->
                step(surface, snapshot.parseAs())
            }
            val rest = recent.map { it.bitmap }
            recent.clear()
            cuts.toList().awaitAll()
            pages.complete(withContext(cutting) { strip.finish(rest) })
        } catch (e: Throwable) {
            pages.completeExceptionally(e)
        }
    }

    private fun WebViewScope<Unit>.step(surface: Surface, page: ReaderPage): Step<Unit> {
        when (page.gate) {
            // Turnstile may pass by itself, like a Cloudflare check
            "check" -> {
                val since = waitingSince ?: TimeSource.Monotonic.markNow().also { waitingSince = it }
                if (since.elapsedNow() > CHECK_WAIT) throw IOException(SOLVE_IN_WEBVIEW)
                return Step.Wait
            }
            "locked" -> throw IOException(LOCKED)
        }
        // The reader shows up once the site has the chapter's page list
        val reader = page.reader ?: run {
            val since = waitingSince ?: TimeSource.Monotonic.markNow().also { waitingSince = it }
            if (since.elapsedNow() > DRAW_WAIT) throw IOException("The site's reader did not open the chapter")
            return Step.Wait
        }
        waitingSince = null
        val scale = page.scale
        val left = (reader.left * scale).roundToInt().coerceAtLeast(0)
        val right = ((reader.left + reader.width) * scale).roundToInt().coerceAtMost(surface.width)
        val top = (reader.top * scale).roundToInt()
        val height = (reader.height * scale).roundToInt()
        // Another reader, or the same one at another width: start over
        if (reader.id != readerId || right - left != readerWidth) {
            if (readerId != null) restart()
            readerId = reader.id
            readerWidth = right - left
            furthestAt = TimeSource.Monotonic.markNow()
        }
        if (height != readerHeight) {
            readerHeight = height
            heightSince = TimeSource.Monotonic.markNow()
            // Rows below a page drawn since have moved; the reader's top row stays put
            flatSince.keys.retainAll { it == 0 }
            // A page drawn since: the rows copied last may have changed under it
            if (recent.isNotEmpty()) {
                checking = recent.lastIndex
                checked = null
            }
        }

        // Close the site's floating ad the way a reader does; a few tries in case it comes back
        page.dismiss?.takeIf { dismissals < MAX_DISMISSALS }?.let { close ->
            dismissals++
            return Step.Act { done ->
                tap(scale, close.x, close.y)
                later(TAP_WAIT_MS, done)
            }
        }

        // A page the reader gave up on, reloaded with its own button
        page.reload?.let { reload ->
            if (++reloads > MAX_RELOADS) throw IOException("The site's reader could not load a page")
            return Step.Act { done ->
                tap(scale, reload.x, reload.y)
                later(RELOAD_WAIT_MS, done)
            }
        }

        val viewRows = (page.height * scale).toInt()
        val bands = page.clear.map { ceil(it.top * scale).toInt() to min(floor(it.bottom * scale).toInt(), viewRows) }
        fun shows(screenFrom: Int, screenUntil: Int) = bands.any { (bandTop, bandBottom) -> screenFrom >= bandTop && screenUntil <= bandBottom }

        // A copy counts once a later read shows the reader unchanged, in the same place, with nothing over those rows
        pending?.let { segment ->
            pending = null
            val confirmed = checking < 0 && segment.readerTop == top && segment.readerHeight == height && segment.from == filled &&
                shows(top + segment.from, top + segment.until)
            val kept = if (confirmed) settled(segment) else null
            if (kept != null) {
                sawArt = sawArt || kept.bitmap.flatRuns().none { (start, end) -> start == 0 && end == kept.bitmap.height }
                reloads = 0
                recent.addLast(kept)
                filled = kept.until
                handOver()
            }
            if (kept !== segment) segment.bitmap.recycle()
        }

        if (checking >= 0 || checked != null) return check(surface, page, top, left, right, ::shows)

        // Let a page the reader is drawing settle first
        if (heightSince.elapsedNow() < CALM) return Step.Wait
        if (filled > furthest) {
            furthest = filled
            furthestAt = TimeSource.Monotonic.markNow()
        } else if (furthestAt.elapsedNow() > DRAW_WAIT) {
            throw IOException(if (sawArt) "The site's reader stopped drawing the chapter" else "The site's reader showed no pages")
        }
        if (filled >= height) {
            // The end, once the reader holds drawn pages and grows no more
            return if (sawArt && heightSince.elapsedNow() > END_WAIT) Step.Done(Unit) else Step.Wait
        }

        // Rows under an ad within the reader are left out
        val ads = page.ads.map { (it.top * scale).roundToInt() - top to (it.bottom * scale).roundToInt() - top }
        ads.firstOrNull { (adTop, adBottom) -> filled in adTop until adBottom }?.let { (_, adBottom) ->
            filled = adBottom
            return Step.Wait
        }

        // Only rows nothing covers: the first missing one has to be in such a band
        val missing = top + filled
        val band = bands.firstOrNull { (bandTop, bandBottom) -> missing in bandTop until bandBottom }
            ?: return scrollTo(page, missing)
        val until = minOf(height, band.second - top, ads.filter { it.first > filled }.minOfOrNull { it.first } ?: height)
        val from = filled
        return Step.Act { done ->
            surface.copy(left, missing, right, top + until) { segment ->
                if (segment != null) pending = Segment(segment, top, height, from, until)
                done()
            }
        }
    }

    /**
     * Compares the copied rows in [recent] with the screen, newest first, after the reader changed height: copying goes
     * on after the newest that still matches. A segment's last rows tell, since a change above them moves them too.
     */
    private fun WebViewScope<Unit>.check(
        surface: Surface,
        page: ReaderPage,
        top: Int,
        left: Int,
        right: Int,
        shows: (Int, Int) -> Boolean,
    ): Step<Unit> {
        checked?.let { same ->
            checked = null
            if (same) {
                // Everything after the matching segment is copied again
                dropRecent(after = checking)
                filled = recent.last().until
                checking = -1
                return Step.Wait
            }
            checking--
        }
        // None of the kept rows match: read again from the top, unless rows were already cut into pages
        if (checking < 0) {
            if (handedOver > 0) throw IOException(READER_CHANGED)
            dropRecent()
            filled = 0
            sawArt = false
            flatSince.keys.retainAll { it == 0 }
            return Step.Wait
        }
        val segment = recent[checking]
        val (rowsFrom, rowsUntil) = segment.bitmap.lastArt()
        val screenFrom = top + segment.from + rowsFrom
        val screenUntil = top + segment.from + rowsUntil
        if (!shows(screenFrom, screenUntil)) return scrollTo(page, screenFrom, CHECK_AT)
        return Step.Act { done ->
            surface.copy(left, screenFrom, right, screenUntil) { fresh ->
                val kept = Bitmap.createBitmap(segment.bitmap, 0, rowsFrom, segment.bitmap.width, rowsUntil - rowsFrom)
                checked = fresh != null && fresh.looksLike(kept)
                fresh?.recycle()
                kept.recycle()
                done()
            }
        }
    }

    /**
     * The part of [segment] that can be kept: the reader draws a page over a plain placeholder box, so rows of one flat
     * colour wait until they have stayed so for [FLAT_WAIT], and a flat stretch running off the copy's bottom edge waits
     * for a copy that shows where it ends. Null when nothing can be kept yet.
     */
    private fun settled(segment: Segment): Segment? {
        val bitmap = segment.bitmap
        val runs = bitmap.flatRuns()
        // Drawn over once the page list arrived (the reader changed height), give or take a row of where it sits
        flatSince[0]?.takeIf { seen ->
            segment.from == 0 && segment.readerHeight != seen.readerHeight && runs.none { it.start <= MAX_SHIFT && it.colour.near(seen.colour) }
        }?.let { seen ->
            placeholder = seen.colour
            flatSince.remove(0)
        }
        for ((start, end, colour) in runs) {
            // Running off the copy, unless the copy ends where the reader does
            val reaches = end == bitmap.height && segment.until < segment.readerHeight
            if (end - start < FLAT_ROWS && !(reaches && start == 0)) {
                if (!reaches) continue
                // Keep what is above it; the stretch itself is read again from the next copy
                return segment.take(until = segment.from + start)
            }
            // Once the placeholders' colour is known, other flat stretches are the chapter's own
            if (placeholder?.let { !colour.near(it) } == true) continue
            val since = flatSince.getOrPut(segment.from + start) { FlatSeen(TimeSource.Monotonic.markNow(), colour, segment.readerHeight) }.since
            if (since.elapsedNow() < FLAT_WAIT) return segment.take(until = segment.from + start)
        }
        return segment
    }

    /** This segment's rows up to reader row [until] as a segment of its own; this one when nothing is cut off. */
    private fun Segment.take(until: Int): Segment? {
        if (until <= from) return null
        if (until == this.until) return this
        return Segment(Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, until - from), readerTop, readerHeight, from, until)
    }

    /** Hands the rows nothing can change any more over to be cut into pages. */
    private fun handOver() {
        while (recent.isNotEmpty() && recent.first().until <= filled - SETTLED_ROWS) {
            val segment = recent.removeFirst()
            handedOver = segment.until
            cuts += scope.async(cutting) { strip.add(segment.bitmap) }
        }
    }

    private fun restart() {
        pending?.bitmap?.recycle()
        pending = null
        dropRecent()
        if (handedOver > 0) throw IOException(READER_CHANGED)
        filled = 0
        sawArt = false
        flatSince.keys.retainAll { it == 0 }
        checking = -1
        checked = null
    }

    private fun dropRecent(after: Int = -1) {
        while (recent.lastIndex > after) recent.removeLast().bitmap.recycle()
    }

    /** Swipes until frame row [row] sits [at] (a fraction) of the way down the widest uncovered rows. */
    private fun WebViewScope<Unit>.scrollTo(page: ReaderPage, row: Int, at: Float = 0f): Step<Unit> {
        val reader = page.reader!!
        val widest = page.clear.maxByOrNull { it.bottom - it.top } ?: return Step.Wait
        val distance = row / page.scale - widest.top - (widest.bottom - widest.top) * at - EDGE
        // The same spot again after scrolling: the page can't move there
        val spot = row to reader.top
        if (spot == lastSpot && ++stuck > MAX_STUCK) throw IOException("The site's reader stopped scrolling")
        if (spot != lastSpot) stuck = 0
        lastSpot = spot
        val margin = (widest.bottom - widest.top) * SWIPE_MARGIN
        val reach = widest.bottom - widest.top - 2 * margin
        // A shorter swipe is taken for a tap's wobble and doesn't scroll
        val travel = distance.coerceIn(-reach, reach).let { if (abs(it) < MIN_SWIPE) MIN_SWIPE * sign(it) else it }
        val from = if (travel > 0) widest.bottom - margin else widest.top + margin
        val x = reader.left + reader.width * X_FRACTION
        return Step.Act { done ->
            swipe(page.scale, x, from, x, from - travel, brisk = true) { later(SWIPE_WAIT_MS, done) }
        }
    }
}

/**
 * Whether two copies of the same rows show the same thing, give or take a row: the reader sits at fractional CSS
 * positions, so copies made at different scroll positions can be a row apart.
 */
private fun Bitmap.looksLike(other: Bitmap): Boolean {
    if (width != other.width || abs(height - other.height) > 2) return false
    val rows = min(height, other.height) - 2 * MAX_SHIFT
    if (rows <= 0) return false
    val mine = IntArray(width)
    val theirs = IntArray(width)
    return (-MAX_SHIFT..MAX_SHIFT).any { shift ->
        var differing = 0
        var samples = 0
        for (y in MAX_SHIFT until MAX_SHIFT + rows step 7) {
            getPixels(mine, 0, width, 0, y, width, 1)
            other.getPixels(theirs, 0, width, 0, y + shift, width, 1)
            for (x in 0 until width step 9) {
                samples++
                val a = mine[x]
                val b = theirs[x]
                val diff = abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) + abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) + abs((a and 0xFF) - (b and 0xFF))
                if (diff > PIXEL_TOLERANCE) differing++
            }
        }
        differing * 100 <= samples * DIFFERING_PERCENT
    }
}

/** The rows to compare (start until end): the last [CHECK_ROWS] above any flat stretch the segment ends with. */
private fun Bitmap.lastArt(): Pair<Int, Int> {
    val end = flatRuns().lastOrNull()?.takeIf { it.end == height }?.start?.takeIf { it > 0 } ?: height
    return max(0, end - CHECK_ROWS) to end
}

/** Rows [start] until [end], all of one [colour]. */
private data class FlatRun(val start: Int, val end: Int, val colour: Int)

/**
 * Runs of rows (start until end) that are one flat colour across the whole width: at least [EDGE_ROWS] long, or any
 * length at the bottom edge, where the run may go on below.
 */
private fun Bitmap.flatRuns(): List<FlatRun> {
    val line = IntArray(width)
    val runs = mutableListOf<FlatRun>()
    var start = -1
    var colour = 0
    for (y in 0..height) {
        val flat = y < height && run {
            getPixels(line, 0, width, 0, y, width, 1)
            val first = line[0]
            (0 until width step 6).all { x -> line[x].near(first) } && (start < 0 || first.near(colour))
        }
        if (flat && start < 0) {
            start = y
            colour = line[0]
        } else if (!flat && start >= 0) {
            if (y - start >= EDGE_ROWS || y == height) runs += FlatRun(start, y, colour)
            start = -1
        }
    }
    return runs
}

private fun Int.near(other: Int) = abs((this shr 16 and 0xFF) - (other shr 16 and 0xFF)) <= FLAT_TOLERANCE &&
    abs((this shr 8 and 0xFF) - (other shr 8 and 0xFF)) <= FLAT_TOLERANCE &&
    abs((this and 0xFF) - (other and 0xFF)) <= FLAT_TOLERANCE

/** Copied rows, cut into pages at the quietest row near every [SLICE_ROWS]. */
private class Strip(private val dir: File) {
    private val parts = ArrayDeque<Bitmap>()
    private var skip = 0
    private var rows = 0
    private val files = mutableListOf<File>()

    fun add(bitmap: Bitmap) {
        parts.addLast(bitmap)
        rows += bitmap.height
        while (rows >= SLICE_ROWS + SLICE_SPREAD) cut(quietest())
    }

    fun finish(rest: List<Bitmap>): List<File> {
        rest.forEach(::add)
        if (rows > 0) cut(rows)
        return files
    }

    /** The row near [SLICE_ROWS] with the least going on across it, so a cut falls between panels. */
    private fun quietest(): Int {
        val width = parts.first().width
        val line = IntArray(width)
        var best = SLICE_ROWS
        var bestScore = Int.MAX_VALUE
        for (row in SLICE_ROWS - SLICE_SPREAD until SLICE_ROWS + SLICE_SPREAD) {
            val (part, y) = locate(row)
            part.getPixels(line, 0, width, 0, y, width, 1)
            var low = 255
            var high = 0
            for (x in 0 until width step 4) {
                val p = line[x]
                val luma = ((p shr 16 and 0xFF) * 3 + (p shr 8 and 0xFF) * 6 + (p and 0xFF)) / 10
                low = min(low, luma)
                high = max(high, luma)
            }
            val score = (high - low) * 4 + abs(row - SLICE_ROWS) / 64
            if (score < bestScore) {
                best = row
                bestScore = score
            }
        }
        return best
    }

    private fun locate(row: Int): Pair<Bitmap, Int> {
        var y = row + skip
        for (part in parts) {
            if (y < part.height) return part to y
            y -= part.height
        }
        error("Row $row is past the copied rows")
    }

    private fun cut(count: Int) {
        val width = parts.first().width
        val page = Bitmap.createBitmap(width, count, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(page)
        var done = 0
        while (done < count) {
            val part = parts.first()
            val take = min(part.height - skip, count - done)
            canvas.drawBitmap(part, Rect(0, skip, width, skip + take), Rect(0, done, width, done + take), null)
            done += take
            skip += take
            if (skip == part.height) {
                parts.removeFirst().recycle()
                skip = 0
            }
        }
        rows -= count
        val file = File(dir, "${files.size}.jpg")
        file.outputStream().use { page.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        page.recycle()
        files += file
    }
}

private const val FRAME_WIDTH = 720
private const val MAX_FRAME_HEIGHT = 3600
private val CAPTURE_TIMEOUT = 30.minutes
private val DRAW_WAIT = 1.minutes
private val CALM = 250.milliseconds
private val END_WAIT = 3.seconds
private val CHECK_WAIT = 30.seconds
private const val SETTLED_ROWS = 4000
private const val SLICE_ROWS = 5000
private const val SLICE_SPREAD = 1500
private const val JPEG_QUALITY = 92
private const val MAX_DISMISSALS = 3
private const val MAX_RELOADS = 3
private const val RELOAD_WAIT_MS = 2000L
private const val READER_CHANGED = "The site's reader changed while the chapter was being read"
private const val TAP_WAIT_MS = 800L
private const val LOCKED = "This chapter needs an account or a subscription on the site, log in via WebView"
private const val MAX_STUCK = 4
private const val EDGE = 4f
private const val SWIPE_MARGIN = 0.04f
private const val X_FRACTION = 0.2f
private const val SWIPE_WAIT_MS = 150L
private const val MAX_SHIFT = 2
private const val FLAT_ROWS = 300
private const val EDGE_ROWS = 64
private const val FLAT_TOLERANCE = 6
private val FLAT_WAIT = 2500.milliseconds
private const val CHECK_ROWS = 240
private const val CHECK_AT = 0.4f
private const val MIN_SWIPE = 48f
private const val PIXEL_TOLERANCE = 48
private const val DIFFERING_PERCENT = 3
