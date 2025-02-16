/*
 * Copyright (C) 2024 The Evolution X Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.pixelparts.pixeltorch;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class PixelTorchHelper {

    private static final String TAG = "PixelTorchHelper";

    private static final String TORCH_BRIGHTNESS_PATH_0 = "/sys/class/leds/led:torch_0/brightness";
    private static final String TORCH_BRIGHTNESS_PATH_1 = "/sys/class/leds/led:torch_1/brightness";
    private static final String TOGGLE_SWITCH_PATH = "/sys/class/leds/led:switch_2/brightness";
    private static final int MAX_BRIGHTNESS = 500;

    private final Context mContext;

    public PixelTorchHelper(Context context) {
        this.mContext = context;
    }

    public void toggleTorch() {
        boolean isOn = getTorchBrightness() > 0;
        setTorchBrightness(isOn ? 0 : getSavedBrightness());
    }

    public void setTorchBrightness(int brightness) {
        if (brightness < 0) brightness = 0;
        if (brightness > MAX_BRIGHTNESS) brightness = MAX_BRIGHTNESS;

        Log.d(TAG, "Setting torch brightness: " + brightness);

        // Disable toggle switch before changing brightness
        writeToFile(TOGGLE_SWITCH_PATH, "0");

        // Set brightness for both torch LEDs
        writeToFile(TORCH_BRIGHTNESS_PATH_0, String.valueOf(brightness));
        writeToFile(TORCH_BRIGHTNESS_PATH_1, String.valueOf(brightness));

        // Re-enable toggle switch
        writeToFile(TOGGLE_SWITCH_PATH, "255");

        // Save brightness to system settings for persistence
        Settings.System.putInt(mContext.getContentResolver(), "torch_brightness", brightness);
    }

    public int getTorchBrightness() {
        return Settings.System.getInt(mContext.getContentResolver(), "torch_brightness", 200);
    }

    private int getSavedBrightness() {
        return Settings.System.getInt(mContext.getContentResolver(), "torch_brightness", 200);
    }

    private void writeToFile(String path, String value) {
        try (FileWriter writer = new FileWriter(new File(path))) {
            writer.write(value);
            writer.flush();
        } catch (IOException e) {
            Log.e(TAG, "Failed to write to " + path, e);
        }
    }
}
