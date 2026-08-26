/*
 * Copyright (C) 2023-2024 The Evolution X Project
 *               2024 crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.pixelparts.autohbm;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

import androidx.preference.Preference;

import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;
import com.android.settingslib.widget.UsageProgressBarPreference;

import org.evolution.pixelparts.Constants;
import org.evolution.pixelparts.CustomSeekBarPreference;
import org.evolution.pixelparts.R;
import org.evolution.pixelparts.utils.TileUtils;

public class AutoHbmFragment extends SettingsBasePreferenceFragment
        implements OnCheckedChangeListener, SensorEventListener,
        Preference.OnPreferenceChangeListener {

    private static final String[] AUTO_HBM_PREFERENCES = {
            Constants.KEY_AUTO_HBM_THRESHOLD,
            Constants.KEY_AUTO_HBM_ENABLE_TIME,
            Constants.KEY_AUTO_HBM_DISABLE_TIME,
            Constants.KEY_CURRENT_LUX_LEVEL
    };

    private AutoHbmController mController;
    private CustomSeekBarPreference mAutoHbmThresholdPreference;
    private MainSwitchPreference mAutoHbmSwitch;
    private SensorManager mSensorManager;
    private Sensor mLightSensor;
    private UsageProgressBarPreference mCurrentLuxLevelPreference;
    private int mCurrentLux;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.auto_hbm, rootKey);
        setHasOptionsMenu(true);

        final Context context = getContext();
        mController = AutoHbmController.getInstance(context);

        mAutoHbmSwitch = findPreference(Constants.KEY_AUTO_HBM);
        mAutoHbmSwitch.setChecked(mController.isEnabled());
        mAutoHbmSwitch.addOnSwitchChangeListener(this);

        mAutoHbmThresholdPreference = findPreference(Constants.KEY_AUTO_HBM_THRESHOLD);
        mAutoHbmThresholdPreference.setOnPreferenceChangeListener(this);

        mCurrentLuxLevelPreference = findPreference(Constants.KEY_CURRENT_LUX_LEVEL);

        mSensorManager = context.getSystemService(SensorManager.class);
        mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        toggleAutoHbmPreferencesVisibility(mAutoHbmSwitch.isChecked());
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.auto_hbm_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.add_tile) {
            TileUtils.requestAddTileService(
                    getContext(),
                    AutoHbmTileService.class,
                    R.string.auto_hbm_title,
                    R.drawable.ic_auto_hbm_tile
            );
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // The readout is only on screen while this fragment is, so the
        // listener follows the fragment rather than the setting.
        if (mLightSensor != null) {
            mSensorManager.registerListener(
                    this, mLightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        mController.setEnabled(isChecked);
        toggleAutoHbmPreferencesVisibility(isChecked);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mAutoHbmThresholdPreference) {
            updateCurrentLuxLevelPreference(mCurrentLux, (int) newValue);
        }
        return true;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_LIGHT) {
            return;
        }
        mCurrentLux = (int) event.values[0];
        updateCurrentLuxLevelPreference(mCurrentLux, mAutoHbmThresholdPreference.getValue());
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do nothing
    }

    private void updateCurrentLuxLevelPreference(int currentLux, int threshold) {
        if (mCurrentLuxLevelPreference == null) {
            return;
        }
        mCurrentLuxLevelPreference.setUsageSummary(String.valueOf(currentLux));
        mCurrentLuxLevelPreference.setTotalSummary(String.valueOf(threshold));
        mCurrentLuxLevelPreference.setPercent(Math.min(currentLux, threshold), threshold);
    }

    private void toggleAutoHbmPreferencesVisibility(boolean show) {
        for (String prefKey : AUTO_HBM_PREFERENCES) {
            final Preference pref = findPreference(prefKey);
            if (pref != null) {
                pref.setVisible(show);
            }
        }
    }
}
