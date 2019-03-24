/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.demo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.JsonReader;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.offline.DownloadService;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSourceInputStream;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** An activity for selecting from a list of media samples. */
public class SampleChooserActivity extends Activity {

  private static final String TAG = "SampleChooserActivity";
  private GridView gridview;
  private CustomAdapter customAdapter;
  AlphaAnimation inAnimation;
  AlphaAnimation outAnimation;

  FrameLayout progressBarHolder;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
    setContentView(R.layout.grid_view_list_hori);
    getActionBar().hide();
    gridview = (GridView) findViewById(R.id.customgrid);
    customAdapter = new CustomAdapter(this);
    customAdapter.registerDataSetObserver(new DataSetObserver() {
      @Override
      public void onChanged() {
        Log.d(TAG, "data set changed");
        setDynamicWidth(gridview);
      }
    });
    gridview.setAdapter(customAdapter);
    progressBarHolder = (FrameLayout) findViewById(R.id.progressBarHolder);

    SampleListLoader loaderTask = new SampleListLoader();
    String[] uris = {"http://192.168.0.66/media.exolist.json"};
    loaderTask.execute(uris);
  }



  @Override
  public void onStart() {
    super.onStart();
    customAdapter.notifyDataSetChanged();
  }

  @Override
  public void onStop() {
    super.onStop();
  }

  protected void startVideo(Sample sample) {
    startActivity(
        sample.buildIntent(
            /* context= */ this,
            false,
            PlayerActivity.ABR_ALGORITHM_DEFAULT));
  }

  private int getDownloadUnsupportedStringId(Sample sample) {
    UriSample uriSample = (UriSample) sample;
    if (uriSample.drmInfo != null) {
      return R.string.download_drm_unsupported;
    }
    if (uriSample.adTagUri != null) {
      return R.string.download_ads_unsupported;
    }
    String scheme = uriSample.uri.getScheme();
    if (!("http".equals(scheme) || "https".equals(scheme))) {
      return R.string.download_scheme_unsupported;
    }
    return 0;
  }

  private void setDynamicWidth(GridView gridView) {
    ListAdapter gridViewAdapter = gridView.getAdapter();
    if (gridViewAdapter == null) {
      return;
    }
    int totalWidth;
    int items = (int)Math.ceil(gridViewAdapter.getCount() / 3f);
    Log.d(TAG, "creating layout for "+items+" columns");
    gridView.setNumColumns(items);

    View listItem = gridViewAdapter.getView(0, null, gridView);
    listItem.measure(0, 0);

    totalWidth = listItem.getMeasuredWidth();
    totalWidth = totalWidth*items + 20; // TODO padding from layout

    ViewGroup.LayoutParams params = gridView.getLayoutParams();
    params.width = totalWidth;
    gridView.setLayoutParams(params);
  }

  private static boolean isNonNullAndChecked(@Nullable MenuItem menuItem) {
    // Temporary workaround for layouts that do not inflate the options menu.
    return menuItem != null && menuItem.isChecked();
  }

  private final class SampleListLoader extends AsyncTask<String, Void, List<UriSample>> {

    private boolean sawError;
    private HashMap<String, Bitmap> cache = new HashMap<>();

    @Override
    protected List<UriSample> doInBackground(String... uris) {
      List<UriSample> result = new ArrayList<>();
      Context context = getApplicationContext();
      String userAgent = Util.getUserAgent(context, "ExoPlayerDemo");
      DataSource dataSource =
          new DefaultDataSource(context, userAgent, /* allowCrossProtocolRedirects= */ false);
      // download samples
      List<UriSample> sampleLList = null;
      String uri = uris[0];
        DataSpec dataSpec = new DataSpec(Uri.parse(uri));
        for (int j=0; j<10 && sampleLList == null; j++) {
          InputStream inputStream = new DataSourceInputStream(dataSource, dataSpec);
          try {
            sampleLList = readArray(new JsonReader(new InputStreamReader(inputStream, "UTF-8")));
            customAdapter.addElements(sampleLList);
          } catch (Exception e) {
            Log.e(TAG, "Error loading sample list try #"+j+": " + uri, e);
            if (j==9) {
              sawError = true;
            } else {
              try {
                // we wait until the Synology is up and running
                TimeUnit.SECONDS.sleep(15);
              } catch (InterruptedException e1) {
                Log.w(TAG, "ignoring "+e1.getMessage());
              }
            }
          } finally {
            Util.closeQuietly(dataSource);
          }
        }
      // download URLs (from media json file)
      for (UriSample url : sampleLList) {
        if (!cache.containsKey(url) && !this.isCancelled()) {
          // download image
          try {
            InputStream in = new java.net.URL(url.imgUrl).openStream();
            Log.d(TAG, "Downloading image " + url);
            Bitmap bd = BitmapFactory.decodeStream(in);
            cache.put(url.imgUrl, bd);
          } catch (Exception e) {
            Log.e(TAG, e.getMessage());
          }
        }
      }
      return result;
    }

    @Override
    protected void onPreExecute() {
      super.onPreExecute();
      // optional: disabling controls
      inAnimation = new AlphaAnimation(0f, 1f);
      inAnimation.setDuration(200);
      progressBarHolder.setAnimation(inAnimation);
      gridview.setVisibility(View.GONE);
      progressBarHolder.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onPostExecute(List<UriSample> result) {
      if (sawError) {
        Toast.makeText(getApplicationContext(), R.string.sample_list_load_error, Toast.LENGTH_LONG)
                .show();
      }
      customAdapter.setResources(cache);
      // turn off loading screen
      outAnimation = new AlphaAnimation(1f, 0f);
      outAnimation.setDuration(200);
      progressBarHolder.setAnimation(outAnimation);
      progressBarHolder.setVisibility(View.GONE);
      gridview.setVisibility(View.VISIBLE);
    }

    private List<UriSample> readArray(JsonReader reader) throws IOException {
      List<UriSample> result = new ArrayList<>();
      reader.beginArray();
      while (reader.hasNext()) {
        UriSample sample = readEntry(reader);
        result.add(sample);
      }
      reader.endArray();
      return result;
    }

    private UriSample readEntry(JsonReader reader) throws IOException {
      String sampleName = null;
      Uri uri = null;
      String imgUrl = null;
      String extension = null;
      String drmScheme = null;
      String drmLicenseUrl = null;
      String[] drmKeyRequestProperties = null;
      boolean drmMultiSession = false;
      String adTagUri = null;
      String sphericalStereoMode = null;
      String bgcolor = null;
      reader.beginObject();
      while (reader.hasNext()) {
        String name = reader.nextName();
        switch (name) {
          case "name":
            sampleName = reader.nextString();
            break;
          case "imgurl":
            imgUrl = reader.nextString();
            break;
          case "uri":
            uri = Uri.parse(reader.nextString());
            break;
          case "bgcolor":
            bgcolor = reader.nextString();
            break;
          case "extension":
            extension = reader.nextString();
            break;
          case "drm_scheme":
            //Assertions.checkState(!insidePlaylist, "Invalid attribute on nested item: drm_scheme");
            drmScheme = reader.nextString();
            break;
          case "drm_license_url":
            //Assertions.checkState(!insidePlaylist,"Invalid attribute on nested item: drm_license_url");
            drmLicenseUrl = reader.nextString();
            break;
          case "drm_key_request_properties":
            //Assertions.checkState(!insidePlaylist, "Invalid attribute on nested item: drm_key_request_properties");
            ArrayList<String> drmKeyRequestPropertiesList = new ArrayList<>();
            reader.beginObject();
            while (reader.hasNext()) {
              drmKeyRequestPropertiesList.add(reader.nextName());
              drmKeyRequestPropertiesList.add(reader.nextString());
            }
            reader.endObject();
            drmKeyRequestProperties = drmKeyRequestPropertiesList.toArray(new String[0]);
            break;
          case "drm_multi_session":
            drmMultiSession = reader.nextBoolean();
            break;
          case "ad_tag_uri":
            adTagUri = reader.nextString();
            break;
          case "spherical_stereo_mode":
            //Assertions.checkState(!insidePlaylist, "Invalid attribute on nested item: spherical_stereo_mode");
            sphericalStereoMode = reader.nextString();
            break;
          default:
            throw new ParserException("Unsupported attribute name: " + name);
        }
      }
      reader.endObject();
      DrmInfo drmInfo =
          drmScheme == null
              ? null
              : new DrmInfo(drmScheme, drmLicenseUrl, drmKeyRequestProperties, drmMultiSession);

      int color = Color.WHITE;
      if (bgcolor != null) {
        try {
          color = Color.parseColor(bgcolor);
        } catch (IllegalArgumentException e) {
          Log.w(TAG, "Wrong color code: " + bgcolor + " - " + e.getMessage());
          Toast.makeText(getApplicationContext(), R.string.wrong_color + ": "+bgcolor, Toast.LENGTH_LONG)
                  .show();
        }
      }
        return new UriSample(
            sampleName,
            imgUrl,
            drmInfo,
            uri,
            extension,
            adTagUri,
            sphericalStereoMode, color);

    }


  }
}
