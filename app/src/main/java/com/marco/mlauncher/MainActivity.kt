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
        setContent {
            MLauncherTheme(vm.settings.theme) {
                Root(vm, this@MainActivity)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        try { vm.widgetHost.startListening() } catch (e: Exception) { }
    }

    override fun onStop() {
        try { vm.widgetHost.stopListening() } catch (e: Exception) { }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        updateDefault()
        vm.checkLocationPermission()
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
            vm.onHomePressed()
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
