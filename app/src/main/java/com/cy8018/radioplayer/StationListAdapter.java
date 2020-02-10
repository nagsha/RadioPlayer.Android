package com.cy8018.radioplayer;


import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import java.util.List;

public class StationListAdapter extends RecyclerView.Adapter<StationListAdapter.ViewHolder>{

    private static final String TAG = "StationListAdapter";

    private int selectedPos = RecyclerView.NO_POSITION;

    private List<Station> mStationList;
    private Context mContext;

    StationListAdapter(Context context, List<Station> stationList) {
        this.mContext = context;
        this.mStationList = stationList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_layout, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, final int position) {
        Log.d(TAG, "onBindViewHolder: called.");

        // Load the station logo.
        Glide.with(mContext)
                .asBitmap()
                .load(mStationList.get(position).logo)
                .into(holder.stationLogo);

        // Load the country flag.
//        Glide.with(mContext)
//                .asBitmap()
//                .load(getFlagUrlByCountry(mStationList.get(position).country))
//                .into(holder.flag);

        String flagResource = getFlagResourceByCountry(mStationList.get(position).country);
        int iFlagResource = mContext.getResources().getIdentifier(flagResource, null, mContext.getPackageName());
        Log.d(TAG, "onBindViewHolder: flagResource:" + flagResource + " iFlagResource:" + iFlagResource);
        holder.flag.setImageResource(iFlagResource);

        String title = mStationList.get(position).name + ", " + mStationList.get(position).city;
        if (mStationList.get(position).province != null && mStationList.get(position).province.length() > 0) {
            title = title + ", " + mStationList.get(position).province;
        }

        // Set the station title
        holder.stationTitle.setText(title);

        // Set the station description
        holder.description.setText(mStationList.get(position).description);

        // Set OnClickListener
        holder.parentLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: clicked on:" + mStationList.get(position).name);

                // Below line is just like a safety check, because sometimes holder could be null,
                // in that case, getAdapterPosition() will return RecyclerView.NO_POSITION
                if (holder.getAdapterPosition() == RecyclerView.NO_POSITION) return;

                // Updating old as well as new positions
                notifyItemChanged(selectedPos);
                selectedPos = holder.getAdapterPosition();
                notifyItemChanged(selectedPos);


                // Send MSG_PLAY message to main thread to play the radio
                Message msg = new Message();
                msg.obj = position;
                msg.what = ((MainActivity)mContext).MSG_PLAY;
                ((MainActivity)mContext).mHandler.sendMessage(msg);
            }
        });

        holder.itemView.setSelected(selectedPos == position);
    }

    @Override
    public int getItemCount() {
        if (mStationList == null)
        {
            return 0;
        }
        return mStationList.size();
    }

    private String getFlagResourceByCountry(String country) {
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

    class ViewHolder extends RecyclerView.ViewHolder {

        ImageView stationLogo;
        ImageView flag;
        TextView stationTitle;
        TextView description;
        RelativeLayout parentLayout;

        private ViewHolder (View itemView)
        {
            super(itemView);
            flag = itemView.findViewById(R.id.flag);
            stationLogo = itemView.findViewById(R.id.logo);
            stationTitle = itemView.findViewById(R.id.stationTitle);
            description = itemView.findViewById(R.id.description);
            parentLayout = itemView.findViewById(R.id.parent_layout);
        }
    }
}
