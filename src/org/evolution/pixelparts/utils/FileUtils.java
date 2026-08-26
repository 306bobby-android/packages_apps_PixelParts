/*
 * Copyright (C) 2016 The CyanogenMod Project
 *               2018-2022 crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.pixelparts.utils;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;

public final class FileUtils {

    private static final String TAG = FileUtils.class.getSimpleName();

    private FileUtils() {
    }

    /**
     * Reads the first line of text from the given file.
     * Reference {@link BufferedReader#readLine()} for clarification on what a line is
     *
     * @return the read line contents, or null on failure
     */
    public static String readOneLine(String fileName) {
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName), 512)) {
            return reader.readLine();
        } catch (FileNotFoundException e) {
            Log.w(TAG, "No such file " + fileName + " for reading");
            return null;
        } catch (IOException e) {
            Log.e(TAG, "Could not read from file " + fileName, e);
            return null;
        }
    }

    /**
     * Writes a value to the given node, skipping the write when the node
     * already holds it.
     *
     * @return true if the node holds the value once this returns
     */
    public static boolean writeValueIfChanged(String fileName, String value) {
        if (fileName == null) {
            Log.w(TAG, "Filename is null, write operation aborted");
            return false;
        }
        if (value.equals(readOneLine(fileName))) {
            return true;
        }
        try (FileOutputStream fos = new FileOutputStream(new File(fileName))) {
            fos.write(value.getBytes());
            fos.flush();
            return true;
        } catch (FileNotFoundException e) {
            Log.w(TAG, "No such file " + fileName + " for writing");
            return false;
        } catch (IOException e) {
            Log.e(TAG, "Could not write to file " + fileName, e);
            return false;
        }
    }

    /**
     * Checks whether the given file exists
     *
     * @return true if exists, false if not
     */
    public static boolean fileExists(String fileName) {
        return new File(fileName).exists();
    }
}
