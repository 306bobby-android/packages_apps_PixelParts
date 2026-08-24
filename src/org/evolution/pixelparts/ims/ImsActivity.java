/*
 * Copyright (C) 2024 The Evolution X Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.pixelparts.ims;

import android.os.Bundle;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

public class ImsActivity extends CollapsingToolbarBaseActivity {

    private static final String TAG = "Ims";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getSupportFragmentManager().beginTransaction().replace(
                com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                new ImsFragment(), TAG).commit();
    }
}
