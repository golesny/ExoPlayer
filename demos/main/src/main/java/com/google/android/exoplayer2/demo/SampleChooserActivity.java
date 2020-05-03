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
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.JsonReader;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.Toast;
import com.google.android.exoplayer2.ParserException;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSourceInputStream;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** An activity for selecting from a list of media samples. */
public class SampleChooserActivity extends Activity implements OnClickListener {

  private static final String TAG = "SampleChooserActivity";
  private GridView gridview;
  private CustomAdapter customAdapter;
  private LinearLayout menuLayout;
  AlphaAnimation inAnimation;
  AlphaAnimation outAnimation;

  FrameLayout progressBarHolder;
  private Map<SampleCategory, ImageButton> menuLayoutButtons = new HashMap<>();

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
    setContentView(R.layout.grid_view_list_hori);
    getActionBar().hide();
    gridview = (GridView) findViewById(R.id.customgrid);
    menuLayout = (LinearLayout) findViewById(R.id.menuLayout);
    // extract buttons
    for (int i=0; i<menuLayout.getChildCount(); i++) {
      View c = menuLayout.getChildAt(i);
      if (c instanceof ImageButton) {
        ImageButton bt = (ImageButton)c;
        try {
          SampleCategory cat = SampleCategory.valueOf(bt.getContentDescription().toString());
          menuLayoutButtons.put(cat, bt);
        } catch (Exception e) {
          Toast.makeText(getApplicationContext(), R.string.wrong_category + "in button "+c.getId(), Toast.LENGTH_LONG).show();
        }
      }
    }

    //menuLayout.addView(button1);
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

  @Override
  protected void onResume() {
    super.onResume();
    Log.d(TAG, "onResume()");
    customAdapter.updateFilteredElements();
  }

  protected void startVideo(Sample sample) {
    startActivity(
        sample.buildIntent(
            /* context= */ this,
            false,
            PlayerActivity.ABR_ALGORITHM_DEFAULT));
  }

  @Override
  public void onClick(View view) {
    if (view instanceof ImageButton) {
      ImageButton bt = (ImageButton)view;
      if (bt.getContentDescription() != null) {
        try {
          SampleCategory cat = SampleCategory.valueOf(bt.getContentDescription().toString());
          customAdapter.setFilter(cat);
        } catch (Exception e) {
          Toast.makeText(getApplicationContext(), R.string.wrong_category + "in button "+view.getId(), Toast.LENGTH_LONG)
                  .show();
        }
      }
    }
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
      List<Sample> sampleLList = null;
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
      Set<SampleCategory> unavailableCat = new HashSet<>();
      unavailableCat.addAll(Arrays.asList(SampleCategory.values()));
      for (Sample url : sampleLList) {
        if (!cache.containsKey(url) && !this.isCancelled()) {
          // download image
          try {
            InputStream in = new java.net.URL(url.imgUrl).openStream();
            Log.d(TAG, "Downloading image " + url);
            Bitmap bd = BitmapFactory.decodeStream(in);
            cache.put(url.imgUrl, bd);
            // remove cat from unavailable
            unavailableCat.remove(url.category);
          } catch (Exception e) {
            Log.e(TAG, e.getMessage());
          }
        }
      }
      // make button invisible that have no content
      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          // Stuff that updates the UI
          for (SampleCategory cat : unavailableCat){
            menuLayoutButtons.get(cat).setVisibility(View.GONE);
          }
          menuLayout.invalidate();
          customAdapter.setFilter(SampleCategory.values()[0]);
        }
      });
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

    private List<Sample> readArray(JsonReader reader) throws IOException {
      List<Sample> result = new ArrayList<>();
      reader.beginArray();
      while (reader.hasNext()) {
        UriSample sample = readEntry(reader);
        result.add(sample);
      }
      reader.endArray();
      fillPosInCategory(result);
      return result;
    }

    private void fillPosInCategory(List<Sample> sampleList) {
      Map<SampleCategory, Integer> catmap = new HashMap<>();
      for (SampleCategory c : SampleCategory.values()) {
        catmap.put(c, 0);
      }
      for (int i=0; i<sampleList.size(); i++) {
        int idx = catmap.get(sampleList.get(i).category) + 1;
        catmap.put(sampleList.get(i).category, idx);
        sampleList.get(i).setPosInCategory(idx);
      }
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
      SampleCategory category = SampleCategory.VIDEOS;
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
          case "category":
            String cat = null;
            try {
              cat = reader.nextString();
              category = SampleCategory.valueOf(cat);
            } catch (IllegalArgumentException e) {
              Toast.makeText(getApplicationContext(), R.string.wrong_category + ": "+cat, Toast.LENGTH_LONG)
                      .show();
            }
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
            sphericalStereoMode, color, category);

    }


  }
}
