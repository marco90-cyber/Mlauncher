package com.marco.mlauncher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.horizontalScroll
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

private enum class Step { HELLO, WALLPAPER, THEME, COLORS, MODE, FOLDERS, ICONS, DOCK, MINUS, ANIM, DONE }

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
    // durante il benvenuto si impostano i valori del verticale
    LaunchedEffect(Unit) { vm.settings.previewLand = false }

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
                        Step.HELLO -> Hello(onStart = { next() }, onBackup = { host.importBackup() })
                        Step.WALLPAPER -> PickWallpaper(vm, host)
                        Step.THEME -> PickTheme(vm)
                        Step.COLORS -> PickColors(vm)
                        Step.MODE -> PickMode(vm)
                        Step.FOLDERS -> PickFolders(vm)
                        Step.ICONS -> PickIcons(vm)
                        Step.DOCK -> PickDock(vm)
                        Step.MINUS -> PickMinus(vm, host)
                        Step.ANIM -> PickAnim(vm)
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
    val enter = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        enter.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 260f))
    }
    val breath by androidx.compose.animation.core.rememberInfiniteTransition(label = "logo").animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(tween(2600, easing = Motion.Standard), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "breath"
    )
    Box(
        Modifier
            .graphicsLayer {
                val sc = enter.value * (1f + 0.035f * breath)
                scaleX = sc; scaleY = sc
                rotationZ = (1f - enter.value) * -12f
                alpha = enter.value.coerceIn(0f, 1f)
            }
            .size(size).clip(RoundedCornerShape(size * 0.3f))
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

/** Due anteprime affiancate: si tocca quella preferita e la scelta si applica subito (si va avanti con "Avanti"). */
@Composable
private fun AB(
    leftSelected: Boolean,
    rightSelected: Boolean,
    leftLabel: String,
    rightLabel: String,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    left: @Composable BoxScope.() -> Unit,
    right: @Composable BoxScope.() -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        PickCard(leftSelected, leftLabel, Modifier.weight(1f), onLeft, left)
        PickCard(rightSelected, rightLabel, Modifier.weight(1f), onRight, right)
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
private fun PickTheme(vm: LauncherViewModel) {
    val s = vm.settings
    Headline("Scegline uno", "Chiaro o scuro?")
    AB(
        s.theme == "light", s.theme == "dark", "Chiaro", "Scuro",
        { vm.setSetting("theme", "light") }, { vm.setSetting("theme", "dark") },
        { MiniPhone(mini(vm, false)) },
        { MiniPhone(mini(vm, true)) }
    )
    Spacer(Modifier.height(16.dp))
    FilterChip(
        selected = s.theme == "auto",
        onClick = { vm.setSetting("theme", "auto") },
        label = { Text("Segui il telefono (automatico)") }
    )
    Label("Stile degli interruttori")
    val cs = MaterialTheme.colorScheme
    // un interruttore di prova: si accende e si spegne da solo per far vedere lo stile
    var demo by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1400)
            demo = !demo
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SWITCH_STYLES.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (k, l) ->
                    val on = s.switchStyle == k
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).clickable { vm.setSetting("switchStyle", k) }
                            .background(if (on) cs.primaryContainer else cs.surfaceContainerHigh)
                            .border(if (on) 2.dp else 0.dp, if (on) cs.primary else Color.Transparent, RoundedCornerShape(18.dp))
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        MLSwitch(checked = demo, onCheckedChange = null, style = k)
                        Text(l.substringBefore(" ("), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PickColors(vm: LauncherViewModel) {
    val s = vm.settings
    val cs = MaterialTheme.colorScheme
    Headline("I tuoi colori", "Gli stessi colori di Android, quelli del tuo sfondo oppure un colore fisso.")
    Box(Modifier.fillMaxWidth(0.5f).aspectRatio(0.52f).clip(RoundedCornerShape(26.dp)).border(1.dp, cs.outlineVariant, RoundedCornerShape(26.dp)).padding(6.dp)) {
        MiniPhone(mini(vm, isDarkPreview(s.theme)))
    }
    Spacer(Modifier.height(20.dp))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ACCENTS.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (k, l) ->
                    val on = s.accent == k
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { vm.setSetting("accent", k) }
                            .background(if (on) cs.primaryContainer else Color.Transparent).padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            AccentPreview(k)
                            if (on) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        Text(l, style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PickMode(vm: LauncherViewModel) {
    val s = vm.settings
    val dark = isDarkPreview(s.theme)
    Headline("Scegline un altro", "Dove vuoi le tue app?")
    AB(
        s.homeMode == "drawer", s.homeMode == "all", "Home e cassetto\n(scorri in su)", "Tutte le app\nin Home",
        { if (s.homeMode != "drawer") vm.setSetting("homeMode", "drawer") },
        { if (s.homeMode != "all") vm.setSetting("homeMode", "all") },
        { MiniPhone(mini(vm, dark).copy(allApps = false, folders = false)) },
        { MiniPhone(mini(vm, dark).copy(allApps = true, folders = false)) }
    )
}

@Composable
private fun PickFolders(vm: LauncherViewModel) {
    val s = vm.settings
    val dark = isDarkPreview(s.theme)
    Headline("Ancora uno", "Vuoi che le app si raggruppino da sole in cartelle?")
    AB(
        !s.autoFolders, s.autoFolders, "Icone sciolte", "Cartelle\nintelligenti",
        { vm.setSetting("autoFolders", false) }, { vm.setSetting("autoFolders", true) },
        { MiniPhone(mini(vm, dark).copy(folders = false)) },
        { MiniPhone(mini(vm, dark).copy(folders = true)) }
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
private fun Label(t: String) {
    Text(
        t,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp)
    )
}

@Composable
private fun PickIcons(vm: LauncherViewModel) {
    val s = vm.settings
    val cs = MaterialTheme.colorScheme
    val dark = isDarkPreview(s.theme)
    Headline("Icone e griglia", "Quante icone, di che forma e quanto grandi.")
    Label("Griglia")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Triple(4, 5, "4 × 5"), Triple(4, 6, "4 × 6"), Triple(5, 6, "5 × 6")).forEach { (c, r, l) ->
            PickCard(s.cols == c && s.rows == r, l, Modifier.weight(1f), {
                if (s.cols != c) vm.setSetting("cols", c)
                if (s.rows != r) vm.setSetting("rows", r)
            }) { MiniPhone(mini(vm, dark).copy(cols = c, rows = r, allApps = true, folders = false)) }
        }
    }
    Label("Forma")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf("original" to "Originale", "ios" to "iOS", "circle" to "Cerchio", "square" to "Quadrato").forEach { (k, l) ->
            Column(
                Modifier.clip(RoundedCornerShape(16.dp)).clickable { vm.setSetting("iconShape", k) }
                    .background(if (s.iconShape == k) cs.primaryContainer else Color.Transparent).padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ShapePreview(k, 34.dp)
                Text(l, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
    Label("Grandezza")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0.9f to "Piccole", 1f to "Normali", 1.1f to "Grandi").forEach { (v, l) ->
            FilterChip(
                selected = kotlin.math.abs(s.iconScale - v) < 0.03f,
                onClick = { vm.setSetting("iconScale", v) },
                label = { Text(l) },
                modifier = Modifier.weight(1f)
            )
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh)
            .clickable { vm.setSetting("showLabels", !s.showLabels) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Mostra i nomi delle app", Modifier.weight(1f))
        MLSwitch(checked = s.showLabels, onCheckedChange = { vm.setSetting("showLabels", it) })
    }
}

@Composable
private fun PickDock(vm: LauncherViewModel) {
    val s = vm.settings
    val cs = MaterialTheme.colorScheme
    val dark = isDarkPreview(s.theme)
    Headline("La dock", "La barra delle app in basso e l'indicatore delle pagine.")
    fun pickDock(k: String) {
        vm.setSetting("liquid", k == "liquid")
        vm.setSetting("dockStyle", k)
        if (k == "liquid") {
            vm.setSetting("folderFollowDock", true)
            vm.setSetting("indFollowDock", true)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("liquid" to "Liquid\nGlass", "glass" to "Trasparente", "pill" to "Pillola\npiena").forEach { (k, l) ->
            PickCard(s.dockStyle == k, l, Modifier.weight(1f), { pickDock(k) }) { MiniPhone(mini(vm, dark).copy(dock = k)) }
        }
    }
    if (s.dockStyle == "liquid") Text(
        "Tema Liquid Glass: anche cartelle, indicatore e widget diventano di vetro liquido.",
        color = cs.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 10.dp)
    )
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh)
            .clickable { vm.setSetting("dockGlass", !s.dockGlass) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Effetto glass")
            Text("Riflesso e bordo luminoso come il vetro", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        MLSwitch(checked = s.dockGlass, onCheckedChange = { vm.setSetting("dockGlass", it) })
    }
    Label("Indicatore di pagina")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("pill" to "Allungato", "dots" to "Puntini", "lines" to "Linee", "ios" to "iOS 27", "worm" to "Ovale", "number" to "Numero")
            .chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (k, l) ->
                        val on = s.pageIndicator == k
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { vm.setSetting("pageIndicator", k) }
                                .background(if (on) cs.primaryContainer else cs.surfaceContainerHigh)
                                .border(if (on) 2.dp else 0.dp, if (on) cs.primary else Color.Transparent, RoundedCornerShape(16.dp))
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            IndicatorPreview(k)
                            Text(l, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
    }
    Text(
        "Altri stili della dock (scuro, colorato, barra, tessere…) in Impostazioni → Dock",
        color = cs.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 14.dp)
    )
}

@Composable
private fun Done(vm: LauncherViewModel, host: Host) {
    var city by remember { mutableStateOf(false) }
    if (city) CityPickerDialog(vm, host) { city = false }
    Spacer(Modifier.height(24.dp))
    Logo(88.dp)
    Spacer(Modifier.height(20.dp))
    Headline("Tutto pronto!", "Ecco il tuo MLauncher. Gli ultimi ritocchi qui sotto sono facoltativi.")
    Summary(vm)
    Spacer(Modifier.height(12.dp))
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
    ToggleRow("Scuoti per riordinare", "In modifica, scuoti il telefono per compattare la pagina", vm.settings.shakeTidy) {
        vm.setSetting("shakeTidy", it)
    }
    ToggleRow("Blocca la Home", "Icone e widget non si spostano per sbaglio (sblocchi tenendo premuto uno spazio vuoto)", vm.settings.lockHome) {
        vm.setSetting("lockHome", it)
    }
    SetupRow(
        "Backup automatico", "Ogni settimana in una cartella a scelta (cambi frequenza nelle impostazioni)",
        vm.settings.autoBackup && vm.settings.backupDir.isNotEmpty(), "Attiva"
    ) {
        vm.setSetting("autoBackup", true)
        host.pickBackupFolder()
    }
}

@Composable
private fun PickWallpaper(vm: LauncherViewModel, host: Host) {
    val s = vm.settings
    val cs = MaterialTheme.colorScheme
    val dark = isDarkPreview(s.theme)
    Headline("Il tuo sfondo", "Scegli una tua foto e ritagliala, oppure uno degli sfondi inclusi. I colori del Launcher si prendono dall'immagine.")
    Box(Modifier.fillMaxWidth(0.5f).aspectRatio(0.52f).clip(RoundedCornerShape(26.dp)).border(1.dp, cs.outlineVariant, RoundedCornerShape(26.dp)).padding(6.dp)) {
        MiniPhone(mini(vm, dark))
    }
    run {
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { host.pickWallpaperImage() }, modifier = Modifier.weight(1f)) { Text("Galleria") }
            OutlinedButton(onClick = { vm.importSystemWallpaper() }, modifier = Modifier.weight(1f)) { Text("Importa") }
            OutlinedButton(onClick = { vm.editCurrentWallpaper() }, modifier = Modifier.weight(1f)) { Text("Modifica") }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BUILTIN_WALLS.forEach { (k, l) ->
                val thumb = remember(k) { builtinWall(k, 90, 180).asImageBitmap() }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { vm.builtinWallpaper(k) }) {
                    androidx.compose.foundation.Image(thumb, contentDescription = l, modifier = Modifier.size(52.dp, 104.dp).clip(RoundedCornerShape(12.dp)))
                    Text(l, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        if (vm.wallBusy) Text("Preparo lo sfondo…", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ToggleRow(title: String, why: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh)
            .clickable { onChange(!checked) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(why, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        MLSwitch(checked = checked, onCheckedChange = onChange)
    }
}

/** Pagina −1: sì o no; se sì, il nome per il saluto e lo stile del contenitore. */
@Composable
private fun PickMinus(vm: LauncherViewModel, host: Host) {
    val s = vm.settings
    val cs = MaterialTheme.colorScheme
    Headline("Pagina \u22121", "Scorri verso destra dalla prima pagina: un saluto, la ricerca e i tuoi widget preferiti.")
    AB(
        s.minusOn, !s.minusOn, "S\u00ec, la voglio", "No, grazie",
        { vm.setSetting("minusOn", true) }, { vm.setSetting("minusOn", false) },
        { MiniMinus(vm, true) },
        { MiniMinus(vm, false) }
    )
    if (s.minusOn) {
        Label("Come ti chiami?")
        var t by remember { mutableStateOf(s.minusName.ifEmpty { vm.autoName ?: "" }) }
        androidx.compose.material3.OutlinedTextField(
            value = t,
            onValueChange = { t = it; vm.setSetting("minusName", it.trim()) },
            singleLine = true,
            placeholder = { Text("Il tuo nome") },
            supportingText = { Text("Per il saluto: \u201cBuongiorno, ${t.trim().ifEmpty { "\u2026" }}\u201d") },
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        )
        // se il telefono lo trova dopo il permesso, lo scrivo nel campo
        LaunchedEffect(vm.autoName) {
            val a = vm.autoName
            if (a != null && t.isBlank()) { t = a; vm.setSetting("minusName", a) }
        }
        if (vm.autoName == null) TextButton(onClick = { host.requestContactsName() }) {
            Text("Prendi il nome dal profilo del telefono")
        }
        Label("Contenitore dei widget")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("dock" to "Come la dock", "tonal" to "Tonale", "glass" to "Vetro").forEach { (k, l) ->
                FilterChip(selected = s.minusStyle == k, onClick = { vm.setSetting("minusStyle", k) }, label = { Text(l) })
            }
        }
    }
}

/** Anteprima della pagina −1 (o della Home senza) per la scelta nel benvenuto. */
@Composable
private fun BoxScope.MiniMinus(vm: LauncherViewModel, on: Boolean) {
    val cs = MaterialTheme.colorScheme
    val wall = if (vm.customWallActive) vm.wallBitmap else null
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(Brush.verticalGradient(listOf(cs.primary.copy(alpha = 0.55f), cs.tertiary.copy(alpha = 0.45f))))) {
        if (wall != null) androidx.compose.foundation.Image(
            wall.asImageBitmap(), contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(if (on) 8.dp else 0.dp)
        )
        if (on) {
            Box(Modifier.fillMaxSize().background(cs.surface.copy(alpha = 0.35f)))
            Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Spacer(Modifier.height(10.dp))
                Text("Buongiorno", color = cs.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Box(Modifier.fillMaxWidth(0.6f).height(5.dp).clip(CircleShape).background(cs.onSurface.copy(alpha = 0.35f)))
                Box(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(cs.surfaceContainerHigh))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainer.copy(alpha = 0.85f)).padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(9.dp)).background(cs.primaryContainer))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(9.dp)).background(cs.tertiaryContainer))
                        Box(Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(9.dp)).background(cs.secondaryContainer))
                    }
                    Box(Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(9.dp)).background(cs.surfaceContainerHighest))
                }
            }
        } else {
            // Home normale: solo icone
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.height(30.dp))
                repeat(3) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        repeat(4) { Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.85f))) }
                    }
                }
            }
        }
    }
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

/** Cosa mostra il telefono in miniatura. */
private data class Mini(
    val dark: Boolean,
    val allApps: Boolean,
    val dock: String,
    val glass: Boolean,
    val folders: Boolean,
    val cols: Int,
    val rows: Int,
    val shape: String,
    val scale: Float,
    val labels: Boolean,
    val indicator: String,
    val wall: androidx.compose.ui.graphics.ImageBitmap? = null
)

/** Miniatura con le scelte fatte finora. */
private fun mini(s: LauncherSettings, dark: Boolean) = Mini(
    dark = dark,
    allApps = s.homeMode == "all",
    dock = s.dockStyle,
    glass = s.dockGlass,
    folders = s.autoFolders && s.homeMode == "all",
    cols = s.colsOf(false),
    rows = s.rowsOf(false),
    shape = s.iconShape,
    scale = s.iconScale,
    labels = s.showLabels,
    indicator = s.pageIndicator
)

/** Come [mini], con l'immagine dello sfondo di MLauncher se c'è. */
private fun mini(vm: LauncherViewModel, dark: Boolean) = mini(vm.settings, dark).copy(wall = if (vm.customWallActive) vm.wallBitmap else null)

@Composable
private fun BoxScope.MiniPhone(m: Mini) {
    val cs = MaterialTheme.colorScheme
    val dark = m.dark
    val wall = if (dark) Brush.verticalGradient(listOf(Color(0xFF0E1A2B), Color(0xFF1B2F4A), Color(0xFF0A1220)))
    else Brush.verticalGradient(listOf(Color(0xFFDDE8F7), Color(0xFFB9D0EE), Color(0xFFE9EEF6)))
    val widgetBg = if (dark) Color(0xFF2A3346) else Color(0xFFF7F9FD)
    val widgetFg = if (dark) Color.White else Color(0xFF1B1F27)
    val iconSz = (if (m.cols >= 5) 10.dp else 13.dp) * m.scale
    val iconShape = when (m.shape) {
        "circle" -> CircleShape
        "square" -> RoundedCornerShape(iconSz * 0.2f)
        "ios" -> RoundedCornerShape(iconSz * 0.28f)
        else -> RoundedCornerShape(iconSz * 0.32f)
    }
    if (m.wall != null) androidx.compose.foundation.Image(
        m.wall, contentDescription = null,
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = Modifier.matchParentSize().clip(RoundedCornerShape(20.dp))
    )
    Column(
        Modifier.matchParentSize().clip(RoundedCornerShape(20.dp)).then(if (m.wall == null) Modifier.background(wall) else Modifier).padding(8.dp)
    ) {
        Text("10:08", fontSize = 7.sp, color = widgetFg.copy(alpha = 0.8f))
        Spacer(Modifier.height(5.dp))
        // barra di ricerca e widget
        Box(Modifier.fillMaxWidth().height(9.dp).clip(CircleShape).background(widgetBg.copy(alpha = 0.85f)))
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(
                Modifier.weight(1f).height(26.dp).clip(RoundedCornerShape(8.dp)).background(cs.primaryContainer),
                contentAlignment = Alignment.Center
            ) { Text("10:08", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = cs.onPrimaryContainer) }
            Box(Modifier.weight(1f).height(26.dp).clip(RoundedCornerShape(8.dp)).background(cs.tertiaryContainer)) {
                Box(Modifier.align(Alignment.Center).size(11.dp).clip(CircleShape).background(Color(0xFFFFC107)))
            }
        }
        Spacer(Modifier.height(6.dp))
        val rows = if (m.allApps) (m.rows - 2).coerceAtLeast(2) else 1
        if (!m.allApps) Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(if (m.labels) 3.dp else 5.dp)) {
            repeat(rows) { r ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    repeat(m.cols) { c ->
                        val i = r * m.cols + c
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (m.folders && (i == 0 || i == m.cols + 1 || i == 2 * m.cols + 2)) MiniFolder(widgetBg, i, iconSz)
                            else Box(Modifier.size(iconSz).clip(iconShape).background(iconColors[i % iconColors.size]))
                            if (m.labels) Box(Modifier.padding(top = 1.5.dp).size(iconSz * 0.8f, 1.5.dp).clip(CircleShape).background(widgetFg.copy(alpha = 0.55f)))
                        }
                    }
                }
            }
        }
        if (m.allApps) Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(5.dp))
        // indicatore (con tutte le app) o linguetta del cassetto
        Box(Modifier.fillMaxWidth().height(8.dp), contentAlignment = Alignment.Center) {
            if (!m.allApps) Box(Modifier.size(16.dp, 3.dp).clip(CircleShape).background(widgetFg.copy(alpha = 0.6f)))
            else MiniIndicator(m.indicator, widgetFg)
        }
        Spacer(Modifier.height(5.dp))
        // dock
        val shape = RoundedCornerShape(12.dp)
        val dockMod = when (m.dock) {
            "pill" -> Modifier.clip(shape).background(if (dark) Color(0xFF2B3140) else Color(0xFFF3F5F9))
            "dark" -> Modifier.clip(shape).background(Color.Black.copy(alpha = 0.4f))
            "tinted" -> Modifier.clip(shape).background(cs.primaryContainer.copy(alpha = 0.6f))
            "none", "tiles" -> Modifier
            "liquid" -> Modifier.clip(shape).background(Color.White.copy(alpha = 0.14f))
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent, Color.White.copy(alpha = 0.15f))))
                .border(1.5.dp, Brush.linearGradient(listOf(Color.White, Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.7f))), shape)
            else -> Modifier.clip(shape).background(Color.White.copy(alpha = 0.22f))
        }.let {
            if (m.glass && m.dock != "none" && m.dock != "tiles" && m.dock != "liquid")
                it.background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent)))
                    .border(1.dp, Color.White.copy(alpha = 0.75f), shape)
            else it
        }
        Row(
            Modifier.fillMaxWidth().then(dockMod).padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            repeat(4) { Box(Modifier.size(iconSz).clip(iconShape).background(iconColors[(it * 3 + 1) % iconColors.size])) }
        }
    }
}

@Composable
private fun MiniIndicator(key: String, fg: Color) {
    when (key) {
        "none" -> {}
        "number" -> Text("1 / 3", fontSize = 6.sp, color = fg)
        "ios" -> Box(Modifier.clip(CircleShape).background(fg.copy(alpha = 0.25f)).padding(horizontal = 5.dp, vertical = 1.dp)) {
            Text("Cerca", fontSize = 5.sp, color = fg)
        }
        else -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(3) { i ->
                val on = i == 0
                val w = when (key) {
                    "pill" -> if (on) 8.dp else 3.dp
                    "lines" -> 7.dp
                    "worm" -> if (on) 6.dp else 3.dp
                    "progress" -> 7.dp
                    else -> 3.dp
                }
                Box(Modifier.size(w, if (key == "lines") 1.5.dp else 3.dp).clip(CircleShape).background(fg.copy(alpha = if (on) 0.95f else 0.4f)))
            }
        }
    }
}

@Composable
private fun MiniFolder(bg: Color, seed: Int, size: Dp) {
    Column(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(bg.copy(alpha = 0.7f)).padding(size * 0.15f),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        repeat(2) { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                repeat(2) { c -> Box(Modifier.size(size * 0.27f).clip(CircleShape).background(iconColors[(seed + r * 2 + c) % iconColors.size])) }
            }
        }
    }
}

/** Riepilogo delle scelte fatte nel benvenuto. */
@Composable
private fun Summary(vm: LauncherViewModel) {
    val s = vm.settings
    val cs = MaterialTheme.colorScheme
    val rows = listOf(
        "Tema" to when (s.theme) { "light" -> "Chiaro"; "dark" -> "Scuro"; else -> "Automatico" },
        "Colori" to (ACCENTS.firstOrNull { it.first == s.accent }?.second ?: ""),
        "App" to (if (s.homeMode == "all") "Tutte in Home" + (if (s.autoFolders) ", cartelle intelligenti" else "") else "Home e cassetto"),
        "Griglia" to "${s.colsOf(false)} × ${s.rowsOf(false)}",
        "Interruttori" to (SWITCH_STYLES.firstOrNull { it.first == s.switchStyle }?.second?.substringBefore(" (") ?: ""),
        "Dock" to (DOCK_STYLES.firstOrNull { it.first == s.dockStyle }?.second ?: ""),
        "Pagina \u22121" to (if (s.minusOn) "Attiva" else "Spenta"),
        "Ritorno alla Home" to (RETURN_ANIMS.firstOrNull { it.first == s.returnAnim }?.second?.substringBefore(" (") ?: "")
    )
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh).padding(16.dp)) {
        rows.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(k, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(v, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Animazioni: ritorno alla Home (con anteprima che si ripete) ed effetto di cambio pagina. */
@Composable
private fun PickAnim(vm: LauncherViewModel) {
    val s = vm.settings
    val cs = MaterialTheme.colorScheme
    val dark = isDarkPreview(s.theme)
    Headline("Animazioni", "Come riappare la Home quando chiudi un'app o sblocchi il telefono.")
    // anteprima: ripete l'animazione scelta
    val loop by androidx.compose.animation.core.rememberInfiniteTransition(label = "ret").animateFloat(
        0f, 1.6f,
        androidx.compose.animation.core.infiniteRepeatable(tween(1800, easing = androidx.compose.animation.core.LinearEasing)),
        label = "p"
    )
    Box(
        Modifier.fillMaxWidth(0.5f).aspectRatio(0.52f).clip(RoundedCornerShape(26.dp))
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(26.dp)).padding(6.dp)
    ) {
        Box(Modifier.matchParentSize().graphicsLayer {
            val p = loop.coerceIn(0f, 1f)
            val e = Motion.EmphasizedDecelerate.transform(p)
            when (s.returnAnim) {
                "settle" -> { val sc = 1.07f - 0.07f * e; scaleX = sc; scaleY = sc; alpha = 0.35f + 0.65f * e }
                "approach" -> { val sc = 0.86f + 0.14f * e; scaleX = sc; scaleY = sc; alpha = e }
                "fade" -> alpha = p
                "cascade_down" -> { translationY = (1f - e) * -40f; alpha = e }
                "cascade_up" -> { translationY = (1f - e) * 40f; alpha = e }
                "wave" -> { val sc = 0.8f + 0.2f * e; scaleX = sc; scaleY = sc; alpha = e }
            }
        }) { MiniPhone(mini(vm, dark)) }
    }
    Spacer(Modifier.height(16.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RETURN_ANIMS.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (k, l) ->
                    FilterChip(
                        selected = s.returnAnim == k,
                        onClick = { vm.setSetting("returnAnim", k) },
                        label = { Text(l.substringBefore(" ("), maxLines = 1) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    listOf(
        Triple("Quando torni da un'app", s.returnOnApp, "returnOnApp"),
        Triple("Quando sblocchi il telefono", s.returnOnUnlock, "returnOnUnlock"),
        Triple("Rimbalzo dell'icona dell'app chiusa", s.returnBounce, "returnBounce")
    ).forEach { (l, v, k) ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHigh)
                .clickable { vm.setSetting(k, !v) }.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(l, Modifier.weight(1f))
            MLSwitch(checked = v, onCheckedChange = { vm.setSetting(k, it) })
        }
    }
}
