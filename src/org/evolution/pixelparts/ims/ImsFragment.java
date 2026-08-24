/*
 * Copyright (C) 2024 The Evolution X Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.pixelparts.ims;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.telephony.SubscriptionInfo;
import android.text.TextUtils;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.FooterPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.evolution.pixelparts.R;

import java.util.List;

/**
 * One master switch per SIM.
 *
 * <p>The switches are built from the active subscriptions rather than from a
 * fixed pair, so each one can be labelled with the carrier it actually applies
 * to - "SIM 1" is meaningless when the thing being overridden is that carrier's
 * published configuration.
 */
public class ImsFragment extends SettingsBasePreferenceFragment {

    private static final String KEY_SIM_CATEGORY = "ims_sim_category";
    private static final String KEY_FOOTER = "ims_footer";

    private ImsController mController;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.ims, rootKey);
        mController = ImsController.getInstance(getContext());
    }

    @Override
    public void onResume() {
        super.onResume();
        rebuildSwitches();
    }

    private void rebuildSwitches() {
        final PreferenceCategory category = findPreference(KEY_SIM_CATEGORY);
        final FooterPreference footer = findPreference(KEY_FOOTER);
        if (category == null) {
            return;
        }
        category.removeAll();

        final List<SubscriptionInfo> subs = mController.getActiveSubscriptions();
        if (subs.isEmpty()) {
            category.setVisible(false);
            if (footer != null) {
                footer.setTitle(R.string.ims_no_sim_summary);
            }
            return;
        }

        category.setVisible(true);
        if (footer != null) {
            footer.setTitle(R.string.ims_footer_summary);
        }

        for (SubscriptionInfo info : subs) {
            category.addPreference(buildSwitch(info));
        }
    }

    private SwitchPreferenceCompat buildSwitch(SubscriptionInfo info) {
        final Context context = getPreferenceManager().getContext();
        final int subId = info.getSubscriptionId();

        final SwitchPreferenceCompat pref = new SwitchPreferenceCompat(context);
        pref.setKey("ims_override_" + subId);
        pref.setTitle(describe(info));
        pref.setSummary(R.string.ims_switch_summary);
        // The controller is the source of truth, not the preference store, so
        // that a switch cannot drift from the override that is actually applied.
        pref.setPersistent(false);
        pref.setChecked(mController.isEnabled(subId));

        pref.setOnPreferenceChangeListener((preference, newValue) -> {
            final boolean enabled = (Boolean) newValue;
            if (!enabled) {
                mController.setEnabled(subId, false);
                return true;
            }
            confirmEnable(subId, (SwitchPreferenceCompat) preference);
            // Only tick once the warning has been accepted.
            return false;
        });
        return pref;
    }

    /**
     * Enabling this changes how emergency calls are carried, so it is worth one
     * deliberate confirmation rather than a silent flip.
     */
    private void confirmEnable(int subId, SwitchPreferenceCompat pref) {
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.ims_warning_title)
                .setMessage(R.string.ims_warning_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.ims_warning_confirm, (dialog, which) -> {
                    mController.setEnabled(subId, true);
                    pref.setChecked(true);
                })
                .show();
    }

    private String describe(SubscriptionInfo info) {
        final CharSequence carrier = info.getDisplayName();
        if (TextUtils.isEmpty(carrier)) {
            return getString(R.string.ims_sim_slot, info.getSimSlotIndex() + 1);
        }
        return getString(R.string.ims_sim_slot_carrier,
                info.getSimSlotIndex() + 1, carrier.toString());
    }
}
