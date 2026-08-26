/*
 * Copyright (C) 2023-2024 The Evolution X Project
 *               2024 crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.pixelparts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.evolution.pixelparts.autohbm.AutoHbmActivity;
import org.evolution.pixelparts.autohbm.AutoHbmController;
import org.evolution.pixelparts.autohbm.AutoHbmTileService;
import org.evolution.pixelparts.ims.ImsActivity;
import org.evolution.pixelparts.ims.ImsController;
import org.evolution.pixelparts.utils.ComponentUtils;

public class Startup extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // Auto hbm
        ComponentUtils.toggleComponent(
                context,
                AutoHbmActivity.class,
                AutoHbmController.isSupported()
        );

        ComponentUtils.toggleComponent(
                context,
                AutoHbmTileService.class,
                AutoHbmController.isSupported()
        );

        // IMS
        ComponentUtils.toggleComponent(
                context,
                ImsActivity.class,
                ImsController.isSupported(context)
        );
    }
}
