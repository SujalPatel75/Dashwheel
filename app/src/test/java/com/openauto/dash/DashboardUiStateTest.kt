package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardUiStateTest {
    /** Everything the drive lock closes, opened. */
    private fun allOpen(setupDone: Boolean = true) = DashboardUiState(SettingsTab.LOOK, setupDone = setupDone).apply {
        editing = true
        showTemplates = true
        fillPage = 2
        showSystemDialog = true
        showAddSheet = true
        tileOptions = 1 to 3
        removeChoice = 4
        confirmBringBack = Triple(1, 2, 3)
        confirmTemplate = TemplateChoice(DashTemplate.entries.first(), setOf(0), replace = true)
        setupStep = SetupStep.ACCESS
        setupShownStep = SetupStep.LOOK
        offerCarTiles = true
        showSplitPicker = true
        showSplitEnable = true
        showDevicePicker = true
        launchBarEditor = 0 to 1
        tourStep = TourStep.SWIPE
    }

    @Test
    fun startsClosed() {
        val ui = DashboardUiState()
        assertNull(ui.settingsTab)
        assertNull(ui.setupStep)
        assertNull(ui.tourStep)
        assertFalse(ui.editing)
        assertFalse(ui.fullScreenSheet)
        assertTrue(ui.tourCanStart(moving = false))
    }

    @Test
    fun aFreshInstallStartsOnTheSetup() {
        val ui = DashboardUiState(setupDone = false)
        assertEquals(SetupStep.CAR, ui.setupStep)
        assertFalse(ui.setupDone)
        assertTrue(ui.fullScreenSheet)
        assertFalse(ui.tourCanStart(moving = false))
    }

    @Test
    fun aScreenTurnInSettingsReopensIt() {
        assertEquals(SettingsTab.SYSTEM, DashboardUiState(settingsTab = SettingsTab.SYSTEM).settingsTab)
    }

    @Test
    fun closingSettingsLetsItsSheetLeaveFirst() {
        val ui = DashboardUiState(SettingsTab.CAR)
        var left = 0
        OpenSheet.opened { left++ }
        ui.closeSettings()
        assertNull(ui.settingsTab)
        assertEquals(1, left)
        // Already closed: nothing more is dismissed.
        OpenSheet.opened { left++ }
        ui.closeSettings()
        assertEquals(1, left)
        OpenSheet.dismiss()
    }

    @Test
    fun theHomeKeyClosesTheDrawerSettingsAndArranging() {
        val ui = DashboardUiState(SettingsTab.OVERVIEW).apply {
            showAllApps = true
            editing = true
            showAddSheet = true
        }
        ui.closeForHome()
        assertFalse(ui.showAllApps)
        assertNull(ui.settingsTab)
        assertFalse(ui.editing)
        // Left as it was, as before the move into this class.
        assertTrue(ui.showAddSheet)
    }

    @Test
    fun theDriveLockClosesEverything() {
        val ui = allOpen()
        ui.closeForDriving()
        assertFalse(ui.editing)
        assertNull(ui.settingsTab)
        assertFalse(ui.showTemplates)
        assertNull(ui.fillPage)
        assertFalse(ui.showSystemDialog)
        assertFalse(ui.showAddSheet)
        assertNull(ui.tileOptions)
        assertNull(ui.removeChoice)
        assertNull(ui.confirmBringBack)
        assertNull(ui.confirmTemplate)
        assertNull(ui.setupStep)
        assertFalse(ui.offerCarTiles)
        assertFalse(ui.showSplitPicker)
        assertFalse(ui.showSplitEnable)
        assertFalse(ui.showDevicePicker)
        assertNull(ui.launchBarEditor)
        assertNull(ui.tourStep)
        // A setup already done is not brought back.
        assertNull(ui.setupResume)
        ui.resumeAfterDriving()
        assertNull(ui.setupStep)
    }

    @Test
    fun theDriveLockLeavesTheBarSheetsAndTheDrawer() {
        val ui = allOpen().apply {
            showAllApps = true
            dashSheet = 1
            appSheet = 2
            showBarChooser = true
            pickBarApp = true
            barSettings = true
        }
        ui.closeForDriving()
        assertTrue(ui.showAllApps)
        assertEquals(1, ui.dashSheet)
        assertEquals(2, ui.appSheet)
        assertTrue(ui.showBarChooser)
        assertTrue(ui.pickBarApp)
        assertTrue(ui.barSettings)
    }

    @Test
    fun aFirstSetupClosedByTheLockComesBackOnItsStep() {
        val ui = allOpen(setupDone = false)
        ui.closeForDriving()
        assertNull(ui.setupStep)
        assertEquals(SetupStep.LOOK, ui.setupResume)
        ui.resumeAfterDriving()
        assertEquals(SetupStep.LOOK, ui.setupStep)
        assertNull(ui.setupResume)
    }

    @Test
    fun aSetupFinishedWhileDrivingIsNotReopened() {
        val ui = allOpen(setupDone = false)
        ui.closeForDriving()
        ui.setupDone = true
        ui.resumeAfterDriving()
        assertNull(ui.setupStep)
        assertNull(ui.setupResume)
    }

    @Test
    fun theTourWaitsForParkedAndNothingOpen() {
        val ui = DashboardUiState()
        assertFalse(ui.tourCanStart(moving = true))
        for (open in listOf<DashboardUiState.() -> Unit>(
            { showAllApps = true }, { settingsTab = SettingsTab.LOOK }, { editing = true },
            { showTemplates = true }, { showSplitPicker = true }, { showSplitEnable = true },
            { setupStep = SetupStep.CAR },
        )) {
            assertFalse(DashboardUiState().apply(open).tourCanStart(moving = false))
        }
    }

    @Test
    fun fullScreenSheets() {
        assertTrue(DashboardUiState().apply { showAllApps = true }.fullScreenSheet)
        assertTrue(DashboardUiState().apply { showAddSheet = true }.fullScreenSheet)
        assertTrue(DashboardUiState(SettingsTab.LOOK).fullScreenSheet)
        assertFalse(DashboardUiState().apply { editing = true; tourStep = TourStep.WELCOME }.fullScreenSheet)
    }
}
