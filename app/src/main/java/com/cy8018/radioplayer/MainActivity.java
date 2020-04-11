package com.cy8018.radioplayer;


import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.net.TrafficStats;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

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
    public static final String ServerPrefix = "https://gitee.com/cy8018/Resources/raw/master/radio/";

    // Station list JSON file url
    public static final String StationListFileName = "radio_station_list_ext.json";

    public final MsgHandler mHandler = new MsgHandler(this);

    // message to load the station list
    public static final int MSG_LOAD_LIST = 0;

    // message to play the radio
    public static final int MSG_PLAY = 1;

    // message to get buffering info
    public static final int MSG_GET_BUFFERING_INFO = 2;

    // station list
    protected List<Station> mStationList;

    private Station mCurrentStation;

    private int mCurrentSourceIndex;

    // Exo Player instance
    protected SimpleExoPlayer player;

    // DataSource factory instance
    protected DataSource.Factory dataSourceFactory;

    protected TextView textCurrentStationName;

    protected TextView textSourceInfo;
    protected TextView textBufferingInfo;

    protected ImageView imageCurrentStationLogo;

    protected GifImageView imagePlayBtn;

    protected ImageView imageCurrentFlag;

    private long lastTotalRxBytes = 0;

    private long lastTimeStamp = 0;

    protected boolean isBuffering = false;

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
        public void handleMessage(@NotNull Message msg) {
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
                mainActivity.play(mainActivity.mCurrentStation, 0);
            }
            else if (msg.what == MSG_GET_BUFFERING_INFO) {
                mainActivity.getBufferingInfo();
            }
        }
    }

    protected void setCurrentPlayInfo(@NotNull Station station)
    {
        // Load the station logo.
        Glide.with(this)
                .asBitmap()
                .load(ServerPrefix + "logo/" + station.logo)
                .into(imageCurrentStationLogo);

        String title = station.name + ", " + station.city;
        if (station.province != null && station.province.length() > 0) {
            title = title + ", " + station.province;
        }
        textCurrentStationName.setText(title);

        int iResource = getResources().getIdentifier("@drawable/loading_circle", null, getPackageName());
        imagePlayBtn.setImageResource(iResource);

        imageCurrentFlag.setImageResource(getResources().getIdentifier(getFlagResourceByCountry(station.country), null, getPackageName()));
    }

    private void getBufferingInfo() {
        String netSpeed = getNetSpeedText(getNetSpeed());
        //int percent = getBufferedPercentage();
        //String bufferingInfo = " " + percent + "%\n" + netSpeed;
        String bufferingInfo = netSpeed;
        textBufferingInfo.setText(bufferingInfo);
        Log.d(TAG, bufferingInfo);
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
        textSourceInfo = findViewById(R.id.textSourceInfo);
        textBufferingInfo = findViewById(R.id.textBufferingInfo);

        imagePlayBtn.setImageResource(getResources().getIdentifier("@drawable/play", null, getPackageName()));

        imagePlayBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Log.d(TAG, "OnClickListener mPlaybackStatus: " + mPlaybackStatus);

                switch (mPlaybackStatus) {
                    case PlaybackStatus.IDLE:
                    case PlaybackStatus.PAUSED:
                        if (null != mCurrentStation && !mCurrentStation.url.isEmpty()) {
                            play(mCurrentStation.url.get(mCurrentSourceIndex));
                        }
                        break;
                    case PlaybackStatus.PLAYING:
                        player.stop(false);
                        break;
                    default:
                }
            }
        });

        textSourceInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchSource();
            }
        });

        initializePlayer();

        new Thread(loadListRunnable).start();
        new Thread(networkCheckRunnable).start();
    }

    @Override
    protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }

    private  void hideBufferingInfo () {
        isBuffering = false;
        textBufferingInfo.setText("");
        textBufferingInfo.setVisibility(View.INVISIBLE);
    }

    private void showBufferingInfo () {
        isBuffering = true;
        textBufferingInfo.setVisibility(View.VISIBLE);
    }

    private long getNetSpeed() {

        long nowTotalRxBytes = TrafficStats.getUidRxBytes(getApplicationContext().getApplicationInfo().uid) == TrafficStats.UNSUPPORTED ? 0 : (TrafficStats.getTotalRxBytes() / 1024);
        long nowTimeStamp = System.currentTimeMillis();
        long calculationTime = (nowTimeStamp - lastTimeStamp);
        if (calculationTime == 0) {
            return calculationTime;
        }

        long speed = ((nowTotalRxBytes - lastTotalRxBytes) * 1000 / calculationTime);
        lastTimeStamp = nowTimeStamp;
        lastTotalRxBytes = nowTotalRxBytes;
        return speed;
    }

    public String getNetSpeedText(long speed) {
        String text = "";
        if (speed >= 0 && speed < 1024) {
            text = speed + "B/s";
        } else if (speed >= 1024 && speed < (1024 * 1024)) {
            text = speed / 1024 + "KB/s";
        } else if (speed >= (1024 * 1024) && speed < (1024 * 1024 * 1024)) {
            text = speed / (1024 * 1024) + "MB/s";
        }
        return text;
    }

    public int getBufferedPercentage() {
        if (null == player) {
            return 0;
        }

        return player.getBufferedPercentage();
    }

    protected void initializePlayer (){
        if (null == player) {
            player = new SimpleExoPlayer.Builder(this).build();
        }

        player.addListener(this);
        // Produces DataSource instances through which media data is loaded.
        dataSourceFactory = new DefaultDataSourceFactory(this, Util.getUserAgent(this, "RadioPlayer"));

    }

    private String getSourceInfo(Station station, int source) {

        return source + 1 + "/" + station.url.size();
    }

    protected void play(Station station, int source) {

        mCurrentStation = station;
        mCurrentSourceIndex = source;

        //textCurrentStationName.setText(station.name);
        textSourceInfo.setText(getSourceInfo(station, source));

        play(station.url.get(source));
    }

    protected void play(String url)
    {
        if (url == null || url.isEmpty())
            return;

        Toast toast= Toast.makeText(getApplicationContext(), "Playing  "+ mCurrentStation.name + "  " + textSourceInfo.getText() + "\n" + url, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.BOTTOM, 0, 180);
        toast.show();

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
                showBufferingInfo();
                break;
            case Player.STATE_ENDED:
                mPlaybackStatus = PlaybackStatus.STOPPED;
                imagePlayBtn.setImageResource(getResources().getIdentifier("@drawable/play", null, getPackageName()));
                hideBufferingInfo();
                break;
            case Player.STATE_READY:
                mPlaybackStatus = playWhenReady ? PlaybackStatus.PLAYING : PlaybackStatus.PAUSED;
                if (mPlaybackStatus ==PlaybackStatus.PLAYING) {
                    imagePlayBtn.setImageResource(getResources().getIdentifier("@drawable/pause", null, getPackageName()));
                }
                else {
                    imagePlayBtn.setImageResource(getResources().getIdentifier("@drawable/play", null, getPackageName()));
                }
                hideBufferingInfo();
                break;
            case Player.STATE_IDLE:
            default:
                mPlaybackStatus = PlaybackStatus.IDLE;
                imagePlayBtn.setImageResource(getResources().getIdentifier("@drawable/play", null, getPackageName()));
                hideBufferingInfo();
                break;
        }
    }

    protected  void switchSource() {

        if (null == mCurrentStation) {
            mCurrentStation = mStationList.get(mCurrentSourceIndex);
        }

        int index = 0;
        if (mCurrentSourceIndex + 1 < mCurrentStation.url.size()) {
            index = mCurrentSourceIndex + 1;
        }
        play(mCurrentStation, index);
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

    Runnable networkCheckRunnable = new Runnable() {
        @Override
        public void run() {
            while (true) {
                if (isBuffering) {
                    try {
                        mHandler.sendEmptyMessage(MSG_GET_BUFFERING_INFO);
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    };

    Runnable loadListRunnable = new Runnable(){
        @Override
        public void run() {
            String jsonString = getJsonString(ServerPrefix + StationListFileName);

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
