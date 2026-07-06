/*
 * Copyright (C) 2026 The Evolution X Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.pixelparts.pixeltorch;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import org.evolution.pixelparts.Constants;
import org.evolution.pixelparts.R;

public class PixelTorchDialogActivity extends Activity {

    private SharedPreferences mSharedPrefs;
    private CameraManager mCameraManager;
    private PixelTorchHelper mPixelTorchHelper;
    private CameraManager.TorchCallback mTorchCallback;

    private Switch mTorchSwitch;
    private SeekBar mTorchSlider;
    private TextView mValueLabel;

    private boolean mIsUpdatingFromCallback = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set Dialog window gravity to bottom and match parent width
        Window window = getWindow();
        if (window != null) {
            window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
        }

        setContentView(R.layout.qs_torch_slider);

        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        mPixelTorchHelper = new PixelTorchHelper(mCameraManager);

        mTorchSwitch = findViewById(R.id.torch_switch);
        mTorchSlider = findViewById(R.id.torch_slider);
        mValueLabel = findViewById(R.id.value_label);

        // Get saved strength
        int savedStrength = mSharedPrefs.getInt(Constants.KEY_PIXEL_TORCH_STRENGTH_1, 255);
        mTorchSlider.setProgress(savedStrength);
        updateValueLabel(savedStrength);

        mTorchSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Ensure value is at least 1
                int value = Math.max(1, progress);
                updateValueLabel(value);

                if (fromUser) {
                    // Save the new strength as default strength
                    mSharedPrefs.edit().putInt(Constants.KEY_PIXEL_TORCH_STRENGTH_1, value).apply();
                    
                    // If cycle modes is enabled, maybe we also want to set strength_2/3?
                    // But for the simple pop-up slider, updating the main/current strength is best.
                    int currentState = mPixelTorchHelper.getCurrentState(mSharedPrefs);
                    if (currentState > 0) {
                        mPixelTorchHelper.writeTorchBrightness(value);
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        mTorchSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (mIsUpdatingFromCallback) return;

            int currentState = mPixelTorchHelper.getCurrentState(mSharedPrefs);
            boolean isCurrentlyOn = currentState > 0;

            if (isChecked != isCurrentlyOn) {
                mPixelTorchHelper.toggleTorch(mSharedPrefs);
            }
        });

        mTorchCallback = new CameraManager.TorchCallback() {
            @Override
            public void onTorchModeChanged(String cameraId, boolean enabled) {
                super.onTorchModeChanged(cameraId, enabled);
                runOnUiThread(() -> {
                    mIsUpdatingFromCallback = true;
                    mTorchSwitch.setChecked(enabled);
                    mIsUpdatingFromCallback = false;
                });
            }
        };

        mCameraManager.registerTorchCallback(mTorchCallback, null);
    }

    private void updateValueLabel(int value) {
        mValueLabel.setText(getString(R.string.custom_seekbar_value, String.valueOf(value)));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraManager != null && mTorchCallback != null) {
            mCameraManager.unregisterTorchCallback(mTorchCallback);
        }
    }
}
