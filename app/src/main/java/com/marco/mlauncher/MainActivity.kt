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
        val info = pendingInfo
        val id = pendingWidgetId
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
            val info = pendingInfo
            val id = pendingWidgetId
            if (info != null && id != -1) {
                if (resultCode == RESULT_OK) finishPending(id, info) else cancelPending()
            }
        }
    }

    private fun cancelPending() {
        if (pendingWidgetId != -1) vm.discardWidgetId(pendingWidgetId)
        pendingWidgetId = -1
        pendingInfo = null
    }

    private fun finishPending(id: Int, info: AppWidgetProviderInfo) {
        vm.addExternalWidget(id, info)
        pendingWidgetId = -1
        pendingInfo = null
    }

    private fun configureOrFinish(id: Int, info: AppWidgetProviderInfo) {
        if (info.configure != null) {
            try {
                vm.widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIGURE, null)
            } catch (e: Exception) {
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

    override fun launch(app: AppEntry, bounds: Rect?) {
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
