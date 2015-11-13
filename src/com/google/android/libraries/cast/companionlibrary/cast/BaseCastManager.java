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

//import com.google.android.gms.cast.ApplicationMetadata;
//import com.google.android.gms.cast.Cast;
//import com.google.android.gms.cast.Cast.ApplicationConnectionResult;
//import com.google.android.gms.cast.CastDevice;
import com.connectsdk.discovery.DiscoveryManagerListener;
import com.connectsdk.discovery.DiscoveryProvider;
import com.connectsdk.route.provider.ConnectSDKMediaRouteProvider;
//import com.google.android.gms.cast.CastMediaControlIntent;
//import com.google.android.gms.common.ConnectionResult;
//import com.google.android.gms.common.api.GoogleApiClient;
//import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
//import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
//import com.google.android.gms.common.api.ResultCallback;
//import com.google.android.gms.common.api.Status;
import com.connectsdk.service.CompanionService;
import com.google.android.libraries.cast.companionlibrary.R;

import com.connectsdk.device.ConnectableDevice;
import com.connectsdk.device.ConnectableDeviceListener;
import com.connectsdk.discovery.DiscoveryManager;
import com.connectsdk.service.DeviceService;
import com.connectsdk.service.capability.VolumeControl;
import com.connectsdk.service.capability.WebAppLauncher;
import com.connectsdk.service.capability.listeners.ResponseListener;
import com.connectsdk.service.command.ServiceCommandError;
import com.connectsdk.service.sessions.WebAppSession;

import com.google.android.libraries.cast.companionlibrary.cast.callbacks.BaseCastConsumer;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.CastException;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.NoConnectionException;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.OnFailedListener;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.TransientNetworkDisconnectionException;
import com.google.android.libraries.cast.companionlibrary.cast.reconnection.ReconnectionService;
import com.google.android.libraries.cast.companionlibrary.utils.LogUtils;
import com.google.android.libraries.cast.companionlibrary.utils.PreferenceAccessor;
import com.google.android.libraries.cast.companionlibrary.utils.Utils;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.RemoteControlClient;
import android.net.nsd.NsdManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.app.MediaRouteButton;
import android.support.v7.app.MediaRouteDialogFactory;
import android.support.v7.media.MediaRouteProvider;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.support.v7.media.MediaRouter.RouteInfo;
import android.view.Menu;
import android.view.MenuItem;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;

/**
 * An abstract class that manages connectivity to a cast device. Subclasses are expected to extend
 * the functionality of this class based on their purpose.
 */
public abstract class BaseCastManager implements /*ConnectionCallbacks, OnConnectionFailedListener,*/ DiscoveryManagerListener, ConnectableDeviceListener, OnFailedListener {

    private static final String TAG = LogUtils.makeLogTag(BaseCastManager.class);

    public static final int RECONNECTION_STATUS_STARTED = 1;
    public static final int RECONNECTION_STATUS_IN_PROGRESS = 2;
    public static final int RECONNECTION_STATUS_FINALIZED = 3;
    public static final int RECONNECTION_STATUS_INACTIVE = 4;

    public static final int FEATURE_DEBUGGING = 1;
    public static final int FEATURE_LOCKSCREEN = 1 << 1;
    public static final int FEATURE_NOTIFICATION = 1 << 2;
    public static final int FEATURE_WIFI_RECONNECT = 1 << 3;
    public static final int FEATURE_CAPTIONS_PREFERENCE = 1 << 4;

    public static final String PREFS_KEY_SESSION_ID = "session-id";
    public static final String PREFS_KEY_SSID = "ssid";
    public static final String PREFS_KEY_MEDIA_END = "media-end";
    public static final String PREFS_KEY_APPLICATION_ID = "application-id";
    public static final String PREFS_KEY_CAST_ACTIVITY_NAME = "cast-activity-name";
    public static final String PREFS_KEY_CAST_CUSTOM_DATA_NAMESPACE = "cast-custom-data-namespace";
    public static final String PREFS_KEY_ROUTE_ID = "route-id";

    public static final int CLEAR_ALL = 0;
    public static final int CLEAR_ROUTE = 1;
    public static final int CLEAR_WIFI = 1 << 1;
    public static final int CLEAR_SESSION = 1 << 2;
    public static final int CLEAR_MEDIA_END = 1 << 3;

    public static final int NO_STATUS_CODE = -1;
    private static final int SESSION_RECOVERY_TIMEOUT_S = 10;
    private static final int WHAT_UI_VISIBLE = 0;
    private static final int WHAT_UI_HIDDEN = 1;
    private static final int UI_VISIBILITY_DELAY_MS = 300;

    private static String sCclVersion;

    protected Context mContext;
    protected MediaRouter mMediaRouter;
    protected MediaRouteSelector mMediaRouteSelector;
    protected CastMediaRouterCallback mMediaRouterCallback;
    protected ConnectableDevice mSelectedCastDevice;
    protected String mDeviceName;
    protected PreferenceAccessor mPreferenceAccessor;

    private final Set<BaseCastConsumer> mBaseCastConsumers = new CopyOnWriteArraySet<>();
    private boolean mDestroyOnDisconnect = false;
    protected String mApplicationId;
    protected int mReconnectionStatus = RECONNECTION_STATUS_INACTIVE;
    protected int mVisibilityCounter;
    protected boolean mUiVisible;
    //protected GoogleApiClient mApiClient;
    protected AsyncTask<Void, Integer, Boolean> mReconnectionTask;
    protected int mCapabilities;
    protected boolean mConnectionSuspended;
    protected boolean isConnecting;
    protected String mSessionId;
    private Handler mUiVisibilityHandler;
    private RouteInfo mRouteInfo;
    //private ConnectSDKMediaRouteProvider mConnectSDKRouteProvider;
    private MediaRouteProvider mMediaRouteProvider;


    protected BaseCastManager() {
    }

    /**
     * Since application lifecycle callbacks are managed by subclasses, this abstract method needs
     * to be implemented by each subclass independently.
     *
     * @param device The Cast receiver device returned from the MediaRouteProvider. Should not be
     * {@code null}.
     */
//    protected abstract Cast.CastOptions.Builder getCastOptionBuilder(CastDevice device);

    /**
     * Subclasses can decide how the Cast Controller Dialog should be built. If this returns
     * <code>null</code>, the default dialog will be shown.
     */
    protected abstract MediaRouteDialogFactory getMediaRouteDialogFactory();

    /**
     * Subclasses should implement this to react appropriately to the successful launch of their
     * application. This is called when the application is successfully launched.
     */
    //protected abstract void onApplicationConnected(ApplicationMetadata applicationMetadata, String applicationStatus, String sessionId, boolean wasLaunched);
    protected abstract void onApplicationConnected(WebAppSession webAppSession, WebAppSession.WebAppStatus status);

    /**
     * Called when the launch of application has failed. Subclasses need to handle this by doing
     * appropriate clean up.
     */
    protected abstract void onApplicationConnectionFailed(int statusCode);

    /**
     * Called when the attempt to stop application has failed.
     */
    protected abstract void onApplicationStopFailed(int statusCode);

    /**
     * Called when a Cast device is unselected (i.e. disconnected). Most of the logic is handled by
     * the {@link BaseCastManager} but each subclass may have some additional logic that can be
     * done, e.g. detaching data or media channels that they may have set up.
     */
    protected void onDeviceUnselected() {
        // no-op implementation
    }

    protected BaseCastManager(Context context, String applicationId) {
        sCclVersion = context.getString(R.string.ccl_version);
        LOGD(TAG, "BaseCastManager is instantiated\nVersion: " + sCclVersion + "\nApplication ID: " + applicationId);
        mContext = context.getApplicationContext();
        mPreferenceAccessor = new PreferenceAccessor(mContext);
        mUiVisibilityHandler = new Handler(new UpdateUiVisibilityHandlerCallback());
        mApplicationId = applicationId;
        mPreferenceAccessor.saveStringToPreference(PREFS_KEY_APPLICATION_ID, applicationId);

        mMediaRouter = MediaRouter.getInstance(mContext);

//        mMediaRouteProvider = new ConnectSDKMediaRouteProvider(context);
//        mMediaRouter.addProvider(mMediaRouteProvider);
//        mMediaRouteSelector = new MediaRouteSelector.Builder().addControlCategory(ConnectSDKMediaRouteProvider.CATEGORY_SAMPLE_ROUTE).build();
//      //mMediaRouteSelector = new MediaRouteSelector.Builder().addControlCategory(CastMediaControlIntent.categoryForCast(mApplicationId)).build();


        mMediaRouterCallback = new CastMediaRouterCallback(this);
//        mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
//        setRouteProvider(new ConnectSDKMediaRouteProvider(context), ConnectSDKMediaRouteProvider.CATEGORY_SAMPLE_ROUTE);
    }

    public MediaRouteProvider getRouteProvider(){
        return mMediaRouteProvider;
    }

    public void setRouteProvider(MediaRouteProvider provider, String category){
        mMediaRouteProvider = provider;
        //Check for double is already made internally by addProvider and addCallback
        mMediaRouter.addProvider(mMediaRouteProvider);
        mMediaRouteSelector = new MediaRouteSelector.Builder().addControlCategory(category).build();
        mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
    }

    public ConnectableDevice getDeviceForRouteId(RouteInfo routeInfo) {
        ConnectableDevice device = null;

        if(routeInfo!=null) {

            //String json = routeinfo.getExtras().getString(ConnectSDKMediaRouteProviderDeprecated.EXTRA_CONNECTABLE_DEVICE);
            //JSONObject jsonObject = new JSONObject(json);
            //final ConnectableDevice device = new ConnectableDevice(jsonObject);
			//JSONObject jsonServices = jsonObject.getJSONObject(ConnectableDevice.KEY_SERVICES);
			//Iterator<?> keys = jsonServices.keys();
			//while( keys.hasNext()) {
			//	String key = (String)keys.next();
			//	if ( jsonServices.get(key) instanceof JSONObject ) {
			//		JSONObject serviceObject = (JSONObject)jsonServices.get(key);
			//		DeviceService service = DeviceService.getService(serviceObject);
			//		mSelectedDevice.addService(service);
			//	}
			//}

			//mSelectedDevice = (ConnectableDevice) SerializationUtils.bytes2Object(bundle.getByteArray(ConnectSDKMediaRouteProviderDeprecated.EXTRA_CONNECTABLE_DEVICE));

            //ConnectableDevice device = ConnectableDevice.getFromBundle(routeInfo.getExtras());

            //String deviceId = routeInfo.getExtras().getString(ConnectableDevice.KEY_ID);
            //ConnectableDevice device = DiscoveryManager.getInstance().getConnectableDeviceStore().getDevice(deviceId);

            if (mMediaRouteProvider instanceof ConnectSDKMediaRouteProvider) {
                device = ((ConnectSDKMediaRouteProvider) mMediaRouteProvider).getDeviceForRouteId(routeInfo.getId());
            }
        }

        return device;
    }

    /**
     * Called when a {@link ConnectableDevice} is extracted from the {@link RouteInfo}. This is where all
     * the fun starts!
     */
    public final void onDeviceSelected(ConnectableDevice device) {
        if (device == null) {
            if(mSelectedCastDevice!=null) {
                for (BaseCastConsumer consumer : mBaseCastConsumers) {
                    consumer.onDeviceUnselected(mSelectedCastDevice);
                }
            }
            disconnectDevice(mDestroyOnDisconnect, true, false);
        } else {
            setDevice(device);
        }

        for (BaseCastConsumer consumer : mBaseCastConsumers) {
            consumer.onDeviceSelected(device);
        }

    }

    /**
     * This is called from
     * {@link com.google.android.libraries.cast.companionlibrary.cast.CastMediaRouterCallback} to
     * signal the change in presence of cast devices on network.
     *
     * @param castDevicePresent Indicates where a cast device is present, <code>true</code>, or not,
     * <code>false</code>.
     */
    public final void onCastAvailabilityChanged(boolean castDevicePresent) {
        for (BaseCastConsumer consumer : mBaseCastConsumers) {
            consumer.onCastAvailabilityChanged(castDevicePresent);
        }
    }

    /**
     * Disconnects from the connected device.
     *
     * @param stopAppOnExit If {@code true}, the application running on the cast device will be
     * stopped when disconnected.
     * @param clearPersistedConnectionData If {@code true}, the persisted connection information
     * will be cleared as part of this call.
     * @param setDefaultRoute If {@code true}, after disconnection, the selected route will be set
     * to the Default Route.
     */
    public final void disconnectDevice(boolean stopAppOnExit, boolean clearPersistedConnectionData, boolean setDefaultRoute) {
        LOGD(TAG, "disconnectDevice(" + stopAppOnExit + ","+ clearPersistedConnectionData + "," + setDefaultRoute + ")");


        try {
            if (stopAppOnExit) {
                LOGD(TAG, "Calling stopApplication");
                stopApplication();
            }
        } catch (NoConnectionException | TransientNetworkDisconnectionException e) {
            LOGE(TAG, "Failed to stop the application before disconnecting route", e);
        }

        if (mSelectedCastDevice != null) {
            mSelectedCastDevice.disconnect();
            mSelectedCastDevice.removeListener(this);
            mSelectedCastDevice = null;
        }

        mDeviceName = null;
        LOGD(TAG, "mConnectionSuspended: " + mConnectionSuspended);
        if (!mConnectionSuspended && clearPersistedConnectionData) {
            clearPersistedConnectionInfo(CLEAR_ALL);
            stopReconnectionService();
        }

        onDisconnected(stopAppOnExit, clearPersistedConnectionData, setDefaultRoute);
        onDeviceUnselected();
//        if (mApiClient != null) {
//            // the following conditional clause is to get around a bug in play services
//            if (mApiClient.isConnected()) {
//                LOGD(TAG, "Trying to disconnect");
//                mApiClient.disconnect();
//            }
            if ((mMediaRouter != null) && setDefaultRoute) {
                LOGD(TAG, "disconnectDevice(): Setting route to default");
                mMediaRouter.selectRoute(mMediaRouter.getDefaultRoute());
            }
//            mApiClient = null;
//        }
        mSessionId = null;
    }

    /**
     * Returns {@code true} if and only if the selected cast device is on the local network.
     *
     * @throws CastException if no cast device has been selected.
     */
    public final boolean isDeviceOnLocalNetwork() throws CastException {
        if (mSelectedCastDevice == null) {
            throw new CastException("No cast device has yet been selected");
        }
        return mSelectedCastDevice.isConnectable();
    }

    public ConnectableDevice getSelectedDevice(){
        return mSelectedCastDevice;
    }

    private void setDevice(ConnectableDevice device) {
        mSelectedCastDevice = device;
        mDeviceName = mSelectedCastDevice.getFriendlyName();

//        if (mApiClient == null) {
//            LOGD(TAG, "acquiring a connection to Google Play services for " + mSelectedCastDevice);
//            Cast.CastOptions.Builder apiOptionsBuilder = getCastOptionBuilder(mSelectedCastDevice);
//            mApiClient = new GoogleApiClient.Builder(mContext)
//                    .addApi(Cast.API, apiOptionsBuilder.build())
//                    .addConnectionCallbacks(this)
//                    .addOnConnectionFailedListener(this)
//                    .build();
//            mApiClient.connect();
//        } else if (!mApiClient.isConnected() && !mApiClient.isConnecting()) {
//            mApiClient.connect();
//        }

        mSelectedCastDevice.addListener(this);
        isConnecting = true;
        mSelectedCastDevice.connect();
    }

    private String getApplicationId(){
        String appId = mApplicationId;
        if(mSelectedCastDevice!=null){
            DeviceService service = mSelectedCastDevice.getServiceByName(mSelectedCastDevice.getConnectedServiceNames());
            if(service instanceof CompanionService) {
                appId = ((CompanionService)service).getApplicationId();
            }
        }
        return appId;
    }

    /**
     * Called as soon as a non-default {@link RouteInfo} is discovered. The main usage for this is
     * to provide a hint to clients that the cast button is going to become visible/available soon.
     * A client, for example, can use this to show a quick help screen to educate the user on the
     * cast concept and the usage of the cast button.
     */
    public final void onCastDeviceDetected(RouteInfo info) {
        for (BaseCastConsumer consumer : mBaseCastConsumers) {
            consumer.onCastDeviceDetected(info);
        }
    }

    /**
     * Adds and wires up the Media Router cast button. It returns a reference to the Media Router
     * menu item if the caller needs such reference. It is assumed that the enclosing
     * {@link android.app.Activity} inherits (directly or indirectly) from
     * {@link android.support.v7.app.ActionBarActivity}.
     *
     * @param menu Menu reference
     * @param menuResourceId The resource id of the cast button in the xml menu descriptor file
     */
    public final MenuItem addMediaRouterButton(Menu menu, int menuResourceId) {
        MenuItem mediaRouteMenuItem = menu.findItem(menuResourceId);
        MediaRouteActionProvider mediaRouteActionProvider = (MediaRouteActionProvider) MenuItemCompat.getActionProvider(mediaRouteMenuItem);
        mediaRouteActionProvider.setRouteSelector(mMediaRouteSelector);
        if (getMediaRouteDialogFactory() != null) {
            mediaRouteActionProvider.setDialogFactory(getMediaRouteDialogFactory());
        }
        return mediaRouteMenuItem;
    }

    /**
     * Adds and wires up the {@link android.support.v7.app.MediaRouteButton} instance that is passed
     * as an argument. This requires that
     * <ul>
     * <li>The enclosing {@link android.app.Activity} inherits (directly or indirectly) from
     * {@link android.support.v4.app.FragmentActivity}</li>
     * <li>User adds the {@link android.support.v7.app.MediaRouteButton} to the layout and passes a
     * reference to that instance to this method</li>
     * <li>User is in charge of controlling the visibility of this button. However, this library
     * makes it easier to do so: use the callback <code>onCastAvailabilityChanged(boolean)</code>
     * to change the visibility of the button in your client. For example, extend
     * {@link com.google.android.libraries.cast.companionlibrary.cast.callbacks.VideoCastConsumerImpl}
     * and override that method:
     *
     * <pre>
       public void onCastAvailabilityChanged(boolean castPresent) {
           mMediaRouteButton.setVisibility(castPresent ? View.VISIBLE : View.INVISIBLE);
       }
     * </pre>
     * </li>
     * </ul>
     */
    public final void addMediaRouterButton(MediaRouteButton button) {
        button.setRouteSelector(mMediaRouteSelector);
        if (getMediaRouteDialogFactory() != null) {
            button.setDialogFactory(getMediaRouteDialogFactory());
        }
    }

    /**
     * Calling this method signals the library that an activity page is made visible. In common
     * cases, this should be called in the "onResume()" method of each activity of the application.
     * The library keeps a counter and when at least one page of the application becomes visible,
     * the {@link #onUiVisibilityChanged(boolean)} method is called.
     */
    public final synchronized void incrementUiCounter() {
        mVisibilityCounter++;
        if (!mUiVisible) {
            mUiVisible = true;
            mUiVisibilityHandler.removeMessages(WHAT_UI_HIDDEN);
            mUiVisibilityHandler.sendEmptyMessageDelayed(WHAT_UI_VISIBLE, UI_VISIBILITY_DELAY_MS);
        }
        if (mVisibilityCounter == 0) {
            LOGD(TAG, "UI is no longer visible");
        } else {
            LOGD(TAG, "UI is visible");
        }
    }

    /**
     * Calling this method signals the library that an activity page is made invisible. In common
     * cases, this should be called in the "onPause()" method of each activity of the application.
     * The library keeps a counter and when all pages of the application become invisible, the
     * {@link #onUiVisibilityChanged(boolean)} method is called.
     */
    public final synchronized void decrementUiCounter() {
        if (--mVisibilityCounter == 0) {
            LOGD(TAG, "UI is no longer visible");
            if (mUiVisible) {
                mUiVisible = false;
                mUiVisibilityHandler.removeMessages(WHAT_UI_VISIBLE);
                mUiVisibilityHandler.sendEmptyMessageDelayed(WHAT_UI_HIDDEN,
                        UI_VISIBILITY_DELAY_MS);
            }
        } else {
            LOGD(TAG, "UI is visible");
        }
    }

    /**
     * This is called when UI visibility of the client has changed
     *
     * @param visible The updated visibility status
     */
    protected void onUiVisibilityChanged(boolean visible) {
        if (visible) {
            if (mMediaRouter != null && mMediaRouterCallback != null) {
                LOGD(TAG, "onUiVisibilityChanged() addCallback called");
                startCastDiscovery();
            }
        } else {
            if (mMediaRouter != null) {
                LOGD(TAG, "onUiVisibilityChanged() removeCallback called");
                stopCastDiscovery();
            }
        }
        for (BaseCastConsumer consumer : mBaseCastConsumers) {
            consumer.onUiVisibilityChanged(visible);
        }
    }

    /**
     * Starts the discovery of cast devices by registering a {@link android.support.v7.media
     * .MediaRouter.Callback}
     */
    public final void startCastDiscovery() {
        LOGD(TAG, "startCastDiscovery() mMediaRouter.addCallback called");
        mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
        DiscoveryManager.getInstance().removeListener(this);
        DiscoveryManager.getInstance().addListener(this);
        DiscoveryManager.getInstance().start();
    }

    /**
     * Stops the process of cast discovery by removing the registered
     * {@link android.support.v7.media.MediaRouter.Callback}
     */
    public final void stopCastDiscovery() {
        LOGD(TAG, "stopCastDiscovery() mMediaRouter.removeCallback called");
        mMediaRouter.removeCallback(mMediaRouterCallback);
        DiscoveryManager.getInstance().removeListener(this);
        DiscoveryManager.getInstance().stop();
    }

    /**
     * A utility method to validate that the appropriate version of the Google Play Services is
     * available on the device. If not, it will open a dialog to address the issue. The dialog
     * displays a localized message about the error and upon user confirmation (by tapping on
     * dialog) will direct them to the Play Store if Google Play services is out of date or missing,
     * or to system settings if Google Play services is disabled on the device.
     */
    public static boolean checkGooglePlayServices(final Activity activity) {
        return Utils.checkGooglePlayServices(activity);
    }

    /**
     * can be used to find out if the application is connected to the service or not.
     *
     * @return <code>true</code> if connected, <code>false</code> otherwise.
     */
    public final boolean isConnected() {
        return (mSelectedCastDevice != null) && mSelectedCastDevice.isConnected();
    }

    /**
     * Returns <code>true</code> only if application is connecting to the Cast service.
     */
    public final boolean isConnecting() {
        return (mSelectedCastDevice != null) && isConnecting;
    }

    /**
     * Disconnects from the cast device and stops the application on the cast device.
     */
    public final void disconnect() {
        if (isConnected() || isConnecting()) {
            for (BaseCastConsumer consumer : mBaseCastConsumers) {
                consumer.onDeviceUnselected(mSelectedCastDevice);
            }
            disconnectDevice(mDestroyOnDisconnect, true, true);
        }
    }

    /**
     * Returns the assigned human-readable name of the device, or <code>null</code> if no device is
     * connected.
     */
    public final String getDeviceName() {
        return mDeviceName;
    }

    /**
     * Sets a flag to control whether disconnection form a cast device should result in stopping
     * the running application or not. If <code>true</code> is passed, then application will be
     * stopped. Default behavior is not to stop the app.
     */
    public final void setStopOnDisconnect(boolean stopOnExit) {
        mDestroyOnDisconnect = stopOnExit;
    }

    /**
     * Returns the {@link MediaRouteSelector} object.
     */
    public final MediaRouteSelector getMediaRouteSelector() {
        return mMediaRouteSelector;
    }

    /**
     * Returns the {@link android.support.v7.media.MediaRouter.RouteInfo} corresponding to the
     * selected route.
     */
    public final RouteInfo getRouteInfo() {
        return mRouteInfo;
    }

    /**
     * Sets the {@link android.support.v7.media.MediaRouter.RouteInfo} corresponding to the
     * selected route.
     */
    public final void setRouteInfo(RouteInfo routeInfo) {
        mRouteInfo = routeInfo;
    }

    /**
     * Turns on configurable features in the library. All the supported features are turned off by
     * default and clients, prior to using them, need to turn them on; it is best to do this
     * immediately after initialization of the library. Bitwise OR combination of features should be
     * passed in if multiple features are needed
     * <p/>
     * Current set of configurable features are:
     * <ul>
     * <li>FEATURE_DEBUGGING : turns on debugging in Google Play services
     * <li>FEATURE_NOTIFICATION : turns notifications on
     * <li>FEATURE_LOCKSCREEN : turns on Lock Screen using {@link RemoteControlClient} in supported
     * versions (JB+)
     * </ul>
     */
    public final void enableFeatures(int capabilities) {
        mCapabilities = capabilities;
        onFeaturesUpdated(mCapabilities);
    }

    /*
     * Returns true if and only if the feature is turned on
     */
    public final boolean isFeatureEnabled(int feature) {
        return (feature & mCapabilities) == feature;
    }

    /**
     * Allow subclasses to be notified of changes to capabilities if they want to.
     */
    protected void onFeaturesUpdated(int capabilities) {
    }

    /**
     * Sets the device (system) volume.
     *
     * @param volume Should be a value between 0 and 1, inclusive.
     * @throws CastException
     * @throws NoConnectionException
     * @throws TransientNetworkDisconnectionException
     */
    public final void setDeviceVolume(double volume) throws CastException, TransientNetworkDisconnectionException, NoConnectionException {
        checkConnectivity();
        try {
            //Cast.CastApi.setVolume(mApiClient, volume);
            VolumeControl volumeControl = mSelectedCastDevice.getCapability(VolumeControl.class);
            if(volumeControl!=null){
                volumeControl.setVolume((float) volume, null);
            }
//        } catch (IOException e) {
//            throw new CastException("Failed to set volume", e);
        } catch (IllegalStateException e) {
            throw new NoConnectionException("setDeviceVolume()", e);
        }  catch (Exception e) {
            throw new CastException("Failed to set volume", e);
        }
    }

    /**
     * Gets the remote's system volume, a number between 0 and 1, inclusive.
     *
     * @throws NoConnectionException
     * @throws TransientNetworkDisconnectionException
     */
    public void getDeviceVolume(VolumeControl.VolumeListener listener) throws TransientNetworkDisconnectionException, NoConnectionException {
        checkConnectivity();
        try {
            //return Cast.CastApi.getVolume(mApiClient);
            VolumeControl volumeControl = mSelectedCastDevice.getCapability(VolumeControl.class);
            if(volumeControl!=null) {

                volumeControl.getVolume(listener);
            }
        } catch (IllegalStateException e) {
            throw new NoConnectionException("getDeviceVolume()", e);
        }
    }

    /**
     * Increments (or decrements) the device volume by the given amount.
     *
     * @throws CastException
     * @throws NoConnectionException
     * @throws TransientNetworkDisconnectionException
     */
    public final void adjustDeviceVolume(final double delta) throws CastException, TransientNetworkDisconnectionException, NoConnectionException {
        checkConnectivity();
        getDeviceVolume(new VolumeControl.VolumeListener() {
            @Override
            public void onSuccess(Float volume) {
                if (volume >= 0) {
                    try {
                        setDeviceVolume(volume + delta);
                    } catch (CastException e) {
                        e.printStackTrace();
                    } catch (TransientNetworkDisconnectionException e) {
                        e.printStackTrace();
                    } catch (NoConnectionException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onError(ServiceCommandError serviceCommandError) {

            }
        });

    }

    /**
     * Returns <code>true</code> if remote device is muted. It internally determines if this should
     * be done for <code>stream</code> or <code>device</code> volume.
     *
     * @throws NoConnectionException
     * @throws TransientNetworkDisconnectionException
     */
    public void isDeviceMute(VolumeControl.MuteListener listener) throws TransientNetworkDisconnectionException, NoConnectionException {
        checkConnectivity();
        try {
            //return Cast.CastApi.isMute(mApiClient);
            VolumeControl volumeControl = mSelectedCastDevice.getCapability(VolumeControl.class);
            if(volumeControl!=null) {
                volumeControl.getMute(listener);
            }
        } catch (IllegalStateException e) {
            throw new NoConnectionException("isDeviceMute()", e);
        }
    }

    /**
     * Mutes or un-mutes the device volume.
     *
     * @throws CastException
     * @throws NoConnectionException
     * @throws TransientNetworkDisconnectionException
     */
    public final void setDeviceMute(boolean mute) throws CastException, TransientNetworkDisconnectionException, NoConnectionException {
        checkConnectivity();
        try {
            //Cast.CastApi.setMute(mApiClient, mute);
            VolumeControl volumeControl = mSelectedCastDevice.getCapability(VolumeControl.class);
            if(volumeControl!=null){
                volumeControl.setMute(mute, null);
            }
//        } catch (IOException e) {
//            throw new CastException("setDeviceMute", e);
        } catch (IllegalStateException e) {
            throw new NoConnectionException("setDeviceMute()", e);
        } catch (Exception e) {
            throw new CastException("setDeviceMute", e);
        }
    }

    /**
     * Returns the current reconnection status
     */
    public final int getReconnectionStatus() {
        return mReconnectionStatus;
    }

    /**
     * Sets the reconnection status
     */
    public final void setReconnectionStatus(int status) {
        if (mReconnectionStatus != status) {
            mReconnectionStatus = status;
            onReconnectionStatusChanged(mReconnectionStatus);
        }
    }

    private void onReconnectionStatusChanged(int status) {
        for (BaseCastConsumer consumer : mBaseCastConsumers) {
            consumer.onReconnectionStatusChanged(status);
        }
    }

    /**
     * Returns <code>true</code> if there is enough persisted information to attempt a session
     * recovery. For this to return <code>true</code>, there needs to be a persisted session ID and
     * a route ID from the last successful launch.
     */
    protected final boolean canConsiderSessionRecovery() {
        return canConsiderSessionRecovery(null);
    }

    /**
     * Returns <code>true</code> if there is enough persisted information to attempt a session
     * recovery. For this to return <code>true</code>, there needs to be persisted session ID and
     * route ID from the last successful launch. In addition, if <code>ssidName</code> is non-null,
     * then an additional check is also performed to make sure the persisted wifi name is the same
     * as the <code>ssidName</code>
     */
    public final boolean canConsiderSessionRecovery(String ssidName) {
        String sessionId = mPreferenceAccessor.getStringFromPreference(PREFS_KEY_SESSION_ID);
        String routeId = mPreferenceAccessor.getStringFromPreference(PREFS_KEY_ROUTE_ID);
        String ssid = mPreferenceAccessor.getStringFromPreference(PREFS_KEY_SSID);
        if (sessionId == null || routeId == null) {
            return false;
        }
        if (ssidName != null && (ssid == null || (!ssid.equals(ssidName)))) {
            return false;
        }
        LOGD(TAG, "Found session info in the preferences, so proceed with an "
                + "attempt to reconnect if possible");
        return true;
    }

    private void reconnectSessionIfPossibleInternal(RouteInfo theRoute) {
        if (isConnected()) {
            return;
        }
        String sessionId = mPreferenceAccessor.getStringFromPreference(PREFS_KEY_SESSION_ID);
        String routeId = mPreferenceAccessor.getStringFromPreference(PREFS_KEY_ROUTE_ID);
        LOGD(TAG, "reconnectSessionIfPossible() Retrieved from preferences: " + "sessionId="
                + sessionId + ", routeId=" + routeId);
        if (sessionId == null || routeId == null) {
            return;
        }
        setReconnectionStatus(RECONNECTION_STATUS_IN_PROGRESS);

        ConnectableDevice device = getDeviceForRouteId(theRoute);
        if (device != null) {
            LOGD(TAG, "trying to acquire Cast Client for " + device);
            onDeviceSelected(device);
        }
    }

    /*
     * Cancels the task responsible for recovery of prior sessions, is used internally.
     */
    public final void cancelReconnectionTask() {
        LOGD(TAG, "cancelling reconnection task");
        if (mReconnectionTask != null && !mReconnectionTask.isCancelled()) {
            mReconnectionTask.cancel(true);
        }
    }

    /**
     * This method tries to automatically re-establish re-establish connection to a session if
     * <ul>
     * <li>User had not done a manual disconnect in the last session
     * <li>Device that user had connected to previously is still running the same session
     * </ul>
     * Under these conditions, a best-effort attempt will be made to continue with the same
     * session. This attempt will go on for {@code SESSION_RECOVERY_TIMEOUT} seconds.
     */
    public final void reconnectSessionIfPossible() {
        reconnectSessionIfPossible(SESSION_RECOVERY_TIMEOUT_S);
    }

    /**
     * This method tries to automatically re-establish connection to a session if
     * <ul>
     * <li>User had not done a manual disconnect in the last session
     * <li>The Cast Device that user had connected to previously is still running the same session
     * </ul>
     * Under these conditions, a best-effort attempt will be made to continue with the same
     * session. This attempt will go on for <code>timeoutInSeconds</code> seconds.
     */
    public final void reconnectSessionIfPossible(final int timeoutInSeconds) {
        reconnectSessionIfPossible(timeoutInSeconds, null);
    }

    /**
     * This method tries to automatically re-establish connection to a session if
     * <ul>
     * <li>User had not done a manual disconnect in the last session
     * <li>The Cast Device that user had connected to previously is still running the same session
     * </ul>
     * Under these conditions, a best-effort attempt will be made to continue with the same
     * session.
     * This attempt will go on for <code>timeoutInSeconds</code> seconds.
     *
     * @param timeoutInSeconds the length of time, in seconds, to attempt reconnection before giving
     * up
     * @param ssidName The name of Wifi SSID
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public void reconnectSessionIfPossible(final int timeoutInSeconds, String ssidName) {
        LOGD(TAG, String.format("reconnectSessionIfPossible(%d, %s)", timeoutInSeconds, ssidName));
        if (isConnected()) {
            return;
        }
        String routeId = mPreferenceAccessor.getStringFromPreference(PREFS_KEY_ROUTE_ID);
        if (canConsiderSessionRecovery(ssidName)) {
            List<RouteInfo> routes = mMediaRouter.getRoutes();
            RouteInfo theRoute = null;
            if (routes != null) {
                for (RouteInfo route : routes) {
                    if (route.getId().equals(routeId)) {
                        theRoute = route;
                        break;
                    }
                }
            }
            if (theRoute != null) {
                // route has already been discovered, so lets just get the device
                reconnectSessionIfPossibleInternal(theRoute);
            } else {
                // we set a flag so if the route is discovered within a short period, we let
                // onRouteAdded callback of CastMediaRouterCallback take care of that
                setReconnectionStatus(RECONNECTION_STATUS_STARTED);
            }

            // cancel any prior reconnection task
            if (mReconnectionTask != null && !mReconnectionTask.isCancelled()) {
                mReconnectionTask.cancel(true);
            }

            // we may need to reconnect to an existing session
            mReconnectionTask = new AsyncTask<Void, Integer, Boolean>() {

                @Override
                protected Boolean doInBackground(Void... params) {
                    for (int i = 0; i < timeoutInSeconds; i++) {
                        LOGD(TAG, "Reconnection: Attempt " + (i + 1));
                        if (isCancelled()) {
                            return true;
                        }
                        try {
                            if (isConnected()) {
                                cancel(true);
                            }
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            // ignore
                        }
                    }
                    return false;
                }

                @Override
                protected void onPostExecute(Boolean result) {
                    if (result == null || !result) {
                        LOGD(TAG, "Couldn't reconnect, dropping connection");
                        setReconnectionStatus(RECONNECTION_STATUS_INACTIVE);
                        onDeviceSelected(null);
                    }
                }

            };
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                mReconnectionTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            } else {
                mReconnectionTask.execute();
            }
        }
    }

    /**
     * This is called by the library when a connection is re-established after a transient
     * disconnect. Note: this is not called by SDK.
     */
    public void onConnectivityRecovered() {
        for (BaseCastConsumer consumer : mBaseCastConsumers) {
            consumer.onConnectivityRecovered();
        }
    }

    /*
    * Note: this is not called by the SDK anymore but this library calls this in the appropriate
    * time.
    */
    protected void onDisconnected(boolean stopAppOnExit, boolean clearPersistedConnectionData, boolean setDefaultRoute) {
        LOGD(TAG, "onDisconnected() reached");
        mDeviceName = null;
        for (BaseCastConsumer consumer : mBaseCastConsumers) {
            consumer.onDisconnected();
        }
    }

    /*
     * (non-Javadoc)
     * @see com.google.android.gms.GoogleApiClient.ConnectionCallbacks#onConnected
     * (android.os.Bundle)
     */
//    @Override
//    public final void onConnected(Bundle hint) {
//        LOGD(TAG, "onConnected() reached with prior suspension: " + mConnectionSuspended);
//        if (mConnectionSuspended) {
//            mConnectionSuspended = false;
//            if (hint != null && hint.getBoolean(Cast.EXTRA_APP_NO_LONGER_RUNNING)) {
//                // the same app is not running any more
//                LOGD(TAG, "onConnected(): App no longer running, so disconnecting");
//                disconnect();
//            } else {
//                onConnectivityRecovered();
//            }
//            return;
//        }
//        if (!isConnected()) {
//            if (mReconnectionStatus == RECONNECTION_STATUS_IN_PROGRESS) {
//                setReconnectionStatus(RECONNECTION_STATUS_INACTIVE);
//            }
//            return;
//        }
//        try {
//            if (isFeatureEnabled(FEATURE_WIFI_RECONNECT)) {
//                String ssid = Utils.getWifiSsid(mContext);
//                mPreferenceAccessor.saveStringToPreference(PREFS_KEY_SSID, ssid);
//            }
//            Cast.CastApi.requestStatus(mApiClient);
//            launchApp();
//
//            for (BaseCastConsumer consumer : mBaseCastConsumers) {
//                consumer.onConnected();
//            }
//
//        } catch (IOException | IllegalStateException e) {
//            LOGE(TAG, "requestStatus()", e);
//        }
//
//    }

    /*
     * (non-Javadoc)
     * @see com.google.android.gms.GoogleApiClient.OnConnectionFailedListener#
     * onConnectionFailed(com.google.android.gms.common.ConnectionResult)
     */
//    @Override
//    public void onConnectionFailed(ConnectionResult result) {
//        LOGD(TAG, "onConnectionFailed() reached, error code: " + result.getErrorCode()
//                + ", reason: " + result.toString());
//        disconnectDevice(mDestroyOnDisconnect, false /* clearPersistentConnectionData */,
//                false /* setDefaultRoute */);
//        mConnectionSuspended = false;
//        if (mMediaRouter != null) {
//            mMediaRouter.selectRoute(mMediaRouter.getDefaultRoute());
//        }
//
//        for (BaseCastConsumer consumer : mBaseCastConsumers) {
//            consumer.onConnectionFailed(result);
//        }
//    }

//    @Override
//    public void onConnectionSuspended(int cause) {
//        mConnectionSuspended = true;
//        LOGD(TAG, "onConnectionSuspended() was called with cause: " + cause);
//        for (BaseCastConsumer consumer : mBaseCastConsumers) {
//            consumer.onConnectionSuspended(cause);
//        }
//    }

    @Override
    public void onDeviceReady(ConnectableDevice connectableDevice) {
        LOGD(TAG, "onConnected() reached with prior suspension: " + mConnectionSuspended);
        isConnecting = false;

        if (mConnectionSuspended) {
            mConnectionSuspended = false;
//            if (hint != null && hint.getBoolean(Cast.EXTRA_APP_NO_LONGER_RUNNING)) {
//                // the same app is not running any more
//                LOGD(TAG, "onConnected(): App no longer running, so disconnecting");
//                disconnect();
//            } else {
                onConnectivityRecovered();
//            }
//            return;
        }
        if (!isConnected()) {
            if (mReconnectionStatus == RECONNECTION_STATUS_IN_PROGRESS) {
                setReconnectionStatus(RECONNECTION_STATUS_INACTIVE);
            }
            return;
        }
        try {
            if (isFeatureEnabled(FEATURE_WIFI_RECONNECT)) {
                String ssid = Utils.getWifiSsid(mContext);
                mPreferenceAccessor.saveStringToPreference(PREFS_KEY_SSID, ssid);
            }

            //Cast.CastApi.requestStatus(mApiClient);
            launchApp();

//            for (BaseCastConsumer consumer : mBaseCastConsumers) {
//                consumer.onDeviceSelected(connectableDevice);
//            }

            for (BaseCastConsumer consumer : mBaseCastConsumers) {
                consumer.onConnected();
            }

        } catch (IOException | IllegalStateException e) {
            LOGE(TAG, "requestStatus()", e);
        }
    }

    @Override
    public void onPairingRequired(ConnectableDevice connectableDevice, DeviceService deviceService, DeviceService.PairingType pairingType) {

    }

    @Override
    public void onDeviceDisconnected(ConnectableDevice connectableDevice) {
        LOGD(TAG, "onDeviceDisconnected() reached, device " + connectableDevice);
        isConnecting = false;

//        for (BaseCastConsumer consumer : mBaseCastConsumers) {
//            consumer.onDeviceUnselected(connectableDevice);
//        }

        disconnectDevice(mDestroyOnDisconnect, false, true);
        //mConnectionSuspended = false;

        for (BaseCastConsumer consumer : mBaseCastConsumers) {
            consumer.onDisconnected();
        }
    }

    @Override
    public void onCapabilityUpdated(ConnectableDevice connectableDevice, List<String> list, List<String> list1) {
//        mConnectionSuspended = true;
//        LOGD(TAG, "onConnectionSuspended() was called with cause: " + cause);
//        for (BaseCastConsumer consumer : mBaseCastConsumers) {
//            consumer.onConnectionSuspended(cause);
//        }
    }

    @Override
    public void onConnectionFailed(ConnectableDevice connectableDevice, ServiceCommandError serviceCommandError) {
        LOGD(TAG, "onConnectionFailed() reached, error code: " + serviceCommandError.getCode() + ", reason: " + serviceCommandError.getPayload());
        isConnecting = false;
        disconnectDevice(mDestroyOnDisconnect, false, true);
        mConnectionSuspended = false;

        for (BaseCastConsumer consumer : mBaseCastConsumers) {
            consumer.onConnectionFailed(serviceCommandError);
        }
    }

    @Override
    public void onDeviceAdded(DiscoveryManager discoveryManager, ConnectableDevice connectableDevice) {

    }

    @Override
    public void onDeviceUpdated(DiscoveryManager discoveryManager, ConnectableDevice connectableDevice) {

    }

    @Override
    public void onDiscoveryFailed(DiscoveryManager discoveryManager, ServiceCommandError serviceCommandError) {

    }

    @Override
    public void onDeviceRemoved(DiscoveryManager discoveryManager, ConnectableDevice connectableDevice) {
        if(connectableDevice == mSelectedCastDevice) {
            mConnectionSuspended = true;
            LOGD(TAG, "onConnectionSuspended() was called with cause: " + -1);
            for (BaseCastConsumer consumer : mBaseCastConsumers) {
                consumer.onConnectionSuspended(-1);
            }
            //disconnectDevice(false, false, true);
        }
    }

    /*
     * Launches application. For this to succeed, a connection should be already established by the
     * CastClient.
     */
    private void launchApp() throws TransientNetworkDisconnectionException, NoConnectionException {
        LOGD(TAG, "launchApp() is called");
        if (!isConnected()) {
            if (mReconnectionStatus == RECONNECTION_STATUS_IN_PROGRESS) {
                setReconnectionStatus(RECONNECTION_STATUS_INACTIVE);
                return;
            }
            checkConnectivity();
        }

        if (mReconnectionStatus == RECONNECTION_STATUS_IN_PROGRESS) {
            LOGD(TAG, "Attempting to join a previously interrupted session...");
            String sessionId = mPreferenceAccessor.getStringFromPreference(PREFS_KEY_SESSION_ID);
            LOGD(TAG, "joinApplication() -> start");
//            Cast.CastApi.joinApplication(mApiClient, mApplicationId, sessionId).setResultCallback(
//                    new ResultCallback<Cast.ApplicationConnectionResult>() {
//
//                        @Override
//                        public void onResult(ApplicationConnectionResult result) {
//                            if (result.getStatus().isSuccess()) {
//                                LOGD(TAG, "joinApplication() -> success");
//                                onApplicationConnected(result.getApplicationMetadata(),
//                                        result.getApplicationStatus(), result.getSessionId(),
//                                        result.getWasLaunched());
//                            } else {
//                                LOGD(TAG, "joinApplication() -> failure");
//                                clearPersistedConnectionInfo(CLEAR_SESSION | CLEAR_MEDIA_END);
//                                onApplicationConnectionFailed(result.getStatus().getStatusCode());
//                            }
//                        }
//                    }
//            );

            WebAppLauncher launcher = mSelectedCastDevice.getCapability(WebAppLauncher.class);
            if(launcher!=null){
                launcher.joinWebApp(getApplicationId(), new WebAppSession.LaunchListener() {
                    @Override
                    public void onSuccess(WebAppSession webAppSession) {
                        LOGD(TAG, "joinWebApp() -> success");
                        //onApplicationConnected(result.getApplicationMetadata(), result.getApplicationStatus(), result.getSessionId(), result.getWasLaunched());
                        onApplicationConnected(webAppSession, WebAppSession.WebAppStatus.Open);
                    }

                    @Override
                    public void onError(ServiceCommandError serviceCommandError) {
                        LOGD(TAG, "joinWebApp() -> failure");
                        clearPersistedConnectionInfo(CLEAR_SESSION | CLEAR_MEDIA_END);
                        //onApplicationConnectionFailed(result.getStatus().getStatusCode());
                        onApplicationConnectionFailed(serviceCommandError.getCode());
                    }
                });
            }
        } else {
            LOGD(TAG, "Launching app");
//            Cast.CastApi.launchApplication(mApiClient, mApplicationId).setResultCallback(
//                    new ResultCallback<Cast.ApplicationConnectionResult>() {
//
//                        @Override
//                        public void onResult(ApplicationConnectionResult result) {
//                            if (result.getStatus().isSuccess()) {
//                                LOGD(TAG, "launchApplication() -> success result");
//                                onApplicationConnected(result.getApplicationMetadata(),
//                                        result.getApplicationStatus(), result.getSessionId(),
//                                        result.getWasLaunched());
//                            } else {
//                                LOGD(TAG, "launchApplication() -> failure result");
//                                onApplicationConnectionFailed(result.getStatus().getStatusCode());
//                            }
//                        }
//                    }
//            );

            WebAppLauncher launcher = mSelectedCastDevice.getCapability(WebAppLauncher.class);
            if(launcher!=null){
                launcher.launchWebApp(getApplicationId(), new WebAppSession.LaunchListener() {
                    @Override
                    public void onSuccess(WebAppSession webAppSession) {
                        LOGD(TAG, "launchWebApp() -> success :" + webAppSession);
                        //onApplicationConnected(result.getApplicationMetadata(), result.getApplicationStatus(), result.getSessionId(), result.getWasLaunched());
                        onApplicationConnected(webAppSession, WebAppSession.WebAppStatus.Open);
                    }

                    @Override
                    public void onError(ServiceCommandError serviceCommandError) {
                        LOGD(TAG, "launchWebApp() -> failure");
                        clearPersistedConnectionInfo(CLEAR_SESSION | CLEAR_MEDIA_END);
                        //onApplicationConnectionFailed(result.getStatus().getStatusCode());
                        onApplicationConnectionFailed(serviceCommandError.getCode());
                    }
                });
            }
        }
    }

    /**
     * Stops the application on the receiver device.
     *
     * @throws NoConnectionException
     * @throws TransientNetworkDisconnectionException
     */
    public final void stopApplication() throws TransientNetworkDisconnectionException, NoConnectionException {
        checkConnectivity();
//        Cast.CastApi.stopApplication(mApiClient, mSessionId).setResultCallback(
//                new ResultCallback<Status>() {
//
//                    @Override
//                    public void onResult(Status result) {
//                        if (!result.isSuccess()) {
//                            LOGD(TAG, "stopApplication -> onResult: stopping "
//                                    + "application failed");
//                            onApplicationStopFailed(result.getStatusCode());
//                        } else {
//                            LOGD(TAG, "stopApplication -> onResult Stopped application "
//                                    + "successfully");
//                        }
//                    }
//                });

        WebAppLauncher launcher = mSelectedCastDevice.getCapability(WebAppLauncher.class);
        if(launcher!=null){
            launcher.closeWebApp(null, new ResponseListener<Object>() {
                @Override
                public void onError(ServiceCommandError serviceCommandError) {
                    LOGD(TAG, "stopApplication -> onResult: stopping " + "application failed");
                    //onApplicationStopFailed(result.getStatusCode());
                    onApplicationStopFailed(serviceCommandError.getCode());
                }

                @Override
                public void onSuccess(Object o) {
                    LOGD(TAG, "stopApplication -> onResult Stopped application " + "successfully");
                }
            });
        }
    }

    /**
     * Registers a {@link BaseCastConsumer} interface with this class. Registered listeners will be
     * notified of changes to a variety of lifecycle callbacks that the interface provides.
     *
     * @see {@code BaseCastConsumerImpl}
     */
    public final void addBaseCastConsumer(BaseCastConsumer listener) {
        if (listener != null) {
            if (mBaseCastConsumers.add(listener)) {
                LOGD(TAG, "Successfully added the new BaseCastConsumer listener " + listener);
            }
        }
    }

    /**
     * Unregisters a {@link BaseCastConsumer}.
     */
    public final void removeBaseCastConsumer(BaseCastConsumer listener) {
        if (listener != null) {
            if (mBaseCastConsumers.remove(listener)) {
                LOGD(TAG, "Successfully removed the existing BaseCastConsumer listener "
                        + listener);
            }
        }
    }

    /**
     * A simple method that throws an exception of there is no connectivity to the cast device.
     *
     * @throws TransientNetworkDisconnectionException If framework is still trying to recover
     * @throws NoConnectionException If no connectivity to the device exists
     */
    public final void checkConnectivity() throws TransientNetworkDisconnectionException, NoConnectionException {
        if (!isConnected()) {
            if (mConnectionSuspended) {
                throw new TransientNetworkDisconnectionException();
            } else {
                throw new NoConnectionException();
            }
        }
    }

    @Override
    public void onFailed(int resourceId, int statusCode) {
        LOGD(TAG, "onFailed() was called with statusCode: " + statusCode);
        for (BaseCastConsumer consumer : mBaseCastConsumers) {
            consumer.onFailed(resourceId, statusCode);
        }
    }

    /**
     * Returns the version of this library.
     */
    public static final String getCclVersion() {
        return sCclVersion;
    }

    public PreferenceAccessor getPreferenceAccessor() {
        return mPreferenceAccessor;
    }

    /**
     * Clears the persisted connection information. Bitwise OR combination of the following options
     * should be passed as the argument:
     * <ul>
     *     <li>CLEAR_SESSION</li>
     *     <li>CLEAR_ROUTE</li>
     *     <li>CLEAR_WIFI</li>
     *     <li>CLEAR_MEDIA_END</li>
     *     <li>CLEAR_ALL</li>
     * </ul>
     * Clients can form an or
     */
    public final void clearPersistedConnectionInfo(int what) {
        LOGD(TAG, "clearPersistedConnectionInfo(): Clearing persisted data for " + what);
        if (isFlagSet(what, CLEAR_SESSION)) {
            mPreferenceAccessor.saveStringToPreference(PREFS_KEY_SESSION_ID, null);
        }
        if (isFlagSet(what, CLEAR_ROUTE)) {
            mPreferenceAccessor.saveStringToPreference(PREFS_KEY_ROUTE_ID, null);
        }
        if (isFlagSet(what, CLEAR_WIFI)) {
            mPreferenceAccessor.saveStringToPreference(PREFS_KEY_SSID, null);
        }
        if (isFlagSet(what, CLEAR_MEDIA_END)) {
            mPreferenceAccessor.saveLongToPreference(PREFS_KEY_MEDIA_END, null);
        }
    }

    private static boolean isFlagSet(int mask, int flag) {
        return (mask == CLEAR_ALL) || ((mask & flag) == flag);
    }

    protected void startReconnectionService(long mediaDurationLeft) {
        if (!isFeatureEnabled(FEATURE_WIFI_RECONNECT)) {
            return;
        }
        LOGD(TAG, "startReconnectionService() for media length lef = " + mediaDurationLeft);
        long endTime = SystemClock.elapsedRealtime() + mediaDurationLeft;
        mPreferenceAccessor.saveLongToPreference(PREFS_KEY_MEDIA_END, endTime);
        Context applicationContext = mContext.getApplicationContext();
        Intent service = new Intent(applicationContext, ReconnectionService.class);
        service.setPackage(applicationContext.getPackageName());
        applicationContext.startService(service);
    }

    protected void stopReconnectionService() {
        if (!isFeatureEnabled(FEATURE_WIFI_RECONNECT)) {
            return;
        }
        LOGD(TAG, "stopReconnectionService()");
        Context applicationContext = mContext.getApplicationContext();
        Intent service = new Intent(applicationContext, ReconnectionService.class);
        service.setPackage(applicationContext.getPackageName());
        applicationContext.stopService(service);
    }

    /**
     * A Handler.Callback to receive individual messages when UI goes hidden or becomes visible.
     */
    private class UpdateUiVisibilityHandlerCallback implements Handler.Callback {

        @Override
        public boolean handleMessage(Message msg) {
            onUiVisibilityChanged(msg.what == WHAT_UI_VISIBLE);
            return true;
        }
    }

    /**
     * Returns {@code true} if and only if there is at least one route matching the
     * {@link #getMediaRouteSelector()}.
     */
    public boolean isAnyRouteAvailable() {
        return mMediaRouterCallback.isRouteAvailable();
    }
}
