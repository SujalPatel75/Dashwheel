package com.openauto.dash

/**
 * The station each radio app tunes, from what its media session says: a live
 * session (no length) titled with no artist and no "artist - song" is naming
 * its station; once it names a song, the station is the one it named before.
 * Pure, so it's unit-tested.
 */
internal class RadioStations {
    private val byApp = HashMap<String, String>()

    /**
     * Notes what [app] plays now; the station's name when [title] is a song
     * on it, else empty (a station naming itself, a track with a length).
     */
    fun note(app: String, title: String, artist: String, live: Boolean): String {
        if (!live || title.isBlank()) return ""
        val song = artist.isNotBlank() || SONG.containsMatchIn(title)
        if (!song) {
            byApp[app] = title.trim()
            return ""
        }
        val station = byApp[app] ?: return ""
        // A song whose words already carry the station's name needs no turn.
        return station.takeUnless { title.contains(it, ignoreCase = true) || artist.contains(it, ignoreCase = true) }.orEmpty()
    }

    private companion object {
        /** "Artist - Song", the way radios that send no artist write it. */
        val SONG = Regex("\\S\\s[-–]\\s\\S")
    }
}
