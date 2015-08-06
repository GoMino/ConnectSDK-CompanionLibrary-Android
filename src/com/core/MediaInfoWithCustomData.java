package com.core;

import com.connectsdk.core.ImageInfo;
import com.connectsdk.core.MediaInfo;

import org.json.JSONObject;

import java.io.Serializable;
import java.util.List;

/**
 * Created by gomino on 7/8/15.
 */
public class MediaInfoWithCustomData extends MediaInfo {

    private JSONObject mCustomData;
    private JSONObject mCustomDataForLoad;

    public MediaInfoWithCustomData(String url, String mimeType, String title, String description) {
        super(url, mimeType, title, description);
    }

    public MediaInfoWithCustomData(String url, String mimeType, String title, String description, List<ImageInfo> allImages) {
        super(url, mimeType, title, description, allImages);
    }

    public JSONObject getCustomData() {
        return mCustomData;
    }

    public void setCustomData(JSONObject customData) {
        this.mCustomData = customData;
    }

    public JSONObject getCustomDataForLoad() {
        return mCustomDataForLoad;
    }

    public void setCustomDataForLoad(JSONObject customDataForLoad) {
        this.mCustomDataForLoad = customDataForLoad;
    }

}
