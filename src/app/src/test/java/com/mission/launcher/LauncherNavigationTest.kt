package com.mission.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherNavigationTest {
    @Test
    fun folderBackClosesOnlyFolder() {
        assertEquals(
            LauncherNavigation.BackStep.CLOSE_FOLDER_OR_DRAWER,
            LauncherNavigation.step(
                hasPanel = false,
                hasDrawer = true,
                drawerInFolder = true,
                editing = false,
                pageIndex = 2,
            ),
        )
    }

    @Test
    fun backMovesToPreviousPageBeforeTaskExit() {
        assertEquals(
            LauncherNavigation.BackStep.PREVIOUS_PAGE,
            LauncherNavigation.step(
                hasPanel = false,
                hasDrawer = false,
                drawerInFolder = false,
                editing = false,
                pageIndex = 2,
            ),
        )
        assertEquals(
            LauncherNavigation.BackStep.MOVE_TASK_BACK,
            LauncherNavigation.step(
                hasPanel = false,
                hasDrawer = false,
                drawerInFolder = false,
                editing = false,
                pageIndex = 0,
            ),
        )
    }

    @Test
    fun overlaysAndEditorTakePriority() {
        assertEquals(
            LauncherNavigation.BackStep.CLOSE_PANEL,
            LauncherNavigation.step(true, true, false, true, 2),
        )
        assertEquals(
            LauncherNavigation.BackStep.CLOSE_EDITOR,
            LauncherNavigation.step(false, false, false, true, 2),
        )
    }
}
