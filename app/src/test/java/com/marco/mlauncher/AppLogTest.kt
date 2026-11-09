package com.marco.mlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** Registro: divisione in eventi, pulizia dopo 7 giorni, filtri Errori e Velocità. */
class AppLogTest {
    private fun at(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply { clear(); set(year, month - 1, day, 12, 0, 0) }.timeInMillis

    private val text = listOf(
        "09/11 17:19:40  I  Home in background",
        "09/11 17:25:47  W  Rallentamento (ritorno alla Home): 632 ms in un solo lavoro\n    campioni: 60\n    in MLauncher:\n      11× HomeScreen.kt:1178",
        "09/11 17:25:48  E  Widget: riconfigurazione non avviata\njava.lang.IllegalStateException: boom\n\tat a.b.C.d(C.kt:1)",
        "09/11 17:26:00  I  Scatti — Apertura cassetto: 3 fotogrammi persi su 51 (il più lento 68 ms)",
        "09/11 17:27:00  I  Apro WhatsApp (com.whatsapp)"
    ).joinToString("\n") + "\n"

    @Test fun entries_keepsMultiLineEventsTogether() {
        val e = AppLog.entries(text)
        assertEquals(5, e.size)
        assertTrue(e[1].contains("campioni: 60"))
        assertTrue(e[2].contains("IllegalStateException"))
        assertEquals("", AppLog.entries("").joinToString())
    }

    @Test fun levelAndFilters() {
        val e = AppLog.entries(text)
        assertEquals(listOf("I", "W", "E", "I", "I"), e.map { AppLog.level(it) })
        assertEquals("errori = avvisi ed errori", listOf(false, true, true, false, false), e.map { AppLog.isError(it) })
        assertEquals("velocità = rallentamenti e scatti", listOf(false, true, false, true, false), e.map { AppLog.isSpeed(it) })
    }

    @Test fun recent_dropsOlderThanSevenDays() {
        val now = at(2026, 11, 9)
        val list = listOf("30/10 10:00:00  I  vecchio", "01/11 10:00:00  I  otto giorni fa", "03/11 10:00:00  I  sei giorni fa", "09/11 10:00:00  I  oggi")
        assertEquals(listOf("03/11 10:00:00  I  sei giorni fa", "09/11 10:00:00  I  oggi"), AppLog.recent(list, now))
    }

    @Test fun recent_worksAcrossNewYear() {
        val now = at(2027, 1, 3)
        val list = listOf("20/12 10:00:00  I  troppo vecchio", "30/12 10:00:00  I  quattro giorni fa", "02/01 10:00:00  I  ieri")
        assertEquals(listOf("30/12 10:00:00  I  quattro giorni fa", "02/01 10:00:00  I  ieri"), AppLog.recent(list, now))
    }

    @Test fun recent_keepsLinesWithoutDate() {
        assertEquals(listOf("riga strana"), AppLog.recent(listOf("riga strana"), at(2026, 11, 9)))
        assertFalse(AppLog.recent(listOf("01/01 00:00:00  I  x"), at(2026, 11, 9)).isNotEmpty())
    }
}
