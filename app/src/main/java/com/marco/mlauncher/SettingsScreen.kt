package com.marco.mlauncher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private class Cat(val key: String, val title: String, val summary: String, val icon: ImageVector)

private val shapeNames = mapOf(
    "original" to "Predefinita", "ios" to "iOS", "circle" to "Cerchio", "square" to "Quadrato arrotondato"
)
private val effectNames = mapOf(
    "slide" to "Scorrimento", "fade" to "Dissolvenza", "cube" to "Cubo", "rotate" to "Rotazione", "cascade" to "Cascata"
)
private val folderNames = mapOf("grid" to "Griglia 3\u00d73", "grid2" to "Griglia 2\u00d72", "stack" to "Ventaglio")
private val orderOptions = listOf(
    "name" to "Per nome (A-Z)",
    "name_desc" to "Per nome (Z-A)",
    "usage" to "Più usate per prime",
    "recent" to "Installate di recente",
    "color" to "Per colore dell'icona"
)
private val smartModes = listOf(
    "genre_plus" to "Genere preciso (consigliato)",
    "genre" to "Genere",
    "color" to "Colore dell'icona",
    "letter" to "Lettera iniziale"
)
private val themeNames = mapOf("auto" to "Automatico", "light" to "Chiaro", "dark" to "Scuro")
private val dockNames = mapOf("ios" to "iOS", "samsung" to "Samsung", "hyperos" to "HyperOS", "none" to "Nessuno")

private fun pageTitle(key: String?) = when (key) {
    "home" -> "Schermata Home"
    "dock" -> "Dock"
    "icons" -> "Icone e notifiche"
    "folders" -> "Cartelle"
    "drawer" -> "Cassetto app"
    "widgets" -> "Widget e meteo"
    "gestures" -> "Gesti e vibrazione"
    "look" -> "Tema e animazioni"
    "backup" -> "Backup e manutenzione"
    "wallpaper" -> "Sfondo"
    "info" -> "Informazioni"
    "log" -> "Registro log"
    else -> "Impostazioni"
}

/** Voce delle impostazioni da evidenziare (arrivando dalla ricerca). */
private val LocalSettingsHighlight = androidx.compose.runtime.compositionLocalOf<String?> { null }

/** Lampeggia e porta in vista la voce cercata. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Modifier.hl(title: String): Modifier {
    if (LocalSettingsHighlight.current != title) return this
    val req = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    val a = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(380)
        try { req.bringIntoView() } catch (e: Exception) { }
        repeat(2) {
            a.animateTo(1f, androidx.compose.animation.core.tween(260))
            a.animateTo(0.25f, androidx.compose.animation.core.tween(380))
        }
        a.animateTo(0f, androidx.compose.animation.core.tween(700))
    }
    val c = MaterialTheme.colorScheme.primary
    return this
        .bringIntoViewRequester(req)
        .drawBehind { drawRect(c.copy(alpha = 0.28f * a.value)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: LauncherViewModel, host: Host) {
    val cs = MaterialTheme.colorScheme
    val page = vm.settingsPage
    // ogni pagina riparte dall'orientamento reale del telefono
    androidx.compose.runtime.LaunchedEffect(page) { vm.settings.previewLand = null }
    androidx.compose.runtime.LaunchedEffect(vm.settingsHighlight) {
        if (vm.settingsHighlight != null) {
            kotlinx.coroutines.delay(3500)
            vm.settingsHighlight = null
        }
    }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    androidx.compose.runtime.CompositionLocalProvider(LocalSettingsHighlight provides vm.settingsHighlight) {
    Scaffold(
        containerColor = cs.surface,
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(pageTitle(page), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (vm.settingsPage != null) vm.settingsPage = null else vm.overlay = Overlay.NONE
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = cs.surface,
                    scrolledContainerColor = cs.surface
                )
            )
        }
    ) { pad ->
        AnimatedContent(
            targetState = page,
            modifier = Modifier.padding(pad),
            transitionSpec = {
                val inSpec = androidx.compose.animation.core.tween<androidx.compose.ui.unit.IntOffset>(Motion.SHEET, easing = Motion.EmphasizedDecelerate)
                val outSpec = androidx.compose.animation.core.tween<androidx.compose.ui.unit.IntOffset>(Motion.SHEET, easing = Motion.EmphasizedAccelerate)
                val fIn = androidx.compose.animation.core.tween<Float>(Motion.SHEET, easing = Motion.StandardDecelerate)
                val fOut = androidx.compose.animation.core.tween<Float>(Motion.FADE, easing = Motion.StandardAccelerate)
                if (targetState != null) {
                    (slideInHorizontally(inSpec) { it / 4 } + fadeIn(fIn)) togetherWith (slideOutHorizontally(outSpec) { -it / 4 } + fadeOut(fOut))
                } else {
                    (slideInHorizontally(inSpec) { -it / 4 } + fadeIn(fIn)) togetherWith (slideOutHorizontally(outSpec) { it / 4 } + fadeOut(fOut))
                }
            },
            label = "settingsPage"
        ) { p ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (p) {
                    null -> Dashboard(vm, host)
                    "home" -> PageHome(vm)
                    "dock" -> PageDock(vm)
                    "icons" -> PageIcons(vm, host)
                    "folders" -> PageFolders(vm)
                    "drawer" -> PageDrawer(vm)
                    "widgets" -> PageWidgets(vm, host)
                    "gestures" -> PageGestures(vm, host)
                    "look" -> PageLook(vm, host)
                    "wallpaper" -> PageWallpaper(vm, host)
                    "backup" -> PageBackup(vm, host)
                    "log" -> PageLog(vm)
                    else -> PageInfo(vm, host)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    }
}

@Composable
private fun Dashboard(vm: LauncherViewModel, host: Host) {
    val s = vm.settings
    val cs = MaterialTheme.colorScheme
    // ricerca tra tutte le voci delle impostazioni
    androidx.compose.material3.TextField(
        value = vm.settingsQuery,
        onValueChange = { vm.settingsQuery = it },
        singleLine = true,
        placeholder = { Text("Cerca nelle impostazioni") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (vm.settingsQuery.isNotEmpty()) IconButton(onClick = { vm.settingsQuery = "" }) {
                Icon(Icons.Default.Close, contentDescription = "Cancella")
            }
        },
        shape = RoundedCornerShape(28.dp),
        colors = androidx.compose.material3.TextFieldDefaults.colors(
            focusedContainerColor = cs.surfaceContainerHigh,
            unfocusedContainerColor = cs.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    )
    val q = vm.settingsQuery.trim()
    if (q.isNotEmpty()) {
        SettingsSearchResults(vm, q)
        return
    }
    if (!vm.isDefaultHome) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = cs.primaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "MLauncher non è la Home predefinita",
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onPrimaryContainer
                )
                Text(
                    "Impostala per usare i gesti e il tasto Home con MLauncher.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onPrimaryContainer,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                Button(onClick = { host.requestHomeRole() }) { Text("Imposta come predefinita") }
            }
        }
    }
    Group(null) {
        ListItem(
            headlineContent = { Text("Imposta come Home") },
            supportingContent = {
                Text(if (vm.isDefaultHome) "MLauncher è la Home predefinita" else "Tocca per scegliere MLauncher come Home")
            },
            colors = prefColors(),
            modifier = Modifier.clickable { host.requestHomeRole() }
        )
    }
    val sections = listOf(
        "Personalizza" to listOf(
            Cat("wallpaper", "Sfondo", if (vm.customWallActive) "Sfondo di MLauncher" + (if (s.liquid) " \u00b7 Liquid Glass" else "") else "Sfondo di Android", Icons.Default.Create),
            Cat("home", "Schermata Home", "${s.cols}\u00d7${s.rows} \u00b7 " + (if (s.homeMode == "drawer") "con cassetto" else "tutte le app in Home") + " \u00b7 indicatore di pagina", Icons.Default.Home),
            Cat("dock", "Dock", "${s.dockSlots} icone \u00b7 ${DOCK_STYLES.firstOrNull { it.first == s.dockStyle }?.second ?: ""}", Icons.Default.Menu),
            Cat("folders", "Cartelle", "Forma, anteprima, sfocatura", Icons.Default.List),
            *(if (s.homeMode == "drawer") arrayOf(
                Cat("drawer", "Cassetto app", "${if (s.drLayout == "list") "Elenco" else "Griglia"} \u00b7 ${s.drCols} colonne", Icons.Default.Menu)
            ) else emptyArray()),
            Cat("icons", "Icone e notifiche", "Forma: ${shapeNames[s.iconShape] ?: ""} \u00b7 badge \u00b7 app nascoste", Icons.Default.Star),
            Cat("widgets", "Widget e meteo", "Città del meteo, stack di widget", Icons.Default.Search)
        ),
        "Comportamento" to listOf(
            Cat("gestures", "Gesti e vibrazione", if (s.haptics) "Feedback tattile attivo" else "Feedback tattile spento", Icons.Default.Lock),
            Cat("look", "Tema e animazioni", "Tema ${(themeNames[s.theme] ?: "").lowercase()}", Icons.Default.Create)
        ),
        "Sistema" to listOf(
            Cat("backup", "Backup e manutenzione", "Esporta, ripristina, riavvia", Icons.Default.Build),
            Cat("log", "Registro log", if (vm.lastCrash != null) "C'è un errore registrato" else "Eventi ed errori del Launcher", Icons.Default.Warning),
            Cat("info", "Informazioni", vm.updateInfo?.let { "Aggiornamento ${it.version} disponibile" } ?: "MLauncher ${vm.currentVersionName} \u00b7 aggiornamenti", Icons.Default.Info)
        )
    )
    sections.forEach { (title, cats) ->
        Group(title) {
            cats.forEach { c ->
                ListItem(
                    headlineContent = { Text(c.title) },
                    supportingContent = { Text(c.summary) },
                    leadingContent = {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(cs.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) { Icon(c.icon, contentDescription = null, tint = cs.onPrimaryContainer) }
                    },
                    colors = prefColors(),
                    modifier = Modifier.clickable { vm.settingsPage = c.key }
                )
            }
        }
    }
}

/** Voci che il generatore dell'indice non riesce a leggere (titolo calcolato). */
private val EXTRA_INDEX = listOf(
    SettingEntry("Cerca aggiornamenti", "info", "Aggiornamenti", "Scarica e installa la nuova versione"),
    SettingEntry("Versione installata", "info", "Aggiornamenti", "")
)

@Composable
private fun SettingsSearchResults(vm: LauncherViewModel, q: String) {
    val cs = MaterialTheme.colorScheme
    val words = q.lowercase().split(' ').filter { it.isNotBlank() }
    val drawerOn = vm.settings.homeMode == "drawer"
    val pages = listOf("wallpaper", "home", "dock", "icons", "folders", "drawer", "widgets", "gestures", "look", "backup", "log", "info")
    val results = remember(q, drawerOn) {
        val pageHits = pages.filter { p -> words.all { pageTitle(p).lowercase().contains(it) } }
            .map { SettingEntry(pageTitle(it), it, "", "Apri la sezione") }
        val hits = (SETTINGS_INDEX + EXTRA_INDEX).filter { e ->
            val hay = (e.title + " " + e.group + " " + e.hint + " " + pageTitle(e.page)).lowercase()
            words.all { hay.contains(it) }
        }.sortedBy { if (it.title.lowercase().startsWith(words.first())) 0 else 1 }
        (pageHits + hits).filter { drawerOn || it.page != "drawer" }
    }
    if (results.isEmpty()) {
        Text(
            "Nessuna impostazione trovata",
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp)
        )
        return
    }
    Group("${results.size} risultati") {
        results.take(40).forEach { e ->
            ListItem(
                headlineContent = { Text(e.title) },
                supportingContent = {
                    Text(
                        pageTitle(e.page) + (if (e.group.isNotEmpty()) " \u203a " + e.group else "") +
                            (if (e.hint.isNotEmpty() && e.hint != "Apri la sezione") "\n" + e.hint else "")
                    )
                },
                colors = prefColors(),
                modifier = Modifier.clickable {
                    vm.settingsHighlight = if (e.hint == "Apri la sezione") null else e.title
                    vm.settingsPage = e.page
                }
            )
        }
    }
}

@Composable
private fun PageLog(vm: LauncherViewModel) {
    val cs = MaterialTheme.colorScheme
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    var log by remember { mutableStateOf(AppLog.read()) }
    val crash = vm.lastCrash
    val all = (if (crash != null) "=== ULTIMO ERRORE ===\n$crash\n\n" else "") + "=== REGISTRO ===\n$log"
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        androidx.compose.material3.FilledTonalButton(onClick = { log = AppLog.read(); vm.loadCrash() }) { Text("Aggiorna") }
        androidx.compose.material3.FilledTonalButton(onClick = {
            clip.setText(androidx.compose.ui.text.AnnotatedString(all))
            android.widget.Toast.makeText(ctx, "Copiato", android.widget.Toast.LENGTH_SHORT).show()
        }) { Text("Copia") }
        androidx.compose.material3.FilledTonalButton(onClick = {
            val i = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                .putExtra(android.content.Intent.EXTRA_TEXT, all)
            try { ctx.startActivity(android.content.Intent.createChooser(i, "Condividi registro")) } catch (e: Exception) { }
        }) { Text("Invia") }
    }
    if (crash != null) Group("Ultimo errore") {
        Text(
            crash.take(6000),
            fontSize = 11.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = cs.onErrorContainer,
            modifier = Modifier.fillMaxWidth().background(cs.errorContainer).padding(14.dp)
        )
    }
    val entries = remember(log) {
        log.split(Regex("\n(?=\\d\\d/\\d\\d \\d\\d:)")).filter { it.isNotBlank() }.reversed().take(400)
    }
    Group(if (entries.isEmpty()) "Registro vuoto" else "Eventi, dal più recente") {
        Column(Modifier.fillMaxWidth().background(cs.surfaceContainer).padding(12.dp)) {
            if (entries.isEmpty()) Text("Ancora nessun evento registrato.", color = cs.onSurfaceVariant)
            entries.forEach { line ->
                val lvl = line.drop(16).take(3).trim()
                Text(
                    line.trimEnd(),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = when (lvl) {
                        "E" -> cs.error
                        "W" -> cs.tertiary
                        else -> cs.onSurface
                    },
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
    Group(null) {
        ListItem(
            headlineContent = { Text("Cancella il registro", color = cs.error) },
            supportingContent = { Text("Svuota gli eventi e l'ultimo errore") },
            colors = prefColors(),
            modifier = Modifier.clickable {
                AppLog.clear()
                vm.clearCrash()
                log = ""
            }
        )
    }
}

@Composable
private fun PageWallpaper(vm: LauncherViewModel, host: Host) {
    val s = vm.settings
    val cs = MaterialTheme.colorScheme
    val ctx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(vm.wallError) {
        if (vm.wallError > 0) android.widget.Toast.makeText(
            ctx, "Immagine non disponibile. Android a volte non lascia leggere il suo sfondo: sceglilo dalla galleria", android.widget.Toast.LENGTH_LONG
        ).show()
    }
    Group("Quale sfondo") {
        ChoiceRow(
            "Sfondo della Home",
            listOf("system" to "Quello di Android", "custom" to "Scelto in MLauncher"),
            s.wallMode
        ) {
            vm.setSetting("wallMode", it)
            vm.readWallpaperHint()
        }
        Text(
            "Con lo sfondo scelto in MLauncher hai il Liquid Glass completo, la parallasse e i colori presi dalla tua immagine. Gli sfondi animati funzionano solo con \"Quello di Android\".",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().background(cs.surfaceContainer).padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
    if (s.wallMode == "custom") {
        val bm = vm.wallBitmap
        if (bm != null) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Image(
                bm, contentDescription = "Sfondo attuale",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(120.dp, 250.dp).clip(RoundedCornerShape(22.dp)).border(1.dp, cs.outlineVariant, RoundedCornerShape(22.dp))
            )
        }
        Group("Scegli l'immagine") {
            ListItem(
                headlineContent = { Text(if (vm.wallBusy) "Preparo lo sfondo\u2026" else "Dalla galleria") },
                supportingContent = { Text("Una tua foto o un'immagine qualsiasi") },
                colors = prefColors(),
                modifier = Modifier.clickable { host.pickWallpaperImage() }
            )
            ListItem(
                headlineContent = { Text("Importa lo sfondo di Android") },
                supportingContent = { Text("Copia quello che hai adesso, se il sistema lo permette") },
                colors = prefColors(),
                modifier = Modifier.clickable { vm.importSystemWallpaper() }
            )
            Column(Modifier.fillMaxWidth().background(cs.surfaceContainer).padding(12.dp)) {
                Text("Sfondi inclusi", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BUILTIN_WALLS.forEach { (k, l) ->
                        val thumb = remember(k) { builtinWall(k, 90, 180).asImageBitmap() }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { vm.builtinWallpaper(k) }) {
                            androidx.compose.foundation.Image(
                                thumb, contentDescription = l,
                                modifier = Modifier.size(60.dp, 120.dp).clip(RoundedCornerShape(14.dp))
                            )
                            Text(l, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
        if (bm != null) {
            Group("Effetti") {
                SliderRow("Scurisci", if (s.wallDim == 0) "No" else "${s.wallDim}%", s.wallDim.toFloat(), 0f..60f, 11) {
                    val v = (it / 5f).roundToInt() * 5
                    if (v != s.wallDim) vm.setSetting("wallDim", v)
                }
                SliderRow("Sfoca", if (s.wallBlur == 0) "No" else "${s.wallBlur} dp", s.wallBlur.toFloat(), 0f..24f, 11) {
                    val v = (it / 2f).roundToInt() * 2
                    if (v != s.wallBlur) vm.setSetting("wallBlur", v)
                }
                SwitchRow("Parallasse", "Lo sfondo scorre piano quando cambi pagina", s.wallParallax) { vm.setSetting("wallParallax", it) }
            }
            Group("Schermata di blocco") {
                ListItem(
                    headlineContent = { Text("Usa anche sulla schermata di blocco") },
                    supportingContent = { Text("Imposta questa immagine come sfondo di blocco di Android. Per uno diverso, scegli lì un'altra immagine") },
                    colors = prefColors(),
                    modifier = Modifier.clickable {
                        val ok = vm.wallpaperToLockScreen()
                        android.widget.Toast.makeText(ctx, if (ok) "Sfondo di blocco impostato" else "Non riuscito", android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

/** Gruppo di voci che hanno un valore per il verticale e uno per l'orizzontale. */
@Composable
private fun OrientedGroup(title: String, s: LauncherSettings, content: @Composable () -> Unit) {
    Group(title) {
        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(top = 12.dp, bottom = 6.dp)
        ) {
            Text(
                "Queste voci hanno valori separati per il telefono in verticale e in orizzontale",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)
            )
            OrientationTabs(s)
        }
        content()
    }
}

/** Scelta dell'orientazione di cui si stanno modificando i valori. */
@Composable
private fun OrientationTabs(s: LauncherSettings) {
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        androidx.compose.material3.FilterChip(
            selected = !s.landscape,
            onClick = { s.previewLand = false },
            label = { Text("Verticale") },
            modifier = Modifier.weight(1f)
        )
        androidx.compose.material3.FilterChip(
            selected = s.landscape,
            onClick = { s.previewLand = true },
            label = { Text("Orizzontale") },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PageHome(vm: LauncherViewModel) {
    val s = vm.settings
    Group("Disposizione delle app") {
        SwitchRow(
            "Blocca la Home",
            "Icone, cartelle e widget non si possono spostare né togliere. Si sblocca anche dal menu della Home",
            s.lockHome
        ) { vm.setSetting("lockHome", it) }
        var askOrder by remember { mutableStateOf(false) }
        ChoiceRow(
            "Modalità Home",
            listOf("drawer" to "Home e cassetto app", "all" to "Tutte le app in Home"),
            s.homeMode
        ) {
            vm.setSetting("homeMode", it)
            if (it == "all") askOrder = true
        }
        if (s.homeMode == "all") {
            SwitchRow(
                "Cartelle intelligenti",
                "Raggruppa le app in cartelle. Le app che non rientrano in nessun gruppo restano in Home, senza cartelle generiche",
                s.autoFolders
            ) { vm.setSetting("autoFolders", it) }
            if (s.autoFolders) ChoiceRow("Raggruppa per", smartModes, s.smartMode) { vm.setSetting("smartMode", it) }
            ChoiceRow(if (s.autoFolders) "Come disporre le app sciolte" else "Come disporre le app", orderOptions, s.homeOrder) { vm.setSetting("homeOrder", it) }
        } else {
            SwitchRow(
                "Aggiungi le nuove app in Home",
                "Altrimenti restano solo nel cassetto",
                s.autoAddNew
            ) { vm.setSetting("autoAddNew", it) }
        }
        if (askOrder) {
            AlertDialog(
                onDismissRequest = { askOrder = false },
                title = { Text("Come vuoi le app in Home?") },
                text = {
                    Column {
                        orderOptions.forEach { (k, l) ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    vm.setSetting("homeOrder", k)
                                    askOrder = false
                                }.padding(vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = k == s.homeOrder, onClick = null)
                                Spacer(Modifier.width(12.dp))
                                Text(l)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { askOrder = false }) { Text("Chiudi") } }
            )
        }
    }
    OrientedGroup("Griglia e icone", s) {
        SliderRow("Colonne", "${s.cols}", s.cols.toFloat(), if (s.landscape) 5f..10f else 4f..6f, if (s.landscape) 4 else 1, preview = { GridPreview(s.cols, s.rows) }) {
            val v = it.roundToInt()
            if (v != s.cols) vm.setSetting("cols", v)
        }
        SliderRow("Righe", "${s.rows}", s.rows.toFloat(), if (s.landscape) 2f..5f else 5f..8f, 2, preview = { GridPreview(s.cols, s.rows) }) {
            val v = it.roundToInt()
            if (v != s.rows) vm.setSetting("rows", v)
        }
        SliderRow("Dimensione icone", "${(s.iconScale * 100).roundToInt()}%", s.iconScale, 0.8f..1.2f, 0, preview = { ShapePreview(s.iconShape, 30.dp * s.iconScale) }) {
            vm.setSetting("iconScale", (it * 20).roundToInt() / 20f)
        }
    }
    Group("Aspetto") {
        SwitchRow("Mostra i nomi delle app", null, s.showLabels) { vm.setSetting("showLabels", it) }
        if (s.showLabels) ChoiceRow(
            "Colore dei nomi",
            listOf("auto" to "Automatico (in base allo sfondo)", "white" to "Sempre bianchi", "dark" to "Sempre scuri"),
            s.labelColor
        ) { vm.setSetting("labelColor", it) }
        SwitchRow(
            "Rotazione della Home",
            "In orizzontale la dock passa a lato. Spento: la Home resta in verticale",
            s.allowRotation
        ) { vm.setSetting("allowRotation", it) }
    }
    Group("Pagine") {
        ChoiceRow(
            "Indicatore di pagina",
            listOf(
                "ios" to "iOS 27 (diventa \"Cerca\")",
                "pill" to "Puntini (attivo allungato)",
                "dots" to "Puntini semplici",
                "lines" to "Linee",
                "worm" to "Puntini con ovale scorrevole",
                "progress" to "Barra di avanzamento",
                "number" to "Numero (2 / 5)",
                "none" to "Nessuno"
            ),
            s.pageIndicator,
            preview = { IndicatorPreview(it) }
        ) { vm.setSetting("pageIndicator", it) }
        SwitchRow(
            "Indicatore come la dock",
            "Prende stile, trasparenza ed effetto vetro della dock",
            s.indFollowDock
        ) { vm.setSetting("indFollowDock", it) }
        ChoiceRow(
            "Effetto cambio pagina",
            listOf(
                "slide" to "Scorrimento",
                "fade" to "Dissolvenza",
                "cube" to "Cubo",
                "rotate" to "Rotazione",
                "cascade" to "Cascata"
            ),
            s.pageEffect,
            preview = { EffectPreview(it) }
        ) { vm.setSetting("pageEffect", it) }
        Text(
            "Con qualsiasi indicatore: tieni premuto sopra e scorri per cambiare pagina al volo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun PageDock(vm: LauncherViewModel) {
    val s = vm.settings
    DockLivePreview(s)
    Group("Stile") {
        ChoiceRow(
            "Stile della dock",
            DOCK_STYLES,
            s.dockStyle,
            preview = { DockPreview(it, s.dockSlots, s.dockOpacity, s.dockGlass, s.dockCorner) }
        ) { vm.setSetting("dockStyle", it) }
        SwitchRow(
            "Effetto glass",
            "Riflesso e bordo luminoso come il vetro",
            s.dockGlass
        ) { vm.setSetting("dockGlass", it) }
        SliderRow("Trasparenza", "${100 - s.dockOpacity}%", s.dockOpacity.toFloat(), 0f..100f, 19) {
            val v = (it / 5f).roundToInt() * 5
            if (v != s.dockOpacity) vm.setSetting("dockOpacity", v)
        }
        SliderRow("Arrotondamento", "${s.dockCorner} dp", s.dockCorner.toFloat(), 0f..40f, 0) {
            val v = it.roundToInt()
            if (v != s.dockCorner) vm.setSetting("dockCorner", v)
        }
    }
    OrientedGroup("Dimensioni", s) {
        SliderRow("Grandezza", "${(s.dockSize * 100).roundToInt()}%", s.dockSize, 0.8f..1.2f, 7) {
            vm.setSetting("dockSize", (it * 20).roundToInt() / 20f)
        }
        SliderRow("Icone nella dock", "${s.dockSlots}", s.dockSlots.toFloat(), 4f..6f, 1) {
            val v = it.roundToInt()
            if (v != s.dockSlots) vm.setSetting("dockSlots", v)
        }
    }
}

@Composable
private fun PageIcons(vm: LauncherViewModel, host: Host) {
    val s = vm.settings
    Group("Icone") {
        ChoiceRow(
            "Forma",
            listOf(
                "original" to "Predefinita (come sono sempre state)",
                "ios" to "iOS (squircola)",
                "circle" to "Cerchio",
                "square" to "Quadrato arrotondato"
            ),
            s.iconShape,
            preview = { ShapePreview(it, 34.dp) }
        ) { vm.setSetting("iconShape", it) }
        ChoiceRow(
            "Pacchetto icone",
            listOf("" to "Predefinito") + vm.iconPacks.map { it.first to it.second },
            s.iconPack
        ) { vm.setSetting("iconPack", it) }
        SwitchRow(
            "Icone Material You",
            "Icone monocromatiche con i colori dello sfondo, dove l'app le supporta",
            s.themedIcons
        ) { vm.setSetting("themedIcons", it) }
    }
    Group("Notifiche") {
        ChoiceRow(
            "Badge delle notifiche",
            listOf("off" to "Disattivati", "dot" to "Puntino", "count" to "Numero"),
            s.badges,
            preview = { BadgePreview(it) }
        ) {
            vm.setSetting("badges", it)
            if (it != "off" && !vm.listenerEnabled) host.openListenerSettings()
        }
        if (s.badges != "off" && !vm.listenerEnabled) {
            ListItem(
                headlineContent = { Text("Serve l'accesso alle notifiche") },
                supportingContent = { Text("Tocca per attivarlo per MLauncher") },
                colors = prefColors(),
                modifier = Modifier.clickable { host.openListenerSettings() }
            )
        }
    }
    Group("App") {
        var hiddenOpen by remember { mutableStateOf(false) }
        ListItem(
            headlineContent = { Text("App nascoste") },
            supportingContent = { Text("${vm.hidden.size} nascoste") },
            colors = prefColors(),
            modifier = Modifier.clickable { hiddenOpen = true }
        )
        if (hiddenOpen) {
            AlertDialog(
                onDismissRequest = { hiddenOpen = false },
                title = { Text("App nascoste") },
                text = {
                    Column {
                        if (vm.hidden.isEmpty()) Text("Nessuna app nascosta")
                        vm.hidden.toList().forEach { id ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(vm.apps[id]?.label ?: id, Modifier.weight(1f))
                                TextButton(onClick = { vm.unhideApp(id) }) { Text("Mostra") }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { hiddenOpen = false }) { Text("Chiudi") } }
            )
        }
    }
}

@Composable
private fun PageDrawer(vm: LauncherViewModel) {
    val s = vm.settings
    Group("Disposizione") {
        ChoiceRow(
            "Disposizione",
            listOf("grid" to "Griglia", "list" to "Elenco"),
            s.drLayout
        ) { vm.setSetting("drLayout", it) }
        ChoiceRow(
            "Ordine delle app",
            listOf("name" to "Per nome (A-Z)", "name_desc" to "Per nome (Z-A)", "usage" to "Più usate", "recent" to "Installate di recente"),
            s.drSort
        ) { vm.setSetting("drSort", it) }
        SwitchRow("App suggerite", "Le 4 più usate in cima", s.drSuggest) { vm.setSetting("drSuggest", it) }
        SwitchRow("Ricorda la posizione", "Riapre il cassetto dove l'avevi lasciato", s.drRemember) { vm.setSetting("drRemember", it) }
    }
    Group("Ricerca e scorrimento") {
        SwitchRow("Barra di ricerca", "In cima al cassetto", s.drSearchBar) { vm.setSetting("drSearchBar", it) }
        if (s.drSearchBar) SwitchRow("Sfondo della barra di ricerca", null, s.drSearchBg) { vm.setSetting("drSearchBg", it) }
        SwitchRow("Barra laterale A-Z", "Scorri veloce tra le lettere", s.drScrollbar) { vm.setSetting("drScrollbar", it) }
    }
    Group("Nomi delle app") {
        SwitchRow("Mostra i nomi", null, s.drLabels) { vm.setSetting("drLabels", it) }
        if (s.drLabels) {
            SliderRow("Dimensione del testo", "${s.drLabelSize} sp", s.drLabelSize.toFloat(), 9f..16f, 6) {
                val v = it.roundToInt()
                if (v != s.drLabelSize) vm.setSetting("drLabelSize", v)
            }
            SwitchRow("Nome su due righe", null, s.drTwoLine) { vm.setSetting("drTwoLine", it) }
        }
        SwitchRow(
            "Puntino sulle app nuove",
            "Segna le app appena installate finché non le apri",
            s.newAppDot
        ) { vm.setSetting("newAppDot", it) }
    }
    Group("Sfondo") {
        ChoiceRow(
            "Colore di sfondo",
            listOf("surface" to "Superficie", "tonal" to "Tonale (colore principale)", "neutral" to "Neutro", "dark" to "Nero"),
            s.drBg
        ) { vm.setSetting("drBg", it) }
        SliderRow("Opacità dello sfondo", "${s.drOpacity}%", s.drOpacity.toFloat(), 0f..100f, 19) {
            val v = (it / 5f).roundToInt() * 5
            if (v != s.drOpacity) vm.setSetting("drOpacity", v)
        }
    }
    OrientedGroup("Griglia e spaziatura", s) {
        if (s.drLayout == "grid") SliderRow("Colonne", "${s.drCols}", s.drCols.toFloat(), 3f..6f, 2) {
            val v = it.roundToInt()
            if (v != s.drCols) vm.setSetting("drCols", v)
        }
        SliderRow("Dimensione icone", "${(s.drIcon * 100).roundToInt()}%", s.drIcon, 0.7f..1.4f, 13) {
            vm.setSetting("drIcon", (it * 20).roundToInt() / 20f)
        }
        SliderRow("Altezza delle righe", "${(s.drRowH * 100).roundToInt()}%", s.drRowH, 0.6f..1.6f, 9) {
            vm.setSetting("drRowH", (it * 20).roundToInt() / 20f)
        }
        SliderRow("Rientro laterale", "${s.drIndent} dp", s.drIndent.toFloat(), 0f..32f, 0) {
            val v = it.roundToInt()
            if (v != s.drIndent) vm.setSetting("drIndent", v)
        }
        SliderRow("Margine superiore", "${s.drTop} dp", s.drTop.toFloat(), 0f..48f, 0) {
            val v = it.roundToInt()
            if (v != s.drTop) vm.setSetting("drTop", v)
        }
    }
}

@Composable
private fun PageFolders(vm: LauncherViewModel) {
    val s = vm.settings
    Group("Aspetto") {
        SwitchRow(
            "Segui l'aspetto della dock",
            "Le cartelle usano stile, vetro e trasparenza della dock",
            s.folderFollowDock
        ) { vm.setSetting("folderFollowDock", it) }
        ChoiceRow(
            "Forma della cartella",
            FOLDER_SHAPES,
            s.folderShape,
            preview = { FolderShapePreview(it) }
        ) { vm.setSetting("folderShape", it) }
        ChoiceRow(
            "Anteprima della cartella",
            listOf("grid" to "Griglia 3\u00d73", "grid2" to "Griglia 2\u00d72 (icone grandi)", "stack" to "Ventaglio"),
            s.folderPreview,
            preview = { FolderPreview(it, s.folderBg, s.folderShape) }
        ) { vm.setSetting("folderPreview", it) }
        if (!s.folderFollowDock) ChoiceRow(
            "Sfondo della cartella",
            listOf("tonal" to "Tonale", "primary" to "Colore principale", "dark" to "Scuro trasparente", "glass" to "Vetro chiaro"),
            s.folderBg,
            preview = { FolderPreview(s.folderPreview, it, s.folderShape) }
        ) { vm.setSetting("folderBg", it) }
    }
    Group("Cartella aperta") {
        SliderRow("Sfocatura dello sfondo", if (s.folderBlur == 0) "Nessuna" else "${s.folderBlur} dp", s.folderBlur.toFloat(), 0f..40f, 0) {
            val v = (it / 2f).roundToInt() * 2
            if (v != s.folderBlur) vm.setSetting("folderBlur", v)
        }
        SliderRow("Colonne", "${s.folderCols}", s.folderCols.toFloat(), 3f..5f, 1) {
            val v = it.roundToInt()
            if (v != s.folderCols) vm.setSetting("folderCols", v)
        }
        ListItem(
            headlineContent = { Text("Ordina A-Z") },
            supportingContent = { Text("Tieni premuta una cartella nella Home e scegli \"Ordina A-Z\". Trascina le icone per riordinarle a mano.") },
            colors = prefColors()
        )
    }
}

/** Anteprima della sola forma: grande, piena e bordata, così le forme si distinguono a colpo d'occhio. */
@Composable
private fun FolderShapePreview(shapeKey: String) {
    val cs = MaterialTheme.colorScheme
    val shape = folderShapeOf(shapeKey)
    Box(
        Modifier
            .size(40.dp)
            .clip(shape)
            .background(cs.primary)
            .border(2.dp, cs.primary, shape),
        contentAlignment = Alignment.Center
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(2) { Box(Modifier.size(7.dp).clip(RoundedCornerShape(2.dp)).background(cs.onPrimary.copy(alpha = 0.9f))) }
                }
            }
        }
    }
}

@Composable
private fun FolderPreview(style: String, bgKey: String, shapeKey: String = "squircle") {
    val cs = MaterialTheme.colorScheme
    val bg = when (bgKey) {
        "primary" -> cs.primaryContainer
        "dark" -> Color.Black.copy(alpha = 0.45f)
        "glass" -> Color.White.copy(alpha = 0.5f)
        else -> cs.secondaryContainer
    }
    val shp = folderShapeOf(shapeKey)
    Box(Modifier.size(34.dp).clip(shp).background(bg).border(1.dp, cs.primary.copy(alpha = 0.45f), shp).padding((34 * folderShapeInset(shapeKey)).dp)) {
        when (style) {
            "grid2" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                repeat(2) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    repeat(2) { Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(cs.primary)) }
                } }
            }
            "stack" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                repeat(3) { i ->
                    val k = 2 - i
                    Box(
                        Modifier.offset(x = (2 * k).dp, y = (-2 * k).dp).size(14.dp)
                            .graphicsLayer { rotationZ = k * 8f }
                            .clip(RoundedCornerShape(4.dp)).background(cs.primary.copy(alpha = 1f - 0.25f * k))
                    )
                }
            }
            else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                repeat(3) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    repeat(3) { Box(Modifier.size(6.dp).clip(RoundedCornerShape(2.dp)).background(cs.primary)) }
                } }
            }
        }
    }
}

@Composable
private fun PageWidgets(vm: LauncherViewModel, host: Host) {
    val s = vm.settings
    var cityDlg by remember { mutableStateOf(false) }
    if (cityDlg) CityPickerDialog(vm, host) { cityDlg = false }
    Group("Meteo") {
        ListItem(
            headlineContent = { Text("Città del meteo") },
            supportingContent = {
                Text(vm.weatherCity?.let { c -> c.name + (if (c.area.isNotEmpty()) " \u00b7 " + c.area else "") } ?: "Posizione del telefono")
            },
            colors = prefColors(),
            modifier = Modifier.clickable { cityDlg = true }
        )
    }
    Group("Ricerca") {
        Text(
            "La barra di ricerca è un widget: tieni premuto sulla Home → Widget → Barra di ricerca. Puoi spostarla e ridimensionarla come gli altri.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
    Group("Widget") {
        SwitchRow(
            "Rotazione automatica degli stack",
            "I widget impilati scorrono da soli",
            s.stackAutoRotate
        ) { vm.setSetting("stackAutoRotate", it) }
    }
}

@Composable
private fun PageGestures(vm: LauncherViewModel, host: Host) {
    val s = vm.settings
    Group("Gesti") {
        SwitchRow(
            "Scuoti per riordinare",
            "In modifica Home, scuoti il telefono per compattare la pagina dall'alto in basso",
            s.shakeTidy
        ) { vm.setSetting("shakeTidy", it) }
        SwitchRow(
            "Tasto Home apre il cassetto",
            "Se sei già sulla prima pagina della Home",
            s.homeOpensDrawer
        ) { vm.setSetting("homeOpensDrawer", it) }
        ChoiceRow(
            "Scorri giù nella Home",
            listOf("notifications" to "Apri le notifiche", "search" to "Cerca app"),
            s.swipeDown
        ) { vm.setSetting("swipeDown", it) }
        ListItem(
            headlineContent = { Text("Scorri su nella Home") },
            supportingContent = {
                Text(if (s.homeMode == "drawer") "Apre il cassetto app" else "Apre la ricerca")
            },
            colors = prefColors()
        )
        SwitchRow(
            "Doppio tocco per bloccare",
            "Tocca due volte uno spazio vuoto della Home",
            s.doubleTapLock
        ) {
            vm.setSetting("doubleTapLock", it)
            if (it && !vm.accessibilityEnabled) host.openAccessibilitySettings()
        }
        if (s.doubleTapLock && !vm.accessibilityEnabled) {
            ListItem(
                headlineContent = { Text("Serve il servizio di accessibilità") },
                supportingContent = { Text("Tocca per attivare MLauncher") },
                colors = prefColors(),
                modifier = Modifier.clickable { host.openAccessibilitySettings() }
            )
        }
    }
    Group("Vibrazione") {
        SwitchRow(
            "Feedback tattile",
            "Interruttore generale",
            s.haptics
        ) { vm.setSetting("haptics", it) }
    }
    if (s.haptics) Group("Quando vibrare") {
        SwitchRow(
            "Pressione lunga e trascinamento",
            "Menu rapidi, inizio spostamento di icone e widget",
            s.hapPress
        ) { vm.setSetting("hapPress", it) }
        SwitchRow(
            "Rilascio e conferme",
            "Icona posata, cartella creata, widget aggiunto, azione non possibile",
            s.hapDrop
        ) { vm.setSetting("hapDrop", it) }
        SwitchRow(
            "A ogni pagina",
            "Un tocco leggero quando cambi pagina nella Home",
            s.hapPage
        ) { vm.setSetting("hapPage", it) }
        SwitchRow(
            "Scorrimento e selezione",
            "Ridimensionamento, lista del cassetto, gesti su e giù",
            s.hapTick
        ) { vm.setSetting("hapTick", it) }
    }
}

@Composable
private fun PageLook(vm: LauncherViewModel, host: Host) {
    val s = vm.settings
    Group("Liquid Glass") {
        SwitchRow(
            "Tema Liquid Glass",
            "Dock, cartelle, indicatore e widget in vetro liquido che piega e sfoca lo sfondo",
            s.liquid
        ) {
            vm.setSetting("liquid", it)
            if (it) {
                vm.setSetting("dockStyle", "liquid")
                vm.setSetting("folderFollowDock", true)
                vm.setSetting("indFollowDock", true)
            } else if (s.dockStyle == "liquid") {
                vm.setSetting("dockStyle", "glass")
            }
        }
        if (s.liquid && !vm.customWallActive) ListItem(
            headlineContent = { Text("Scegli uno sfondo di MLauncher") },
            supportingContent = { Text("Con lo sfondo di Android il vetro è imitato: per la lente e la sfocatura vere serve uno sfondo scelto in MLauncher") },
            colors = prefColors(),
            modifier = Modifier.clickable { vm.settingsPage = "wallpaper" }
        )
    }
    Group("Tema") {
        ChoiceRow(
            "Tema",
            listOf("auto" to "Automatico", "light" to "Chiaro", "dark" to "Scuro"),
            s.theme,
            preview = { ThemePreview(it) }
        ) { vm.setSetting("theme", it) }
        ChoiceRow(
            "Colori",
            ACCENTS,
            s.accent,
            preview = { AccentPreview(it) }
        ) { vm.setSetting("accent", it) }
    }
    Group("Animazioni") {
        ChoiceRow(
            "Velocità animazioni",
            listOf("0" to "Rilassata", "1" to "Bilanciata", "2" to "Veloce"),
            s.animSpeed.toString()
        ) { vm.setSetting("animSpeed", it.toInt()) }
        ChoiceRow(
            "Animazione di disinstallazione",
            listOf("boom" to "Esplosione", "fade" to "Dissolvenza", "none" to "Nessuna"),
            s.uninstallAnim
        ) { vm.setSetting("uninstallAnim", it) }
    }
}

@Composable
private fun PageBackup(vm: LauncherViewModel, host: Host) {
    Group("Backup") {
        ListItem(
            headlineContent = { Text("Esporta layout e impostazioni") },
            colors = prefColors(),
            modifier = Modifier.clickable { host.exportBackup() }
        )
        ListItem(
            headlineContent = { Text("Ripristina da file") },
            colors = prefColors(),
            modifier = Modifier.clickable { host.importBackup() }
        )
    }
    Group("Backup automatico") {
        val s = vm.settings
        SwitchRow(
            "Backup automatico",
            "Salva da solo Home, cartelle, note e impostazioni nella cartella che scegli",
            s.autoBackup
        ) {
            vm.setSetting("autoBackup", it)
            if (it && s.backupDir.isEmpty()) host.pickBackupFolder()
        }
        ListItem(
            headlineContent = { Text("Cartella del backup") },
            supportingContent = {
                Text(
                    if (s.backupDir.isEmpty()) "Nessuna: tocca per sceglierla"
                    else android.net.Uri.decode(s.backupDir).substringAfterLast(':').ifEmpty { "Cartella scelta" }
                )
            },
            colors = prefColors(),
            modifier = Modifier.clickable { host.pickBackupFolder() }
        )
        ChoiceRow(
            "Ogni quanto",
            listOf("1" to "Ogni giorno", "3" to "Ogni 3 giorni", "7" to "Ogni settimana", "14" to "Ogni 2 settimane", "30" to "Ogni mese"),
            s.backupEvery.toString()
        ) { vm.setSetting("backupEvery", it.toInt()) }
        ChoiceRow(
            "Quanti backup tenere",
            listOf("3" to "Gli ultimi 3", "5" to "Gli ultimi 5", "10" to "Gli ultimi 10"),
            s.backupKeep.toString()
        ) { vm.setSetting("backupKeep", it.toInt()) }
        val last = vm.lastAutoBackup
        ListItem(
            headlineContent = { Text(if (vm.autoBackupBusy) "Backup in corso\u2026" else "Fai ora un backup") },
            supportingContent = {
                Text(
                    vm.autoBackupError?.let { "Ultimo tentativo non riuscito: $it" }
                        ?: if (last > 0) "Ultimo backup: " + java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.ITALY).format(java.util.Date(last))
                        else "Ancora nessun backup automatico"
                )
            },
            colors = prefColors(),
            modifier = Modifier.clickable {
                if (s.backupDir.isEmpty()) host.pickBackupFolder() else vm.runAutoBackup(force = true)
            }
        )
    }
    Group("Manutenzione") {
        var confirmReset by remember { mutableStateOf(false) }
        ListItem(
            headlineContent = { Text("Ripristina Home") },
            supportingContent = { Text("Riporta la Home come all'inizio: widget, icone predefinite e dock") },
            colors = prefColors(),
            modifier = Modifier.clickable { confirmReset = true }
        )
        if (confirmReset) {
            AlertDialog(
                onDismissRequest = { confirmReset = false },
                title = { Text("Ripristinare la Home?") },
                text = { Text("La disposizione attuale di icone, cartelle e widget verrà sostituita. Le impostazioni restano.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmReset = false
                        vm.resetHome()
                        vm.overlay = Overlay.NONE
                    }) { Text("Ripristina") }
                },
                dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Annulla") } }
            )
        }
        var confirmAll by remember { mutableStateOf(false) }
        ListItem(
            headlineContent = { Text("Ripristina Launcher") },
            supportingContent = { Text("Porta tutto come all'inizio, impostazioni predefinite comprese") },
            colors = prefColors(),
            modifier = Modifier.clickable { confirmAll = true }
        )
        if (confirmAll) {
            AlertDialog(
                onDismissRequest = { confirmAll = false },
                title = { Text("Ripristinare il Launcher?") },
                text = { Text("Home, cartelle, widget, app nascoste e tutte le impostazioni tornano ai valori iniziali. Il Launcher si riavvia.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmAll = false
                        vm.resetLauncher()
                        host.restartLauncher()
                    }) { Text("Ripristina tutto") }
                },
                dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Annulla") } }
            )
        }
        ListItem(
            headlineContent = { Text("Riavvia Launcher") },
            supportingContent = { Text("Riavvio completo dell'app, senza perdere nulla") },
            colors = prefColors(),
            modifier = Modifier.clickable { host.restartLauncher() }
        )
    }
}

@Composable
private fun PageInfo(vm: LauncherViewModel, host: Host) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Group(null) {
        ListItem(
            headlineContent = { Text("MLauncher") },
            supportingContent = { Text("Versione ${vm.currentVersionName}. Launcher in stile HyperOS con colori Material You. Android 12 o successivo.") },
            colors = prefColors()
        )
    }
    Group("Guida") {
        ListItem(
            headlineContent = { Text("Rivedi il benvenuto") },
            supportingContent = { Text("Le schermate della prima installazione: modalità, stile, permessi, backup") },
            colors = prefColors(),
            modifier = Modifier.hl("Rivedi il benvenuto").clickable {
                vm.overlay = Overlay.NONE
                vm.showWelcome = true
            }
        )
    }
    Group("Aggiornamenti") {
        val up = vm.updateInfo
        ListItem(
            headlineContent = {
                Text(if (up != null) "Nuova versione ${up.version} disponibile" else "Cerca aggiornamenti")
            },
            supportingContent = {
                Text(
                    if (up != null) "Tocca per scaricarla e installarla sopra quella attuale"
                    else "Versione installata ${vm.currentVersionName}"
                )
            },
            colors = prefColors(),
            modifier = Modifier.hl("Cerca aggiornamenti").clickable { AppUpdate.open(ctx) }
        )
        SwitchRow(
            "Controlla all'avvio",
            "Al massimo una volta ogni 6 ore",
            vm.settings.autoUpdateCheck
        ) { vm.setSetting("autoUpdateCheck", it) }
    }
    Group("Realizzazione") {
        Credit("Marco", "Idea, richieste e collaudo")
        Credit("Claude (Anthropic)", "Progettazione e sviluppo del codice")
    }
    Group("Ispirazione") {
        Credit("Xiaomi HyperOS", "Aspetto di Home, dock, widget ed effetto di disinstallazione")
        Credit("Samsung One UI", "Widget impilati con rotazione, dock, gesti")
        Credit("Apple iOS", "Modalità modifica con icone che tremolano, forma delle icone, dock")
        Credit("Lawnchair", "Stile delle impostazioni e menu della Home, elenchi di app per categoria (Flowerpot) usati dalle cartelle intelligenti. Licenza Apache 2.0, github.com/lawnchairlauncher/lawnchair")
        Credit("Google Material Design 3", "Linguaggio grafico e colori dinamici (Material You)")
    }
    Group("Librerie e servizi") {
        Credit("Kotlin", "Linguaggio. JetBrains, licenza Apache 2.0")
        Credit("Jetpack Compose e AndroidX", "Interfaccia e componenti. Google, licenza Apache 2.0")
        Credit("Material 3 per Compose", "Componenti grafici. Google, licenza Apache 2.0")
        Credit("Open-Meteo", "Dati meteo gratuiti. open-meteo.com, licenza CC BY 4.0")
        Credit("Android Open Source Project", "Piattaforma, AppWidget, servizi di accessibilità e notifiche")
        Credit("GitHub Actions", "Compilazione automatica e pubblicazione degli APK")
    }
    Group("Note") {
        Credit("Marchi", "HyperOS, Xiaomi, Samsung, One UI, iOS, Apple, Lawnchair e Google sono marchi dei rispettivi proprietari. MLauncher non è affiliato a nessuno di essi.")
    }
}

@Composable
private fun Credit(title: String, text: String) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(text) },
        colors = prefColors()
    )
}

// ---------------------------------------------------------------------
//  Anteprime
// ---------------------------------------------------------------------

@Composable
internal fun ShapePreview(key: String, size: Dp) {
    val cs = MaterialTheme.colorScheme
    val shape = when (key) {
        "circle" -> CircleShape
        "square" -> RoundedCornerShape(size * 0.22f)
        "ios" -> IosShape
        else -> RoundedCornerShape(size * 0.32f)
    }
    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size).clip(shape).background(cs.primary),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Star, null, tint = cs.onPrimary, modifier = Modifier.size(size * 0.55f)) }
    }
}

@Composable
private fun GridPreview(cols: Int, rows: Int) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.size(if (cols > rows) 52.dp else 34.dp, 34.dp).clip(RoundedCornerShape(6.dp)).background(cs.surfaceContainerHighest).padding(3.dp),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        repeat(rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                repeat(cols) { Box(Modifier.size(3.dp).clip(CircleShape).background(cs.primary)) }
            }
        }
    }
}

private val previewWallpaper = Brush.linearGradient(
    listOf(Color(0xFF0B1B3A), Color(0xFF1E88C8), Color(0xFF0A2A52), Color(0xFF16A6E0))
)

@Composable
internal fun DockPreview(style: String, slots: Int, opacity: Int, glass: Boolean, corner: Int) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(width = 64.dp, height = 40.dp).clip(RoundedCornerShape(8.dp)).background(previewWallpaper),
        contentAlignment = Alignment.BottomCenter
    ) {
        val c = (corner * 0.4f).dp
        val bar = style == "bar"
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = if (bar) 0.dp else 4.dp, vertical = if (bar) 0.dp else 4.dp)
                .height(16.dp)
                .dockLook(style, opacity, glass, c),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(minOf(slots, 5)) {
                Box(
                    Modifier.size(8.dp).clip(RoundedCornerShape(3.dp)).background(Color.White)
                        .then(if (style == "tiles") Modifier.dockTile(opacity, glass, 4.dp) else Modifier)
                )
            }
        }
    }
}

/** Anteprima grande: sfondo, qualche icona e la dock con le impostazioni attuali. */
@Composable
private fun DockLivePreview(s: LauncherSettings) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(previewWallpaper)
    ) {
        // onde dello sfondo
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(260f, 120f),
                    radius = 520f
                )
            )
        )
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = 18.dp).fillMaxWidth().padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            repeat(4) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.85f)))
            }
        }
        val h = 66.dp * s.dockSize
        val bar = s.dockStyle == "bar"
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = if (bar) 0.dp else 14.dp, vertical = if (bar) 0.dp else 12.dp)
                .height(h)
                .dockLook(s.dockStyle, s.dockOpacity, s.dockGlass, (s.dockCorner * 0.8f).dp)
        ) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                repeat(s.dockSlots) {
                    Box(Modifier.weight(1f).fillMaxSize().padding(horizontal = 3.dp, vertical = 2.dp)
                        .then(if (s.dockStyle == "tiles") Modifier.dockTile(s.dockOpacity, s.dockGlass, 14.dp) else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.size(40.dp * s.dockSize).clip(RoundedCornerShape(12.dp * s.dockSize))
                                .background(cs.primaryContainer)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EffectPreview(key: String) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.size(width = 44.dp, height = 34.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(18.dp, 26.dp).offset(x = (-8).dp).clip(RoundedCornerShape(4.dp))
                .background(cs.primary.copy(alpha = if (key == "fade") 0.35f else 0.9f))
                .graphicsLayer {
                    when (key) {
                        "cube" -> scaleX = 0.7f
                        "rotate" -> rotationZ = -14f
                        "cascade" -> { scaleX = 0.85f; scaleY = 0.85f }
                    }
                }
        )
        Box(
            Modifier.size(18.dp, 26.dp).offset(x = 8.dp).clip(RoundedCornerShape(4.dp))
                .background(cs.tertiary.copy(alpha = if (key == "fade") 0.6f else 0.9f))
                .graphicsLayer {
                    when (key) {
                        "cube" -> scaleX = 0.7f
                        "rotate" -> rotationZ = 14f
                        "cascade" -> { scaleX = 0.85f; scaleY = 0.85f }
                    }
                }
        )
    }
}

@Composable
internal fun AccentPreview(key: String) {
    val c = accentSwatch(key)
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(
            if (c != null) Brush.linearGradient(listOf(c, c)) else Brush.sweepGradient(
                listOf(Color(0xFFEA4335), Color(0xFFFBBC05), Color(0xFF34A853), Color(0xFF4285F4), Color(0xFFEA4335))
            )
        )
    )
}

@Composable
internal fun ThemePreview(key: String) {
    val shape = CircleShape
    Row(Modifier.size(34.dp).clip(shape)) {
        val light = Color(0xFFF5F5F5)
        val dark = Color(0xFF222222)
        Box(Modifier.weight(1f).fillMaxSize().background(if (key == "dark") dark else light))
        Box(Modifier.weight(1f).fillMaxSize().background(if (key == "light") light else dark))
    }
}

@Composable
internal fun IndicatorPreview(key: String) {
    val cs = MaterialTheme.colorScheme
    val on = cs.primary
    val off = cs.onSurfaceVariant.copy(alpha = 0.4f)
    Row(
        Modifier.width(56.dp).height(34.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (key) {
            "none" -> Text("\u2014", color = off)
            "ios" -> Row(
                Modifier.clip(CircleShape).background(off.copy(alpha = 0.25f)).padding(horizontal = 7.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = on, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(3.dp))
                Text("Cerca", color = on, style = MaterialTheme.typography.labelSmall)
            }
            "number" -> Text("2 / 5", color = on, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
            "progress" -> Box(Modifier.width(44.dp).height(4.dp).clip(CircleShape).background(off)) {
                Box(Modifier.width(18.dp).height(4.dp).clip(CircleShape).background(on))
            }
            else -> repeat(4) { i ->
                val sel = i == 1
                val w = when (key) {
                    "pill" -> if (sel) 16.dp else 6.dp
                    "lines" -> 10.dp
                    "worm" -> if (sel) 14.dp else 6.dp
                    else -> 6.dp
                }
                val h = if (key == "lines") 3.dp else 6.dp
                Box(
                    Modifier.padding(horizontal = 2.dp).size(w, h).clip(CircleShape)
                        .background(if (sel) on else off)
                )
            }
        }
    }
}

@Composable
private fun BadgePreview(key: String) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(cs.surfaceContainerHighest))
        when (key) {
            "dot" -> Box(Modifier.align(Alignment.TopEnd).size(9.dp).clip(CircleShape).background(cs.error))
            "count" -> Box(
                Modifier.align(Alignment.TopEnd).size(14.dp).clip(CircleShape).background(cs.error),
                contentAlignment = Alignment.Center
            ) { Text("3", color = cs.onError, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

// ---------------------------------------------------------------------
//  Componenti
// ---------------------------------------------------------------------

@Composable
private fun prefColors() = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)

/** Gruppo a elementi separati da un filo di spazio, con gli angoli esterni molto arrotondati. */
@Composable
private fun Group(title: String?, content: @Composable () -> Unit) {
    Column {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 32.dp, top = 6.dp, bottom = 8.dp)
            )
        }
        Column(
            Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(28.dp)),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) { content() }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) ({ Text(subtitle) }) else null,
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = prefColors(),
        modifier = Modifier.hl(title).clickable { onChange(!checked) }
    )
}

@Composable
private fun SliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    preview: (@Composable () -> Unit)? = null,
    onChange: (Float) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .hl(title)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(valueLabel, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            if (preview != null) {
                Spacer(Modifier.width(12.dp))
                preview()
            }
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    options: List<Pair<String, String>>,
    current: String,
    preview: (@Composable (String) -> Unit)? = null,
    onSelect: (String) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(options.firstOrNull { it.first == current }?.second ?: "") },
        trailingContent = if (preview != null) ({ preview(current) }) else null,
        colors = prefColors(),
        modifier = Modifier.hl(title).clickable { open = true }
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    options.forEach { (k, l) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(k)
                                    open = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = k == current, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            if (preview != null) {
                                preview(k)
                                Spacer(Modifier.width(12.dp))
                            }
                            Text(l)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { open = false }) { Text("Annulla") } }
        )
    }
}
