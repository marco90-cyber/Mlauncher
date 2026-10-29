package com.marco.mlauncher

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun Root(vm: LauncherViewModel, host: Host) {
    val view = LocalView.current
    val lightSurface = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    androidx.compose.runtime.LaunchedEffect(vm.overlay) {
        if (vm.overlay != Overlay.SETTINGS) vm.settingsPage = null
    }
    SideEffect {
        Haptics.enabled = vm.settings.haptics
        Haptics.onPress = vm.settings.hapPress
        Haptics.onDrop = vm.settings.hapDrop
        Haptics.onTick = vm.settings.hapTick
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val c = WindowCompat.getInsetsController(window, view)
            // sulla Home (sfondo) icone chiare; sui pannelli chiari icone scure
            val onPanel = vm.overlay != Overlay.NONE
            c.isAppearanceLightStatusBars = onPanel && lightSurface
            c.isAppearanceLightNavigationBars = onPanel && lightSurface
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalIconShape provides vm.settings.iconShape) {
        Box(Modifier.fillMaxSize()) {
            if (vm.loaded) HomeScreen(vm, host)
            FolderOverlay(vm, host)
            AppsOverlay(vm, host)
            WidgetPicker(vm, host)
            SettingsOverlay(vm, host)
        }
    }

    // l'ordine conta: l'ultimo registrato ha la precedenza
    BackHandler(enabled = vm.editMode) { vm.exitEdit() }
    BackHandler(enabled = vm.openFolderId != null) { vm.openFolderId = null }
    BackHandler(enabled = vm.overlay == Overlay.DRAWER) { vm.overlay = Overlay.NONE }
    BackHandler(enabled = vm.overlay == Overlay.WIDGETS) { vm.overlay = Overlay.NONE }
    BackHandler(enabled = vm.overlay == Overlay.SETTINGS) {
        if (vm.settingsPage != null) vm.settingsPage = null else vm.overlay = Overlay.NONE
    }
}
