/*
 * Copyright (C) 2024 The Evolution X Project
 *               2024 crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.pixelparts;

import android.app.Application;

import org.evolution.pixelparts.autohbm.AutoHbmController;
import org.evolution.pixelparts.ims.ImsController;

/**
 * Entry point for the persistent PixelParts process.
 *
 * <p>Automatic HBM has to follow the light sensor with nothing of ours on
 * screen, which is what the application process is for. Doing it here rather
 * than in a started service means there is no separate notion of "is the
 * service running" to fall out of step with the user setting.
 *
 * <p>The application is deliberately not android:persistent. A persistent
 * app that throws during onCreate is restarted by the activity manager
 * without limit, which turns any startup bug in here into a boot loop.
 * Without it a failure is logged once and contained, at the cost of the
 * process being killable - {@link Startup} brings it back on the next boot.
 */
public class PixelPartsApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        AutoHbmController.getInstance(this).start();

        if (ImsController.isSupported(this)) {
            ImsController.getInstance(this).start();
        }
    }
}
