package com.topjohnwu.magisk;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.WindowManager;
import android.widget.Toast;

import com.topjohnwu.magisk.module.ModuleHelper;
import com.topjohnwu.magisk.utils.Async;
import com.topjohnwu.magisk.utils.Logger;
import com.topjohnwu.magisk.utils.Shell;
import com.topjohnwu.magisk.utils.Utils;

import butterknife.BindView;
import butterknife.ButterKnife;

public class SettingsActivity extends AppCompatActivity {

    @BindView(R.id.toolbar) Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String theme = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("theme", "");
        Logger.dev("AboutActivity: Theme is " + theme);
        if (Global.Configs.isDarkTheme) {
            setTheme(R.style.AppTheme_dh);
        }

        setContentView(R.layout.activity_container);
        ButterKnife.bind(this);

        setSupportActionBar(toolbar);

        toolbar.setNavigationOnClickListener(view -> finish());

        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setTitle(R.string.settings);
            ab.setDisplayHomeAsUpEnabled(true);
        }

        setFloating();

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction().add(R.id.container, new SettingsFragment()).commit();
        }

    }

    public void setFloating() {
        boolean isTablet = getResources().getBoolean(R.bool.isTablet);
        if (isTablet) {
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.height = getResources().getDimensionPixelSize(R.dimen.floating_height);
            params.width = getResources().getDimensionPixelSize(R.dimen.floating_width);
            params.alpha = 1.0f;
            params.dimAmount = 0.6f;
            params.flags |= 2;
            getWindow().setAttributes(params);
            setFinishOnTouchOutside(true);
        }
    }

    public static class SettingsFragment extends PreferenceFragment
            implements SharedPreferences.OnSharedPreferenceChangeListener {

        private SharedPreferences prefs;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.app_settings);
            PreferenceManager.setDefaultValues(getActivity(), R.xml.app_settings, false);
            prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

            CheckBoxPreference busyboxPreference = (CheckBoxPreference) findPreference("busybox");
            CheckBoxPreference magiskhidePreference = (CheckBoxPreference) findPreference("magiskhide");
            SwitchPreference hostsPreference = (SwitchPreference) findPreference("hosts");
            Preference clear = findPreference("clear");

            clear.setOnPreferenceClickListener((pref) -> {
                ModuleHelper.clearRepoCache(getActivity());
                return true;
            });

            if (!Shell.rootAccess()) {
                busyboxPreference.setEnabled(false);
                magiskhidePreference.setEnabled(false);
                hostsPreference.setEnabled(false);
            } else {
                if (Global.Info.magiskVersion < 9) {
                    hostsPreference.setEnabled(false);
                    busyboxPreference.setEnabled(false);
                }
                if (Global.Info.magiskVersion < 8) {
                    magiskhidePreference.setEnabled(false);
                }
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            prefs.registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onStop() {
            super.onStop();
            prefs.unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            Logger.dev("Settings: Prefs change " + key);
            boolean checked;

            switch (key) {
                case "dark_theme":
                    checked = prefs.getBoolean("dark_theme", false);
                    if (Global.Configs.isDarkTheme != checked) {
                        Global.Configs.isDarkTheme = checked;
                        getActivity().recreate();
                        Global.Events.reloadMainActivity.trigger();
                    }
                    break;
                case "magiskhide":
                    checked = prefs.getBoolean("magiskhide", false);
                    new Async.RootTask<Void, Void, Void>() {
                        private boolean enable = checked;
                        @Override
                        protected Void doInBackground(Void... params) {
                            if (enable) {
                                Utils.createFile("/magisk/.core/magiskhide/enable");
                            } else {
                                Utils.removeItem("/magisk/.core/magiskhide/enable");
                            }

                            return null;
                        }
                    }.exec();
                    Toast.makeText(getActivity(), R.string.settings_reboot_toast, Toast.LENGTH_LONG).show();
                    break;
                case "busybox":
                    checked = prefs.getBoolean("busybox", false);
                    new Async.RootTask<Void, Void, Void>() {
                        private boolean enable = checked;
                        @Override
                        protected Void doInBackground(Void... params) {
                            if (enable) {
                                Utils.createFile("/magisk/.core/busybox/enable");
                            } else {
                                Utils.removeItem("/magisk/.core/busybox/enable");
                            }
                            return null;
                        }
                    }.exec();
                    Toast.makeText(getActivity(), R.string.settings_reboot_toast, Toast.LENGTH_LONG).show();
                    break;
                case "hosts":
                    checked = prefs.getBoolean("hosts", false);
                    new Async.RootTask<Void, Void, Void>() {
                        private boolean enable = checked;
                        @Override
                        protected Void doInBackground(Void... voids) {
                            if (enable) {
                                Shell.su("cp -af /system/etc/hosts /magisk/.core/hosts",
                                        "mount -o bind /magisk/.core/hosts /system/etc/hosts");
                            } else {
                                Shell.su("umount -l /system/etc/hosts",
                                        "rm -f /magisk/.core/hosts");
                            }
                            return null;
                        }
                    }.exec();
                    break;
                case "developer_logging":
                    Global.Configs.devLogging = prefs.getBoolean("developer_logging", false);
                    break;
                case "shell_logging":
                    Global.Configs.shellLogging = prefs.getBoolean("shell_logging", false);
                    break;
            }

        }
    }

}
