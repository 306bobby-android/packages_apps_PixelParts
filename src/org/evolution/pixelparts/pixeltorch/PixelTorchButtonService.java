/*
 * Copyright (C) 2023 Cyb3rKo
 *               2024 The Evolution X Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.pixelparts.pixeltorch;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;
import android.view.KeyEvent;

import androidx.preference.PreferenceManager;

public class PixelTorchButtonService extends AccessibilityService {

    private SharedPreferences mSharedPrefs;
    private PixelTorchHelper mPixelTorchHelper;
    private boolean mVolumeUp = false;
    private boolean mVolumeDown = false;

    @Override
    public void onServiceConnected() {
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mPixelTorchHelper = new PixelTorchHelper(this);
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        if (event == null) return false;

        int brightness = mPixelTorchHelper.getTorchBrightness();
        boolean pressed = event.getAction() == KeyEvent.ACTION_DOWN;

        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP) {
            mVolumeUp = pressed;
            if (pressed) {
                brightness = Math.min(brightness + 50, 500);  // Increase brightness
            }
        } else if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN) {
            mVolumeDown = pressed;
            if (pressed) {
                brightness = Math.max(brightness - 50, 0);  // Decrease brightness
            }
        }

        // If both volume buttons are pressed, toggle the torch
        if (mVolumeUp && mVolumeDown) {
            if (brightness > 0) {
                mPixelTorchHelper.setTorchBrightness(0);
            } else {
                mPixelTorchHelper.setTorchBrightness(200);  // Default brightness
            }
        } else {
            mPixelTorchHelper.setTorchBrightness(brightness);
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
