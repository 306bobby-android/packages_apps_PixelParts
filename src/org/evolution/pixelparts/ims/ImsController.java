/*
 * Copyright (C) 2024 The Evolution X Project
 *               2024 crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.pixelparts.ims;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Forces the IMS feature set on for uncertified carriers, per subscription,
 * via {@link CarrierConfigManager#overrideConfig}.
 *
 * <p>This only makes Android offer VoLTE, VoWiFi and NR. Whether the modem can
 * register also needs a usable carrier configuration - see mbn/README.md.
 */
public final class ImsController {

    private static final String TAG = "ImsController";

    private static final String PREF_PREFIX = "ims_override_sub_";

    private static ImsController sInstance;

    private final SharedPreferences mPrefs;
    private final CarrierConfigManager mCarrierConfigManager;
    private final SubscriptionManager mSubscriptionManager;
    private final TelephonyManager mTelephonyManager;
    private final Context mContext;

    private ImsController(Context context) {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        mCarrierConfigManager = context.getSystemService(CarrierConfigManager.class);
        mSubscriptionManager = context.getSystemService(SubscriptionManager.class);
        mTelephonyManager = context.getSystemService(TelephonyManager.class);
        mContext = context;
    }

    public static synchronized ImsController getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ImsController(context.getApplicationContext());
        }
        return sInstance;
    }

    public static boolean isSupported(Context context) {
        return context.getPackageManager().hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION);
    }

    /** Re-applies the user's choice when subscriptions change. */
    public void start() {
        // This runs in the system uid, so an escaping exception is expensive.
        try {
            final Executor executor = Executors.newSingleThreadExecutor();
            mSubscriptionManager.addOnSubscriptionsChangedListener(executor,
                    new SubscriptionManager.OnSubscriptionsChangedListener() {
                        @Override
                        public void onSubscriptionsChanged() {
                            try {
                                restoreAll();
                            } catch (RuntimeException e) {
                                Log.e(TAG, "Could not restore IMS overrides", e);
                            }
                        }
                    });
        } catch (RuntimeException e) {
            Log.e(TAG, "Could not listen for subscription changes", e);
        }
    }

    /** Whether the modem can be restarted without rebooting. */
    public boolean canRestartModem() {
        return mContext.getPackageManager().hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS);
    }

    /**
     * Restarts the modem so it re-runs carrier configuration selection, which
     * it otherwise does only when a SIM is first seen.
     *
     * @return true if the restart was requested
     */
    public boolean restartModem() {
        if (!canRestartModem()) {
            return false;
        }
        try {
            mTelephonyManager.rebootModem();
            Log.i(TAG, "Requested modem restart");
            return true;
        } catch (RuntimeException e) {
            // rebootModem() signals every failure as some RuntimeException.
            Log.e(TAG, "Could not restart the modem", e);
            return false;
        }
    }

    /** Active subscriptions, in slot order. Empty when no SIM is present. */
    public List<SubscriptionInfo> getActiveSubscriptions() {
        final List<SubscriptionInfo> subs =
                mSubscriptionManager.getActiveSubscriptionInfoList();
        return subs != null ? subs : Collections.emptyList();
    }

    public boolean isEnabled(int subId) {
        return mPrefs.getBoolean(PREF_PREFIX + subId, false);
    }

    /** Applies or clears the override for one subscription, and remembers it. */
    public void setEnabled(int subId, boolean enabled) {
        mPrefs.edit().putBoolean(PREF_PREFIX + subId, enabled).apply();
        apply(subId, enabled);
    }

    /** Re-applies the user's choice to every active subscription. */
    public void restoreAll() {
        for (SubscriptionInfo info : getActiveSubscriptions()) {
            final int subId = info.getSubscriptionId();
            if (isEnabled(subId) && !isAlreadyApplied(subId)) {
                apply(subId, true);
            }
        }
    }

    /** Whether the config already reads the way we want, so we can skip it. */
    private boolean isAlreadyApplied(int subId) {
        final PersistableBundle config = mCarrierConfigManager.getConfigForSubId(subId);
        return config != null
                && config.getBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL);
    }

    private void apply(int subId, boolean enabled) {
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            return;
        }
        try {
            // A null bundle restores the carrier's own values.
            mCarrierConfigManager.overrideConfig(
                    subId, enabled ? buildOverrides() : null, true /* persistent */);
            Log.i(TAG, (enabled ? "Applied" : "Cleared") + " IMS override for subId " + subId);
        } catch (IllegalArgumentException | SecurityException e) {
            Log.e(TAG, "Could not " + (enabled ? "apply" : "clear")
                    + " IMS override for subId " + subId, e);
        }
    }

    /**
     * What a carrier supporting these features would publish. Availability
     * alone is not enough - provisioning and visibility keep it unreachable.
     */
    private static PersistableBundle buildOverrides() {
        final PersistableBundle bundle = new PersistableBundle();

        // Availability
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VT_AVAILABLE_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL, true);

        // An uncertified carrier will not provision us over the air.
        bundle.putBoolean(
                CarrierConfigManager.KEY_CARRIER_VOLTE_PROVISIONING_REQUIRED_BOOL, false);
        bundle.putBoolean(
                CarrierConfigManager.KEY_CARRIER_VOLTE_OVERRIDE_WFC_PROVISIONING_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_IMS_GBA_REQUIRED_BOOL, false);

        // Keep the platform's own toggles reachable and on by default.
        bundle.putBoolean(CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_HIDE_ENHANCED_4G_LTE_BOOL, false);
        bundle.putBoolean(CarrierConfigManager.KEY_ENHANCED_4G_LTE_ON_BY_DEFAULT_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_ALLOW_TURNOFF_IMS_BOOL, true);

        // Wi-Fi calling
        bundle.putBoolean(CarrierConfigManager.KEY_EDITABLE_WFC_MODE_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_WFC_SUPPORTS_WIFI_ONLY_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_ENABLED_BOOL, true);

        // 5G NSA and SA, plus voice over NR.
        bundle.putIntArray(CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY,
                new int[] {
                        CarrierConfigManager.CARRIER_NR_AVAILABILITY_NSA,
                        CarrierConfigManager.CARRIER_NR_AVAILABILITY_SA,
                });
        bundle.putBoolean(CarrierConfigManager.KEY_VONR_ENABLED_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_VONR_SETTING_VISIBILITY_BOOL, true);

        return bundle;
    }
}
