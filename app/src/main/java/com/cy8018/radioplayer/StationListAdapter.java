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
        // Load the station logo.
        Glide.with(mContext)
                .asBitmap()
                .load(((MainActivity)mContext).ServerPrefix + "logo/" + mStationList.get(position).logo)
                .into(holder.stationLogo);

        String flagResource = ((MainActivity)mContext).getFlagResourceByCountry(mStationList.get(position).country);
        int iFlagResource = mContext.getResources().getIdentifier(flagResource, null, mContext.getPackageName());

        holder.flag.setImageResource(iFlagResource);

        String title = mStationList.get(position).name + ", " + mStationList.get(position).city;
        if (mStationList.get(position).province != null && mStationList.get(position).province.length() > 0) {
            title = title + ", " + mStationList.get(position).province;
        }

        // Set the station title
        holder.stationTitle.setText(title);

        Log.d(TAG, "onBindViewHolder: Station loaded: " + title);
        // Set the station description
        holder.description.setText(mStationList.get(position).description);

        // Set OnClickListener
        holder.parentLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: clicked on: " + mStationList.get(position).name);

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
