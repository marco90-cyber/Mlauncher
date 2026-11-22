package com.marco.mlauncher

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/*
 * Ricerca app: punteggio per pertinenza (esatto > inizio nome > parola > sinonimo > iniziali > contiene > pacchetto
 * > errore di battitura). Ignora maiuscole e accenti. Adattato dal SearchScorer di Lawnchair Lite
 * (https://github.com/SysAdminDoc/Lawnchair-Lite), licenza MIT, Copyright (c) 2026 SysAdminDoc:
 * permesso di usare, copiare, modificare e distribuire a patto di mantenere questo avviso.
 * Modifiche nostre: sinonimi anche in italiano, meno gruppi "semantici" in inglese, niente traslitterazione cirillico/greco.
 */
object AppSearch {
    private const val ALIAS_SCORE = 85
    private const val SEMANTIC_SCORE = 82
    private const val SEMANTIC_PACKAGE_SCORE = 62
    private const val INITIALS_SCORE = 75
    private const val ALIAS_CONTAINS_SCORE = 68
    private const val FUZZY_SCORE = 55

    private val tokenRegex = Regex("[a-z0-9]+")
    private val combiningMarksRegex = Regex("\\p{Mn}+")
    private val whitespaceRegex = Regex("\\s+")

    private val aliasGroups = listOf(
        setOf("sms", "messages", "messaging", "messaggi", "messaggio", "text", "texts"),
        setOf("phone", "dialer", "call", "calls", "telefono", "chiamate", "chiamata", "contatti", "rubrica"),
        setOf("browser", "web", "internet", "chrome", "firefox", "edge", "brave", "navigatore web"),
        setOf("calendar", "agenda", "events", "calendario", "eventi"),
        setOf("clock", "alarm", "alarms", "timer", "stopwatch", "orologio", "sveglia", "cronometro"),
        setOf("files", "file manager", "documents", "storage", "file", "documenti", "archivio", "gestore file"),
        setOf("mail", "email", "gmail", "outlook", "posta"),
        setOf("maps", "map", "navigation", "gps", "mappe", "mappa", "navigatore"),
        setOf("photos", "gallery", "images", "pictures", "foto", "galleria", "immagini"),
        setOf("music", "audio", "songs", "player", "musica", "canzoni", "lettore"),
        setOf("store", "market", "play store", "negozio"),
        setOf("settings", "preferences", "config", "impostazioni", "preferenze", "opzioni"),
        setOf("calculator", "calc", "calcolatrice", "calcolatore"),
        setOf("camera", "selfie", "fotocamera", "fotografia"),
    )

    private val semanticFillerTokens = setOf("app", "apps", "applicazione", "apri", "open", "launch", "il", "la", "my", "the")

    private class SemanticGroup(val queryTerms: Set<String>, val appHints: Set<String>) {
        fun matchesLabel(label: Norm) = appHints.any { h ->
            label.tokens.any { it == h || it.startsWith(h) } || label.compact.contains(h)
        }
        fun matchesPackage(pkg: Norm) = appHints.any { h ->
            pkg.tokens.any { it == h || it.startsWith(h) } || pkg.compact.contains(h)
        }
    }

    private val semanticGroups = listOf(
        SemanticGroup(
            setOf("password", "passwords", "vault", "login", "credenziali", "2fa", "totp", "autenticatore"),
            setOf("bitwarden", "onepassword", "1password", "lastpass", "protonpass", "dashlane", "authy", "aegis", "authenticator")
        ),
        SemanticGroup(
            setOf("soldi", "banca", "pagamento", "pagamenti", "portafoglio", "money", "bank", "pay", "wallet"),
            setOf("paypal", "satispay", "wallet", "bank", "banca", "revolut", "postepay", "pay", "bancoposta")
        ),
        SemanticGroup(
            setOf("vpn", "privacy", "sicurezza", "security"),
            setOf("vpn", "protonvpn", "mullvad", "nord", "expressvpn", "security", "shield")
        ),
        SemanticGroup(
            setOf("fitness", "allenamento", "palestra", "gym", "workout", "corsa"),
            setOf("fit", "fitness", "workout", "gym", "training", "health", "strava", "garmin", "salute")
        )
    )

    private class Norm(val text: String, val tokens: List<String>, val compact: String, val initials: String)

    private fun text(raw: String): Norm {
        val tokens = tokenRegex.findAll(normalize(raw)).map { it.value }.toList()
        return Norm(
            tokens.joinToString(" "),
            tokens,
            tokens.joinToString(""),
            tokens.mapNotNull { it.firstOrNull() }.joinToString("")
        )
    }

    private val aliasLookup: Map<String, Set<String>> = buildMap {
        aliasGroups.forEach { group ->
            val g = group.map { text(it).text }.filter { it.isNotBlank() }.toSet()
            g.forEach { a ->
                put(a, g - a)
                put(text(a).compact, g - a)
            }
        }
    }

    /** Pertinenza di un'app per la ricerca [query]: 0 = non corrisponde, 100 = nome identico. */
    fun score(label: String, packageName: String, query: String): Int {
        val q = text(query)
        if (q.text.isBlank()) return 0
        val l = text(label)
        val p = text(packageName)
        val alias = aliasScore(l, p, q)
        val semantic = semanticScore(l, p, q)
        return when {
            l.text == q.text -> 100
            l.text.startsWith(q.text) || l.compact.startsWith(q.compact) -> 90
            l.tokens.any { it.startsWith(q.compact) } -> 80
            semantic != null -> semantic
            alias != null -> alias
            l.initials == q.compact || (q.compact.length > 1 && l.initials.startsWith(q.compact)) -> INITIALS_SCORE
            l.text.contains(q.text) || l.compact.contains(q.compact) -> 70
            p.text.contains(q.text) || p.compact.contains(q.compact) -> 60
            fuzzy(q, l) -> FUZZY_SCORE
            isSubsequence(q.compact, l.compact) -> 50
            else -> 0
        }
    }

    /** Filtra e ordina per pertinenza (a parità resta l'ordine di partenza). */
    fun filter(apps: List<AppEntry>, query: String): List<AppEntry> {
        if (query.isBlank()) return apps
        return apps.map { it to score(it.label, it.pkg, query) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun semanticScore(label: Norm, pkg: Norm, query: Norm): Int? {
        val meaningful = query.tokens.filterNot { it in semanticFillerTokens }.toSet()
        if (meaningful.isEmpty()) return null
        val groups = semanticGroups.filter { g -> meaningful.any { it in g.queryTerms } }
        if (groups.isEmpty()) return null
        return when {
            groups.any { it.matchesLabel(label) } -> SEMANTIC_SCORE
            groups.any { it.matchesPackage(pkg) } -> SEMANTIC_PACKAGE_SCORE
            else -> null
        }
    }

    private fun aliasScore(label: Norm, pkg: Norm, query: Norm): Int? {
        val aliases = (aliasLookup[query.text].orEmpty() + aliasLookup[query.compact].orEmpty())
            .map { text(it) }.filter { it.text.isNotBlank() }
        if (aliases.isEmpty()) return null
        return when {
            aliases.any { a -> label.text == a.text || label.compact == a.compact } -> ALIAS_SCORE
            aliases.any { a -> label.text.startsWith(a.text) || label.tokens.any { it.startsWith(a.compact) } } -> ALIAS_SCORE
            aliases.any { a -> label.text.contains(a.text) || label.compact.contains(a.compact) } -> ALIAS_CONTAINS_SCORE
            aliases.any { a -> pkg.text.contains(a.text) || pkg.compact.contains(a.compact) } -> 60
            else -> null
        }
    }

    private fun fuzzy(query: Norm, label: Norm): Boolean {
        if (query.compact.length < 4) return false
        if (query.tokens.isNotEmpty() && query.tokens.all { qt ->
                qt.length >= 3 && label.tokens.any { lt -> fuzzyToken(qt, lt) }
            }) return true
        val maxD = allowedDistance(query.compact.length, label.compact.length)
        if (maxD > 0 && withinDistance(query.compact, label.compact, maxD)) return true
        val prefix = label.compact.take(min(label.compact.length, query.compact.length + maxD))
        return prefix.length >= query.compact.length - maxD && withinDistance(query.compact, prefix, maxD)
    }

    private fun fuzzyToken(q: String, l: String): Boolean {
        if (l.startsWith(q)) return true
        val d = allowedDistance(q.length, l.length)
        return d > 0 && withinDistance(q, l, d)
    }

    private fun allowedDistance(a: Int, b: Int): Int {
        val shorter = min(a, b)
        val longer = max(a, b)
        return when {
            shorter < 4 -> 0
            longer <= 5 -> 1
            longer <= 9 -> 2
            else -> 3
        }
    }

    /** Distanza di modifica (Levenshtein) minore o uguale a [maxD], con uscita anticipata. */
    private fun withinDistance(a: String, b: String, maxD: Int): Boolean {
        if (abs(a.length - b.length) > maxD) return false
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                rowMin = min(rowMin, cur[j])
            }
            if (rowMin > maxD) return false
            val swap = prev
            prev = cur
            cur = swap
        }
        return prev[b.length] <= maxD
    }

    private fun isSubsequence(query: String, text: String): Boolean {
        if (query.isBlank()) return false
        var qi = 0
        for (ch in text) if (qi < query.length && ch == query[qi]) qi++
        return qi == query.length
    }

    private fun normalize(raw: String): String {
        val t = buildString {
            raw.forEach {
                append(
                    when (it.lowercaseChar()) {
                        'æ' -> "ae"
                        'œ' -> "oe"
                        'ß' -> "ss"
                        'ø' -> "o"
                        'ł' -> "l"
                        'đ', 'ð' -> "d"
                        else -> it.toString()
                    }
                )
            }
        }
        return Normalizer.normalize(t, Normalizer.Form.NFKD)
            .replace(combiningMarksRegex, "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(whitespaceRegex, " ")
    }
}
