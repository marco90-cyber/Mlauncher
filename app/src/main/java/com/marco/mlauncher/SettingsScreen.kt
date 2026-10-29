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
    "color" to "Per colore dell'icona",
    "cat_folders" to "Cartelle per categoria",
    "letter_folders" to "Cartelle per lettera"
)
private val themeNames = mapOf("auto" to "Automatico", "light" to "Chiaro", "dark" to "Scuro")
private val dockNames = mapOf("ios" to "iOS", "samsung" to "Samsung", "hyperos" to "HyperOS", "none" to "Nessuno")
private val searchNames = mapOf("home" to "in alto nella Home", "dock" to "sopra la dock", "none" to "nascosta")

private fun pageTitle(key: String?) = when (key) {
    "home" -> "Schermata Home"
    "dock" -> "Dock"
    "icons" -> "Icone e notifiche"
    "folders" -> "Cartelle"
    "widgets" -> "Widget e ricerca"
    "gestures" -> "Gesti e feedback"
    "effects" -> "Effetti e animazioni"
    "look" -> "Aspetto"
    "backup" -> "Backup e manutenzione"
    "info" -> "Informazioni"
    else -> "Impostazioni"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: LauncherViewModel, host: Host) {
    val cs = MaterialTheme.colorScheme
    val page = vm.settingsPage
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
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
                    "widgets" -> PageWidgets(vm)
                    "gestures" -> PageGestures(vm, host)
                    "effects" -> PageEffects(vm)
                    "look" -> PageLook(vm)
                    "backup" -> PageBackup(vm, host)
                    else -> PageInfo()
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun Dashboard(vm: LauncherViewModel, host: Host) {
    val s = vm.settings
    val cs = MaterialTheme.colorScheme
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
    val cats = listOf(
        Cat("home", "Schermata Home", "${s.cols}\u00d7${s.rows} \u00b7 " + (if (s.homeMode == "drawer") "con cassetto" else "tutte le app in Home"), Icons.Default.Home),
        Cat("dock", "Dock", "${s.dockSlots} icone \u00b7 stile ${dockNames[s.dockStyle] ?: ""}", Icons.Default.Menu),
        Cat("icons", "Icone e notifiche", "Forma: ${shapeNames[s.iconShape] ?: ""}", Icons.Default.Star),
        Cat("folders", "Cartelle", "Anteprima: ${folderNames[s.folderPreview] ?: ""}", Icons.Default.List),
        Cat("widgets", "Widget e ricerca", "Ricerca ${searchNames[s.searchBar] ?: ""}", Icons.Default.Search),
        Cat("gestures", "Gesti e feedback", if (s.haptics) "Feedback tattile attivo" else "Feedback tattile spento", Icons.Default.Lock),
        Cat("effects", "Effetti e animazioni", "Cambio pagina: ${effectNames[s.pageEffect] ?: ""}", Icons.Default.Favorite),
        Cat("look", "Aspetto", "Tema ${(themeNames[s.theme] ?: "").lowercase()}", Icons.Default.Create),
        Cat("backup", "Backup e manutenzione", "Esporta, ripristina, riordina", Icons.Default.Build),
        Cat("info", "Informazioni", "MLauncher", Icons.Default.Info)
    )
    Group(null) {
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

@Composable
private fun PageHome(vm: LauncherViewModel) {
    val s = vm.settings
    Group("Griglia") {
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
            ChoiceRow("Come disporre le app", orderOptions, s.homeOrder) { vm.setSetting("homeOrder", it) }
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
        SliderRow("Colonne", "${s.cols}", s.cols.toFloat(), 4f..6f, 1, preview = { GridPreview(s.cols, s.rows) }) {
            val v = it.roundToInt()
            if (v != s.cols) vm.setSetting("cols", v)
        }
        SliderRow("Righe", "${s.rows}", s.rows.toFloat(), 5f..8f, 2, preview = { GridPreview(s.cols, s.rows) }) {
            val v = it.roundToInt()
            if (v != s.rows) vm.setSetting("rows", v)
        }
    }
    Group("Icone in Home") {
        SliderRow("Dimensione icone", "${(s.iconScale * 100).roundToInt()}%", s.iconScale, 0.8f..1.2f, 0, preview = { ShapePreview(s.iconShape, 30.dp * s.iconScale) }) {
            vm.setSetting("iconScale", (it * 20).roundToInt() / 20f)
        }
        SwitchRow("Mostra i nomi delle app", null, s.showLabels) { vm.setSetting("showLabels", it) }
        if (s.homeMode == "drawer") {
            SwitchRow(
                "Aggiungi le nuove app in Home",
                "Altrimenti restano solo nel cassetto",
                s.autoAddNew
            ) { vm.setSetting("autoAddNew", it) }
        }
    }
}

@Composable
private fun PageDock(vm: LauncherViewModel) {
    val s = vm.settings
    Group(null) {
        SliderRow("Icone nella dock", "${s.dockSlots}", s.dockSlots.toFloat(), 4f..6f, 1, preview = { DockPreview(s.dockStyle, s.dockSlots) }) {
            val v = it.roundToInt()
            if (v != s.dockSlots) vm.setSetting("dockSlots", v)
        }
        ChoiceRow(
            "Stile della dock",
            listOf("ios" to "iOS", "samsung" to "Samsung", "hyperos" to "HyperOS", "none" to "Nessuno"),
            s.dockStyle,
            preview = { DockPreview(it, s.dockSlots) }
        ) { vm.setSetting("dockStyle", it) }
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
private fun PageFolders(vm: LauncherViewModel) {
    val s = vm.settings
    Group("Aspetto") {
        ChoiceRow(
            "Anteprima della cartella",
            listOf("grid" to "Griglia 3\u00d73", "grid2" to "Griglia 2\u00d72 (icone grandi)", "stack" to "Ventaglio"),
            s.folderPreview,
            preview = { FolderPreview(it, s.folderBg) }
        ) { vm.setSetting("folderPreview", it) }
        ChoiceRow(
            "Sfondo della cartella",
            listOf("tonal" to "Tonale", "primary" to "Colore principale", "dark" to "Scuro trasparente", "glass" to "Vetro chiaro"),
            s.folderBg,
            preview = { FolderPreview(s.folderPreview, it) }
        ) { vm.setSetting("folderBg", it) }
    }
    Group("Cartella aperta") {
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

@Composable
private fun FolderPreview(style: String, bgKey: String) {
    val cs = MaterialTheme.colorScheme
    val bg = when (bgKey) {
        "primary" -> cs.primaryContainer
        "dark" -> Color.Black.copy(alpha = 0.45f)
        "glass" -> Color.White.copy(alpha = 0.5f)
        else -> cs.surfaceContainerHighest
    }
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(bg).padding(5.dp)) {
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
private fun PageWidgets(vm: LauncherViewModel) {
    val s = vm.settings
    Group("Ricerca") {
        ChoiceRow(
            "Barra di ricerca",
            listOf("home" to "In alto nella Home", "dock" to "Sopra la dock", "none" to "Nascosta"),
            s.searchBar
        ) { vm.setSetting("searchBar", it) }
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
    Group("Feedback") {
        SwitchRow(
            "Feedback tattile",
            "Interruttore generale",
            s.haptics
        ) { vm.setSetting("haptics", it) }
    }
    if (s.haptics) Group("Dove vuoi il feedback") {
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
            "Scorrimento e selezione",
            "Cambio pagina, ridimensionamento, gesti su e giù",
            s.hapTick
        ) { vm.setSetting("hapTick", it) }
    }
}

@Composable
private fun PageEffects(vm: LauncherViewModel) {
    val s = vm.settings
    Group(null) {
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
private fun PageLook(vm: LauncherViewModel) {
    val s = vm.settings
    Group(null) {
        ChoiceRow(
            "Tema",
            listOf("auto" to "Automatico", "light" to "Chiaro", "dark" to "Scuro"),
            s.theme,
            preview = { ThemePreview(it) }
        ) { vm.setSetting("theme", it) }
        ChoiceRow(
            "Indicatore di pagina",
            listOf(
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
private fun PageInfo() {
    Group(null) {
        ListItem(
            headlineContent = { Text("MLauncher") },
            supportingContent = { Text("Launcher in stile HyperOS con colori Material You. Android 12 o successivo.") },
            colors = prefColors()
        )
    }
    Group("Realizzazione") {
        Credit("Marco", "Idea, richieste e collaudo")
        Credit("Claude (Anthropic)", "Progettazione e sviluppo del codice")
    }
    Group("Ispirazione") {
        Credit("Xiaomi HyperOS", "Aspetto di Home, dock, widget ed effetto di disinstallazione")
        Credit("Samsung One UI", "Widget impilati con rotazione, dock, gesti")
        Credit("Apple iOS", "Modalità modifica con icone che tremolano, forma delle icone, dock")
        Credit("Lawnchair", "Stile delle impostazioni e menu della Home. Licenza Apache 2.0, github.com/lawnchairlauncher/lawnchair")
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
private fun ShapePreview(key: String, size: Dp) {
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
        Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)).background(cs.surfaceContainerHighest).padding(3.dp),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        repeat(rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                repeat(cols) { Box(Modifier.size(3.dp).clip(CircleShape).background(cs.primary)) }
            }
        }
    }
}

@Composable
private fun DockPreview(style: String, slots: Int) {
    val cs = MaterialTheme.colorScheme
    val bg = cs.surfaceContainerHighest
    Box(Modifier.width(56.dp).height(34.dp), contentAlignment = Alignment.BottomCenter) {
        val shape = when (style) {
            "ios" -> RoundedCornerShape(10.dp)
            "samsung" -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
            "hyperos" -> RoundedCornerShape(50)
            else -> RoundedCornerShape(0.dp)
        }
        val m = if (style == "samsung") 0.dp else 3.dp
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = m, vertical = if (style == "samsung") 0.dp else 3.dp)
                .height(if (style == "ios") 18.dp else 16.dp)
                .clip(shape)
                .background(if (style == "none") Color.Transparent else bg),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(slots) { Box(Modifier.size(5.dp).clip(RoundedCornerShape(2.dp)).background(cs.primary)) }
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
private fun ThemePreview(key: String) {
    val shape = CircleShape
    Row(Modifier.size(34.dp).clip(shape)) {
        val light = Color(0xFFF5F5F5)
        val dark = Color(0xFF222222)
        Box(Modifier.weight(1f).fillMaxSize().background(if (key == "dark") dark else light))
        Box(Modifier.weight(1f).fillMaxSize().background(if (key == "light") light else dark))
    }
}

@Composable
private fun IndicatorPreview(key: String) {
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
        modifier = Modifier.clickable { onChange(!checked) }
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
        modifier = Modifier.clickable { open = true }
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
