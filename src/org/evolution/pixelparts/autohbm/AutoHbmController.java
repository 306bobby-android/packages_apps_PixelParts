/*
 * Copyright (C) 2023-2024 The Evolution X Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.pixelparts.autohbm;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.evolution.pixelparts.Constants;
import org.evolution.pixelparts.utils.FileUtils;

/**
 * Drives the panel's high brightness mode from the ambient light sensor.
 *
 * <p>Owned by {@link org.evolution.pixelparts.PixelPartsApp} rather than by a
 * service: the previous implementation started a plain background service and
 * tracked whether it was running in a static boolean, which went out of step
 * with reality the first time the process was restarted, leaving HBM stuck on
 * or the sensor listener leaked.
 *
 * <p>Both edges are debounced and the release threshold sits below the trigger
 * threshold, so light hovering around the threshold cannot pulse the backlight.
 */
public final class AutoHbmController implements SensorEventListener {

    private static final String TAG = "AutoHbmController";

    private static final int DEFAULT_THRESHOLD = 20000;
    private static final int DEFAULT_ENABLE_TIME = 0;
    private static final int DEFAULT_DISABLE_TIME = 1;

    /**
     * Light has to fall this far below the threshold before HBM is released,
     * as a fraction of the threshold. Without a gap, ambient light sitting on
     * the threshold flips HBM on and off repeatedly and the backlight visibly
     * pulses.
     */
    private static final int HYSTERESIS_PERCENT = 10;
    private static final int HYSTERESIS_FLOOR_LUX = 500;

    private static AutoHbmController sInstance;

    private final Context mContext;
    private final SharedPreferences mPrefs;
    private final SensorManager mSensorManager;
    private final Sensor mLightSensor;
    private final PowerManager mPowerManager;
    private final KeyguardManager mKeyguardManager;
    private final Handler mHandler;

    // Touched only on mHandler.
    private boolean mEnabled;
    private boolean mListening;
    private boolean mHbmActive;
    private long mAboveSince;
    private long mBelowSince;

    private final BroadcastReceiver mScreenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(action)) {
                mHandler.post(() -> updateListening());
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                mHandler.post(() -> {
                    stopListening();
                    // HBM has no meaning with the panel off, and leaving it set
                    // means the next screen on starts at full brightness.
                    if (isSupported()) {
                        setHbm(false);
                    }
                });
            }
        }
    };

    private AutoHbmController(Context context) {
        mContext = context;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        mSensorManager = context.getSystemService(SensorManager.class);
        mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        mPowerManager = context.getSystemService(PowerManager.class);
        mKeyguardManager = context.getSystemService(KeyguardManager.class);

        final HandlerThread thread = new HandlerThread(TAG);
        thread.start();
        mHandler = new Handler(thread.getLooper());
    }

    public static synchronized AutoHbmController getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new AutoHbmController(context.getApplicationContext());
        }
        return sInstance;
    }

    /**
     * Whether this device exposes an HBM node at all. Checked by existence
     * rather than writability, because init may not have handed the node over
     * yet when this is first asked.
     */
    public static boolean isSupported() {
        return FileUtils.fileExists(Constants.NODE_HBM);
    }

    /** Starts tracking the user setting. Safe to call more than once. */
    public void start() {
        // Only a missing light sensor is permanent. The HBM node comes from
        // msm_drm.ko, so it is deliberately not checked here - this runs early
        // enough that a missing node would mean giving up for the whole boot.
        // isSupported() is consulted per decision instead.
        if (mLightSensor == null) {
            Log.i(TAG, "No light sensor, automatic HBM unavailable");
            return;
        }

        final IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        mContext.registerReceiver(mScreenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);

        mHandler.post(() -> {
            mEnabled = mPrefs.getBoolean(Constants.KEY_AUTO_HBM, false);
            if (isSupported()) {
                // Whatever the node was left holding is not ours to trust.
                setHbm(false);
            }
            updateListening();
        });
    }

    public boolean isEnabled() {
        return mPrefs.getBoolean(Constants.KEY_AUTO_HBM, false);
    }

    /** Applies the user setting, persisting it and starting or stopping work. */
    public void setEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(Constants.KEY_AUTO_HBM, enabled).apply();
        mHandler.post(() -> {
            mEnabled = enabled;
            updateListening();
            if (!enabled && isSupported()) {
                setHbm(false);
            }
        });
    }

    private void updateListening() {
        if (mEnabled && mLightSensor != null && isSupported()
                && mPowerManager.isInteractive()) {
            startListening();
        } else {
            stopListening();
        }
    }

    private void startListening() {
        if (mListening) {
            return;
        }
        mAboveSince = 0;
        mBelowSince = 0;
        mSensorManager.registerListener(
                this, mLightSensor, SensorManager.SENSOR_DELAY_NORMAL, mHandler);
        mListening = true;
    }

    private void stopListening() {
        if (!mListening) {
            return;
        }
        mSensorManager.unregisterListener(this);
        mListening = false;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_LIGHT) {
            return;
        }

        final int threshold = mPrefs.getInt(Constants.KEY_AUTO_HBM_THRESHOLD, DEFAULT_THRESHOLD);
        final long enableDelayMs =
                mPrefs.getInt(Constants.KEY_AUTO_HBM_ENABLE_TIME, DEFAULT_ENABLE_TIME) * 1000L;
        final long disableDelayMs =
                mPrefs.getInt(Constants.KEY_AUTO_HBM_DISABLE_TIME, DEFAULT_DISABLE_TIME) * 1000L;

        final float lux = event.values[0];
        final long now = SystemClock.elapsedRealtime();

        // HBM on the lock screen is not wanted, and folding it into the
        // predicate means an appearing keyguard releases HBM through the
        // normal debounced path instead of leaving it stuck on.
        final boolean wantHbm = lux >= threshold && !mKeyguardManager.isKeyguardLocked();

        if (wantHbm) {
            mBelowSince = 0;
            if (mAboveSince == 0) {
                mAboveSince = now;
            }
            if (!mHbmActive && now - mAboveSince >= enableDelayMs) {
                setHbm(true);
            }
            return;
        }

        // Only start the release timer once light is clear of the threshold,
        // so that readings jittering across it hold the current state.
        if (lux >= releaseThreshold(threshold) && !mKeyguardManager.isKeyguardLocked()) {
            return;
        }

        mAboveSince = 0;
        if (mBelowSince == 0) {
            mBelowSince = now;
        }
        if (mHbmActive && now - mBelowSince >= disableDelayMs) {
            setHbm(false);
        }
    }

    private static int releaseThreshold(int threshold) {
        return threshold - Math.max(threshold * HYSTERESIS_PERCENT / 100, HYSTERESIS_FLOOR_LUX);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do nothing
    }

    private void setHbm(boolean enable) {
        mHbmActive = enable;
        mAboveSince = 0;
        mBelowSince = 0;
        FileUtils.writeValueIfChanged(Constants.NODE_HBM, enable ? "1" : "0");
    }
}
