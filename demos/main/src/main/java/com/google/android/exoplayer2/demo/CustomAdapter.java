package com.google.android.exoplayer2.demo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomAdapter extends BaseAdapter{
    private static final String TAG = "CustomAdapter";
    SampleChooserActivity context;
    List<UriSample> elements = new ArrayList<>();
    Map<String, Bitmap> imageCache = new HashMap<>();

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

            final UriSample sample = elements.get(position);
            if (sample.isEmpty()) {
                rowView.setVisibility(View.INVISIBLE);
            } else {
                rowView.setVisibility(View.VISIBLE);
                os_text.setText(sample.name);
                os_text.setBackgroundColor(sample.color);
                if (imageCache.containsKey(sample.imgUrl)) {
                    Bitmap img = imageCache.get(sample.imgUrl);
                    os_img.setImageBitmap(img);
                }

                rowView.setOnClickListener(new OnClickListener() {

                    @Override
                    public void onClick(View view) {
                        //Toast.makeText(context, "You Clicked "+elements.get(position).uri, Toast.LENGTH_SHORT).show();
                        UriSample s = sample;
                        frame.setBackgroundColor(0xFF009900);
                        if (s != null) {
                            context.startVideo(s);
                        }
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

    public void addElements(List<UriSample> res) {
        elements = translatePositions(res);
    }

    /**
     * 1  2  3  4        1  4  7  10
     * 5  6  7  8   -->  2  5  8
     * 9 10              3  6  9
     */
    private List<UriSample> translatePositions(List<UriSample> samples) {
        Log.i(TAG, "translating video positions, "+samples.size()+" samples");
        int itemsPerRow = (int)Math.ceil(samples.size() / 3f);
        List<List<UriSample>> samplesRows = new ArrayList<>();
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
        List<UriSample> returnVal = new ArrayList<>();
        returnVal.addAll(samplesRows.get(0));
        returnVal.addAll(samplesRows.get(1));
        returnVal.addAll(samplesRows.get(2));
        return returnVal;
    }
}