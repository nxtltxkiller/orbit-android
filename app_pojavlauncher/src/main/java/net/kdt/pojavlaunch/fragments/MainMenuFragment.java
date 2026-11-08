package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.dialogOnUiThread;
import static net.kdt.pojavlaunch.Tools.hasMods;
import static net.kdt.pojavlaunch.Tools.hasNoOnlineProfileDialog;
import static net.kdt.pojavlaunch.Tools.hasOnlineProfile;
import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.runOnUiThread;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.ProgressLayout;
import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.OrbitUpdater;
import net.kdt.pojavlaunch.OrbitImporter;
import net.kdt.pojavlaunch.modloaders.modpacks.api.CommonApi;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModLoader;
import net.kdt.pojavlaunch.modloaders.modpacks.api.NotificationDownloadListener;
import net.kdt.pojavlaunch.utils.DownloadUtils;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.modloaders.LWJGL3ifyUtils;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.util.List;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;
    // Orbit: pick another launcher's game folder (Mojo / Pojav show it in the system picker)
    private final androidx.activity.result.ActivityResultLauncher<android.net.Uri> mImportPicker =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
                    uri -> OrbitImporter.start(requireActivity(), uri));

    private static final String ORBIT_PACK_URL = "https://github.com/nxtltxkiller/orbit-android/releases/latest/download/Orbit-Android.mrpack";

    /** Orbit: download the latest Orbit pack and install it as a profile (Minecraft, Fabric, mods, packs, settings). */
    private void getOrbitPack() {
        final android.app.Activity act = requireActivity();
        Toast.makeText(act, R.string.orbit_get_pack_start, Toast.LENGTH_SHORT).show();
        PojavApplication.sExecutorService.execute(() -> {
            File pack = new File(Tools.DIR_CACHE, "orbit_pack.mrpack");
            try {
                ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.orbit_get_pack_downloading);
                DownloadUtils.downloadFile(ORBIT_PACK_URL, pack);
                ModLoader loader = new CommonApi(act.getString(R.string.curseforge_api_key)).importModpack(pack);
                pack.delete();
                if (loader != null) loader.getDownloadTask(new NotificationDownloadListener(act, loader)).run();
                act.runOnUiThread(() -> Toast.makeText(act, R.string.orbit_get_pack_ready, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                pack.delete();
                ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
                Tools.showErrorRemote(act, R.string.modpack_install_download_failed, e);
            }
        });
    }

    public MainMenuFragment(){
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button mNewsButton = view.findViewById(R.id.news_button);
        Button mDiscordButton = view.findViewById(R.id.discord_button);
        Button mCustomControlButton = view.findViewById(R.id.custom_control_button);
        Button mInstallJarButton = view.findViewById(R.id.install_jar_button);
        Button mShareLogsButton = view.findViewById(R.id.share_logs_button);
        Button mOpenDirectoryButton = view.findViewById(R.id.open_files_button);

        ImageButton mEditProfileButton = view.findViewById(R.id.edit_profile_button);
        Button mPlayButton = view.findViewById(R.id.play_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);

        mNewsButton.setOnClickListener(v -> getOrbitPack());
        // Orbit: in-app updates (tap), the website is still a long-press away
        mDiscordButton.setOnClickListener(v -> OrbitUpdater.check(requireActivity(), true));
        mDiscordButton.setOnLongClickListener(v -> { Tools.openURL(requireActivity(), "https://orbit-launcher-mc.netlify.app"); return true; });
        OrbitUpdater.checkOnLaunch(requireActivity());
        view.findViewById(R.id.orbit_import_button).setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                .setTitle(R.string.orbit_import_how_title).setMessage(R.string.orbit_import_how)
                .setPositiveButton(R.string.orbit_import_pick_folder, (d, w) -> mImportPicker.launch(null))
                .setNegativeButton(android.R.string.cancel, null).show());
        mCustomControlButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        if (hasOnlineProfile()) {
            mInstallJarButton.setOnClickListener(v -> runInstallerWithConfirmation(false));
            mInstallJarButton.setOnLongClickListener(v -> {
                runInstallerWithConfirmation(true);
                return true;
            });
        } else mInstallJarButton.setOnClickListener(v -> hasNoOnlineProfileDialog(requireActivity()));
        mEditProfileButton.setOnClickListener(v -> mVersionSpinner.openProfileEditor(requireActivity()));

        mPlayButton.setOnClickListener(v -> {
            if (Tools.hasMods("sodium") && !(LauncherPreferences.DEFAULT_PREF.getBoolean("sodium_override", false))) {
                AlertDialog sodiumWarningDialog = new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.sodium_warning_title)
                        .setMessage(R.string.sodium_warning_message)
                        .setNeutralButton(R.string.delete_sodium, (d,w)-> {
                            Tools.deleteSodiumMods();
                            ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
                        })
                        .create();
                sodiumWarningDialog.show();
            } else ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
        });

        mShareLogsButton.setOnClickListener((v) -> shareLog(requireContext()));

        mOpenDirectoryButton.setOnClickListener((v)-> {
            if (Tools.isDemoProfile(v.getContext())){ // Say a different message when on demo profile since they might see the hidden demo folder
                hasNoOnlineProfileDialog(getActivity(), getString(R.string.demo_unsupported), getString(R.string.change_account));
            } else if (!hasOnlineProfile()) { // Otherwise display the generic pop-up to log in
                hasNoOnlineProfileDialog(requireActivity());
            } else openPath(v.getContext(), getCurrentProfileDirectory(), false);

        });


        mNewsButton.setOnLongClickListener((v)->{
            Tools.swapFragment(requireActivity(), GamepadMapperFragment.class, GamepadMapperFragment.TAG, null);
            return true;
        });
    }

    private File getCurrentProfileDirectory() {
        String currentProfile = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
        if(!Tools.isValidString(currentProfile)) return new File(Tools.DIR_GAME_NEW);
        LauncherProfiles.load();
        MinecraftProfile profileObject = LauncherProfiles.mainProfileJson.profiles.get(currentProfile);
        if(profileObject == null) return new File(Tools.DIR_GAME_NEW);
        return Tools.getGameDirPath(profileObject);
    }

    @Override
    public void onResume() {
        super.onResume();
        mVersionSpinner.reloadProfiles();
    }

    private void runInstallerWithConfirmation(boolean isCustomArgs) {
        if (ProgressKeeper.getTaskCount() == 0)
            Tools.installMod(requireActivity(), isCustomArgs);
        else
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
    }
}
