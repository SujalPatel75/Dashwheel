package com.openauto.dash

import com.openauto.dash.PrivilegedShell.Access
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitCapsTest {

    private val caps = UnitCaps(
        listOf(
            CapClaim(Cap.IGNITION),
            CapClaim(Cap.DOORS, Need.SETTINGS),
            CapClaim(Cap.VOLUME_UNIT, Need.SHELL),
            CapClaim(Cap.BOOT_LOGO, Need.ROOT)
        )
    )

    @Test
    fun whatIsNotClaimed_isAbsent() {
        assertFalse(caps.has(Cap.TYRES))
        assertNull(caps.need(Cap.TYRES))
        for (access in Access.entries) assertEquals(CapState.ABSENT, caps.state(Cap.TYRES, access, settingsGranted = true, used = setOf(Cap.TYRES)))
    }

    @Test
    fun aClaimWithNoNeed_isReady_andLiveOnceUsed() {
        assertEquals(CapState.READY, caps.state(Cap.IGNITION, Access.NONE, settingsGranted = false, used = emptySet()))
        assertEquals(CapState.LIVE, caps.state(Cap.IGNITION, Access.NONE, settingsGranted = false, used = setOf(Cap.IGNITION)))
    }

    @Test
    fun theSettingsNeed_isMetByTheGrant_orByAnyShell() {
        assertEquals(CapState.LOCKED, caps.state(Cap.DOORS, Access.NONE, settingsGranted = false, used = emptySet()))
        assertEquals(CapState.READY, caps.state(Cap.DOORS, Access.NONE, settingsGranted = true, used = emptySet()))
        for (access in listOf(Access.ROOT, Access.SHIZUKU, Access.ADB)) {
            assertEquals(access.name, CapState.READY, caps.state(Cap.DOORS, access, settingsGranted = false, used = emptySet()))
        }
        assertEquals(CapState.LOCKED, caps.state(Cap.DOORS, Access.UNKNOWN, settingsGranted = false, used = emptySet()))
    }

    @Test
    fun theShellNeed_takesAnyShell_theRootNeedOnlySu() {
        assertEquals(setOf(Access.ROOT, Access.SHIZUKU, Access.ADB), Access.entries.filter { UnitCaps.needMet(Need.SHELL, it, false) }.toSet())
        assertEquals(setOf(Access.ROOT), Access.entries.filter { UnitCaps.needMet(Need.ROOT, it, true) }.toSet())
        assertTrue(Access.entries.all { UnitCaps.needMet(Need.NONE, it, false) })
        assertEquals(CapState.LOCKED, caps.state(Cap.BOOT_LOGO, Access.ADB, settingsGranted = true, used = emptySet()))
        assertEquals(CapState.READY, caps.state(Cap.VOLUME_UNIT, Access.ADB, settingsGranted = false, used = emptySet()))
    }

    @Test
    fun theEmptyCaps_claimNothing() {
        assertTrue(UnitCaps.NONE.claimed.isEmpty())
        assertEquals(CapState.ABSENT, UnitCaps.NONE.state(Cap.IGNITION, Access.ROOT, settingsGranted = true, used = emptySet()))
    }
}
