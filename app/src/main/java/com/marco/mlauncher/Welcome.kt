package com.marco.mlauncher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private const val PAGES = 6

/** Benvenuto al primo avvio: Home predefinita, modalità, aspetto, permessi, backup. */
@Composable
fun WelcomeScreen(vm: LauncherViewModel, host: Host) {
    val cs = MaterialTheme.colorScheme
    val pager = rememberPagerState { PAGES }
    val scope = rememberCoroutineScope()
    fun go(p: Int) = scope.launch { pager.animateScrollToPage(p.coerceIn(0, PAGES - 1)) }
    BackHandler {
        if (pager.currentPage > 0) go(pager.currentPage - 1) else vm.finishWelcome()
    }
    Surface(color = cs.surface, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${pager.currentPage + 1} di $PAGES",
                    color = cs.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 12.dp).weight(1f)
                )
                if (pager.currentPage < PAGES - 1) TextButton(onClick = { vm.finishWelcome() }) { Text("Salta") }
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { p ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (p) {
                        0 -> PageHello(vm, host)
                        1 -> PageMode(vm)
                        2 -> PageLookW(vm)
                        3 -> PagePerms(vm, host)
                        4 -> PageRestore(host) { go(5) }
                        else -> PageDone()
                    }
                }
            }
            // puntini e pulsanti
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(96.dp)) {
                    if (pager.currentPage > 0) TextButton(onClick = { go(pager.currentPage - 1) }) { Text("Indietro") }
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                    repeat(PAGES) { i ->
                        val w by animateDpAsState(if (i == pager.currentPage) 18.dp else 7.dp, label = "dot")
                        Box(
                            Modifier.padding(horizontal = 3.dp).size(w, 7.dp).clip(CircleShape)
                                .background(if (i == pager.currentPage) cs.primary else cs.outlineVariant)
                        )
                    }
                }
                Box(Modifier.width(96.dp), contentAlignment = Alignment.CenterEnd) {
                    if (pager.currentPage < PAGES - 1) Button(onClick = { go(pager.currentPage + 1) }) { Text("Avanti") }
                    else Button(onClick = { vm.finishWelcome() }) { Text("Inizia") }
                }
            }
        }
    }
}

@Composable
private fun Title(t: String, sub: String) {
    Text(t, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun Logo() {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(112.dp).clip(RoundedCornerShape(34.dp))
            .background(Brush.linearGradient(listOf(cs.primary, cs.tertiary))),
        contentAlignment = Alignment.Center
    ) {
        Text("M", color = cs.onPrimary, fontSize = 62.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PageHello(vm: LauncherViewModel, host: Host) {
    Spacer(Modifier.height(32.dp))
    Logo()
    Spacer(Modifier.height(28.dp))
    Title("Benvenuto in MLauncher", "Una Home in stile HyperOS con i colori Material You. Bastano pochi passaggi per farla tua.")
    if (vm.isDefaultHome) {
        DoneRow("MLauncher è la tua Home predefinita")
    } else {
        Button(onClick = { host.requestHomeRole() }, modifier = Modifier.fillMaxWidth()) { Text("Imposta come Home predefinita") }
        Text(
            "Serve per usare il tasto Home e i gesti con MLauncher. Puoi farlo anche dopo.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun DoneRow(t: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.secondaryContainer).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Check, contentDescription = null, tint = cs.onSecondaryContainer)
        Spacer(Modifier.width(12.dp))
        Text(t, color = cs.onSecondaryContainer)
    }
}

/** Scheda grande selezionabile con un piccolo disegno. */
@Composable
private fun ChoiceCard(selected: Boolean, title: String, text: String, onClick: () -> Unit, art: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) cs.primaryContainer else cs.surfaceContainerHigh)
            .border(2.dp, if (selected) cs.primary else Color.Transparent, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        art()
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = if (selected) cs.onPrimaryContainer else cs.onSurface)
            Text(text, style = MaterialTheme.typography.bodySmall, color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant)
        }
    }
}

/** Disegnino di un telefono: [dots] icone in Home, [drawer] = linguetta del cassetto. */
@Composable
private fun PhoneArt(dots: Int, drawer: Boolean) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.size(52.dp, 84.dp).clip(RoundedCornerShape(10.dp)).background(cs.surface).border(2.dp, cs.outline, RoundedCornerShape(10.dp)).padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        repeat((dots + 3) / 4) { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(minOf(4, dots - r * 4)) { Box(Modifier.size(7.dp).clip(RoundedCornerShape(2.dp)).background(cs.primary)) }
            }
        }
        Spacer(Modifier.weight(1f))
        if (drawer) Box(Modifier.align(Alignment.CenterHorizontally).size(16.dp, 3.dp).clip(CircleShape).background(cs.outline))
    }
}

@Composable
private fun PageMode(vm: LauncherViewModel) {
    val s = vm.settings
    Spacer(Modifier.height(16.dp))
    Title("Come vuoi le tue app?", "Puoi cambiarlo quando vuoi da Impostazioni → Schermata Home.")
    ChoiceCard(
        s.homeMode == "drawer", "Home e cassetto app",
        "In Home solo le app che scegli, tutte le altre nel cassetto (scorri in su).",
        { if (s.homeMode != "drawer") vm.setSetting("homeMode", "drawer") }
    ) { PhoneArt(6, true) }
    ChoiceCard(
        s.homeMode == "all", "Tutte le app in Home",
        "Come su iPhone e HyperOS: ogni app installata ha la sua icona nelle pagine della Home.",
        { if (s.homeMode != "all") vm.setSetting("homeMode", "all") }
    ) { PhoneArt(16, false) }
    if (s.homeMode == "all") {
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { vm.setSetting("autoFolders", !s.autoFolders) }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Cartelle intelligenti", fontWeight = FontWeight.SemiBold)
                Text("Raggruppa da sole le app in cartelle", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = s.autoFolders, onCheckedChange = { vm.setSetting("autoFolders", it) })
        }
        if (s.autoFolders) {
            Spacer(Modifier.height(8.dp))
            ChipRow(
                listOf("genre_plus" to "Genere", "color" to "Colore", "letter" to "Lettera"),
                if (s.smartMode == "genre") "genre_plus" else s.smartMode
            ) { vm.setSetting("smartMode", it) }
        }
    }
}

@Composable
private fun ChipRow(options: List<Pair<String, String>>, current: String, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (k, l) ->
            FilterChip(selected = k == current, onClick = { onPick(k) }, label = { Text(l) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Section(t: String) {
    Text(
        t,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp)
    )
}

@Composable
private fun PageLookW(vm: LauncherViewModel) {
    val s = vm.settings
    val cs = MaterialTheme.colorScheme
    Spacer(Modifier.height(16.dp))
    Title("Il tuo stile", "Le anteprime si aggiornano subito. Tutto il resto lo trovi nelle impostazioni.")
    Section("Tema")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf("auto" to "Automatico", "light" to "Chiaro", "dark" to "Scuro").forEach { (k, l) ->
            Column(
                Modifier.clip(RoundedCornerShape(16.dp)).clickable { vm.setSetting("theme", k) }
                    .background(if (s.theme == k) cs.primaryContainer else Color.Transparent).padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ThemePreview(k)
                Text(l, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
    Section("Forma delle icone")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf("original" to "Originale", "ios" to "iOS", "circle" to "Cerchio", "square" to "Quadrato").forEach { (k, l) ->
            Column(
                Modifier.clip(RoundedCornerShape(16.dp)).clickable { vm.setSetting("iconShape", k) }
                    .background(if (s.iconShape == k) cs.primaryContainer else Color.Transparent).padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ShapePreview(k, 36.dp)
                Text(l, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
    Section("Stile della dock")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DOCK_STYLES.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (k, l) ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { vm.setSetting("dockStyle", k) }
                            .background(if (s.dockStyle == k) cs.primaryContainer else cs.surfaceContainerHigh).padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        DockPreview(k, s.dockSlots, s.dockOpacity, s.dockGlass, s.dockCorner)
                        Text(l, style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PermRow(title: String, why: String, done: Boolean, action: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(why, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        if (done) Icon(Icons.Default.Check, contentDescription = "Attivo", tint = cs.primary)
        else OutlinedButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun PagePerms(vm: LauncherViewModel, host: Host) {
    var city by remember { mutableStateOf(false) }
    if (city) CityPickerDialog(vm, host) { city = false }
    Spacer(Modifier.height(16.dp))
    Title("Permessi facoltativi", "Nessuno è obbligatorio: senza, la funzione relativa resta spenta. Puoi attivarli anche dopo.")
    PermRow(
        "Meteo", "Posizione approssimativa per il widget meteo, oppure scegli tu una città",
        vm.weatherReady, "Attiva"
    ) { city = true }
    PermRow(
        "Puntini delle notifiche", "Mostra sulle icone quante notifiche ha ogni app",
        vm.listenerEnabled, "Attiva"
    ) { host.openListenerSettings() }
    PermRow(
        "Doppio tocco per bloccare", "Servizio di accessibilità: serve solo a spegnere lo schermo con un doppio tocco",
        vm.accessibilityEnabled, "Attiva"
    ) {
        vm.setSetting("doubleTapLock", true)
        host.openAccessibilitySettings()
    }
}

@Composable
private fun PageRestore(host: Host, onSkip: () -> Unit) {
    Spacer(Modifier.height(16.dp))
    Title("Hai già un backup?", "Se hai usato MLauncher su questo o su un altro telefono puoi riprendere Home, cartelle e impostazioni.")
    Button(onClick = { host.importBackup() }, modifier = Modifier.fillMaxWidth()) { Text("Ripristina da file") }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text("Inizia da zero") }
}

@Composable
private fun PageDone() {
    Spacer(Modifier.height(32.dp))
    Logo()
    Spacer(Modifier.height(28.dp))
    Title("Tutto pronto!", "Qualche gesto per iniziare:")
    listOf(
        "Scorri in su" to "apri il cassetto o la ricerca",
        "Tieni premuta un'icona" to "menu rapido e scorciatoie",
        "Trascina un'icona su un'altra" to "crei una cartella",
        "Tieni premuto uno spazio vuoto" to "sfondo, widget e modifica Home",
        "Impostazioni Home" to "trovi tutto, anche con la ricerca"
    ).forEach { (a, b) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 7.dp).size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(12.dp))
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold))
                    append(a)
                    pop()
                    append(": $b")
                }
            )
        }
    }
}
