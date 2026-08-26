/*
 * Copyright (C) 2024 The Evolution X Project
 *               2024 crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.pixelparts.ims;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.telephony.SubscriptionInfo;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.FooterPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.evolution.pixelparts.R;

import java.util.List;

/**
 * One master switch per SIM, built from the active subscriptions so each can
 * be labelled with the carrier whose configuration it overrides.
 */
public class ImsFragment extends SettingsBasePreferenceFragment {

    private static final String KEY_SIM_CATEGORY = "ims_sim_category";
    private static final String KEY_FOOTER = "ims_footer";
    private static final String KEY_RESTART_MODEM = "ims_restart_modem";

    private ImsController mController;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.ims, rootKey);
        mController = ImsController.getInstance(getContext());

        final Preference restart = findPreference(KEY_RESTART_MODEM);
        if (restart != null) {
            // The modem only notices a new configuration when it re-selects.
            restart.setVisible(mController.canRestartModem());
            restart.setOnPreferenceClickListener(preference -> {
                confirmRestartModem();
                return true;
            });
        }
    }

    private void confirmRestartModem() {
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.ims_restart_modem_title)
                .setMessage(R.string.ims_restart_modem_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.ims_restart_modem_confirm, (dialog, which) -> {
                    final boolean ok = mController.restartModem();
                    Toast.makeText(getContext(),
                            ok ? R.string.ims_restart_modem_done
                               : R.string.ims_restart_modem_failed,
                            Toast.LENGTH_LONG).show();
                })
                .show();
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
        // The controller is the source of truth, not the preference store.
        pref.setPersistent(false);
        pref.setChecked(mController.isEnabled(subId));

        pref.setOnPreferenceChangeListener((preference, newValue) -> {
            final boolean enabled = (Boolean) newValue;
            if (!enabled) {
                applyAndRestart(subId, false);
                return true;
            }
            confirmEnable(subId, (SwitchPreferenceCompat) preference);
            // Only tick once the warning has been accepted.
            return false;
        });
        return pref;
    }

    /** Enabling this changes how emergency calls are carried. */
    private void confirmEnable(int subId, SwitchPreferenceCompat pref) {
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.ims_warning_title)
                .setMessage(R.string.ims_warning_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.ims_warning_confirm, (dialog, which) -> {
                    applyAndRestart(subId, true);
                    pref.setChecked(true);
                })
                .show();
    }

    /** Restarted on both edges: an override only lands when the modem re-registers. */
    private void applyAndRestart(int subId, boolean enabled) {
        mController.setEnabled(subId, enabled);
        final boolean restarted = mController.restartModem();
        Toast.makeText(getContext(),
                restarted ? R.string.ims_restart_modem_done
                          : R.string.ims_restart_modem_failed,
                Toast.LENGTH_LONG).show();
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
