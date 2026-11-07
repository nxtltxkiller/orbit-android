package net.kdt.pojavlaunch;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.Locale;

/** Orbit: a small in-game corner readout of live CPS (attack / use button taps per second), battery %,
 *  battery temperature and free RAM, so a phone that is about to throttle shows it before the frame rate
 *  drops. Turns red at 42 C and above. CPS refreshes 4x a second; battery and RAM every 2 s. */
public final class PhoneHud {
    private static final int HOT_C = 42;
    private final Activity mActivity;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private TextView mView;
    private boolean mRunning;
    private int mTicks;
    private String mPhone = "";
    private boolean mHot;

    // taps in the last second, per button (0 = attack, 1 = use); written from the UI thread only
    private static final long[][] sTaps = new long[2][64];
    private static final int[] sHead = new int[2];

    /** An attack (0) or use (1) button went down. */
    public static void click(int button) {
        long[] r = sTaps[button];
        r[sHead[button]++ & 63] = android.os.SystemClock.uptimeMillis();
    }

    private static int cps(int button) {
        long now = android.os.SystemClock.uptimeMillis(); int n = 0;
        for (long t : sTaps[button]) if (t != 0 && now - t <= 1000) n++;
        return n;
    }

    private final Runnable mTick = new Runnable() {
        @Override public void run() {
            if (!mRunning) return;
            update();
            mHandler.postDelayed(this, 250);
        }
    };

    public PhoneHud(Activity activity) { mActivity = activity; }

    public void attach() {
        if (mView != null) return;
        ViewGroup root = mActivity.findViewById(android.R.id.content);
        if (root == null) return;
        mView = new TextView(mActivity);
        mView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        mView.setTextColor(0xFFF5F6F7);
        mView.setBackgroundColor(0x8005070A);
        int p = (int) (4 * mActivity.getResources().getDisplayMetrics().density);
        mView.setPadding(p * 2, p, p * 2, p);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        lp.setMargins(p, p, p, p);
        root.addView(mView, lp);
    }

    public void start() { if (mView == null || mRunning) return; mRunning = true; mHandler.post(mTick); }

    public void stop() { mRunning = false; mHandler.removeCallbacks(mTick); }

    private void update() {
        if (mTicks++ % 8 == 0) samplePhone();
        mView.setText(String.format(Locale.ROOT, "CPS %d | %d   %s", cps(0), cps(1), mPhone));
        mView.setTextColor(mHot ? Color.rgb(255, 100, 100) : 0xFFF5F6F7);
    }

    private void samplePhone() {
        Intent b = mActivity.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int pct = -1, tempC = -1;
        if (b != null) {
            int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (level >= 0 && scale > 0) pct = level * 100 / scale;
            int t = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            if (t > 0) tempC = t / 10;
        }
        ActivityManager am = (ActivityManager) mActivity.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(mi);
        long freeMb = mi.availMem / (1024 * 1024);
        mPhone = String.format(Locale.ROOT, "%s%%  %s°C  %d MB free", pct < 0 ? "?" : pct, tempC < 0 ? "?" : tempC, freeMb);
        mHot = tempC >= HOT_C || mi.lowMemory;
    }
}
