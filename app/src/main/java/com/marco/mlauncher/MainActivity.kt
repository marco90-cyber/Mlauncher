package com.marco.mlauncher

import android.app.ActivityOptions
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.ui.geometry.Rect
import android.Manifest
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.pm.PackageManager
import android.provider.AlarmClock

class MainActivity : ComponentActivity(), Host {

    private val vm: LauncherViewModel by viewModels()

    private val uninstallLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        if (r.resultCode == RESULT_OK) vm.uninstallSucceeded() else vm.uninstallCancelled()
    }

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { updateDefault() }

    private val locationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { vm.checkLocationPermission(); vm.refreshWeather(true) }

    private val contactsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { vm.loadAutoName() }

    override fun requestContactsName() {
        if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            vm.loadAutoName()
        } else {
            try { contactsLauncher.launch(android.Manifest.permission.READ_CONTACTS) } catch (e: Exception) { }
        }
    }

    private val btLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val bindLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        restorePending()
        val info = pendingInfo
        val id = pendingWidgetId
        AppLog.i("Widget: permesso di collegamento -> " + if (r.resultCode == RESULT_OK) "concesso" else "negato")
        if (info != null && id != -1) {
            if (r.resultCode == RESULT_OK) configureOrFinish(id, info) else cancelPending()
        }
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(vm.buildBackup().toByteArray()) }
                Toast.makeText(this, "Backup salvato", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Backup non riuscito", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val text = contentResolver.openInputStream(uri)?.use { String(it.readBytes()) } ?: ""
                val ok = vm.restoreBackup(text)
                Toast.makeText(this, if (ok) "Ripristinato" else "File non valido", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Ripristino non riuscito", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val exportLayoutLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(vm.buildLayoutBackup().toByteArray()) }
                Toast.makeText(this, "Disposizione salvata", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Salvataggio non riuscito", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val importLayoutLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val text = contentResolver.openInputStream(uri)?.use { String(it.readBytes()) } ?: ""
                val lost = vm.restoreLayout(text)
                Toast.makeText(
                    this,
                    when {
                        lost < 0 -> "File non valido"
                        lost == 0 -> "Disposizione ripristinata"
                        lost == 1 -> "Disposizione ripristinata (1 widget non disponibile)"
                        else -> "Disposizione ripristinata ($lost widget non disponibili)"
                    },
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Ripristino non riuscito", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun exportLayout() {
        val d = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ITALY).format(java.util.Date())
        exportLayoutLauncher.launch("mlauncher-home-$d.json")
    }

    override fun importLayout() {
        importLayoutLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
    }

    /** Cartella del backup automatico: il permesso resta anche dopo il riavvio. */
    private val folderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                vm.setSetting("backupDir", uri.toString())
                vm.runAutoBackup(force = true)
            } catch (e: Exception) {
                AppLog.e("Cartella del backup non utilizzabile", e)
                Toast.makeText(this, "Cartella non utilizzabile", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Foto per lo sfondo di MLauncher (selettore di Android, senza permessi). */
    private val wallLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) vm.pickedWallpaper(uri) }

    private var pendingWidgetId = -1
    private var pendingInfo: AppWidgetProviderInfo? = null
    /** quando è stata aperta la configurazione del widget (per riconoscere le ROM che la aprono a parte) */
    private var configOpenedAt = 0L
    private var waitConfigReturn = false

    /** Il widget in attesa resta salvato: se Android chiude MLauncher durante la configurazione, non si perde. */
    private val pendingSp by lazy { getSharedPreferences("pending_widget", MODE_PRIVATE) }

    private fun savePending() {
        pendingSp.edit()
            .putInt("id", pendingWidgetId)
            .putBoolean("minus", vm.minusPick)
            .putString("stack", vm.stackTarget)
            .apply()
    }

    private fun restorePending() {
        if (pendingWidgetId != -1 && pendingInfo != null) return
        val id = pendingSp.getInt("id", -1)
        if (id == -1) return
        val info = try { vm.widgetMgr.getAppWidgetInfo(id) } catch (e: Exception) { null }
        if (info == null) {
            pendingSp.edit().clear().apply()
            return
        }
        pendingWidgetId = id
        pendingInfo = info
        vm.minusPick = pendingSp.getBoolean("minus", false)
        vm.stackTarget = pendingSp.getString("stack", null)
        AppLog.i("Widget in attesa recuperato dopo il riavvio del Launcher (id $id)")
    }

    /** Cambia layout prima che la Home venga ridisegnata, così non c'è un frame con la griglia sbagliata. */
    private fun syncOrientation(c: android.content.res.Configuration) {
        val land = c.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        vm.settings.actualLand = land
        vm.syncOrientation(land)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        syncOrientation(newConfig)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        window.isNavigationBarContrastEnforced = false
        syncOrientation(resources.configuration)
        try {
            registerReceiver(unlockReceiver, android.content.IntentFilter(Intent.ACTION_USER_PRESENT))
        } catch (e: Exception) { }
        vm.checkUpdate(auto = true)
        vm.loadCrash()
        AppLog.i("Launcher avviato" + if (savedInstanceState != null) " (ricreato)" else "")
        setContent {
            MLauncherTheme(vm.settings.theme, vm.settings.accent, if (vm.customWallActive) vm.wallSeed else null) {
                Root(vm, this@MainActivity)
                AppUpdateHost()
                val hideSb = vm.settings.hideStatusBar
                androidx.compose.runtime.LaunchedEffect(hideSb) { applyStatusBar(hideSb) }
            }
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(unlockReceiver) } catch (e: Exception) { }
        super.onDestroy()
    }

    /** il Launcher è andato in secondo piano perché lo schermo si è spento */
    private var stoppedByScreenOff = false
    private var wasStopped = false
    private var stoppedAt = 0L
    /** un'app è stata aperta dal Launcher dopo l'ultimo ritorno in primo piano */
    private var launchedSinceStart = false

    /** Sblocco del telefono: animazione di ritorno se la Home è quella che si vede. */
    private val unlockReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context?, i: Intent?) {
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) vm.playReturn(fromApp = false)
        }
    }

    override fun onStart() {
        super.onStart()
        // tornato da un'app (non dallo schermo spento): animazione di ritorno
        // (un gesto Recenti fatto e annullato sulla Home ferma il Launcher per un attimo:
        //  lì niente animazione, altrimenti si sovrappone a quella del sistema)
        val away = android.os.SystemClock.uptimeMillis() - stoppedAt
        if (wasStopped && !stoppedByScreenOff && (launchedSinceStart || away > 1500)) vm.playReturn(fromApp = true)
        wasStopped = false
        launchedSinceStart = false
        try { vm.widgetHost.startListening() } catch (e: Exception) { AppLog.e("Widget host non avviato", e) }
        AppLog.i("Home in primo piano")
    }

    override fun onStop() {
        wasStopped = true
        stoppedAt = android.os.SystemClock.uptimeMillis()
        stoppedByScreenOff = try {
            !getSystemService(android.os.PowerManager::class.java).isInteractive
        } catch (e: Exception) {
            false
        }
        try { vm.widgetHost.stopListening() } catch (e: Exception) { }
        AppLog.i("Home in background")
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        applyStatusBar(vm.settings.hideStatusBar)
        // configurazione aperta a parte: al ritorno il widget si aggiunge
        if (waitConfigReturn && android.os.SystemClock.uptimeMillis() - configOpenedAt > 1500L) {
            val info = pendingInfo
            val id = pendingWidgetId
            if (info != null && id != -1) finishPending(id, info) else waitConfigReturn = false
        }
        updateDefault()
        vm.checkLocationPermission()
        vm.readWallpaperHint()
        vm.runAutoBackup()
        vm.refreshWeather()
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        vm.listenerEnabled = enabled.contains(packageName)
        val acc = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        vm.accessibilityEnabled = acc.contains(packageName)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CONFIGURE) {
            restorePending()
            val info = pendingInfo
            val id = pendingWidgetId
            val elapsed = android.os.SystemClock.uptimeMillis() - configOpenedAt
            AppLog.i("Widget: configurazione chiusa -> " + (if (resultCode == RESULT_OK) "fatto" else "annullato") + " dopo ${elapsed} ms")
            if (info != null && id != -1) {
                when {
                    resultCode == RESULT_OK -> finishPending(id, info)
                    // risposta "annullato" immediata: alcune ROM aprono la configurazione in un'altra finestra
                    // e rispondono subito; il widget si aggiunge quando torni in MLauncher
                    configOpenedAt > 0L && elapsed < 1500L -> {
                        waitConfigReturn = true
                        AppLog.i("Widget: configurazione aperta a parte, lo aggiungo al ritorno")
                    }
                    else -> cancelPending()
                }
            }
        }
    }

    private fun cancelPending() {
        if (pendingWidgetId != -1) vm.discardWidgetId(pendingWidgetId)
        pendingWidgetId = -1
        pendingInfo = null
        waitConfigReturn = false
        configOpenedAt = 0L
        pendingSp.edit().clear().apply()
    }

    private fun finishPending(id: Int, info: AppWidgetProviderInfo) {
        vm.addExternalWidget(id, info)
        AppLog.i("Widget aggiunto: ${info.provider.flattenToShortString()}")
        pendingWidgetId = -1
        pendingInfo = null
        waitConfigReturn = false
        configOpenedAt = 0L
        pendingSp.edit().clear().apply()
    }

    private fun configureOrFinish(id: Int, info: AppWidgetProviderInfo) {
        if (info.configure != null) {
            try {
                configOpenedAt = android.os.SystemClock.uptimeMillis()
                AppLog.i("Widget: apro la configurazione di ${info.provider.flattenToShortString()}")
                vm.widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIGURE, null)
            } catch (e: Exception) {
                AppLog.e("Widget: configurazione non avviata", e)
                cancelPending()
            }
        } else {
            finishPending(id, info)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            // come Launcher3: conta come "già in Home" solo se la finestra aveva il focus
            vm.onHomePressed(alreadyHome = hasWindowFocus())
        }
    }

    private fun updateDefault() {
        val rm = getSystemService(RoleManager::class.java)
        vm.isDefaultHome = rm != null && rm.isRoleHeld(RoleManager.ROLE_HOME)
    }

    // ---------- Host ----------

    /** Esegue un'azione di MLauncher (icona "azione" in Home). */
    private fun runAction(spec: String) {
        AppLog.i("Azione $spec")
        val key = spec.substringBefore(":")
        when (key) {
            "drawer" -> { vm.openFolderId = null; vm.overlay = Overlay.DRAWER }
            "search" -> { vm.openFolderId = null; vm.searchFocus = true; vm.overlay = Overlay.DRAWER }
            "notif" -> expandNotifications()
            "qs" -> try {
                Class.forName("android.app.StatusBarManager").getMethod("expandSettingsPanel").invoke(getSystemService("statusbar"))
            } catch (e: Exception) {
                Toast.makeText(this, "Non posso aprire le impostazioni rapide da qui", Toast.LENGTH_SHORT).show()
            }
            "recents" -> {
                val svc = MLAccessibilityService.instance
                if (svc == null) Toast.makeText(this, "Attiva il servizio di accessibilità di MLauncher", Toast.LENGTH_SHORT).show()
                else svc.recents()
            }
            "lock" -> lockScreen()
            "statusbar" -> vm.setSetting("hideStatusBar", !vm.settings.hideStatusBar)
            "dock" -> vm.setSetting("hideDock", !vm.settings.hideDock)
            "home" -> vm.goToPage(0)
            "page" -> vm.goToPage((spec.substringAfter(":").toIntOrNull() ?: 1) - 1)
            "overview" -> vm.showOverview = true
        }
    }

    /** Barra di stato nascosta con l'azione "Barra di stato" (si rivede scorrendo dall'alto). */
    private fun applyStatusBar(hide: Boolean) {
        val c = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        if (hide) {
            c.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        } else c.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
    }

    override fun launch(app: AppEntry, bounds: Rect?) {
        if (app.pkg == packageName && app.isAction) {
            runAction(app.cls.removePrefix(ACTION_PREFIX))
            return
        }
        if (app.pkg == packageName && app.cls == SETTINGS_CLS) {
            vm.openFolderId = null
            vm.overlay = Overlay.SETTINGS
            return
        }
        vm.recordLaunch(app.id)
        vm.noteLaunched(app.id)
        launchedSinceStart = true
        AppLog.i("Apro ${app.label} (${app.pkg})")
        val i = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(app.pkg, app.cls))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            val opts = if (bounds != null && bounds.width > 0f) {
                ActivityOptions.makeScaleUpAnimation(
                    window.decorView,
                    bounds.left.toInt(), bounds.top.toInt(),
                    bounds.width.toInt(), bounds.height.toInt()
                ).toBundle()
            } else null
            startActivity(i, opts)
        } catch (e: Exception) {
            AppLog.e("Impossibile aprire ${app.pkg}", e)
            Toast.makeText(this, "Impossibile aprire ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun uninstall(app: AppEntry) {
        val i = Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:${app.pkg}"))
            .putExtra(Intent.EXTRA_RETURN_RESULT, true)
        vm.beginUninstall(app)
        try {
            uninstallLauncher.launch(i)
        } catch (e: Exception) {
            vm.uninstallCancelled()
        }
    }

    override fun appInfo(app: AppEntry) {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            // niente
        }
    }

    override fun expandNotifications() {
        try {
            val sb = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel")
                .invoke(sb)
        } catch (e: Exception) {
            Toast.makeText(this, "Non posso aprire le notifiche da qui", Toast.LENGTH_SHORT).show()
        }
    }

    override fun requestHomeRole() {
        val rm = getSystemService(RoleManager::class.java)
        try {
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
            } else {
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            }
        } catch (e: Exception) {
            // niente
        }
    }

    override fun pickWallpaper() {
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Sfondo"))
        } catch (e: Exception) {
            // niente
        }
    }

    override fun requestLocation() {
        locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    override fun addWidget(info: AppWidgetProviderInfo) {
        cancelPending()
        val id = vm.widgetHost.allocateAppWidgetId()
        pendingWidgetId = id
        pendingInfo = info
        savePending()
        AppLog.i("Widget: aggiungo ${info.provider.flattenToShortString()} (id $id)")
        if (vm.widgetMgr.bindAppWidgetIdIfAllowed(id, info.provider)) {
            configureOrFinish(id, info)
        } else {
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            try {
                bindLauncher.launch(i)
            } catch (e: Exception) {
                cancelPending()
            }
        }
    }

    override fun exportBackup() {
        exportLauncher.launch("mlauncher-backup.json")
    }

    override fun pickWallpaperImage() {
        try {
            wallLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (e: Exception) {
            Toast.makeText(this, "Nessuna galleria disponibile", Toast.LENGTH_SHORT).show()
        }
    }

    override fun pickBackupFolder() {
        try {
            folderLauncher.launch(null)
        } catch (e: Exception) {
            Toast.makeText(this, "Nessuna app per scegliere le cartelle", Toast.LENGTH_SHORT).show()
        }
    }

    override fun importBackup() {
        importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
    }

    override fun openListenerSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (e: Exception) { }
    }

    override fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) { }
    }

    override fun restartLauncher() {
        val i = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(i)
        finishAffinity()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    override fun lockScreen(): Boolean {
        val svc = MLAccessibilityService.instance
        if (svc == null) {
            Toast.makeText(this, "Attiva il servizio di accessibilità di MLauncher", Toast.LENGTH_SHORT).show()
            return false
        }
        return svc.lock()
    }

    override fun webSearch(query: String) {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) { }
    }

    override fun requestBluetooth() {
        btLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
    }

    override fun openClockApp() {
        try {
            startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { }
    }

    companion object {
        private const val REQ_CONFIGURE = 7001
    }
}
