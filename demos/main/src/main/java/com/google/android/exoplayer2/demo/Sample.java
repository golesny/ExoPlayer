package com.google.android.exoplayer2.demo;

import android.content.Context;
import android.content.Intent;

public abstract class Sample {
    public final String name;
    public final String imgUrl;
    public final DrmInfo drmInfo;
    public final int color;

    public Sample(String name, String imgUrl, DrmInfo drmInfo, int color) {
        this.name = name;
        this.imgUrl = imgUrl;
        this.drmInfo = drmInfo;
        this.color = color;
    }

    public Intent buildIntent(
            Context context, boolean preferExtensionDecoders, String abrAlgorithm) {
        Intent intent = new Intent(context, PlayerActivity.class);
        intent.putExtra(PlayerActivity.PREFER_EXTENSION_DECODERS_EXTRA, preferExtensionDecoders);
        intent.putExtra(PlayerActivity.ABR_ALGORITHM_EXTRA, abrAlgorithm);
        if (drmInfo != null) {
            drmInfo.updateIntent(intent);
        }
        return intent;
    }

}