/*
 * Copyright (C) 2023-2024 The Evolution X Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.pixelparts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.evolution.pixelparts.utils.ComponentUtils;
import org.evolution.pixelparts.utils.FileUtils;

public class Startup extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();

        ComponentUtils.toggleComponent(
                context,
        );

        ComponentUtils.toggleComponent(
                context,
        );

        ComponentUtils.toggleComponent(
                context,
        );

    }
}
