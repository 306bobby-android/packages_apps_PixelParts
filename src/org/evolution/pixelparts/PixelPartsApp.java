/*
 * Copyright (C) 2024 The Evolution X Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.pixelparts;

import android.app.Application;

import org.evolution.pixelparts.autohbm.AutoHbmController;

/**
 * Entry point for the persistent PixelParts process.
 *
 * <p>Automatic HBM has to follow the light sensor with nothing of ours on
 * screen, which is what the application process is for. Doing it here rather
 * than in a started service means there is no restart window during which the
 * panel is left in whatever state it was in, and no separate notion of
 * "is the service running" to fall out of step with the user setting.
 */
public class PixelPartsApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        AutoHbmController.getInstance(this).start();
    }
}
