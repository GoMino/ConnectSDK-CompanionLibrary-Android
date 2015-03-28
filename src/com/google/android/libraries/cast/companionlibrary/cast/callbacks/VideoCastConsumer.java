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

package com.google.android.libraries.cast.companionlibrary.cast.callbacks;

import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.TextTrackStyle;

import java.util.Locale;

/**
 * An interface that extends {@link BaseCastConsumer} and
 * adds callbacks related to the lifecycle of a video-centric application.
 */
public interface VideoCastConsumer extends BaseCastConsumer {

    /**
     * Called when the application is successfully launched or joined. Upon successful connection, a
     * session ID is returned. <code>wasLaunched</code> indicates if the application was launched or
     * joined.
     */
    public void onApplicationConnected(ApplicationMetadata appMetadata,
            String sessionId, boolean wasLaunched);

    /**
     * Called when an application launch has failed. Failure reason is captured in the
     * <code>errorCode</code> argument. Here is a list of possible values:
     * <ul>
     * <li>{@link com.google.android.gms.cast.CastStatusCodes#APPLICATION_NOT_FOUND}
     * <li>{@link com.google.android.gms.cast.CastStatusCodes#APPLICATION_NOT_RUNNING}
     * </ul>
     */
    public void onApplicationConnectionFailed(int errorCode);

    /**
     * Called when an attempt to stop a receiver application has failed.
     */
    public void onApplicationStopFailed(int errorCode);

    /**
     * Called when application status changes. The argument is built by the receiver
     */
    public void onApplicationStatusChanged(String appStatus);

    /**
     * Called when the device's volume is changed. Note not to mix that with the stream's volume
     */
    public void onVolumeChanged(double value, boolean isMute);

    /**
     * Called when the current application has stopped
     */
    public void onApplicationDisconnected(int errorCode);

    /**
     * Called when metadata of the current media changes
     */
    public void onRemoteMediaPlayerMetadataUpdated();

    /**
     * Called when media's status updated.
     */
    public void onRemoteMediaPlayerStatusUpdated();

    /**
     * Called when the data channel callback is removed from the {@link Cast} object.
     */
    public void onNamespaceRemoved();

    /**
     * Called when there is an error sending a message.
     *
     * @param errorCode An error code indicating the reason for the disconnect. One of the error
     * constants defined in CastErrors.
     */
    public void onDataMessageSendFailed(int errorCode);

    /**
     * Called when a message is received from a given {@link CastDevice}.
     *
     * @param message The received payload for the message.
     */
    public void onDataMessageReceived(String message);

    /**
     * Called when the style of the text caption has changed
     * @param style The new style
     */
    public void onTextTrackStyleChanged(TextTrackStyle style);

    /**
     * Called when Close Captions on/off is changed
     */
    public void onTextTrackEnabledChanged(boolean isEnabled);

    /**
     * Called when the locale for the caption has changed
     */
    public void onTextTrackLocaleChanged(Locale locale);

    /**
     * A callback to inform the client of the result of a load request
     *
     * @param statusCode The status code that represents the success or failure of the request.
     * The possible value are defined in
     * {@link com.google.android.gms.common.api.CommonStatusCodes} or
     * {@link com.google.android.gms.cast.CastStatusCodes}.
     * {@link com.google.android.gms.cast.CastStatusCodes#SUCCESS} signifies a successful request.
     */
    public void onMediaLoadResult(int statusCode);

}
