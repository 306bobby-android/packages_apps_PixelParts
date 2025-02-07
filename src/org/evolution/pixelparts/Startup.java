/*
 * Copyright (C) 2023-2024 The Evolution X Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.pixelparts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.evolution.pixelparts.autohbm.AutoHbmActivity;
import org.evolution.pixelparts.autohbm.AutoHbmFragment;
import org.evolution.pixelparts.autohbm.AutoHbmTileService;
import org.evolution.pixelparts.chargecontrol.ChargeControlFragment;
import org.evolution.pixelparts.saturation.SaturationFragment;
import org.evolution.pixelparts.utils.ComponentUtils;
import org.evolution.pixelparts.utils.FileUtils;

public class Startup extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();

        // Auto hbm
        AutoHbmFragment.toggleAutoHbmService(context);

        ComponentUtils.toggleComponent(
                context,
                AutoHbmActivity.class,
                AutoHbmFragment.isHbmSupported(context)
        );

        ComponentUtils.toggleComponent(
                context,
                AutoHbmTileService.class,
                AutoHbmFragment.isHbmSupported(context)
        );

        // Charge control
        ChargeControlFragment.restoreStartChargingSetting(context);
        ChargeControlFragment.restoreStopChargingSetting(context);

        // Saturation
        SaturationFragment.restoreSaturationSetting(context);
    }
}
