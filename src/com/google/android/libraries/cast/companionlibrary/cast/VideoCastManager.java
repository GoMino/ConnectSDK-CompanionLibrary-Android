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

package com.google.android.libraries.cast.companionlibrary.cast;

import static com.google.android.libraries.cast.companionlibrary.utils.LogUtils.LOGD;
import static com.google.android.libraries.cast.companionlibrary.utils.LogUtils.LOGE;

import com.connectsdk.core.ImageInfo;
import com.connectsdk.core.MediaInfo;
import com.connectsdk.core.MediaInfoWithCustomData;
import com.connectsdk.device.ConnectableDevice;
import com.connectsdk.service.DeviceService;
import com.connectsdk.service.capability.MediaControl;
import com.connectsdk.service.capability.MediaPlayer;
import com.connectsdk.service.capability.VolumeControl;
import com.connectsdk.service.capability.listeners.ResponseListener;
import com.connectsdk.service.command.ServiceCommandError;
import com.connectsdk.service.command.URLServiceSubscription;
import com.connectsdk.service.sessions.WebAppSession;
//import com.google.android.gms.cast.ApplicationMetadata;
//import com.google.android.gms.cast.Cast;
//import com.google.android.gms.cast.Cast.CastOptions.Builder;
//import com.google.android.gms.cast.Cast.MessageReceivedCallback;
//import com.google.android.gms.cast.CastDevice;
//import com.google.android.gms.cast.CastStatusCodes;
//import com.google.android.gms.cast.MediaInfo;
//import com.google.android.gms.cast.MediaMetadata;
//import com.google.android.gms.cast.MediaQueueItem;
//import com.google.android.gms.cast.MediaStatus;
//import com.google.android.gms.cast.MediaTrack;
//import com.google.android.gms.cast.RemoteMediaPlayer;
//import com.google.android.gms.cast.RemoteMediaPlayer.MediaChannelResult;
//import com.google.android.gms.cast.TextTrackStyle;
//import com.google.android.gms.common.ConnectionResult;
//import com.google.android.gms.common.GooglePlayServicesUtil;
//import com.google.android.gms.common.api.ResultCallback;
//import com.google.android.gms.common.api.Status;
//import com.google.android.gms.common.images.WebImage;
import com.google.android.gms.cast.CastStatusCodes;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.libraries.cast.companionlibrary.R;
import com.google.android.libraries.cast.companionlibrary.cast.callbacks.VideoCastConsumer;
import com.google.android.libraries.cast.companionlibrary.cast.callbacks.VideoCastConsumerImpl;
import com.google.android.libraries.cast.companionlibrary.cast.dialog.video.VideoMediaRouteDialogFactory;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.CastException;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.NoConnectionException;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.OnFailedListener;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.TransientNetworkDisconnectionException;
import com.google.android.libraries.cast.companionlibrary.cast.player.MediaAuthService;
import com.google.android.libraries.cast.companionlibrary.cast.player.VideoCastController;
import com.google.android.libraries.cast.companionlibrary.cast.player.VideoCastControllerActivity;
import com.google.android.libraries.cast.companionlibrary.cast.tracks.OnTracksSelectedListener;
import com.google.android.libraries.cast.companionlibrary.cast.tracks.TracksPreferenceManager;
import com.google.android.libraries.cast.companionlibrary.notification.VideoCastNotificationService;
import com.google.android.libraries.cast.companionlibrary.remotecontrol.RemoteControlClientCompat;
import com.google.android.libraries.cast.companionlibrary.remotecontrol.RemoteControlHelper;
import com.google.android.libraries.cast.companionlibrary.remotecontrol.VideoIntentReceiver;
import com.google.android.libraries.cast.companionlibrary.utils.FetchBitmapTask;
import com.google.android.libraries.cast.companionlibrary.utils.LogUtils;
import com.google.android.libraries.cast.companionlibrary.utils.Utils;
import com.google.android.libraries.cast.companionlibrary.widgets.IMiniController;
import com.google.android.libraries.cast.companionlibrary.widgets.MiniController;
import com.google.android.libraries.cast.companionlibrary.widgets.MiniController.OnMiniControllerChangedListener;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources.NotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceScreen;
import android.support.v7.app.MediaRouteDialogFactory;
import android.support.v7.media.MediaRouter.RouteInfo;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.CaptioningManager;
import android.widget.MediaController;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * An abstract subclass of {@link BaseCastManager} that is suitable for casting video contents (it
 * also provides a single custom data channel/namespace if an out-of-band communication is
 * needed).
 * <p>
 * Clients need to initialize this class by calling
 * {@link #initialize(android.content.Context, String, Class, String)} and implement
 * the abstracts methods defined here. To access the (singleton) instance of this class, clients
 * need to call {@link #getInstance()}. After acquiring an instance,  callers can enable a
 * number of features (all features are turned off by default). To do so, call
 * {@link #enableFeatures(int)} and pass an OR-ed expression built from one or more of the
 * following constants:
 * <p>
 * <ul>
 * <li>FEATURE_DEBUGGING: to enable Google Play Services level logging</li>
 * <li>FEATURE_NOTIFICATION: to enable system notifications</li>
 * <li>FEATURE_LOCKSCREEN: to enable lock-screen controls on supported versions</li>
 * <li>FEATURE_WIFI_RECONNECT: to enable reconnection logic</li>
 * <li>FEATURE_CAPTIONS_PREFERENCE: to enable Closed Caption Preference handling logic</li>
 * </ul>
 * Callers can add {@link MiniController} components to their application pages by adding the
 * corresponding widget to their layout xml and then calling <code>addMiniController()</code>. This
 * class manages various states of the remote cast device. Client applications, however, can
 * complement the default behavior of this class by hooking into various callbacks that it provides
 * (see
 * {@link com.google.android.libraries.cast.companionlibrary.cast.callbacks.VideoCastConsumer}).
 * Since the number of these callbacks is usually much larger than what a single application might
 * be interested in, there is a no-op implementation of this interface (see
 * {@link VideoCastConsumerImpl}) that applications can subclass to override only those methods that
 * they are interested in. Since this library depends on the cast functionalities provided by the
 * Google Play services, the library checks to ensure that the right version of that service is
 * installed. It also provides a simple static method {@code checkGooglePlayServices()} that clients
 * can call at an early stage of their applications to provide a dialog for users if they need to
 * update/activate their Google Play Services library. To learn more about this library, please read
 * the documentation that is distributed as part of this library.
 */
public class VideoCastManager extends BaseCastManager implements OnMiniControllerChangedListener, OnFailedListener {

    private static final String TAG = LogUtils.makeLogTag(VideoCastManager.class);

    public static final String EXTRA_HAS_AUTH = "hasAuth";
    public static final String EXTRA_MEDIA = "media";
    public static final String EXTRA_START_POINT = "startPoint";
    public static final String EXTRA_SHOULD_START = "shouldStart";
    public static final String EXTRA_NEXT_PREVIOUS_VISIBILITY_POLICY = "nextPrevPolicy";
    public static final String EXTRA_CUSTOM_DATA = "customData";
    public static final double DEFAULT_VOLUME_STEP = 0.05;
    private static final long PROGRESS_UPDATE_INTERVAL_MS = TimeUnit.SECONDS.toMillis(1);
    private double mVolumeStep = DEFAULT_VOLUME_STEP;
    public static final long DEFAULT_LIVE_STREAM_DURATION_MS = TimeUnit.HOURS.toMillis(2); // 2hrs
    public static final String PREFS_KEY_START_ACTIVITY = "ccl-start-cast-activity";
    private TracksPreferenceManager mTrackManager;
    private ComponentName mMediaEventReceiver;
    private MediaQueue mMediaQueue;
    private MediaControl mMediaStatus;
    private WebAppSession mWebAppSession;
    private Timer mProgressTimer;
    private UpdateProgressTask mProgressTask;
    private int mNextPreviousVisibilityPolicy = VideoCastController.NEXT_PREV_VISIBILITY_POLICY_DISABLED;


    /**
     * Volume can be controlled at two different layers, one is at the "stream" level and one at
     * the "device" level. <code>VolumeType</code> encapsulates these two types.
     *
     * @see {@link #setVolumeType}
     */
    public static enum VolumeType {
        STREAM,
        DEVICE
    }

    private static VideoCastManager sInstance;
    private Class<?> mTargetActivity;
    private final Set<IMiniController> mMiniControllers = Collections.synchronizedSet(new HashSet<IMiniController>());
    private AudioManager mAudioManager;
    private ComponentName mMediaButtonReceiverComponent;
    private MediaPlayer mRemoteMediaPlayer;
    private RemoteControlClientCompat mRemoteControlClientCompat;
    private VolumeType mVolumeType = VolumeType.DEVICE;
    private int mState = MediaControl.PLAYER_STATE_IDLE;
    private int mIdleReason;
    private String mDataNamespace;
    //private Cast.MessageReceivedCallback mDataChannel;
    private final Set<VideoCastConsumer> mVideoConsumers = new CopyOnWriteArraySet<>();
    private final Set<OnTracksSelectedListener> mTracksSelectedListeners = new CopyOnWriteArraySet<>();
    private MediaAuthService mAuthService;
    private long mLiveStreamDuration = DEFAULT_LIVE_STREAM_DURATION_MS;
    private MediaInfo mCurrentMediaInfo;
    private MediaControl.PlayStateStatus mCurrentPlayStateStatus;
    //private MediaQueueItem mPreLoadingItem;

    public static final int QUEUE_OPERATION_LOAD = 1;
    public static final int QUEUE_OPERATION_INSERT_ITEMS = 2;
    public static final int QUEUE_OPERATION_UPDATE_ITEMS = 3;
    public static final int QUEUE_OPERATION_JUMP = 4;
    public static final int QUEUE_OPERATION_REMOVE_ITEM = 5;
    public static final int QUEUE_OPERATION_REMOVE_ITEMS = 6;
    public static final int QUEUE_OPERATION_REORDER = 7;
    public static final int QUEUE_OPERATION_MOVE = 8;
    public static final int QUEUE_OPERATION_APPEND = 9;
    public static final int QUEUE_OPERATION_NEXT = 10;
    public static final int QUEUE_OPERATION_PREV = 11;
    public static final int QUEUE_OPERATION_SET_REPEAT = 12;

    /**
     * Returns the namespace for an additional data namespace that this library can manage for an
     * application to have an out-of-band communication channel with the receiver. Note that this
     * only prepares the sender and your own receiver needs to be able to receive and manage the
     * channel as well. The default implementation is not to set up any additional channel.
     *
     * @return The namespace that the library can manage for the application. If {@code null}, no
     * namespace will be set up.
     */
    protected String getDataNamespace() {
        return null;
    }

    private VideoCastManager() {
    }

    protected VideoCastManager(Context context, String applicationId, Class<?> targetActivity, String dataNamespace) {
        super(context, applicationId);
        LOGD(TAG, "VideoCastManager is instantiated");
        mDataNamespace = dataNamespace;
        if (targetActivity == null) {
            targetActivity = VideoCastControllerActivity.class;
        }
        mTargetActivity = targetActivity;
        mPreferenceAccessor.saveStringToPreference(PREFS_KEY_CAST_ACTIVITY_NAME,
                mTargetActivity.getName());
        if (!TextUtils.isEmpty(mDataNamespace)) {
            mPreferenceAccessor.saveStringToPreference(PREFS_KEY_CAST_CUSTOM_DATA_NAMESPACE,
                    dataNamespace);
        }

        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mMediaButtonReceiverComponent = new ComponentName(mContext, VideoIntentReceiver.class);
    }

    public static synchronized VideoCastManager initialize(Context context, String applicationId, Class<?> targetActivity, String dataNamespace) {
        if (sInstance == null) {
            LOGD(TAG, "New instance of VideoCastManager is created");
//            if (ConnectionResult.SUCCESS != GooglePlayServicesUtil.isGooglePlayServicesAvailable(context)) {
//                String msg = "Couldn't find the appropriate version of Google Play Services";
//                LOGE(TAG, msg);
//            }
            sInstance = new VideoCastManager(context, applicationId, targetActivity, dataNamespace);
            sInstance.restartProgressTimer();
        }
        return sInstance;
    }


    /**
     * Returns a (singleton) instance of this class. Clients should call this method in order to
     * get a hold of this singleton instance, only after it is initialized. If it is not initialized
     * yet, an {@link IllegalStateException} will be thrown.
     *
     */
    public static VideoCastManager getInstance() {
        if (sInstance == null) {
            String msg = "No VideoCastManager instance was found, did you forget to initialize it?";
            LOGE(TAG, msg);
            throw new IllegalStateException(msg);
        }
        return sInstance;
    }

    @Override
    protected void onFeaturesUpdated(int capabilities) {
        if (isFeatureEnabled(FEATURE_CAPTIONS_PREFERENCE)) {
            mTrackManager = new TracksPreferenceManager(mContext.getApplicationContext());
            registerCaptionListener(mContext.getApplicationContext());
        }
    }

    /**
     * Updates the information and state of a MiniController.
     *
     * @throws TransientNetworkDisconnectionException
     * @throws NoConnectionException
     */
    private void updateMiniController(final IMiniController controller)
            throws TransientNetworkDisconnectionException, NoConnectionException {
        checkConnectivity();
        checkRemoteMediaPlayerAvailable();
        getMediaDuration(new MediaControl.DurationListener() {
            @Override
            public void onSuccess(Long duration) {
                try {
                    if ( duration> 0 || isRemoteStreamLive()) {
                        MediaInfo mediaInfo = getRemoteMediaInformation();
                        if (mediaInfo != null) {
                            //MediaMetadata mm = mediaInfo.getMetadata();
                            if(mediaInfo instanceof MediaInfoWithCustomData){
                                controller.setStreamType(((MediaInfoWithCustomData)mediaInfo).getStreamType());
                            }
                            controller.setPlaybackStatus(mState, mIdleReason);
                            controller.setSubtitle(mContext.getResources().getString(R.string.ccl_casting_to_device, mDeviceName));
                            controller.setTitle(mediaInfo.getTitle());
                            controller.setIcon(Utils.getImageUri(mediaInfo, 0));
                            LOGE(TAG, "updateMiniControllers() title:" + mediaInfo.getTitle() + " imageUrl:" + Utils.getImageUri(mediaInfo, 0));
                        }
                    }
                } catch (TransientNetworkDisconnectionException e) {
                    e.printStackTrace();
                } catch (NoConnectionException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onError(ServiceCommandError serviceCommandError) {
                LOGE(TAG, "updateMiniControllers() getting duration error:"+ serviceCommandError);
            }
        });
    }

    /*
     * Updates the information and state of all MiniControllers
     */
    private void updateMiniControllers() {
        synchronized (mMiniControllers) {
            for (final IMiniController controller : mMiniControllers) {
                try {
                    updateMiniController(controller);
                } catch (TransientNetworkDisconnectionException | NoConnectionException e) {
                    LOGE(TAG, "updateMiniControllers() Failed to update mini controller", e);
                }
            }
        }
    }

    /*
     * (non-Javadoc)
     * @see com.google.android.libraries.cast.companionlibrary.widgets.MiniController.
     * OnMiniControllerChangedListener#onPlayPauseClicked(android.view.View)
     * @throws TransientNetworkDisconnectionException
     * @throws NoConnectionException
     * @throws CastException
     */
    @Override
    public void onPlayPauseClicked(View v) throws CastException,
            TransientNetworkDisconnectionException, NoConnectionException {
        checkConnectivity();
        if (mState == MediaControl.PLAYER_STATE_PLAYING) {
            pause();
        } else {
            boolean isLive = isRemoteStreamLive();
            //if ((mState == MediaControl.PLAYER_STATE_PAUSED && !isLive) || (mState == MediaControl.PLAYER_STATE_IDLE && isLive)) {
                play();
            //}
        }
    }

    /*
     * (non-Javadoc)
     * @see com.google.android.libraries.cast.companionlibrary.widgets.MiniController.
     * OnMiniControllerChangedListener #onTargetActivityInvoked(android.content.Context)
     */
    @Override
    public void onTargetActivityInvoked(Context context) throws
            TransientNetworkDisconnectionException, NoConnectionException {
        Intent intent = new Intent(context, mTargetActivity);
        intent.putExtra(EXTRA_MEDIA, Utils.mediaInfoToBundle(getRemoteMediaInformation()));
        context.startActivity(intent);
    }

//    @Override
//    public void onUpcomingPlayClicked(View view, MediaQueueItem upcomingItem) {
//        for (VideoCastConsumer consumer : mVideoConsumers) {
//            consumer.onUpcomingPlayClicked(view, upcomingItem);
//        }
//    }
//
//    @Override
//    public void onUpcomingStopClicked(View view, MediaQueueItem upcomingItem) {
//        for (VideoCastConsumer consumer : mVideoConsumers) {
//            consumer.onUpcomingStopClicked(view, upcomingItem);
//        }
//    }

    /**
     * Updates the visibility of the mini controllers. In most cases, clients do not need to use
     * this as the {@link VideoCastManager} handles the visibility.
     *
     * @param visible If {@link true}, mini controllers wil be visible; hidden otherwise.
     */
    public void updateMiniControllersVisibility(boolean visible) {
        LOGD(TAG, "updateMiniControllersVisibility() reached with visibility: " + visible);
        synchronized (mMiniControllers) {
            for (IMiniController controller : mMiniControllers) {
                controller.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
        }
    }

//    public void updateMiniControllersVisibilityForUpcoming(MediaQueueItem item) {
//        synchronized (mMiniControllers) {
//            for (IMiniController controller : mMiniControllers) {
//                controller.setUpcomingItem(item);
//                controller.setUpcomingVisibility(item != null);
//            }
//        }
//    }

    /**
     * Sets an internal flag that is used to disambiguate the two cases that the
     * {@code VideoCastControllerActivity} is started programmatically or through the system (say,
     * after configuration change or from recent history).
     */
    private void setFlagForStartCastControllerActivity() {
        mPreferenceAccessor.saveBooleanToPreference(PREFS_KEY_START_ACTIVITY, true);
    }

    /**
     * Launches the VideoCastControllerActivity that provides a default Cast Player page.
     *
     * @param context The context to use for starting the activity
     * @param mediaWrapper a bundle wrapper for the media that is or will be casted
     * @param position Starting point, in milliseconds,  of the media playback
     * @param shouldStart indicates if the remote playback should start after launching the new
     * page
     * @param customData Optional {@link JSONObject}
     */
    public void startVideoCastControllerActivity(Context context, Bundle mediaWrapper, int position,
            boolean shouldStart, JSONObject customData) {
        Intent intent = new Intent(context, getTargetActivity());
        intent.putExtra(EXTRA_MEDIA, mediaWrapper);
        intent.putExtra(EXTRA_START_POINT, position);
        intent.putExtra(EXTRA_SHOULD_START, shouldStart);
        intent.putExtra(EXTRA_NEXT_PREVIOUS_VISIBILITY_POLICY, mNextPreviousVisibilityPolicy);
        if (customData != null) {
            intent.putExtra(EXTRA_CUSTOM_DATA, customData.toString());
        }
        setFlagForStartCastControllerActivity();
        context.startActivity(intent);
    }

    /**
     * Launches the {@link VideoCastControllerActivity} that provides a default Cast Player page.
     *
     * @param context The context to use for starting the activity
     * @param mediaWrapper A bundle wrapper for the media that is or will be casted
     * @param position Starting point, in milliseconds,  of the media playback
     * @param shouldStart Indicates if the remote playback should start after launching the new
     * page
     */
    public void startVideoCastControllerActivity(Context context, Bundle mediaWrapper, int position,
            boolean shouldStart) {
        startVideoCastControllerActivity(context, mediaWrapper, position, shouldStart, null);
    }

    /**
     * Launches the {@link VideoCastControllerActivity} that provides a default Cast Player page.
     * This variation should be used when an
     * {@link com.google.android.libraries.cast.companionlibrary.cast.player.MediaAuthService}
     * needs to be used.
     */
    public void startVideoCastControllerActivity(Context context, MediaAuthService authService) {
        if (authService != null) {
            mAuthService = authService;
            Intent intent = new Intent(context, getTargetActivity());
            intent.putExtra(EXTRA_HAS_AUTH, true);
            setFlagForStartCastControllerActivity();
            context.startActivity(intent);
        }
    }

    /**
     * Launches the {@link VideoCastControllerActivity} that provides a default Cast Player page.
     *
     * @param context The context to use for starting the activity
     * @param mediaInfo The media that is or will be casted
     * @param position Starting point, in milliseconds,  of the media playback
     * @param shouldStart Indicates if the remote playback should start after launching the new page
     */
    public void startVideoCastControllerActivity(Context context, MediaInfo mediaInfo, int position, boolean shouldStart) {
        startVideoCastControllerActivity(context, Utils.mediaInfoToBundle(mediaInfo), position, shouldStart);
    }

    /**
     * Returns the instance of
     * {@link com.google.android.libraries.cast.companionlibrary.cast.player.MediaAuthService},
     * or null if there is no such instance.
     */
    public MediaAuthService getMediaAuthService() {
        return mAuthService;
    }

    /**
     * Sets the
     * {@link com.google.android.libraries.cast.companionlibrary.cast.player.MediaAuthService}.
     */
    public void setMediaAuthService(MediaAuthService authService) {
        mAuthService = authService;
    }

    /**
     * Removes the pointer to the
     * {@link com.google.android.libraries.cast.companionlibrary.cast.player.MediaAuthService} to
     * avoid any leak.
     */
    public void removeMediaAuthService() {
        mAuthService = null;
    }

    /**
     * Returns the active {@link MediaPlayer} instance. Since there are a number of media
     * control APIs that this library do not provide a wrapper for, client applications can call
     * those methods directly after obtaining an instance of the active {@link MediaPlayer}.
     */
    public final MediaPlayer getRemoteMediaPlayer() {
        return mRemoteMediaPlayer;
    }

    /**
     * Determines if the media that is loaded remotely is a live stream or not.
     *
     * @throws TransientNetworkDisconnectionException
     * @throws NoConnectionException
     */
    public final boolean isRemoteStreamLive() throws TransientNetworkDisconnectionException,
            NoConnectionException {
        checkConnectivity();
        MediaInfo info = getRemoteMediaInformation();
        if(info instanceof MediaInfoWithCustomData) {
            return (info != null) && (((MediaInfoWithCustomData)info).getStreamType() == MediaInfoWithCustomData.STREAM_TYPE_LIVE);
        }
        return false;
    }

    /**
     * A helper method to determine if, given a player state and an idle reason (if the state is
     * idle) will warrant having a UI for remote presentation of the remote content.
     *
     * @throws TransientNetworkDisconnectionException
     * @throws NoConnectionException
     */
    public boolean shouldRemoteUiBeVisible(int state, int idleReason)
            throws TransientNetworkDisconnectionException, NoConnectionException {
        switch (state) {
            case MediaControl.PLAYER_STATE_PLAYING:
            case MediaControl.PLAYER_STATE_PAUSED:
            case MediaControl.PLAYER_STATE_BUFFERING:
                return true;
            case MediaControl.PLAYER_STATE_IDLE:
                return isRemoteStreamLive() /*&& (idleReason == MediaControl.IDLE_REASON_CANCELED)*/;
            default:
        }
        return false;
    }

    /*
     * A simple check to make sure mRemoteMediaPlayer is not null
     */
    private void checkRemoteMediaPlayerAvailable() throws NoConnectionException {
        if (mRemoteMediaPlayer == null || mMediaStatus==null) {
            throw new NoConnectionException();
        }
    }

    /**
     * Sets the type of volume. Most applications should use {@code VolumeType.DEVICE} (which is
     * the default value) but in rare cases, an application can set the type to {@code
     * VolumeType.STREAM}
     */
    public final void setVolumeType(VolumeType volumeType) {
        mVolumeType = volumeType;
    }

    /**
     * Returns the url for the movie that is currently playing on the remote device. If there is no
     * connection, this will return <code>null</code>.
     *
     * @throws NoConnectionException If no connectivity to the device exists
     * @throws TransientNetworkDisconnectionException If framework is still trying to recover from
     * a possibly transient loss of network
     */
    public String getRemoteMediaUrl() throws TransientNetworkDisconnectionException, NoConnectionException {
        checkConnectivity();
//        if (mRemoteMediaPlayer != null && mRemoteMediaPlayer.getMediaInfo() != null) {
//            MediaInfo info = mRemoteMediaPlayer.getMediaInfo();
//            mRemoteMediaPlayer.getMediaStatus().getPlayerState();
//            return info.getUrl();
//        }
        String url = null;
        if(getRemoteMediaInformation()!=null) {
            url = getRemoteMediaInformation().getUrl();
        }
        return url;
    }

    /**
     * Indicates if the remote movie is currently playing (or buffering).
     *
     * @throws NoConnectionException
     * @throws TransientNetworkDisconnectionException
     */
    public boolean isRemoteMediaPlaying() throws TransientNetworkDisconnectionException,
            NoConnectionException {
        checkConnectivity();
        return mState == MediaControl.PLAYER_STATE_BUFFERING
                || mState == MediaControl.PLAYER_STATE_PLAYING;
    }

    /**
     * Returns <code>true</code> if the remote connected device is playing a movie.
     *
     * @throws NoConnectionException
     * @throws TransientNetworkDisconnectionException
     */
    public boolean isRemoteMediaPaused() throws TransientNetworkDisconnectionException,
            NoConnectionException {
        checkConnectivity();
        return mState == MediaControl.PLAYER_STATE_PAUSED;
    }

    /**
     * Returns <code>true</code> only if there is a media on the remote being played, paused or
     * buffered.
     *
     * @throws NoConnectionException
     * @throws TransientNetworkDisconnectionException
     */
    public boolean isRemoteMediaLoaded() throws TransientNetworkDisconnectionException,
            NoConnectionException {
        checkConnectivity();
        return isRemoteMediaPaused() || isRemoteMediaPlaying();
    }

    /**
     * Returns the {@link MediaInfo} for the current media
     *
     * @throws NoConnectionException If no connectivity to the device exists
     * @throws TransientNetworkDisconnectionException If framework is still trying to recover from
     * a possibly transient loss of network
     */
    public MediaInfo getRemoteMediaInformation() throws TransientNetworkDisconnectionException, NoConnectionException {
        checkConnectivity();
        checkRemoteMediaPlayerAvailable();
        if(mState == MediaControl.PLAYER_STATE_IDLE)
            return null;
        return mCurrentMediaInfo;
    }

    /**
     * Gets the remote's system volume. It internally detects what type of volume is used.
     *
     * @throws NoConnectionException If no connectivity to the device exists
     * @throws TransientNetworkDisconnectionException If framework is still trying to recover from
     * a possibly transient loss of network
     */
    public void getVolume(VolumeControl.VolumeListener listener) throws TransientNetworkDisconnectionException, NoConnectionException {
        checkConnectivity();
//        if (mVolumeType == VolumeType.STREAM) {
//            checkRemoteMediaPlayerAvailable();
//            return mRemoteMediaPlayer.getMediaStatus().getStreamVolume();
//        }
        getDeviceVolume(listener);
    }

    /**
     * Sets the volume. It internally determines if this should be done for <code>stream</code> or
     * <code>device</code> volume.
     *
     * @param volume Should be a value between 0 and 1, inclusive.
     * @throws NoConnectionException
     * @throws TransientNetworkDisconnectionException
     * @throws CastException If setting system volume fails
     *
     * @see {link #setVolumeType()}
     */
    public void setVolume(double volume) throws CastException,
            TransientNetworkDisconnectionException, NoConnectionException {
        checkConnectivity();
        if (volume > 1.0) {
            volume = 1.0;
        } else if (volume < 0) {
            volume = 0.0;
        }
//        if (mVolumeType == VolumeType.STREAM) {
//            checkRemoteMediaPlayerAvailable();
//            mRemoteMediaPlayer.setStreamVolume(mApiClient, volume).setResultCallback(
//                    new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
//
//                        @Override
//                        public void onResult(MediaChannelResult result) {
//                            if (!result.getStatus().isSuccess()) {
//                                onFailed(R.string.ccl_failed_setting_volume,
//                                        result.getStatus().getStatusCode());
//                            }
//                        }
//                    }
//            );
//        } else {
            setDeviceVolume(volume);
//        }
    }

    /**
     * Increments (or decrements) the volume by the given amount. It internally determines if this
     * should be done for stream or device volume.
     *
     * @throws NoConnectionException If no connectivity to the device exists
     * @throws TransientNetworkDisconnectionException If framework is still trying to recover from
     * a possibly transient loss of network
     * @throws CastException
     */
    public void adjustVolume(final double delta) throws CastException,
            TransientNetworkDisconnectionException, NoConnectionException {
        checkConnectivity();
        getVolume(new VolumeControl.VolumeListener() {
            @Override
            public void onSuccess(Float volume) {
                double vol = volume + delta;
                if (vol > 1) {
                    vol = 1;
                } else if (vol < 0) {
                    vol = 0;
                }
                try {
                    setVolume(vol);
                } catch (CastException e) {
                    e.printStackTrace();
                } catch (TransientNetworkDisconnectionException e) {
                    e.printStackTrace();
                } catch (NoConnectionException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onError(ServiceCommandError serviceCommandError) {

            }
        });
    }

    /**
     * Increments or decrements volume by <code>delta</code> if {@code delta < 0} or
     * {@code delta > 0}, respectively. Note that the volume range is between 0 and {@code
     * RouteInfo.getVolumeMax()}.
     */
    public void updateVolume(int delta) {
        RouteInfo info = mMediaRouter.getSelectedRoute();
        //RouteInfo info = getRouteInfo();
        info.requestUpdateVolume(delta);
    }

    /**
     * Returns <code>true</code> if remote device is muted. It internally determines if this should
     * be done for <code>stream</code> or <code>device</code> volume.
     *
     * @throws NoConnectionException
     * @throws TransientNetworkDisconnectionException
     */
    public void isMute(VolumeControl.MuteListener listener) throws TransientNetworkDisconnectionException, NoConnectionException {
        checkConnectivity();
//        if (mVolumeType == VolumeType.STREAM) {
//            checkRemoteMediaPlayerAvailable();
//            return mRemoteMediaPlayer.getMediaStatus().isMute();
//        } else {
            isDeviceMute(listener);
//        }
    }

    /**
     * Mutes or un-mutes the volume. It internally determines if this should be done for
     * <code>stream</code> or <code>device</code> volume.
     *
     * @throws CastException
     * @throws NoConnectionException
     * @throws TransientNetworkDisconnectionException
     */
    public void setMute(boolean mute) throws CastException, TransientNetworkDisconnectionException,
            NoConnectionException {
        checkConnectivity();
//        if (mVolumeType == VolumeType.STREAM) {
//            checkRemoteMediaPlayerAvailable();
//            mRemoteMediaPlayer.setStreamMute(mApiClient, mute);
//        } else {
            setDeviceMute(mute);
//        }
    }

    /**
     * Returns the duration of the media that is loaded, in milliseconds.
     *
     * @throws NoConnectionException
     * @throws TransientNetworkDisconnectionException
     */
    public void getMediaDuration(MediaControl.DurationListener listener) throws TransientNetworkDisconnectionException, NoConnectionException {
        checkConnectivity();
        checkRemoteMediaPlayerAvailable();
        //return mRemoteMediaPlayer.getStreamDuration();
        mMediaStatus.getDuration(listener);
    }


    /**
     * Returns the time left (in milliseconds) of the current media. If there is no
     * {@code RemoteMediaPlayer}, it returns -1.
     *
     * @throws TransientNetworkDisconnectionException
     * @throws NoConnectionException
     */
    public void getMediaTimeRemaining(final ResponseListener<Long> listener) throws TransientNetworkDisconnectionException, NoConnectionException {
        checkConnectivity();
        if (mRemoteMediaPlayer == null) {
            //return -1;
            listener.onError(new ServiceCommandError(-1, "connect to remote media player first"));
        }
        if(isRemoteStreamLive()) {
            //return mLiveStreamDuration;
            listener.onSuccess(mLiveStreamDuration);
        } else {
            //return getMediaDuration() - getCurrentMediaPosition();
            getMediaDuration(new MediaControl.DurationListener() {
                @Override
                public void onSuccess(Long duration) {
                    final long mediaDuration = duration;
                    try {
                        getCurrentMediaPosition(new MediaControl.PositionListener() {
                            @Override
                            public void onSuccess(Long position) {
                                final long mediaPosition = position;
                                listener.onSuccess(mediaDuration - mediaPosition);
                            }

                            @Override
                            public void onError(ServiceCommandError serviceCommandError) {
                                listener.onError(serviceCommandError);
                            }
                        });
                    } catch (TransientNetworkDisconnectionException | NoConnectionException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onError(ServiceCommandError serviceCommandError) {
                    listener.onError(serviceCommandError);
                }
            });
        }
    }

    /**
     * Returns the current (approximate) position of the current media, in milliseconds.
     *
     * @throws NoConnectionException
     * @throws TransientNetworkDisconnectionException
     */
    public long getCurrentMediaPosition() throws TransientNetworkDisconnectionException, NoConnectionException {
        checkConnectivity();
        checkRemoteMediaPlayerAvailable();
        //return mRemoteMediaPlayer.getApproximateStreamPosition();

        final Long[] result = new Long[1];
        result[0] = -1l;
        final CountDownLatch positionLatch = new CountDownLatch(1);
        try {

            mMediaStatus.getPosition(new MediaControl.PositionListener() {
                @Override
                public void onSuccess(Long duration) {
                    result[0] = duration;
                    positionLatch.countDown();
                }

                @Override
                public void onError(ServiceCommandError serviceCommandError) {
                    positionLatch.countDown();
                }
            });
            positionLatch.await();


        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return result[0];
    }

    public void getCurrentMediaPosition(MediaControl.PositionListener listener) throws TransientNetworkDisconnectionException, NoConnectionException {
        checkConnectivity();
        checkRemoteMediaPlayerAvailable();
        //return mRemoteMediaPlayer.getApproximateStreamPosition();
        mMediaStatus.getPosition(listener);
    }

    /*
     * Starts a service that can last beyond the lifetime of the application to provide
     * notifications. The service brings itself down when needed. The service will be started only
     * if the notification feature has been enabled during the initialization.
     * @see {@link BaseCastManager#enableFeatures()}
     */
    private boolean startNotificationService() {
        if (!isFeatureEnabled(FEATURE_NOTIFICATION)) {
            return true;
        }
        LOGD(TAG, "startNotificationService()");
        Intent service = new Intent(mContext, VideoCastNotificationService.class);
        service.setPackage(mContext.getPackageName());
        service.setAction(VideoCastNotificationService.ACTION_VISIBILITY);
        service.putExtra(VideoCastNotificationService.NOTIFICATION_VISIBILITY, !mUiVisible);
        return mContext.startService(service) != null;
    }

    private void stopNotificationService() {
        if (!isFeatureEnabled(FEATURE_NOTIFICATION)) {
            return;
        }
        if (mContext != null) {
            mContext.stopService(new Intent(mContext, VideoCastNotificationService.class));
        }
    }

    private void onApplicationDisconnected(int errorCode) {
        LOGD(TAG, "onApplicationDisconnected() reached with error code: " + errorCode);
        updateRemoteControl(false);
        if (mRemoteControlClientCompat != null && isFeatureEnabled(FEATURE_LOCKSCREEN)) {
            mRemoteControlClientCompat.removeFromMediaRouter(mMediaRouter);
        }
        for (VideoCastConsumer consumer : mVideoConsumers) {
            consumer.onApplicationDisconnected(errorCode);
        }
        if (mMediaRouter != null) {
            LOGD(TAG, "onApplicationDisconnected(): Cached RouteInfo: " + getRouteInfo());
            LOGD(TAG, "onApplicationDisconnected(): Selected RouteInfo: "
                    + mMediaRouter.getSelectedRoute());
            if (getRouteInfo() == null || mMediaRouter.getSelectedRoute().equals(getRouteInfo())) {
                LOGD(TAG, "onApplicationDisconnected(): Setting route to default");
                mMediaRouter.selectRoute(mMediaRouter.getDefaultRoute());
            }
        }
        onDeviceSelected(null);
        updateMiniControllersVisibility(false);
        stopNotificationService();
    }

//    private void onApplicationStatusChanged() {
//        if (!isConnected()) {
//            return;
//        }
//        try {
//            String appStatus = Cast.CastApi.getApplicationStatus(mApiClient);
//            LOGD(TAG, "onApplicationStatusChanged() reached: " + appStatus);
//            for (VideoCastConsumer consumer : mVideoConsumers) {
//                consumer.onApplicationStatusChanged(appStatus);
//            }
//        } catch (IllegalStateException e) {
//            LOGE(TAG, "onApplicationStatusChanged()", e);
//        }
//    }

    private void onVolumeChanged() {
        LOGD(TAG, "onVolumeChanged() reached");
        double volume;
        try {
            getVolume(new VolumeControl.VolumeListener() {
                @Override
                public void onSuccess(final Float volume) {
                    try {
                        isMute(new VolumeControl.MuteListener() {
                            @Override
                            public void onSuccess(Boolean isMute) {
                                for (VideoCastConsumer consumer : mVideoConsumers) {
                                    consumer.onVolumeChanged(volume, isMute);
                                }
                            }

                            @Override
                            public void onError(ServiceCommandError serviceCommandError) {

                            }
                        });
                    } catch (TransientNetworkDisconnectionException | NoConnectionException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onError(ServiceCommandError serviceCommandError) {
                    LOGE(TAG, "Failed to get volume", serviceCommandError);
                }
            });

        } catch (TransientNetworkDisconnectionException | NoConnectionException e) {
            LOGE(TAG, "Failed to get volume", e);
        }
    }

    @Override
    protected void onApplicationConnected(WebAppSession webAppSession, WebAppSession.WebAppStatus status) {
        LOGD(TAG, "onApplicationConnected() reached with sessionId: " + webAppSession.launchSession.getSessionId() + ", and mReconnectionStatus=" + mReconnectionStatus);

        if (mReconnectionStatus == RECONNECTION_STATUS_IN_PROGRESS) {
            // we have tried to reconnect and successfully launched the app, so
            // it is time to select the route and make the cast icon happy :-)
            List<RouteInfo> routes = mMediaRouter.getRoutes();
            if (routes != null) {
                String routeId = mPreferenceAccessor.getStringFromPreference(PREFS_KEY_ROUTE_ID);
                for (RouteInfo routeInfo : routes) {
                    if (routeId.equals(routeInfo.getId())) {
                        // found the right route
                        LOGD(TAG, "Found the correct route during reconnection attempt");
                        mReconnectionStatus = RECONNECTION_STATUS_FINALIZED;
                        mMediaRouter.selectRoute(routeInfo);
                        break;
                    }
                }
            }
        }
        startNotificationService();
        try {
            mWebAppSession = webAppSession;
            mMediaStatus = webAppSession.getMediaControl();
            mSessionId =  webAppSession.launchSession.getSessionId();

            //attachDataChannel();
            attachMediaChannel();


            // saving device for future retrieval; we only save the last session info
            mPreferenceAccessor.saveStringToPreference(PREFS_KEY_SESSION_ID, mSessionId);
//            mRemoteMediaPlayer.requestStatus(mApiClient).
//                    setResultCallback(new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
//
//                        @Override
//                        public void onResult(MediaChannelResult result) {
//                            if (!result.getStatus().isSuccess()) {
//                                onFailed(R.string.ccl_failed_status_request, result.getStatus().getStatusCode());
//                            }
//
//                        }
//                    });
            for (VideoCastConsumer consumer : mVideoConsumers) {
                consumer.onApplicationConnected(webAppSession, status);
            }
        } catch (TransientNetworkDisconnectionException e) {
            LOGE(TAG, "Failed to attach media/data channel due to network issues", e);
            onFailed(R.string.ccl_failed_no_connection_trans, NO_STATUS_CODE);
        } catch (NoConnectionException e) {
            LOGE(TAG, "Failed to attach media/data channel due to network issues", e);
            onFailed(R.string.ccl_failed_no_connection, NO_STATUS_CODE);
        }

    }

    /*
     * (non-Javadoc)
     * @see com.google.android.libraries.cast.companionlibrary.cast.BaseCastManager
     * #onConnectivityRecovered()
     */
    @Override
    public void onConnectivityRecovered() {
        reattachMediaChannel();
        //reattachDataChannel();
        super.onConnectivityRecovered();
    }

    /*
     * (non-Javadoc)
     * @see com.google.android.gms.cast.CastClient.Listener#onApplicationStopFailed (int)
     */
    @Override
    public void onApplicationStopFailed(int errorCode) {
        for (VideoCastConsumer consumer : mVideoConsumers) {
            consumer.onApplicationStopFailed(errorCode);
        }
    }

    @Override
    public void onApplicationConnectionFailed(int errorCode) {
        LOGD(TAG, "onApplicationConnectionFailed() reached with errorCode: " + errorCode);
        if (mReconnectionStatus == RECONNECTION_STATUS_IN_PROGRESS) {
            if (errorCode == CastStatusCodes.APPLICATION_NOT_RUNNING) {
                // while trying to re-establish session, we found out that the app is not running
                // so we need to disconnect
                mReconnectionStatus = RECONNECTION_STATUS_INACTIVE;
                onDeviceSelected(null);
            }
        } else {
            for (VideoCastConsumer consumer : mVideoConsumers) {
                consumer.onApplicationConnectionFailed(errorCode);
            }
            onDeviceSelected(null);
            if (mMediaRouter != null) {
                LOGD(TAG, "onApplicationConnectionFailed(): Setting route to default");
                mMediaRouter.selectRoute(mMediaRouter.getDefaultRoute());
            }
        }
    }

    /**
     * Loads a media. For this to succeed, you need to have successfully launched the application.
     *
     * @param media The media to be loaded
     * @param autoPlay If <code>true</code>, playback starts after load
     * @param position Where to start the playback (only used if autoPlay is <code>true</code>.
     * Units is milliseconds.
     * @throws NoConnectionException
     * @throws TransientNetworkDisconnectionException
     */
    public void loadMedia(MediaInfo media, boolean autoPlay, int position)
            throws TransientNetworkDisconnectionException, NoConnectionException {
        loadMedia(media, autoPlay, position, null);
    }

    /**
     * Loads a media. For this to succeed, you need to have successfully launched the application.
     *
     * @param media The media to be loaded
     * @param autoPlay If <code>true</code>, playback starts after load
     * @param position Where to start the playback (only used if autoPlay is <code>true</code>).
     * Units is milliseconds.
     * @param customData Optional {@link JSONObject} data to be passed to the cast device
     * @throws NoConnectionException
     * @throws TransientNetworkDisconnectionException
     */
    public void loadMedia(MediaInfo media, boolean autoPlay, int position, JSONObject customData)
            throws TransientNetworkDisconnectionException, NoConnectionException {
        loadMedia(media, null, autoPlay, position, customData);
    }

    /**
     * Loads a media. For this to succeed, you need to have successfully launched the application.
     *
     * @param media The media to be loaded
     * @param activeTracks An array containing the list of track IDs to be set active for this
     * media upon a successful load
     * @param autoPlay If <code>true</code>, playback starts after load
     * @param position Where to start the playback (only used if autoPlay is <code>true</code>).
     * Units is milliseconds.
     * @param customData Optional {@link JSONObject} data to be passed to the cast device
     * @throws NoConnectionException
     * @throws TransientNetworkDisconnectionException
     */
    public void loadMedia(MediaInfo media, final long[] activeTracks, boolean autoPlay,
            int position, JSONObject customData)
            throws TransientNetworkDisconnectionException, NoConnectionException {
        LOGD(TAG, "loadMedia");
        checkConnectivity();
        if (media == null) {
            return;
        }
        if (mRemoteMediaPlayer == null) {
            LOGE(TAG, "Trying to load a video with no active media session");
            throw new NoConnectionException();
        }

        //MediaPlayer player = mSelectedCastDevice.getCapability(MediaPlayer.class);
//        mRemoteMediaPlayer.load(mApiClient, media, autoPlay, position, activeTracks, customData)
//                .setResultCallback(new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
//
//                    @Override
//                    public void onResult(MediaChannelResult result) {
//                        for (VideoCastConsumer consumer : mVideoConsumers) {
//                            consumer.onMediaLoadResult(result.getStatus().getStatusCode());
//                        }
//                    }
//                });

        if(media instanceof MediaInfoWithCustomData){
            try {
                JSONObject customDataForLoad = ((MediaInfoWithCustomData)media).getCustomDataForLoad();
                if(customDataForLoad==null) {
                    customDataForLoad = new JSONObject();
                    ((MediaInfoWithCustomData) media).setCustomDataForLoad(customDataForLoad);
                }
                customDataForLoad.put(EXTRA_START_POINT, position);

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        mRemoteMediaPlayer.playMedia(media, false, new MediaPlayer.LaunchListener() {
            @Override
            public void onSuccess(MediaPlayer.MediaLaunchObject mediaLaunchObject) {
                LOGD(TAG, "cast success");
                for (VideoCastConsumer consumer : mVideoConsumers) {
                    //consumer.onMediaLoadResult(CastStatusCodes.SUCCESS);
                    //consumer.onMediaLoadResult(mediaLaunchObject);
                    consumer.onMediaLoadResult(mWebAppSession);
                }
            }

            @Override
            public void onError(ServiceCommandError serviceCommandError) {
                LOGE(TAG, "cast failure code " + serviceCommandError.getCode());
            }
        });
    }
    /**
     * Loads and optionally starts playback of a new queue of media items.
     *
     * @param items Array of items to load, in the order that they should be played. Must not be
     *              {@code null} or empty.
     * @param startIndex The array index of the item in the {@code items} array that should be
     *                   played first (i.e., it will become the currentItem).If {@code repeatMode}
     *                   is {@link MediaStatus#REPEAT_MODE_REPEAT_OFF} playback will end when the
     *                   last item in the array is played.
     *                   <p>
     *                   This may be useful for continuation scenarios where the user was already
     *                   using the sender application and in the middle decides to cast. This lets
     *                   the sender application avoid mapping between the local and remote queue
     *                   positions and/or avoid issuing an extra request to update the queue.
     *                   <p>
     *                   This value must be less than the length of {@code items}.
     * @param repeatMode The repeat playback mode for the queue. One of
     *                   {@link MediaStatus#REPEAT_MODE_REPEAT_OFF},
     *                   {@link MediaStatus#REPEAT_MODE_REPEAT_ALL},
     *                   {@link MediaStatus#REPEAT_MODE_REPEAT_SINGLE} and
     *                   {@link MediaStatus#REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE}.
     * @param customData Custom application-specific data to pass along with the request, may be
     *                   {@code null}.
     * @throws TransientNetworkDisconnectionException
     * @throws NoConnectionException
     */
//    public void queueLoad(final MediaQueueItem[] items, final int startIndex, final int repeatMode,
//            final JSONObject customData)
//            throws TransientNetworkDisconnectionException, NoConnectionException {
//        LOGD(TAG, "queueLoad");
//        checkConnectivity();
//        if (items == null || items.length == 0) {
//            return;
//        }
//        if (mRemoteMediaPlayer == null) {
//            LOGE(TAG, "Trying to queue one or more videos with no active media session");
//            throw new NoConnectionException();
//        }
//        mRemoteMediaPlayer
//                .queueLoad(mApiClient, items, startIndex, repeatMode, customData)
//                .setResultCallback(new ResultCallback<MediaChannelResult>() {
//
//                    @Override
//                    public void onResult(MediaChannelResult result) {
//                        for (VideoCastConsumer consumer : mVideoConsumers) {
//                            consumer.onMediaQueueOperationResult(QUEUE_OPERATION_LOAD,
//                                    result.getStatus().getStatusCode());
//                        }
//                    }
//                });
//    }

    /**
     * Inserts a list of new media items into the queue.
     *
     * @param itemsToInsert List of items to insert into the queue, in the order that they should be
     *                      played. The itemId field of the items should be unassigned or the
     *                      request will fail with an INVALID_PARAMS error. Must not be {@code null}
     *                      or empty.
     * @param insertBeforeItemId ID of the item that will be located immediately after the inserted
     *                           list. If the value is {@link MediaQueueItem#INVALID_ITEM_ID} or
     *                           invalid, the inserted list will be appended to the end of the
     *                           queue.
     * @param customData Custom application-specific data to pass along with the request. May be
     *                   {@code null}.
     * @throws TransientNetworkDisconnectionException
     * @throws NoConnectionException
     * @throws IllegalArgumentException
     */
//    public void queueInsertItems(final MediaQueueItem[] itemsToInsert, final int insertBeforeItemId,
//            final JSONObject customData)
//            throws TransientNetworkDisconnectionException, NoConnectionException {
//        LOGD(TAG, "queueInsertItems");
//        checkConnectivity();
//        if (itemsToInsert == null || itemsToInsert.length == 0) {
//            throw new IllegalArgumentException("items cannot be empty or null");
//        }
//        if (mRemoteMediaPlayer == null) {
//            LOGE(TAG, "Trying to insert into queue with no active media session");
//            throw new NoConnectionException();
//        }
//        mRemoteMediaPlayer
//                .queueInsertItems(mApiClient, itemsToInsert, insertBeforeItemId, customData)
//                .setResultCallback(
//                        new ResultCallback<MediaChannelResult>() {
//
//                            @Override
//                            public void onResult(MediaChannelResult result) {
//                                for (VideoCastConsumer consumer : mVideoConsumers) {
//                                    consumer.onMediaQueueOperationResult(
//                                            QUEUE_OPERATION_INSERT_ITEMS,
//                                            result.getStatus().getStatusCode());
//                                }
//                            }
//                        });
//    }

    /**
     * Updates properties of a subset of the existing items in the media queue.
     *
     * @param itemsToUpdate List of queue items to be updated. The items will retain the existing
     *                      order and will be fully replaced with the ones provided, including the
     *                      media information. Any other items currently in the queue will remain
     *                      unchanged. The tracks information can not change once the item is loaded
     *                      (if the item is the currentItem). If any of the items does not exist it
     *                      will be ignored.
     * @param customData Custom application-specific data to pass along with the request. May be
     *                   {@code null}.
     * @throws TransientNetworkDisconnectionException
     * @throws NoConnectionException
     */
//    public void queueUpdateItems(final MediaQueueItem[] itemsToUpdate, final JSONObject customData)
//            throws TransientNetworkDisconnectionException, NoConnectionException {
//        checkConnectivity();
//        if (mRemoteMediaPlayer == null) {
//            LOGE(TAG, "Trying to update the queue with no active media session");
//            throw new NoConnectionException();
//        }
//        mRemoteMediaPlayer
//                .queueUpdateItems(mApiClient, itemsToUpdate, customData).setResultCallback(
//                new ResultCallback<MediaChannelResult>() {
//
//                    @Override
//                    public void onResult(MediaChannelResult result) {
//                        LOGD(TAG, "queueUpdateItems() " + result.getStatus() + result.getStatus()
//                                .isSuccess());
//                        for (VideoCastConsumer consumer : mVideoConsumers) {
//                            consumer.onMediaQueueOperationResult(QUEUE_OPERATION_UPDATE_ITEMS,
//                                    result.getStatus().getStatusCode());
//                        }
//                    }
//                });
//    }

    /**
     * Plays the item with {@code itemId} in the queue.
     *
     * If {@code itemId} is not found in the queue, this method will report success without sending
     * a request to the receiver.
     *
     * @param itemId The ID of the item to which to jump.
     * @param customData Custom application-specific data to pass along with the request. May be
     *                   {@code null}.
     * @throws TransientNetworkDisconnectionException
     * @throws NoConnectionException
     * @throws IllegalArgumentException
     */
//    public void queueJumpToItem(int itemId, final JSONObject customData)
//            throws TransientNetworkDisconnectionException, NoConnectionException,
//            IllegalArgumentException {
//        checkConnectivity();
//        if (itemId == MediaQueueItem.INVALID_ITEM_ID) {
//            throw new IllegalArgumentException("itemId is not valid");
//        }
//        if (mRemoteMediaPlayer == null) {
//            LOGE(TAG, "Trying to jump in a queue with no active media session");
//            throw new NoConnectionException();
//        }
//        mRemoteMediaPlayer
//                .queueJumpToItem(mApiClient, itemId, customData).setResultCallback(
//                new ResultCallback<MediaChannelResult>() {
//
//                    @Override
//                    public void onResult(MediaChannelResult result) {
//                        for (VideoCastConsumer consumer : mVideoConsumers) {
//                            consumer.onMediaQueueOperationResult(QUEUE_OPERATION_JUMP,
//                                    result.getStatus().getStatusCode());
//                        }
//                    }
//                });
//    }

    /**
     * Removes a list of items from the queue. If the remaining queue is empty, the media session
     * will be terminated.
     *
     * @param itemIdsToRemove The list of media item IDs to remove. Must not be {@code null} or
     *                        empty.
     * @param customData Custom application-specific data to pass along with the request. May be
     *                   {@code null}.
     * @throws TransientNetworkDisconnectionException
     * @throws NoConnectionException
     * @throws IllegalArgumentException
     */
//    public void queueRemoveItems(final int[] itemIdsToRemove, final JSONObject customData)
//            throws TransientNetworkDisconnectionException, NoConnectionException,
//            IllegalArgumentException {
//        LOGD(TAG, "queueRemoveItems");
//        checkConnectivity();
//        if (itemIdsToRemove == null || itemIdsToRemove.length == 0) {
//            throw new IllegalArgumentException("itemIds cannot be empty or null");
//        }
//        if (mRemoteMediaPlayer == null) {
//            LOGE(TAG, "Trying to remove items from queue with no active media session");
//            throw new NoConnectionException();
//        }
//        mRemoteMediaPlayer
//                .queueRemoveItems(mApiClient, itemIdsToRemove, customData).setResultCallback(
//                new ResultCallback<MediaChannelResult>() {
//
//                    @Override
//                    public void onResult(MediaChannelResult result) {
//                        for (VideoCastConsumer consumer : mVideoConsumers) {
//                            consumer.onMediaQueueOperationResult(QUEUE_OPERATION_REMOVE_ITEMS,
//                                    result.getStatus().getStatusCode());
//                        }
//                    }
//                });
//    }

    /**
     * Removes the item with {@code itemId} from the queue.
     *
     * If {@code itemId} is not found in the queue, this method will silently return without sending
     * a request to the receiver. A {@code itemId} may not be in the queue because it wasn't
     * originally in the queue, or it was removed by another sender.
     *
     * @param itemId The ID of the item to be removed.
     * @param customData Custom application-specific data to pass along with the request. May be
     *                   {@code null}.
     * @throws TransientNetworkDisconnectionException
     * @throws NoConnectionException
     * @throws IllegalArgumentException
     */
//    public void queueRemoveItem(final int itemId, final JSONObject customData)
//            throws TransientNetworkDisconnectionException, NoConnectionException,
//            IllegalArgumentException {
//        LOGD(TAG, "queueRemoveItem");
//        checkConnectivity();
//        if (itemId == MediaQueueItem.INVALID_ITEM_ID) {
//            throw new IllegalArgumentException("itemId is invalid");
//        }
//        if (mRemoteMediaPlayer == null) {
//            LOGE(TAG, "Trying to remove an item from queue with no active media session");
//            throw new NoConnectionException();
//        }
//        mRemoteMediaPlayer
//                .queueRemoveItem(mApiClient, itemId, customData).setResultCallback(
//                new ResultCallback<MediaChannelResult>() {
//
//                    @Override
//                    public void onResult(MediaChannelResult result) {
//                        for (VideoCastConsumer consumer : mVideoConsumers) {
//                            consumer.onMediaQueueOperationResult(QUEUE_OPERATION_REMOVE_ITEM,
//                                    result.getStatus().getStatusCode());
//                        }
//                    }
//                });
//    }

    /**
     * Reorder a list of media items in the queue.
     *
     * @param itemIdsToReorder The list of media item IDs to reorder, in the new order. Any other
     *                         items currently in the queue will maintain their existing order. The
     *                         list will be inserted just before the item specified by
     *                         {@code insertBeforeItemId}, or at the end of the queue if
     *                         {@code insertBeforeItemId} is {@link MediaQueueItem#INVALID_ITEM_ID}.
     *                         <p>
     *                         For example:
     *                         <p>
     *                         If insertBeforeItemId is not specified <br>
     *                         Existing queue: "A","D","G","H","B","E" <br>
     *                         itemIds: "D","H","B" <br>
     *                         New Order: "A","G","E","D","H","B" <br>
     *                         <p>
     *                         If insertBeforeItemId is "A" <br>
     *                         Existing queue: "A","D","G","H","B" <br>
     *                         itemIds: "D","H","B" <br>
     *                         New Order: "D","H","B","A","G","E" <br>
     *                         <p>
     *                         If insertBeforeItemId is "G" <br>
     *                         Existing queue: "A","D","G","H","B" <br>
     *                         itemIds: "D","H","B" <br>
     *                         New Order: "A","D","H","B","G","E" <br>
     *                         <p>
     *                         If any of the items does not exist it will be ignored.
     *                         Must not be {@code null} or empty.
     * @param insertBeforeItemId ID of the item that will be located immediately after the reordered
     *                           list. If set to {@link MediaQueueItem#INVALID_ITEM_ID}, the
     *                           reordered list will be appended at the end of the queue.
     * @param customData Custom application-specific data to pass along with the request. May be
     *                   {@code null}.
     * @throws TransientNetworkDisconnectionException
     * @throws NoConnectionException
     */
//    public void queueReorderItems(final int[] itemIdsToReorder, final int insertBeforeItemId,
//            final JSONObject customData)
//            throws TransientNetworkDisconnectionException, NoConnectionException,
//            IllegalArgumentException {
//        LOGD(TAG, "queueReorderItems");
//        checkConnectivity();
//        if (itemIdsToReorder == null || itemIdsToReorder.length == 0) {
//            throw new IllegalArgumentException("itemIdsToReorder cannot be empty or null");
//        }
//        if (mRemoteMediaPlayer == null) {
//            LOGE(TAG, "Trying to reorder items in a queue with no active media session");
//            throw new NoConnectionException();
//        }
//        mRemoteMediaPlayer
//                .queueReorderItems(mApiClient, itemIdsToReorder, insertBeforeItemId, customData)
//                .setResultCallback(
//                        new ResultCallback<MediaChannelResult>() {
//
//                            @Override
//                            public void onResult(MediaChannelResult result) {
//                                for (VideoCastConsumer consumer : mVideoConsumers) {
//                                    consumer.onMediaQueueOperationResult(QUEUE_OPERATION_REORDER,
//                                            result.getStatus().getStatusCode());
//                                }
//                            }
//                        });
//    }

    /**
     * Moves the item with {@code itemId} to a new position in the queue.
     *
     * If {@code itemId} is not found in the queue, either because it wasn't there originally or it
     * was removed by another sender before calling this function, this function will silently
     * return without sending a request to the receiver.
     *
     * @param itemId The ID of the item to be moved.
     * @param newIndex The new index of the item. If the value is negative, an error will be
     *                 returned. If the value is out of bounds, or becomes out of bounds because the
     *                 queue was shortened by another sender while this request is in progress, the
     *                 item will be moved to the end of the queue.
     * @param customData Custom application-specific data to pass along with the request. May be
     *                   {@code null}.
     * @throws TransientNetworkDisconnectionException
     * @throws NoConnectionException
     */
//    public void queueMoveItemToNewIndex(int itemId, int newIndex, final JSONObject customData)
//            throws TransientNetworkDisconnectionException, NoConnectionException {
//        mRemoteMediaPlayer
//                .queueMoveItemToNewIndex(mApiClient, itemId, newIndex, customData)
//                .setResultCallback(
//                        new ResultCallback<MediaChannelResult>() {
//
//                            @Override
//                            public void onResult(MediaChannelResult result) {
//                                for (VideoCastConsumer consumer : mVideoConsumers) {
//                                    consumer.onMediaQueueOperationResult(QUEUE_OPERATION_MOVE,
//                                            result.getStatus().getStatusCode());;
//                                }
//                            }
//                        });
//    }

    /**
     * Appends a new media item to the end of the queue.
     *
     * @param item The item to append. Must not be {@code null}.
     * @param customData Custom application-specific data to pass along with the request. May be
     *                   {@code null}.
     * @throws TransientNetworkDisconnectionException
     * @throws NoConnectionException
     */
//    public void queueAppendItem(MediaQueueItem item, final JSONObject customData)
//            throws TransientNetworkDisconnectionException, NoConnectionException {
//        mRemoteMediaPlayer
//                .queueAppendItem(mApiClient, item, customData)
//                .setResultCallback(
//                        new ResultCallback<MediaChannelResult>() {
//
//                            @Override
//                            public void onResult(MediaChannelResult result) {
//                                for (VideoCastConsumer consumer : mVideoConsumers) {
//                                    consumer.onMediaQueueOperationResult(QUEUE_OPERATION_APPEND,
//                                            result.getStatus().getStatusCode());
//                                }
//                            }
//                        });
//    }

    /**
     * Jumps to the next item in the queue.
     *
     * @param customData Custom application-specific data to pass along with the request. May be
     *                   {@code null}.
     * @throws TransientNetworkDisconnectionException
     * @throws NoConnectionException
     */
//    public void queueNext(final JSONObject customData)
//            throws TransientNetworkDisconnectionException, NoConnectionException {
//        checkConnectivity();
//        if (mRemoteMediaPlayer == null) {
//            LOGE(TAG, "Trying to update the queue with no active media session");
//            throw new NoConnectionException();
//        }
//        mRemoteMediaPlayer
//                .queueNext(mApiClient, customData).setResultCallback(
//                new ResultCallback<MediaChannelResult>() {
//
//                    @Override
//                    public void onResult(MediaChannelResult result) {
//                        for (VideoCastConsumer consumer : mVideoConsumers) {
//                            consumer.onMediaQueueOperationResult(QUEUE_OPERATION_NEXT,
//                                    result.getStatus().getStatusCode());
//                        }
//                    }
//                });
//    }

    /**
     * Jumps to the previous item in the queue.
     *
     * @param customData Custom application-specific data to pass along with the request. May be
     *                   {@code null}.
     * @throws TransientNetworkDisconnectionException
     * @throws NoConnectionException
     */
//    public void queuePrev(final JSONObject customData)
//            throws TransientNetworkDisconnectionException, NoConnectionException {
//        checkConnectivity();
//        if (mRemoteMediaPlayer == null) {
//            LOGE(TAG, "Trying to update the queue with no active media session");
//            throw new NoConnectionException();
//        }
//        mRemoteMediaPlayer
//                .queuePrev(mApiClient, customData).setResultCallback(
//                new ResultCallback<MediaChannelResult>() {
//
//                    @Override
//                    public void onResult(MediaChannelResult result) {
//                        for (VideoCastConsumer consumer : mVideoConsumers) {
//                            consumer.onMediaQueueOperationResult(QUEUE_OPERATION_PREV,
//                                    result.getStatus().getStatusCode());
//                        }
//                    }
//                });
//    }

    /**
     * Inserts an item in the queue and starts the playback of that newly inserted item. It is
     * assumed that we are inserting  before the "current item"
     *
     * @param item The item to be inserted
     * @param insertBeforeItemId ID of the item that will be located immediately after the inserted
     * and is assumed to be the "current item"
     * @param customData Custom application-specific data to pass along with the request. May be
     *                   {@code null}.
     * @throws TransientNetworkDisconnectionException
     * @throws NoConnectionException
     * @throws IllegalArgumentException
     */
//    public void queueInsertBeforeCurrentAndPlay(MediaQueueItem item, int insertBeforeItemId,
//            final JSONObject customData)
//            throws TransientNetworkDisconnectionException, NoConnectionException {
//        checkConnectivity();
//        if (mRemoteMediaPlayer == null) {
//            LOGE(TAG, "Trying to insert into queue with no active media session");
//            throw new NoConnectionException();
//        }
//        if (item == null || insertBeforeItemId == MediaQueueItem.INVALID_ITEM_ID) {
//            throw new IllegalArgumentException(
//                    "item cannot be empty or insertBeforeItemId cannot be invalid");
//        }
//        mRemoteMediaPlayer.queueInsertItems(mApiClient, new MediaQueueItem[]{item},
//                insertBeforeItemId, customData).setResultCallback(
//                new ResultCallback<MediaChannelResult>() {
//
//                    @Override
//                    public void onResult(MediaChannelResult result) {
//                        if (result.getStatus().isSuccess()) {
//
//                            try {
//                                queuePrev(customData);
//                            } catch (TransientNetworkDisconnectionException |
//                                    NoConnectionException e) {
//                                LOGE(TAG, "queuePrev() Failed to skip to previous", e);
//                            }
//                        }
//                        for (VideoCastConsumer consumer : mVideoConsumers) {
//                            consumer.onMediaQueueOperationResult(QUEUE_OPERATION_INSERT_ITEMS,
//                                    result.getStatus().getStatusCode());
//                        }
//                    }
//                });
//    }

    /**
     * Sets the repeat mode of the queue.
     *
     * @param repeatMode The repeat playback mode for the queue.
     * @param customData Custom application-specific data to pass along with the request. May be
     *                   {@code null}.
     * @throws TransientNetworkDisconnectionException
     * @throws NoConnectionException
     */
//    public void queueSetRepeatMode(final int repeatMode, final JSONObject customData)
//            throws TransientNetworkDisconnectionException, NoConnectionException {
//        checkConnectivity();
//        if (mRemoteMediaPlayer == null) {
//            LOGE(TAG, "Trying to update the queue with no active media session");
//            throw new NoConnectionException();
//        }
//        mRemoteMediaPlayer
//                .queueSetRepeatMode(mApiClient, repeatMode, customData).setResultCallback(
//                new ResultCallback<MediaChannelResult>() {
//
//                    @Override
//                    public void onResult(MediaChannelResult result) {
//                        if (!result.getStatus().isSuccess()) {
//                            LOGD(TAG, "Failed with status: " + result.getStatus());
//                        }
//                        for (VideoCastConsumer consumer : mVideoConsumers) {
//                            consumer.onMediaQueueOperationResult(QUEUE_OPERATION_SET_REPEAT,
//                                    result.getStatus().getStatusCode());
//                        }
//                    }
//                });
//    }

    /**
     * Plays the loaded media.
     *
     * @param position Where to start the playback. Units is milliseconds.
     * @throws NoConnectionException
     * @throws TransientNetworkDisconnectionException
     */
    public void play(int position) throws TransientNetworkDisconnectionException, NoConnectionException {
        checkConnectivity();
        LOGD(TAG, "attempting to play media at position " + position + " seconds");
        if (mRemoteMediaPlayer == null) {
            LOGE(TAG, "Trying to play a video with no active media session");
            throw new NoConnectionException();
        }
        seekAndPlay(position);
    }

    /**
     * Resumes the playback from where it was left (can be the beginning).
     *
     * @param customData Optional {@link JSONObject} data to be passed to the cast device
     * @throws NoConnectionException
     * @throws TransientNetworkDisconnectionException
     */
    public void play(JSONObject customData) throws
            TransientNetworkDisconnectionException, NoConnectionException {
        LOGD(TAG, "play(customData)");
        checkConnectivity();
        if (mRemoteMediaPlayer == null) {
            LOGE(TAG, "Trying to play a video with no active media session");
            throw new NoConnectionException();
        }
//        mRemoteMediaPlayer.play(mApiClient, customData)
//                .setResultCallback(new ResultCallback<MediaChannelResult>() {
//
//                    @Override
//                    public void onResult(MediaChannelResult result) {
//                        if (!result.getStatus().isSuccess()) {
//                            onFailed(R.string.ccl_failed_to_play,
//                                    result.getStatus().getStatusCode());
//                        }
//                    }
//
//                });

        mMediaStatus.play(new ResponseListener<Object>() {

            @Override
            public void onSuccess(Object o) {

            }

            @Override
            public void onError(ServiceCommandError serviceCommandError) {
                onFailed(R.string.ccl_failed_to_play, serviceCommandError.getCode());
            }
        });
    }

    /**
     * Resumes the playback from where it was left (can be the beginning).
     *
     * @throws CastException
     * @throws NoConnectionException
     * @throws TransientNetworkDisconnectionException
     */
    public void play() throws CastException, TransientNetworkDisconnectionException,
            NoConnectionException {
        play(null);
    }

    /**
     * Stops the playback of media/stream
     *
     * @param customData Optional {@link JSONObject}
     * @throws TransientNetworkDisconnectionException
     * @throws NoConnectionException
     */
    public void stop(JSONObject customData) throws
            TransientNetworkDisconnectionException, NoConnectionException {
        LOGD(TAG, "stop()");
        checkConnectivity();
        if (mRemoteMediaPlayer == null) {
            LOGE(TAG, "Trying to stop a stream with no active media session");
            throw new NoConnectionException();
        }
//        mRemoteMediaPlayer.stop(mApiClient, customData).setResultCallback(
//                new ResultCallback<MediaChannelResult>() {
//
//                    @Override
//                    public void onResult(MediaChannelResult result) {
//                        if (!result.getStatus().isSuccess()) {
//                            onFailed(R.string.ccl_failed_to_stop,
//                                    result.getStatus().getStatusCode());
//                        }
//                    }
//
//                }
//        );

        mMediaStatus.stop(new ResponseListener<Object>() {

            @Override
            public void onSuccess(Object o) {
                LOGD(TAG, "stop success");
                for (VideoCastConsumer consumer : mVideoConsumers) {
                    //consumer.onMediaStopped(CastStatusCodes.SUCCESS);
                    consumer.onMediaStopped(mWebAppSession);
                }
            }

            @Override
            public void onError(ServiceCommandError serviceCommandError) {
                onFailed(R.string.ccl_failed_to_stop, serviceCommandError.getCode());
            }
        });
    }

    /**
     * Stops the playback of media/stream
     *
     * @throws CastException
     * @throws TransientNetworkDisconnectionException
     * @throws NoConnectionException
     */
    public void stop() throws CastException,
            TransientNetworkDisconnectionException, NoConnectionException {
        stop(null);
    }

    /**
     * Pauses the playback.
     *
     * @throws CastException
     * @throws NoConnectionException
     * @throws TransientNetworkDisconnectionException
     */
    public void pause() throws CastException, TransientNetworkDisconnectionException,
            NoConnectionException {
        pause(null);
    }

    /**
     * Pauses the playback.
     *
     * @param customData Optional {@link JSONObject} data to be passed to the cast device
     * @throws NoConnectionException
     * @throws TransientNetworkDisconnectionException
     */
    public void pause(JSONObject customData) throws
            TransientNetworkDisconnectionException, NoConnectionException {
        LOGD(TAG, "attempting to pause media");
        checkConnectivity();
        if (mRemoteMediaPlayer == null) {
            LOGE(TAG, "Trying to pause a video with no active media session");
            throw new NoConnectionException();
        }
//        mRemoteMediaPlayer.pause(mApiClient, customData)
//                .setResultCallback(new ResultCallback<MediaChannelResult>() {
//
//                    @Override
//                    public void onResult(MediaChannelResult result) {
//                        if (!result.getStatus().isSuccess()) {
//                            onFailed(R.string.ccl_failed_to_pause,
//                                    result.getStatus().getStatusCode());
//                        }
//                    }
//
//                });

        mMediaStatus.pause(new ResponseListener<Object>() {

            @Override
            public void onSuccess(Object o) {
                LOGD(TAG, "pause success");
            }

            @Override
            public void onError(ServiceCommandError serviceCommandError) {
                onFailed(R.string.ccl_failed_to_pause, serviceCommandError.getCode());
            }
        });
    }

    /**
     * Seeks to the given point without changing the state of the player, i.e. after seek is
     * completed, it resumes what it was doing before the start of seek.
     *
     * @param position in milliseconds
     * @throws NoConnectionException
     * @throws TransientNetworkDisconnectionException
     */
    public void seek(int position) throws TransientNetworkDisconnectionException,
            NoConnectionException {
        LOGD(TAG, "attempting to seek media");
        checkConnectivity();
        if (mRemoteMediaPlayer == null) {
            LOGE(TAG, "Trying to seek a video with no active media session");
            throw new NoConnectionException();
        }
//        mRemoteMediaPlayer.seek(mApiClient,
//                position,
//                RemoteMediaPlayer.RESUME_STATE_UNCHANGED).
//                setResultCallback(new ResultCallback<MediaChannelResult>() {
//
//                    @Override
//                    public void onResult(MediaChannelResult result) {
//                        if (!result.getStatus().isSuccess()) {
//                            onFailed(R.string.ccl_failed_seek, result.getStatus().getStatusCode());
//                        }
//                    }
//
//                });
        mMediaStatus.seek(position, new ResponseListener<Object>() {
            @Override
            public void onSuccess(Object o) {

            }

            @Override
            public void onError(ServiceCommandError serviceCommandError) {
                onFailed(R.string.ccl_failed_seek, serviceCommandError.getCode());
            }
        });
    }

    /**
     * Seeks to the given point and starts playback regardless of the starting state.
     *
     * @param position in milliseconds
     * @throws NoConnectionException
     * @throws TransientNetworkDisconnectionException
     */
    public void seekAndPlay(int position) throws TransientNetworkDisconnectionException, NoConnectionException {
        LOGD(TAG, "attempting to seek media");
        checkConnectivity();
        if (mRemoteMediaPlayer == null) {
            LOGE(TAG, "Trying to seekAndPlay a video with no active media session");
            throw new NoConnectionException();
        }
//        ResultCallback<MediaChannelResult> resultCallback =
//                new ResultCallback<MediaChannelResult>() {
//
//                    @Override
//                    public void onResult(MediaChannelResult result) {
//                        if (!result.getStatus().isSuccess()) {
//                            onFailed(R.string.ccl_failed_seek, result.getStatus().getStatusCode());
//                        }
//                    }
//
//                };
//        mRemoteMediaPlayer.seek(mApiClient,
//                position,
//                RemoteMediaPlayer.RESUME_STATE_PLAY).setResultCallback(resultCallback);

        mMediaStatus.seek(position, new ResponseListener<Object>() {
            @Override
            public void onSuccess(Object o) {
                try {
                    play();
                } catch (CastException e) {
                    e.printStackTrace();
                } catch (TransientNetworkDisconnectionException e) {
                    e.printStackTrace();
                } catch (NoConnectionException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onError(ServiceCommandError serviceCommandError) {
                onFailed(R.string.ccl_failed_seek, serviceCommandError.getCode());
            }
        });
    }

    /**
     * Toggles the playback of the movie.
     *
     * @throws CastException
     * @throws NoConnectionException
     * @throws TransientNetworkDisconnectionException
     */
    public void togglePlayback() throws CastException, TransientNetworkDisconnectionException,
            NoConnectionException {
        checkConnectivity();
        boolean isPlaying = isRemoteMediaPlaying();
        if (isPlaying) {
            pause();
        } else {
            if (mState == MediaControl.PLAYER_STATE_IDLE){
                    //&& mIdleReason == MediaControl.IDLE_REASON_FINISHED) {
                loadMedia(getRemoteMediaInformation(), true, 0);
            } else {
                play();
            }
        }
    }

    private void attachMediaChannel() throws TransientNetworkDisconnectionException,
            NoConnectionException {
        LOGD(TAG, "attachMediaChannel()");
        checkConnectivity();
        if (mRemoteMediaPlayer == null) {

//            mRemoteMediaPlayer = new RemoteMediaPlayer();

//            mRemoteMediaPlayer.setOnStatusUpdatedListener(
//                    new RemoteMediaPlayer.OnStatusUpdatedListener() {
//
//                        @Override
//                        public void onStatusUpdated() {
//                            LOGD(TAG, "RemoteMediaPlayer::onStatusUpdated() is reached");
//                            VideoCastManager.this.onRemoteMediaPlayerStatusUpdated();
//                        }
//                    }
//            );

//            mRemoteMediaPlayer.setOnPreloadStatusUpdatedListener(
//                    new RemoteMediaPlayer.OnPreloadStatusUpdatedListener() {
//
//                        @Override
//                        public void onPreloadStatusUpdated() {
//                            LOGD(TAG,
//                                    "[preload] RemoteMediaPlayer::onPreloadStatusUpdated() is "
//                                            + "reached");
//                            VideoCastManager.this.onRemoteMediaPreloadStatusUpdated();
//                        }
//                    });


//            mRemoteMediaPlayer.setOnMetadataUpdatedListener(
//                    new RemoteMediaPlayer.OnMetadataUpdatedListener() {
//                        @Override
//                        public void onMetadataUpdated() {
//                            LOGD(TAG, "RemoteMediaPlayer::onMetadataUpdated() is reached");
//                            VideoCastManager.this.onRemoteMediaPlayerMetadataUpdated();
//                        }
//                    }
//            );

//            mRemoteMediaPlayer.setOnQueueStatusUpdatedListener(
//                    new RemoteMediaPlayer.OnQueueStatusUpdatedListener() {
//
//                        @Override
//                        public void onQueueStatusUpdated() {
//                            LOGD(TAG,
//                                    "RemoteMediaPlayer::onQueueStatusUpdated() is "
//                                            + "reached");
//                            mMediaStatus = mRemoteMediaPlayer.getMediaStatus();
//                            if (mMediaStatus != null
//                                    && mMediaStatus.getQueueItems() != null) {
//                                List<MediaQueueItem> queueItems = mMediaStatus
//                                        .getQueueItems();
//                                int itemId = mMediaStatus.getCurrentItemId();
//                                MediaQueueItem item = mMediaStatus
//                                        .getQueueItemById(itemId);
//                                int repeatMode = mMediaStatus.getQueueRepeatMode();
//                                boolean shuffle = false;
//                                onQueueUpdated(queueItems, item, repeatMode, shuffle);
//                            } else {
//                                onQueueUpdated(null, null,
//                                        MediaStatus.REPEAT_MODE_REPEAT_OFF,
//                                        false);
//                            }
//                        }
//                    });

            mRemoteMediaPlayer = mSelectedCastDevice.getCapability(MediaPlayer.class);

            mRemoteMediaPlayer.subscribeMediaInfo(new MediaPlayer.MediaInfoListener() {
                @Override
                public void onSuccess(MediaInfo mediaInfo) {
                    LOGD(TAG, "RemoteMediaPlayer::onMetadataUpdated() is reached");
                    mCurrentMediaInfo = mediaInfo;
                    VideoCastManager.this.onRemoteMediaPlayerMetadataUpdated();
                }

                @Override
                public void onError(ServiceCommandError serviceCommandError) {

                }
            });

//            if(mMediaStatus.getMediaControl() instanceof DeviceService){
//                DeviceService service = ((DeviceService)mMediaStatus.getMediaControl());
//                URLServiceSubscription<MediaPlayer.MediaInfoListener> request = new URLServiceSubscription<MediaPlayer.MediaInfoListener>(service, "info", null, null);
//                ((DeviceService)mMediaStatus.getMediaControl()).unsubscribe(request);
//            }

            mMediaStatus.subscribePlayState(new MediaControl.PlayStateListener() {
                @Override
                public void onSuccess(MediaControl.PlayStateStatus playStateStatus) {
                    LOGD(TAG, "RemoteMediaPlayer::onStatusUpdated() is reached");
                    mCurrentPlayStateStatus = playStateStatus;
//                    switch (mCurrentPlayStateStatus)
//                    {
//                        case Finished:
//                            mCurrentMediaInfo = null;
//                            LOGD(TAG, "RemoteMediaPlayer::onStatusUpdated() status finished, clearing mediaInfo");
//                            break;
//                    }
                    VideoCastManager.this.onRemoteMediaPlayerStatusUpdated();
                }

                @Override
                public void onError(ServiceCommandError serviceCommandError) {

                }
            });

        }
//        try {
//            LOGD(TAG, "Registering MediaChannel namespace");
//            Cast.CastApi.setMessageReceivedCallbacks(mApiClient, mRemoteMediaPlayer.getNamespace(),
//                    mRemoteMediaPlayer);
//        } catch (IOException | IllegalStateException e) {
//            LOGE(TAG, "attachMediaChannel()", e);
//        }
    }

    private void reattachMediaChannel() {
//        if (mRemoteMediaPlayer != null && mApiClient != null) {
//            try {
//                LOGD(TAG, "Registering MediaChannel namespace");
//                Cast.CastApi.setMessageReceivedCallbacks(mApiClient,
//                        mRemoteMediaPlayer.getNamespace(), mRemoteMediaPlayer);
//            } catch (IOException | IllegalStateException e) {
//                LOGE(TAG, "reattachMediaChannel()", e);
//            }
//        }
    }

    private void detachMediaChannel() {
        LOGD(TAG, "trying to detach media channel");
        if (mRemoteMediaPlayer != null) {
//            try {
//                Cast.CastApi.removeMessageReceivedCallbacks(mApiClient,
//                        mRemoteMediaPlayer.getNamespace());
//            } catch (IOException | IllegalStateException e) {
//                LOGE(TAG, "detachMediaChannel()", e);
//            }
            mRemoteMediaPlayer = null;
        }
    }

    /**
     * Returns the playback status of the remote device.
     *
     * @return Returns one of the values
     * <ul>
     * <li> <code>MediaStatus.PLAYER_STATE_PLAYING</code>
     * <li> <code>MediaStatus.PLAYER_STATE_PAUSED</code>
     * <li> <code>MediaStatus.PLAYER_STATE_BUFFERING</code>
     * <li> <code>MediaStatus.PLAYER_STATE_IDLE</code>
     * <li> <code>MediaStatus.PLAYER_STATE_UNKNOWN</code>
     * </ul>
     */
    public int getPlaybackStatus() {
        return mState;
    }

    /**
     * Returns the latest retrieved value for the {@link MediaControl}. This value is updated
     * whenever the onStatusUpdated callback is called.
     */
    public final MediaControl getMediaStatus() {
        return mMediaStatus;
    }

    /**
     * Returns the Idle reason, defined in <code>MediaStatus.IDLE_*</code>. Note that the returned
     * value is only meaningful if the status is truly <code>MediaStatus.PLAYER_STATE_IDLE
     * </code>
     */
    public int getIdleReason() {
        return mIdleReason;
    }

    /*
     * If a data namespace was provided when initializing this class, we set things up for a data
     * channel
     *
     * @throws NoConnectionException
     * @throws TransientNetworkDisconnectionException
     */
//    private void attachDataChannel() throws TransientNetworkDisconnectionException,
//            NoConnectionException {
//        if (TextUtils.isEmpty(mDataNamespace)) {
//            return;
//        }
//        if (mDataChannel != null) {
//            return;
//        }
//        checkConnectivity();
//        mDataChannel = new MessageReceivedCallback() {
//
//            @Override
//            public void onMessageReceived(CastDevice castDevice, String namespace, String message) {
//                for (VideoCastConsumer consumer : mVideoConsumers) {
//                    consumer.onDataMessageReceived(message);
//                }
//            }
//        };
//        try {
//            Cast.CastApi.setMessageReceivedCallbacks(mApiClient, mDataNamespace, mDataChannel);
//        } catch (IOException | IllegalStateException e) {
//            LOGE(TAG, "attachDataChannel()", e);
//        }
//    }

//    private void reattachDataChannel() {
//        if (!TextUtils.isEmpty(mDataNamespace) && mDataChannel != null) {
//            try {
//                Cast.CastApi.setMessageReceivedCallbacks(mApiClient, mDataNamespace, mDataChannel);
//            } catch (IOException | IllegalStateException e) {
//                LOGE(TAG, "reattachDataChannel()", e);
//            }
//        }
//    }

    private void onMessageSendFailed(int errorCode) {
        for (VideoCastConsumer consumer : mVideoConsumers) {
            consumer.onDataMessageSendFailed(errorCode);
        }
    }

    /**
     * Sends the <code>message</code> on the data channel for the namespace that was provided
     * during the initialization of this class. If <code>messageId &gt; 0</code>, then it has to be
     * a unique identifier for the message; this id will be returned if an error occurs. If
     * <code>messageId == 0</code>, then an auto-generated unique identifier will be created and
     * returned for the message.
     *
     * @throws IllegalStateException If the namespace is empty or null
     * @throws NoConnectionException If no connectivity to the device exists
     * @throws TransientNetworkDisconnectionException If framework is still trying to recover from
     * a possibly transient loss of network
     */
//    public void sendDataMessage(String message) throws TransientNetworkDisconnectionException,
//            NoConnectionException {
//        if (TextUtils.isEmpty(mDataNamespace)) {
//            throw new IllegalStateException("No Data Namespace is configured");
//        }
//        checkConnectivity();
//        Cast.CastApi.sendMessage(mApiClient, mDataNamespace, message)
//                .setResultCallback(new ResultCallback<Status>() {
//
//                    @Override
//                    public void onResult(Status result) {
//                        if (!result.isSuccess()) {
//                            VideoCastManager.this.onMessageSendFailed(result.getStatusCode());
//                        }
//                    }
//                });
//    }

    /**
     * Remove the custom data channel, if any. It returns <code>true</code> if it succeeds
     * otherwise if it encounters an error or if no connection exists or if no custom data channel
     * exists, then it returns <code>false</code>
     */
//    public boolean removeDataChannel() {
//        if (TextUtils.isEmpty(mDataNamespace)) {
//            return false;
//        }
//        try {
//            if (mApiClient != null) {
//                Cast.CastApi.removeMessageReceivedCallbacks(mApiClient, mDataNamespace);
//            }
//            mDataChannel = null;
//            mPreferenceAccessor.saveStringToPreference(PREFS_KEY_CAST_CUSTOM_DATA_NAMESPACE, null);
//            return true;
//        } catch (IOException | IllegalStateException e) {
//            LOGE(TAG, "removeDataChannel() failed to remove namespace " + mDataNamespace, e);
//        }
//        return false;
//
//    }

    /*
     * This is called by onStatusUpdated() of the RemoteMediaPlayer
     */
    private void onRemoteMediaPlayerStatusUpdated() {
        LOGD(TAG, "onRemoteMediaPlayerStatusUpdated() reached");
        if (mSelectedCastDevice == null || mRemoteMediaPlayer == null || mMediaStatus== null) {
            LOGD(TAG, "mApiClient or mRemoteMediaPlayer is null, so will not proceed");
            return;
        }
        //mMediaStatus = mRemoteMediaPlayer.getMediaStatus();
//        List<MediaQueueItem> queueItems = mMediaStatus.getQueueItems();
//        if (queueItems != null) {
//            int itemId = mMediaStatus.getCurrentItemId();
//            MediaQueueItem item = mMediaStatus.getQueueItemById(itemId);
//            int repeatMode = mMediaStatus.getQueueRepeatMode();
//            boolean shuffle = false; //mMediaStatus.isShuffleEnabled();
//            onQueueUpdated(queueItems, item, repeatMode, shuffle);
//        } else {
//            onQueueUpdated(null, null, MediaStatus.REPEAT_MODE_REPEAT_OFF, false);
//        }
//        int currentItemId = mMediaStatus.getCurrentItemId();
//        if (queueItems != null && !queueItems.isEmpty()) {
//            for (MediaQueueItem item : queueItems) {
//                LOGD(TAG, "[queue] Queue Item is: " + item.toJson());
//            }
//        }
        mMediaStatus.getPlayState(new MediaControl.PlayStateListener() {
            @Override
            public void onSuccess(MediaControl.PlayStateStatus playStateStatus) {
                switch(playStateStatus) {
                    case Unknown:
                    default:
                        mState = MediaControl.PLAYER_STATE_UNKNOWN;
                        break;
                    case Finished:
                        mState = MediaControl.PLAYER_STATE_IDLE;
                        break;
                    case Playing:
                        mState = MediaControl.PLAYER_STATE_PLAYING;
                        break;
                    case Paused:
                        mState =  MediaControl.PLAYER_STATE_PAUSED;
                        break;
                    case Buffering:
                        mState = MediaControl.PLAYER_STATE_BUFFERING;
                }

                //mIdleReason = mMediaStatus.getIdleReason();

                try {
                    getVolume(new VolumeControl.VolumeListener() {
                        @Override
                        public void onSuccess(final Float volume) {
                            try {
                                isMute(new VolumeControl.MuteListener(){
                                    @Override
                                    public void onSuccess(Boolean isMute) {
                                        for (VideoCastConsumer consumer : mVideoConsumers) {
                                            consumer.onRemoteMediaPlayerStatusUpdated();
                                            consumer.onVolumeChanged(volume, isMute);
                                        }
                                    }

                                    @Override
                                    public void onError(ServiceCommandError serviceCommandError) {

                                    }
                                });
                            } catch (TransientNetworkDisconnectionException | NoConnectionException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onError(ServiceCommandError serviceCommandError) {

                        }
                    });

                    boolean makeUiHidden = false;
                    if (mState == MediaControl.PLAYER_STATE_PLAYING) {
                        LOGD(TAG, "onRemoteMediaPlayerStatusUpdated(): Player status = playing");
                        updateRemoteControl(true);
                        getMediaTimeRemaining(new ResponseListener<Long>() {
                            @Override
                            public void onSuccess(Long mediaDurationLeft) {
                                startReconnectionService(mediaDurationLeft);
                                startNotificationService();
                            }

                            @Override
                            public void onError(ServiceCommandError serviceCommandError) {

                            }
                        });


                    } else if (mState == MediaControl.PLAYER_STATE_PAUSED) {
                        LOGD(TAG, "onRemoteMediaPlayerStatusUpdated(): Player status = paused");
                        updateRemoteControl(false);
                        //updateRemoteControl(true);
                        startNotificationService();
                    } else if (mState == MediaControl.PLAYER_STATE_IDLE) {
                        LOGD(TAG, "onRemoteMediaPlayerStatusUpdated(): Player status = idle");
                        updateRemoteControl(false);
//                switch (mIdleReason) {
//                    case MediaStatus.IDLE_REASON_FINISHED:
                        removeRemoteControlClient();
                        makeUiHidden = true;
//                        break;
//                    case MediaStatus.IDLE_REASON_ERROR:
//                        // something bad happened on the cast device
//                        LOGD(TAG, "onRemoteMediaPlayerStatusUpdated(): IDLE reason = ERROR");
//                        makeUiHidden = true;
//                        removeRemoteControlClient();
//                        onFailed(R.string.ccl_failed_receiver_player_error, NO_STATUS_CODE);
//                        break;
//                    case MediaStatus.IDLE_REASON_CANCELED:
//                        LOGD(TAG, "onRemoteMediaPlayerStatusUpdated(): IDLE reason = CANCELLED");
//                        makeUiHidden = !isRemoteStreamLive();
//                        break;
//                    case MediaStatus.IDLE_REASON_INTERRUPTED:
//                        if (mMediaStatus.getLoadingItemId() == MediaQueueItem.INVALID_ITEM_ID) {
//                            // we have reached the end of queue
//                            removeRemoteControlClient();
//                            makeUiHidden = true;
//                        }
//                        break;
//                    default:
//                        LOGE(TAG, "onRemoteMediaPlayerStatusUpdated(): Unexpected Idle Reason " + mIdleReason);
//                }
                        if (makeUiHidden) {
                            stopReconnectionService();
                        }
                    } else if (mState == MediaControl.PLAYER_STATE_BUFFERING) {
                        LOGD(TAG, "onRemoteMediaPlayerStatusUpdated(): Player status = buffering");
                    } else {
                        LOGD(TAG, "onRemoteMediaPlayerStatusUpdated(): Player status = unknown");
                        makeUiHidden = true;
                    }
                    if (makeUiHidden) {
                        stopNotificationService();
                    }
                    updateMiniControllersVisibility(!makeUiHidden);
                    updateMiniControllers();
//                    for (VideoCastConsumer consumer : mVideoConsumers) {
//                        consumer.onRemoteMediaPlayerStatusUpdated();
//                        consumer.onVolumeChanged(volume, isMute);
//                    }
                } catch (TransientNetworkDisconnectionException | NoConnectionException e) {
                    LOGE(TAG, "Failed to get volume state due to network issues", e);
                }
            }

            @Override
            public void onError(ServiceCommandError serviceCommandError) {

            }
        });

    }

//    private void onRemoteMediaPreloadStatusUpdated() {
//        MediaQueueItem item = null;
//        mMediaStatus = mRemoteMediaPlayer.getMediaStatus();
//        if (mMediaStatus != null) {
//            item = mMediaStatus.getQueueItemById(mMediaStatus.getPreloadedItemId());
//        }
//        mPreLoadingItem = item;
//        updateMiniControllersVisibilityForUpcoming(item);
//        LOGD(TAG, "onRemoteMediaPreloadStatusUpdated() " + item);
//        for (VideoCastConsumer consumer : mVideoConsumers) {
//            consumer.onRemoteMediaPreloadStatusUpdated(item);
//        }
//    }

//    public MediaQueueItem getPreLoadingItem() {
//        return mPreLoadingItem;
//    }

    /*
    * This is called by onQueueStatusUpdated() of RemoteMediaPlayer
    */
//    private void onQueueUpdated(List<MediaQueueItem> queueItems, MediaQueueItem item, int repeatMode,
//            boolean shuffle) {
//        LOGD(TAG, "onQueueUpdated() reached");
//        LOGD(TAG, String.format("Queue Items size: %d, Item: %s, Repeat Mode: %d, Shuffle: %s",
//                queueItems == null ? 0 : queueItems.size(), item, repeatMode, shuffle));
//        if (queueItems != null) {
//            mMediaQueue = new MediaQueue(new CopyOnWriteArrayList<>(queueItems), item, shuffle,
//                    repeatMode);
//        } else {
//            mMediaQueue = new MediaQueue(new CopyOnWriteArrayList<MediaQueueItem>(), null, false,
//                    MediaStatus.REPEAT_MODE_REPEAT_OFF);
//        }
//        for (VideoCastConsumer consumer : mVideoConsumers) {
//            consumer.onMediaQueueUpdated(queueItems, item, repeatMode, shuffle);
//        }
//    }

    /*
     * This is called by onMetadataUpdated() of RemoteMediaPlayer
     */
    public void onRemoteMediaPlayerMetadataUpdated() {
        LOGD(TAG, "onRemoteMediaPlayerMetadataUpdated() reached");
        updateLockScreenMetadata();
        for (VideoCastConsumer consumer : mVideoConsumers) {
            consumer.onRemoteMediaPlayerMetadataUpdated();
        }
        try {
            updateLockScreenImage(getRemoteMediaInformation());
        } catch (TransientNetworkDisconnectionException | NoConnectionException e) {
            LOGE(TAG, "Failed to update lock screen metadata due to a network issue", e);
        }
    }

    /*
     * Sets up the {@link RemoteControlClient} for this application. It also handles the audio
     * focus.
     */
    @SuppressLint("InlinedApi")
    private void setUpRemoteControl(final MediaInfo info) {
        if (!isFeatureEnabled(BaseCastManager.FEATURE_LOCKSCREEN)) {
            return;
        }
        LOGD(TAG, "setUpRemoteControl() was called");
        mAudioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);

        mMediaEventReceiver = new ComponentName(mContext, VideoIntentReceiver.class.getName());
        mAudioManager.registerMediaButtonEventReceiver(mMediaEventReceiver);

        if (mRemoteControlClientCompat == null) {
            Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
            intent.setComponent(mMediaButtonReceiverComponent);
            mRemoteControlClientCompat = new RemoteControlClientCompat(
                    PendingIntent.getBroadcast(mContext, 0, intent, 0));
            RemoteControlHelper.registerRemoteControlClient(mAudioManager,
                    mRemoteControlClientCompat);
        }
        mRemoteControlClientCompat.addToMediaRouter(mMediaRouter);
        mRemoteControlClientCompat.setTransportControlFlags(
                RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE);
        if (info == null) {
            mRemoteControlClientCompat.setPlaybackState(RemoteControlClient.PLAYSTATE_PAUSED);
            return;
        } else {
            mRemoteControlClientCompat.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);
        }

        // Update the remote control's image
        updateLockScreenImage(info);

        // update the remote control's metadata
        updateLockScreenMetadata();
    }

    /*
     * Updates lock screen image
     */
    private void updateLockScreenImage(final MediaInfo info) {
        if (info == null) {
            return;
        }
        setBitmapForLockScreen(info);
    }

    /*
     * Sets the appropriate {@link Bitmap} for the right size image for lock screen. In ICS and
     * JB, the image shown on the lock screen is a small size bitmap but for KitKat, the image is a
     * full-screen image so we need to separately handle these two cases.
     */
    private void setBitmapForLockScreen(MediaInfo video) {
        if (video == null || mRemoteControlClientCompat == null) {
            return;
        }
        Uri imgUrl = null;
        Bitmap bm = null;
        List<ImageInfo> images = video.getImages();
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2) {
            if (images.size() > 1) {
                imgUrl = Uri.parse(images.get(1).getUrl());
            } else if (images.size() == 1) {
                imgUrl = Uri.parse(images.get(0).getUrl());
            } else if (mContext != null) {
                // we don't have a url for image so get a placeholder image from resources
                bm = BitmapFactory.decodeResource(mContext.getResources(),
                        R.drawable.album_art_placeholder_large);
            }
        } else if (!images.isEmpty()) {
            imgUrl = Uri.parse(images.get(0).getUrl());
        } else {
            // we don't have a url for image so get a placeholder image from resources
            bm = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.album_art_placeholder);
        }
        if (bm != null) {
            mRemoteControlClientCompat.editMetadata(false).putBitmap(
                    RemoteControlClientCompat.MetadataEditorCompat.
                            METADATA_KEY_ARTWORK, bm).apply();
        } else {

            new FetchBitmapTask() {
                @Override
                protected void onPostExecute(Bitmap bitmap) {
                    if (mRemoteControlClientCompat != null) {
                        mRemoteControlClientCompat.editMetadata(false).putBitmap(
                                RemoteControlClientCompat.MetadataEditorCompat.
                                        METADATA_KEY_ARTWORK, bitmap).apply();
                    }
                }
            }.execute(imgUrl);
        }
    }
    /*
     * Updates the playback status of the RemoteControlClient
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void updateRemoteControl(boolean playing) {
        if (!isFeatureEnabled(FEATURE_LOCKSCREEN)) {
            return;
        }
        if (!isConnected()) {
            return;
        }
        try {
            if ((mRemoteControlClientCompat == null) && playing) {
                setUpRemoteControl(getRemoteMediaInformation());
            }
            if (mRemoteControlClientCompat != null) {
                int playState = isRemoteStreamLive() ? RemoteControlClient.PLAYSTATE_BUFFERING
                        : RemoteControlClient.PLAYSTATE_PLAYING;
                mRemoteControlClientCompat
                        .setPlaybackState(playing ? playState
                                : RemoteControlClient.PLAYSTATE_PAUSED);
            }
        } catch (TransientNetworkDisconnectionException | NoConnectionException e) {
            LOGE(TAG, "Failed to set up RCC due to network issues", e);
        }
    }

    /*
     * On ICS and JB, lock screen metadata is one liner: Title - Album Artist - Album. On KitKat, it
     * has two lines: Title , Album Artist - Album
     */
    private void updateLockScreenMetadata() {
        if ((mRemoteControlClientCompat == null) || !isFeatureEnabled(FEATURE_LOCKSCREEN)) {
            return;
        }

        try {
            // Update the remote controls
            MediaInfo info = getRemoteMediaInformation();
            if (info == null) {
                return;
            }
            //final MediaMetadata mm = info.getMetadata();

            mRemoteControlClientCompat.editMetadata(false)
                    .putString(MediaMetadataRetriever.METADATA_KEY_TITLE, info.getTitle())
                    .putString(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST, mContext.getResources().getString(R.string.ccl_casting_to_device, getDeviceName()))
                    .putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, info.getDuration())
                    .apply();
        } catch (NotFoundException e) {
            LOGE(TAG, "Failed to update RCC due to resource not found", e);
        } catch (TransientNetworkDisconnectionException | NoConnectionException e) {
            LOGE(TAG, "Failed to update RCC due to network issues", e);
        }
    }

    /*
     * Removes the remote control client
     */
    public void removeRemoteControlClient() {
        LOGD(TAG, "removeRemoteControlClient()");
        if (isFeatureEnabled(FEATURE_LOCKSCREEN)) {
            mAudioManager.abandonAudioFocus(null);
            if (mMediaEventReceiver != null) {
                mAudioManager.unregisterMediaButtonEventReceiver(mMediaEventReceiver);
            }
            if (mRemoteControlClientCompat != null) {
                RemoteControlHelper.unregisterRemoteControlClient(mAudioManager,
                        mRemoteControlClientCompat);
                mRemoteControlClientCompat = null;
            }
        }
    }

    /**
     * Registers an
     * {@link com.google.android.libraries.cast.companionlibrary.cast.callbacks.VideoCastConsumer}
     * interface with this class. Registered listeners will be notified of changes to a variety of
     * lifecycle and media status changes through the callbacks that the interface provides.
     *
     * @see VideoCastConsumerImpl
     */
    public synchronized void addVideoCastConsumer(VideoCastConsumer listener) {
        if (listener != null) {
            addBaseCastConsumer(listener);
            mVideoConsumers.add(listener);
            LOGD(TAG, "Successfully added the new CastConsumer listener " + listener);
        }
    }

    /**
     * Unregisters an
     * {@link com.google.android.libraries.cast.companionlibrary.cast.callbacks.VideoCastConsumer}.
     */
    public synchronized void removeVideoCastConsumer(VideoCastConsumer listener) {
        if (listener != null) {
            removeBaseCastConsumer(listener);
            mVideoConsumers.remove(listener);
        }
    }

    /**
     * Adds a new {@link IMiniController} component. Callers need to provide their own
     * {@link OnMiniControllerChangedListener}.
     *
     * @see {@link #removeMiniController(IMiniController)}
     */
    public void addMiniController(IMiniController miniController,
            OnMiniControllerChangedListener onChangedListener) {
        if (miniController != null) {
            boolean result;
            synchronized (mMiniControllers) {
                result = mMiniControllers.add(miniController);
            }
            if (result) {
                miniController.setOnMiniControllerChangedListener(onChangedListener == null ? this
                        : onChangedListener);
                try {
                    if (isConnected() && isRemoteMediaLoaded()) {
                        updateMiniController(miniController);
                        miniController.setVisibility(View.VISIBLE);
                    }
                } catch (TransientNetworkDisconnectionException | NoConnectionException e) {
                    LOGE(TAG, "Failed to get the status of media playback on receiver", e);
                }
                LOGD(TAG, "Successfully added the new MiniController " + miniController);
            } else {
                LOGD(TAG, "Attempting to adding " + miniController + " but it was already "
                        + "registered, skipping this step");
            }
        }
    }

    /**
     * Adds a new {@link IMiniController} component and assigns {@link VideoCastManager} as the
     * {@link OnMiniControllerChangedListener} for this component.
     */
    public void addMiniController(IMiniController miniController) {
        addMiniController(miniController, null);
    }

    /**
     * Removes a {@link IMiniController} listener from the list of listeners.
     */
    public void removeMiniController(IMiniController listener) {
        if (listener != null) {
            synchronized (mMiniControllers) {
                mMiniControllers.remove(listener);
            }
        }
    }

    @Override
    protected void onDeviceUnselected() {
        stopNotificationService();
        detachMediaChannel();
        //removeDataChannel();
        mState = MediaControl.PLAYER_STATE_IDLE;
    }

//    @Override
//    protected Builder getCastOptionBuilder(CastDevice device) {
//        Builder builder = Cast.CastOptions.builder(mSelectedCastDevice, new CastListener());
//        if (isFeatureEnabled(FEATURE_DEBUGGING)) {
//            builder.setVerboseLoggingEnabled(true);
//        }
//        return builder;
//    }

    @Override
    public void onConnectionFailed(ConnectableDevice connectableDevice, ServiceCommandError serviceCommandError) {
        super.onConnectionFailed(connectableDevice, serviceCommandError);
        updateRemoteControl(false);
        stopNotificationService();
    }

    @Override
    public void onDisconnected(boolean stopAppOnExit, boolean clearPersistedConnectionData,
            boolean setDefaultRoute) {
        super.onDisconnected(stopAppOnExit, clearPersistedConnectionData, setDefaultRoute);
        updateMiniControllersVisibility(false);
        if (clearPersistedConnectionData && !mConnectionSuspended) {
            removeRemoteControlClient();
        }
        mState = MediaControl.PLAYER_STATE_IDLE;
        mMediaQueue = null;
    }

    @Override
    protected MediaRouteDialogFactory getMediaRouteDialogFactory() {
        return new VideoMediaRouteDialogFactory();
    }

//    class CastListener extends Cast.Listener {
//
//        /*
//         * (non-Javadoc)
//         * @see com.google.android.gms.cast.Cast.Listener#onApplicationDisconnected (int)
//         */
//        @Override
//        public void onApplicationDisconnected(int statusCode) {
//            VideoCastManager.this.onApplicationDisconnected(statusCode);
//        }
//
//        /*
//         * (non-Javadoc)
//         * @see com.google.android.gms.cast.Cast.Listener#onApplicationStatusChanged ()
//         */
//        @Override
//        public void onApplicationStatusChanged() {
//            VideoCastManager.this.onApplicationStatusChanged();
//        }
//
//        @Override
//        public void onVolumeChanged() {
//            VideoCastManager.this.onVolumeChanged();
//        }
//    }

    @Override
    public void onFailed(int resourceId, int statusCode) {
        LOGD(TAG, "onFailed: " + mContext.getString(resourceId) + ", code: " + statusCode);
        super.onFailed(resourceId, statusCode);
    }

    /**
     * Returns the class for the full screen activity that can control the remote media playback.
     * This activity will also be invoked from the notification shade. If {@code null} is returned,
     * this library will use a default implementation.
     *
     * @see {@link VideoCastControllerActivity}
     */
    public Class<?> getTargetActivity() {
        return mTargetActivity;
    }

    /**
     * Clients can call this method to delegate handling of the volume. Clients should override
     * {@code dispatchEvent} and call this method:
     * <pre>
     public boolean dispatchKeyEvent(KeyEvent event) {
         if (mCastManager.onDispatchVolumeKeyEvent(event, VOLUME_DELTA)) {
            return true;
         }
         return super.dispatchKeyEvent(event);
     }
     * </pre>
     * @param event The dispatched event.
     * @param volumeDelta The amount by which volume should be increased or decreased in each step
     * @return <code>true</code> if volume is handled by the library, <code>false</code> otherwise.
     */
    public boolean onDispatchVolumeKeyEvent(KeyEvent event, double volumeDelta) {
        if (isConnected()) {
            boolean isKeyDown = event.getAction() == KeyEvent.ACTION_DOWN;

//            volumeDelta = (float) volumeDelta/10;
//            switch (event.getKeyCode()) {
//                case KeyEvent.KEYCODE_VOLUME_UP:
//                    if (changeVolume(volumeDelta, isKeyDown)) {
//                        return true;
//                    }
//                    break;
//                case KeyEvent.KEYCODE_VOLUME_DOWN:
//                    if (changeVolume(-volumeDelta, isKeyDown)) {
//                        return true;
//                    }
//                    break;
//            }

            int keyCode = event.getKeyCode();
            if(isKeyDown && getRouteInfo()!=null) {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    int delta = (int)volumeDelta;
                    updateVolume(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ? -delta : delta);
                    return true;
                }
            }

        }
        return false;
    }

    private boolean changeVolume(double volumeIncrement, boolean isKeyDown) {
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
                && getPlaybackStatus() == MediaControl.PLAYER_STATE_PLAYING
                && isFeatureEnabled(BaseCastManager.FEATURE_LOCKSCREEN)) {
            return false;
        }

        if (isKeyDown) {
            try {
                adjustVolume(volumeIncrement);
            } catch (CastException | TransientNetworkDisconnectionException |
                    NoConnectionException e) {
                LOGE(TAG, "Failed to change volume", e);
            }
        }
        return true;
    }

    /**
     * Sets the volume step, i.e. the fraction by which volume will increase or decrease each time
     * user presses the hard volume buttons on the device.
     *
     * @param volumeStep Should be a double between 0 and 1, inclusive.
     */
    public VideoCastManager setVolumeStep(double volumeStep) {
        if ((volumeStep > 1) || (volumeStep < 0)) {
            throw new IllegalArgumentException("Volume Step should be between 0 and 1, inclusive");
        }
        mVolumeStep = volumeStep;
        return this;
    }

    /**
     * Returns the volume step. The default value is {@code DEFAULT_VOLUME_STEP}.
     */
    public double getVolumeStep() {
        return mVolumeStep;
    }

    /**
     * Set the live stream duration; this is purely used in the reconnection logic. If this method
     * is not called, the default value {@code DEFAULT_LIVE_STREAM_DURATION_MS} is used.
     *
     * @param duration Duration, specified in milliseconds.
     */
    public void setLiveStreamDuration(long duration) {
        mLiveStreamDuration = duration;
    }

    /**
     * Sets the active tracks for the currently loaded media.
     */
    public void setActiveTrackIds(long[] trackIds) {
        LOGE(TAG, "setActiveTrackIds not yet implemented");

//        if (mRemoteMediaPlayer == null || mRemoteMediaPlayer.getMediaInfo() == null) {
//            return;
//        }
//
//        mRemoteMediaPlayer.setActiveMediaTracks(mApiClient, trackIds)
//                .setResultCallback(new ResultCallback<MediaChannelResult>() {
//                    @Override
//                    public void onResult(MediaChannelResult mediaChannelResult) {
//                        LOGD(TAG, "Setting track result was successful? "
//                                + mediaChannelResult.getStatus().isSuccess());
//                        if (!mediaChannelResult.getStatus().isSuccess()) {
//                            LOGD(TAG, "Failed since: " + mediaChannelResult.getStatus()
//                                    + " and status code:" + mediaChannelResult.getStatus()
//                                    .getStatusCode());
//                        }
//                    }
//                });
    }

    /**
     * Sets or updates the style of the Text Track.
     */
//    public void setTextTrackStyle(TextTrackStyle style) {
//        mRemoteMediaPlayer.setTextTrackStyle(mApiClient, style)
//                .setResultCallback(new ResultCallback<MediaChannelResult>() {
//                    @Override
//                    public void onResult(MediaChannelResult result) {
//                        if (!result.getStatus().isSuccess()) {
//                            onFailed(R.string.ccl_failed_to_set_track_style,
//                                    result.getStatus().getStatusCode());
//                        }
//                    }
//                });
//        for (VideoCastConsumer consumer : mVideoConsumers) {
//            try {
//                consumer.onTextTrackStyleChanged(style);
//            } catch (Exception e) {
//                LOGE(TAG, "onTextTrackStyleChanged(): Failed to inform " + consumer, e);
//            }
//        }
//    }

    /**
     * Signals a change in the Text Track style. Clients should not call this directly.
     */
//    public void onTextTrackStyleChanged(TextTrackStyle style) {
//        LOGD(TAG, "onTextTrackStyleChanged() reached");
//        if (mRemoteMediaPlayer == null || mRemoteMediaPlayer.getMediaInfo() == null) {
//            return;
//        }
//        mRemoteMediaPlayer.setTextTrackStyle(mApiClient, style)
//                .setResultCallback(new ResultCallback<MediaChannelResult>() {
//                    @Override
//                    public void onResult(MediaChannelResult result) {
//                        if (!result.getStatus().isSuccess()) {
//                            onFailed(R.string.ccl_failed_to_set_track_style,
//                                    result.getStatus().getStatusCode());
//                        }
//                    }
//                });
//        for (VideoCastConsumer consumer : mVideoConsumers) {
//            try {
//                consumer.onTextTrackStyleChanged(style);
//            } catch (Exception e) {
//                LOGE(TAG, "onTextTrackStyleChanged(): Failed to inform " + consumer, e);
//            }
//        }
//    }

    /**
     * Signals a change in the Text Track on/off state. Clients should not call this directly.
     */
    public void onTextTrackEnabledChanged(boolean isEnabled) {
        LOGD(TAG, "onTextTrackEnabledChanged() reached");
        if (!isEnabled) {
            setActiveTrackIds(new long[]{});
        }

        for (VideoCastConsumer consumer : mVideoConsumers) {
            consumer.onTextTrackEnabledChanged(isEnabled);
        }
    }

    /**
     * Signals a change in the Text Track locale. Clients should not call this directly.
     */
    public void onTextTrackLocaleChanged(Locale locale) {
        LOGD(TAG, "onTextTrackLocaleChanged() reached");
        for (VideoCastConsumer consumer : mVideoConsumers) {
            consumer.onTextTrackLocaleChanged(locale);
        }
    }

    @SuppressLint("NewApi")
    private void registerCaptionListener(final Context context) {
        if (Utils.IS_KITKAT_OR_ABOVE) {
            CaptioningManager captioningManager =
                    (CaptioningManager) context.getSystemService(Context.CAPTIONING_SERVICE);
            captioningManager.addCaptioningChangeListener(
                    new CaptioningManager.CaptioningChangeListener() {
                        @Override
                        public void onEnabledChanged(boolean enabled) {
                            onTextTrackEnabledChanged(enabled);
                        }

                        @Override
                        public void onUserStyleChanged(CaptioningManager.CaptionStyle userStyle) {
                            //onTextTrackStyleChanged(mTrackManager.getTextTrackStyle());
                        }

                        @Override
                        public void onFontScaleChanged(float fontScale) {
                            //onTextTrackStyleChanged(mTrackManager.getTextTrackStyle());
                        }

                        @Override
                        public void onLocaleChanged(Locale locale) {
                            onTextTrackLocaleChanged(locale);
                        }
                    }
            );
        }
    }

    /**
     * Updates the summary of the captions between "on" and "off" based on the user selected
     * preferences. This can be called by the caller application when they add captions settings to
     * their preferences. Preferably this should be called in the {@code onResume()} of the
     * PreferenceActivity so that it gets updated when needed.
     */
    public void updateCaptionSummary(String captionScreenKey, PreferenceScreen preferenceScreen) {
        int status = R.string.ccl_info_na;
        if (isFeatureEnabled(FEATURE_CAPTIONS_PREFERENCE)) {
            status = mTrackManager.isCaptionEnabled() ? R.string.ccl_on : R.string.ccl_off;
        }
        preferenceScreen.findPreference(captionScreenKey)
                .setSummary(status);
    }

    /**
     * Returns the instance of {@link TracksPreferenceManager} that is being used.
     */
    public TracksPreferenceManager getTracksPreferenceManager() {
        return mTrackManager;
    }

    /**
     * Returns the list of current active tracks. If there is no remote media, then this will
     * return <code>null</code>.
     */
    public long[] getActiveTrackIds() {
//        if (mRemoteMediaPlayer != null && mRemoteMediaPlayer.getMediaStatus() != null) {
//            return mRemoteMediaPlayer.getMediaStatus().getActiveTrackIds();
//        }
        return null;
    }

    /**
     * Adds an
     * {@link com.google.android.libraries.cast.companionlibrary.cast.tracks.OnTracksSelectedListener} // NOLINT
     * to the lis of listeners.
     */
    public void addTracksSelectedListener(OnTracksSelectedListener listener) {
        if (listener != null) {
            mTracksSelectedListeners.add(listener);
        }
    }

    /**
     * Removes an
     * {@link com.google.android.libraries.cast.companionlibrary.cast.tracks.OnTracksSelectedListener} // NOLINT
     * from the lis of listeners.
     */
    public void removeTracksSelectedListener(OnTracksSelectedListener listener) {
        if (listener != null) {
            mTracksSelectedListeners.remove(listener);
        }
    }

    /**
     * Notifies all the
     * {@link com.google.android.libraries.cast.companionlibrary.cast.tracks.OnTracksSelectedListener} // NOLINT
     * that the set of active tracks has changed.
     *
     * @param tracks the set of active tracks. Must be {@code non-null} but can be an empty list.
     */
//    public void notifyTracksSelectedListeners(List<MediaTrack> tracks) {
//        if (tracks == null) {
//            throw new IllegalArgumentException("tracks must not be null");
//        }
//        for (OnTracksSelectedListener listener : mTracksSelectedListeners) {
//            listener.onTracksSelected(tracks);
//        }
//    }

    public final MediaQueue getMediaQueue() {
        return mMediaQueue;
    }

    private void stopProgressTimer() {
        LOGD(TAG, "Stopped TrickPlay Timer");
        if (mProgressTask != null) {
            mProgressTask.cancel();
            mProgressTask = null;
        }
        if (mProgressTimer != null) {
            mProgressTimer.cancel();
            mProgressTimer = null;
        }
    }

    private void restartProgressTimer() {
        stopProgressTimer();
        mProgressTimer = new Timer();
        mProgressTask = new UpdateProgressTask();
        mProgressTimer.scheduleAtFixedRate(mProgressTask, 100, PROGRESS_UPDATE_INTERVAL_MS);
        LOGD(TAG, "Restarted Progress Timer");
    }

    private class UpdateProgressTask extends TimerTask {

        @Override
        public void run() {
            //final int currentPos;
            if (mState == MediaControl.PLAYER_STATE_BUFFERING || mState == MediaControl.PLAYER_STATE_IDLE || !isConnected() || mRemoteMediaPlayer == null) {
                return;
            }
            try {
//                int duration = (int) getMediaDuration();
//                if (duration > 0) {
//                    currentPos = (int) getCurrentMediaPosition();
//                    updateProgress(currentPos, duration);
//                }

                getMediaDuration(new MediaControl.DurationListener() {
                    @Override
                    public void onSuccess(Long duration) {
                        final int mediaDuration = duration.intValue();
                        if (mediaDuration > 0) {
                            try {
                                getCurrentMediaPosition(new MediaControl.PositionListener() {
                                    @Override
                                    public void onSuccess(Long position) {
                                        final int currentPos = position.intValue();
                                        updateProgress(currentPos, mediaDuration);
                                    }

                                    @Override
                                    public void onError(ServiceCommandError serviceCommandError) {
                                        LOGE(TAG, "Failed to update the progress tracker due to network issues", serviceCommandError);
                                    }
                                });
                            } catch (TransientNetworkDisconnectionException | NoConnectionException e) {
                                LOGE(TAG, "Failed to update the progress tracker due to network issues", e);
                            }

                        }
                    }

                    @Override
                    public void onError(ServiceCommandError serviceCommandError) {
                        LOGE(TAG, "Failed to update the progress tracker due to network issues", serviceCommandError);
                    }
                });

            } catch (TransientNetworkDisconnectionException | NoConnectionException e) {
                LOGE(TAG, "Failed to update the progress tracker due to network issues", e);
            }
        }
    }

    /**
     * <b>Note:</b> This is called on a worker thread
     */
    private void updateProgress(int currentPosition, int duration) {
        synchronized (mMiniControllers) {
            for (final IMiniController controller : mMiniControllers) {
                controller.setProgress(currentPosition, duration);
            }
        }
    }

    /**
     * Sets the policy to be used for the visibility of skip forward/backward on the {@link
     * VideoCastControllerActivity}. Note that the new policy is enforced the next time that
     * activity is opened and does not apply to the currently runnig one, if any.
     *
     * @param policy can be one of {@link VideoCastController#NEXT_PREV_VISIBILITY_POLICY_DISABLED},
     * {@link VideoCastController#NEXT_PREV_VISIBILITY_POLICY_HIDDEN} or
     * {@link VideoCastController#NEXT_PREV_VISIBILITY_POLICY_ALWAYS}.
     */
    public void setNextPreviousVisibilityPolicy(final int policy) {
        switch(policy) {
            case VideoCastController.NEXT_PREV_VISIBILITY_POLICY_DISABLED:
            case VideoCastController.NEXT_PREV_VISIBILITY_POLICY_ALWAYS:
            case VideoCastController.NEXT_PREV_VISIBILITY_POLICY_HIDDEN:
                mNextPreviousVisibilityPolicy = policy;
                return;
            default:
                LOGD(TAG, "Invalid value for the NextPreviousVisibilityPolicy was requested");
        }
        throw new IllegalArgumentException(
                "Invalid value for the NextPreviousVisibilityPolicy was requested");
    }

}
