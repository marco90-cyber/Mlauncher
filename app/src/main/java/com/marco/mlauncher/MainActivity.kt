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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        window.isNavigationBarContrastEnforced = false
        setContent {
            MLauncherTheme(vm.settings.theme) {
                Root(vm, this@MainActivity)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateDefault()
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
}
