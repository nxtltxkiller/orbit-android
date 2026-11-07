package net.kdt.pojavlaunch;

import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;

/**
 * Orbit low-latency mode, for the game screen only:
 *  - keeps Wi-Fi out of power save while playing (Android 10+ low-latency lock; older phones get the
 *    high-performance lock), which removes the 20-100 ms ping spikes power save adds;
 *  - picks the display's highest refresh rate at the current resolution, so frames show sooner after a touch;
 *  - asks the display to skip image post-processing (Android 11+), which can add a frame of lag.
 * Nothing here touches game packets or timing.
 */
public final class LowLatency {
    private final Activity mActivity;
    private WifiManager.WifiLock mLock;

    public LowLatency(Activity activity) { mActivity = activity; }

    /** Call once the game window exists (refresh rate and post-processing are window settings). */
    public void setupWindow() {
        if (!LauncherPreferences.PREF_LOW_LATENCY) return;
        Window w = mActivity.getWindow();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Display d = ((WindowManager) mActivity.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
                Display.Mode cur = d.getMode(), best = cur;
                for (Display.Mode m : d.getSupportedModes())
                    if (m.getPhysicalWidth() == cur.getPhysicalWidth() && m.getPhysicalHeight() == cur.getPhysicalHeight()
                            && m.getRefreshRate() > best.getRefreshRate()) best = m;
                WindowManager.LayoutParams lp = w.getAttributes();
                lp.preferredDisplayModeId = best.getModeId();
                w.setAttributes(lp);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) w.setPreferMinimalPostProcessing(true);
        } catch (Throwable ignored) { }
    }

    public void acquire() {
        if (!LauncherPreferences.PREF_LOW_LATENCY || mLock != null) return;
        try {
            WifiManager wm = (WifiManager) mActivity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            int mode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? WifiManager.WIFI_MODE_FULL_LOW_LATENCY : WifiManager.WIFI_MODE_FULL_HIGH_PERF;
            mLock = wm.createWifiLock(mode, "Orbit:lowLatency");
            mLock.setReferenceCounted(false);
            mLock.acquire();
        } catch (Throwable ignored) { mLock = null; }
    }

    public void release() {
        try { if (mLock != null && mLock.isHeld()) mLock.release(); } catch (Throwable ignored) { }
        mLock = null;
    }
}
