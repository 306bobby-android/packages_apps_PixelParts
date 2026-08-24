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
 * Forces the IMS feature set on for carriers that have not been certified for
 * this device.
 *
 * <p>Where a Magisk module would set {@code persist.dbg.volte_avail_ovr} and
 * friends, this goes through {@link CarrierConfigManager#overrideConfig}, which
 * is the supported route: it is per subscription rather than global, it is
 * reversible by handing back {@code null}, and the platform stores it
 * persistently so it survives a reboot. The debug properties are a single
 * global switch that no longer distinguishes between SIMs.
 *
 * <p>This is only half of the problem. It makes Android <em>offer</em> VoLTE,
 * VoWiFi and NR; whether the modem can actually register depends on it having a
 * usable carrier configuration, which is what the MBN payload is for. The two
 * are independent - on a permissive network this alone is often enough.
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

    /**
     * Re-applies the user's choice whenever the set of subscriptions changes.
     *
     * <p>The platform persists overrides itself, so this exists for the cases
     * where it drops them: a carrier config update, or a SIM being removed and
     * re-inserted, both land here.
     */
    public void start() {
        final Executor executor = Executors.newSingleThreadExecutor();
        mSubscriptionManager.addOnSubscriptionsChangedListener(executor,
                new SubscriptionManager.OnSubscriptionsChangedListener() {
                    @Override
                    public void onSubscriptionsChanged() {
                        restoreAll();
                    }
                });
    }

    /**
     * Whether the modem can be restarted without rebooting the device.
     */
    public boolean canRestartModem() {
        return mContext.getPackageManager().hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS);
    }

    /**
     * Restarts the modem so it re-runs carrier configuration selection.
     *
     * <p>The modem caches which mcfg it picked, so a configuration that was
     * not on the device when the SIM was first seen is not noticed on its own.
     * The Magisk module deals with this by deleting /data/vendor/radio and
     * qcril.db from its installer; this is the same effect without reaching
     * into another process's data directory.
     *
     * @return true if the restart was requested
     */
    public boolean restartModem() {
        try {
            mTelephonyManager.rebootModem();
            Log.i(TAG, "Requested modem restart");
            return true;
        } catch (IllegalStateException | UnsupportedOperationException | RuntimeException e) {
            // rebootModem() throws outright when the modem does not support it.
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

    /**
     * Re-applies what the user asked for to every active subscription.
     *
     * <p>The platform already persists overrides, so this is belt and braces
     * against them being dropped - a carrier config update or a SIM being
     * re-inserted can reset them.
     */
    public void restoreAll() {
        for (SubscriptionInfo info : getActiveSubscriptions()) {
            final int subId = info.getSubscriptionId();
            if (isEnabled(subId) && !isAlreadyApplied(subId)) {
                apply(subId, true);
            }
        }
    }

    /**
     * Whether the config already reads the way we want it to, either because we
     * applied it or because the carrier publishes it. Keeps
     * {@link #restoreAll()} from re-issuing an override on every subscription
     * change.
     */
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
            // A null bundle drops every previous override and puts the
            // subscription back on the carrier's production values.
            mCarrierConfigManager.overrideConfig(
                    subId, enabled ? buildOverrides() : null, true /* persistent */);
            Log.i(TAG, (enabled ? "Applied" : "Cleared") + " IMS override for subId " + subId);
        } catch (IllegalArgumentException | SecurityException e) {
            Log.e(TAG, "Could not " + (enabled ? "apply" : "clear")
                    + " IMS override for subId " + subId, e);
        }
    }

    /**
     * The values a carrier that supports these features would be publishing.
     *
     * <p>Availability alone is not enough: a carrier config that advertises
     * VoLTE but also demands provisioning, or hides the user-facing toggle,
     * still leaves the feature unreachable. The provisioning and visibility
     * keys are here for that reason.
     */
    private static PersistableBundle buildOverrides() {
        final PersistableBundle bundle = new PersistableBundle();

        // Availability
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VT_AVAILABLE_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL, true);

        // Provisioning. An uncertified carrier will not provision us over the
        // air, so requiring it would leave the features permanently pending.
        bundle.putBoolean(
                CarrierConfigManager.KEY_CARRIER_VOLTE_PROVISIONING_REQUIRED_BOOL, false);
        bundle.putBoolean(
                CarrierConfigManager.KEY_CARRIER_VOLTE_OVERRIDE_WFC_PROVISIONING_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_IMS_GBA_REQUIRED_BOOL, false);

        // Make sure the platform's own toggles are reachable and default on.
        bundle.putBoolean(CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_HIDE_ENHANCED_4G_LTE_BOOL, false);
        bundle.putBoolean(CarrierConfigManager.KEY_ENHANCED_4G_LTE_ON_BY_DEFAULT_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_ALLOW_TURNOFF_IMS_BOOL, true);

        // Wi-Fi calling
        bundle.putBoolean(CarrierConfigManager.KEY_EDITABLE_WFC_MODE_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_WFC_SUPPORTS_WIFI_ONLY_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_ENABLED_BOOL, true);

        // 5G, both non-standalone and standalone, plus voice over NR.
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
