package com.openauto.dash

/**
 * The station each radio app tunes, from what its media session says, for
 * the title's turns between the song and the station (a live session, no
 * length). Radio apps say it in different ways:
 * - the station first as the title, then the song in its place (the station
 *   is the title seen before the first song);
 * - the song as the title and the station as the album, or as the artist
 *   while the title reads "artist - song".
 * Pure, so it's unit-tested.
 */
internal class RadioStations {
    private val byApp = HashMap<String, String>()

    /**
     * Notes what [app] plays now; the station's name when [title] is a song
     * on it, else empty (a station naming itself, a track with a length).
     */
    fun note(app: String, title: String, artist: String, album: String = "", live: Boolean): String {
        if (!live || title.isBlank()) return ""
        val t = title.trim()
        val a = artist.trim()
        val b = album.trim()
        val song = (a.isNotEmpty() && !a.equals(t, ignoreCase = true)) || SONG.containsMatchIn(t)
        if (!song) {
            byApp[app] = t
            return ""
        }
        val station = when {
            // An album that isn't the song's words is the station's name on a live stream.
            b.isNotEmpty() && !b.equals(t, ignoreCase = true) && !b.equals(a, ignoreCase = true) -> b
            // "Artist - Song" as the title, the station as the artist.
            SONG.containsMatchIn(t) && a.isNotEmpty() && !t.contains(a, ignoreCase = true) -> a
            else -> byApp[app] ?: return ""
        }
        // A song whose words already carry the station's name needs no turn.
        return station.takeUnless { t.contains(it, ignoreCase = true) }.orEmpty()
    }

    private companion object {
        /** "Artist - Song", the way radios that send no artist write it. */
        val SONG = Regex("\\S\\s[-–/|:]\\s\\S")
    }
}
