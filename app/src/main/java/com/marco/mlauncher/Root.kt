package com.marco.mlauncher

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
            vm.settingsQuery = ""
            vm.settingsHighlight = null
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
        Haptics.onPage = vm.settings.hapPage
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
        LocalFolderShape provides vm.settings.folderShape,
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

    vm.lastCrash?.let { CrashDialog(it, vm) }

    // l'ordine conta: l'ultimo registrato ha la precedenza
    BackHandler(enabled = vm.editMode) { vm.exitEdit() }
    BackHandler(enabled = vm.openFolderId != null) { vm.openFolderId = null }
    BackHandler(enabled = vm.overlay == Overlay.DRAWER) { vm.overlay = Overlay.NONE }
    BackHandler(enabled = vm.overlay == Overlay.WIDGETS) { vm.overlay = Overlay.NONE }
    BackHandler(enabled = vm.overlay == Overlay.SETTINGS) {
        if (vm.settingsPage != null) vm.settingsPage = null else vm.overlay = Overlay.NONE
    }
}

/** Dopo una chiusura improvvisa mostra l'errore, da copiare o condividere. */
@Composable
private fun CrashDialog(text: String, vm: LauncherViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { vm.clearCrash() },
        title = { androidx.compose.material3.Text("MLauncher si è chiuso per un errore") },
        text = {
            androidx.compose.foundation.layout.Column {
                androidx.compose.material3.Text(
                    "Copia o condividi questo testo e mandalo a Claude per correggere il problema.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                )
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .padding(top = 12.dp)
                        .heightIn(max = 320.dp)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                ) {
                    androidx.compose.material3.Text(
                        text,
                        fontSize = 11.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                val i = android.content.Intent(android.content.Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(android.content.Intent.EXTRA_TEXT, text)
                try {
                    ctx.startActivity(android.content.Intent.createChooser(i, "Condividi errore"))
                } catch (e: Exception) { }
            }) { androidx.compose.material3.Text("Condividi") }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                androidx.compose.material3.TextButton(onClick = {
                    clip.setText(androidx.compose.ui.text.AnnotatedString(text))
                    android.widget.Toast.makeText(ctx, "Copiato", android.widget.Toast.LENGTH_SHORT).show()
                }) { androidx.compose.material3.Text("Copia") }
                androidx.compose.material3.TextButton(onClick = { vm.clearCrash() }) { androidx.compose.material3.Text("Chiudi") }
            }
        }
    )
}
