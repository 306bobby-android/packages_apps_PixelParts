/*
 * Copyright (C) 2023-2024 The Evolution X Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.pixelparts.pixeltorch;

import android.content.Context;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

public class PixelTorchTileService extends TileService {

    private static final String TAG = "PixelTorchTile";
    private PixelTorchHelper mPixelTorchHelper;

    @Override
    public void onCreate() {
        super.onCreate();
        mPixelTorchHelper = new PixelTorchHelper(this);
    }

    @Override
    public void onStartListening() {
        updateTile();
    }

    @Override
    public void onClick() {
        mPixelTorchHelper.toggleTorch();
        updateTile();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        int brightness = mPixelTorchHelper.getTorchBrightness();

        if (brightness > 0) {
            tile.setState(Tile.STATE_ACTIVE);
            tile.setLabel("Torch: " + brightness);
        } else {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setLabel("Torch Off");
        }

        tile.updateTile();
    }
}
