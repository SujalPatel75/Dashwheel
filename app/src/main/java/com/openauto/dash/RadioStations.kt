package com.openauto.dash

/**
 * The station each radio app tunes, from what its media session says, to
 * show under the song (a live session, no length). Radio apps say it in
 * different ways:
 * - the station first as the title (often as the artist too), then the song
 *   in its place: the station is the title seen before the first song;
 * - DAB-Z (com.zoulou.dab): tuned, title and artist are the station and the
 *   album its multiplex ("SLO DAB+ R1"); then the radio text as the title
 *   with the station as the artist; then the song and its singer, the album
 *   reading "station - multiplex";
 * - the song as the title and the station as the album, or as the artist
 *   while the title reads "artist - song".
 * Pure, so it's unit-tested.
 */
internal class RadioStations {
    private val byApp = HashMap<String, String>()

    /**
     * Notes what [app] plays now; the station's name, held from the moment it
     * names itself to the next one (empty for a track with a length, or a
     * song with no station known).
     */
    fun note(app: String, title: String, artist: String, album: String = "", live: Boolean): String {
        if (title.isBlank()) return ""
        val t = title.trim()
        val a = artist.trim()
        val b = album.trim()
        if (!live) {
            // DAB-Z gives a song a length some 28 s after it starts: still the radio while the
            // album or the artist names the station it tuned. Any other track is no radio.
            val known = byApp[app] ?: return ""
            val still = b.startsWith(known, ignoreCase = true) || a.equals(known, ignoreCase = true)
            return known.takeIf { still && !t.contains(it, ignoreCase = true) }.orEmpty()
        }
        val song = (a.isNotEmpty() && !a.equals(t, ignoreCase = true)) || SONG.containsMatchIn(t)
        if (!song) {
            byApp[app] = t
            return t
        }
        val known = byApp[app]
        val station = when {
            // The station it named itself, still the artist (the radio text as the title) or the album's start.
            known != null && (a.equals(known, ignoreCase = true) || b.startsWith(known, ignoreCase = true)) -> known
            // "Station - multiplex" as the album.
            ALBUM_SPLIT in b -> b.substringBefore(ALBUM_SPLIT).trim()
            // An album that isn't the song's words, with no station named before: the station's name.
            known == null && b.isNotEmpty() && !b.equals(t, ignoreCase = true) && !b.equals(a, ignoreCase = true) -> b
            // "Artist - Song" as the title, the station as the artist.
            SONG.containsMatchIn(t) && a.isNotEmpty() && !t.contains(a, ignoreCase = true) -> a
            else -> known ?: return ""
        }
        // A song whose words already carry the station's name needs it no more.
        return station.takeUnless { it.isEmpty() || t.contains(it, ignoreCase = true) }.orEmpty()
    }

    private companion object {
        /** "Artist - Song", the way radios that send no artist write it. */
        val SONG = Regex("\\S\\s[-–/|:]\\s\\S")

        /** Between the station and its multiplex in a DAB album. */
        const val ALBUM_SPLIT = " - "
    }
}
