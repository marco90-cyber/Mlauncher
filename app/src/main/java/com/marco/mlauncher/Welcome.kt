package com.marco.mlauncher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/*
 * Benvenuto in stile Nova Launcher: un saluto, il backup, poi domande "scegline uno" con due
 * grandi anteprime affiancate (si tocca quella che piace e si passa alla domanda dopo),
 * infine "Tutto pronto" con Home predefinita e permessi facoltativi.
 */

private enum class Step { HELLO, BACKUP, THEME, MODE, FOLDERS, DOCK, DONE }

@Composable
fun WelcomeScreen(vm: LauncherViewModel, host: Host) {
    val cs = MaterialTheme.colorScheme
    var step by remember { mutableStateOf(Step.HELLO) }
    var forward by remember { mutableStateOf(true) }
    // la domanda sulle cartelle ha senso solo con "tutte le app in Home"
    val steps = Step.entries.filter { it != Step.FOLDERS || vm.settings.homeMode == "all" }
    fun go(to: Step) {
        forward = to.ordinal > step.ordinal
        step = to
    }
    fun next() = steps.getOrNull(steps.indexOf(step) + 1)?.let { go(it) } ?: vm.finishWelcome()
    fun back() = steps.getOrNull(steps.indexOf(step) - 1)?.let { go(it) }
    BackHandler { if (step == Step.HELLO) vm.finishWelcome() else back() }

    Surface(color = cs.surface, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // barra in alto: indietro e salta
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (step != Step.HELLO) TextButton(onClick = { back() }) { Text("Indietro") }
                Spacer(Modifier.weight(1f))
                if (step != Step.DONE) TextButton(onClick = { vm.finishWelcome() }) { Text("Salta") }
            }
            AnimatedContent(
                targetState = step,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    val d = if (forward) 1 else -1
                    (slideInHorizontally(tween(Motion.SHEET, easing = Motion.EmphasizedDecelerate)) { d * it / 3 } + fadeIn(tween(Motion.SHEET))) togetherWith
                        (slideOutHorizontally(tween(Motion.FADE, easing = Motion.EmphasizedAccelerate)) { -d * it / 3 } + fadeOut(tween(Motion.FADE)))
                },
                label = "welcome"
            ) { st ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (st) {
                        Step.HELLO -> Hello(onStart = { next() }, onBackup = { go(Step.BACKUP) })
                        Step.BACKUP -> Backup(host, onFresh = { next() })
                        Step.THEME -> PickTheme(vm) { next() }
                        Step.MODE -> PickMode(vm) { next() }
                        Step.FOLDERS -> PickFolders(vm) { next() }
                        Step.DOCK -> PickDock(vm) { next() }
                        Step.DONE -> Done(vm, host)
                    }
                }
            }
            // avanzamento e pulsante principale
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f)) {
                    steps.forEach { s ->
                        val on = s == step
                        val w by animateDpAsState(if (on) 20.dp else 7.dp, label = "dot")
                        Box(
                            Modifier.padding(end = 6.dp).size(w, 7.dp).clip(CircleShape)
                                .background(if (on) cs.primary else cs.outlineVariant)
                        )
                    }
                }
                when (step) {
                    Step.HELLO -> {}
                    Step.DONE -> Button(onClick = { vm.finishWelcome() }) { Text("Vai alla Home") }
                    else -> Button(onClick = { next() }) { Text("Avanti") }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
//  Pagine
// ---------------------------------------------------------------------

@Composable
private fun Headline(big: String, small: String) {
    Spacer(Modifier.height(12.dp))
    Text(big, fontSize = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 40.sp)
    Spacer(Modifier.height(10.dp))
    Text(small, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(28.dp))
}

@Composable
private fun Logo(size: Dp = 120.dp) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f))
            .background(Brush.linearGradient(listOf(cs.primary, cs.tertiary))),
        contentAlignment = Alignment.Center
    ) {
        Text("M", color = cs.onPrimary, fontSize = (size.value * 0.55f).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Hello(onStart: () -> Unit, onBackup: () -> Unit) {
    Spacer(Modifier.height(48.dp))
    Logo()
    Spacer(Modifier.height(36.dp))
    Headline("Ciao!", "Benvenuto in MLauncher. Rispondi a qualche domanda e la Home sarà subito come piace a te.")
    Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Iniziamo", fontSize = 16.sp) }
    Spacer(Modifier.height(10.dp))
    TextButton(onClick = onBackup) { Text("Ho già un backup di MLauncher") }
}

@Composable
private fun Backup(host: Host, onFresh: () -> Unit) {
    Spacer(Modifier.height(24.dp))
    Headline("Scegli un backup", "Riprendi Home, cartelle e impostazioni da un file salvato con MLauncher, oppure parti da zero.")
    BigOption("Ripristina da file", "Scegli il file .json salvato da Impostazioni → Backup") { host.importBackup() }
    Spacer(Modifier.height(12.dp))
    BigOption("Inizia da zero", "Continua con le domande sulla Home") { onFresh() }
}

@Composable
private fun BigOption(title: String, text: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerHigh)
            .clickable(onClick = onClick).padding(20.dp)
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        Text(text, color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Due anteprime affiancate: si tocca quella preferita, la scelta si applica subito
 * e dopo un attimo si passa alla domanda successiva.
 */
@Composable
private fun AB(
    leftSelected: Boolean,
    rightSelected: Boolean,
    leftLabel: String,
    rightLabel: String,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onPicked: () -> Unit,
    left: @Composable BoxScope.() -> Unit,
    right: @Composable BoxScope.() -> Unit
) {
    var picked by remember { mutableIntStateOf(0) }
    LaunchedEffect(picked) {
        if (picked > 0) {
            delay(420)
            onPicked()
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        PickCard(leftSelected, leftLabel, Modifier.weight(1f), { onLeft(); picked++ }, left)
        PickCard(rightSelected, rightLabel, Modifier.weight(1f), { onRight(); picked++ }, right)
    }
}

@Composable
private fun PickCard(selected: Boolean, label: String, modifier: Modifier, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bw by animateDpAsState(if (selected) 3.dp else 1.dp, label = "bw")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.52f)
                .clip(RoundedCornerShape(26.dp))
                .border(bw, if (selected) cs.primary else cs.outlineVariant, RoundedCornerShape(26.dp))
                .clickable(onClick = onClick)
                .padding(6.dp)
        ) {
            content()
            if (selected) Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp).clip(CircleShape).background(cs.primary),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Check, contentDescription = null, tint = cs.onPrimary, modifier = Modifier.size(18.dp)) }
        }
        Text(
            label,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) cs.primary else cs.onSurface,
            modifier = Modifier.padding(top = 10.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PickTheme(vm: LauncherViewModel, onPicked: () -> Unit) {
    val s = vm.settings
    Headline("Scegline uno", "Chiaro o scuro?")
    AB(
        s.theme == "light", s.theme == "dark", "Chiaro", "Scuro",
        { vm.setSetting("theme", "light") }, { vm.setSetting("theme", "dark") }, onPicked,
        { MiniPhone(dark = false, allApps = s.homeMode == "all", dock = s.dockStyle, folders = false) },
        { MiniPhone(dark = true, allApps = s.homeMode == "all", dock = s.dockStyle, folders = false) }
    )
    Spacer(Modifier.height(16.dp))
    FilterChip(
        selected = s.theme == "auto",
        onClick = { vm.setSetting("theme", "auto") },
        label = { Text("Segui il telefono (automatico)") }
    )
}

@Composable
private fun PickMode(vm: LauncherViewModel, onPicked: () -> Unit) {
    val s = vm.settings
    val dark = isDarkPreview(s.theme)
    Headline("Scegline un altro", "Dove vuoi le tue app?")
    AB(
        s.homeMode == "drawer", s.homeMode == "all", "Home e cassetto\n(scorri in su)", "Tutte le app\nin Home",
        { if (s.homeMode != "drawer") vm.setSetting("homeMode", "drawer") },
        { if (s.homeMode != "all") vm.setSetting("homeMode", "all") },
        onPicked,
        { MiniPhone(dark, allApps = false, dock = s.dockStyle, folders = false) },
        { MiniPhone(dark, allApps = true, dock = s.dockStyle, folders = false) }
    )
}

@Composable
private fun PickFolders(vm: LauncherViewModel, onPicked: () -> Unit) {
    val s = vm.settings
    val dark = isDarkPreview(s.theme)
    Headline("Ancora uno", "Vuoi che le app si raggruppino da sole in cartelle?")
    AB(
        !s.autoFolders, s.autoFolders, "Icone sciolte", "Cartelle\nintelligenti",
        { vm.setSetting("autoFolders", false) }, { vm.setSetting("autoFolders", true) }, onPicked,
        { MiniPhone(dark, allApps = true, dock = s.dockStyle, folders = false) },
        { MiniPhone(dark, allApps = true, dock = s.dockStyle, folders = true) }
    )
    if (s.autoFolders) {
        Spacer(Modifier.height(16.dp))
        Text("Raggruppa per", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("genre_plus" to "Genere", "color" to "Colore", "letter" to "Lettera").forEach { (k, l) ->
                FilterChip(selected = s.smartMode == k, onClick = { vm.setSetting("smartMode", k) }, label = { Text(l) })
            }
        }
    }
}

@Composable
private fun PickDock(vm: LauncherViewModel, onPicked: () -> Unit) {
    val s = vm.settings
    val dark = isDarkPreview(s.theme)
    Headline("Ultimo!", "Che dock preferisci?")
    AB(
        s.dockStyle == "glass", s.dockStyle == "pill", "Vetro", "Pillola piena",
        { vm.setSetting("dockStyle", "glass"); vm.setSetting("dockGlass", true) },
        { vm.setSetting("dockStyle", "pill") },
        onPicked,
        { MiniPhone(dark, allApps = s.homeMode == "all", dock = "glass", folders = s.autoFolders && s.homeMode == "all") },
        { MiniPhone(dark, allApps = s.homeMode == "all", dock = "pill", folders = s.autoFolders && s.homeMode == "all") }
    )
    Spacer(Modifier.height(10.dp))
    Text(
        "Altri stili (scuro, colorato, barra, tessere…) in Impostazioni → Dock",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun Done(vm: LauncherViewModel, host: Host) {
    var city by remember { mutableStateOf(false) }
    if (city) CityPickerDialog(vm, host) { city = false }
    Spacer(Modifier.height(24.dp))
    Logo(88.dp)
    Spacer(Modifier.height(20.dp))
    Headline("Tutto pronto!", "Ultimi ritocchi, tutti facoltativi. Li trovi anche nelle impostazioni.")
    SetupRow(
        "Home predefinita", "Usa MLauncher con il tasto Home e i gesti",
        vm.isDefaultHome, "Imposta"
    ) { host.requestHomeRole() }
    SetupRow(
        "Meteo", "Posizione approssimativa o una città a scelta",
        vm.weatherReady, "Scegli"
    ) { city = true }
    SetupRow(
        "Puntini delle notifiche", "Quante notifiche ha ogni app, sulla sua icona",
        vm.listenerEnabled, "Attiva"
    ) { host.openListenerSettings() }
}

@Composable
private fun SetupRow(title: String, why: String, done: Boolean, action: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(why, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        if (done) Icon(Icons.Default.Check, contentDescription = "Fatto", tint = cs.primary)
        else OutlinedButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun isDarkPreview(theme: String): Boolean = when (theme) {
    "dark" -> true
    "light" -> false
    else -> androidx.compose.foundation.isSystemInDarkTheme()
}

// ---------------------------------------------------------------------
//  Telefono in miniatura per le anteprime
// ---------------------------------------------------------------------

private val iconColors = listOf(
    Color(0xFFEA4335), Color(0xFF34A853), Color(0xFF4285F4), Color(0xFFFBBC05),
    Color(0xFF9C27B0), Color(0xFF00ACC1), Color(0xFFFF7043), Color(0xFF5C6BC0),
    Color(0xFF26A69A), Color(0xFFEC407A), Color(0xFF8D6E63), Color(0xFF7CB342)
)

@Composable
private fun BoxScope.MiniPhone(dark: Boolean, allApps: Boolean, dock: String, folders: Boolean) {
    val cs = MaterialTheme.colorScheme
    val wall = if (dark) Brush.verticalGradient(listOf(Color(0xFF0E1A2B), Color(0xFF1B2F4A), Color(0xFF0A1220)))
    else Brush.verticalGradient(listOf(Color(0xFFDDE8F7), Color(0xFFB9D0EE), Color(0xFFE9EEF6)))
    val widgetBg = if (dark) Color(0xFF2A3346) else Color(0xFFF7F9FD)
    val widgetFg = if (dark) Color.White else Color(0xFF1B1F27)
    Column(
        Modifier.matchParentSize().clip(RoundedCornerShape(20.dp)).background(wall).padding(8.dp)
    ) {
        // barra di stato
        Row(Modifier.fillMaxWidth()) {
            Text("10:08", fontSize = 7.sp, color = widgetFg.copy(alpha = 0.8f))
        }
        Spacer(Modifier.height(6.dp))
        // ricerca e widget
        Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(widgetBg.copy(alpha = 0.85f)))
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(
                Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(8.dp)).background(cs.primaryContainer),
                contentAlignment = Alignment.Center
            ) { Text("10:08", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = cs.onPrimaryContainer) }
            Box(Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(8.dp)).background(widgetBg)) {
                Box(Modifier.align(Alignment.Center).size(12.dp).clip(CircleShape).background(Color(0xFFFFC107)))
            }
        }
        Spacer(Modifier.height(8.dp))
        // icone
        val rows = if (allApps) 4 else 1
        if (!allApps) Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(rows) { r ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    repeat(4) { c ->
                        val i = r * 4 + c
                        if (folders && (i == 0 || i == 5 || i == 10)) MiniFolder(widgetBg, i)
                        else Box(Modifier.size(13.dp).clip(RoundedCornerShape(4.dp)).background(iconColors[i % iconColors.size]))
                    }
                }
            }
        }
        if (allApps) Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(6.dp))
        // linguetta del cassetto (solo con il cassetto)
        if (!allApps) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(16.dp, 3.dp).clip(CircleShape).background(widgetFg.copy(alpha = 0.6f)))
            Spacer(Modifier.height(5.dp))
        } else {
            Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(3) { Box(Modifier.size(if (it == 0) 8.dp else 3.dp, 3.dp).clip(CircleShape).background(widgetFg.copy(alpha = if (it == 0) 0.9f else 0.4f))) }
            }
            Spacer(Modifier.height(5.dp))
        }
        // dock
        val dockMod = when (dock) {
            "pill" -> Modifier.clip(RoundedCornerShape(12.dp)).background(if (dark) Color(0xFF2B3140) else Color(0xFFF3F5F9))
            "dark" -> Modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.4f))
            "tinted" -> Modifier.clip(RoundedCornerShape(12.dp)).background(cs.primaryContainer.copy(alpha = 0.6f))
            "none", "tiles" -> Modifier
            else -> Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.28f))
                .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
        }
        Row(
            Modifier.fillMaxWidth().then(dockMod).padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            repeat(4) { Box(Modifier.size(13.dp).clip(CircleShape).background(iconColors[(it * 3 + 1) % iconColors.size])) }
        }
    }
}

@Composable
private fun MiniFolder(bg: Color, seed: Int) {
    Column(
        Modifier.size(13.dp).clip(RoundedCornerShape(4.dp)).background(bg.copy(alpha = 0.7f)).padding(2.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        repeat(2) { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                repeat(2) { c -> Box(Modifier.size(3.5.dp).clip(CircleShape).background(iconColors[(seed + r * 2 + c) % iconColors.size])) }
            }
        }
    }
}
