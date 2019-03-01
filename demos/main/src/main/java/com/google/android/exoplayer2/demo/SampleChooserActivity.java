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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
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
import java.util.List;

/** An activity for selecting from a list of media samples. */
public class SampleChooserActivity extends Activity
    implements DownloadTracker.Listener, OnChildClickListener {

  private static final String TAG = "SampleChooserActivity";

  private boolean useExtensionRenderers;
  private DownloadTracker downloadTracker;
  private SampleAdapter sampleAdapter;
  private MenuItem preferExtensionDecodersMenuItem;
  private MenuItem randomAbrMenuItem;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.sample_chooser_activity);
    sampleAdapter = new SampleAdapter();
    ExpandableListView sampleListView = findViewById(R.id.sample_list);
    sampleListView.setAdapter(sampleAdapter);
    sampleListView.setOnChildClickListener(this);

    Intent intent = getIntent();
    String dataUri = intent.getDataString();
    String[] uris;
    if (dataUri != null) {
      uris = new String[] {dataUri};
    } else {
      ArrayList<String> uriList = new ArrayList<>();
      /*AssetManager assetManager = getAssets();
      try {
        for (String asset : assetManager.list("")) {
          if (asset.endsWith(".exolist.json")) {
            uriList.add("asset:///" + asset);
          }
        }
      } catch (IOException e) {
        Toast.makeText(getApplicationContext(), R.string.sample_list_load_error, Toast.LENGTH_LONG)
            .show();
      }
      uris = new String[uriList.size()];
      uriList.toArray(uris);
      Arrays.sort(uris);
      */
      uris = new String[1];
      uris[0] = "http://192.168.0.101/media.exolist.json";
    }

    DemoApplication application = (DemoApplication) getApplication();
    useExtensionRenderers = application.useExtensionRenderers();
    downloadTracker = application.getDownloadTracker();
    SampleListLoader loaderTask = new SampleListLoader();
    loaderTask.execute(uris);

    // Start the download service if it should be running but it's not currently.
    // Starting the service in the foreground causes notification flicker if there is no scheduled
    // action. Starting it in the background throws an exception if the app is in the background too
    // (e.g. if device screen is locked).
    try {
      DownloadService.start(this, DemoDownloadService.class);
    } catch (IllegalStateException e) {
      DownloadService.startForeground(this, DemoDownloadService.class);
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.sample_chooser_menu, menu);
    preferExtensionDecodersMenuItem = menu.findItem(R.id.prefer_extension_decoders);
    preferExtensionDecodersMenuItem.setVisible(useExtensionRenderers);
    randomAbrMenuItem = menu.findItem(R.id.random_abr);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    item.setChecked(!item.isChecked());
    return true;
  }

  @Override
  public void onStart() {
    super.onStart();
    downloadTracker.addListener(this);
    sampleAdapter.notifyDataSetChanged();
  }

  @Override
  public void onStop() {
    downloadTracker.removeListener(this);
    super.onStop();
  }

  @Override
  public void onDownloadsChanged() {
    sampleAdapter.notifyDataSetChanged();
  }

  private void onSampleGroups(final List<SampleGroup> groups, boolean sawError) {
    if (sawError) {
      Toast.makeText(getApplicationContext(), R.string.sample_list_load_error, Toast.LENGTH_LONG)
          .show();
    }
    sampleAdapter.setSampleGroups(groups);
  }

  @Override
  public boolean onChildClick(
      ExpandableListView parent, View view, int groupPosition, int childPosition, long id) {
    Sample sample = (Sample) view.getTag();
    startVideo(sample);
    return true;
  }

  private void startVideo(Sample sample) {
    startActivity(
        sample.buildIntent(
            /* context= */ this,
            isNonNullAndChecked(preferExtensionDecodersMenuItem),
            isNonNullAndChecked(randomAbrMenuItem)
                ? PlayerActivity.ABR_ALGORITHM_RANDOM
                : PlayerActivity.ABR_ALGORITHM_DEFAULT));
  }

  private void onSampleDownloadButtonClicked(Sample sample) {
    int downloadUnsupportedStringId = getDownloadUnsupportedStringId(sample);
    if (downloadUnsupportedStringId != 0) {
      Toast.makeText(getApplicationContext(), downloadUnsupportedStringId, Toast.LENGTH_LONG)
          .show();
    } else {
      UriSample uriSample = (UriSample) sample;
      downloadTracker.toggleDownload(this, sample.name, uriSample.uri, uriSample.extension);
    }
  }

  private int getDownloadUnsupportedStringId(Sample sample) {
    if (sample instanceof PlaylistSample) {
      return R.string.download_playlist_unsupported;
    }
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

  private static boolean isNonNullAndChecked(@Nullable MenuItem menuItem) {
    // Temporary workaround for layouts that do not inflate the options menu.
    return menuItem != null && menuItem.isChecked();
  }

  private final class SampleListLoader extends AsyncTask<String, Void, List<SampleGroup>> {

    private boolean sawError;

    @Override
    protected List<SampleGroup> doInBackground(String... uris) {
      List<SampleGroup> result = new ArrayList<>();
      Context context = getApplicationContext();
      String userAgent = Util.getUserAgent(context, "ExoPlayerDemo");
      DataSource dataSource =
          new DefaultDataSource(context, userAgent, /* allowCrossProtocolRedirects= */ false);
      for (String uri : uris) {
        DataSpec dataSpec = new DataSpec(Uri.parse(uri));
        InputStream inputStream = new DataSourceInputStream(dataSource, dataSpec);
        try {
          readSampleGroups(new JsonReader(new InputStreamReader(inputStream, "UTF-8")), result);
        } catch (Exception e) {
          Log.e(TAG, "Error loading sample list: " + uri, e);
          sawError = true;
        } finally {
          Util.closeQuietly(dataSource);
        }
      }
      return result;
    }

    @Override
    protected void onPostExecute(List<SampleGroup> result) {
      onSampleGroups(result, sawError);
    }

    private void readSampleGroups(JsonReader reader, List<SampleGroup> groups) throws IOException {
      reader.beginArray();
      while (reader.hasNext()) {
        readSampleGroup(reader, groups);
      }
      reader.endArray();
    }

    private void readSampleGroup(JsonReader reader, List<SampleGroup> groups) throws IOException {
      String groupName = "";
      String imgUrl = "";
      ArrayList<Sample> samples = new ArrayList<>();

      reader.beginObject();
      while (reader.hasNext()) {
        String name = reader.nextName();
        switch (name) {
          case "name":
            groupName = reader.nextString();
            break;
          case "imgurl":
            imgUrl = reader.nextString();
            break;
          case "samples":
            reader.beginArray();
            while (reader.hasNext()) {
              samples.add(readEntry(reader, false));
            }
            reader.endArray();
            break;
          case "_comment":
            reader.nextString(); // Ignore.
            break;
          default:
            throw new ParserException("Unsupported name: " + name);
        }
      }
      reader.endObject();

      SampleGroup group = getGroup(groupName, imgUrl, groups);
      group.samples.addAll(samples);
    }

    private Sample readEntry(JsonReader reader, boolean insidePlaylist) throws IOException {
      String sampleName = null;
      Uri uri = null;
      String imgUrl = null;
      String extension = null;
      String drmScheme = null;
      String drmLicenseUrl = null;
      String[] drmKeyRequestProperties = null;
      boolean drmMultiSession = false;
      ArrayList<UriSample> playlistSamples = null;
      String adTagUri = null;
      String sphericalStereoMode = null;

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
          case "extension":
            extension = reader.nextString();
            break;
          case "drm_scheme":
            Assertions.checkState(!insidePlaylist, "Invalid attribute on nested item: drm_scheme");
            drmScheme = reader.nextString();
            break;
          case "drm_license_url":
            Assertions.checkState(!insidePlaylist,
                "Invalid attribute on nested item: drm_license_url");
            drmLicenseUrl = reader.nextString();
            break;
          case "drm_key_request_properties":
            Assertions.checkState(!insidePlaylist,
                "Invalid attribute on nested item: drm_key_request_properties");
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
          case "playlist":
            Assertions.checkState(!insidePlaylist, "Invalid nesting of playlists");
            playlistSamples = new ArrayList<>();
            reader.beginArray();
            while (reader.hasNext()) {
              playlistSamples.add((UriSample) readEntry(reader, true));
            }
            reader.endArray();
            break;
          case "ad_tag_uri":
            adTagUri = reader.nextString();
            break;
          case "spherical_stereo_mode":
            Assertions.checkState(
                !insidePlaylist, "Invalid attribute on nested item: spherical_stereo_mode");
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
      if (playlistSamples != null) {
        UriSample[] playlistSamplesArray = playlistSamples.toArray(
            new UriSample[playlistSamples.size()]);
        return new PlaylistSample(sampleName, imgUrl, drmInfo, playlistSamplesArray);
      } else {
        return new UriSample(
            sampleName,
            imgUrl,
            drmInfo,
            uri,
            extension,
            adTagUri,
            sphericalStereoMode);
      }
    }

    private SampleGroup getGroup(String groupName, String imgUrl, List<SampleGroup> groups) {
      for (int i = 0; i < groups.size(); i++) {
        if (Util.areEqual(groupName, groups.get(i).title)) {
          return groups.get(i);
        }
      }
      SampleGroup group = new SampleGroup(groupName, imgUrl);
      groups.add(group);
      return group;
    }

  }

  private final class SampleAdapter extends BaseExpandableListAdapter implements OnClickListener {

    private List<SampleGroup> sampleGroups;

    public SampleAdapter() {
      sampleGroups = Collections.emptyList();
    }

    public void setSampleGroups(List<SampleGroup> sampleGroups) {
      this.sampleGroups = sampleGroups;
      notifyDataSetChanged();
    }

    @Override
    public Sample getChild(int groupPosition, int childPosition) {
      return getGroup(groupPosition).samples.get(childPosition);
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
      return childPosition;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
        View convertView, ViewGroup parent) {
      View view = convertView;
      Sample child = getChild(groupPosition, childPosition);
      if (view == null) {
        view = getLayoutInflater().inflate(R.layout.sample_list_item, parent, false);
        ImageButton downloadButton = (ImageButton) view.findViewById(R.id.download_button);
        if (child.imgUrl != null) {
          new DownloadImageTask(downloadButton).execute(child.imgUrl);
        }
        downloadButton.setOnClickListener(this);
        downloadButton.setFocusable(false);
      }

      initializeChildView(view, child);
      return view;
    }
 // better answer 2: https://stackoverflow.com/questions/5776851/load-image-from-url
    private void getBitmap(final String urlString, ImageButton imageBt) {
      try {
        final URL url = new URL(urlString);
        final Uri uri = Uri.parse(urlString);
        Activity bt = new Activity() {
          @Override
          protected void onActivityResult(int requestCode, int resultCode, Intent data) {
            Log.w(TAG, "URL Error loading bitmap from url {}: ");
            try {
              Bitmap bmp = BitmapFactory.decodeStream(url.openConnection().getInputStream());
              imageBt.setImageBitmap(bmp);
            } catch (IOException e) {
              Log.e(TAG, "IO Error loading bitmap from url {}: " + url, e);
            } catch (Exception e) {
              Log.e(TAG, "General Error loading bitmap from url {}: " + url, e);
            }
          }
        };
        downloadTracker.toggleDownload(bt, urlString, uri, "jpg");
      } catch (MalformedURLException e) {
        Log.e(TAG, "URL Error loading bitmap from url {}: " + urlString, e);
      }
    }

    @Override
    public int getChildrenCount(int groupPosition) {
      return getGroup(groupPosition).samples.size();
    }

    @Override
    public SampleGroup getGroup(int groupPosition) {
      return sampleGroups.get(groupPosition);
    }

    @Override
    public long getGroupId(int groupPosition) {
      return groupPosition;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView,
        ViewGroup parent) {
      View view = convertView;
      if (view == null) {
        view =
            getLayoutInflater()
                .inflate(R.layout.sample_list_item, parent, false);
      }
      SampleGroup group = getGroup(groupPosition);
      ImageButton downloadButton = (ImageButton) view.findViewById(R.id.download_button);
        if (group.coverImgUrl != null) {
            new DownloadImageTask(downloadButton).execute(group.coverImgUrl);
        }
        downloadButton.setPadding(40, 0,20,0);
        downloadButton.setOnClickListener(this);
        downloadButton.setFocusable(false);


      TextView textView = (TextView) view.findViewById(R.id.sample_title);
      textView.setText(group.title);

      new DownloadTextViewImageTask(textView).execute(group.coverImgUrl);
      return view;
    }

    @Override
    public int getGroupCount() {
      return sampleGroups.size();
    }

    @Override
    public boolean hasStableIds() {
      return false;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
      return true;
    }

    @Override
    public void onClick(View view) {
     Sample s = (Sample) view.getTag();

     if (s != null) {
         startVideo(s);
     }
      //onSampleDownloadButtonClicked((Sample) view.getTag());
    }

    private void initializeChildView(View view, Sample sample) {
      view.setTag(sample);
      TextView sampleTitle = view.findViewById(R.id.sample_title);
      sampleTitle.setText(sample.name);
;
      boolean canDownload = false; //getDownloadUnsupportedStringId(sample) == 0;
      boolean isDownloaded = canDownload && downloadTracker.isDownloaded(((UriSample) sample).uri);
      ImageButton downloadButton = view.findViewById(R.id.download_button);
      downloadButton.setTag(sample);
      downloadButton.setPadding(60,0,20,0);
      /*downloadButton.setColorFilter(
          canDownload ? (isDownloaded ? 0xFF42A5F5 : 0xFFBDBDBD) : 0xFFEEEEEE);
      downloadButton.setImageResource(
          isDownloaded ? R.drawable.ic_download_done : R.drawable.ic_download);*/
    }
  }

  private static final class SampleGroup {

    public final String title;
    public final String coverImgUrl;
    public final List<Sample> samples;

    public SampleGroup(String title, String coverUrl) {
      this.title = title;
      this.coverImgUrl = coverUrl;
      this.samples = new ArrayList<>();
    }

  }

  private static final class DrmInfo {
    public final String drmScheme;
    public final String drmLicenseUrl;
    public final String[] drmKeyRequestProperties;
    public final boolean drmMultiSession;

    public DrmInfo(
        String drmScheme,
        String drmLicenseUrl,
        String[] drmKeyRequestProperties,
        boolean drmMultiSession) {
      this.drmScheme = drmScheme;
      this.drmLicenseUrl = drmLicenseUrl;
      this.drmKeyRequestProperties = drmKeyRequestProperties;
      this.drmMultiSession = drmMultiSession;
    }

    public void updateIntent(Intent intent) {
      Assertions.checkNotNull(intent);
      intent.putExtra(PlayerActivity.DRM_SCHEME_EXTRA, drmScheme);
      intent.putExtra(PlayerActivity.DRM_LICENSE_URL_EXTRA, drmLicenseUrl);
      intent.putExtra(PlayerActivity.DRM_KEY_REQUEST_PROPERTIES_EXTRA, drmKeyRequestProperties);
      intent.putExtra(PlayerActivity.DRM_MULTI_SESSION_EXTRA, drmMultiSession);
    }
  }

  private abstract static class Sample {
    public final String name;
    public final String imgUrl;
    public final DrmInfo drmInfo;

    public Sample(String name, String imgUrl, DrmInfo drmInfo) {
      this.name = name;
      this.imgUrl = imgUrl;
      this.drmInfo = drmInfo;
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

  private static final class UriSample extends Sample {

    public final Uri uri;
    public final String extension;
    public final String adTagUri;
    public final String sphericalStereoMode;

    public UriSample(
        String name,
        String imgUrl,
        DrmInfo drmInfo,
        Uri uri,
        String extension,
        String adTagUri,
        String sphericalStereoMode) {
      super(name, imgUrl, drmInfo);
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

  }

  private static final class PlaylistSample extends Sample {

    public final UriSample[] children;

    public PlaylistSample(
        String name,
        String imgUrl,
        DrmInfo drmInfo,
        UriSample... children) {
      super(name, imgUrl, drmInfo);
      this.children = children;
    }

    @Override
    public Intent buildIntent(
        Context context, boolean preferExtensionDecoders, String abrAlgorithm) {
      String[] uris = new String[children.length];
      String[] extensions = new String[children.length];
      for (int i = 0; i < children.length; i++) {
        uris[i] = children[i].uri.toString();
        extensions[i] = children[i].extension;
      }
      return super.buildIntent(context, preferExtensionDecoders, abrAlgorithm)
          .putExtra(PlayerActivity.URI_LIST_EXTRA, uris)
          .putExtra(PlayerActivity.EXTENSION_LIST_EXTRA, extensions)
          .setAction(PlayerActivity.ACTION_VIEW_LIST);
    }

  }

  private class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
    ImageView bmImage;

    public DownloadImageTask(ImageView bmImage) {
      this.bmImage = bmImage;
    }

    protected Bitmap doInBackground(String... urls) {
      String urldisplay = urls[0];
      Bitmap mIcon11 = null;
      try {
        InputStream in = new java.net.URL(urldisplay).openStream();
        Log.d(TAG, "Image input stream established. "+urldisplay);
        mIcon11 = BitmapFactory.decodeStream(in);
      } catch (Exception e) {
        Log.e(TAG, e.getMessage());
      }
      return mIcon11;
    }

    protected void onPostExecute(Bitmap result) {
      bmImage.setImageBitmap(result);
    }
  }

  private class DownloadTextViewImageTask extends AsyncTask<String, Void, Drawable> {
    TextView textView;

    public DownloadTextViewImageTask(TextView bmImage) {
      this.textView = bmImage;
    }

    protected Drawable doInBackground(String... urls) {
      String urldisplay = urls[0];
      //Bitmap mIcon11 = null;
      Drawable bd = null;
      try {
        InputStream in = new java.net.URL(urldisplay).openStream();
        Log.d(TAG, "Image input stream established. "+urldisplay);
        //mIcon11 = BitmapFactory.decodeStream(in);
        bd = BitmapDrawable.createFromStream(in, urldisplay);
      } catch (Exception e) {
        Log.e(TAG, e.getMessage());
      }
      return bd;
    }

    protected void onPostExecute(Drawable result) {
      //bmImage.setCompoundDrawables(null, null, result, null);
      textView.setCompoundDrawables(null, result, null, null);
      //textView.setCompoundDrawablePadding((int)context.getResources().getDimension(R.dimen.imagemenu_drawable_padding)); // 12dp
      textView.setIncludeFontPadding(false);

    }
  }
}
