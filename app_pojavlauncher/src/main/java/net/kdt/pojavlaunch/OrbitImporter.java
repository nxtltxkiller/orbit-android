package net.kdt.pojavlaunch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Orbit: import profiles from MojoLauncher, PojavLauncher, Amethyst or any other Pojav-family launcher. Those apps
 * show their game folder in Android's file picker, so the user picks it and we copy each chosen profile into a new
 * Orbit profile: mods, resource packs, shaders, config, worlds, options.txt (all settings), servers, and the
 * on-screen button layouts. Minecraft itself, libraries and assets are re-downloaded, never copied. Accounts are
 * never touched (they live in the other app's private storage anyway).
 */
public final class OrbitImporter {
    private static final String KEY = "orbit_import";
    // re-downloadable or private: never copied out of a profile's game folder
    private static final Set<String> SKIP = new HashSet<>(Arrays.asList("versions", "libraries", "assets", "logs",
            "crash-reports", "screenshots", "natives", "custom_instances", ".fabric", ".cache", "webcache2",
            "launcher_profiles.json", "usercache.json", "usernamecache.json"));

    private final Activity mAct;
    private final ContentResolver mCr;
    private final Uri mTree;
    private int mFiles;

    private OrbitImporter(Activity act, Uri tree) { mAct = act; mCr = act.getContentResolver(); mTree = tree; }

    private static final class Doc {
        final String id, name; final boolean dir;
        Doc(String id, String name, boolean dir) { this.id = id; this.name = name; this.dir = dir; }
    }

    /** Called with the folder the user picked. */
    public static void start(Activity act, Uri tree) {
        if (tree == null) return;
        PojavApplication.sExecutorService.execute(() -> new OrbitImporter(act, tree).scan());
    }

    private void scan() {
        try {
            Doc root = new Doc(DocumentsContract.getTreeDocumentId(mTree), "", true);
            Doc mc = child(root, ".minecraft");
            if (mc == null) throw new IOException(mAct.getString(R.string.orbit_import_wrong_folder));
            String label = displayName(root.id).replace("(Debug)", "").trim();
            if (label.isEmpty() || label.equals(".minecraft")) label = "Launcher";

            List<String> names = new ArrayList<>();
            List<JSONObject> profiles = new ArrayList<>();
            Doc lp = child(mc, "launcher_profiles.json");
            if (lp != null) {
                JSONObject all = new JSONObject(readText(lp)).optJSONObject("profiles");
                if (all != null) for (Iterator<String> it = all.keys(); it.hasNext(); ) {
                    JSONObject p = all.getJSONObject(it.next());
                    profiles.add(p);
                    names.add(p.optString("name", "Profile") + "  (" + p.optString("lastVersionId", "?") + ")");
                }
            }
            if (profiles.isEmpty()) {   // no profile list: take the whole .minecraft as one profile
                profiles.add(new JSONObject().put("name", label).put("lastVersionId", MinecraftProfile.LATEST_RELEASE));
                names.add(label);
            }
            final String fLabel = label;
            boolean[] picked = new boolean[names.size()];
            Arrays.fill(picked, true);
            mAct.runOnUiThread(() -> new AlertDialog.Builder(mAct)
                    .setTitle(mAct.getString(R.string.orbit_import_pick, fLabel))
                    .setMultiChoiceItems(names.toArray(new String[0]), picked, (d, i, on) -> picked[i] = on)
                    .setPositiveButton(R.string.orbit_import_go, (d, w) -> PojavApplication.sExecutorService.execute(() -> {
                        List<JSONObject> chosen = new ArrayList<>();
                        for (int i = 0; i < picked.length; i++) if (picked[i]) chosen.add(profiles.get(i));
                        importAll(root, mc, fLabel, chosen);
                    }))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show());
        } catch (Exception e) {
            fail(e);
        }
    }

    private void importAll(Doc root, Doc mc, String label, List<JSONObject> chosen) {
        try {
            ProgressLayout.setProgress(KEY, 0, R.string.orbit_import_progress, 0);
            // button layouts: copy every layout once, prefixed so nothing of Orbit's is overwritten
            Doc ctrl = child(root, "controlmap");
            String prefix = safe(label) + "-";
            if (ctrl != null) for (Doc d : children(ctrl))
                if (!d.dir && d.name.endsWith(".json")) copyFile(d, new File(Tools.CTRLMAP_PATH, prefix + d.name));

            LauncherProfiles.load();
            String firstKey = null;
            for (JSONObject p : chosen) {
                String name = p.optString("name", "Profile");
                String gameDir = p.optString("gameDir", "");
                Doc src = gameDir.isEmpty() ? mc : path(root, gameDir.replaceFirst("^[a-zA-Z]+://", "").replaceFirst("^\\./", ""));
                if (src == null) continue;
                String folder = uniqueFolder(safe(label + " " + name));
                File dest = new File(Tools.DIR_GAME_HOME, "custom_instances/" + folder);
                copyTree(src, dest, true);

                String ver = p.optString("lastVersionId", MinecraftProfile.LATEST_RELEASE);
                Doc vdir = path(mc, "versions/" + ver);                // modded versions can't always be re-fetched
                Doc vjson = vdir == null ? null : child(vdir, ver + ".json");
                File ourJson = new File(Tools.DIR_HOME_VERSION, ver + "/" + ver + ".json");
                if (vjson != null && !ourJson.exists()) copyFile(vjson, ourJson);

                MinecraftProfile prof = new MinecraftProfile();
                prof.name = mAct.getString(R.string.orbit_import_profile_name, name, label);
                prof.lastVersionId = ver;
                prof.gameDir = "./custom_instances/" + folder;
                prof.icon = p.optString("icon", null);
                prof.javaArgs = p.optString("javaArgs", null);
                String cf = p.optString("controlFile", "");
                if (cf.isEmpty()) cf = "default.json";
                if (new File(Tools.CTRLMAP_PATH, prefix + cf).exists()) prof.controlFile = prefix + cf;
                String key = LauncherProfiles.getFreeProfileKey();
                LauncherProfiles.mainProfileJson.profiles.put(key, prof);
                if (firstKey == null) firstKey = key;
            }
            LauncherProfiles.write();
            ProgressLayout.clearProgress(KEY);
            final String select = firstKey;
            final int n = chosen.size();
            mAct.runOnUiThread(() -> {
                if (select != null) {
                    LauncherPreferences.DEFAULT_PREF.edit().putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, select).apply();
                    ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, select);
                }
                new AlertDialog.Builder(mAct).setTitle(R.string.orbit_import_done_title)
                        .setMessage(mAct.getString(R.string.orbit_import_done, n, mFiles, label))
                        .setPositiveButton(android.R.string.ok, (d, w) -> mAct.recreate())
                        .setCancelable(false).show();
            });
        } catch (Exception e) {
            fail(e);
        }
    }

    private void copyTree(Doc dir, File dest, boolean top) throws IOException {
        for (Doc d : children(dir)) {
            if (top && (SKIP.contains(d.name) || d.name.toLowerCase().contains("account"))) continue;
            File out = new File(dest, d.name);
            if (d.dir) copyTree(d, out, false);
            else copyFile(d, out);
        }
    }

    private void copyFile(Doc d, File out) throws IOException {
        File parent = out.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Can't create " + parent);
        try (InputStream in = mCr.openInputStream(DocumentsContract.buildDocumentUriUsingTree(mTree, d.id));
             OutputStream os = new FileOutputStream(out)) {
            if (in == null) throw new IOException("Can't read " + d.name);
            byte[] b = new byte[65536];
            for (int r; (r = in.read(b)) > 0; ) os.write(b, 0, r);
        }
        if (++mFiles % 25 == 0) ProgressLayout.setProgress(KEY, 0, R.string.orbit_import_progress, mFiles);
    }

    private List<Doc> children(Doc dir) {
        List<Doc> out = new ArrayList<>();
        Uri u = DocumentsContract.buildChildDocumentsUriUsingTree(mTree, dir.id);
        try (Cursor c = mCr.query(u, new String[]{Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE}, null, null, null)) {
            while (c != null && c.moveToNext())
                out.add(new Doc(c.getString(0), c.getString(1), Document.MIME_TYPE_DIR.equals(c.getString(2))));
        }
        return out;
    }

    private Doc child(Doc dir, String name) {
        for (Doc d : children(dir)) if (d.name.equals(name)) return d;
        return null;
    }

    private Doc path(Doc from, String rel) {
        Doc d = from;
        for (String part : rel.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if ((d = child(d, part)) == null) return null;
        }
        return d;
    }

    private String displayName(String id) {
        try (Cursor c = mCr.query(DocumentsContract.buildDocumentUriUsingTree(mTree, id), new String[]{Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            return c != null && c.moveToFirst() ? c.getString(0) : "";
        }
    }

    private String readText(Doc d) throws IOException {
        try (InputStream in = mCr.openInputStream(DocumentsContract.buildDocumentUriUsingTree(mTree, d.id))) {
            if (in == null) throw new IOException("Can't read " + d.name);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            for (int r; (r = in.read(b)) > 0; ) bo.write(b, 0, r);
            return bo.toString("UTF-8");
        }
    }

    private static String safe(String s) {
        String t = s.replaceAll("[^A-Za-z0-9 ._-]", "").trim().replace(' ', '_');
        return t.isEmpty() ? "import" : t;
    }

    private static String uniqueFolder(String base) {
        String f = base;
        for (int i = 2; new File(Tools.DIR_GAME_HOME, "custom_instances/" + f).exists(); i++) f = base + "_" + i;
        return f;
    }

    private void fail(Exception e) {
        ProgressLayout.clearProgress(KEY);
        mAct.runOnUiThread(() -> new AlertDialog.Builder(mAct).setTitle(R.string.orbit_import_failed)
                .setMessage(String.valueOf(e.getMessage())).setPositiveButton(android.R.string.ok, null).show());
    }
}
