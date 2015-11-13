package com.connectsdk.route.provider;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaRouter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v7.media.MediaControlIntent;
import android.support.v7.media.MediaRouteDescriptor;
import android.support.v7.media.MediaRouteDiscoveryRequest;
import android.support.v7.media.MediaRouteProvider;
import android.support.v7.media.MediaRouteProviderDescriptor;
import android.util.Log;

import com.connectsdk.device.ConnectableDevice;
import com.connectsdk.device.ConnectableDeviceListener;
import com.connectsdk.discovery.DiscoveryManager;
import com.connectsdk.discovery.DiscoveryManagerListener;
import com.connectsdk.service.capability.listeners.ResponseListener;
import com.connectsdk.service.command.ServiceCommandError;
import com.connectsdk.service.sessions.WebAppSession;
import com.google.android.libraries.cast.companionlibrary.cast.BaseCastManager;
import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.CastException;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.NoConnectionException;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.TransientNetworkDisconnectionException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.android.libraries.cast.companionlibrary.utils.LogUtils.LOGE;

/**
 * Created by gomino on 4/7/2015.
 */
public class ConnectSDKMediaRouteProvider extends MediaRouteProvider{


    private static final String TAG = ConnectSDKMediaRouteProvider.class.getSimpleName();
    /**
     * A custom media control intent category for special requests that are
     * supported by this provider's routes.
     */
    public static final String CATEGORY_SAMPLE_ROUTE =
            "com.connectsdk.android.mediarouteprovider.CATEGORY_SAMPLE_ROUTE";

    /**
     * A custom media control intent action for special requests that are
     * supported by this provider's routes.
     * <p>
     * This particular request is designed to return a bundle of not very
     * interesting statistics for demonstration purposes.
     * </p>
     *
     * @see #DATA_PLAYBACK_COUNT
     */
    public static final String ACTION_GET_STATISTICS =
            "com.connectsdk.android.mediarouteprovider.ACTION_GET_STATISTICS";

    /**
     * {@link #ACTION_GET_STATISTICS} result data: Number of times the
     * playback action was invoked.
     */
    public static final String DATA_PLAYBACK_COUNT = "com.connectsdk.android.mediarouteprovider.EXTRA_PLAYBACK_COUNT";
    public static final String EXTRA_CONNECTABLE_DEVICE = "com.connectsdk.android.mediarouteprovider.EXTRA_CONNECTABLE_DEVICE";

    private static final ArrayList<IntentFilter> CONTROL_FILTERS_BASIC;
    private static final ArrayList<IntentFilter> CONTROL_FILTERS_QUEUING;
    private static final ArrayList<IntentFilter> CONTROL_FILTERS_SESSION;

    static {
        IntentFilter f1 = new IntentFilter();
        f1.addCategory(CATEGORY_SAMPLE_ROUTE);
        f1.addAction(ACTION_GET_STATISTICS);

        IntentFilter f2 = new IntentFilter();
        f2.addCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK);
        f2.addAction(MediaControlIntent.ACTION_PLAY);
        f2.addDataScheme("http");
        f2.addDataScheme("https");
        f2.addDataScheme("rtsp");
        f2.addDataScheme("file");
        addDataTypeUnchecked(f2, "video/*");

        IntentFilter f3 = new IntentFilter();
        f3.addCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK);
        f3.addAction(MediaControlIntent.ACTION_SEEK);
        f3.addAction(MediaControlIntent.ACTION_GET_STATUS);
        f3.addAction(MediaControlIntent.ACTION_PAUSE);
        f3.addAction(MediaControlIntent.ACTION_RESUME);
        f3.addAction(MediaControlIntent.ACTION_STOP);

        IntentFilter f4 = new IntentFilter();
        f4.addCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK);
        f4.addAction(MediaControlIntent.ACTION_ENQUEUE);
        f4.addDataScheme("http");
        f4.addDataScheme("https");
        f4.addDataScheme("rtsp");
        f4.addDataScheme("file");
        addDataTypeUnchecked(f4, "video/*");

        IntentFilter f5 = new IntentFilter();
        f5.addCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK);
        f5.addAction(MediaControlIntent.ACTION_REMOVE);

        IntentFilter f6 = new IntentFilter();
        f6.addCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK);
        f6.addAction(MediaControlIntent.ACTION_START_SESSION);
        f6.addAction(MediaControlIntent.ACTION_GET_SESSION_STATUS);
        f6.addAction(MediaControlIntent.ACTION_END_SESSION);

        CONTROL_FILTERS_BASIC = new ArrayList<IntentFilter>();
        CONTROL_FILTERS_BASIC.add(f1);
        CONTROL_FILTERS_BASIC.add(f2);
        CONTROL_FILTERS_BASIC.add(f3);

        CONTROL_FILTERS_QUEUING =
                new ArrayList<IntentFilter>(CONTROL_FILTERS_BASIC);
        CONTROL_FILTERS_QUEUING.add(f4);
        CONTROL_FILTERS_QUEUING.add(f5);

        CONTROL_FILTERS_SESSION =
                new ArrayList<IntentFilter>(CONTROL_FILTERS_QUEUING);
        CONTROL_FILTERS_SESSION.add(f6);
    }

    private static void addDataTypeUnchecked(IntentFilter filter, String type) {
        try {
            filter.addDataType(type);
        } catch (IntentFilter.MalformedMimeTypeException ex) {
            throw new RuntimeException(ex);
        }
    }

    protected static final int VOLUME_MAX = 10;
    private Map<String, ConnectableDevice> mRouteIdToDeviceMap;
    protected int mVolume = 5;
    private int mEnqueueCount;

    public ConnectSDKMediaRouteProvider(Context context) {
        super(context);

        Log.d(TAG, "");
        mRouteIdToDeviceMap = new HashMap<String, ConnectableDevice>();

        DiscoveryManager.getInstance().addListener(new DiscoveryManagerListener() {
            @Override
            public void onDeviceAdded(DiscoveryManager discoveryManager, ConnectableDevice connectableDevice) {
                Log.i(TAG, "deviceAdded:" + connectableDevice);
                //setDiscoveryRequest(null);
                onDiscoveryRequestChanged(null);
            }

            @Override
            public void onDeviceUpdated(DiscoveryManager discoveryManager, ConnectableDevice connectableDevice) {
                Log.i(TAG, "onDeviceUpdated:" + connectableDevice);
                //setDiscoveryRequest(null);
                onDiscoveryRequestChanged(null);
            }

            @Override
            public void onDeviceRemoved(DiscoveryManager discoveryManager, ConnectableDevice connectableDevice) {
                Log.i(TAG, "onDeviceRemoved:" + connectableDevice);
                //providerDescriptors.remove(connectableDevice);
                //setDiscoveryRequest(null);
                onDiscoveryRequestChanged(null);
            }

            @Override
            public void onDiscoveryFailed(DiscoveryManager discoveryManager, ServiceCommandError serviceCommandError) {
                Log.i(TAG, "onDiscoveryFailed:" + serviceCommandError);
                onDiscoveryRequestChanged(null);
            }
        });

        setCallback(new Callback() {
            @Override
            public void onDescriptorChanged(MediaRouteProvider provider, @Nullable MediaRouteProviderDescriptor descriptor) {
                super.onDescriptorChanged(provider, descriptor);
                Log.d(TAG, "onDescriptorChanged");
            }
        });

        //DiscoveryManager.getInstance().start();
    }

    @Override
    public void onDiscoveryRequestChanged(MediaRouteDiscoveryRequest request) {
        super.onDiscoveryRequestChanged(request);
        //Log.d(TAG, "onDiscoveryRequestChanged");
        Log.d(TAG, "onDiscoveryRequestChanged allDevices(" + DiscoveryManager.getInstance().getAllDevices().size()+") VS compatibleDevices(" + DiscoveryManager.getInstance().getCompatibleDevices().size()+")");
        MediaRouteProviderDescriptor.Builder providerDescriptorBuilder = new MediaRouteProviderDescriptor.Builder();
        for(ConnectableDevice device: DiscoveryManager.getInstance().getAllDevices().values()) {
            MediaRouteDescriptor routeDescriptor = getMediaRouteDescriptorForDevice(device);
            mRouteIdToDeviceMap.put(routeDescriptor.getId(), device);
            providerDescriptorBuilder.addRoute(routeDescriptor);
        }
        setDescriptor(providerDescriptorBuilder.build());
    }

    @Nullable
    @Override
    public RouteController onCreateRouteController(String routeId) {
        return new ConnectSDKRouteController(routeId);
    }

    public MediaRouteDescriptor getMediaRouteDescriptorForDevice(ConnectableDevice device) {
        Bundle bundle = new Bundle();
        //bundle.putString(EXTRA_CONNECTABLE_DEVICE, device.toJSONObject().toString());
        //bundle.putString(EXTRA_CONNECTABLE_DEVICE, new Gson().toJson(device));
        bundle.putString(ConnectableDevice.KEY_ID, device.getId());
//        try {
//            bundle.putByteArray(EXTRA_CONNECTABLE_DEVICE, SerializationUtils.object2Bytes(device));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        MediaRouteDescriptor routeDescriptor = new MediaRouteDescriptor.Builder(device.getId(), device.getFriendlyName())
                .setDescription(device.getConnectedServiceNames())
                .addControlFilters(CONTROL_FILTERS_SESSION)
                .setPlaybackStream(AudioManager.STREAM_MUSIC)
                .setPlaybackType(MediaRouter.RouteInfo.PLAYBACK_TYPE_REMOTE)
                .setVolumeHandling(MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE)
                //.setVolumeHandling((int)VideoCastManager.getInstance().getVolumeStep())
                .setVolumeMax(VOLUME_MAX)
                .setVolume(mVolume)
                .setExtras(bundle)
                .build();

        return routeDescriptor;
    }

    public ConnectableDevice getDeviceForRouteId(String routeId)
    {
        String[] uniqueId = routeId.split(":");
		String routeDescriptorId = uniqueId[uniqueId.length-1];
        return mRouteIdToDeviceMap.get(routeDescriptorId);
    }


    private final class ConnectSDKRouteController extends RouteController {
        private final String mRouteId;
        private final ConnectableDevice mDevice;
        private PendingIntent mSessionReceiver;

        public ConnectSDKRouteController(String routeId) {
            mRouteId = routeId;
            mDevice = getDeviceForRouteId(routeId);
            Log.d(TAG, mRouteId + ": Controller created");
        }

        @Override
        public void onRelease() {
            Log.d(TAG, mRouteId + ": Controller released");

        }

        @Override
        public void onSelect() {
            Log.d(TAG, mRouteId + ": Selected");
        }

        @Override
        public void onUnselect() {
            Log.d(TAG, mRouteId + ": Unselected");
        }

        @Override
        public void onSetVolume(int volume) {
            Log.d(TAG, mRouteId + ": Set volume to " + volume);
            setVolumeInternal(volume);
        }

        @Override
        public void onUpdateVolume(int delta) {
            Log.d(TAG, mRouteId + ": Update volume by " + delta);
            setVolumeInternal(mVolume + delta);
        }

        @Override
        public boolean onControlRequest(Intent intent, android.support.v7.media.MediaRouter.ControlRequestCallback callback) {
            Log.d(TAG, mRouteId + ": Received control request " + intent);
            String action = intent.getAction();
            if (intent.hasCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)) {
                boolean success = false;
                if (action.equals(MediaControlIntent.ACTION_PLAY)) {
                    success = handlePlay(intent, callback);
                } else if (action.equals(MediaControlIntent.ACTION_ENQUEUE)) {
                    success = handleEnqueue(intent, callback);
                } else if (action.equals(MediaControlIntent.ACTION_REMOVE)) {
                    success = handleRemove(intent, callback);
                } else if (action.equals(MediaControlIntent.ACTION_SEEK)) {
                    success = handleSeek(intent, callback);
                } else if (action.equals(MediaControlIntent.ACTION_GET_STATUS)) {
                    success = handleGetStatus(intent, callback);
                } else if (action.equals(MediaControlIntent.ACTION_PAUSE)) {
                    success = handlePause(intent, callback);
                } else if (action.equals(MediaControlIntent.ACTION_RESUME)) {
                    success = handleResume(intent, callback);
                } else if (action.equals(MediaControlIntent.ACTION_STOP)) {
                    success = handleStop(intent, callback);
                } else if (action.equals(MediaControlIntent.ACTION_START_SESSION)) {
                    success = handleStartSession(intent, callback);
                } else if (action.equals(MediaControlIntent.ACTION_GET_SESSION_STATUS)) {
                    success = handleGetSessionStatus(intent, callback);
                } else if (action.equals(MediaControlIntent.ACTION_END_SESSION)) {
                    success = handleEndSession(intent, callback);
                }
                //Log.d(TAG, mSessionManager.toString());
                return success;
            }

            if (action.equals(ACTION_GET_STATISTICS)
                    && intent.hasCategory(CATEGORY_SAMPLE_ROUTE)) {
                Bundle data = new Bundle();
                data.putInt(DATA_PLAYBACK_COUNT, mEnqueueCount);
                if (callback != null) {
                    callback.onResult(data);
                }
                return true;
            }
            return false;
        }

        private void setVolumeInternal(int volume) {
            if (volume >= 0 && volume <= VOLUME_MAX) {
                mVolume = volume;
                Log.d(TAG, mRouteId + ": New volume is " + mVolume);
                //AudioManager audioManager = (AudioManager)getContext().getSystemService(Context.AUDIO_SERVICE);
                //audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);

                try {
                    float volumeToAdjust = (float) mVolume/10;
                    VideoCastManager.getInstance().setVolume(volumeToAdjust);
                } catch (CastException | TransientNetworkDisconnectionException | NoConnectionException e) {
                    LOGE(TAG, "Failed to change volume", e);
                }
            }
        }

        private boolean handlePlay(Intent intent, android.support.v7.media.MediaRouter.ControlRequestCallback callback) {
            String sid = intent.getStringExtra(MediaControlIntent.EXTRA_SESSION_ID);
            if (sid != null /* && !sid.equals(mSessionManager.getSessionId())*/ ) {
                Log.d(TAG, "handlePlay fails because of bad sid="+sid);
                return false;
            }
            /*
            if (mSessionManager.hasSession()) {
                mSessionManager.stop();
            }
            */
            return handleEnqueue(intent, callback);
        }

        private boolean handleEnqueue(Intent intent, android.support.v7.media.MediaRouter.ControlRequestCallback callback) {
            String sid = intent.getStringExtra(MediaControlIntent.EXTRA_SESSION_ID);
            if (sid != null /*&& !sid.equals(mSessionManager.getSessionId())*/) {
                Log.d(TAG, "handleEnqueue fails because of bad sid="+sid);
                return false;
            }

            Uri uri = intent.getData();
            if (uri == null) {
                Log.d(TAG, "handleEnqueue fails because of bad uri="+uri);
                return false;
            }

            boolean enqueue = intent.getAction().equals(MediaControlIntent.ACTION_ENQUEUE);
            String mime = intent.getType();
            long pos = intent.getLongExtra(MediaControlIntent.EXTRA_ITEM_CONTENT_POSITION, 0);
            Bundle metadata = intent.getBundleExtra(MediaControlIntent.EXTRA_ITEM_METADATA);
            Bundle headers = intent.getBundleExtra(MediaControlIntent.EXTRA_ITEM_HTTP_HEADERS);
            PendingIntent receiver = (PendingIntent)intent.getParcelableExtra(
                    MediaControlIntent.EXTRA_ITEM_STATUS_UPDATE_RECEIVER);

            Log.d(TAG, mRouteId + ": Received " + (enqueue?"enqueue":"play") + " request"
                    + ", uri=" + uri
                    + ", mime=" + mime
                    + ", sid=" + sid
                    + ", pos=" + pos
                    + ", metadata=" + metadata
                    + ", headers=" + headers
                    + ", receiver=" + receiver);
            //PlaylistItem item = mSessionManager.add(uri, mime, receiver);
            if (callback != null) {
//                if (item != null) {
//                    Bundle result = new Bundle();
//                    result.putString(MediaControlIntent.EXTRA_SESSION_ID, item.getSessionId());
//                    result.putString(MediaControlIntent.EXTRA_ITEM_ID, item.getItemId());
//                    result.putBundle(MediaControlIntent.EXTRA_ITEM_STATUS, item.getStatus().asBundle());
//                    callback.onResult(result);
//                } else {
//                    callback.onError("Failed to open " + uri.toString(), null);
//                }
            }
            mEnqueueCount +=1;
            return true;
        }

        private boolean handleRemove(Intent intent, android.support.v7.media.MediaRouter.ControlRequestCallback callback) {
            String sid = intent.getStringExtra(MediaControlIntent.EXTRA_SESSION_ID);
//            if (sid == null || !sid.equals(mSessionManager.getSessionId())) {
//                return false;
//            }
//
//            String iid = intent.getStringExtra(MediaControlIntent.EXTRA_ITEM_ID);
//            PlaylistItem item = mSessionManager.remove(iid);
//            if (callback != null) {
//                if (item != null) {
//                    Bundle result = new Bundle();
//                    result.putBundle(MediaControlIntent.EXTRA_ITEM_STATUS,
//                            item.getStatus().asBundle());
//                    callback.onResult(result);
//                } else {
//                    callback.onError("Failed to remove" +
//                            ", sid=" + sid + ", iid=" + iid, null);
//                }
//            }
//            return (item != null);
            return true;
        }

        private boolean handleSeek(Intent intent, android.support.v7.media.MediaRouter.ControlRequestCallback callback) {
            String sid = intent.getStringExtra(MediaControlIntent.EXTRA_SESSION_ID);
//            if (sid == null || !sid.equals(mSessionManager.getSessionId())) {
//                return false;
//            }
//
//            String iid = intent.getStringExtra(MediaControlIntent.EXTRA_ITEM_ID);
//            long pos = intent.getLongExtra(MediaControlIntent.EXTRA_ITEM_CONTENT_POSITION, 0);
//            Log.d(TAG, mRouteId + ": Received seek request, pos=" + pos);
//            PlaylistItem item = mSessionManager.seek(iid, pos);
//            if (callback != null) {
//                if (item != null) {
//                    Bundle result = new Bundle();
//                    result.putBundle(MediaControlIntent.EXTRA_ITEM_STATUS,
//                            item.getStatus().asBundle());
//                    callback.onResult(result);
//                } else {
//                    callback.onError("Failed to seek" +
//                            ", sid=" + sid + ", iid=" + iid + ", pos=" + pos, null);
//                }
//            }
//            return (item != null);

            return true;
        }

        private boolean handleGetStatus(Intent intent, android.support.v7.media.MediaRouter.ControlRequestCallback callback) {
            String sid = intent.getStringExtra(MediaControlIntent.EXTRA_SESSION_ID);
            String iid = intent.getStringExtra(MediaControlIntent.EXTRA_ITEM_ID);
            Log.d(TAG, mRouteId + ": Received getStatus request, sid=" + sid + ", iid=" + iid);
//            PlaylistItem item = mSessionManager.getStatus(iid);
//            if (callback != null) {
//                if (item != null) {
//                    Bundle result = new Bundle();
//                    result.putBundle(MediaControlIntent.EXTRA_ITEM_STATUS,
//                            item.getStatus().asBundle());
//                    callback.onResult(result);
//                } else {
//                    callback.onError("Failed to get status" +
//                            ", sid=" + sid + ", iid=" + iid, null);
//                }
//            }
//            return (item != null);
            return true;
        }

        private boolean handlePause(Intent intent, android.support.v7.media.MediaRouter.ControlRequestCallback callback) {
            String sid = intent.getStringExtra(MediaControlIntent.EXTRA_SESSION_ID);
//            boolean success = (sid != null) && sid.equals(mSessionManager.getSessionId());
//            mSessionManager.pause();
//            if (callback != null) {
//                if (success) {
//                    callback.onResult(new Bundle());
//                    handleSessionStatusChange(sid);
//                } else {
//                    callback.onError("Failed to pause, sid=" + sid, null);
//                }
//            }
//            return success;
            return true;
        }

        private boolean handleResume(Intent intent, android.support.v7.media.MediaRouter.ControlRequestCallback callback) {
            String sid = intent.getStringExtra(MediaControlIntent.EXTRA_SESSION_ID);
//            boolean success = (sid != null) && sid.equals(mSessionManager.getSessionId());
//            mSessionManager.resume();
//            if (callback != null) {
//                if (success) {
//                    callback.onResult(new Bundle());
//                    handleSessionStatusChange(sid);
//                } else {
//                    callback.onError("Failed to resume, sid=" + sid, null);
//                }
//            }
//            return success;
            return true;
        }

        private boolean handleStop(Intent intent, android.support.v7.media.MediaRouter.ControlRequestCallback callback) {
            String sid = intent.getStringExtra(MediaControlIntent.EXTRA_SESSION_ID);
//            boolean success = (sid != null) && sid.equals(mSessionManager.getSessionId());
//            mSessionManager.stop();
//            if (callback != null) {
//                if (success) {
//                    callback.onResult(new Bundle());
//                    handleSessionStatusChange(sid);
//                } else {
//                    callback.onError("Failed to stop, sid=" + sid, null);
//                }
//            }
//            return success;
            return true;
        }

        private boolean handleStartSession(Intent intent, android.support.v7.media.MediaRouter.ControlRequestCallback callback) {
//            String sid = mSessionManager.startSession();
//            Log.d(TAG, "StartSession returns sessionId "+sid);
//            if (callback != null) {
//                if (sid != null) {
//                    Bundle result = new Bundle();
//                    result.putString(MediaControlIntent.EXTRA_SESSION_ID, sid);
//                    result.putBundle(MediaControlIntent.EXTRA_SESSION_STATUS,
//                            mSessionManager.getSessionStatus(sid).asBundle());
//                    callback.onResult(result);
//                    mSessionReceiver = (PendingIntent)intent.getParcelableExtra(
//                            MediaControlIntent.EXTRA_SESSION_STATUS_UPDATE_RECEIVER);
//                    handleSessionStatusChange(sid);
//                } else {
//                    callback.onError("Failed to start session.", null);
//                }
//            }
//            return (sid != null);
            return true;
        }

        private boolean handleGetSessionStatus(Intent intent, android.support.v7.media.MediaRouter.ControlRequestCallback callback) {
            String sid = intent.getStringExtra(MediaControlIntent.EXTRA_SESSION_ID);

//            MediaSessionStatus sessionStatus = mSessionManager.getSessionStatus(sid);
//            if (callback != null) {
//                if (sessionStatus != null) {
//                    Bundle result = new Bundle();
//                    result.putBundle(MediaControlIntent.EXTRA_SESSION_STATUS,
//                            mSessionManager.getSessionStatus(sid).asBundle());
//                    callback.onResult(result);
//                } else {
//                    callback.onError("Failed to get session status, sid=" + sid, null);
//                }
//            }
//            return (sessionStatus != null);
            return true;
        }

        private boolean handleEndSession(Intent intent, android.support.v7.media.MediaRouter.ControlRequestCallback callback) {
            String sid = intent.getStringExtra(MediaControlIntent.EXTRA_SESSION_ID);
//            boolean success = (sid != null) && sid.equals(mSessionManager.getSessionId())
//                    && mSessionManager.endSession();
//            if (callback != null) {
//                if (success) {
//                    Bundle result = new Bundle();
//                    MediaSessionStatus sessionStatus = new MediaSessionStatus.Builder(
//                            MediaSessionStatus.SESSION_STATE_ENDED).build();
//                    result.putBundle(MediaControlIntent.EXTRA_SESSION_STATUS, sessionStatus.asBundle());
//                    callback.onResult(result);
//                    handleSessionStatusChange(sid);
//                    mSessionReceiver = null;
//                } else {
//                    callback.onError("Failed to end session, sid=" + sid, null);
//                }
//            }
//            return success;
            return true;
        }

//        private void handleStatusChange(PlaylistItem item) {
//            if (item == null) {
//                item = mSessionManager.getCurrentItem();
//            }
//            if (item != null) {
//                PendingIntent receiver = item.getUpdateReceiver();
//                if (receiver != null) {
//                    Intent intent = new Intent();
//                    intent.putExtra(MediaControlIntent.EXTRA_SESSION_ID, item.getSessionId());
//                    intent.putExtra(MediaControlIntent.EXTRA_ITEM_ID, item.getItemId());
//                    intent.putExtra(MediaControlIntent.EXTRA_ITEM_STATUS,
//                            item.getStatus().asBundle());
//                    try {
//                        receiver.send(getContext(), 0, intent);
//                        Log.d(TAG, mRouteId + ": Sending status update from provider");
//                    } catch (PendingIntent.CanceledException e) {
//                        Log.d(TAG, mRouteId + ": Failed to send status update!");
//                    }
//                }
//            }
//        }

        private void handleSessionStatusChange(String sid) {
            if (mSessionReceiver != null) {
                Intent intent = new Intent();
                intent.putExtra(MediaControlIntent.EXTRA_SESSION_ID, sid);
                //intent.putExtra(MediaControlIntent.EXTRA_SESSION_STATUS, mSessionManager.getSessionStatus(sid).asBundle());
                try {
                    mSessionReceiver.send(getContext(), 0, intent);
                    Log.d(TAG, mRouteId + ": Sending session status update from provider");
                } catch (PendingIntent.CanceledException e) {
                    Log.d(TAG, mRouteId + ": Failed to send session status update!");
                }
            }
        }
    }
}
