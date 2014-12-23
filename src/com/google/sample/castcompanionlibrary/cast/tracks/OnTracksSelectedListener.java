package com.google.sample.castcompanionlibrary.cast.tracks;

import com.google.android.gms.cast.MediaTrack;

import java.util.List;

/**
 * An interface to listen to changes to the active tracks for a media.
 */
public interface OnTracksSelectedListener {

    /**
     * Called to inform the listeners of the new set of active tracks.
     *
     * @param tracks A Non-<code>null</code> list of MediaTracks.
     */
    void onTracksSelected(List<MediaTrack> tracks);
}
