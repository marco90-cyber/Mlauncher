package com.marco.mlauncher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

private sealed class UpState {
    data object Checking : UpState()
    data object UpToDate : UpState()
    data class Available(val r: Updater.Release) : UpState()
    data class Downloading(val r: Updater.Release, val p: Float) : UpState()
    data class Ready(val r: Updater.Release, val file: File) : UpState()
    data class Failed(val msg: String, val page: String?) : UpState()
}

@Composable
fun UpdateDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpState>(UpState.Checking) }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        state = try {
            val r = Updater.latest(ctx)
            if (r.build > Updater.currentBuild(ctx) && r.apkUrl.isNotBlank()) UpState.Available(r) else UpState.UpToDate
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("Controllo aggiornamenti non riuscito: ${e.message ?: e.javaClass.simpleName}")
            UpState.Failed("Impossibile controllare gli aggiornamenti. Controlla la connessione.", Updater.RELEASES)
        }
    }

    fun startDownload(r: Updater.Release) {
        progress = 0f
        state = UpState.Downloading(r, 0f)
        scope.launch {
            state = try {
                val f = Updater.download(ctx, r.apkUrl) { p -> progress = p }
                AppLog.i("Aggiornamento ${r.name} scaricato").let { UpState.Ready(r, f) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Download aggiornamento non riuscito: ${e.message ?: e.javaClass.simpleName}")
                UpState.Failed("Download non riuscito.", r.pageUrl)
            }
        }
    }

    val s = state
    AlertDialog(
        onDismissRequest = { if (s !is UpState.Downloading) onDismiss() },
        title = {
            Text(
                when (s) {
                    UpState.Checking -> "Controllo aggiornamenti…"
                    UpState.UpToDate -> "Nessun aggiornamento"
                    is UpState.Available, is UpState.Ready -> "Nuova versione disponibile"
                    is UpState.Downloading -> "Download in corso…"
                    is UpState.Failed -> "Errore"
                }
            )
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                when (s) {
                    UpState.Checking -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    UpState.UpToDate -> Text("Hai già l'ultima versione (${Updater.versionName(ctx)}).")
                    is UpState.Available -> ReleaseText(s.r)
                    is UpState.Downloading -> {
                        Text(s.r.name)
                        Spacer(Modifier.height(12.dp))
                        if (progress >= 0f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    is UpState.Ready -> Text(
                        "${s.r.name} è pronta. Tocca «Installa» e conferma nella finestra di Android."
                    )
                    is UpState.Failed -> Text(s.msg)
                }
            }
        },
        confirmButton = {
            when (s) {
                is UpState.Available -> TextButton(onClick = { startDownload(s.r) }) { Text("Scarica") }
                is UpState.Ready -> TextButton(onClick = {
                    if (Updater.ensureInstallPermission(ctx)) Updater.install(ctx, s.file)
                }) { Text("Installa") }
                is UpState.Failed -> if (s.page != null) TextButton(onClick = { try {
                    ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(s.page)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) { } }) { Text("Apri la pagina") }
                else TextButton(onClick = onDismiss) { Text("OK") }
                is UpState.Downloading -> {}
                else -> TextButton(onClick = onDismiss) { Text("OK") }
            }
        },
        dismissButton = {
            if (s is UpState.Available || s is UpState.Ready || (s is UpState.Failed && s.page != null)) {
                TextButton(onClick = onDismiss) { Text("Chiudi") }
            }
        }
    )
}

@Composable
private fun ReleaseText(r: Updater.Release) {
    val ctx = LocalContext.current
    Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
        Text("${r.name} (installata: ${Updater.versionName(ctx)})", style = MaterialTheme.typography.bodyMedium)
        if (r.notes.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(r.notes.take(1500), style = MaterialTheme.typography.bodySmall)
        }
    }
}
