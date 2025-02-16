/*
 * Copyright (C) 2023-2024 The Evolution X Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.pixelparts.pixeltorch;

import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import org.evolution.pixelparts.R;

public class PixelTorchFragment extends Fragment {

    private PixelTorchHelper mPixelTorchHelper;
    private SeekBar brightnessSlider;
    private TextView brightnessText;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPixelTorchHelper = new PixelTorchHelper(getContext());

        setContentView(R.layout.fragment_torch_brightness);
        brightnessSlider = findViewById(R.id.brightness_slider);
        brightnessText = findViewById(R.id.brightness_text);

        brightnessSlider.setMax(500); // Max brightness
        brightnessSlider.setProgress(mPixelTorchHelper.getTorchBrightness());

        brightnessSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mPixelTorchHelper.setTorchBrightness(progress);
                brightnessText.setText("Brightness: " + progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }
}
