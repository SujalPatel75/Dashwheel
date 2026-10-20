package com.openauto.dash

/**
 * The dashboard as a home screen Android may never make a floating window:
 * the same screen as [MainActivity], declared not resizable in the manifest.
 * Some QF units turned the home screen into a floating window at every start,
 * and the Recents screen then stayed over it for good. Off by default; only
 * [FloatingHome] switches it on, on a unit where that happened.
 */
class FullscreenHomeActivity : MainActivity()
