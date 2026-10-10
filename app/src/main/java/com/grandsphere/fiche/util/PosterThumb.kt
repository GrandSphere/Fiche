package com.grandsphere.fiche.util

import android.net.Uri

/**
 * Returns a thumbnail-sized variant of a remote poster URL using host-aware
 * query-parameter or path rewriting.
 *
 * Supports:
 *  - **TVMaze**: `https://static.tvmaze.com/uploads/...jpg` → adds `?w=185`
 *  - **TMDB**: `https://image.tmdb.org/t/p/<size>/...` → replaces size segment
 *    with `w185`
 *  - **Open Library**: `https://covers.openlibrary.org/b/id/<id>-L.jpg` →
 *    replaces `-L` suffix with `-M` (medium cover)
 *  - **RAWG**: `https://media.rawg.io/media/.../...jpg?resize=...` → overrides
 *    the `resize` query param to `185`
 *
 * All other URLs are returned unchanged.
 */
fun posterThumb(url: String?): String? {
    if (url.isNullOrBlank()) return url
    return runCatching {
        val uri = Uri.parse(url)
        when {
            // TVMaze: static.tvmaze.com
            uri.host?.endsWith("tvmaze.com") == true -> {
                uri.buildUpon().appendQueryParameter("w", "185").build().toString()
            }
            // TMDB: image.tmdb.org/t/p/<size>/...
            uri.host?.endsWith("tmdb.org") == true -> {
                val segments = uri.pathSegments.toMutableList()
                // path is typically: t / p / <size> / filename
                val sizeIndex = segments.indexOfFirst { it.startsWith("w") || it == "original" }
                if (sizeIndex >= 0) {
                    segments[sizeIndex] = "w185"
                    uri.buildUpon().path(segments.joinToString("/", "/")).build().toString()
                } else url
            }
            // Open Library: covers.openlibrary.org
            uri.host?.endsWith("openlibrary.org") == true -> {
                url.replace("-L.jpg", "-M.jpg")
                    .replace("-L.png", "-M.png")
            }
            // RAWG: media.rawg.io
            uri.host?.endsWith("rawg.io") == true -> {
                uri.buildUpon()
                    .clearQuery()
                    .appendQueryParameter("resize", "185")
                    .build()
                    .toString()
            }
            else -> url
        }
    }.getOrDefault(url)
}
