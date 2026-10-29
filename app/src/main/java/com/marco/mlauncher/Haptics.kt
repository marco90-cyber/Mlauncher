package com.marco.mlauncher

import android.view.HapticFeedbackConstants
import android.view.View

/** Feedback tattile centralizzato, disattivabile dalle impostazioni. */
object Haptics {
    @Volatile
    var enabled: Boolean = true
    @Volatile var onPress: Boolean = true
    @Volatile var onDrop: Boolean = true
    @Volatile var onTick: Boolean = true
    val pressOn get() = enabled && onPress

    /** Leggero: passaggio di pagina, passo di ridimensionamento, selezione. */
    fun tick(v: View) {
        if (enabled && onTick) v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Pressione lunga, inizio trascinamento. */
    fun press(v: View) {
        if (enabled && onPress) v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    /** Azione riuscita: rilascio di un elemento, cartella creata, widget aggiunto. */
    fun confirm(v: View) {
        if (enabled && onDrop) v.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    /** Azione non possibile. */
    fun reject(v: View) {
        if (enabled && onDrop) v.performHapticFeedback(HapticFeedbackConstants.REJECT)
    }
}
