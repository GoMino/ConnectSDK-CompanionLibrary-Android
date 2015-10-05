/*
 * Copyright (C) 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.libraries.cast.companionlibrary.utils;

import com.google.android.libraries.cast.companionlibrary.cast.BaseCastManager;

import android.util.Log;

/**
 * Provides a simple wrapper to control logging in development vs production environment. This
 * library should only use the wrapper methods that this class provides.
 */
public class LogUtils {

    private static final String LOG_PREFIX = "ccl_";
    private static final int LOG_PREFIX_LENGTH = LOG_PREFIX.length();
    private static final int MAX_LOG_TAG_LENGTH = 23;

    //private static final boolean DEBUG = false;
    public static boolean DEBUG = false;

    private LogUtils() {
    }

    public static String makeLogTag(String str) {
        if (str.length() > MAX_LOG_TAG_LENGTH - LOG_PREFIX_LENGTH) {
            return LOG_PREFIX + str.substring(0, MAX_LOG_TAG_LENGTH - LOG_PREFIX_LENGTH - 1);
        }

        return LOG_PREFIX + str;
    }

    /**
     * WARNING: Don't use this when obfuscating class names with Proguard!
     */
    public static String makeLogTag(Class<?> cls) {
        return makeLogTag(cls.getSimpleName());
    }

    @SuppressWarnings("unused")
    public static final void LOGD(final String tag, String message) {
        if (DEBUG || Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, getVersionPrefix() + message);
        }
    }

    @SuppressWarnings("unused")
    public static final void LOGD(final String tag, String message, Throwable cause) {
        if (DEBUG || Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, getVersionPrefix() + message, cause);
        }
    }

    public static final void LOGV(final String tag, String message) {
        if (DEBUG && Log.isLoggable(tag, Log.VERBOSE)) {
            Log.v(tag, getVersionPrefix() + message);
        }
    }

    public static final void LOGV(final String tag, String message, Throwable cause) {
        if (DEBUG && Log.isLoggable(tag, Log.VERBOSE)) {
            Log.v(tag, getVersionPrefix() + message, cause);
        }
    }

    public static final void LOGI(final String tag, String message) {
        Log.i(tag, getVersionPrefix() + message);
    }

    public static final void LOGI(final String tag, String message, Throwable cause) {
        Log.i(tag, message, cause);
    }

    public static final void LOGW(final String tag, String message) {
        Log.w(tag, getVersionPrefix() + message);
    }

    public static final void LOGW(final String tag, String message, Throwable cause) {
        Log.w(tag, getVersionPrefix() + message, cause);
    }

    public static final void LOGE(final String tag, String message) {
        Log.e(tag, getVersionPrefix() + message);
    }

    public static final void LOGE(final String tag, String message, Throwable cause) {
        Log.e(tag, getVersionPrefix() + message, cause);
    }

    public static final String getVersionPrefix(){
        return "[v" + BaseCastManager.getCclVersion() + "] ";
    }

}
