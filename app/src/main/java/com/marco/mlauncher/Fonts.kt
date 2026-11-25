package com.marco.mlauncher

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import java.io.File

/**
 * Caratteri incorporati (licenza SIL OFL, testi in assets/fonts/LICENSES.txt).
 * [bold] è il file del grassetto per i caratteri statici; chi è variabile usa un solo file per tutti i pesi.
 */
class AppFont(val key: String, val label: String, val asset: String, val bold: String? = null, val variable: Boolean = true)

val APP_FONTS = listOf(
    AppFont("poppins", "Poppins", "fonts/poppins_regular.ttf", "fonts/poppins_bold.ttf", variable = false),
    AppFont("opensans", "Open Sans", "fonts/opensans.ttf"),
    AppFont("montserrat", "Montserrat", "fonts/montserrat.ttf"),
    AppFont("nunito", "Nunito", "fonts/nunito.ttf"),
    AppFont("playfair", "Playfair Display", "fonts/playfair.ttf"),
    AppFont("robotomono", "Roboto Mono", "fonts/robotomono.ttf"),
    AppFont("oswald", "Oswald", "fonts/oswald.ttf"),
    AppFont("caveat", "Caveat", "fonts/caveat.ttf"),
    AppFont("pacifico", "Pacifico", "fonts/pacifico.ttf", variable = false)
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
object AppFonts {
    private const val CUSTOM_FILE = "custom_font.ttf"
    private const val MAX_BYTES = 15L * 1024 * 1024
    private val WEIGHTS = listOf(400, 500, 600, 700)

    fun customFile(ctx: Context) = File(ctx.filesDir, CUSTOM_FILE)

    fun label(key: String, customName: String): String = when (key) {
        "default" -> "Predefinito del telefono"
        "custom" -> customName.ifBlank { "Personale" }
        else -> APP_FONTS.firstOrNull { it.key == key }?.label ?: "Predefinito del telefono"
    }

    /** La famiglia per la chiave scelta; null = carattere di sistema (anche se il file manca o non si legge). */
    fun family(ctx: Context, key: String): FontFamily? = try {
        when (key) {
            "custom" -> customFile(ctx).takeIf { it.exists() }?.let { f ->
                FontFamily(WEIGHTS.map { w ->
                    Font(f, FontWeight(w), FontStyle.Normal, FontVariation.Settings(FontWeight(w), FontStyle.Normal))
                })
            }
            else -> APP_FONTS.firstOrNull { it.key == key }?.let { a -> build(ctx, a) }
        }
    } catch (e: Exception) {
        AppLog.quiet("caricamento del carattere $key", e)
        null
    }

    private fun build(ctx: Context, a: AppFont): FontFamily {
        val am = ctx.assets
        val fonts = if (a.variable) {
            WEIGHTS.map { w ->
                Font(a.asset, am, FontWeight(w), FontStyle.Normal, FontVariation.Settings(FontWeight(w), FontStyle.Normal))
            }
        } else {
            val heavy = a.bold ?: a.asset
            listOf(
                Font(a.asset, am, FontWeight(400)),
                Font(a.asset, am, FontWeight(500)),
                Font(heavy, am, FontWeight(600)),
                Font(heavy, am, FontWeight(700))
            )
        }
        return FontFamily(fonts)
    }

    /** Il file è davvero un carattere che Android sa leggere? */
    private fun valid(f: File): Boolean = try {
        android.graphics.Typeface.Builder(f).build() != null
    } catch (e: Exception) {
        false
    }

    /** Copia il file scelto tra i dati dell'app, dopo averlo controllato. Restituisce il nome da mostrare o null. */
    fun importFrom(ctx: Context, uri: Uri): String? {
        val tmp = File(ctx.cacheDir, "font_import.tmp")
        try {
            var total = 0L
            val input = ctx.contentResolver.openInputStream(uri) ?: return null
            input.use { ins ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) return null
                        out.write(buf, 0, n)
                    }
                }
            }
            if (total == 0L || !valid(tmp)) return null
            tmp.copyTo(customFile(ctx), overwrite = true)
            val name = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            return (name ?: "Personale").substringBeforeLast('.').take(40)
        } catch (e: Exception) {
            AppLog.quiet("importazione del carattere", e)
            return null
        } finally {
            tmp.delete()
        }
    }

    fun removeCustom(ctx: Context) {
        try { customFile(ctx).delete() } catch (e: Exception) { AppLog.quiet("rimozione del carattere", e) }
    }
}
