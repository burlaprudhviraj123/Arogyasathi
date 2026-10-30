package org.arogyasathi.app.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Haptic feedback utility providing distinct physical alerts when clinical danger
 * signs or emergency escalations are detected during frontline triage.
 */
object HapticFeedbackHelper {

    /**
     * Urgent dual-pulse tactile vibration sequence:
     * 0ms delay -> 320ms alert buzz -> 140ms silent gap -> 450ms critical buzz.
     */
    fun vibrateDangerSign(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            vibrator?.let { v ->
                if (!v.hasVibrator()) return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = longArrayOf(0, 320, 140, 450)
                    val amplitudes = intArrayOf(0, 255, 0, 255)
                    val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                    v.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(longArrayOf(0, 320, 140, 450), -1)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
