package net.kdt.pojavlaunch.customcontrols.buttons;

import net.kdt.pojavlaunch.EfficientAndroidLWJGLKeycode;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import org.lwjgl.glfw.CallbackBridge;

/**
 * Orbit snap tap (SOCD "last input wins") for W/S and A/D buttons. Holding S and pressing W releases S
 * and presses W, so the newest direction wins at once with no stuck or ghost key; letting go of W while
 * S is still held brings S back. It only resolves keys the player is holding: nothing extra is ever sent.
 */
final class SnapTap {
    private SnapTap() {}

    private static final int W = 87, A = 65, S = 83, D = 68;
    private static final boolean[] held = new boolean[512];   // fingers on the button
    private static final boolean[] sent = new boolean[512];   // what the game was last told

    static boolean handles(int key) {
        return LauncherPreferences.PREF_SNAP_TAP && (key == W || key == A || key == S || key == D);
    }

    private static int opposite(int key) {
        switch (key) { case W: return S; case S: return W; case A: return D; default: return A; }
    }

    static synchronized void key(int key, boolean down) {
        int o = opposite(key);
        if (down) {
            held[key] = true;
            if (sent[o]) send(o, false);
            if (!sent[key]) send(key, true);
        } else {
            held[key] = false;
            if (sent[key]) send(key, false);
            if (held[o] && !sent[o]) send(o, true);
        }
    }

    private static void send(int key, boolean down) {
        sent[key] = down;
        CallbackBridge.sendKeyPress(key, EfficientAndroidLWJGLKeycode.getLwjglChar(key), CallbackBridge.getCurrentMods(), down);
    }
}
