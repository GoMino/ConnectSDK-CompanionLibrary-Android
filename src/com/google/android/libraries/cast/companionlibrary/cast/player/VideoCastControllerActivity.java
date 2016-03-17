/*
 * Copyright (C) 2014 Google Inc. All Rights Reserved.
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

import static com.google.android.libraries.cast.companionlibrary.utils.LogUtils.LOGD;
import static com.google.android.libraries.cast.companionlibrary.utils.LogUtils.LOGE;

//import com.google.android.gms.cast.MediaInfo;
//import com.google.android.gms.cast.MediaMetadata;
//import com.google.android.gms.cast.MediaStatus;
import com.connectsdk.service.capability.MediaControl;
import com.google.android.libraries.cast.companionlibrary.R;
import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.NoConnectionException;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions
        .TransientNetworkDisconnectionException;
import com.google.android.libraries.cast.companionlibrary.cast.tracks.ui.TracksChooserDialog;
import com.google.android.libraries.cast.companionlibrary.utils.LogUtils;
import com.google.android.libraries.cast.companionlibrary.utils.Utils;
import com.google.android.libraries.cast.companionlibrary.widgets.MiniController;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

/**
 * This class provides an {@link android.app.Activity} that clients can easily add to their
 * applications to provide an out-of-the-box remote player when a video is casting to a cast device.
 * {@link com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager} can manage the
 * lifecycle and presentation of this activity.
 * <p>
 * This activity provides a number of controllers for managing the playback of the remote content:
 * play/pause (or play/stop when a live stream is used) and seekbar (for non-live streams).
 * <p>
 * Clients who need to perform a pre-authorization process for playback can register a
 * {@link com.google.android.libraries.cast.companionlibrary.cast.player.MediaAuthListener} by
 * calling
 * {@link VideoCastManager#startVideoCastControllerActivity(android.content.Context, com.google.android.libraries.cast.companionlibrary.cast.player.MediaAuthService)}
 * In that case, this activity manages starting the
 * {@link com.google.android.libraries.cast.companionlibrary.cast.player.MediaAuthService} and will
 * register a listener to handle the result.
 */
public class VideoCastControllerActivity extends ActionBarActivity implements
        VideoCastController {

    private static final String TAG = LogUtils
            .makeLogTag(VideoCastControllerActivity.class);
    public static final String TASK_TAG = "task";
    public static final String DIALOG_TAG = "dialog";
    private VideoCastManager mCastManager;
    private View mPageView;
    private ImageButton mPlayPause;
    private TextView mLiveText;
    private TextView mStart;
    private TextView mEnd;
    private SeekBar mSeekbar;
    private TextView mLine2;
    private ProgressBar mLoading;
    private double mVolumeIncrement;
    private View mControllers;
    private Drawable mPauseDrawable;
    private Drawable mPlayDrawable;
    private Drawable mStopDrawable;
    private OnVideoCastControllerListener mListener;
    private int mStreamType;
    private ImageButton mClosedCaptionIcon;
    private ImageButton mSkipNext;
    private ImageButton mSkipPrevious;
    private View mPlaybackControls;
    private MiniController mMini;
    private Toolbar mToolbar;
    private int mNextPreviousVisibilityPolicy
            = VideoCastController.NEXT_PREV_VISIBILITY_POLICY_DISABLED;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.cast_activity);
        loadAndSetupViews();
        mCastManager = VideoCastManager.getInstance();
        mVolumeIncrement = mCastManager.getVolumeStep();

        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            finish();
            return;
        }

        Bundle mediaWrapper = extras.getBundle(VideoCastManager.EXTRA_MEDIA);
        String title = mediaWrapper.getString(Utils.KEY_TITLE);
        setUpActionBar(title);

        FragmentManager fm = getSupportFragmentManager();
        VideoCastControllerFragment videoCastControlleFragment
                = (VideoCastControllerFragment) fm.findFragmentByTag(
                TASK_TAG);

        // if fragment is null, it means this is the first time, so create it
        if (videoCastControlleFragment == null) {
            videoCastControlleFragment = VideoCastControllerFragment
                    .newInstance(extras);
            fm.beginTransaction().add(videoCastControlleFragment, TASK_TAG).commit();
            mListener = videoCastControlleFragment;
            setOnVideoCastControllerChangedListener(mListener);
        } else {
            mListener = videoCastControlleFragment;
            mListener.onConfigurationChanged();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCastManager.addMiniController(mMini);
    }

    @Override
    protected void onPause() {
        mCastManager.removeMiniController(mMini);
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.cast_player_menu, menu);
        mCastManager.addMediaRouterButton(menu, R.id.media_route_menu_item);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return mCastManager.onDispatchVolumeKeyEvent(event, mVolumeIncrement) || super
                .dispatchKeyEvent(event);
    }

    private void loadAndSetupViews() {
        mPauseDrawable = getResources().getDrawable(R.drawable.ic_pause_circle_white_80dp);
        mPlayDrawable = getResources().getDrawable(R.drawable.ic_play_circle_white_80dp);
        mStopDrawable = getResources().getDrawable(R.drawable.ic_stop_circle_white_80dp);
        mPageView = findViewById(R.id.pageview);
        mPlayPause = (ImageButton) findViewById(R.id.play_pause_toggle);
        mLiveText = (TextView) findViewById(R.id.live_text);
        mStart = (TextView) findViewById(R.id.start_text);
        mEnd = (TextView) findViewById(R.id.end_text);
        mSeekbar = (SeekBar) findViewById(R.id.seekbar);
        mLine2 = (TextView) findViewById(R.id.textview2);
        mLoading = (ProgressBar) findViewById(R.id.progressbar1);
        mControllers = findViewById(R.id.controllers);
        mClosedCaptionIcon = (ImageButton) findViewById(R.id.cc);
        mSkipNext = (ImageButton) findViewById(R.id.next);
        mSkipPrevious = (ImageButton) findViewById(R.id.previous);
        mPlaybackControls = findViewById(R.id.playback_controls);
        mMini = (MiniController) findViewById(R.id.miniController1);
        mMini.setCurrentVisibility(false);
        setClosedCaptionState(CC_DISABLED);
        mPlayPause.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                try {
                    mListener.onPlayPauseClicked(v);
                } catch (TransientNetworkDisconnectionException e) {
                    LOGE(TAG, "Failed to toggle playback due to temporary network issue", e);
                    Utils.showToast(VideoCastControllerActivity.this,
                            R.string.ccl_failed_no_connection_trans);
                } catch (NoConnectionException e) {
                    LOGE(TAG, "Failed to toggle playback due to network issues", e);
                    Utils.showToast(VideoCastControllerActivity.this,
                            R.string.ccl_failed_no_connection);
                } catch (Exception e) {
                    LOGE(TAG, "Failed to toggle playback due to other issues", e);
                    Utils.showToast(VideoCastControllerActivity.this,
                            R.string.ccl_failed_perform_action);
                }
            }
        });

        mSeekbar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                try {
                    if (mListener != null) {
                        mListener.onStopTrackingTouch(seekBar);
                    }
                } catch (Exception e) {
                    LOGE(TAG, "Failed to complete seek", e);
                    finish();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                try {
                    if (mListener != null) {
                        mListener.onStartTrackingTouch(seekBar);
                    }
                } catch (Exception e) {
                    LOGE(TAG, "Failed to start seek", e);
                    finish();
                }
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                mStart.setText(Utils.formatMillis(progress));
                try {
                    if (mListener != null) {
                        mListener.onProgressChanged(seekBar, progress, fromUser);
                    }
                } catch (Exception e) {
                    LOGE(TAG, "Failed to set the progress result", e);
                }
            }
        });

        mClosedCaptionIcon.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    showTracksChooserDialog();
                } catch (TransientNetworkDisconnectionException | NoConnectionException e) {
                    LOGE(TAG, "Failed to get the media", e);
                }
            }
        });

        mSkipNext.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    mListener.onSkipNextClicked(v);
                } catch (TransientNetworkDisconnectionException | NoConnectionException e) {
                    LOGE(TAG, "Failed to move to the next item in the queue", e);
                }
            }
        });

        mSkipPrevious.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    mListener.onSkipPreviousClicked(v);
                } catch (TransientNetworkDisconnectionException | NoConnectionException e) {
                    LOGE(TAG, "Failed to move to the previous item in the queue", e);
                }
            }
        });
    }

    private void showTracksChooserDialog()
            throws TransientNetworkDisconnectionException, NoConnectionException {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        Fragment prev = getSupportFragmentManager().findFragmentByTag(DIALOG_TAG);
        if (prev != null) {
            transaction.remove(prev);
        }
        transaction.addToBackStack(null);

        // Create and show the dialog.
        TracksChooserDialog dialogFragment = TracksChooserDialog.newInstance(mCastManager.getRemoteMediaInformation());
        dialogFragment.show(transaction, DIALOG_TAG);
    }

    private void setUpActionBar(String title) {
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public void showLoading(boolean visible) {
        mLoading.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    public void adjustControllersForLiveStream(boolean isLive) {
        int visibility = isLive ? View.INVISIBLE : View.VISIBLE;
        mLiveText.setVisibility(isLive ? View.VISIBLE : View.INVISIBLE);
        mStart.setVisibility(visibility);
        mEnd.setVisibility(visibility);
        mSeekbar.setVisibility(visibility);
    }

    @Override
    public void setClosedCaptionState(int status) {
        switch (status) {
            case CC_ENABLED:
                mClosedCaptionIcon.setVisibility(View.VISIBLE);
                mClosedCaptionIcon.setEnabled(true);
                break;
            case CC_DISABLED:
                mClosedCaptionIcon.setVisibility(View.VISIBLE);
                mClosedCaptionIcon.setEnabled(false);
                break;
            case CC_HIDDEN:
                mClosedCaptionIcon.setVisibility(View.GONE);
                break;
            default:
                LOGE(TAG, "setClosedCaptionState(): Invalid state requested: " + status);
        }
    }

    @Override
    public void onQueueItemsUpdated(int queueLength, int position) {
        boolean prevAvailable = position > 0 ;
        boolean nextAvailable = position < queueLength - 1;
        switch(mNextPreviousVisibilityPolicy) {
            case VideoCastController.NEXT_PREV_VISIBILITY_POLICY_HIDDEN:
                if (nextAvailable) {
                    mSkipNext.setVisibility(View.VISIBLE);
                    mSkipNext.setEnabled(true);
                } else {
                    mSkipNext.setVisibility(View.INVISIBLE);
                }
                if (prevAvailable) {
                    mSkipPrevious.setVisibility(View.VISIBLE);
                    mSkipPrevious.setEnabled(true);
                } else {
                    mSkipPrevious.setVisibility(View.INVISIBLE);
                }
                break;
            case VideoCastController.NEXT_PREV_VISIBILITY_POLICY_ALWAYS:
                mSkipNext.setVisibility(View.VISIBLE);
                mSkipNext.setEnabled(true);
                mSkipPrevious.setVisibility(View.VISIBLE);
                mSkipPrevious.setEnabled(true);
                break;
            case VideoCastController.NEXT_PREV_VISIBILITY_POLICY_DISABLED:
                if (nextAvailable) {
                    mSkipNext.setVisibility(View.VISIBLE);
                    mSkipNext.setEnabled(true);
                } else {
                    mSkipNext.setVisibility(View.VISIBLE);
                    mSkipNext.setEnabled(false);
                }
                if (prevAvailable) {
                    mSkipPrevious.setVisibility(View.VISIBLE);
                    mSkipPrevious.setEnabled(true);
                } else {
                    mSkipPrevious.setVisibility(View.VISIBLE);
                    mSkipPrevious.setEnabled(false);
                }
                break;
            default:
                LOGE(TAG, "onQueueItemsUpdated(): Invalid NextPreviousPolicy has been set");
        }
    }

    @Override
    public void setPlaybackStatus(int state) {
        LOGD(TAG, "setPlaybackStatus(): state = " + state);
        switch (state) {
            case MediaControl.PLAYER_STATE_PLAYING:
                mLoading.setVisibility(View.INVISIBLE);
                mPlaybackControls.setVisibility(View.VISIBLE);
//                if (mStreamType == MediaInfo.STREAM_TYPE_LIVE) {
//                    mPlayPause.setImageDrawable(mStopDrawable);
//                } else {
                    mPlayPause.setImageDrawable(mPauseDrawable);
//                }

                mLine2.setText(getString(R.string.ccl_casting_to_device,
                        mCastManager.getDeviceName()));
                mControllers.setVisibility(View.VISIBLE);
                break;
            case MediaControl.PLAYER_STATE_PAUSED:
                mControllers.setVisibility(View.VISIBLE);
                mLoading.setVisibility(View.INVISIBLE);
                mPlaybackControls.setVisibility(View.VISIBLE);
                mPlayPause.setImageDrawable(mPlayDrawable);
                mLine2.setText(getString(R.string.ccl_casting_to_device,
                        mCastManager.getDeviceName()));
                break;
            case MediaControl.PLAYER_STATE_IDLE:
                mLoading.setVisibility(View.INVISIBLE);
                mPlayPause.setImageDrawable(mPlayDrawable);
                mPlaybackControls.setVisibility(View.VISIBLE);
                mLine2.setText(getString(R.string.ccl_casting_to_device,
                        mCastManager.getDeviceName()));
                break;
            case MediaControl.PLAYER_STATE_BUFFERING:
                mPlaybackControls.setVisibility(View.INVISIBLE);
                mLoading.setVisibility(View.VISIBLE);
                mLine2.setText(getString(R.string.ccl_loading));
                break;
            default:
        }
    }

    @Override
    public void updateSeekbar(int position, int duration) {
        mSeekbar.setProgress(position);
        mSeekbar.setMax(duration);
        mStart.setText(Utils.formatMillis(position));
        mEnd.setText(Utils.formatMillis(duration));
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setImage(Bitmap bitmap) {
        if (bitmap != null) {
            if (mPageView instanceof ImageView) {
                ((ImageView) mPageView).setImageBitmap(bitmap);
            } else {
                mPageView.setBackgroundDrawable(new BitmapDrawable(getResources(), bitmap));
            }
        }
    }

    @Override
    public void setTitle(String text) {
        mToolbar.setTitle(text);
    }

    @Override
    public void setSubTitle(String text) {
        mLine2.setText(text);
    }

    @Override
    public void setOnVideoCastControllerChangedListener(OnVideoCastControllerListener listener) {
        if (listener != null) {
            //mListener = listener;
        }
    }

    @Override
    public void setStreamType(int streamType) {
        this.mStreamType = streamType;
    }

    @Override
    public void updateControllersStatus(boolean enabled) {
        mControllers.setVisibility(enabled ? View.VISIBLE : View.INVISIBLE);
//        if (enabled) {
//            adjustControllersForLiveStream(mStreamType == MediaInfo.STREAM_TYPE_LIVE);
//        }
    }

    @Override
    public void closeActivity() {
        finish();
    }

    @Override // from VideoCastController
    public void setNextPreviousVisibilityPolicy(int policy) {
        mNextPreviousVisibilityPolicy = policy;
    }

}
