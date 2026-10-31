package com.marco.mlauncher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Colori del foglio: chiave -> (sfondo, testo). */
private val NOTE_COLORS = listOf(
    "yellow" to (Color(0xFFFFF3B0) to Color(0xFF3B3200)),
    "green" to (Color(0xFFD4F5D0) to Color(0xFF0E3A12)),
    "blue" to (Color(0xFFD3E6FF) to Color(0xFF0B2A52)),
    "pink" to (Color(0xFFFFD9E4) to Color(0xFF4A0F24)),
    "purple" to (Color(0xFFE7DBFF) to Color(0xFF2D1660)),
    "gray" to (Color(0xFFE6E6EA) to Color(0xFF202126)),
    "dark" to (Color(0xFF2B2D33) to Color(0xFFF1F1F4)),
    "theme" to (Color.Unspecified to Color.Unspecified)
)

@Composable
private fun noteColors(key: String): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    if (key == "theme") return cs.secondaryContainer to cs.onSecondaryContainer
    return NOTE_COLORS.firstOrNull { it.first == key }?.second ?: NOTE_COLORS[0].second
}

/** Nota veloce in Home: tocca per scrivere, tieni premuto per spostarla o ridimensionarla. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotesWidget(ref: WidgetRef, vm: LauncherViewModel, onLongPress: () -> Unit, modifier: Modifier, sample: Boolean = false) {
    val n = if (sample) NoteData("Latte, pane\nChiamare Luca", "yellow") else vm.notes[ref.provider] ?: NoteData()
    val (bg, fg) = noteColors(n.color)
    var editing by remember { mutableStateOf(false) }
    if (editing) NoteEditor(n, onSave = { vm.saveNote(ref.provider, it) }) { editing = false }
    Box(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .combinedClickable(onClick = { if (!sample) editing = true }, onLongClick = onLongPress)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        if (n.text.isBlank()) {
            Text("Tocca per scrivere una nota", color = fg.copy(alpha = 0.55f), fontSize = (if (sample) 9 else n.size).sp)
        } else {
            Text(
                n.text,
                color = fg,
                fontSize = (if (sample) 9 else n.size).sp,
                lineHeight = ((if (sample) 9 else n.size) * 1.3f).sp,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Finestra per scrivere la nota: testo, colore del foglio, grandezza del testo. */
@Composable
private fun NoteEditor(start: NoteData, onSave: (NoteData) -> Unit, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var text by remember { mutableStateOf(start.text) }
    var color by remember { mutableStateOf(start.color) }
    var size by remember { mutableIntStateOf(start.size) }
    fun close() {
        onSave(NoteData(text, color, size))
        onClose()
    }
    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = cs.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Nota", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                val (bg, fg) = noteColors(color)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Scrivi qui…") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = size.sp, color = fg),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = bg,
                        unfocusedContainerColor = bg,
                        focusedBorderColor = cs.primary,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text("Colore del foglio", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    NOTE_COLORS.forEach { (k, _) ->
                        val (b, _) = noteColors(k)
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(b)
                                .border(if (k == color) 3.dp else 1.dp, if (k == color) cs.primary else cs.outlineVariant, CircleShape)
                                .clickable { color = k },
                            contentAlignment = Alignment.Center
                        ) {
                            if (k == color) Icon(Icons.Default.Check, contentDescription = null, tint = noteColors(k).second, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Grandezza del testo", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(12 to "Piccolo", 15 to "Normale", 19 to "Grande").forEach { (v, l) ->
                        FilterChip(selected = size == v, onClick = { size = v }, label = { Text(l) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (text.isNotEmpty()) TextButton(onClick = { text = "" }) { Text("Svuota") }
                    TextButton(onClick = { close() }) { Text("Fatto") }
                }
            }
        }
    }
}
