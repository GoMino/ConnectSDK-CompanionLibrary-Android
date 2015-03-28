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

package com.google.android.libraries.cast.companionlibrary.cast.player;

import com.google.android.gms.cast.MediaStatus;
import com.google.android.libraries.cast.companionlibrary.widgets.MiniController.OnMiniControllerChangedListener;

import android.graphics.Bitmap;

/**
 * An interface that can be used to display a remote controller for the video that is playing on
 * the cast device.
 */
public interface VideoCastController {

    public static final int CC_ENABLED = 1;
    public static final int CC_DISABLED = 2;
    public static final int CC_HIDDEN = 3;

    /**
     * Sets the bitmap for the album art
     */
    public void setImage(Bitmap bitmap);

    /**
     * Sets the title
     */
    public void setTitle(String text);

    /**
     * Sets the subtitle
     */
    public void setSubTitle(String text);

    /**
     * Sets the playback state, and the idleReason (this is only used when the state is idle).
     * Values that can be passed to this method are from {@link MediaStatus}
     */
    public void setPlaybackStatus(int state);

    /**
     * Assigns a {@link OnMiniControllerChangedListener} listener to be notified of the changes in
     * the mini controller
     */
    public void setOnVideoCastControllerChangedListener(OnVideoCastControllerListener listener);

    /**
     * Sets the type of stream. {@code streamType} can be
     * {@link com.google.android.gms.cast.MediaInfo#STREAM_TYPE_LIVE} or
     * {@link com.google.android.gms.cast.MediaInfo#STREAM_TYPE_BUFFERED}
     */
    public void setStreamType(int streamType);

    /**
     * Updates the position and total duration for the seekbar that presents the progress of media.
     * Both of these need to be provided in milliseconds.
     */
    public void updateSeekbar(int position, int duration);

    /**
     * Adjust the visibility of control widgets on the UI.
     */
    public void updateControllersStatus(boolean enabled);

    /**
     * Can be used to show a loading icon during processes that could take time.
     */
    public void showLoading(boolean visible);

    /**
     * Closes the activity related to the UI.
     */
    public void closeActivity();

    /**
     * This can be used to adjust the UI for playback of live versus pre-recorded streams. Certain
     * UI widgets may need to be updated when playing a live stream. For example, the progress bar
     * may not be needed for a live stream while it may be required for a pre-recorded stream.
     */
    public void adjustControllersForLiveStream(boolean isLive);

    /**
     * Updates the visual status of the Closed Caption icon. Possible states are provided by
     * <code>CC_ENABLED, CC_DISABLED, CC_HIDDEN</code>
     */
    public void setClosedCaptionState(int status);
}
