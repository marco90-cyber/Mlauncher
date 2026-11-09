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
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.blur
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun Root(vm: LauncherViewModel, host: Host) {
    val view = LocalView.current
    val lightSurface = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    androidx.compose.runtime.LaunchedEffect(vm.overlay) {
        // chiudendo un pannello nessun campo di testo deve restare collegato alla tastiera
        if (vm.overlay == Overlay.NONE) focus.clearFocus(force = true)
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
        // cartelle, widget: il materiale scelto in Stile (generale o proprio dell'elemento)
        LocalFolderDock provides vm.settings.matOf("folder").let { m -> if (m.plain) null else DockLookSpec(m.style, m.opacity, m.glass, m.corner) },
        LocalGlassDarkText provides vm.labelDark,
        LocalLiquid provides (vm.settings.matOf("widgets").style == "liquid"),
        LocalWidgetMat provides vm.settings.matOf("widgets")
    ) {
        // la Home dietro la cartella aperta si sfoca (vetro smerigliato)
        val blurRadius by androidx.compose.animation.core.animateDpAsState(
            if (vm.openFolderId != null) vm.settings.folderBlur.dp else 0.dp,
            androidx.compose.animation.core.tween(Motion.FOLDER, easing = Motion.Standard),
            label = "folderBlur"
        )
        var rootSize by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
        val wb = vm.wallBitmap
        val wall = if (vm.customWallActive && wb != null && rootSize.width > 0) androidx.compose.runtime.remember(wb, rootSize, vm.settings.wallParallax) {
            WallRender(wb, rootSize.width.toFloat(), rootSize.height.toFloat(), vm.settings.wallParallax) { vm.homeScroll }
        } else null
        Box(Modifier.fillMaxSize().onSizeChanged { rootSize = it; vm.screenPx = it }) {
            androidx.compose.runtime.CompositionLocalProvider(LocalWall provides wall) {
                // ricerca come la pagina −1: la Home dietro si sfoca
                val searchBlur by androidx.compose.animation.core.animateDpAsState(
                    if (vm.overlay == Overlay.DRAWER && vm.searchMinusLook) vm.searchBlurEff.dp
                    else if (vm.overlay == Overlay.DRAWER && !vm.settings.matOf("drawer").plain) vm.settings.minusBlur.dp
                    else 0.dp,
                    androidx.compose.animation.core.tween(Motion.ALL_APPS_OPEN, easing = Motion.Standard),
                    label = "searchBlur"
                )
                // Sfocatura "a foto": appena serve (cartella, cassetto, pagina −1) si fotografa la Home una volta,
                // la si sfoca piccola in background e la si disegna sopra in dissolvenza. Prima la Home veniva
                // sfocata a grandezza piena a ogni fotogramma: era il lavoro più pesante per la scheda grafica.
                val homeLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
                var blurred by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
                var blurTarget by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
                val density = androidx.compose.ui.platform.LocalDensity.current
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    androidx.compose.runtime.snapshotFlow {
                        maxOf(blurRadius, (vm.minusP * vm.settings.minusBlur).dp, searchBlur) > 0.5.dp
                    }.collect { on ->
                        if (!on) {
                            blurred = null
                            return@collect
                        }
                        // quanto sarà sfocata alla fine (la dissolvenza va da 0 a questa)
                        val target = maxOf(
                            if (vm.openFolderId != null) vm.settings.folderBlur.dp else 0.dp,
                            if (vm.minusP > 0f) vm.settings.minusBlur.dp else 0.dp,
                            if (vm.overlay == Overlay.DRAWER && vm.searchMinusLook) vm.searchBlurEff.dp
                            else if (vm.overlay == Overlay.DRAWER && !vm.settings.matOf("drawer").plain) vm.settings.minusBlur.dp
                            else 0.dp
                        )
                        blurTarget = with(density) { target.toPx() }.coerceAtLeast(1f)
                        val shot = try { homeLayer.toImageBitmap() } catch (e: Exception) { null } ?: return@collect
                        val sigma = blurTarget * 0.577f
                        val img = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                            blurSnapshot(shot.asAndroidBitmap(), sigma)?.asImageBitmap()
                        }
                        // ancora da sfocare? (nel frattempo la cartella potrebbe essersi già chiusa)
                        if (maxOf(blurRadius, (vm.minusP * vm.settings.minusBlur).dp, searchBlur) > 0.5.dp) blurred = img
                    }
                }
                Box(
                    Modifier.fillMaxSize().drawWithContent {
                        homeLayer.record { this@drawWithContent.drawContent() }
                        val r = maxOf(blurRadius, (vm.minusP * vm.settings.minusBlur).dp, searchBlur).toPx()
                        val f = (r / blurTarget).coerceIn(0f, 1f)
                        val img = blurred
                        if (img == null || f <= 0f) {
                            homeLayer.alpha = 1f
                            drawLayer(homeLayer)
                        } else {
                            // la Home nitida sfuma via e la foto sfocata compare
                            if (f < 1f) {
                                homeLayer.alpha = 1f - f
                                drawLayer(homeLayer)
                            }
                            drawImage(
                                img,
                                dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                                alpha = f,
                                filterQuality = androidx.compose.ui.graphics.FilterQuality.Low
                            )
                        }
                    }
                ) {
                    // sfondo di MLauncher (con quello di Android si vede la finestra trasparente)
                    if (wall != null) Box(Modifier.fillMaxSize().returnWall(vm)) {
                        // sfoca e scurisci ora sono effetti dell'editor, dentro l'immagine
                        WallpaperLayer(wall, 0f, 0.dp)
                    }
                    if (vm.loaded) HomeScreen(vm, host)
                }
                if (vm.loaded) MinusPage(vm, host)
                FolderOverlay(vm, host)
            }
            AppsOverlay(vm, host)
            WidgetPicker(vm, host)
            SettingsOverlay(vm, host)
        }
    }

    vm.lastCrash?.let { CrashDialog(it, vm) }
    if (vm.showWelcome) WelcomeScreen(vm, host)
    vm.wallEditSrc?.let { WallpaperEditor(vm, it) }
    vm.genEdit?.let { WallGenerator(vm, it) }

    // l'ordine conta: l'ultimo registrato ha la precedenza
    // gesto indietro sulla Home senza nulla di aperto: come Launcher3 non chiude il Launcher
    // (prima l'attività si chiudeva e si ricaricava); riporta alla prima pagina
    BackHandler(enabled = !vm.showWelcome && vm.wallEditSrc == null && vm.genEdit == null) { vm.backOnHome() }
    BackHandler(enabled = vm.minusP > 0f) { if (vm.minusEdit) vm.minusEdit = false else vm.closeMinus() }
    BackHandler(enabled = vm.editMode) { vm.exitEdit() }
    BackHandler(enabled = vm.openFolderId != null) { vm.openFolderId = null }
    BackHandler(enabled = vm.overlay == Overlay.DRAWER) { vm.overlay = Overlay.NONE }
    BackHandler(enabled = vm.overlay == Overlay.WIDGETS) { vm.overlay = Overlay.NONE }
    BackHandler(enabled = vm.overlay == Overlay.SETTINGS && vm.genEdit == null && vm.wallEditSrc == null) {
        if (vm.settingsPage != null) vm.settingsBack() else vm.overlay = Overlay.NONE
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
