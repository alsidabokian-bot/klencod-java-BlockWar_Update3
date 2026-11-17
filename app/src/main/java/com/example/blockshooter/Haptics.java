package com.example.blockshooter;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Short vibration pulses. */
public final class Haptics {
    public static volatile boolean enabled = true;
    private static Vibrator vibrator;

    private Haptics() {}

    public static void init(Context context) {
        try {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        } catch (Throwable e) {
            vibrator = null;
        }
    }

    public static void vibrate(long ms) {
        if (!enabled || vibrator == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(ms);
            }
        } catch (Throwable ignored) {
        }
    }
}
