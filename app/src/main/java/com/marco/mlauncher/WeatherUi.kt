package com.marco.mlauncher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Scheda del meteo che si apre toccando il widget: dettagli, prossime ore, prossimi giorni. */
@Composable
fun WeatherDetailDialog(vm: LauncherViewModel, onCity: () -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val w = vm.weather ?: return
    val d = vm.weatherDetail
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = cs.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(max = 640.dp)
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            w.place.ifEmpty { "Meteo" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Aggiornato alle " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(w.time)),
                            color = cs.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    IconButton(onClick = { vm.refreshWeather(true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Aggiorna")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WeatherIcon(w.code, w.isDay, Modifier.size(84.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("${w.temp}°", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
                        Text(weatherText(w.code), color = cs.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
                    }
                }
                Text(
                    "Max ${w.hi}°  ·  Min ${w.lo}°" + (d?.let { "  ·  Percepita ${it.feels}°" } ?: ""),
                    color = cs.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (d != null) {
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoTile("Umidità", "${d.humidity}%", Modifier.weight(1f))
                        InfoTile("Vento", "${d.wind} km/h", Modifier.weight(1f))
                        InfoTile("Pioggia", "${d.pop}%", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoTile("Alba", d.sunrise, Modifier.weight(1f))
                        InfoTile("Tramonto", d.sunset, Modifier.weight(1f))
                    }
                    if (d.hours.isNotEmpty()) {
                        SectionLabel("Prossime ore")
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            d.hours.forEach { h ->
                                Column(
                                    Modifier
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(if (h.label == "Ora") cs.primaryContainer else cs.surfaceContainerHighest)
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(h.label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                                    WeatherIcon(h.code, h.isDay, Modifier.padding(vertical = 4.dp).size(28.dp))
                                    Text("${h.temp}°", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (h.pop > 0) "${h.pop}%" else " ",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = cs.primary
                                    )
                                }
                            }
                        }
                    }
                    if (d.days.isNotEmpty()) {
                        SectionLabel("Prossimi giorni")
                        val tMin = d.days.minOf { it.min }
                        val tMax = d.days.maxOf { it.max }
                        val span = maxOf(1, tMax - tMin).toFloat()
                        d.days.forEachIndexed { i, day ->
                            if (i > 0) HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f))
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(day.label, Modifier.width(92.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                WeatherIcon(day.code, true, Modifier.size(26.dp))
                                Text(
                                    if (day.pop > 0) "${day.pop}%" else "",
                                    Modifier.width(44.dp).padding(start = 6.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.primary
                                )
                                Text("${day.min}°", Modifier.width(34.dp), color = cs.onSurfaceVariant)
                                // barra dell'escursione termica rispetto alla settimana
                                Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(cs.surfaceContainerHighest)) {
                                    Row(Modifier.fillMaxWidth()) {
                                        val a = (day.min - tMin) / span
                                        val b = (day.max - day.min) / span
                                        if (a > 0f) Spacer(Modifier.weight(a))
                                        Box(Modifier.weight(maxOf(b, 0.04f)).height(6.dp).clip(CircleShape).background(cs.primary))
                                        val rest = 1f - a - b
                                        if (rest > 0f) Spacer(Modifier.weight(rest))
                                    }
                                }
                                Text("${day.max}°", Modifier.width(40.dp).padding(start = 8.dp), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCity) { Text("Cambia città") }
                    TextButton(onClick = onDismiss) { Text("Chiudi") }
                }
            }
        }
    }
}

@Composable
private fun InfoTile(title: String, value: String, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainerHighest).padding(12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionLabel(t: String) {
    Text(
        t,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

/** Scelta della città del meteo: posizione del telefono oppure ricerca per nome. */
@Composable
fun CityPickerDialog(vm: LauncherViewModel, host: Host, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<City>?>(null) }
    fun search() {
        if (q.isBlank()) return
        busy = true
        scope.launch {
            results = vm.searchCity(q)
            busy = false
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = cs.surfaceContainerHigh) {
            Column(Modifier.padding(20.dp)) {
                Text("Città del meteo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (vm.weatherCity == null && vm.hasLocation) cs.secondaryContainer else cs.surfaceContainerHighest)
                        .clickable {
                            vm.chooseWeatherCity(null)
                            if (!vm.hasLocation) host.requestLocation()
                            onDismiss()
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = cs.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Usa la mia posizione")
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it },
                    singleLine = true,
                    label = { Text("Cerca una città") },
                    trailingIcon = {
                        IconButton(onClick = { search() }) { Icon(Icons.Default.Search, contentDescription = "Cerca") }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    modifier = Modifier.fillMaxWidth()
                )
                if (busy) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                }
                val r = results
                if (!busy && r != null) {
                    if (r.isEmpty()) {
                        Text("Nessuna città trovata", color = cs.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
                    }
                    Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                        r.forEach { c ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        vm.chooseWeatherCity(c)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 10.dp)
                            ) {
                                Text(c.name, fontWeight = FontWeight.SemiBold)
                                if (c.area.isNotEmpty()) Text(c.area, color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                vm.weatherCity?.let {
                    Text(
                        "Attuale: ${it.name}",
                        color = cs.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Chiudi") }
                }
            }
        }
    }
}
