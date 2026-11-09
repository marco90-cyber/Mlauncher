package com.marco.mlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Piccole funzioni senza Android: nomi della cache delle icone, sfocatura. */
class PureLogicTest {
    @Test fun iconCacheName_isStableSafeAndDistinct() {
        val a = iconCacheName("com.whatsapp", "com.whatsapp.Main")
        assertEquals(a, iconCacheName("com.whatsapp", "com.whatsapp.Main"))
        assertNotEquals(a, iconCacheName("com.whatsapp", "com.whatsapp.Other"))
        assertNotEquals(iconCacheName("com.marco.mlauncher", "action:page:1"), iconCacheName("com.marco.mlauncher", "action:page:2"))
        assertTrue("solo caratteri adatti a un nome di file: $a", Regex("[a-z0-9_]+").matches(a))
    }

    @Test fun boxBlur_leavesFlatColorUntouched() {
        val c = 0xFF336699.toInt()
        val px = IntArray(12 * 9) { c }
        boxBlur(px, 12, 9, 3)
        assertTrue(px.all { it == c })
    }

    @Test fun boxBlur_spreadsALightAndKeepsOpacity() {
        val w = 15
        val h = 15
        val px = IntArray(w * h) { 0xFF000000.toInt() }
        px[7 * w + 7] = 0xFFFFFFFF.toInt()
        boxBlur(px, w, h, 2)
        assertTrue("resta tutto opaco", px.all { (it ushr 24) == 255 })
        val center = px[7 * w + 7] and 255
        assertTrue("il punto luminoso si attenua", center in 1..254)
        assertTrue("la luce arriva ai vicini", (px[7 * w + 8] and 255) > 0 && (px[6 * w + 7] and 255) > 0)
        assertEquals("lontano resta nero", 0, px[0] and 255)
        // simmetrica
        assertEquals(px[7 * w + 5], px[7 * w + 9])
        assertEquals(px[5 * w + 7], px[9 * w + 7])
    }

    @Test fun isActionId_recognisesOnlyActions() {
        assertTrue(isActionId("com.marco.mlauncher/action:drawer"))
        assertTrue(isActionId("com.marco.mlauncher/action:page:3"))
        assertTrue(!isActionId("com.marco.mlauncher/com.marco.mlauncher.SettingsActivity"))
        assertTrue(!isActionId("com.whatsapp/com.whatsapp.Main"))
        assertTrue("un'app che si chiama action ma non è un'Azione", !isActionId("action.app/com.x.Main"))
    }
}
