package com.wmt.app.ui.theme

import com.wmt.app.domain.repository.SettingsRepository
import com.wmt.app.testing.MainDispatcherRule
import com.wmt.app.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The first frame, before DataStore has said anything.
 *
 * Both of these used to be wrong in the same way: the view model started on a
 * hardcoded default, so a device set to dark opened on one light frame and an
 * organisation on teal opened blue, each correcting itself a moment later. The
 * fix is a value read synchronously at process start, and what makes it a fix
 * is that it is the *initial* value -- which is exactly what a flow that never
 * emits lets us pin down.
 */
class ThemeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeSettings(
        override val startupThemeMode: String,
        override val startupPrimaryColor: String?,
        override val themeMode: Flow<String> = emptyFlow(),
        override val primaryColor: Flow<String?> = emptyFlow(),
    ) : SettingsRepository {
        override val serverUrl: Flow<String?> = emptyFlow()
        override val maxUploadSizeMb: Flow<Int> = flowOf(10)
        override suspend fun checkHealth(baseUrl: String): Resource<Unit> = Resource.Success(Unit)
        override suspend fun saveServerUrl(baseUrl: String) = Unit
        override suspend fun clearServerUrl() = Unit
        override suspend fun setThemeMode(mode: String) = Unit
        override suspend fun refreshAppSettings() = Unit
    }

    @Test
    fun `opens on the saved theme before the flow emits`() {
        val vm = ThemeViewModel(FakeSettings(startupThemeMode = "dark", startupPrimaryColor = null))
        assertEquals("dark", vm.themeMode.value)
    }

    @Test
    fun `opens on the cached accent before the flow emits`() {
        val vm = ThemeViewModel(FakeSettings(startupThemeMode = "system", startupPrimaryColor = "teal"))
        assertEquals(Accent.Teal, vm.accent.value)
    }

    @Test
    fun `a device that has saved nothing follows the system, in blue`() {
        val vm = ThemeViewModel(FakeSettings(startupThemeMode = "system", startupPrimaryColor = null))
        assertEquals("system", vm.themeMode.value)
        assertEquals(Accent.Blue, vm.accent.value)
    }

    @Test
    fun `a later emission still wins`() {
        val vm = ThemeViewModel(
            FakeSettings(
                startupThemeMode = "dark",
                startupPrimaryColor = "blue",
                themeMode = flowOf("light"),
                primaryColor = flowOf("orange"),
            ),
        )
        assertEquals("light", vm.themeMode.value)
        assertEquals(Accent.Orange, vm.accent.value)
    }
}
