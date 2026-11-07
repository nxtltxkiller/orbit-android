package net.kdt.pojavlaunch.prefs.screens;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.preference.Preference;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;

/** Orbit tools: one-tap Auto-Optimize for this phone, and Free up RAM. */
public class LauncherPreferenceOrbitFragment extends LauncherPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_orbit);
        Preference opt = requirePreference("orbitOptimize");
        opt.setOnPreferenceClickListener(p -> { autoOptimize(requireContext()); return true; });
        Preference ram = requirePreference("orbitFreeRam");
        ram.setOnPreferenceClickListener(p -> { freeRam(requireContext()); return true; });
        requirePreference("orbitDeviceInfo").setSummary(deviceSummary(requireContext()));
    }

    // ------------------------------------------------------------------ tiers
    /** 3 = flagship (8 GB+, 8 cores), 2 = upper mid (6 GB+), 1 = mid (4 GB+), 0 = low end. */
    static int tier(Context ctx) {
        long mb = totalMb(ctx);
        int cores = Runtime.getRuntime().availableProcessors();
        if (mb >= 7500 && cores >= 8) return 3;
        if (mb >= 5500) return 2;
        if (mb >= 3500) return 1;
        return 0;
    }

    static long totalMb(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.totalMem / (1024 * 1024);
    }

    static long availMb(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.availMem / (1024 * 1024);
    }

    private static String deviceSummary(Context ctx) {
        String[] names = {"Low end", "Mid range", "Upper mid range", "Flagship"};
        return String.format(Locale.ROOT, "%s: %d MB RAM, %d CPU cores, %.0f Hz screen",
                names[tier(ctx)], totalMb(ctx), Runtime.getRuntime().availableProcessors(), refreshHz(ctx));
    }

    private static float refreshHz(Context ctx) {
        try { return ((android.view.WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRefreshRate(); }
        catch (Throwable t) { return 60f; }
    }

    // ------------------------------------------------------------------ auto-optimize
    private void autoOptimize(Context ctx) {
        int t = tier(ctx);
        long total = totalMb(ctx);
        // Leave at least ~2.5 GB to Android and the renderer; Minecraft 1.21 wants 1.5 GB+.
        int ram = (int) Math.max(1024, Math.min(t >= 3 ? 4096 : 3072, ((total - 2560) / 256) * 256));
        int[] res = {55, 70, 85, 100};
        int[] rd = {4, 6, 8, 10}, sd = {3, 5, 5, 6};
        int fps = (int) Math.min(t >= 2 ? 120 : 60, Math.round(refreshHz(ctx)));

        SharedPreferences.Editor e = LauncherPreferences.DEFAULT_PREF.edit();
        e.putInt("allocation", ram);
        e.putInt("resolutionRatio", res[t]);
        e.putBoolean("sustainedPerformance", t >= 2);
        e.apply();
        LauncherPreferences.loadPreferences(ctx);

        Map<String, String> o = new LinkedHashMap<>();
        o.put("renderDistance", String.valueOf(rd[t]));
        o.put("simulationDistance", String.valueOf(sd[t]));
        o.put("maxFps", String.valueOf(fps));
        o.put("enableVsync", "false");
        o.put("graphicsMode", "0");
        o.put("particles", t >= 2 ? "1" : "2");
        o.put("entityShadows", "false");
        o.put("biomeBlendRadius", "0");
        o.put("ao", "false");
        o.put("cloudStatus", t >= 3 ? "\"fast\"" : "\"false\"");
        o.put("entityDistanceScaling", t >= 2 ? "0.75" : "0.5");
        o.put("chunkSectionFadeInTime", "0.0");
        int files = 0;
        for (File f : optionsFiles()) if (writeOptions(f, o)) files++;

        Toast.makeText(ctx, String.format(Locale.ROOT,
                "Optimized: %d MB RAM, %d%% resolution, %d chunks, %d FPS cap (%d game folder%s)",
                ram, res[t], rd[t], fps, files, files == 1 ? "" : "s"), Toast.LENGTH_LONG).show();
    }

    /** The main .minecraft plus every modpack instance (the Orbit pack lives in custom_instances). */
    private static List<File> optionsFiles() {
        List<File> out = new ArrayList<>();
        File main = new File(Tools.DIR_GAME_NEW);
        out.add(new File(main, "options.txt"));
        File[] inst = new File(main, "custom_instances").listFiles(File::isDirectory);
        if (inst != null) for (File d : inst) out.add(new File(d, "options.txt"));
        return out;
    }

    private static boolean writeOptions(File f, Map<String, String> o) {
        try {
            List<String> lines = f.exists() ? new ArrayList<>(Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) : new ArrayList<>();
            Set<String> done = new HashSet<>();
            for (int i = 0; i < lines.size(); i++) {
                String l = lines.get(i);
                int c = l.indexOf(':');
                if (c <= 0) continue;
                String k = l.substring(0, c);
                if (o.containsKey(k)) { lines.set(i, k + ":" + o.get(k)); done.add(k); }
            }
            for (Map.Entry<String, String> en : o.entrySet()) if (!done.contains(en.getKey())) lines.add(en.getKey() + ":" + en.getValue());
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            Files.write(f.toPath(), lines, StandardCharsets.UTF_8);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    // ------------------------------------------------------------------ free up RAM
    /** Asks Android to stop other apps' background processes (never the foreground app, system apps or Orbit). */
    private void freeRam(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        long before = availMb(ctx);
        PackageManager pm = ctx.getPackageManager();
        Set<String> pkgs = new HashSet<>();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(launcher, 0)) pkgs.add(ri.activityInfo.packageName);
        int n = 0;
        for (String p : pkgs) {
            if (p.equals(ctx.getPackageName())) continue;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(p, 0);
                if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                am.killBackgroundProcesses(p);
                n++;
            } catch (Exception ignored) { }
        }
        System.gc();
        final int apps = n;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            long freed = Math.max(0, availMb(ctx) - before);
            Toast.makeText(ctx, String.format(Locale.ROOT, "Freed %d MB (asked %d apps to close in the background). %d MB free now.",
                    freed, apps, availMb(ctx)), Toast.LENGTH_LONG).show();
            requirePreference("orbitDeviceInfo").setSummary(deviceSummary(ctx));
        }, 1200);
    }
}
