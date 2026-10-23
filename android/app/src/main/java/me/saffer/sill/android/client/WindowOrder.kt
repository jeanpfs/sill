package me.saffer.sill.android.client

import me.saffer.sill.android.protocol.WindowInfo

/** The order the windows are listed in on the device: the favorite app's right after the Desktop, the rest as the Mac lists them. */
object WindowOrder {
    /** `windows` with those of `favoriteBundleID` first, each group keeping its own order; the same list when there is no favorite or it is not open. */
    fun favoriteFirst(windows: List<WindowInfo>, favoriteBundleID: String?): List<WindowInfo> {
        if (favoriteBundleID.isNullOrEmpty() || windows.none { it.bundleID == favoriteBundleID }) return windows
        val (favorite, others) = windows.partition { it.bundleID == favoriteBundleID }
        return favorite + others
    }
}
