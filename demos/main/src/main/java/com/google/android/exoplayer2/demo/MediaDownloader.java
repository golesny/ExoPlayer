package com.google.android.exoplayer2.demo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;

import com.google.android.exoplayer2.util.Log;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MediaDownloader extends AsyncTask<String, Void, Map<String, Bitmap>> {
    private static final String TAG = "MediaDownloader";

    private HashMap<String, Bitmap> cache = new HashMap<>();
    private List<String> queue = new ArrayList<>();
    private  CustomAdapter adapter;

    public MediaDownloader(CustomAdapter adapter) {
        this.adapter = adapter;
    }

    /**
     * These URLs will be downloaded.
     * @param url the url
     */
    public void addUrl(String url) {
        queue.add(url);
    }

    public void startExecuting() {
     execute(queue.toArray(new String[0]));
    }

    protected Map<String, Bitmap> doInBackground(String... args) {
        for (String url : args) {
            if (!cache.containsKey(url) && !this.isCancelled()) {
                // download image
                try {
                    InputStream in = new java.net.URL(url).openStream();
                    Log.d(TAG, "Downloading image " + url);
                    //mIcon11 = BitmapFactory.decodeStream(in);
                    Bitmap bd = BitmapFactory.decodeStream(in);
                    cache.put(url, bd);
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage());
                }
            }
        }
        return cache;
    }

    @Override
    protected void onPostExecute(Map<String, Bitmap> res) {
        Log.d(TAG, "onPostExecute");
        adapter.setResources(res);
    }
}
