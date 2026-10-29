package com.marco.mlauncher

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.blur
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun Root(vm: LauncherViewModel, host: Host) {
    val view = LocalView.current
    val lightSurface = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    androidx.compose.runtime.LaunchedEffect(vm.overlay) {
        if (vm.overlay != Overlay.SETTINGS) {
            vm.settingsPage = null
            vm.settings.previewLand = null
        }
    }
    val isLand = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    SideEffect { vm.settings.actualLand = isLand }
    LaunchedEffect(isLand) { vm.syncOrientation(isLand) }
    SideEffect {
        Haptics.enabled = vm.settings.haptics
        (view.context as? Activity)?.requestedOrientation =
            if (vm.settings.allowRotation) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
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

    androidx.compose.runtime.CompositionLocalProvider(
        LocalIconShape provides vm.settings.iconShape,
        LocalFolderStyle provides (vm.settings.folderPreview to vm.settings.folderBg),
        LocalFolderDock provides (
            if (vm.settings.folderFollowDock) with(vm.settings) { DockLookSpec(dockStyle, dockOpacity, dockGlass, dockCorner) }
            else null
        )
    ) {
        // la Home dietro la cartella aperta si sfoca (vetro smerigliato)
        val blurRadius by androidx.compose.animation.core.animateDpAsState(
            if (vm.openFolderId != null) vm.settings.folderBlur.dp else 0.dp,
            androidx.compose.animation.core.tween(Motion.FOLDER, easing = Motion.Standard),
            label = "folderBlur"
        )
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().blur(blurRadius)) {
                if (vm.loaded) HomeScreen(vm, host)
            }
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
