package com.cy8018.radioplayer;


import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
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
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static final String StationListUrl = "http://52.183.27.112/radio/station_list.json";

    public static final int MSG_LOAD_LIST = 0;

    public static final int MSG_PLAY = 1;


    protected List<Station> mStationList;

    protected SimpleExoPlayer player;
    protected DataSource.Factory dataSourceFactory;

    private String CurrentStationUrl;

    public String getCurrentStationUrl() {
        return CurrentStationUrl;
    }

    public void setCurrentStationUrl(String currentStationUrl) {
        CurrentStationUrl = currentStationUrl;
    }


    public static Handler mHandler;
    {
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);


                Log.d(TAG, "Handler: msg.what = " + msg.what);

                if (msg.what == MSG_LOAD_LIST) {
                    initStationListView();
                }
                else if (msg.what == MSG_PLAY) {
                    String url = (String) msg.obj;
                    play(url);
                }
            }
        };
    }

//    public void OnPlay(String url)
//    {
//        setCurrentStationUrl(url);
//        new Thread(playerRunnable).start();
//    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate: ");

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
        MediaSource mediaSource = null;

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

    Runnable playerRunnable = new Runnable() {
        @Override
        public void run() {
            String url = getCurrentStationUrl();
            if (null != url && url.length() > 0)
            {
                play(url);
            }
        }
    };

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
