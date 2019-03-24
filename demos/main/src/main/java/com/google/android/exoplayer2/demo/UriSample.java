package com.google.android.exoplayer2.demo;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

public final class UriSample extends Sample {

    public final Uri uri;
    public final String extension;
    public final String adTagUri;
    public final String sphericalStereoMode;

    public UriSample() {
        super(null, "", null, 0);
        this.uri = null;
        this.extension = null;
        this.adTagUri = null;
        this.sphericalStereoMode = null;
    }
    public UriSample(
            String name,
            String imgUrl,
            DrmInfo drmInfo,
            Uri uri,
            String extension,
            String adTagUri,
            String sphericalStereoMode,
            int color) {
        super(name, imgUrl, drmInfo, color);
        this.uri = uri;
        this.extension = extension;
        this.adTagUri = adTagUri;
        this.sphericalStereoMode = sphericalStereoMode;
    }

    @Override
    public Intent buildIntent(
            Context context, boolean preferExtensionDecoders, String abrAlgorithm) {
        return super.buildIntent(context, preferExtensionDecoders, abrAlgorithm)
                .setData(uri)
                .putExtra(PlayerActivity.EXTENSION_EXTRA, extension)
                .putExtra(PlayerActivity.AD_TAG_URI_EXTRA, adTagUri)
                .putExtra(PlayerActivity.SPHERICAL_STEREO_MODE_EXTRA, sphericalStereoMode)
                .setAction(PlayerActivity.ACTION_VIEW);
    }

    @Override
    public String toString() {
        return "UriSample("+uri+")";
    }

    public boolean isEmpty() {
        if (name == null) {
            return true;
        }
        return false;
    }
}