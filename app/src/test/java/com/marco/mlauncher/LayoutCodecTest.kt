package com.marco.mlauncher

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Salvataggio e rilettura della disposizione (preferenze e backup). */
class LayoutCodecTest {
    private val all = listOf(
        AppItem("com.whatsapp/com.whatsapp.Main", 0, 3, 5),
        AppItem("com.android.chrome/Main", -1, 2, 0),
        FolderItem("f1", "Google \"e\" altro", listOf("a/b", "c/d"), 1, 0, 0, big = true),
        FolderItem("f2", "", emptyList(), 2, 1, 1),
        WidgetItem("w1", WidgetRef("clock"), 0, 0, 0, 4, 2),
        WidgetItem("w2", WidgetRef("ext", 42, "com.marco.mlmeteo/.Widget"), 0, 0, 2, 4, 2),
        StackItem("s1", listOf(WidgetRef("notes"), WidgetRef("ext", 7, "x/y")), 1, 2, 2, 2, 2)
    )

    @Test fun roundTrip_keepsEverything() {
        val text = LayoutCodec.encode(all).toString()
        assertEquals(all, LayoutCodec.decode(JSONArray(text), skipExt = false))
    }

    @Test fun skipExt_dropsWidgetsOfOtherApps() {
        val out = LayoutCodec.decode(JSONArray(LayoutCodec.encode(all).toString()), skipExt = true)
        assertFalse(out.any { it is WidgetItem && it.ref.kind == "ext" })
        val stack = out.first { it is StackItem } as StackItem
        assertEquals("nello stack resta solo il widget di MLauncher", listOf(WidgetRef("notes")), stack.refs)
        assertEquals(all.size - 1, out.size)
    }

    @Test fun oldFolderWithoutBig_isSmall() {
        val old = """[{"t":"folder","fid":"f","name":"Vecchia","apps":["a/b"],"page":0,"x":1,"y":2}]"""
        val f = LayoutCodec.decode(JSONArray(old), false).single() as FolderItem
        assertFalse(f.big)
        assertEquals(1, f.spanX)
    }

    @Test fun bigFolderTakesFourCells() {
        val f = all[2]
        assertTrue(f.spanX == 2 && f.spanY == 2)
    }
}
