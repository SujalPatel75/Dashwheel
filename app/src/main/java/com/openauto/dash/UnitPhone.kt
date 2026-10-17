package com.openauto.dash

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Calls on the head unit's own Bluetooth, whatever its firmware
 * ([UnitPlatform.adapter]'s [PhoneBridge]): the call card, the voice and the
 * OBD link read the call here; a unit whose firmware tells of none has a
 * flow that stays null.
 */
object UnitPhone {
    private val none = MutableStateFlow<PhoneCall?>(null)

    /** The call on the unit's Bluetooth, or null. Read after [UnitPlatform.init]. */
    val call: StateFlow<PhoneCall?> get() = UnitPlatform.adapter.phone?.call ?: none

    /** Whether this unit's firmware tells of its Bluetooth calls at all. */
    fun available(@Suppress("UNUSED_PARAMETER") context: Context): Boolean = UnitPlatform.adapter.phone != null

    fun accept() { UnitPlatform.adapter.phone?.accept() }
    fun decline() { UnitPlatform.adapter.phone?.decline() }
    fun hangUp() { UnitPlatform.adapter.phone?.hangUp() }
}
