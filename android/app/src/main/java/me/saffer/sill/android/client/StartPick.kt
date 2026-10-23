package me.saffer.sill.android.client

import me.saffer.sill.android.protocol.StreamSource
import me.saffer.sill.android.protocol.WindowInfo

/** What a session opens on: the favorite app's window when it is open on the Mac, else whatever the Mac streams (the Desktop). */
object StartPick {
    /**
     * The window to ask for when a session begins, or null to leave things as they are. Only when nothing is
     * streamed yet or the Desktop is: a window already streamed (another device is watching it) is not taken
     * from them. `favoriteBundleID` is the app's bundle id (a window's id changes every time it opens);
     * with several windows of it the first listed, the frontmost, wins.
     */
    fun choose(favoriteBundleID: String?, windows: List<WindowInfo>, active: StreamSource): StreamSource.Window? {
        if (favoriteBundleID.isNullOrEmpty()) return null
        if (active is StreamSource.Window) return null
        val w = windows.firstOrNull { it.bundleID == favoriteBundleID } ?: return null
        return StreamSource.Window(w.id)
    }
}
