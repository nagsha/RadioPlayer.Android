package com.cy8018.radioplayer;


import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bumptech.glide.Glide;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    // Station list JSON file url
    public static final String StationListUrl = "http://13.78.120.63/radio/station_list.json";

    public final MsgHandler mHandler = new MsgHandler(this);

    // message to load the station list
    public static final int MSG_LOAD_LIST = 0;

    // message to play the radio
    public static final int MSG_PLAY = 1;

    // station list
    protected List<Station> mStationList;

    // Exo Player instance
    protected SimpleExoPlayer player;

    // DataSource factory instance
    protected DataSource.Factory dataSourceFactory;

    private Station mCurrentStation;

    protected TextView textCurrentStationName;

    protected ImageView imageCurrentStationLogo;

    protected ImageView imagePlayBtn;

    public static class MsgHandler extends Handler {
        WeakReference<MainActivity> mMainActivityWeakReference;

        MsgHandler(MainActivity mainActivity) {
            mMainActivityWeakReference = new WeakReference<>(mainActivity);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            Log.d(TAG, "Handler: msg.what = " + msg.what);

            MainActivity mainActivity = mMainActivityWeakReference.get();

            if (msg.what == MSG_LOAD_LIST) {
                mainActivity.initStationListView();
            }
            else if (msg.what == MSG_PLAY) {
                int selectedPosition = (int) msg.obj;
                mainActivity.mCurrentStation = mainActivity.mStationList.get(selectedPosition);
                mainActivity.setCurrentPlayInfo(mainActivity.mCurrentStation);
                mainActivity.play(mainActivity.mCurrentStation.url);
            }
        }
    }

    protected void setCurrentPlayInfo(Station station)
    {
        // Load the station logo.
        Glide.with(this)
                .asBitmap()
                .load(station.logo)
                .into(imageCurrentStationLogo);

        textCurrentStationName.setText(station.name);

        int iResource = getResources().getIdentifier("@drawable/refresh", null, getPackageName());
        imagePlayBtn.setImageResource(iResource);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate: ");

        imagePlayBtn = findViewById(R.id.imagePlayBtn);
        imageCurrentStationLogo = findViewById(R.id.imageCurrentStationLogo);
        textCurrentStationName = findViewById(R.id.textCurrentStationName);

        initializePlayer();

        new Thread(loadListRunnable).start();
    }

    protected void initializePlayer (){
        if (null == player) {
            player = new SimpleExoPlayer.Builder(this).build();
        }

        // Produces DataSource instances through which media data is loaded.
        dataSourceFactory = new DefaultDataSourceFactory(this, Util.getUserAgent(this, "RadioPlayer"));
    }

    protected void play(String url)
    {
        Uri uri = Uri.parse(url);
        if (player.isPlaying()) {
            player.stop();
        }

        // This is the MediaSource representing the media to be played.
        MediaSource mediaSource;

        // Makes a best guess to infer the type from a Uri.
        int mediaType = Util.inferContentType(uri);
        switch (mediaType) {
            case C.TYPE_DASH:
                Log.d(TAG, "MediaType: DASH,  URL:" + url);
                mediaSource =  new DashMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
                break;
            case C.TYPE_SS:
                Log.d(TAG, "MediaType: SS,  URL:" + url);
                mediaSource =  new SsMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
                break;
            case C.TYPE_HLS:
                Log.d(TAG, "MediaType: HLS,  URL:" + url);
                mediaSource =  new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
                break;
            default:
                Log.d(TAG, "MediaType: OTHER,  URL:" + url);
                mediaSource =  new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
                break;
        }

        // Prepare the player with the source.
        player.prepare(mediaSource);
        player.setPlayWhenReady(true);
    }

    private void initStationListView() {
        Log.d(TAG, "initStationListView: ");
        RecyclerView stationListView = findViewById(R.id.stationList);
        StationListAdapter adapter= new StationListAdapter(this, mStationList);
        stationListView.setAdapter(adapter);
        stationListView.setLayoutManager(new LinearLayoutManager(this));
    }

    Runnable loadListRunnable = new Runnable(){
        @Override
        public void run() {
            String jsonString = getJsonString(StationListUrl);

            JSONObject object = JSON.parseObject(jsonString);
            Object objArray = object.get("stations");
            String str = objArray+"";

            mStationList = JSON.parseArray(str, Station.class);
            Log.d(TAG,  mStationList.size() +" stations loaded from server.");

            // Send Message to Main thread to load the station list
            mHandler.sendEmptyMessage(MSG_LOAD_LIST);
        }

        private String getJsonString(String url) {
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url(url)
                        .build();
                Response responses = null;

                try {
                    responses = client.newCall(request).execute();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                String jsonData = responses.body().string();
                Log.d(TAG, "getJsonString: jsonData:[" + jsonData + "]");
                return jsonData;
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    };
}
