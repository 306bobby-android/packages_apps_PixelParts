/*
 * Copyright (C) 2023-2024 The Evolution X Project
 *               2024 crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.pixelparts.autohbm;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import org.evolution.pixelparts.R;

public class AutoHbmTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile(AutoHbmController.getInstance(this).isEnabled());
    }

    @Override
    public void onClick() {
        super.onClick();
        final AutoHbmController controller = AutoHbmController.getInstance(this);
        final boolean enabled = !controller.isEnabled();
        controller.setEnabled(enabled);
        updateTile(enabled);
    }

    private void updateTile(boolean enabled) {
        final Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setSubtitle(getString(enabled ? R.string.tile_on : R.string.tile_off));
        tile.updateTile();
    }
}
