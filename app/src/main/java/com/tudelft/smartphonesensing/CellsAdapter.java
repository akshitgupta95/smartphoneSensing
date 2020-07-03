package com.tudelft.smartphonesensing;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class CellsAdapter extends RecyclerView.Adapter<CellsAdapter.CellsViewHolder> {

    private ArrayList<LocationCell> data;
    private AdapterView.OnItemClickListener onItemClickListener;


    public static class CellsViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        // each data item is just a string in this case
        private TextView textView;
        private Context mContext;

        public CellsViewHolder(View v) {
            super(v);
            mContext = v.getContext();
            v.setOnClickListener(this);
            textView = v.findViewById(R.id.cell_tv);
        }

        @Override
        public void onClick(View view) {
            Bundle bundle = new Bundle();
            bundle.putInt("cellName",(int) textView.getTag());
            Fragment fragment = new CellFragment();
            fragment.setArguments(bundle);
            //TODO: Bad practice, decouple fragment and activity using some other method, use viewPager
            MainActivity activity = (MainActivity) mContext;
            activity.getSupportFragmentManager().beginTransaction().hide(activity.getActiveFragment()).replace(R.id.main_container, fragment).addToBackStack(null).commit();
//            activity.getSupportFragmentManager().beginTransaction().hide(activity.getActiveFragment()).replace(R.id.main_container, fragment).show(fragment).addToBackStack(null).commit();

            activity.setActiveFragment(fragment);
            Log.i("CLICK", "RecyclerView Item Click Position");
        }
    }

    // Provide a suitable constructor (depends on the kind of dataset)
    public CellsAdapter(ArrayList<LocationCell> data, AdapterView.OnItemClickListener onItemClickListener) {
        this.data = data;
        this.onItemClickListener = onItemClickListener;
    }

    // Create new views (invoked by the layout manager)
    @Override
    public CellsAdapter.CellsViewHolder onCreateViewHolder(ViewGroup parent,
                                                           int viewType) {
        // create a new view
        View v = (View) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_cells_rv, parent, false);

        CellsViewHolder vh = new CellsViewHolder(v);
        return vh;
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(CellsViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        LocationCell cell = data.get(position);
        final String cellName = String.format("%s: %s", cell.getFloorplanId(), cell.getName());
        holder.textView.setText(cellName);
        holder.textView.setTag(cell.getId());
    }


    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return data.size();
    }
}
