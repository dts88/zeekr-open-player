package io.github.dts88.openplayer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

public final class AppExit {
    private static final long SETTLE_MS = 420;
    private static boolean leaving;

    private AppExit() {
    }

    public static boolean leaving() {
        return leaving;
    }

    public static void run(Activity activity) {
        if (leaving) return;
        leaving = true;
        Context app = activity.getApplicationContext();
        try {
            PlayerEngine.get(app).stopAll();
            app.stopService(new Intent(app, PlaybackService.class));
        } catch (RuntimeException ignored) {
        }
        activity.finishAndRemoveTask();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Process.killProcess(Process.myPid());
            System.exit(0);
        }, SETTLE_MS);
    }
}
