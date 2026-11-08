package net.kdt.pojavlaunch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.utils.DownloadUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * Orbit's in-app updater. Reads the latest GitHub release, shows what's new, downloads the APK, checks its
 * SHA-256 against the release's SHA256SUMS.txt and hands it to Android's installer (which always asks the user
 * to confirm; that step can't be skipped). The Orbit pack updates itself through "Get Orbit pack".
 */
public final class OrbitUpdater {
    private static final String API = "https://api.github.com/repos/nxtltxkiller/orbit-android/releases/latest";
    private static final String APK = "Orbit-Android-debug.apk", SUMS = "SHA256SUMS.txt", KEY = "orbit_update";
    private static boolean sCheckedThisRun;

    private OrbitUpdater() { }

    /** Once per app start, quietly: only speaks up when there is a newer version. */
    public static void checkOnLaunch(Activity act) {
        if (sCheckedThisRun) return;
        sCheckedThisRun = true;
        check(act, false);
    }

    /** loud = tell the user "up to date" or the error, for the button. */
    public static void check(Activity act, boolean loud) {
        if (loud) Toast.makeText(act, R.string.orbit_update_checking, Toast.LENGTH_SHORT).show();
        PojavApplication.sExecutorService.execute(() -> {
            try {
                JSONObject rel = new JSONObject(DownloadUtils.downloadString(API));
                String latest = rel.getString("tag_name").replaceFirst("^v", "");
                String mine = act.getString(R.string.orbit_version);
                if (newer(latest, mine) <= 0) {
                    if (loud) act.runOnUiThread(() -> Toast.makeText(act, act.getString(R.string.orbit_update_none, mine), Toast.LENGTH_LONG).show());
                    return;
                }
                String apkUrl = null, sumsUrl = null;
                JSONArray assets = rel.getJSONArray("assets");
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject a = assets.getJSONObject(i);
                    if (APK.equals(a.getString("name"))) apkUrl = a.getString("browser_download_url");
                    if (SUMS.equals(a.getString("name"))) sumsUrl = a.getString("browser_download_url");
                }
                if (apkUrl == null || sumsUrl == null) throw new IllegalStateException("release has no " + APK + " / " + SUMS);
                final String fApk = apkUrl, fSums = sumsUrl, notes = rel.optString("body", "");
                act.runOnUiThread(() -> new AlertDialog.Builder(act)
                        .setTitle(act.getString(R.string.orbit_update_title, latest))
                        .setMessage(act.getString(R.string.orbit_update_from, mine) + "\n\n" + notes)
                        .setPositiveButton(R.string.orbit_update_now, (d, w) -> download(act, fApk, fSums))
                        .setNegativeButton(R.string.orbit_update_later, null)
                        .show());
            } catch (Exception e) {
                if (loud) act.runOnUiThread(() -> Toast.makeText(act, act.getString(R.string.orbit_update_failed, e.getMessage()), Toast.LENGTH_LONG).show());
            }
        });
    }

    private static void download(Activity act, String apkUrl, String sumsUrl) {
        PojavApplication.sExecutorService.execute(() -> {
            // Android 6 and older can't open content:// APKs, so they get a file:// in the shared cache
            File dir = new File(Build.VERSION.SDK_INT >= 24 ? act.getCacheDir() : act.getExternalCacheDir(), "updates");
            File apk = new File(dir, APK);
            try {
                String expected = null;
                for (String line : DownloadUtils.downloadString(sumsUrl).split("\n")) {
                    String[] p = line.trim().split("\\s+");
                    if (p.length == 2 && p[1].replaceFirst("^\\*", "").equals(APK)) expected = p[0].toLowerCase();
                }
                if (expected == null) throw new IllegalStateException(SUMS + " has no line for " + APK);
                ProgressLayout.setProgress(KEY, 0, R.string.orbit_update_downloading, 0);
                DownloadUtils.downloadFileMonitored(apkUrl, apk, null, (cur, max) -> {
                    int pct = max > 0 ? (int) (cur * 100L / max) : 0;
                    ProgressLayout.setProgress(KEY, pct, R.string.orbit_update_downloading, pct);
                });
                ProgressLayout.clearProgress(KEY);
                if (!expected.equals(sha256(apk))) {
                    apk.delete();
                    throw new SecurityException(act.getString(R.string.orbit_update_badhash));
                }
                act.runOnUiThread(() -> install(act, apk));
            } catch (Exception e) {
                ProgressLayout.clearProgress(KEY);
                apk.delete();
                act.runOnUiThread(() -> new AlertDialog.Builder(act).setTitle(R.string.orbit_update_failed_title)
                        .setMessage(String.valueOf(e.getMessage())).setPositiveButton(android.R.string.ok, null).show());
            }
        });
    }

    private static void install(Activity act, File apk) {
        if (Build.VERSION.SDK_INT >= 26 && !act.getPackageManager().canRequestPackageInstalls()) {
            // Android asks once: "allow Orbit to install apps". After that, tap Update again.
            Toast.makeText(act, R.string.orbit_update_allow, Toast.LENGTH_LONG).show();
            act.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + act.getPackageName())));
            return;
        }
        Uri uri = Build.VERSION.SDK_INT >= 24
                ? FileProvider.getUriForFile(act, act.getPackageName() + ".orbitupdate", apk)
                : Uri.fromFile(apk);
        act.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[65536];
            for (int n; (n = in.read(b)) > 0; ) md.update(b, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte x : md.digest()) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    /** >0 when a is a newer x.y.z than b. */
    static int newer(String a, String b) {
        String[] x = a.split("[.]"), y = b.split("[.]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? num(x[i]) : 0, q = i < y.length ? num(y[i]) : 0;
            if (p != q) return p - q;
        }
        return 0;
    }

    private static int num(String s) {
        String d = s.replaceAll("\\D.*", "");
        return d.isEmpty() ? 0 : Integer.parseInt(d);
    }
}
