package com.marco.mlauncher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: LauncherViewModel, host: Host) {
    val s = vm.settings
    val cs = MaterialTheme.colorScheme
    Scaffold(
        containerColor = cs.surface,
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni MLauncher") },
                navigationIcon = {
                    IconButton(onClick = { vm.overlay = Overlay.NONE }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface)
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!vm.isDefaultHome) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cs.primaryContainer),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
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
            }
            item {
                Group("Launcher") {
                    ListItem(
                        headlineContent = { Text("Imposta come Home") },
                        supportingContent = {
                            Text(if (vm.isDefaultHome) "MLauncher è la Home predefinita" else "Tocca per scegliere MLauncher come Home")
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { host.requestHomeRole() }
                    )
                }
            }
            item {
                Group("Schermata Home") {
                    ChoiceRow(
                        "Modalità Home",
                        listOf("drawer" to "Home e cassetto app", "all" to "Tutte le app in Home"),
                        s.homeMode
                    ) { vm.setSetting("homeMode", it) }
                    Divider()
                    SliderRow("Colonne", "${s.cols}", s.cols.toFloat(), 4f..6f, 1) {
                        val v = it.roundToInt()
                        if (v != s.cols) vm.setSetting("cols", v)
                    }
                    SliderRow("Righe", "${s.rows}", s.rows.toFloat(), 5f..8f, 2) {
                        val v = it.roundToInt()
                        if (v != s.rows) vm.setSetting("rows", v)
                    }
                    SliderRow("Dimensione icone", "${(s.iconScale * 100).roundToInt()}%", s.iconScale, 0.8f..1.2f, 0) {
                        vm.setSetting("iconScale", (it * 20).roundToInt() / 20f)
                    }
                    Divider()
                    SliderRow("Icone nella dock", "${s.dockSlots}", s.dockSlots.toFloat(), 4f..6f, 1) {
                        val v = it.roundToInt()
                        if (v != s.dockSlots) vm.setSetting("dockSlots", v)
                    }
                    ChoiceRow(
                        "Stile della dock",
                        listOf("ios" to "iOS", "samsung" to "Samsung", "hyperos" to "HyperOS", "none" to "Nessuno"),
                        s.dockStyle
                    ) { vm.setSetting("dockStyle", it) }
                    Divider()
                    SwitchRow("Mostra i nomi delle app", null, s.showLabels) { vm.setSetting("showLabels", it) }
                    SwitchRow(
                        "Icone Material You",
                        "Icone monocromatiche con i colori dello sfondo, dove l'app le supporta",
                        s.themedIcons
                    ) { vm.setSetting("themedIcons", it) }
                    if (s.homeMode == "drawer") {
                        SwitchRow(
                            "Aggiungi le nuove app in Home",
                            "Altrimenti restano solo nel cassetto",
                            s.autoAddNew
                        ) { vm.setSetting("autoAddNew", it) }
                    }
                }
            }
            item {
                Group("Effetti e animazioni") {
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
            item {
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
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
            item {
                Group("Ricerca e widget") {
                    ChoiceRow(
                        "Barra di ricerca",
                        listOf("home" to "In alto nella Home", "dock" to "Sopra la dock", "none" to "Nascosta"),
                        s.searchBar
                    ) { vm.setSetting("searchBar", it) }
                    SwitchRow(
                        "Rotazione automatica degli stack",
                        "I widget impilati scorrono da soli",
                        s.stackAutoRotate
                    ) { vm.setSetting("stackAutoRotate", it) }
                }
            }
            item {
                Group("Icone e notifiche") {
                    ChoiceRow(
                        "Pacchetto icone",
                        listOf("" to "Predefinito") + vm.iconPacks.map { it.first to it.second },
                        s.iconPack
                    ) { vm.setSetting("iconPack", it) }
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
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { host.openListenerSettings() }
                        )
                    }
                    var hiddenOpen by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = { Text("App nascoste") },
                        supportingContent = { Text("${vm.hidden.size} nascoste") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
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
            item {
                Group("Blocco schermo") {
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
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { host.openAccessibilitySettings() }
                        )
                    }
                }
            }
            item {
                Group("Backup") {
                    ListItem(
                        headlineContent = { Text("Esporta layout e impostazioni") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { host.exportBackup() }
                    )
                    ListItem(
                        headlineContent = { Text("Ripristina da file") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { host.importBackup() }
                    )
                }
            }
            item {
                Group("Aspetto") {
                    ChoiceRow(
                        "Tema",
                        listOf("auto" to "Automatico", "light" to "Chiaro", "dark" to "Scuro"),
                        s.theme
                    ) { vm.setSetting("theme", it) }
                }
            }
            item {
                Group("Manutenzione") {
                    var confirmReset by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = { Text("Riordina Home") },
                        supportingContent = { Text("Widget in alto, poche app e dock. Le altre restano nel cassetto") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { vm.restartHome() }
                    )
                }
            }
            item {
                Group("Info") {
                    ListItem(
                        headlineContent = { Text("MLauncher") },
                        supportingContent = { Text("Colori dinamici Material You dallo sfondo. Android 12 o successivo.") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth()) { content() }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) ({ Text(subtitle) }) else null,
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(valueLabel, color = MaterialTheme.colorScheme.primary)
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
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
