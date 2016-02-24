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

package com.google.android.libraries.cast.companionlibrary.widgets;

//import com.google.android.gms.cast.MediaInfo;
//import com.google.android.gms.cast.MediaMetadata;
//import com.google.android.gms.cast.MediaQueueItem;
//import com.google.android.gms.cast.MediaStatus;
import com.connectsdk.core.MediaInfoWithCustomData;
import com.connectsdk.service.capability.MediaControl;
import com.google.android.libraries.cast.companionlibrary.R;
import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.CastException;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.NoConnectionException;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.OnFailedListener;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.TransientNetworkDisconnectionException;
import com.google.android.libraries.cast.companionlibrary.utils.FetchBitmapTask;
import com.google.android.libraries.cast.companionlibrary.utils.Utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * A compound component that provides a superset of functionalities required for the global access
 * requirement. This component provides an image for the album art, a play/pause button, a seekbar
 * for trick-play with current time and duration and a mute/unmute button. Clients can add this
 * compound component to their layout xml and register that with the instance of
 * {@link VideoCastManager} by using the following pattern:<br/>
 *
 * <pre>
 * mMiniController = (MiniController) findViewById(R.id.miniController1);
 * mCastManager.addMiniController(mMiniController);
 * mMiniController.setOnMiniControllerChangedListener(mCastManager);
 * </pre>
 *
 * Then the {@link VideoCastManager} will manage the behavior, including its state and metadata and
 * interactions.
 */
public class MiniController extends RelativeLayout implements IMiniController {

    public static final int UNDEFINED_STATUS_CODE = -1;
    private Handler mHandler;
    protected ImageView mIcon;
    protected TextView mTitle;
    protected TextView mSubTitle;
    protected ImageView mPlayPause;
    protected ProgressBar mLoading;
    private OnMiniControllerChangedListener mListener;
    private Uri mIconUri;
    private Drawable mPauseDrawable;
    private Drawable mPlayDrawable;
    private int mStreamType = MediaInfoWithCustomData.STREAM_TYPE_BUFFERED;
    private Drawable mStopDrawable;
    private FetchBitmapTask mFetchBitmapTask;
    private ProgressBar mProgressBar;
    private ImageView mUpcomingIcon;
    private TextView mUpcomingTitle;
    private View mUpcomingContainer;
    private View mUpcomingPlay;
    private View mUpcomingStop;
    private Uri mUpcomingIconUri;
    private FetchBitmapTask mFetchUpcomingBitmapTask;
    private View mMainContainer;
    //private MediaQueueItem mUpcomingItem;

    public MiniController(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.mini_controller, this);
        mPauseDrawable = getResources().getDrawable(R.drawable.ic_mini_controller_pause);
        mPlayDrawable = getResources().getDrawable(R.drawable.ic_mini_controller_play);
        mStopDrawable = getResources().getDrawable(R.drawable.ic_mini_controller_stop);
        mHandler = new Handler();
        loadViews();
        setUpCallbacks();
    }

    /**
     * Sets the listener that should be notified when a relevant event is fired from this
     * component.
     * Clients can register the {@link VideoCastManager} instance to be the default listener so it
     * can control the remote media playback.
     */
    @Override
    public void setOnMiniControllerChangedListener(OnMiniControllerChangedListener listener) {
        if (listener != null) {
            this.mListener = listener;
        }
    }

    /**
     * Removes the listener that was registered by
     * {@link #setOnMiniControllerChangedListener(OnMiniControllerChangedListener)}
     */
    public void removeOnMiniControllerChangedListener(OnMiniControllerChangedListener listener) {
        if ((listener != null) && (mListener == listener)) {
            mListener = null;
        }
    }

    @Override
    public void setStreamType(int streamType) {
        mStreamType = streamType;
    }

    @Override
    public void setProgress(final int progress, final int duration) {
        // for live streams, we do not attempt to update the progress bar
        if (mStreamType == MediaInfoWithCustomData.STREAM_TYPE_LIVE || mProgressBar == null) {
            return;
        }
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mProgressBar.setMax(duration);
                mProgressBar.setProgress(progress);
            }
        });
    }

    @Override
    public void setProgressVisibility(boolean visible) {
        if (mProgressBar == null) {
            return;
        }
        mProgressBar.setVisibility(
                visible && (mStreamType != MediaInfoWithCustomData.STREAM_TYPE_LIVE) ? View.VISIBLE
                        : View.INVISIBLE);

        mProgressBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void setUpcomingVisibility(boolean visible) {
        mUpcomingContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
        setProgressVisibility(!visible);
    }

//    @Override
//    public void setUpcomingItem(MediaQueueItem item) {
//        mUpcomingItem = item;
//        if (item != null) {
//            MediaInfo mediaInfo = item.getMedia();
//            if (mediaInfo != null) {
//                MediaMetadata metadata = mediaInfo.getMetadata();
//                setUpcomingTitle(metadata.getString(MediaMetadata.KEY_TITLE));
//                setUpcomingIcon(Utils.getImageUri(mediaInfo, 0));
//            }
//        } else {
//            setUpcomingTitle("");
//            setUpcomingIcon((Uri) null);
//        }
//    }

    @Override
    public void setCurrentVisibility(boolean visible) {
        mMainContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void setUpCallbacks() {

        mPlayPause.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (mListener != null) {
                    setLoadingVisibility(true);
                    try {
                        mListener.onPlayPauseClicked(v);
                    } catch (CastException e) {
                        mListener.onFailed(R.string.ccl_failed_perform_action,
                                UNDEFINED_STATUS_CODE);
                    } catch (TransientNetworkDisconnectionException e) {
                        mListener.onFailed(R.string.ccl_failed_no_connection_trans,
                                UNDEFINED_STATUS_CODE);
                    } catch (NoConnectionException e) {
                        mListener
                                .onFailed(R.string.ccl_failed_no_connection, UNDEFINED_STATUS_CODE);
                    }
                }
            }
        });

        mMainContainer.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {

                if (mListener != null) {
                    setLoadingVisibility(false);
                    try {
                        mListener.onTargetActivityInvoked(mIcon.getContext());
                    } catch (Exception e) {
                        mListener.onFailed(R.string.ccl_failed_perform_action, -1);
                    }
                }

            }
        });

        mUpcomingPlay.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mListener != null) {
                    //mListener.onUpcomingPlayClicked(v, mUpcomingItem);
                }
            }
        });

        mUpcomingStop.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mListener != null) {
                    //mListener.onUpcomingStopClicked(v, mUpcomingItem);
                }
            }
        });
    }

    public MiniController(Context context) {
        super(context);
        loadViews();
    }

    @Override
    public final void setIcon(Bitmap bm) {
        mIcon.setImageBitmap(bm);
    }

    private void setUpcomingIcon(Bitmap bm) {
        mUpcomingIcon.setImageBitmap(bm);
    }

    @Override
    public void setIcon(Uri uri) {
        if (mIconUri != null && mIconUri.equals(uri)) {
            return;
        }

        mIconUri = uri;
        if (mFetchBitmapTask != null) {
            mFetchBitmapTask.cancel(true);
        }
        mFetchBitmapTask = new FetchBitmapTask() {
            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if (bitmap == null) {
                    bitmap = BitmapFactory.decodeResource(getResources(),
                            R.drawable.album_art_placeholder);
                }
                setIcon(bitmap);
                if (this == mFetchBitmapTask) {
                    mFetchBitmapTask = null;
                }
            }
        };

        mFetchBitmapTask.execute(uri);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mFetchBitmapTask != null) {
            mFetchBitmapTask.cancel(true);
            mFetchBitmapTask = null;
        }
    }

    @Override
    public void setTitle(String title) {
        mTitle.setText(title);
    }

    @Override
    public void setSubtitle(String subtitle) {
        mSubTitle.setText(subtitle);
    }

    @Override
    public void setPlaybackStatus(int state, int idleReason) {
        switch (state) {
            case MediaControl.PLAYER_STATE_PLAYING:
                mPlayPause.setVisibility(View.VISIBLE);
                mPlayPause.setImageDrawable(getPauseStopDrawable());
                setLoadingVisibility(false);
                break;
            case MediaControl.PLAYER_STATE_PAUSED:
                mPlayPause.setVisibility(View.VISIBLE);
                mPlayPause.setImageDrawable(mPlayDrawable);
                setLoadingVisibility(false);
                break;
            case MediaControl.PLAYER_STATE_IDLE:
//                switch (mStreamType) {
                    //case MediaInfo.STREAM_TYPE_BUFFERED:
                        mPlayPause.setVisibility(View.INVISIBLE);
                        setLoadingVisibility(false);
//                        break;
//                    case MediaInfo.STREAM_TYPE_LIVE:
//                        if (idleReason == MediaStatus.IDLE_REASON_CANCELED) {
//                            mPlayPause.setVisibility(View.VISIBLE);
//                            mPlayPause.setImageDrawable(mPlayDrawable);
//                            setLoadingVisibility(false);
//                        } else {
//                            mPlayPause.setVisibility(View.INVISIBLE);
//                            setLoadingVisibility(false);
//                        }
//                        break;
//                }
                break;
            case MediaControl.PLAYER_STATE_BUFFERING:
                mPlayPause.setVisibility(View.INVISIBLE);
                setLoadingVisibility(true);
                break;
            default:
                mPlayPause.setVisibility(View.INVISIBLE);
                setLoadingVisibility(false);
                break;
        }
    }

    @Override
    public boolean isVisible() {
        return isShown();
    }

    private void loadViews() {
        mIcon = (ImageView) findViewById(R.id.icon_view);
        mTitle = (TextView) findViewById(R.id.title_view);
        mSubTitle = (TextView) findViewById(R.id.subtitle_view);
        mPlayPause = (ImageView) findViewById(R.id.play_pause);
        mLoading = (ProgressBar) findViewById(R.id.loading_view);
        mMainContainer = findViewById(R.id.container_current);
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mUpcomingIcon = (ImageView) findViewById(R.id.icon_view_upcoming);
        mUpcomingTitle = (TextView) findViewById(R.id.title_view_upcoming);
        mUpcomingContainer = findViewById(R.id.container_upcoming);
        mUpcomingPlay = findViewById(R.id.play_upcoming);
        mUpcomingStop = findViewById(R.id.stop_upcoming);
    }

    private void setLoadingVisibility(boolean show) {
        mLoading.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private Drawable getPauseStopDrawable() {
//        switch (mStreamType) {
//            case MediaInfo.STREAM_TYPE_BUFFERED:
//                return mPauseDrawable;
//            case MediaInfo.STREAM_TYPE_LIVE:
//                return mStopDrawable;
//            default:
                return mPauseDrawable;
//        }
    }

    private void setUpcomingIcon(Uri uri) {
        if (mUpcomingIconUri != null && mUpcomingIconUri.equals(uri)) {
            return;
        }

        mUpcomingIconUri = uri;
        if (mFetchUpcomingBitmapTask != null) {
            mFetchUpcomingBitmapTask.cancel(true);
        }
        mFetchUpcomingBitmapTask = new FetchBitmapTask() {
            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if (bitmap == null) {
                    bitmap = BitmapFactory.decodeResource(getResources(),
                            R.drawable.album_art_placeholder);
                }
                setUpcomingIcon(bitmap);
                if (this == mFetchUpcomingBitmapTask) {
                    mFetchUpcomingBitmapTask = null;
                }
            }
        };

        mFetchUpcomingBitmapTask.execute(uri);
    }

    private void setUpcomingTitle(String title) {
        mUpcomingTitle.setText(title);
    }

    /**
     * The interface for a listener that will be called when user interacts with the
     * {@link MiniController}, like clicking on the play/pause button, etc.
     */
    public interface OnMiniControllerChangedListener extends OnFailedListener {

        /**
         * Notification that user has clicked on the Play/Pause button
         *
         * @throws TransientNetworkDisconnectionException
         * @throws NoConnectionException
         * @throws CastException
         */
        void onPlayPauseClicked(View v) throws CastException,
                TransientNetworkDisconnectionException, NoConnectionException;

        /**
         * Notification that the user has clicked on the album art
         *
         * @throws NoConnectionException
         * @throws TransientNetworkDisconnectionException
         */
        void onTargetActivityInvoked(Context context)
                throws TransientNetworkDisconnectionException, NoConnectionException;

        /**
         * Is called when the "play" button in the upcoming area is clicked.
         */
        //void onUpcomingPlayClicked(View v, MediaQueueItem upcomingItem);

        /**
         * Is called when the "stop" button in the upcoming area is clicked.
         */
        //void onUpcomingStopClicked(View view, MediaQueueItem upcomingItem);

    }
}
