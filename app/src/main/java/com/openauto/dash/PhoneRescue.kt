package com.openauto.dash

import android.content.Context
import com.openauto.dash.link.RescueCommand

/**
 * A way out sent from the paired phone's Rescue card ([RescueCommand]) for a
 * driver locked out of the car's screen: each one runs here with no tap on
 * it, through the same paths the settings use, and is noted in the
 * [DebugLog]. On the main thread.
 */
internal object PhoneRescue {
    /** Whether [action] did what it says (nothing to do counts as done). */
    fun run(context: Context, action: RescueCommand.Action): Boolean {
        val app = context.applicationContext
        val ok = runCatching { apply(app, action) }.getOrDefault(false)
        DebugLog.note(app, "Rescue from the phone: $action ${if (ok) "done" else "failed"}")
        return ok
    }

    private fun apply(context: Context, action: RescueCommand.Action): Boolean = when (action) {
        RescueCommand.Action.CLEAR_WHEEL_BUTTONS -> {
            // The store saves only once it knows the app (it does as soon as the dashboard starts).
            SteeringWheelStore.setContext(context)
            SteeringWheelStore.resetAll()
            true
        }
        RescueCommand.Action.STANDARD_LOOK -> {
            DashThemeStore.askMode(context, DashThemeMode.AUTO)
            DashThemeStore.askRail(context, false)
            true
        }
        // On this ROM the split toggle exits split screen.
        RescueCommand.Action.LEAVE_SPLIT ->
            if (SplitAccessibilityService.isSplit() || MainActivity.inMultiWindowNow()) SplitAccessibilityService.requestSplit() else true
        RescueCommand.Action.HAND_HOME_BACK -> {
            HomeHandBack.handBack(context)
            true
        }
        RescueCommand.Action.TAKE_HOME_BACK -> {
            HomeHandBack.takeBack(context)
            true
        }
    }
}
