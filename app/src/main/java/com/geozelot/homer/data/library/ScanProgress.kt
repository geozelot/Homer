package com.geozelot.homer.data.library

import android.content.res.Resources
import com.geozelot.homer.R

/**
 * "12 folders · 3 books" — the one line a running crawl reports, built the same way for the
 * worker's notification and for the two screens that show it, with each count pluralised on its
 * own. A format string with two bare numbers in it read "1 folders · 1 books" at the start of
 * every scan, in both languages.
 */
fun scanProgressLine(resources: Resources, folders: Int, books: Int): String =
    resources.getString(
        R.string.sync_scan_folders_books,
        resources.getQuantityString(R.plurals.sync_folders_count, folders, folders),
        resources.getQuantityString(R.plurals.sync_books_count, books, books),
    )
