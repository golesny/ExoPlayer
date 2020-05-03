package com.google.android.exoplayer2.demo;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TabHost;
import android.widget.TextView;

import com.google.android.exoplayer2.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CustomAdapter extends BaseAdapter{
    private static final String TAG = "CustomAdapter";
    SampleChooserActivity context;
    List<Sample> allelements = new ArrayList<>();
    List<Sample> elements = new ArrayList<>();
    Map<String, Bitmap> imageCache = new HashMap<>();
    SampleCategory cat = SampleCategory.VIDEOS;

    private static LayoutInflater inflater=null;

    public CustomAdapter(SampleChooserActivity mainActivity) {
        context=mainActivity;
        inflater = ( LayoutInflater )context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount() {
        return elements.size();
    }

    @Override
    public Object getItem(int position) {
        return elements.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        TextView os_text;
        ImageView os_img;
        final RelativeLayout frame;
        View rowView;

        rowView = inflater.inflate(R.layout.sample_gridlayout, null);
        os_text =(TextView) rowView.findViewById(R.id.os_texts);
        os_img =(ImageView) rowView.findViewById(R.id.os_images);
        frame = (RelativeLayout) rowView.findViewById(R.id.sample_frame);

        if (position < elements.size()) {

            final Sample sample = elements.get(position);
            if (sample.isEmpty()) {
                rowView.setVisibility(View.INVISIBLE);
            } else {
                rowView.setVisibility(View.VISIBLE);
                os_text.setText(sample.name);
                os_text.setBackgroundColor(sample.color);
                if (imageCache.containsKey(sample.imgUrl)) {
                    Bitmap img = imageCache.get(sample.imgUrl);
                    os_img.setImageBitmap(img);
                    os_img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                }

                rowView.setOnClickListener(new OnClickListener() {

                    @Override
                    public void onClick(View view) {
                        //Toast.makeText(context, "You Clicked "+elements.get(position).uri, Toast.LENGTH_SHORT).show();
                        Sample s = sample;
                        frame.setBackgroundColor(0xFF009900);
                        context.startVideo(s);
                    }
                });
            }
        }
        return rowView;
    }

    public void setResources(Map<String, Bitmap> res) {
        imageCache.putAll(res);
        notifyDataSetChanged();
    }

    public void addElements(List<Sample> res) {
        allelements = res;
    }

    /**
     * 1  2  3  4        1  4  7  10
     * 5  6  7  8   -->  2  5  8
     * 9 10              3  6  9
     */
    private List<Sample> translatePositions(List<Sample> samples) {
        Log.i(TAG, "translating video positions, "+samples.size()+" samples");
        int itemsPerRow = (int)Math.ceil(samples.size() / 3f);
        List<List<Sample>> samplesRows = new ArrayList<>();
        samplesRows.add(new ArrayList<>());
        samplesRows.add(new ArrayList<>());
        samplesRows.add(new ArrayList<>());
        int origidx=0;
        while (origidx < samples.size()) {
            int row = origidx % 3;
            samplesRows.get(row).add(samples.get(origidx++));
        }
        if (samplesRows.get(0).size() > samplesRows.get(1).size()) {
            samplesRows.get(1).add(new UriSample());
        }
        List<Sample> returnVal = new ArrayList<>();
        returnVal.addAll(samplesRows.get(0));
        returnVal.addAll(samplesRows.get(1));
        returnVal.addAll(samplesRows.get(2));
        return returnVal;
    }

    public void setFilter(final SampleCategory cat) {
        this.cat = cat;
        updateFilteredElements();
    }

    public void updateFilteredElements() {
        internalUpdateFilteredElements(context.getSharedPreferences(PlayerActivity.PREFFILE, Context.MODE_PRIVATE));
    }

    private void internalUpdateFilteredElements(SharedPreferences sharedPreferences) {
        long max = sharedPreferences.getInt(PlayerActivity.getCategoryKey(PlayerActivity.KEY_LAST_ITEM_COUNT_BASE, cat.name()), 1);
        Log.d(TAG, "update Filtered Elements: maxItems="+max);
        elements = translatePositions(allelements.stream().filter(s -> s.category == cat).limit(max).collect(Collectors.toList()));
        notifyDataSetChanged();
    }
}