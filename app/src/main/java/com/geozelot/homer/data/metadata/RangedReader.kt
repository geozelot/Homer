package com.geozelot.homer.data.metadata

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec

/**
 * Reads a byte range of a media file through the authenticated data source — the same transport
 * that streams audio, so it works for a WebDAV URL and a downloaded file alike.
 *
 * Every metadata reader here is built on this one operation. Reading a few bytes at a known offset
 * is what makes them cheap: the alternative each of them replaced was opening a player and letting
 * it stream until the answer fell out.
 */
@OptIn(UnstableApi::class)
internal class RangedReader(private val dataSourceFactory: DataSource.Factory) {

    /** [length] bytes at [position], short if the file ends first, or null if nothing was read. */
    fun readAt(uri: Uri, position: Long, length: Long): ByteArray? {
        if (length <= 0) return null
        val ds = dataSourceFactory.createDataSource()
        return try {
            ds.open(DataSpec.Builder().setUri(uri).setPosition(position).setLength(length).build())
            val out = ByteArray(length.toInt())
            var off = 0
            while (off < out.size) {
                val n = ds.read(out, off, out.size - off)
                if (n == C.RESULT_END_OF_INPUT) break
                off += n
            }
            if (off == 0) null else if (off < out.size) out.copyOf(off) else out
        } catch (e: Exception) {
            null
        } finally {
            runCatching { ds.close() }
        }
    }
}
