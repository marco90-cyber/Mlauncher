package com.marco.mlauncher

import androidx.compose.runtime.compositionLocalOf

/**
 * Voci delle impostazioni nate o cambiate in un aggiornamento: titolo della voce -> pagina che la contiene.
 * Finché non le tocchi hanno un puntino (e anche la categoria che le contiene). Ad ogni aggiornamento si aggiungono
 * qui le voci nuove; chi installa MLauncher per la prima volta non vede nessun puntino.
 */
val NEW_SETTINGS: Map<String, String> = mapOf(
    "Effetto cambio pagina" to "anim",
    "Animazione di apertura app" to "anim",
    "Doppio tocco sulla Home" to "gestures",
    "Scorri giù nella Home" to "gestures",
    "Scorri su nella Home" to "gestures",
    "App bloccate" to "icons",
    "Profondità (inclinazione)" to "wallpaper",
    "Distanza dal bordo" to "dock"
)

/** Il ViewModel per i puntini "nuovo" delle impostazioni (le righe non lo ricevono come parametro). */
internal val LocalNewVm = compositionLocalOf<LauncherViewModel?> { null }
