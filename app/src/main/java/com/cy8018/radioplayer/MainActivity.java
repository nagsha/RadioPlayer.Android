package com.cy8018.radioplayer;


import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bumptech.glide.Glide;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import pl.droidsonroids.gif.GifImageView;

public class MainActivity extends AppCompatActivity implements Player.EventListener {

    private static final String TAG = "MainActivity";

    // ServerPrefix address
    public static final String ServerPrefix = "https://gitee.com/cy8018/Resources/raw/master";

    // Station list JSON file url
    public static final String StationListUrl = ServerPrefix + "/radio/radio_station_list.json";

    public final MsgHandler mHandler = new MsgHandler(this);

    // message to load the station list
    public static final int MSG_LOAD_LIST = 0;

    // message to play the radio
    public static final int MSG_PLAY = 1;

    // station list
    protected List<Station> mStationList;

    private Station mCurrentStation;

    // Exo Player instance
    protected SimpleExoPlayer player;

    // DataSource factory instance
    protected DataSource.Factory dataSourceFactory;

    protected TextView textCurrentStationName;

    protected ImageView imageCurrentStationLogo;

    protected GifImageView imagePlayBtn;

    protected ImageView imageCurrentFlag;

    protected int mPlaybackStatus;

    static class PlaybackStatus {
        static final int IDLE = 0;
        static final int LOADING = 1;
        static final int PLAYING = 2;
        static final int PAUSED = 3;
        static final int STOPPED = 4;
    }

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

    protected void setCurrentPlayInfo(@NotNull Station station)
    {
        // Load the station logo.
        Glide.with(this)
                .asBitmap()
                .load(ServerPrefix + "/radio/logo/" + station.logo)
                .into(imageCurrentStationLogo);

        String title = station.name + ", " + station.city;
        if (station.province != null && station.province.length() > 0) {
            title = title + ", " + station.province;
        }
        textCurrentStationName.setText(title);

        int iResource = getResources().getIdentifier("@drawable/loading_snake", null, getPackageName());
        imagePlayBtn.setImageResource(iResource);

        imageCurrentFlag.setImageResource(getResources().getIdentifier(getFlagResourceByCountry(station.country), null, getPackageName()));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate: ");

        imagePlayBtn = findViewById(R.id.imagePlayBtn);
        imageCurrentFlag = findViewById(R.id.imageCurrentFlag);
        imageCurrentStationLogo = findViewById(R.id.imageCurrentStationLogo);
        textCurrentStationName = findViewById(R.id.textCurrentStationName);


        imagePlayBtn.setImageResource(getResources().getIdentifier("@drawable/play", null, getPackageName()));


        imagePlayBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Log.d(TAG, "OnClickListener mPlaybackStatus: " + mPlaybackStatus);

                switch (mPlaybackStatus) {
                    case PlaybackStatus.IDLE:
                    case PlaybackStatus.PAUSED:
                        if (null != mCurrentStation && !mCurrentStation.url.isEmpty()) {
                            play(mCurrentStation.url);
                        }
                        break;
                    case PlaybackStatus.PLAYING:
                        player.stop(false);
                        break;
                    default:
                }
            }
        });

        initializePlayer();

        new Thread(loadListRunnable).start();
    }

    @Override
    protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }

    protected void initializePlayer (){
        if (null == player) {
            player = new SimpleExoPlayer.Builder(this).build();
        }

        player.addListener(this);
        // Produces DataSource instances through which media data is loaded.
        dataSourceFactory = new DefaultDataSourceFactory(this, Util.getUserAgent(this, "RadioPlayer"));

    }

    protected void play(String url)
    {
        if (url == null || url.isEmpty())
            return;

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
                Log.d(TAG, "MediaType: DASH,  URL: " + url);
                mediaSource =  new DashMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
                break;
            case C.TYPE_SS:
                Log.d(TAG, "MediaType: SS,  URL: " + url);
                mediaSource =  new SsMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
                break;
            case C.TYPE_HLS:
                Log.d(TAG, "MediaType: HLS,  URL: " + url);
                mediaSource =  new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
                break;
            default:
                Log.d(TAG, "MediaType: OTHER,  URL: " + url);
                mediaSource =  new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
                break;
        }

        // Prepare the player with the source.
        player.prepare(mediaSource);
        player.setPlayWhenReady(true);
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {

        Log.d(TAG, "onPlayerStateChanged: playWhenReady:"+ playWhenReady + " playbackState:" + playbackState);
        switch (playbackState) {
            case Player.STATE_BUFFERING:
                mPlaybackStatus = PlaybackStatus.LOADING;
                imagePlayBtn.setImageResource(getResources().getIdentifier("@drawable/loading_circle", null, getPackageName()));
                break;
            case Player.STATE_ENDED:
                mPlaybackStatus = PlaybackStatus.STOPPED;
                imagePlayBtn.setImageResource(getResources().getIdentifier("@drawable/play", null, getPackageName()));
                break;
            case Player.STATE_READY:
                mPlaybackStatus = playWhenReady ? PlaybackStatus.PLAYING : PlaybackStatus.PAUSED;
                if (mPlaybackStatus ==PlaybackStatus.PLAYING) {
                    imagePlayBtn.setImageResource(getResources().getIdentifier("@drawable/pause", null, getPackageName()));
                }
                else {
                    imagePlayBtn.setImageResource(getResources().getIdentifier("@drawable/play", null, getPackageName()));
                }
                break;
            default:
                mPlaybackStatus = PlaybackStatus.IDLE;
                imagePlayBtn.setImageResource(getResources().getIdentifier("@drawable/play", null, getPackageName()));
                break;
        }
    }

    private void releasePlayer() {
        if (player != null) {
            player.stop();
            player.release();
            player = null;
        }
    }

    private void initStationListView() {
        Log.d(TAG, "initStationListView: ");
        RecyclerView stationListView = findViewById(R.id.stationList);
        StationListAdapter adapter= new StationListAdapter(this, mStationList);
        stationListView.setAdapter(adapter);
        stationListView.setLayoutManager(new LinearLayoutManager(this));
    }

    public String getFlagResourceByCountry(@NotNull String country) {
        String resource = null;
        switch(country)
        {
            case "AU":
                resource = "@drawable/flag_au";
                break;
            case "CA":
                resource = "@drawable/flag_ca";
                break;
            case "CN":
                resource = "@drawable/flag_cn";
                break;
            case "UK":
                resource = "@drawable/flag_uk";
                break;
            case "US":
                resource = "@drawable/flag_us";
                break;
            case "NZ":
                resource = "@drawable/flag_nz";
                break;
        }
        return resource;
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

        @Nullable
        private String getJsonString(String url) {
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(url).build();
                Response responses = client.newCall(request).execute();
                String jsonData = responses.body().string();
                Log.d(TAG, "getJsonString: [" + jsonData + "]");

                return jsonData;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    };
}
