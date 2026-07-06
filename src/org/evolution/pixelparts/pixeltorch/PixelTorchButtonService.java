/*
 * Copyright (C) 2023 Cyb3rKo
 *               2024 The Evolution X Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.pixelparts.pixeltorch;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.camera2.CameraManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.KeyEvent;

import androidx.preference.PreferenceManager;

import org.evolution.pixelparts.Constants;

public class PixelTorchButtonService extends AccessibilityService {

    private SharedPreferences mSharedPrefs;
    private CameraManager mCameraManager;
    private CameraManager.TorchCallback mTorchCallback;
    private PixelTorchHelper mPixelTorchHelper;
    private boolean mVolumeUp = false;
    private boolean mVolumeDown = false;

    @Override
    public void onServiceConnected() {
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        mPixelTorchHelper = new PixelTorchHelper(mCameraManager);

        mTorchCallback = new CameraManager.TorchCallback() {
            @Override
            public void onTorchModeChanged(String cameraId, boolean enabled) {
                super.onTorchModeChanged(cameraId, enabled);
                if (!enabled) {
                    mPixelTorchHelper.setCurrentState(mSharedPrefs, 0);
                    mPixelTorchHelper.writeTorchOff();
                } else {
                    int currentState = mPixelTorchHelper.getCurrentState(mSharedPrefs);
                    if (currentState == 0) {
                        currentState = 1;
                        mPixelTorchHelper.setCurrentState(mSharedPrefs, currentState);
                    }
                    int torchStrength = mSharedPrefs.getInt(
                            currentState == 2 ? Constants.KEY_PIXEL_TORCH_STRENGTH_2 :
                            currentState == 3 ? Constants.KEY_PIXEL_TORCH_STRENGTH_3 :
                            Constants.KEY_PIXEL_TORCH_STRENGTH_1,
                            currentState == 2 ? 150 :
                            currentState == 3 ? 50 : 255
                    );
                    mPixelTorchHelper.writeTorchBrightness(torchStrength);
                }
            }
        };

        mCameraManager.registerTorchCallback(mTorchCallback, null);
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        if (event == null) return false;

        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP ||
                event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN) {

            boolean pressed = event.getAction() == KeyEvent.ACTION_DOWN;

            if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP) {
                mVolumeUp = pressed;
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN) {
                mVolumeDown = pressed;
            }

            if (mVolumeUp && mVolumeDown) {
                mPixelTorchHelper.toggleTorch(mSharedPrefs);
            }
        } else {
            mVolumeUp = false;
            mVolumeDown = false;
        }

        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
