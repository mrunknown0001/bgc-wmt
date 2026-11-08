package com.wmt.app.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wmt.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Exposes the persisted theme preference so [MainActivity] can pick light/dark before content. */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {
    /**
     * Seeded from the choice read at process start, so a device set to "dark"
     * opens dark instead of showing one light frame while DataStore catches
     * up. Nothing saved yet means "system", as before.
     */
    val themeMode: StateFlow<String> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsRepository.startupThemeMode)

    /**
     * The accent the server has us set to.
     *
     * Seeded from the value read at process start, so a cold start opens on the
     * cached colour rather than showing the default and correcting itself once
     * DataStore emits. Before the first login there is nothing cached and the
     * app is [Accent.Default] -- blue.
     */
    val accent: StateFlow<Accent> = settingsRepository.primaryColor
        .map { Accent.from(it) }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            Accent.from(settingsRepository.startupPrimaryColor),
        )
}
