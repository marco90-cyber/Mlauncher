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
private val themeNames = mapOf("auto" to "Automatico", "light" to "Chiaro", "dark" to "Scuro")
private val dockNames = mapOf("ios" to "iOS", "samsung" to "Samsung", "hyperos" to "HyperOS", "none" to "Nessuno")
private val searchNames = mapOf("home" to "in alto nella Home", "dock" to "sopra la dock", "none" to "nascosta")

private fun pageTitle(key: String?) = when (key) {
    "home" -> "Schermata Home"
    "dock" -> "Dock"
    "icons" -> "Icone e notifiche"
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
                if (targetState != null) {
                    (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
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
        ChoiceRow(
            "Modalità Home",
            listOf("drawer" to "Home e cassetto app", "all" to "Tutte le app in Home"),
            s.homeMode
        ) { vm.setSetting("homeMode", it) }
        SliderRow("Colonne", "${s.cols}", s.cols.toFloat(), 4f..6f, 1) {
            val v = it.roundToInt()
            if (v != s.cols) vm.setSetting("cols", v)
        }
        SliderRow("Righe", "${s.rows}", s.rows.toFloat(), 5f..8f, 2) {
            val v = it.roundToInt()
            if (v != s.rows) vm.setSetting("rows", v)
        }
    }
    Group("Icone in Home") {
        SliderRow("Dimensione icone", "${(s.iconScale * 100).roundToInt()}%", s.iconScale, 0.8f..1.2f, 0) {
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
        SliderRow("Icone nella dock", "${s.dockSlots}", s.dockSlots.toFloat(), 4f..6f, 1) {
            val v = it.roundToInt()
            if (v != s.dockSlots) vm.setSetting("dockSlots", v)
        }
        ChoiceRow(
            "Stile della dock",
            listOf("ios" to "iOS", "samsung" to "Samsung", "hyperos" to "HyperOS", "none" to "Nessuno"),
            s.dockStyle
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
            s.iconShape
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
            s.badges
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
            "Vibrazione leggera nelle azioni principali",
            s.haptics
        ) { vm.setSetting("haptics", it) }
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
            s.pageEffect
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
            s.theme
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
            s.pageIndicator
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
            headlineContent = { Text("Riordina Home") },
            supportingContent = { Text("Widget in alto, poche app e dock. Le altre restano nel cassetto") },
            colors = prefColors(),
            modifier = Modifier.clickable { confirmReset = true }
        )
        if (confirmReset) {
            AlertDialog(
                onDismissRequest = { confirmReset = false },
                title = { Text("Riordinare la Home?") },
                text = { Text("La disposizione attuale di icone, cartelle e widget verrà sostituita.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmReset = false
                        vm.resetHome()
                        vm.overlay = Overlay.NONE
                    }) { Text("Riordina") }
                },
                dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Annulla") } }
            )
        }
        ListItem(
            headlineContent = { Text("Riavvia Home") },
            supportingContent = { Text("Ricarica app, icone, meteo e widget") },
            colors = prefColors(),
            modifier = Modifier.clickable { vm.restartHome() }
        )
    }
}

@Composable
private fun PageInfo() {
    Group(null) {
        ListItem(
            headlineContent = { Text("MLauncher") },
            supportingContent = { Text("Colori dinamici Material You dallo sfondo. Android 12 o successivo.") },
            colors = prefColors()
        )
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
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    options: List<Pair<String, String>>,
    current: String,
    onSelect: (String) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(options.firstOrNull { it.first == current }?.second ?: "") },
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
