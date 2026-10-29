package com.marco.mlauncher

import android.graphics.Path
import android.view.animation.PathInterpolator
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * Curve e durate prese da Lawnchair / Launcher3 (Interpolators.java, config.xml,
 * FolderAnimationManager). Tutte le animazioni dell'app usano questi valori.
 */
object Motion {
    /** Material 3 "emphasized": stessa curva a due segmenti usata da Launcher3. */
    val Emphasized: Easing = run {
        val path = Path().apply {
            moveTo(0f, 0f)
            cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
            cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
        }
        val i = PathInterpolator(path)
        Easing { f -> i.getInterpolation(f) }
    }
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val StandardAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)
    val StandardDecelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)
    val FastOutSlowIn: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    val TouchResponse: Easing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)

    // durate in ms (config.xml di Lawnchair)
    const val FOLDER = 200
    const val FOLDER_CLOSE = 150
    const val SHEET = 267            // config_bottomSheetOpen/CloseDuration
    const val ALL_APPS_OPEN = 400    // EXPAND_COLLAPSE_DURATION
    const val ALL_APPS_CLOSE = 300   // config_allAppsCloseDuration
    const val PAGE_SNAP = 300
    const val DROP = 285             // DRAG_VIEW_DROP_DURATION
    const val REORDER = 150          // REORDER_ANIMATION_DURATION
    const val FADE = 150             // CONTAINER_OPACITY_DURATION
}
