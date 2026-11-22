package com.marco.mlauncher

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

/**
 * Effetto profondità: inclinando il telefono lo sfondo si muove di più e le icone un po' meno
 * in senso opposto, come se fossero a livelli diversi. Il sensore è acceso solo con l'impostazione attiva.
 */
@Composable
fun TiltSensor(vm: LauncherViewModel) {
    val ctx = LocalContext.current
    val on = vm.settings.tilt && vm.overlay == Overlay.NONE
    DisposableEffect(on) {
        if (!on) {
            vm.tiltX = 0f
            vm.tiltY = 0f
            return@DisposableEffect onDispose { }
        }
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) return@DisposableEffect onDispose { }
        val l = object : SensorEventListener {
            // posizione "di riposo" che si aggiorna piano: conta solo quanto inclini rispetto a come tieni il telefono
            var baseX = Float.NaN
            var baseY = 0f
            override fun onSensorChanged(e: SensorEvent) {
                val rx = -e.values[0] / 9.81f
                val ry = e.values[1] / 9.81f
                if (baseX.isNaN()) { baseX = rx; baseY = ry }
                baseX += (rx - baseX) * 0.01f
                baseY += (ry - baseY) * 0.01f
                val tx = ((rx - baseX) / 0.35f).coerceIn(-1f, 1f)
                val ty = ((ry - baseY) / 0.35f).coerceIn(-1f, 1f)
                vm.tiltX += (tx - vm.tiltX) * 0.15f
                vm.tiltY += (ty - vm.tiltY) * 0.15f
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose {
            sm.unregisterListener(l)
            vm.tiltX = 0f
            vm.tiltY = 0f
        }
    }
}

/** Sfondo: ingrandito un po' (così non si vedono i bordi) e spostato dall'inclinazione. */
fun Modifier.tiltWall(vm: LauncherViewModel): Modifier = this.graphicsLayer {
    if (!vm.settings.tilt) return@graphicsLayer
    scaleX = 1.1f
    scaleY = 1.1f
    translationX = vm.tiltX * size.width * 0.04f
    translationY = vm.tiltY * size.height * 0.03f
}

/** Icone: si muovono poco e in senso opposto allo sfondo. Ferme durante la modifica della Home. */
fun Modifier.tiltIcons(vm: LauncherViewModel): Modifier = this.graphicsLayer {
    if (!vm.settings.tilt || vm.editMode) return@graphicsLayer
    translationX = -vm.tiltX * 7.dp.toPx()
    translationY = -vm.tiltY * 5.dp.toPx()
}
