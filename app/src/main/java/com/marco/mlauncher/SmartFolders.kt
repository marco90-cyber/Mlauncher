package com.marco.mlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.AlarmClock
import android.provider.MediaStore

/**
 * Cartelle intelligenti "per genere preciso".
 *
 * Ordine con cui si decide la cartella di un'app (vince la prima regola che risponde):
 *  1. elenco nostro: app italiane e app Google/di sistema divise per funzione
 *  2. ruolo dell'app nel telefono (browser, email, galleria, musica, calendario, calcolatrice…)
 *  3. elenchi del Play Store presi da Lawnchair (assets/flowerpot, ~9000 app in 32 categorie)
 *  4. categoria dichiarata dall'app nel manifest (o "è un gioco")
 *  5. parole nel nome dell'app o del pacchetto (banca, meteo, treno, fit…)
 *  6. app Google rimaste senza genere -> "Google"
 * Le app che non rientrano in nessuna regola restano sciolte: nessuna cartella generica tipo "Altro".
 */
class SmartFolders(private val ctx: Context) {
    private val pm: PackageManager = ctx.packageManager

    companion object {
        const val SOCIAL = "Social"
        const val COMM = "Comunicazione"
        const val WEB = "Internet"
        const val PHOTO = "Foto"
        const val VIDEO = "Video e TV"
        const val MUSIC = "Musica e audio"
        const val GAMES = "Giochi"
        const val SHOP = "Shopping"
        const val MONEY = "Finanza"
        const val TRAVEL = "Viaggi e mappe"
        const val HEALTH = "Salute e sport"
        const val WORK = "Lavoro"
        const val TOOLS = "Strumenti"
        const val READ = "Notizie e lettura"
        const val EDU = "Istruzione"
        const val FOOD = "Cibo"
        const val STYLE = "Personalizzazione"
        const val HOME = "Casa e stile"
        const val GOOGLE = "Google"

        /** Categorie del Play Store (file in assets/flowerpot) -> nostre cartelle */
        private val PLAY = mapOf(
            "SOCIAL" to SOCIAL, "DATING" to SOCIAL,
            "COMMUNICATION" to COMM,
            "PHOTOGRAPHY" to PHOTO, "ART_AND_DESIGN" to PHOTO,
            "VIDEO" to VIDEO, "ENTERTAINMENT" to VIDEO, "COMICS" to VIDEO,
            "MUSIC" to MUSIC,
            "GAME" to GAMES,
            "SHOPPING" to SHOP,
            "FINANCE" to MONEY,
            "MAPS_AND_NAVIGATION" to TRAVEL, "AUTO_AND_VEHICLES" to TRAVEL,
            "HEALTH_AND_FITNESS" to HEALTH, "MEDICAL" to HEALTH, "SPORTS" to HEALTH,
            "PRODUCTIVITY" to WORK, "BUSINESS" to WORK,
            "TOOLS" to TOOLS, "LIBRARIES_AND_DEMO" to TOOLS, "WEATHER" to TOOLS,
            "NEWS" to READ, "BOOKS_AND_REFERENCE" to READ,
            "EDUCATION" to EDU,
            "FOOD_AND_DRINK" to FOOD,
            "PERSONALIZATION" to STYLE,
            "LIFESTYLE" to HOME, "HOUSE_AND_HOME" to HOME, "BEAUTY" to HOME, "PARENTING" to HOME, "EVENTS" to HOME
        )

        /** App note: italiane (che negli elenchi americani mancano) e app Google/di sistema per funzione. */
        private val KNOWN: Map<String, String> = buildMap {
            fun add(cat: String, vararg pk: String) = pk.forEach { put(it, cat) }
            // --- Google per funzione ---
            add(VIDEO, "com.google.android.youtube", "com.google.android.apps.youtube.kids", "com.google.android.videos", "com.google.android.apps.youtube.creator")
            add(MUSIC, "com.google.android.apps.youtube.music", "com.google.android.apps.podcasts", "com.google.android.apps.recorder")
            add(TRAVEL, "com.google.android.apps.maps", "com.google.android.apps.mapslite", "com.waze", "com.google.android.apps.travel.onthego")
            add(COMM, "com.google.android.gm", "com.google.android.apps.messaging", "com.google.android.dialer", "com.google.android.contacts",
                "com.google.android.apps.tachyon", "com.google.android.apps.meetings", "com.google.android.apps.dynamite")
            add(PHOTO, "com.google.android.apps.photos", "com.google.android.GoogleCamera", "com.google.android.apps.photosgo", "com.niksoftware.snapseed")
            add(WORK, "com.google.android.apps.docs", "com.google.android.apps.docs.editors.docs", "com.google.android.apps.docs.editors.sheets",
                "com.google.android.apps.docs.editors.slides", "com.google.android.calendar", "com.google.android.keep",
                "com.google.android.apps.tasks", "com.google.android.apps.bard", "com.google.android.apps.classroom")
            add(WEB, "com.android.chrome", "com.chrome.beta", "org.mozilla.firefox", "com.opera.browser", "com.microsoft.emmx",
                "com.brave.browser", "com.duckduckgo.mobile.android", "com.sec.android.app.sbrowser", "com.vivaldi.browser")
            add(MONEY, "com.google.android.apps.walletnfcrel")
            add(HEALTH, "com.google.android.apps.fitness", "com.fitbit.FitbitMobile")
            add(READ, "com.google.android.apps.magazines", "com.google.android.apps.books", "org.wikipedia")
            add(GAMES, "com.google.android.play.games")
            add(HOME, "com.google.android.apps.chromecast.app")
            add(TOOLS, "com.google.android.apps.translate", "com.google.android.calculator", "com.google.android.deskclock",
                "com.google.android.apps.nbu.files", "com.android.vending", "com.google.android.apps.authenticator2",
                "com.google.android.apps.adm", "com.google.ar.lens", "com.google.android.apps.subscriptions.red",
                "com.google.android.apps.googleassistant", "com.google.android.apps.safetyhub", "com.android.settings",
                "com.google.android.apps.wellbeing", "com.google.android.apps.weather")
            // --- comunicazione e social diffusi ---
            add(COMM, "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thoughtcrime.securesms", "com.facebook.orca",
                "com.viber.voip", "com.skype.raider", "us.zoom.videomeetings", "com.microsoft.teams", "com.discord",
                "com.microsoft.office.outlook", "ch.protonmail.android", "it.libero.mail", "com.yahoo.mobile.client.android.mail")
            add(SOCIAL, "com.instagram.android", "com.facebook.katana", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
                "com.twitter.android", "com.reddit.frontpage", "com.linkedin.android", "com.snapchat.android", "com.pinterest",
                "com.instagram.barcelona", "com.bereal.ft", "com.tumblr", "com.bsky.app")
            // --- Italia: banche e pagamenti ---
            add(MONEY, "com.satispay.customer", "it.pagopa.io.app", "it.poste.postepay", "posteitaliane.posteapp.appbpol",
                "posteitaliane.posteapp.appposteid", "com.latuabancaperandroid", "it.intesasanpaolo.isybank", "eu.unicredit.mobile",
                "it.bnl.apps.banking", "it.hype.app", "com.fineco.it", "it.bpm.mobile", "it.creval.mobile", "it.mediolanum.mobile",
                "com.bper.smartbanking", "it.ingdirect.app", "com.ing.mobile", "it.chebanca.app", "it.widiba.mobile", "it.bancomat.pay",
                "com.revolut.revolut", "com.paypal.android.p2pmobile", "de.number26.android", "com.bunq.android", "com.wise.android",
                "com.binance.dev", "com.coinbase.android", "com.etoro.openbook", "com.trading212", "it.agenziaentrate.app")
            // --- Italia: viaggi e trasporti ---
            add(TRAVEL, "com.lynxspa.prontotreno", "it.trenitalia.app", "com.italotreno.mobile", "it.italotreno.mobile",
                "com.flixbus.app", "com.ryanair.cheapflights", "com.mttnow.droid.easyjet", "com.ita.airways", "com.booking",
                "com.airbnb.android", "com.tripadvisor.tripadvisor", "com.ubercab", "taxi.android.client", "com.tranzmate",
                "com.citymapper.app.release", "it.telepass.mobile", "com.telepass.telepassmobile", "it.autostrade.app",
                "com.easypark.customer", "com.mycicero.app", "com.trainline.app", "com.omio.android", "com.skyscanner.android.main",
                "it.atm.appmobile", "it.atac.app", "com.enjoy.car", "com.sharenow.car", "com.helbiz", "com.limebike", "io.voiapp.voi")
            // --- Italia: shopping e cibo ---
            add(SHOP, "it.subito", "com.amazon.mShop.android.shopping", "com.ebay.mobile", "com.zalando.mobile", "com.zzkko",
                "com.einnovation.temu", "com.alibaba.aliexpresshd", "fr.vinted", "com.ingka.ikea.app", "it.esselunga.mobile",
                "it.conad.app", "it.coop.app", "com.lidl.eci.lidlplus", "it.mediaworld.app", "it.unieuro.app", "it.doveconviene.android",
                "com.shpock.android", "com.wallapop")
            add(FOOD, "com.glovo", "it.justeat.app", "com.justeat.app.it", "com.deliveroo.orderapp", "com.ubercab.eats",
                "com.lafourchette.lafourchette", "com.app.tgtg", "com.mcdonalds.mobileapp", "it.giallozafferano.app", "com.cookpad.android.activities")
            // --- Italia: video, musica, notizie ---
            add(VIDEO, "it.rai.raiplay", "it.rai.digital.yoyo", "it.mediaset.rti.mediasetplay", "it.mediaset.mediasetplay",
                "com.netflix.mediaclient", "com.disney.disneyplus", "com.amazon.avod.thirdpartyclient", "com.dazn",
                "it.sky.anywhere", "it.sky.now", "com.nowtv.it", "tv.twitch.android.app", "com.discovery.dplay", "it.timvision.app",
                "com.apple.atve.androidtv.appletv", "com.paramountplus.android", "tv.pluto.android", "org.videolan.vlc", "com.mxtech.videoplayer.ad")
            add(MUSIC, "com.spotify.music", "deezer.android.app", "com.apple.android.music", "com.shazam.android",
                "com.soundcloud.android", "com.amazon.mp3", "com.audible.application", "it.rds.app", "it.rtl1025.app", "com.radioit")
            add(READ, "it.gedi.repubblica", "it.rcs.corriere", "it.ansa.android", "it.rcs.gazzetta", "it.ilpost.app",
                "it.ilsole24ore.app", "com.google.android.apps.news", "flipboard.app", "com.amazon.kindle", "com.kobobooks.android")
            // --- lavoro, salute, istruzione ---
            add(WORK, "com.microsoft.office.word", "com.microsoft.office.excel", "com.microsoft.office.powerpoint",
                "com.microsoft.office.officehubrow", "com.microsoft.skydrive", "com.dropbox.android", "com.adobe.reader",
                "com.adobe.scan.android", "notion.id", "com.todoist", "com.evernote", "com.Slack", "com.anthropic.claude",
                "com.openai.chatgpt", "com.microsoft.copilot", "com.microsoft.todos", "com.trello", "com.microsoft.office.onenote")
            add(HEALTH, "com.strava", "com.myfitnesspal.android", "com.garmin.android.apps.connectmobile", "com.mi.health",
                "com.xiaomi.wearable", "com.huawei.health", "com.samsung.android.wear.shealth", "com.nike.plusgps", "com.adidas.app",
                "it.sanita.fse", "it.ministerodellasalute.verificaC19", "com.sleep.cycle")
            add(EDU, "com.duolingo", "com.babbel.mobile.android.en", "it.argosoft.didup.famiglia", "it.spaggiari.classeviva",
                "com.quizlet.quizletandroid", "org.khanacademy.android")
            add(TOOLS, "com.ilmeteo.android.ilmeteo", "it.ilmeteo.android", "com.meteoam.app", "com.accuweather.android",
                "com.weather.Weather", "com.ookla.speedtest", "com.lastpass.lpandroid", "com.x8bit.bitwarden", "com.authy.authy")
        }

        /** Parole chiave: ultima risorsa prima di lasciare l'app sciolta. */
        private val WORDS: List<Pair<Regex, String>> = listOf(
            Regex("\\b(bank|banca|banco|banking|wallet|crypto|bitcoin|trading|borsa|invest|finanz|pagament|payment|postepay)") to MONEY,
            Regex("\\b(meteo|weather|calculator|calcolat|clock|orologio|alarm|sveglia|torcia|flashlight|scanner|qr|vpn|backup|cleaner|file ?manager|files\\b|launcher|keyboard|tastiera|translat|tradutt|compass|bussola|gps)") to TOOLS,
            Regex("\\b(train|treno|treni|bus|taxi|flight|voli|airline|airways|metro|parking|parchegg|navigat|navi\\b|maps?\\b|mappe|travel|viaggi|hotel|autostrad)") to TRAVEL,
            Regex("\\b(camera|fotocamera|photo|foto|gallery|galleria|selfie|collage|lightroom|picsart)") to PHOTO,
            Regex("\\b(music|musica|radio|podcast|audio|player mp3|equali[sz]er|fm\\b)") to MUSIC,
            Regex("\\b(video|tv\\b|movie|film|cinema|stream|anime)") to VIDEO,
            Regex("\\b(health|salute|fitness|fit\\b|workout|allenament|running|corsa|step|passi|sleep|sonno|medic|pharma|farmacia|sanit|yoga|diet)") to HEALTH,
            Regex("\\b(food|ricett|recipe|pizza|cucina|delivery|ristorant|restaurant)") to FOOD,
            Regex("\\b(shop|shopping|store\\b|coupon|offert|volantin|outlet|fashion|moda)") to SHOP,
            Regex("\\b(game|games|gioco|giochi|puzzle|chess|scacchi|solitaire|solitario|sudoku|casino)") to GAMES,
            Regex("\\b(news|notizie|giornale|quotidiano|magazine|rivista|ebook|reader|books?|libri|dizionar|dictionary)") to READ,
            Regex("\\b(mail|email|messag|sms|chat|dialer|phone|telefono|contacts?|rubrica|call|meet)") to COMM,
            Regex("\\b(social|dating|community|forum)") to SOCIAL,
            Regex("\\b(office|docs?\\b|pdf|notes?\\b|note\\b|task|todo|calendar|calendario|drive|cloud|gpt|assistant)") to WORK,
            Regex("\\b(learn|school|scuola|edu|study|studio|corso|quiz|lingu)") to EDU,
            Regex("\\b(wallpaper|sfondi|icon ?pack|theme|temi|widget)") to STYLE,
            Regex("\\b(home|casa|smart ?home|domotic|beauty|baby|bambin)") to HOME
        )
    }

    private var playMap: Map<String, String>? = null
    private var roleMap: Map<String, String>? = null

    /** Da chiamare fuori dal thread principale: legge gli elenchi e interroga il sistema. */
    fun prepare() {
        if (playMap == null) playMap = loadPlay()
        roleMap = loadRoles()
    }

    private fun loadPlay(): Map<String, String> {
        val out = HashMap<String, String>()
        val actions = ArrayList<Pair<Intent, String>>()
        try {
            val files = ctx.assets.list("flowerpot") ?: emptyArray()
            for (f in files) {
                val cat = PLAY[f] ?: continue
                ctx.assets.open("flowerpot/$f").bufferedReader().useLines { lines ->
                    lines.forEach { raw ->
                        val l = raw.trim()
                        if (l.isEmpty()) return@forEach
                        when (l[0]) {
                            '#', '$', '&' -> Unit
                            ':' -> actions.add(Intent(l.substring(1)) to cat)
                            ';' -> actions.add(Intent(Intent.ACTION_MAIN).addCategory(l.substring(1)) to cat)
                            else -> if (l[0].isLetter()) out.putIfAbsent(l, cat)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.e("Elenchi delle categorie non leggibili", e)
        }
        // azioni dichiarate negli elenchi (es. pacchetti di icone -> Personalizzazione)
        for ((intent, cat) in actions) {
            try {
                pm.queryIntentActivities(intent, 0).forEach { out.putIfAbsent(it.activityInfo.packageName, cat) }
            } catch (e: Exception) { }
        }
        return out
    }

    /** Ruolo delle app nel telefono: chi fa da browser, galleria, email… */
    private fun loadRoles(): Map<String, String> {
        val out = HashMap<String, String>()
        fun cat(c: String, folder: String) = try {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(c), 0)
                .forEach { out.putIfAbsent(it.activityInfo.packageName, folder) }
        } catch (e: Exception) { }
        fun act(i: Intent, folder: String) = try {
            pm.queryIntentActivities(i, 0).forEach { out.putIfAbsent(it.activityInfo.packageName, folder) }
        } catch (e: Exception) { }
        cat(Intent.CATEGORY_APP_BROWSER, WEB)
        cat(Intent.CATEGORY_APP_EMAIL, COMM)
        cat(Intent.CATEGORY_APP_MESSAGING, COMM)
        cat(Intent.CATEGORY_APP_CONTACTS, COMM)
        act(Intent(Intent.ACTION_DIAL), COMM)
        cat(Intent.CATEGORY_APP_GALLERY, PHOTO)
        act(Intent(MediaStore.ACTION_IMAGE_CAPTURE), PHOTO)
        act(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), PHOTO)
        cat(Intent.CATEGORY_APP_MUSIC, MUSIC)
        cat(Intent.CATEGORY_APP_CALENDAR, WORK)
        cat(Intent.CATEGORY_APP_MAPS, TRAVEL)
        cat(Intent.CATEGORY_APP_FITNESS, HEALTH)
        cat(Intent.CATEGORY_APP_WEATHER, TOOLS)
        cat(Intent.CATEGORY_APP_CALCULATOR, TOOLS)
        cat(Intent.CATEGORY_APP_FILES, TOOLS)
        act(Intent(AlarmClock.ACTION_SET_ALARM), TOOLS)
        cat(Intent.CATEGORY_APP_MARKET, TOOLS)
        return out
    }

    /** Cartella "per genere preciso" di un'app, o null se va lasciata sciolta. */
    fun folderOf(a: AppEntry): String? {
        KNOWN[a.pkg]?.let { return it }
        roleMap?.get(a.pkg)?.let { return it }
        playMap?.get(a.pkg)?.let { return it }
        val info = try { pm.getApplicationInfo(a.pkg, 0) } catch (e: Exception) { null }
        if (info != null) {
            if (info.flags and ApplicationInfo.FLAG_IS_GAME != 0) return GAMES
            when (info.category) {
                ApplicationInfo.CATEGORY_GAME -> return GAMES
                ApplicationInfo.CATEGORY_AUDIO -> return MUSIC
                ApplicationInfo.CATEGORY_VIDEO -> return VIDEO
                ApplicationInfo.CATEGORY_IMAGE -> return PHOTO
                ApplicationInfo.CATEGORY_SOCIAL -> return SOCIAL
                ApplicationInfo.CATEGORY_NEWS -> return READ
                ApplicationInfo.CATEGORY_MAPS -> return TRAVEL
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> return WORK
                ApplicationInfo.CATEGORY_ACCESSIBILITY -> return TOOLS
            }
        }
        val hay = (a.label + " " + a.pkg.replace('.', ' ').replace('_', ' ')).lowercase()
        for ((re, folder) in WORDS) if (re.containsMatchIn(hay)) return folder
        if (a.pkg.startsWith("com.google.")) return GOOGLE
        return null
    }
}

/** Cartella per colore dominante dell'icona. */
fun colorFolderOf(icon: android.graphics.Bitmap): String {
    val sm = android.graphics.Bitmap.createScaledBitmap(icon, 12, 12, true)
    val hsv = FloatArray(3)
    var sx = 0.0
    var sy = 0.0
    var w = 0.0
    var n = 0
    for (y in 0 until 12) for (x in 0 until 12) {
        val c = sm.getPixel(x, y)
        if (android.graphics.Color.alpha(c) < 60) continue
        android.graphics.Color.colorToHSV(c, hsv)
        n++
        // pesano i pixel colorati e luminosi; bianchi, grigi e neri quasi nulla
        val wt = (hsv[1] * hsv[2]).toDouble()
        if (wt < 0.12) continue
        sx += kotlin.math.cos(Math.toRadians(hsv[0].toDouble())) * wt
        sy += kotlin.math.sin(Math.toRadians(hsv[0].toDouble())) * wt
        w += wt
    }
    if (n == 0 || w < n * 0.10) return "Bianco e nero"
    var h = Math.toDegrees(kotlin.math.atan2(sy, sx))
    if (h < 0) h += 360.0
    return when {
        h < 15 || h >= 345 -> "Rosso"
        h < 40 -> "Arancione"
        h < 68 -> "Giallo"
        h < 165 -> "Verde"
        h < 200 -> "Azzurro"
        h < 255 -> "Blu"
        h < 290 -> "Viola"
        else -> "Rosa"
    }
}
